package com.termux.ai;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.tensorflow.lite.DataType;
import org.tensorflow.lite.Interpreter;
import org.tensorflow.lite.Tensor;

import java.io.File;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;

/**
 * On-device speech-to-text on the Whisper ACFT LiteRT graphs ({@code litert-community/whisper-acft}):
 * one {@link Interpreter} with an {@code encode} signature (log-mel {@code [1, 80, frames]} →
 * audio features {@code [1, frames/2, 512]}) and a {@code decode} signature (features, a fixed
 * 128-slot token sequence and an additive causal mask → logits {@code [1, 128, vocab]}), run
 * through {@link Interpreter#runSignature} with inputs bound by name. The mel front end is
 * {@link WhisperMel}, the vocabulary {@link WhisperTokenizer}, the prompt and greedy loop
 * {@link WhisperDecoder}, the pause-based cutting {@link WhisperSegmenter}.
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
final class WhisperSttRuntime implements AutoCloseable {
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
    @Nullable private Interpreter interpreter;
    @Nullable private TaiXnnpackDelegate xnnpackDelegate;
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
    @Nullable private ByteBuffer melBuffer;
    @Nullable private ByteBuffer featuresBuffer;
    @Nullable private ByteBuffer tokensBuffer;
    @Nullable private ByteBuffer maskBuffer;
    @Nullable private ByteBuffer logitsBuffer;

    WhisperSttRuntime(@NonNull TaiResidency residency, @Nullable Context context) {
        this.residency = residency;
        this.appContext = context == null ? null : context.getApplicationContext();
    }

    /** Whether {@code modelId} is the graph held right now. */
    synchronized boolean isLoaded(@NonNull String modelId) {
        return interpreter != null && modelId.equals(loadedModelId);
    }

    /** Loads the graph and tokenizer without transcribing, so a load overlaps the first words spoken. */
    @NonNull
    synchronized JSONObject warm(@NonNull TaiModelSpec spec) throws JSONException {
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

    /**
     * Transcribes {@code audioFile} (WAV or raw PCM16 16 kHz mono, see {@link WhisperAudio}).
     * {@code language} is an ISO 639-1 code for the multilingual graphs ({@code .en} graphs always
     * decode English); {@code biasText} is the vocabulary line placed behind {@code <|startofprev|>},
     * {@code null} for plain dictation. The audio is cut into pieces at pauses, each piece decoded,
     * and the texts joined with a space.
     */
    @NonNull
    synchronized JSONObject transcribe(@NonNull TaiModelSpec spec, @NonNull File audioFile,
                                       @Nullable String language, @Nullable String biasText) throws JSONException {
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
        String effectiveLanguage = englishOnly || language == null || language.trim().isEmpty() ? "en" : language.trim().toLowerCase(Locale.ROOT);
        int[] prompt;
        try {
            prompt = WhisperDecoder.prompt(vocabulary, effectiveLanguage, biasText);
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
        long melMs = 0L, encodeMs = 0L, decodeMs = 0L;
        int steps = 0;
        residency.setBusy(TaiResidency.Kind.STT, spec.id, true);
        try {
            for (WhisperSegmenter.Piece piece : pieces) {
                long t0 = System.nanoTime();
                float[][] mel = WhisperMel.logMel(piece.samples, frames);
                long t1 = System.nanoTime();
                encode(mel);
                long t2 = System.nanoTime();
                WhisperDecoder.Result result = WhisperDecoder.greedy(this::decodeStep, prompt, sequenceLength, suppression);
                long t3 = System.nanoTime();
                melMs += (t1 - t0) / 1_000_000L;
                encodeMs += (t2 - t1) / 1_000_000L;
                decodeMs += (t3 - t2) / 1_000_000L;
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
        JSONObject timings = new JSONObject();
        timings.put("loadMs", loadMs);
        timings.put("melMs", melMs);
        timings.put("encodeMs", encodeMs);
        timings.put("decodeMs", decodeMs);
        timings.put("decodeSteps", steps);
        timings.put("pieces", pieces.size());
        JSONObject response = new JSONObject();
        response.put("ok", true);
        response.put("text", text.toString());
        response.put("model", spec.id);
        response.put("language", effectiveLanguage);
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
        if (interpreter != null && tokenizer != null && modelPath.equals(loadedModelPath)) return;
        close();
        File tokenizerFile = tokenizerFileFor(modelFile);
        if (tokenizerFile == null) throw new IllegalStateException("tokenizer.json is missing");
        tokenizer = WhisperTokenizer.fromFile(tokenizerFile);
        int threads = Math.max(1, Math.min(4, Runtime.getRuntime().availableProcessors()));
        // simpleperf found litert 1.4.2's own XNNPACK delegate never gets worker threads (99.56%
        // of inference samples on one tid); our JNI shim builds a real one instead, and only the
        // interpreter's own thread count (which is fine) falls back to Interpreter's XNNPACK path.
        xnnpackDelegate = TaiXnnpackDelegate.create(threads);
        Interpreter.Options options = xnnpackDelegate != null
            ? new Interpreter.Options().setNumThreads(threads).setUseXNNPACK(false).addDelegate(xnnpackDelegate)
            : new Interpreter.Options().setNumThreads(threads).setUseXNNPACK(true);
        // The same MemAvailable meter a chat load runs, across the interpreter's construction only.
        TaiLoadMeter meter = TaiLoadMeter.start(appContext);
        long measured;
        try {
            interpreter = new Interpreter(modelFile, options);
        } finally {
            measured = meter.stop();
        }
        try {
            bindSignatures(interpreter);
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
    private void bindSignatures(@NonNull Interpreter interpreter) {
        boolean encode = false, decode = false;
        for (String key : interpreter.getSignatureKeys()) {
            encode |= SIGNATURE_ENCODE.equals(key);
            decode |= SIGNATURE_DECODE.equals(key);
        }
        if (!encode || !decode) throw new IllegalStateException("Graph lacks the encode/decode signatures: " + java.util.Arrays.toString(interpreter.getSignatureKeys()));
        String[] encodeInputs = interpreter.getSignatureInputs(SIGNATURE_ENCODE);
        String[] encodeOutputs = interpreter.getSignatureOutputs(SIGNATURE_ENCODE);
        if (encodeInputs.length != 1 || encodeOutputs.length != 1) throw new IllegalStateException("Unexpected encode signature arity");
        encodeInput = encodeInputs[0];
        encodeOutput = encodeOutputs[0];
        int[] melShape = interpreter.getInputTensorFromSignature(encodeInput, SIGNATURE_ENCODE).shape();
        if (melShape.length != 3 || melShape[1] != WhisperMel.N_MELS) throw new IllegalStateException("Unexpected encode input shape " + java.util.Arrays.toString(melShape));
        frames = melShape[2];
        Tensor featuresTensor = interpreter.getOutputTensorFromSignature(encodeOutput, SIGNATURE_ENCODE);
        decodeAudioInput = decodeTokensInput = decodeMaskInput = "";
        for (String name : interpreter.getSignatureInputs(SIGNATURE_DECODE)) {
            Tensor tensor = interpreter.getInputTensorFromSignature(name, SIGNATURE_DECODE);
            if (tensor.dataType() == DataType.INT32 && tensor.numDimensions() == 2) {
                decodeTokensInput = name;
                sequenceLength = tensor.shape()[1];
            } else if (tensor.numDimensions() == 4) {
                decodeMaskInput = name;
            } else if (tensor.numDimensions() == 3) {
                decodeAudioInput = name;
            }
        }
        if (decodeAudioInput.isEmpty() || decodeTokensInput.isEmpty() || decodeMaskInput.isEmpty()) {
            throw new IllegalStateException("Could not identify the decode inputs by shape");
        }
        String[] decodeOutputs = interpreter.getSignatureOutputs(SIGNATURE_DECODE);
        if (decodeOutputs.length != 1) throw new IllegalStateException("Unexpected decode signature arity");
        decodeOutput = decodeOutputs[0];
        Tensor logitsTensor = interpreter.getOutputTensorFromSignature(decodeOutput, SIGNATURE_DECODE);
        int[] logitsShape = logitsTensor.shape();
        if (logitsShape.length != 3 || logitsShape[1] != sequenceLength) throw new IllegalStateException("Unexpected decode output shape " + java.util.Arrays.toString(logitsShape));
        vocabularySize = logitsShape[2];
        Tensor maskTensor = interpreter.getInputTensorFromSignature(decodeMaskInput, SIGNATURE_DECODE);
        int[] maskShape = maskTensor.shape();
        if (maskShape[2] != sequenceLength || maskShape[3] != sequenceLength) throw new IllegalStateException("Unexpected mask shape " + java.util.Arrays.toString(maskShape));
        // Buffers sized exactly to the tensors (TFLite compares a ByteBuffer's capacity to the
        // tensor's bytes), allocated once and rewound before every run; the encoder's output buffer
        // is fed straight back into the decoder without a copy. Sizes come from the static shapes,
        // which are known before the signature's tensors are allocated.
        melBuffer = direct(WhisperMel.N_MELS * frames * 4);
        featuresBuffer = direct(elements(featuresTensor.shape()) * 4);
        tokensBuffer = direct(sequenceLength * 4);
        maskBuffer = direct(elements(maskShape) * 4);
        logitsBuffer = direct(elements(logitsShape) * 4);
        FloatBuffer mask = maskBuffer.asFloatBuffer();
        for (int i = 0; i < sequenceLength; i++) {
            for (int j = 0; j < sequenceLength; j++) mask.put(j > i ? MASK_DENY : MASK_ALLOW);
        }
    }

    private void encode(@NonNull float[][] mel) {
        Interpreter runner = interpreter;
        if (runner == null || melBuffer == null || featuresBuffer == null) throw new IllegalStateException("STT runtime is not loaded.");
        melBuffer.rewind();
        FloatBuffer floats = melBuffer.asFloatBuffer();
        for (float[] row : mel) floats.put(row, 0, frames);
        melBuffer.rewind();
        featuresBuffer.rewind();
        HashMap<String, Object> inputs = new HashMap<>();
        inputs.put(encodeInput, melBuffer);
        HashMap<String, Object> outputs = new HashMap<>();
        outputs.put(encodeOutput, featuresBuffer);
        runner.runSignature(inputs, outputs, SIGNATURE_ENCODE);
    }

    /** One decoder run over the current features; the logits at slot {@code filled − 1}. */
    @NonNull
    private float[] decodeStep(@NonNull int[] tokens, int filled) {
        Interpreter runner = interpreter;
        if (runner == null || featuresBuffer == null || tokensBuffer == null || maskBuffer == null || logitsBuffer == null) {
            throw new IllegalStateException("STT runtime is not loaded.");
        }
        tokensBuffer.rewind();
        IntBuffer ints = tokensBuffer.asIntBuffer();
        ints.put(tokens, 0, sequenceLength);
        tokensBuffer.rewind();
        featuresBuffer.rewind();
        maskBuffer.rewind();
        logitsBuffer.rewind();
        HashMap<String, Object> inputs = new HashMap<>();
        inputs.put(decodeAudioInput, featuresBuffer);
        inputs.put(decodeTokensInput, tokensBuffer);
        inputs.put(decodeMaskInput, maskBuffer);
        HashMap<String, Object> outputs = new HashMap<>();
        outputs.put(decodeOutput, logitsBuffer);
        runner.runSignature(inputs, outputs, SIGNATURE_DECODE);
        float[] logits = new float[vocabularySize];
        logitsBuffer.position((filled - 1) * vocabularySize * 4);
        logitsBuffer.asFloatBuffer().get(logits, 0, vocabularySize);
        logitsBuffer.rewind();
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
    private static ByteBuffer direct(int bytes) {
        return ByteBuffer.allocateDirect(bytes).order(ByteOrder.nativeOrder());
    }

    private static int elements(@NonNull int[] shape) {
        int count = 1;
        for (int dim : shape) count *= Math.max(1, dim);
        return count;
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
        if (interpreter != null) {
            interpreter.close();
            interpreter = null;
        }
        if (xnnpackDelegate != null) {
            xnnpackDelegate.close();
            xnnpackDelegate = null;
        }
        tokenizer = null;
        if (loadedModelId != null) residency.deregister(TaiResidency.Kind.STT, loadedModelId);
        loadedModelId = null;
        loadedModelPath = null;
        melBuffer = featuresBuffer = tokensBuffer = maskBuffer = logitsBuffer = null;
        frames = sequenceLength = vocabularySize = 0;
    }
}
