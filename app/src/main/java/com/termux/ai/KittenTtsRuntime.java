package com.termux.ai;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import org.json.JSONException;
import org.json.JSONObject;
import org.tensorflow.lite.Interpreter;
import org.tensorflow.lite.Tensor;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * On-device speech with KittenTTS nano 0.8 ({@code litert-community/kitten-tts-nano-0.8}, StyleTTS2
 * + iSTFTNet, 15M parameters, 24 kHz, Apache-2.0): three fp32 LiteRT graphs with a dynamic
 * sequence length, driven by the classic {@link Interpreter} API, and the port of the host glue in
 * {@code scripts/tts-eval/engines.py} and Google's {@code KittenSynthesizer.kt}.
 *
 * <pre>
 *   ids [1,N] ([0] + G2P ids + [0]), style [1,256], speed [1]
 *     → predictor → t_en [1,N,128], durations [N], d [1,N,256]
 *     → repeat every row by its duration → en [1,T,256], asr [1,T,128]
 *     → prosody   → f0 [1,2T], noise [1,2T], harmonics [1,120T+1,22]
 *     → vocoder   → waveform [1,600T] at 24 kHz, minus the last 5000 samples
 * </pre>
 *
 * The style row is {@code voices[voice][min(len(text), 399)]}: KittenTTS conditions on the text
 * length, one row per character count. The predictor and prosody graphs keep their fused LSTM
 * state in variable tensors that survive {@code invoke()}, so every run resets them first; without
 * that the second sentence starts from the first one's final state and its durations come out
 * wrong. Inputs are resized to each sentence's length with {@code resizeInput} and
 * {@code allocateTensors}; outputs are read from the tensors after the run, since the waveform's
 * shape only exists once the vocoder has run.
 *
 * <p>Runs on the CPU through {@link TaiXnnpackDelegate} ({@code min(4, cores)} threads), with the
 * stock interpreter as a fallback: if a delegated run ever fails for anything but a cancellation,
 * the graphs are reloaded without the delegate and the sentence retried once. Locking and residency
 * follow {@link ParakeetSttRuntime}: synthesize, warm and close serialise on this object's monitor;
 * {@link #interrupt} does not take it, so a stop reaches a running graph.
 */
final class KittenTtsRuntime implements TtsRuntime {
    private static final String TAG = "TaiTts";
    static final String RUNTIME_NAME = "kitten-tts";
    /** The architecture the catalogue gives KittenTTS entries. */
    static final String ARCHITECTURE = "kitten-tts";
    static final int SAMPLE_RATE = 24_000;

    static final String PREDICTOR_FILE = "kitten_predictor.tflite";
    static final String PROSODY_FILE = "kitten_prosody.tflite";
    static final String VOCODER_FILE = "kitten_vocoder.tflite";
    static final String VOICES_FILE = "voices.npz";
    static final String PHONEMIZER_FILE = "dp_g2p_matcha_fp16.tflite";
    static final String DICTIONARY_FILE = "g2p_dict.txt.gz";
    static final String PHONEMIZER_META_FILE = "g2p_meta.json";
    /** Every file next to the predictor that a load needs. */
    static final String[] SIDECAR_FILES = {PROSODY_FILE, VOCODER_FILE, VOICES_FILE, PHONEMIZER_FILE,
        DICTIONARY_FILE, PHONEMIZER_META_FILE};

    static final int STYLE_DIM = 256;
    static final int STYLE_ROWS = 400;
    static final int D_DIM = 256;
    static final int ASR_DIM = 128;
    static final int HAR_DIM = 22;
    /** The pip package trims this many samples off the end of every chunk: the vocoder's tail. */
    static final int TAIL_TRIM = 5000;
    static final int MIN_SAMPLES = 1200;

    private final TaiResidency residency;
    @Nullable private final Context appContext;

    @Nullable private volatile Graph predictor, prosody, vocoder;
    @Nullable private TaiDeepPhonemizer phonemizer;
    @Nullable private TaiG2p g2p;
    @Nullable private String loadedModelId;
    @Nullable private String loadedModelPath;
    private final Map<String, TaiNpy.FloatArray> voices = new HashMap<>();
    /** Cleared once a delegated run fails, so the reload and every later one use the stock interpreter. */
    private boolean useDelegate = true;
    /**
     * Set by {@link #interrupt} (a stop, an unload, a memory release) and cleared when the next
     * utterance starts. A graph interrupted mid-run throws, and this is what tells that apart from
     * a delegate that genuinely failed, which would otherwise cost XNNPACK for the rest of the process.
     */
    private volatile boolean interruptRequested;

    KittenTtsRuntime(@NonNull TaiResidency residency, @Nullable Context context) {
        this.residency = residency;
        this.appContext = context == null ? null : context.getApplicationContext();
    }

    @Override
    public int sampleRate() {
        return SAMPLE_RATE;
    }

    @Override
    public synchronized boolean isLoaded(@NonNull String modelId) {
        return predictor != null && modelId.equals(loadedModelId);
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
            TaiG2p loadedG2p = g2p;
            if (loadedG2p != null) loadedG2p.preload();
        } catch (Throwable t) {
            return error(500, "tts_load_failed", "Speech model failed to load: " + message(t));
        }
        JSONObject response = new JSONObject();
        response.put("ok", true);
        response.put("model", spec.id);
        response.put("loaded", true);
        response.put("alreadyLoaded", wasLoaded);
        response.put("loadMs", System.currentTimeMillis() - started);
        response.put("_runtime", RUNTIME_NAME);
        return response;
    }

    @NonNull
    @Override
    public synchronized JSONObject synthesize(@NonNull TaiModelSpec spec, @NonNull String text, @NonNull String voice,
                                              float speed, @NonNull Sink sink, @NonNull Cancellation requested)
            throws JSONException {
        interruptRequested = false;
        Cancellation cancellation = () -> requested.isCancelled() || interruptRequested;
        JSONObject refusal = checkFiles(spec);
        if (refusal != null) return refusal;
        long started = System.currentTimeMillis();
        try {
            ensureLoaded(spec);
        } catch (Throwable t) {
            return error(500, "tts_load_failed", "Speech model failed to load: " + message(t));
        }
        long loadMs = System.currentTimeMillis() - started;
        TaiNpy.FloatArray table;
        try {
            table = voiceTable(voice);
        } catch (IOException | IllegalArgumentException e) {
            return error(500, "tts_voice_unreadable", "The voice " + voice + " could not be read: " + message(e));
        }
        TaiG2p phonemes = g2p;
        if (phonemes == null) return error(500, "tts_load_failed", "Pronunciation data is not loaded.");
        float effectiveSpeed = speed * TaiTtsVoices.speedPrior(voice);
        List<String> chunks = TaiTtsChunker.chunks(text);
        float[] style = new float[STYLE_DIM];
        long g2pMs = 0L, synthMs = 0L, firstAudioMs = -1L;
        long samples = 0L;
        int spoken = 0;
        int neuralWords = 0;
        boolean cancelled = false;
        residency.setBusy(TaiResidency.Kind.TTS, spec.id, true);
        try {
            for (int i = 0; i < chunks.size(); i++) {
                if (cancellation.isCancelled()) {
                    cancelled = true;
                    break;
                }
                long t0 = System.nanoTime();
                TaiG2p.Result g = phonemes.phonemize(chunks.get(i));
                long t1 = System.nanoTime();
                g2pMs += (t1 - t0) / 1_000_000L;
                neuralWords += g.neuralWords;
                if (g.ids.length == 0) continue;
                table.row(styleRow(g.text, table.shape[0]), style);
                float[] audio;
                try {
                    audio = synthesizeChunk(g.ids, style, effectiveSpeed, cancellation);
                } catch (RuntimeException e) {
                    if (cancellation.isCancelled()) {
                        cancelled = true;
                        break;
                    }
                    if (!useDelegate) throw e;
                    // A delegated graph that cannot take this shape: reload on the stock
                    // interpreter and give the sentence one more try.
                    Log.w(TAG, "delegated synthesis failed, retrying without XNNPACK: " + message(e));
                    useDelegate = false;
                    closeGraphs();
                    loadGraphs(new File(spec.localPath));
                    audio = synthesizeChunk(g.ids, style, effectiveSpeed, cancellation);
                }
                synthMs += (System.nanoTime() - t1) / 1_000_000L;
                if (audio == null) {
                    cancelled = true;
                    break;
                }
                if (audio.length == 0) continue;
                if (firstAudioMs < 0) firstAudioMs = System.currentTimeMillis() - started;
                samples += audio.length;
                spoken++;
                if (!sink.onAudio(audio, audio.length, i)) {
                    cancelled = true;
                    break;
                }
            }
        } catch (Throwable t) {
            if (cancellation.isCancelled()) {
                cancelled = true;
            } else {
                return error(500, "tts_inference_failed", "Speech synthesis failed: " + message(t));
            }
        } finally {
            residency.setBusy(TaiResidency.Kind.TTS, spec.id, false);
        }
        JSONObject timings = new JSONObject();
        timings.put("loadMs", loadMs);
        timings.put("firstAudioMs", firstAudioMs);
        timings.put("g2pMs", g2pMs);
        timings.put("synthMs", synthMs);
        JSONObject response = new JSONObject();
        response.put("ok", true);
        response.put("model", spec.id);
        response.put("voice", voice);
        response.put("speed", speed);
        response.put("sampleRate", SAMPLE_RATE);
        response.put("sentences", spoken);
        response.put("chunks", chunks.size());
        response.put("samples", samples);
        response.put("audioSeconds", samples / (double) SAMPLE_RATE);
        response.put("neuralWords", neuralWords);
        response.put("cancelled", cancelled);
        response.put("timings", timings);
        response.put("_runtime", RUNTIME_NAME);
        return response;
    }

    /** One chunk's waveform, trimmed; {@code null} when cancelled between graphs. */
    @Nullable
    private float[] synthesizeChunk(@NonNull int[] symbolIds, @NonNull float[] style, float speed,
                                    @NonNull Cancellation cancellation) {
        Graph predictorGraph = predictor, prosodyGraph = prosody, vocoderGraph = vocoder;
        if (predictorGraph == null || prosodyGraph == null || vocoderGraph == null) {
            throw new IllegalStateException("Speech model is not loaded.");
        }
        int tokens = symbolIds.length + 2;
        int[] ids = new int[tokens];
        System.arraycopy(symbolIds, 0, ids, 1, symbolIds.length);

        // Predictor: per-token features and integer durations.
        ByteBuffer idsIn = predictorGraph.input("input_ids", tokens * 4);
        idsIn.asIntBuffer().put(ids);
        ByteBuffer styleIn = predictorGraph.input("style", STYLE_DIM * 4);
        styleIn.asFloatBuffer().put(style);
        ByteBuffer speedIn = predictorGraph.input("speed", 4);
        speedIn.asFloatBuffer().put(speed);
        predictorGraph.run(new int[][] {{1, tokens}}, new String[] {"input_ids"});
        float[] tEn = predictorGraph.floats(":0");
        int[] durations = predictorGraph.ints(":1");
        float[] d = predictorGraph.floats(":2");
        if (durations.length != tokens || tEn.length != tokens * ASR_DIM || d.length != tokens * D_DIM) {
            throw new IllegalStateException("Unexpected predictor output: " + durations.length + " durations for " + tokens + " tokens");
        }
        int frames = clampDurations(durations);
        if (frames == 0) return new float[0];
        if (cancellation.isCancelled()) return null;

        // Prosody over the length-regulated features.
        ByteBuffer enIn = prosodyGraph.input("en", frames * D_DIM * 4);
        repeatRowsInto(d, D_DIM, durations, enIn.asFloatBuffer());
        prosodyGraph.input("style", STYLE_DIM * 4).asFloatBuffer().put(style);
        prosodyGraph.run(new int[][] {{1, frames, D_DIM}}, new String[] {"en"});
        if (cancellation.isCancelled()) return null;

        // Vocoder: aligned text features plus prosody to the waveform. The prosody outputs are
        // copied tensor to tensor as raw bytes (both sides native order): the harmonics alone are
        // 120 × 22 floats per frame, about 4 MB for a long sentence, and need no Java array.
        ByteBuffer f0 = prosodyGraph.bytes(":0");
        ByteBuffer noise = prosodyGraph.bytes(":1");
        ByteBuffer harmonics = prosodyGraph.bytes(":2");
        int f0Length = f0.remaining() / 4;
        int noiseLength = noise.remaining() / 4;
        int harmonicRows = harmonics.remaining() / 4 / HAR_DIM;
        if (harmonics.remaining() != harmonicRows * HAR_DIM * 4) throw new IllegalStateException("Harmonics are not a multiple of " + HAR_DIM);
        ByteBuffer asrIn = vocoderGraph.input("asr", frames * ASR_DIM * 4);
        repeatRowsInto(tEn, ASR_DIM, durations, asrIn.asFloatBuffer());
        vocoderGraph.input("f0", f0.remaining()).put(f0);
        vocoderGraph.input("n", noise.remaining()).put(noise);
        vocoderGraph.input("har", harmonics.remaining()).put(harmonics);
        vocoderGraph.input("style", STYLE_DIM * 4).asFloatBuffer().put(style);
        vocoderGraph.run(new int[][] {{1, frames, ASR_DIM}, {1, f0Length}, {1, noiseLength},
            {1, harmonicRows, HAR_DIM}}, new String[] {"asr", "f0", "n", "har"});
        float[] waveform = vocoderGraph.floats("");
        return trimTail(waveform);
    }

    // ---- pure pieces, unit-tested ----

    /** Clamps negative durations to zero in place (as the prototype does) and returns the frame count. */
    @VisibleForTesting
    static int clampDurations(@NonNull int[] durations) {
        int total = 0;
        for (int i = 0; i < durations.length; i++) {
            if (durations[i] < 0) durations[i] = 0;
            total += durations[i];
        }
        return total;
    }

    /** {@code np.repeat(x[0], durations, axis=0)} for a row-major {@code [rows, columns]} array. */
    @NonNull
    @VisibleForTesting
    static float[] repeatRows(@NonNull float[] source, int columns, @NonNull int[] durations) {
        int frames = 0;
        for (int duration : durations) frames += Math.max(0, duration);
        float[] out = new float[frames * columns];
        repeatRowsInto(source, columns, durations, FloatBuffer.wrap(out));
        return out;
    }

    static void repeatRowsInto(@NonNull float[] source, int columns, @NonNull int[] durations, @NonNull FloatBuffer out) {
        for (int row = 0; row < durations.length; row++) {
            for (int r = 0; r < durations[row]; r++) out.put(source, row * columns, columns);
        }
    }

    /** The waveform minus the vocoder tail, never shorter than {@link #MIN_SAMPLES} (or the whole of a shorter one), clipped. */
    @NonNull
    @VisibleForTesting
    static float[] trimTail(@NonNull float[] waveform) {
        int keep = Math.max(waveform.length - TAIL_TRIM, Math.min(MIN_SAMPLES, waveform.length));
        float[] out = new float[keep];
        for (int i = 0; i < keep; i++) {
            float value = waveform[i];
            out[i] = value > 1f ? 1f : (value < -1f ? -1f : value);
        }
        return out;
    }

    /** The style row for a chunk: its length in characters, capped at the table's last row. */
    @VisibleForTesting
    static int styleRow(@NonNull String chunkText, int rows) {
        return Math.min(chunkText.codePointCount(0, chunkText.length()), rows - 1);
    }

    // ---- loading ----

    @Nullable
    private JSONObject checkFiles(@NonNull TaiModelSpec spec) throws JSONException {
        if (spec.localPath == null || spec.localPath.trim().isEmpty()) {
            return error(404, "model_file_missing", "Speech model file is missing.");
        }
        File predictorFile = new File(spec.localPath);
        if (!predictorFile.isFile() || !predictorFile.canRead()) {
            return error(404, "model_file_not_readable", "Speech model file is missing or unreadable.");
        }
        List<String> missing = missingSidecars(predictorFile);
        if (!missing.isEmpty()) {
            return error(409, "tts_files_missing", "The voice model is incomplete (missing " + String.join(", ", missing)
                + "); download it again in Model centre.");
        }
        return null;
    }

    @NonNull
    static List<String> missingSidecars(@NonNull File predictorFile) {
        File dir = predictorFile.getParentFile();
        List<String> missing = new ArrayList<>();
        for (String name : SIDECAR_FILES) {
            File file = dir == null ? null : new File(dir, name);
            if (file == null || !file.isFile() || !file.canRead() || file.length() == 0L) missing.add(name);
        }
        return missing;
    }

    private void ensureLoaded(@NonNull TaiModelSpec spec) throws Exception {
        File predictorFile = new File(spec.localPath);
        String path = predictorFile.getAbsolutePath();
        if (predictor != null && g2p != null && path.equals(loadedModelPath)) return;
        close();
        File dir = predictorFile.getParentFile();
        if (dir == null) throw new IOException("Speech model has no directory");
        TaiLoadMeter meter = TaiLoadMeter.start(appContext);
        long measured;
        try {
            loadGraphs(predictorFile);
            phonemizer = new TaiDeepPhonemizer(new File(dir, PHONEMIZER_FILE), new File(dir, PHONEMIZER_META_FILE));
            File dictionary = new File(dir, DICTIONARY_FILE);
            g2p = new TaiG2p(() -> TaiG2pDictionary.loadGzip(dictionary), phonemizer);
        } catch (Exception | Error e) {
            close();
            throw e;
        } finally {
            measured = meter.stop();
        }
        loadedModelId = spec.id;
        loadedModelPath = path;
        residency.register(TaiResidency.Entry.tts(spec).withMeasured(measured >= 0L ? measured : null));
    }

    private void loadGraphs(@NonNull File predictorFile) throws IOException {
        File dir = predictorFile.getParentFile();
        int threads = Math.max(1, Math.min(4, Runtime.getRuntime().availableProcessors()));
        predictor = Graph.open(predictorFile, threads, useDelegate);
        prosody = Graph.open(new File(dir, PROSODY_FILE), threads, useDelegate);
        vocoder = Graph.open(new File(dir, VOCODER_FILE), threads, useDelegate);
        predictor.requireInputs("input_ids", "style", "speed");
        predictor.requireOutputs(":0", ":1", ":2");
        prosody.requireInputs("en", "style");
        prosody.requireOutputs(":0", ":1", ":2");
        vocoder.requireInputs("asr", "f0", "n", "har", "style");
    }

    @NonNull
    private TaiNpy.FloatArray voiceTable(@NonNull String voice) throws IOException {
        TaiNpy.FloatArray table = voices.get(voice);
        if (table != null) return table;
        String dir = loadedModelPath == null ? null : new File(loadedModelPath).getParent();
        if (dir == null) throw new IOException("Speech model is not loaded");
        table = TaiNpy.readFromNpz(new File(dir, VOICES_FILE), TaiTtsVoices.npyKey(voice));
        if (table.shape.length != 2 || table.shape[1] != STYLE_DIM || table.shape[0] < 1) {
            throw new IOException("Unexpected voice table shape " + java.util.Arrays.toString(table.shape));
        }
        voices.put(voice, table);
        return table;
    }

    @Override
    public void interrupt() {
        interruptRequested = true;
        for (Graph graph : new Graph[] {predictor, prosody, vocoder}) {
            if (graph != null) graph.cancel();
        }
    }

    private void closeGraphs() {
        for (Graph graph : new Graph[] {predictor, prosody, vocoder}) {
            if (graph != null) graph.close();
        }
        predictor = prosody = vocoder = null;
    }

    @Override
    public synchronized void close() {
        closeGraphs();
        if (phonemizer != null) {
            phonemizer.close();
            phonemizer = null;
        }
        g2p = null;
        voices.clear();
        if (loadedModelId != null) residency.deregister(TaiResidency.Kind.TTS, loadedModelId);
        loadedModelId = null;
        loadedModelPath = null;
    }

    @NonNull
    private static String message(@NonNull Throwable t) {
        return t.getMessage() == null || t.getMessage().trim().isEmpty() ? t.getClass().getSimpleName() : t.getMessage();
    }

    @NonNull
    private static JSONObject error(int status, @NonNull String code, @NonNull String message) throws JSONException {
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

    /**
     * One graph: its interpreter, its delegate, its inputs by canonical name (so
     * {@code serving_default_style:0} and the vocoder's plain {@code style} are both "style"), and
     * one grow-only direct buffer per input handed out as an exactly-sized slice — the interpreter
     * insists an input buffer's capacity equals the tensor's byte size, and a fresh buffer per
     * sentence per input would churn direct memory.
     */
    private static final class Graph {
        private final Interpreter interpreter;
        @Nullable private final TaiXnnpackDelegate delegate;
        private final String[] inputNames;
        private final String[] outputNames;
        private final ByteBuffer[] backing;
        private final ByteBuffer[] slices;
        private volatile boolean closed;

        private Graph(@NonNull Interpreter interpreter, @Nullable TaiXnnpackDelegate delegate) {
            this.interpreter = interpreter;
            this.delegate = delegate;
            int inputs = interpreter.getInputTensorCount();
            inputNames = new String[inputs];
            for (int i = 0; i < inputs; i++) inputNames[i] = canonical(interpreter.getInputTensor(i).name());
            int outputs = interpreter.getOutputTensorCount();
            outputNames = new String[outputs];
            for (int i = 0; i < outputs; i++) outputNames[i] = interpreter.getOutputTensor(i).name();
            backing = new ByteBuffer[inputs];
            slices = new ByteBuffer[inputs];
        }

        @NonNull
        static Graph open(@NonNull File file, int threads, boolean useDelegate) throws IOException {
            if (!file.isFile()) throw new IOException("Missing " + file.getName());
            TaiXnnpackDelegate delegate = useDelegate ? TaiXnnpackDelegate.create(threads) : null;
            Interpreter.Options options = new Interpreter.Options().setNumThreads(threads).setCancellable(true);
            if (delegate != null) options.setUseXNNPACK(false).addDelegate(delegate);
            else options.setUseXNNPACK(useDelegate);
            try {
                return new Graph(new Interpreter(file, options), delegate);
            } catch (RuntimeException e) {
                if (delegate != null) delegate.close();
                throw new IOException(file.getName() + ": " + e.getMessage(), e);
            }
        }

        void requireInputs(@NonNull String... names) throws IOException {
            for (String name : names) {
                if (inputIndex(name) < 0) throw new IOException("Graph lacks input " + name + ": " + java.util.Arrays.toString(inputNames));
            }
        }

        void requireOutputs(@NonNull String... suffixes) throws IOException {
            for (String suffix : suffixes) {
                if (outputIndex(suffix) < 0) throw new IOException("Graph lacks output *" + suffix + ": " + java.util.Arrays.toString(outputNames));
            }
        }

        /** An exactly {@code bytes}-long native-order slice for input {@code name}, to be filled before {@link #run}. */
        @NonNull
        ByteBuffer input(@NonNull String name, int bytes) {
            int index = inputIndex(name);
            if (index < 0) throw new IllegalArgumentException("No input " + name);
            ByteBuffer buffer = backing[index];
            if (buffer == null || buffer.capacity() < bytes) {
                buffer = ByteBuffer.allocateDirect(Math.max(bytes, buffer == null ? bytes : buffer.capacity() * 3 / 2))
                    .order(ByteOrder.nativeOrder());
                backing[index] = buffer;
            }
            buffer.clear();
            buffer.limit(bytes);
            ByteBuffer slice = buffer.slice().order(ByteOrder.nativeOrder());
            slices[index] = slice;
            return slice;
        }

        /**
         * Resizes the named dynamic inputs to {@code shapes} where they changed, re-allocates,
         * resets the LSTM state and runs. Every input must have been filled through {@link #input}.
         */
        void run(@NonNull int[][] shapes, @NonNull String[] dynamicInputs) {
            boolean resized = false;
            for (int i = 0; i < dynamicInputs.length; i++) {
                int index = inputIndex(dynamicInputs[i]);
                if (!java.util.Arrays.equals(interpreter.getInputTensor(index).shape(), shapes[i])) {
                    interpreter.resizeInput(index, shapes[i]);
                    resized = true;
                }
            }
            if (resized) interpreter.allocateTensors();
            // The fused LSTMs keep their state in variable tensors across invocations; a
            // same-length sentence would otherwise start from the last one's final state.
            interpreter.resetVariableTensors();
            Object[] inputs = new Object[slices.length];
            for (int i = 0; i < slices.length; i++) {
                if (slices[i] == null) throw new IllegalStateException("Input " + inputNames[i] + " was not filled");
                slices[i].rewind();
                inputs[i] = slices[i];
            }
            interpreter.setCancelled(false);
            Map<Integer, Object> none = Collections.emptyMap();
            interpreter.runForMultipleInputsOutputs(inputs, none);
            for (int i = 0; i < slices.length; i++) slices[i] = null;
        }

        /** The float output whose name ends with {@code suffix} (any output for ""), copied out. */
        @NonNull
        float[] floats(@NonNull String suffix) {
            FloatBuffer data = tensorData(suffix).asFloatBuffer();
            float[] out = new float[data.remaining()];
            data.get(out);
            return out;
        }

        /** The raw bytes of the output whose name ends with {@code suffix}; valid until this graph runs again. */
        @NonNull
        ByteBuffer bytes(@NonNull String suffix) {
            return tensorData(suffix);
        }

        @NonNull
        int[] ints(@NonNull String suffix) {
            IntBuffer data = tensorData(suffix).asIntBuffer();
            int[] out = new int[data.remaining()];
            data.get(out);
            return out;
        }

        @NonNull
        private ByteBuffer tensorData(@NonNull String suffix) {
            int index = outputIndex(suffix);
            if (index < 0) throw new IllegalStateException("No output ending " + suffix);
            Tensor tensor = interpreter.getOutputTensor(index);
            // The read-only view comes back big-endian whatever the tensor holds; the data is native order.
            return tensor.asReadOnlyBuffer().order(ByteOrder.nativeOrder());
        }

        private int inputIndex(@NonNull String name) {
            for (int i = 0; i < inputNames.length; i++) if (inputNames[i].equals(name)) return i;
            return -1;
        }

        private int outputIndex(@NonNull String suffix) {
            if (suffix.isEmpty()) return outputNames.length > 0 ? 0 : -1;
            for (int i = 0; i < outputNames.length; i++) if (outputNames[i].endsWith(suffix)) return i;
            return -1;
        }

        void cancel() {
            if (closed) return;
            try {
                interpreter.setCancelled(true);
            } catch (RuntimeException ignored) {
                // Closed under us, or built without cancellation: the between-graph checks still stop it.
            }
        }

        void close() {
            closed = true;
            interpreter.close();
            if (delegate != null) delegate.close();
        }

        @NonNull
        static String canonical(@NonNull String name) {
            String out = name.startsWith("serving_default_") ? name.substring("serving_default_".length()) : name;
            int colon = out.indexOf(':');
            return colon >= 0 ? out.substring(0, colon) : out;
        }
    }
}
