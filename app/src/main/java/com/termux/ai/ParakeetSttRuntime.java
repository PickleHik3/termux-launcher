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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;

/**
 * On-device speech-to-text on NVIDIA Parakeet TDT 0.6B v3 as Google converted it for LiteRT
 * ({@code litert-community/parakeet-tdt-0.6b-v3}, {@code parakeet_tdt_0.6b_v3_5s_i8_stateful.tflite}):
 * one {@link Interpreter} with an {@code encode} signature (NeMo log-mel {@code [1, 128, 500]} →
 * encoder output {@code [1, 1024, 63]}), a stateless {@code decode} (encoder output, a 4-slot
 * token sequence, zero LSTM state → logits {@code [1, 63, 4, 8198]} and the state) and a stateful
 * {@code decode_1} (one token and the carried state). The front end is {@link ParakeetFeatures},
 * the loop {@link ParakeetTdtDecoder}, the vocabulary {@link ParakeetTokenizer}; the graph takes
 * exactly 5 s, so longer audio is cut into 5 s pieces at the quietest 30 ms frame of each piece's
 * last second ({@link WhisperSegmenter#splitPoints}) and the texts joined with a space. The port
 * of {@code scripts/parakeet_replay_server.py}, which the replay rig measured against Whisper.
 *
 * <p>v3 detects the language itself and has no prompt, so {@code language} and {@code biasText}
 * are accepted and ignored ({@code biased} is always false). CPU only, XNNPACK through
 * {@link TaiXnnpackDelegate}, {@code min(4, cores)} threads, like {@link WhisperSttRuntime}: on
 * pong the encoder takes ~170 ms per 5 s window and a {@code decode_1} step ~1 ms, at +1.2 GB RSS.
 *
 * <p>Locking and residency follow {@link WhisperSttRuntime}: transcribe, warm and close serialize
 * on this object's monitor; the graph is registered as {@link TaiResidency.Kind#STT} after the
 * load ({@link TaiResidency#sttEstimateBytes} until the load meter has measured it), busy while
 * transcribing, deregistered on close.
 */
final class ParakeetSttRuntime implements SttRuntime {
    static final String SIGNATURE_ENCODE = "encode";
    static final String SIGNATURE_DECODE = "decode";
    static final String SIGNATURE_DECODE_STATEFUL = "decode_1";
    static final String TOKENIZER_FILE = "tokenizer.json";
    static final String RUNTIME_NAME = "parakeet-tdt";
    /** The architecture the catalog gives Parakeet entries; the router picks this runtime by it. */
    static final String ARCHITECTURE = "parakeet-tdt";

    private final TaiResidency residency;
    /** For the load meter and its history; {@code null} in the router's test seam (nothing is measured). */
    @Nullable private final Context appContext;
    @Nullable private Interpreter interpreter;
    @Nullable private TaiXnnpackDelegate xnnpackDelegate;
    @Nullable private ParakeetTokenizer tokenizer;
    @Nullable private String loadedModelId;
    @Nullable private String loadedModelPath;
    private int encoderFrames;
    private int vocabularySize;
    private String encodeInput = "";
    private String encodeOutput = "";
    @Nullable private Signature decodeSignature;
    @Nullable private Signature statefulSignature;
    @Nullable private ByteBuffer melBuffer;
    @Nullable private ByteBuffer encoderBuffer;
    @Nullable private ByteBuffer stateHIn, stateCIn, stateHOut, stateCOut;

    ParakeetSttRuntime(@NonNull TaiResidency residency, @Nullable Context context) {
        this.residency = residency;
        this.appContext = context == null ? null : context.getApplicationContext();
    }

    @Override
    public synchronized boolean isLoaded(@NonNull String modelId) {
        return interpreter != null && modelId.equals(loadedModelId);
    }

    @NonNull
    @Override
    public synchronized JSONObject warm(@NonNull TaiModelSpec spec) throws JSONException {
        JSONObject refusal = checkFiles(spec);
        if (refusal != null) return refusal;
        long started = System.currentTimeMillis();
        boolean wasLoaded = isLoaded(spec.id);
        try {
            ensureLoaded(spec);
        } catch (Throwable t) {
            return error(500, "stt_load_failed", "Parakeet model failed to load: " + message(t));
        }
        JSONObject response = new JSONObject();
        response.put("ok", true);
        response.put("model", spec.id);
        response.put("loaded", true);
        response.put("alreadyLoaded", wasLoaded);
        response.put("windowSeconds", ParakeetFeatures.WINDOW_SECONDS);
        response.put("loadMs", System.currentTimeMillis() - started);
        response.put("_runtime", RUNTIME_NAME);
        return response;
    }

    @NonNull
    @Override
    public synchronized JSONObject transcribe(@NonNull TaiModelSpec spec, @NonNull File audioFile,
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
            return error(500, "stt_load_failed", "Parakeet model failed to load: " + message(t));
        }
        long loadMs = System.currentTimeMillis() - loadStarted;
        ParakeetTokenizer vocabulary = tokenizer;
        if (vocabulary == null) return error(500, "stt_load_failed", "Parakeet tokenizer is not loaded.");
        List<int[]> pieces = pieces(audio);
        StringBuilder text = new StringBuilder();
        JSONArray segments = new JSONArray();
        long melMs = 0L, encodeMs = 0L, decodeMs = 0L;
        int steps = 0;
        residency.setBusy(TaiResidency.Kind.STT, spec.id, true);
        try {
            for (int[] bounds : pieces) {
                if (bounds[1] <= bounds[0]) continue;
                float[] piece = Arrays.copyOfRange(audio, bounds[0], bounds[1]);
                long t0 = System.nanoTime();
                float[][] features = ParakeetFeatures.features(piece);
                long t1 = System.nanoTime();
                encode(features);
                long t2 = System.nanoTime();
                ParakeetTdtDecoder.Result result = ParakeetTdtDecoder.greedy(new GraphAdapter(), encoderFrames);
                long t3 = System.nanoTime();
                melMs += (t1 - t0) / 1_000_000L;
                encodeMs += (t2 - t1) / 1_000_000L;
                decodeMs += (t3 - t2) / 1_000_000L;
                steps += result.steps;
                String segmentText = vocabulary.decode(result.tokens);
                if (segmentText.isEmpty()) continue;
                if (text.length() > 0) text.append(' ');
                text.append(segmentText);
                JSONObject segment = new JSONObject();
                segment.put("text", segmentText);
                segment.put("start", bounds[0] / (double) ParakeetFeatures.SAMPLE_RATE);
                segment.put("end", bounds[1] / (double) ParakeetFeatures.SAMPLE_RATE);
                segment.put("tokens", result.tokens.length);
                segments.put(segment);
            }
        } catch (Throwable t) {
            return error(500, "stt_inference_failed", "Parakeet inference failed: " + message(t));
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
        // v3 picks the language itself; the request's hint is echoed, never enforced.
        response.put("language", language == null || language.trim().isEmpty() ? "auto" : language.trim().toLowerCase(Locale.ROOT));
        response.put("duration", audio.length / (double) ParakeetFeatures.SAMPLE_RATE);
        response.put("segments", segments);
        response.put("windowSeconds", ParakeetFeatures.WINDOW_SECONDS);
        response.put("biased", false);
        response.put("timings", timings);
        response.put("_backend", TaiModelSpec.BACKEND_LITERT_LM);
        response.put("_runtime", RUNTIME_NAME);
        return response;
    }

    /**
     * {@code [start, end)} sample ranges of at most one window each, cut at the quietest 30 ms
     * frame in the last second before each window boundary — the reference server's {@code split()},
     * which is {@link WhisperSegmenter#splitPoints} at the 5 s window. No piece is dropped or
     * padded here: the features pad every piece to the window themselves.
     */
    @NonNull
    static List<int[]> pieces(@NonNull float[] audio) {
        return new ArrayList<>(WhisperSegmenter.splitPoints(audio, ParakeetFeatures.WINDOW_SAMPLES));
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
                "Parakeet requires tokenizer.json next to the .tflite graph; re-download the speech model.");
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
        tokenizer = ParakeetTokenizer.fromFile(tokenizerFile);
        int threads = Math.max(1, Math.min(4, Runtime.getRuntime().availableProcessors()));
        // litert 1.4.2's own XNNPACK delegate never gets worker threads (see WhisperSttRuntime);
        // the JNI shim builds a real one, and only the interpreter's own thread count falls back.
        xnnpackDelegate = TaiXnnpackDelegate.create(threads);
        Interpreter.Options options = xnnpackDelegate != null
            ? new Interpreter.Options().setNumThreads(threads).setUseXNNPACK(false).addDelegate(xnnpackDelegate)
            : new Interpreter.Options().setNumThreads(threads).setUseXNNPACK(true);
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
        loadedModelId = spec.id;
        loadedModelPath = modelPath;
        residency.register(TaiResidency.Entry.stt(spec, ParakeetFeatures.WINDOW_SECONDS)
            .withMeasured(measured >= 0L ? measured : null));
        if (measured >= 0L && appContext != null) {
            TaiRuntimeHistory.recordMeasuredLoad(appContext, spec, TaiDeviceCapabilities.detect(appContext),
                TaiModelSpec.BACKEND_LITERT_LM, "cpu", 0, measured);
        }
    }

    /**
     * Reads the shapes off the three signatures and picks each decoder input by what it is — the
     * INT32 rank-2 tensor is the token sequence, the float tensor shaped like the encoder output
     * the encoder input, the remaining two the LSTM state ({@code h} then {@code c}, in name order,
     * as the outputs are {@code output_1} then {@code output_2}) — never by position alone.
     */
    private void bindSignatures(@NonNull Interpreter interpreter) {
        boolean encode = false, decode = false, stateful = false;
        for (String key : interpreter.getSignatureKeys()) {
            encode |= SIGNATURE_ENCODE.equals(key);
            decode |= SIGNATURE_DECODE.equals(key);
            stateful |= SIGNATURE_DECODE_STATEFUL.equals(key);
        }
        if (!encode || !decode || !stateful) {
            throw new IllegalStateException("Graph lacks the encode/decode/decode_1 signatures: " + Arrays.toString(interpreter.getSignatureKeys()));
        }
        String[] encodeInputs = interpreter.getSignatureInputs(SIGNATURE_ENCODE);
        String[] encodeOutputs = interpreter.getSignatureOutputs(SIGNATURE_ENCODE);
        if (encodeInputs.length != 1 || encodeOutputs.length != 1) throw new IllegalStateException("Unexpected encode signature arity");
        encodeInput = encodeInputs[0];
        encodeOutput = encodeOutputs[0];
        int[] melShape = interpreter.getInputTensorFromSignature(encodeInput, SIGNATURE_ENCODE).shape();
        if (melShape.length != 3 || melShape[1] != ParakeetFeatures.N_MELS || melShape[2] != ParakeetFeatures.FRAMES) {
            throw new IllegalStateException("Unexpected encode input shape " + Arrays.toString(melShape));
        }
        int[] encoderShape = interpreter.getOutputTensorFromSignature(encodeOutput, SIGNATURE_ENCODE).shape();
        if (encoderShape.length != 3) throw new IllegalStateException("Unexpected encode output shape " + Arrays.toString(encoderShape));
        encoderFrames = encoderShape[2];
        this.decodeSignature = Signature.bind(interpreter, SIGNATURE_DECODE, encoderShape, encoderFrames);
        this.statefulSignature = Signature.bind(interpreter, SIGNATURE_DECODE_STATEFUL, encoderShape, encoderFrames);
        if (this.statefulSignature.slots != 1) throw new IllegalStateException("decode_1 should take one token, takes " + this.statefulSignature.slots);
        if (this.decodeSignature.vocabulary != this.statefulSignature.vocabulary) throw new IllegalStateException("decode and decode_1 disagree on the vocabulary");
        vocabularySize = this.decodeSignature.vocabulary;
        if (vocabularySize != ParakeetTdtDecoder.BLANK + 1 + ParakeetTdtDecoder.NUM_DURATIONS) {
            throw new IllegalStateException("Unexpected logits width " + vocabularySize);
        }
        if (!Arrays.equals(this.decodeSignature.stateShape, this.statefulSignature.stateShape)) throw new IllegalStateException("decode and decode_1 disagree on the state shape");
        // Buffers sized exactly to the tensors, allocated once and rewound before every run; the
        // encoder's output buffer is fed straight into both decoders without a copy.
        melBuffer = direct(ParakeetFeatures.N_MELS * ParakeetFeatures.FRAMES * 4);
        encoderBuffer = direct(elements(encoderShape) * 4);
        int stateBytes = elements(this.decodeSignature.stateShape) * 4;
        stateHIn = direct(stateBytes);
        stateCIn = direct(stateBytes);
        stateHOut = direct(stateBytes);
        stateCOut = direct(stateBytes);
    }

    private void encode(@NonNull float[][] features) {
        Interpreter runner = interpreter;
        if (runner == null || melBuffer == null || encoderBuffer == null || stateHIn == null || stateCIn == null) {
            throw new IllegalStateException("STT runtime is not loaded.");
        }
        melBuffer.rewind();
        FloatBuffer floats = melBuffer.asFloatBuffer();
        for (float[] row : features) floats.put(row, 0, ParakeetFeatures.FRAMES);
        melBuffer.rewind();
        encoderBuffer.rewind();
        HashMap<String, Object> inputs = new HashMap<>();
        inputs.put(encodeInput, melBuffer);
        HashMap<String, Object> outputs = new HashMap<>();
        outputs.put(encodeOutput, encoderBuffer);
        runner.runSignature(inputs, outputs, SIGNATURE_ENCODE);
        // A fresh window starts from a zero LSTM state.
        zero(stateHIn);
        zero(stateCIn);
    }

    /** {@link ParakeetTdtDecoder.Graph} over the loaded interpreter and the current encoder output. */
    private final class GraphAdapter implements ParakeetTdtDecoder.Graph {
        @Override
        public int slots() {
            Signature signature = decodeSignature;
            return signature == null ? 0 : signature.slots;
        }

        @NonNull
        @Override
        public float[] decode(@NonNull int[] tokens, int t, int slot) {
            Signature signature = decodeSignature;
            if (signature == null) throw new IllegalStateException("STT runtime is not loaded.");
            return run(signature, tokens, t, slot);
        }

        @NonNull
        @Override
        public float[] decodeStateful(int token, int t) {
            Signature signature = statefulSignature;
            if (signature == null) throw new IllegalStateException("STT runtime is not loaded.");
            return run(signature, new int[] {token}, t, 0);
        }

        @Override
        public void adoptState() {
            if (stateHIn == null || stateCIn == null || stateHOut == null || stateCOut == null) throw new IllegalStateException("STT runtime is not loaded.");
            copy(stateHOut, stateHIn);
            copy(stateCOut, stateCIn);
        }
    }

    /** One decoder run over the current encoder output; the logits at {@code (t, slot)}. */
    @NonNull
    private float[] run(@NonNull Signature signature, @NonNull int[] tokens, int t, int slot) {
        Interpreter runner = interpreter;
        if (runner == null || encoderBuffer == null || stateHIn == null || stateCIn == null || stateHOut == null || stateCOut == null) {
            throw new IllegalStateException("STT runtime is not loaded.");
        }
        if (tokens.length != signature.slots) throw new IllegalArgumentException("Expected " + signature.slots + " tokens, got " + tokens.length);
        signature.tokensBuffer.rewind();
        signature.tokensBuffer.asIntBuffer().put(tokens, 0, signature.slots);
        signature.tokensBuffer.rewind();
        encoderBuffer.rewind();
        stateHIn.rewind();
        stateCIn.rewind();
        stateHOut.rewind();
        stateCOut.rewind();
        signature.logitsBuffer.rewind();
        HashMap<String, Object> inputs = new HashMap<>();
        inputs.put(signature.encoderInput, encoderBuffer);
        inputs.put(signature.tokensInput, signature.tokensBuffer);
        inputs.put(signature.stateHInput, stateHIn);
        inputs.put(signature.stateCInput, stateCIn);
        HashMap<String, Object> outputs = new HashMap<>();
        outputs.put(signature.logitsOutput, signature.logitsBuffer);
        outputs.put(signature.stateHOutput, stateHOut);
        outputs.put(signature.stateCOutput, stateCOut);
        runner.runSignature(inputs, outputs, signature.key);
        float[] logits = new float[vocabularySize];
        signature.logitsBuffer.position(((t * signature.slots) + slot) * vocabularySize * 4);
        signature.logitsBuffer.asFloatBuffer().get(logits, 0, vocabularySize);
        signature.logitsBuffer.rewind();
        return logits;
    }

    /** One of the two decoder signatures: its tensor names, its slot count and its own buffers. */
    private static final class Signature {
        final String key;
        final String encoderInput, tokensInput, stateHInput, stateCInput;
        final String logitsOutput, stateHOutput, stateCOutput;
        final int slots;
        final int vocabulary;
        final int[] stateShape;
        final ByteBuffer tokensBuffer;
        final ByteBuffer logitsBuffer;

        private Signature(String key, String encoderInput, String tokensInput, String stateHInput, String stateCInput,
                          String logitsOutput, String stateHOutput, String stateCOutput,
                          int slots, int vocabulary, int[] stateShape, int[] logitsShape) {
            this.key = key;
            this.encoderInput = encoderInput; this.tokensInput = tokensInput;
            this.stateHInput = stateHInput; this.stateCInput = stateCInput;
            this.logitsOutput = logitsOutput; this.stateHOutput = stateHOutput; this.stateCOutput = stateCOutput;
            this.slots = slots; this.vocabulary = vocabulary; this.stateShape = stateShape;
            tokensBuffer = direct(slots * 4);
            logitsBuffer = direct(elements(logitsShape) * 4);
        }

        @NonNull
        static Signature bind(@NonNull Interpreter interpreter, @NonNull String key, @NonNull int[] encoderShape, int encoderFrames) {
            String encoderInput = null, tokensInput = null;
            List<String> stateInputs = new ArrayList<>();
            int slots = 0;
            int[] stateShape = null;
            for (String name : interpreter.getSignatureInputs(key)) {
                Tensor tensor = interpreter.getInputTensorFromSignature(name, key);
                if (tensor.dataType() == DataType.INT32 && tensor.numDimensions() == 2) {
                    tokensInput = name;
                    slots = tensor.shape()[1];
                } else if (Arrays.equals(tensor.shape(), encoderShape) && encoderInput == null) {
                    encoderInput = name;
                } else {
                    stateInputs.add(name);
                    if (stateShape == null) stateShape = tensor.shape();
                    else if (!Arrays.equals(stateShape, tensor.shape())) throw new IllegalStateException(key + ": state inputs differ in shape");
                }
            }
            if (encoderInput == null || tokensInput == null || stateInputs.size() != 2 || slots < 1 || stateShape == null) {
                throw new IllegalStateException(key + ": could not identify the inputs by shape: " + Arrays.toString(interpreter.getSignatureInputs(key)));
            }
            String logitsOutput = null;
            List<String> stateOutputs = new ArrayList<>();
            int[] logitsShape = null;
            for (String name : interpreter.getSignatureOutputs(key)) {
                Tensor tensor = interpreter.getOutputTensorFromSignature(name, key);
                if (tensor.numDimensions() == 4) {
                    logitsOutput = name;
                    logitsShape = tensor.shape();
                } else {
                    stateOutputs.add(name);
                    if (!Arrays.equals(stateShape, tensor.shape())) throw new IllegalStateException(key + ": state output " + name + " has shape " + Arrays.toString(tensor.shape()));
                }
            }
            if (logitsOutput == null || logitsShape == null || stateOutputs.size() != 2) {
                throw new IllegalStateException(key + ": could not identify the outputs by shape: " + Arrays.toString(interpreter.getSignatureOutputs(key)));
            }
            if (logitsShape[1] != encoderFrames || logitsShape[2] != slots) {
                throw new IllegalStateException(key + ": unexpected logits shape " + Arrays.toString(logitsShape));
            }
            // args_2 / args_3 and output_1 / output_2: h first, c second, on both sides.
            java.util.Collections.sort(stateInputs);
            java.util.Collections.sort(stateOutputs);
            return new Signature(key, encoderInput, tokensInput, stateInputs.get(0), stateInputs.get(1),
                logitsOutput, stateOutputs.get(0), stateOutputs.get(1), slots, logitsShape[3], stateShape, logitsShape);
        }
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

    private static void zero(@NonNull ByteBuffer buffer) {
        buffer.rewind();
        while (buffer.hasRemaining()) buffer.put((byte) 0);
        buffer.rewind();
    }

    private static void copy(@NonNull ByteBuffer from, @NonNull ByteBuffer to) {
        from.rewind();
        to.rewind();
        to.put(from);
        from.rewind();
        to.rewind();
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
        decodeSignature = statefulSignature = null;
        melBuffer = encoderBuffer = stateHIn = stateCIn = stateHOut = stateCOut = null;
        encoderFrames = vocabularySize = 0;
    }
}
