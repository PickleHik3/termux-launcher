package com.termux.ai;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.tensorflow.lite.Interpreter;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

final class LiteRtEmbeddingRuntime implements AutoCloseable {
    /** Gemma sentencepiece control ids; SentencePieceBpeTokenizer returns ids without them, so this class frames the sequence. */
    private static final int TOKEN_BOS = 2;
    private static final int TOKEN_EOS = 1;

    /** {@code input_type: "query"} — EmbeddingGemma's trained retrieval-query task prefix. */
    static final String INPUT_TYPE_QUERY = "query";
    /** {@code input_type: "document"} (the default) — the trained document/passage task prefix. */
    static final String INPUT_TYPE_DOCUMENT = "document";
    private static final String QUERY_PREFIX = "task: search result | query: ";
    private static final String DOCUMENT_PREFIX_FORMAT = "title: %s | text: ";

    /** Interpreter threads; fixed at build time (TFLite cannot change them on a live interpreter). */
    private static final int DEFAULT_THREADS = Math.max(1, Math.min(4, Runtime.getRuntime().availableProcessors()));

    private final TaiResidency residency;
    /** For the load meter and its history; {@code null} in the router's test seam (nothing is measured). */
    @Nullable private final Context appContext;
    @Nullable private SentencePieceBpeTokenizer tokenizer;
    @Nullable private String loadedModelId;
    @Nullable private String loadedModelPath;
    @Nullable private String loadedTokenizerPath;
    /** The window graphs beside the primary file, keyed by window (filename-derived), ascending. */
    @Nullable private TreeMap<Integer, File> windowGraphs;
    /** Interpreters built so far, one per window actually used; built lazily, closed together. */
    private final TreeMap<Integer, WindowRuntime> windowRuntimes = new TreeMap<>();

    LiteRtEmbeddingRuntime(@NonNull TaiResidency residency) {
        this(residency, null);
    }

    LiteRtEmbeddingRuntime(@NonNull TaiResidency residency, @Nullable Context context) {
        this.residency = residency;
        this.appContext = context == null ? null : context.getApplicationContext();
    }

    @NonNull
    synchronized JSONObject embed(@NonNull TaiModelSpec spec, @NonNull List<String> inputs, int dimensions) throws JSONException {
        return embed(spec, inputs, dimensions, INPUT_TYPE_DOCUMENT, null, false);
    }

    /**
     * @param inputType {@link #INPUT_TYPE_QUERY} or {@link #INPUT_TYPE_DOCUMENT} (default); selects
     *                  the task prefix EmbeddingGemma was trained with (dawn brief item 1).
     * @param title     an optional document heading, folded into the document prefix in place of
     *                  {@code none}; ignored for {@code input_type: "query"}.
     * @param throttled true while a chat generation is running elsewhere in this process: runs the
     *                  calling thread at background priority so embedding batches don't slow the
     *                  live reply (dawn brief item 5).
     */
    @NonNull
    synchronized JSONObject embed(@NonNull TaiModelSpec spec, @NonNull List<String> inputs, int dimensions,
                                   @NonNull String inputType, @Nullable String title, boolean throttled) throws JSONException {
        if (spec.localPath == null || spec.localPath.trim().isEmpty()) {
            return error(404, "model_file_missing", "Embedding model file is missing.");
        }
        if (dimensions < 0) {
            return error(400, "invalid_dimensions", "Embedding dimensions must be positive.");
        }
        File modelFile = new File(spec.localPath);
        if (!modelFile.isFile() || !modelFile.canRead()) {
            return error(404, "model_file_not_readable", "Embedding model file is missing or unreadable.");
        }
        File tokenizerFile = tokenizerFileFor(modelFile);
        if (tokenizerFile == null) {
            return error(409, "embedding_tokenizer_missing",
                "EmbeddingGemma requires sentencepiece.model next to the .tflite model file.");
        }
        try {
            ensureModelReady(spec, modelFile, tokenizerFile);
            if (tokenizer == null || windowGraphs == null || windowGraphs.isEmpty()) {
                throw new IllegalStateException("Embedding runtime is not loaded.");
            }
            // The prefix is the same for every input in one call (one input_type/title per request);
            // only each input's body differs, so it is tokenized once here rather than per item.
            String prefix = buildPrefix(inputType, title);
            int[] prefixIds = prefix.isEmpty() ? new int[0] : tokenizer.encode(prefix);
            int n = inputs.size();
            int[][] bodyIdsPerInput = new int[n][];
            int[] selectedWindow = new int[n];
            for (int i = 0; i < n; i++) {
                bodyIdsPerInput[i] = tokenizer.encode(inputs.get(i));
                int needed = prefixIds.length + bodyIdsPerInput[i].length + 2; // +2 for BOS/EOS
                selectedWindow[i] = pickWindow(windowGraphs.keySet(), needed);
            }
            // Group by window so each graph runs its whole share of the batch, in the original
            // input order once every group is back (dawn brief item 4).
            Map<Integer, List<Integer>> byWindow = new LinkedHashMap<>();
            for (int i = 0; i < n; i++) {
                byWindow.computeIfAbsent(selectedWindow[i], k -> new ArrayList<>()).add(i);
            }
            Embedding[] results = new Embedding[n];
            Integer effectiveDimensions = null;
            residency.setBusy(TaiResidency.Kind.EMBEDDING, spec.id, true);
            int priorPriority = throttled ? MnnEmbeddingRuntime.lowerThreadPriority() : Integer.MIN_VALUE;
            try {
                for (Map.Entry<Integer, List<Integer>> group : byWindow.entrySet()) {
                    WindowRuntime windowRuntime = ensureWindowLoaded(spec, group.getKey());
                    if (effectiveDimensions == null) {
                        effectiveDimensions = dimensions > 0 ? dimensions : windowRuntime.outputDimensions;
                        if (effectiveDimensions <= 0 || effectiveDimensions > windowRuntime.outputDimensions) {
                            return error(400, "invalid_dimensions",
                                "Requested embedding dimensions exceed model output dimensions.");
                        }
                    }
                    for (int index : group.getValue()) {
                        results[index] = embedWithRuntime(windowRuntime, prefixIds, bodyIdsPerInput[index], effectiveDimensions);
                    }
                }
            } finally {
                residency.setBusy(TaiResidency.Kind.EMBEDDING, spec.id, false);
                if (throttled) MnnEmbeddingRuntime.restoreThreadPriority(priorPriority);
            }
            JSONArray data = new JSONArray();
            int promptTokens = 0;
            for (int i = 0; i < n; i++) {
                Embedding embedding = results[i];
                promptTokens += embedding.tokens;
                JSONObject item = new JSONObject();
                item.put("object", "embedding");
                item.put("index", i);
                JSONArray vector = new JSONArray();
                for (float value : embedding.vector) vector.put((double) value);
                item.put("embedding", vector);
                // The count before any body truncation, prefix included (see buildPrefix); the
                // body alone is never reported separately, matching dawn brief item 2.
                item.put("tokens", embedding.tokens);
                item.put("truncated", embedding.truncated);
                data.put(item);
            }
            JSONObject usage = new JSONObject();
            usage.put("prompt_tokens", promptTokens);
            usage.put("total_tokens", promptTokens);
            JSONObject response = new JSONObject();
            response.put("object", "list");
            response.put("data", data);
            response.put("model", spec.id);
            response.put("usage", usage);
            response.put("_backend", TaiModelSpec.BACKEND_LITERT_LM);
            response.put("_runtime", "litert-embedding");
            return response;
        } catch (Throwable t) {
            return error(500, "embedding_inference_failed",
                "LiteRT embedding inference failed: " + (t.getMessage() == null ? t.getClass().getSimpleName() : t.getMessage()));
        }
    }

    /**
     * Loads the tokenizer and rediscovers the window graphs beside {@code modelFile} when the
     * model has changed; a no-op otherwise. Builds no interpreter — each window's graph is built
     * lazily on first use by {@link #ensureWindowLoaded} (dawn/window-routing brief item 3).
     */
    private void ensureModelReady(@NonNull TaiModelSpec spec, @NonNull File modelFile, @NonNull File tokenizerFile) throws Exception {
        String modelPath = modelFile.getAbsolutePath();
        String tokenizerPath = tokenizerFile.getAbsolutePath();
        if (tokenizer != null && windowGraphs != null
            && modelPath.equals(loadedModelPath) && tokenizerPath.equals(loadedTokenizerPath)) {
            return;
        }
        close();
        tokenizer = SentencePieceBpeTokenizer.fromModelFile(tokenizerFile);
        windowGraphs = new TreeMap<>(TaiImportProfiles.siblingWindowGraphs(modelFile));
        if (windowGraphs.isEmpty()) {
            // The primary's own file name carries no seqNNNN window; still serve it as the sole
            // graph, keyed by 0 so it is always picked (pickWindow falls back to the largest).
            windowGraphs.put(0, modelFile);
        }
        loadedModelId = spec.id;
        loadedModelPath = modelPath;
        loadedTokenizerPath = tokenizerPath;
    }

    /**
     * Builds and returns {@code window}'s interpreter, building it on first use only. Each window
     * gets its own {@link TaiXnnpackDelegate}, set up exactly as the single-graph load used to be.
     * Updates the shared {@link TaiResidency} EMBEDDING entry to the sum of every window loaded so
     * far, using the same {@link TaiLoadMeter} measurement a single load used to record (item 5).
     */
    @NonNull
    private WindowRuntime ensureWindowLoaded(@NonNull TaiModelSpec spec, int window) throws Exception {
        WindowRuntime existing = windowRuntimes.get(window);
        if (existing != null) return existing;
        File graphFile = windowGraphs == null ? null : windowGraphs.get(window);
        if (graphFile == null) throw new IllegalStateException("No installed graph for window " + window);
        // Stock Interpreter XNNPACK never gets worker threads (one core on pong, as Whisper found);
        // the JNI shim's delegate spawns a real pthreadpool. See TaiXnnpackDelegate.
        TaiXnnpackDelegate delegate = TaiXnnpackDelegate.create(DEFAULT_THREADS);
        Interpreter.Options options = delegate != null
            ? new Interpreter.Options().setNumThreads(DEFAULT_THREADS).setUseXNNPACK(false).addDelegate(delegate)
            : new Interpreter.Options().setNumThreads(DEFAULT_THREADS).setUseXNNPACK(true);
        // The same MemAvailable meter a chat load runs, across the interpreter's construction only.
        TaiLoadMeter meter = TaiLoadMeter.start(appContext);
        Interpreter interpreter;
        long measured;
        try {
            interpreter = new Interpreter(graphFile, options);
        } finally {
            measured = meter.stop();
        }
        int[] inputShape = interpreter.getInputTensor(0).shape();
        int[] outputShape = interpreter.getOutputTensor(0).shape();
        int sequenceLength = inputShape.length >= 2 ? inputShape[inputShape.length - 1] : 1024;
        int outputDimensions = outputShape.length >= 2 ? outputShape[outputShape.length - 1] : 768;
        // BOS + EOS framing needs at least two slots in every input row.
        if (sequenceLength < 2) {
            interpreter.close();
            if (delegate != null) delegate.close();
            throw new IllegalStateException("Embedding model reports an unusable sequence length: " + sequenceLength);
        }
        if (outputDimensions <= 0) {
            interpreter.close();
            if (delegate != null) delegate.close();
            throw new IllegalStateException("Embedding model reports an unusable output dimension: " + outputDimensions);
        }
        WindowRuntime windowRuntime = new WindowRuntime(interpreter, delegate, sequenceLength, outputDimensions,
            graphFile.length(), measured >= 0L ? measured : null);
        windowRuntimes.put(window, windowRuntime);
        updateResidency(spec);
        if (measured >= 0L && appContext != null) {
            // Keyed at window 0: an embedding load has no context window to bucket.
            TaiRuntimeHistory.recordMeasuredLoad(appContext, spec, TaiDeviceCapabilities.detect(appContext),
                TaiModelSpec.BACKEND_LITERT_LM, "cpu", 0, measured);
        }
        return windowRuntime;
    }

    /**
     * Refreshes the single EMBEDDING resident to reflect every window loaded so far: the estimate
     * is the sum of each graph's file size at the same factor {@link TaiResidency#embeddingEstimateBytes}
     * uses, and the measured figure is the sum of each window's own {@link TaiLoadMeter} reading
     * when every loaded window has one (a partial sum otherwise, better than none). The reported
     * window is the largest one installed, matching {@code _endpoint_context_window}.
     */
    private void updateResidency(@NonNull TaiModelSpec spec) {
        long estimatedTotal = 0L;
        long measuredTotal = 0L;
        boolean anyMeasured = false;
        for (WindowRuntime windowRuntime : windowRuntimes.values()) {
            estimatedTotal += windowRuntime.fileBytes * TaiResidency.LITERT_EMBEDDING_FACTOR_TENTHS / 10L;
            if (windowRuntime.measuredBytes != null) {
                measuredTotal += windowRuntime.measuredBytes;
                anyMeasured = true;
            }
        }
        int reportedWindow = windowGraphs == null || windowGraphs.isEmpty() ? 0 : windowGraphs.lastKey();
        TaiResidency.Entry entry = TaiResidency.Entry.embedding(spec, reportedWindow, estimatedTotal)
            .withMeasured(anyMeasured ? measuredTotal : null);
        residency.register(entry);
    }

    /**
     * The smallest window that fits {@code needed} tokens (prefix + body + BOS/EOS), or the
     * largest installed window when none does — the same window {@link #computeBudget} then
     * truncates the body against, exactly as a single-graph install always has. Pure and static so
     * routing can be unit tested without a loaded interpreter.
     */
    static int pickWindow(@NonNull java.util.Collection<Integer> installedWindows, int needed) {
        int smallestFit = -1;
        int largest = -1;
        for (int window : installedWindows) {
            if (window > largest) largest = window;
            if (window >= needed && (smallestFit == -1 || window < smallestFit)) smallestFit = window;
        }
        return smallestFit != -1 ? smallestFit : largest;
    }

    @NonNull
    private Embedding embedWithRuntime(@NonNull WindowRuntime windowRuntime, @NonNull int[] prefixIds,
                                        @NonNull int[] bodyIds, int dimensions) {
        Budget budget = computeBudget(windowRuntime.sequenceLength, prefixIds.length, bodyIds.length);
        int[][] tokenIds = new int[1][windowRuntime.sequenceLength];
        int cursor = 0;
        tokenIds[0][cursor++] = TOKEN_BOS;
        for (int i = 0; i < budget.prefixTokens; i++) tokenIds[0][cursor++] = prefixIds[i];
        for (int i = 0; i < budget.bodyTokens; i++) tokenIds[0][cursor++] = bodyIds[i];
        tokenIds[0][Math.min(cursor, windowRuntime.sequenceLength - 1)] = TOKEN_EOS;
        float[][] output = new float[1][windowRuntime.outputDimensions];
        windowRuntime.interpreter.run(tokenIds, output);
        float[] vector = output[0];
        if (dimensions == vector.length) return new Embedding(normalize(vector), budget.reportedTokens, budget.truncated);
        float[] shortened = new float[dimensions];
        System.arraycopy(vector, 0, shortened, 0, dimensions);
        return new Embedding(normalize(shortened), budget.reportedTokens, budget.truncated);
    }

    /**
     * EmbeddingGemma's trained task prefixes (google/embeddinggemma-300m model card): a query gets
     * {@code "task: search result | query: "}; a document gets {@code "title: <title or none> | text: "}.
     * Other embedding families ignore {@code input_type} entirely (they never call this).
     */
    @NonNull
    static String buildPrefix(@NonNull String inputType, @Nullable String title) {
        if (INPUT_TYPE_QUERY.equals(inputType)) return QUERY_PREFIX;
        String heading = title == null || title.trim().isEmpty() ? "none" : title.trim();
        return String.format(Locale.ROOT, DOCUMENT_PREFIX_FORMAT, heading);
    }

    /**
     * How many prefix and body tokens fit inside {@code sequenceLength} once BOS and EOS each take
     * a slot: the prefix is counted inside the window and is never the part that is cut (dawn brief
     * item 1); only the body is trimmed to make room, and never below zero tokens. {@code
     * reportedTokens} is prefix + body before any cut — item 2's "count before cutting" — and {@code
     * truncated} is whether the body itself had to be shortened to fit.
     */
    @NonNull
    static Budget computeBudget(int sequenceLength, int prefixLength, int bodyLength) {
        int framingBudget = Math.max(0, sequenceLength - 2);
        int prefixTokens = Math.min(prefixLength, framingBudget);
        int bodyBudget = Math.max(0, framingBudget - prefixTokens);
        boolean truncated = bodyLength > bodyBudget;
        int bodyTokens = Math.min(bodyLength, bodyBudget);
        int reportedTokens = prefixLength + bodyLength;
        return new Budget(prefixTokens, bodyTokens, reportedTokens, truncated);
    }

    /** See {@link #computeBudget}. */
    static final class Budget {
        final int prefixTokens;
        final int bodyTokens;
        final int reportedTokens;
        final boolean truncated;

        Budget(int prefixTokens, int bodyTokens, int reportedTokens, boolean truncated) {
            this.prefixTokens = prefixTokens;
            this.bodyTokens = bodyTokens;
            this.reportedTokens = reportedTokens;
            this.truncated = truncated;
        }
    }

    /**
     * Raw tokenizer output for {@code /v1/tokenize} (dawn brief, "nice to have"): no task prefix and
     * no BOS/EOS framing, just the count dawn's own splitter would otherwise have to estimate. Needs
     * only the tokenizer, so counting tokens never builds a window interpreter.
     */
    synchronized int tokenCount(@NonNull TaiModelSpec spec, @NonNull String text) throws Exception {
        if (spec.localPath == null || spec.localPath.trim().isEmpty()) {
            throw new IllegalStateException("Embedding model file is missing.");
        }
        File modelFile = new File(spec.localPath);
        File tokenizerFile = tokenizerFileFor(modelFile);
        if (tokenizerFile == null) {
            throw new IllegalStateException("EmbeddingGemma requires sentencepiece.model next to the .tflite model file.");
        }
        ensureModelReady(spec, modelFile, tokenizerFile);
        if (tokenizer == null) throw new IllegalStateException("Embedding runtime is not loaded.");
        return tokenizer.encode(text).length;
    }

    @NonNull
    private float[] normalize(@NonNull float[] vector) {
        double sumSquares = 0.0;
        for (float value : vector) sumSquares += (double) value * value;
        double norm = Math.sqrt(sumSquares);
        if (norm > 0.0) {
            for (int i = 0; i < vector.length; i++) vector[i] = (float) (vector[i] / norm);
        }
        return vector;
    }

    @Nullable
    private File tokenizerFileFor(@NonNull File modelFile) {
        File dir = modelFile.getParentFile();
        if (dir == null) return null;
        File sentencePiece = new File(dir, "sentencepiece.model");
        return sentencePiece.isFile() && sentencePiece.canRead() ? sentencePiece : null;
    }

    @NonNull
    private JSONObject error(int status, @NonNull String code, @NonNull String message) throws JSONException {
        JSONObject error = new JSONObject();
        error.put("message", message);
        error.put("type", status >= 500 ? "server_error" : "invalid_request_error");
        error.put("code", code);
        JSONObject response = new JSONObject();
        response.put("error", error);
        response.put("_statusCode", status);
        return response;
    }

    /** Closes all interpreters first, then all delegates, matching the single-graph load's order. */
    @Override
    public synchronized void close() {
        for (WindowRuntime windowRuntime : windowRuntimes.values()) {
            windowRuntime.interpreter.close();
        }
        for (WindowRuntime windowRuntime : windowRuntimes.values()) {
            if (windowRuntime.delegate != null) windowRuntime.delegate.close();
        }
        windowRuntimes.clear();
        tokenizer = null;
        windowGraphs = null;
        if (loadedModelId != null) residency.deregister(TaiResidency.Kind.EMBEDDING, loadedModelId);
        loadedModelId = null;
        loadedModelPath = null;
        loadedTokenizerPath = null;
    }

    private static final class Embedding {
        @NonNull final float[] vector;
        final int tokens;
        final boolean truncated;

        private Embedding(@NonNull float[] vector, int tokens, boolean truncated) {
            this.vector = vector;
            this.tokens = tokens;
            this.truncated = truncated;
        }
    }

    /** One window's loaded graph: its interpreter, delegate, and what it reports about itself. */
    private static final class WindowRuntime {
        @NonNull final Interpreter interpreter;
        @Nullable final TaiXnnpackDelegate delegate;
        final int sequenceLength;
        final int outputDimensions;
        final long fileBytes;
        @Nullable final Long measuredBytes;

        WindowRuntime(@NonNull Interpreter interpreter, @Nullable TaiXnnpackDelegate delegate, int sequenceLength,
                     int outputDimensions, long fileBytes, @Nullable Long measuredBytes) {
            this.interpreter = interpreter;
            this.delegate = delegate;
            this.sequenceLength = sequenceLength;
            this.outputDimensions = outputDimensions;
            this.fileBytes = fileBytes;
            this.measuredBytes = measuredBytes;
        }
    }
}
