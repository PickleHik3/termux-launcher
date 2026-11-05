package com.termux.ai;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;

/**
 * On-device speech-to-text on the Whisper ACFT LiteRT graphs ({@code litert-community/whisper-acft}):
 * one {@link LiteRtGraph} with an {@code encode} signature (log-mel {@code [1, 80, frames]} →
 * audio features {@code [1, frames/2, 512]}) and a {@code decode} signature (features, a fixed
 * 128-slot token sequence and an additive causal mask → logits {@code [1, 128, vocab]}), run
 * through the graph with inputs bound by name ({@link InterpreterGraph}: the stock Interpreter). The mel front end is
 * {@link WhisperMel}, the vocabulary {@link WhisperTokenizer}, the prompt and greedy loop
 * {@link WhisperDecoder}, the language when none is given {@link WhisperLanguageDetection}, the
 * pause-based cutting {@link WhisperSegmenter}.
 *
 * <p>CPU only, XNNPACK, {@code min(4, cores)} threads: the graphs are dynamic-range int8 with fp32
 * activations, the GPU delegate creates zero kernels for them on pong, and keeping STT off the GPU
 * means dictating to an agent never contends with that agent's own generation. Every decode step
 * re-runs the whole decoder (no KV cache), so a 2–4 token command is ~150 ms on base.en 10 s.
 *
 * <p>Locking follows {@link LiteRtEmbeddingRuntime}: transcribe and close serialize on this
 * object's monitor, so an eviction waits for a running transcription and never interrupts it;
 * the router takes no lock of its own around this runtime. Residency: registered as
 * {@link TaiResidency.Kind#STT} after the load (estimate: file size × 1.9 until the load meter
 * has measured it), busy while transcribing, deregistered on close.
 */
final class WhisperSttRuntime implements SttRuntime {
    static final String SIGNATURE_ENCODE = "encode";
    static final String SIGNATURE_DECODE = "decode";
    static final String TOKENIZER_FILE = "tokenizer.json";
    static final String RUNTIME_NAME = "whisper-acft";
    /** The mask's "attend" and "never attend" values, as the graph was exported. */
    static final float MASK_ALLOW = 0f;
    static final float MASK_DENY = -1e9f;

    private final TaiResidency residency;
    /** For the load meter and its history; {@code null} in the router's test seam (nothing is measured). */
    @Nullable private final Context appContext;
    private final LiteRtGraph.Loader loader;
    @Nullable private LiteRtGraph graph;
    @Nullable private WhisperTokenizer tokenizer;
    @Nullable private String loadedModelId;
    @Nullable private String loadedModelPath;
    private boolean englishOnly;
    private int frames;
    private int sequenceLength;
    private int vocabularySize;
    private String encodeInput = "";
    private String encodeOutput = "";
    private String decodeAudioInput = "";
    private String decodeTokensInput = "";
    private String decodeMaskInput = "";
    private String decodeOutput = "";
    @Nullable private LiteRtGraph.Tensor melTensor;
    @Nullable private LiteRtGraph.Tensor logitsTensor;
    @Nullable private LiteRtGraph.Tensor tokensTensor;
    @Nullable private LiteRtGraph.Signature encodeRun;
    @Nullable private LiteRtGraph.Signature decodeRun;
    /** The log-mel rows laid end to end, reused for every piece. */
    @Nullable private float[] melScratch;

    WhisperSttRuntime(@NonNull TaiResidency residency, @Nullable Context context) {
        this(residency, context, InterpreterGraph.LOADER);
    }

    /** With the graph runtime behind {@link LiteRtGraph} chosen by the caller; tests pass a fake. */
    WhisperSttRuntime(@NonNull TaiResidency residency, @Nullable Context context, @NonNull LiteRtGraph.Loader loader) {
        this.residency = residency;
        this.appContext = context == null ? null : context.getApplicationContext();
        this.loader = loader;
    }

    /** Whether {@code modelId} is the graph held right now. */
    @Override
    public synchronized boolean isLoaded(@NonNull String modelId) {
        return graph != null && modelId.equals(loadedModelId);
    }

    /** Loads the graph and tokenizer without transcribing, so a load overlaps the first words spoken. */
    @Override
    @NonNull
    public synchronized JSONObject warm(@NonNull TaiModelSpec spec) throws JSONException {
        JSONObject refusal = checkFiles(spec);
        if (refusal != null) return refusal;
        long started = System.currentTimeMillis();
        boolean wasLoaded = isLoaded(spec.id);
        try {
            ensureLoaded(spec);
        } catch (Throwable t) {
            return error(500, "stt_load_failed", "Whisper model failed to load: " + message(t));
        }
        JSONObject response = new JSONObject();
        response.put("ok", true);
        response.put("model", spec.id);
        response.put("loaded", true);
        response.put("alreadyLoaded", wasLoaded);
        response.put("windowSeconds", frames / WhisperMel.FRAMES_PER_SECOND);
        response.put("loadMs", System.currentTimeMillis() - started);
        response.put("_runtime", RUNTIME_NAME);
        return response;
    }

    /** As {@link #transcribe(TaiModelSpec, File, String, String, String)} with no language hint. */
    @Override
    @NonNull
    public synchronized JSONObject transcribe(@NonNull TaiModelSpec spec, @NonNull File audioFile,
                                              @Nullable String language, @Nullable String biasText) throws JSONException {
        return transcribe(spec, audioFile, language, null, biasText);
    }

    /**
     * Transcribes {@code audioFile} (WAV or raw PCM16 16 kHz mono, see {@link WhisperAudio}).
     * {@code language} is an ISO 639-1 code for the multilingual graphs ({@code .en} graphs always
     * decode English); none, or {@code auto}, has the graph detect it on the first piece, with
     * {@code languageHint} taken instead when the guess is not sure ({@link WhisperLanguageDetection}).
     * {@code biasText} is the vocabulary line placed behind {@code <|startofprev|>}, {@code null}
     * for plain dictation. The audio is cut into pieces at pauses, each piece decoded, and the
     * texts joined with a space.
     */
    @Override
    @NonNull
    public synchronized JSONObject transcribe(@NonNull TaiModelSpec spec, @NonNull File audioFile,
                                              @Nullable String language, @Nullable String languageHint,
                                              @Nullable String biasText) throws JSONException {
        JSONObject refusal = checkFiles(spec);
        if (refusal != null) return refusal;
        float[] audio;
        try {
            audio = WhisperAudio.read(audioFile);
        } catch (Exception e) {
            return error(400, "stt_audio_unreadable", "Audio could not be read: " + message(e));
        }
        long loadStarted = System.currentTimeMillis();
        try {
            ensureLoaded(spec);
        } catch (Throwable t) {
            return error(500, "stt_load_failed", "Whisper model failed to load: " + message(t));
        }
        long loadMs = System.currentTimeMillis() - loadStarted;
        WhisperTokenizer vocabulary = tokenizer;
        if (vocabulary == null) return error(500, "stt_load_failed", "Whisper tokenizer is not loaded.");
        String requested = WhisperLanguageDetection.normalize(language);
        String hint = WhisperLanguageDetection.normalize(languageHint);
        // A hint is advice: one Whisper has no token for is no hint at all.
        if (hint != null && vocabulary.languageToken(hint) < 0) hint = null;
        boolean detect = !englishOnly && requested == null && vocabulary.languages().ids.length > 0;
        // Known now unless the graph is to be asked, which waits for the first piece's features.
        WhisperLanguageDetection.Choice choice = detect ? null : WhisperLanguageDetection.forced(
            englishOnly || requested == null ? WhisperLanguageDetection.DEFAULT_LANGUAGE : requested);
        String promptLanguage = choice != null ? choice.language
            : hint != null ? hint : WhisperLanguageDetection.DEFAULT_LANGUAGE;
        int[] prompt;
        try {
            prompt = WhisperDecoder.prompt(vocabulary, promptLanguage, biasText);
        } catch (IllegalArgumentException e) {
            return error(400, "stt_language_unknown", "Whisper has no language token for '" + language + "'.");
        }
        if (prompt.length >= sequenceLength - 1) {
            return error(400, "stt_prompt_too_long", "The bias prompt leaves no room in the decoder's " + sequenceLength + "-token sequence.");
        }
        WhisperDecoder.Suppression suppression = WhisperDecoder.Suppression.forTokenizer(vocabulary);
        int windowSamples = frames * WhisperMel.HOP;
        List<WhisperSegmenter.Piece> pieces = WhisperSegmenter.pieces(audio, windowSamples);
        StringBuilder text = new StringBuilder();
        JSONArray segments = new JSONArray();
        long melMs = 0L, encodeMs = 0L, detectMs = 0L, decodeMs = 0L;
        int steps = 0;
        residency.setBusy(TaiResidency.Kind.STT, spec.id, true);
        try {
            for (WhisperSegmenter.Piece piece : pieces) {
                long t0 = System.nanoTime();
                float[][] mel = WhisperMel.logMel(piece.samples, frames);
                long t1 = System.nanoTime();
                encode(mel);
                long t2 = System.nanoTime();
                if (choice == null) {
                    WhisperLanguageDetection.Guess guess = WhisperLanguageDetection.detect(this::decodeStep, vocabulary, sequenceLength);
                    choice = WhisperLanguageDetection.choose(guess, hint);
                    // The same length whatever the language: only the language token changes.
                    if (!choice.language.equals(promptLanguage)) prompt = WhisperDecoder.prompt(vocabulary, choice.language, biasText);
                }
                long t3 = System.nanoTime();
                WhisperDecoder.Result result = WhisperDecoder.greedy(this::decodeStep, prompt, sequenceLength, suppression);
                long t4 = System.nanoTime();
                melMs += (t1 - t0) / 1_000_000L;
                encodeMs += (t2 - t1) / 1_000_000L;
                detectMs += (t3 - t2) / 1_000_000L;
                decodeMs += (t4 - t3) / 1_000_000L;
                steps += result.steps;
                String segmentText = vocabulary.decode(result.tokens).trim();
                if (segmentText.isEmpty()) continue;
                if (text.length() > 0) text.append(' ');
                text.append(segmentText);
                JSONObject segment = new JSONObject();
                segment.put("text", segmentText);
                segment.put("start", piece.start / (double) WhisperMel.SAMPLE_RATE);
                segment.put("end", piece.end / (double) WhisperMel.SAMPLE_RATE);
                segment.put("tokens", result.tokens.length);
                segment.put("stoppedAtEndOfText", result.endOfText);
                segment.put("repetitionGuard", result.repetition);
                segments.put(segment);
            }
        } catch (Throwable t) {
            return error(500, "stt_inference_failed", "Whisper inference failed: " + message(t));
        } finally {
            residency.setBusy(TaiResidency.Kind.STT, spec.id, false);
        }
        // No piece had speech, so the graph was never asked.
        if (choice == null) choice = WhisperLanguageDetection.choose(null, hint);
        JSONObject timings = new JSONObject();
        timings.put("loadMs", loadMs);
        timings.put("melMs", melMs);
        timings.put("encodeMs", encodeMs);
        timings.put("detectMs", detectMs);
        timings.put("decodeMs", decodeMs);
        timings.put("decodeSteps", steps);
        timings.put("pieces", pieces.size());
        JSONObject response = new JSONObject();
        response.put("ok", true);
        response.put("text", text.toString());
        response.put("model", spec.id);
        response.put("language", choice.language);
        response.put(WhisperLanguageDetection.RESPONSE_SOURCE, choice.source);
        if (choice.guess != null) {
            response.put(WhisperLanguageDetection.RESPONSE_DETECTED, choice.guess.code);
            response.put(WhisperLanguageDetection.RESPONSE_PROBABILITY, Math.round(choice.guess.probability * 1000.0) / 1000.0);
        }
        response.put("duration", audio.length / (double) WhisperMel.SAMPLE_RATE);
        response.put("segments", segments);
        response.put("windowSeconds", frames / WhisperMel.FRAMES_PER_SECOND);
        response.put("biased", biasText != null && !biasText.trim().isEmpty());
        response.put("timings", timings);
        response.put("_backend", TaiModelSpec.BACKEND_LITERT_LM);
        response.put("_runtime", RUNTIME_NAME);
        return response;
    }

    /** The model file and its tokenizer.json sidecar must both be there before anything is loaded. */
    @Nullable
    private JSONObject checkFiles(@NonNull TaiModelSpec spec) throws JSONException {
        if (spec.localPath == null || spec.localPath.trim().isEmpty()) {
            return error(404, "model_file_missing", "Speech model file is missing.");
        }
        File modelFile = new File(spec.localPath);
        if (!modelFile.isFile() || !modelFile.canRead()) {
            return error(404, "model_file_not_readable", "Speech model file is missing or unreadable.");
        }
        if (tokenizerFileFor(modelFile) == null) {
            return error(409, "stt_tokenizer_missing",
                "Whisper requires tokenizer.json next to the .tflite graph; re-download the speech model.");
        }
        return null;
    }

    private void ensureLoaded(@NonNull TaiModelSpec spec) throws Exception {
        File modelFile = new File(spec.localPath);
        String modelPath = modelFile.getAbsolutePath();
        if (graph != null && tokenizer != null && modelPath.equals(loadedModelPath)) return;
        close();
        File tokenizerFile = tokenizerFileFor(modelFile);
        if (tokenizerFile == null) throw new IllegalStateException("tokenizer.json is missing");
        tokenizer = WhisperTokenizer.fromFile(tokenizerFile);
        int threads = Math.max(1, Math.min(4, Runtime.getRuntime().availableProcessors()));
        // The same MemAvailable meter a chat load runs, across the graph's construction only
        // (InterpreterGraph: the stock LiteRT Interpreter XNNPACK path with `threads` threads).
        TaiLoadMeter meter = TaiLoadMeter.start(appContext);
        long measured;
        try {
            graph = loader.load(modelFile, threads);
        } finally {
            measured = meter.stop();
        }
        try {
            bindSignatures(graph);
        } catch (RuntimeException e) {
            close();
            throw e;
        }
        englishOnly = isEnglishOnly(spec, modelFile);
        loadedModelId = spec.id;
        loadedModelPath = modelPath;
        residency.register(TaiResidency.Entry.stt(spec, frames / WhisperMel.FRAMES_PER_SECOND)
            .withMeasured(measured >= 0L ? measured : null));
        if (measured >= 0L && appContext != null) {
            // Keyed at window 0 like an embedding load: the graph's audio window is not a context window.
            TaiRuntimeHistory.recordMeasuredLoad(appContext, spec, TaiDeviceCapabilities.detect(appContext),
                TaiModelSpec.BACKEND_LITERT_LM, "cpu", 0, measured);
        }
    }

    /**
     * Reads the shapes off the two signatures and picks each decode input by what it is — the
     * INT32 rank-2 tensor is the token sequence, the rank-4 float tensor the mask, the rank-3
     * float tensor the audio features — never by position: the export lists them as
     * {@code (mask, audio, tokens)} in one place and {@code args_0..2} in another.
     */
    private void bindSignatures(@NonNull LiteRtGraph graph) {
        boolean encode = false, decode = false;
        for (String key : graph.signatureKeys()) {
            encode |= SIGNATURE_ENCODE.equals(key);
            decode |= SIGNATURE_DECODE.equals(key);
        }
        if (!encode || !decode) throw new IllegalStateException("Graph lacks the encode/decode signatures: " + java.util.Arrays.toString(graph.signatureKeys()));
        String[] encodeInputs = graph.inputNames(SIGNATURE_ENCODE);
        String[] encodeOutputs = graph.outputNames(SIGNATURE_ENCODE);
        if (encodeInputs.length != 1 || encodeOutputs.length != 1) throw new IllegalStateException("Unexpected encode signature arity");
        encodeInput = encodeInputs[0];
        encodeOutput = encodeOutputs[0];
        int[] melShape = graph.input(SIGNATURE_ENCODE, encodeInput).shape;
        if (melShape.length != 3 || melShape[1] != WhisperMel.N_MELS) throw new IllegalStateException("Unexpected encode input shape " + java.util.Arrays.toString(melShape));
        frames = melShape[2];
        decodeAudioInput = decodeTokensInput = decodeMaskInput = "";
        for (String name : graph.inputNames(SIGNATURE_DECODE)) {
            LiteRtGraph.TensorSpec tensor = graph.input(SIGNATURE_DECODE, name);
            if (tensor.type == LiteRtGraph.ElementType.INT32 && tensor.rank() == 2) {
                decodeTokensInput = name;
                sequenceLength = tensor.shape[1];
            } else if (tensor.rank() == 4) {
                decodeMaskInput = name;
            } else if (tensor.rank() == 3) {
                decodeAudioInput = name;
            }
        }
        if (decodeAudioInput.isEmpty() || decodeTokensInput.isEmpty() || decodeMaskInput.isEmpty()) {
            throw new IllegalStateException("Could not identify the decode inputs by shape");
        }
        String[] decodeOutputs = graph.outputNames(SIGNATURE_DECODE);
        if (decodeOutputs.length != 1) throw new IllegalStateException("Unexpected decode signature arity");
        decodeOutput = decodeOutputs[0];
        int[] logitsShape = graph.output(SIGNATURE_DECODE, decodeOutput).shape;
        if (logitsShape.length != 3 || logitsShape[1] != sequenceLength) throw new IllegalStateException("Unexpected decode output shape " + java.util.Arrays.toString(logitsShape));
        vocabularySize = logitsShape[2];
        int[] maskShape = graph.input(SIGNATURE_DECODE, decodeMaskInput).shape;
        if (maskShape[2] != sequenceLength || maskShape[3] != sequenceLength) throw new IllegalStateException("Unexpected mask shape " + java.util.Arrays.toString(maskShape));
        // Tensors made once, sized by the graph from the static shapes, and reused for every run;
        // the encoder's output tensor is bound straight into the decoder as its audio input,
        // without a copy.
        LiteRtGraph.Tensor mel = graph.newInput(SIGNATURE_ENCODE, encodeInput);
        LiteRtGraph.Tensor features = graph.newOutput(SIGNATURE_ENCODE, encodeOutput);
        LiteRtGraph.Tensor tokens = graph.newInput(SIGNATURE_DECODE, decodeTokensInput);
        LiteRtGraph.Tensor mask = graph.newInput(SIGNATURE_DECODE, decodeMaskInput);
        LiteRtGraph.Tensor logits = graph.newOutput(SIGNATURE_DECODE, decodeOutput);
        float[] causal = new float[sequenceLength * sequenceLength];
        for (int i = 0; i < sequenceLength; i++) {
            for (int j = 0; j < sequenceLength; j++) causal[i * sequenceLength + j] = j > i ? MASK_DENY : MASK_ALLOW;
        }
        mask.writeFloats(causal, causal.length);
        HashMap<String, LiteRtGraph.Tensor> encodeIn = new HashMap<>();
        encodeIn.put(encodeInput, mel);
        HashMap<String, LiteRtGraph.Tensor> encodeOut = new HashMap<>();
        encodeOut.put(encodeOutput, features);
        HashMap<String, LiteRtGraph.Tensor> decodeIn = new HashMap<>();
        decodeIn.put(decodeAudioInput, features);
        decodeIn.put(decodeTokensInput, tokens);
        decodeIn.put(decodeMaskInput, mask);
        HashMap<String, LiteRtGraph.Tensor> decodeOut = new HashMap<>();
        decodeOut.put(decodeOutput, logits);
        encodeRun = graph.bind(SIGNATURE_ENCODE, encodeIn, encodeOut);
        decodeRun = graph.bind(SIGNATURE_DECODE, decodeIn, decodeOut);
        melTensor = mel;
        tokensTensor = tokens;
        logitsTensor = logits;
        melScratch = new float[WhisperMel.N_MELS * frames];
    }

    private void encode(@NonNull float[][] mel) {
        LiteRtGraph.Signature run = encodeRun;
        LiteRtGraph.Tensor input = melTensor;
        float[] flat = melScratch;
        if (graph == null || run == null || input == null || flat == null) throw new IllegalStateException("STT runtime is not loaded.");
        int at = 0;
        for (float[] row : mel) {
            System.arraycopy(row, 0, flat, at, frames);
            at += frames;
        }
        input.writeFloats(flat, at);
        run.run();
    }

    /** One decoder run over the current features; the logits at slot {@code filled − 1}. */
    @NonNull
    private float[] decodeStep(@NonNull int[] tokens, int filled) {
        LiteRtGraph.Signature run = decodeRun;
        LiteRtGraph.Tensor input = tokensTensor;
        LiteRtGraph.Tensor output = logitsTensor;
        if (graph == null || run == null || input == null || output == null) {
            throw new IllegalStateException("STT runtime is not loaded.");
        }
        input.writeInts(tokens, sequenceLength);
        run.run();
        float[] logits = new float[vocabularySize];
        output.readFloats((filled - 1) * vocabularySize, logits, vocabularySize);
        return logits;
    }

    /** The {@code .en} graphs decode English whatever the request says; the id and file name both carry it. */
    static boolean isEnglishOnly(@NonNull TaiModelSpec spec, @NonNull File modelFile) {
        String name = modelFile.getName().toLowerCase(Locale.ROOT);
        return spec.id.endsWith("-en") || name.contains(".en_") || name.contains(".en.");
    }

    @Nullable
    private static File tokenizerFileFor(@NonNull File modelFile) {
        File dir = modelFile.getParentFile();
        if (dir == null) return null;
        File tokenizer = new File(dir, TOKENIZER_FILE);
        return tokenizer.isFile() && tokenizer.canRead() ? tokenizer : null;
    }

    @NonNull
    private static String message(@NonNull Throwable t) {
        return t.getMessage() == null || t.getMessage().trim().isEmpty() ? t.getClass().getSimpleName() : t.getMessage();
    }

    @NonNull
    private JSONObject error(int status, @NonNull String code, @NonNull String message) throws JSONException {
        JSONObject error = new JSONObject();
        error.put("message", message);
        error.put("type", status >= 500 ? "server_error" : "invalid_request_error");
        error.put("code", code);
        JSONObject response = new JSONObject();
        response.put("ok", false);
        response.put("error", error);
        response.put("_statusCode", status);
        return response;
    }

    @Override
    public synchronized void close() {
        if (graph != null) {
            graph.close();
            graph = null;
        }
        tokenizer = null;
        if (loadedModelId != null) residency.deregister(TaiResidency.Kind.STT, loadedModelId);
        loadedModelId = null;
        loadedModelPath = null;
        melTensor = tokensTensor = logitsTensor = null;
        encodeRun = decodeRun = null;
        melScratch = null;
        frames = sequenceLength = vocabularySize = 0;
    }
}
