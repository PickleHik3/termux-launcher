package com.termux.ai;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.tensorflow.lite.Interpreter;

import java.io.File;
import java.util.List;
import java.util.Locale;

final class LiteRtEmbeddingRuntime implements AutoCloseable {
    /** Gemma sentencepiece control ids; the sentencepiece4j binding does not expose bosId()/eosId(). */
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
    @Nullable private Interpreter interpreter;
    @Nullable private TaiXnnpackDelegate xnnpackDelegate;
    @Nullable private SentencePieceBpeTokenizer tokenizer;
    @Nullable private String loadedModelId;
    @Nullable private String loadedModelPath;
    @Nullable private String loadedTokenizerPath;
    private int sequenceLength = 0;
    private int outputDimensions = 0;

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
            ensureLoaded(spec, modelFile, tokenizerFile);
            int effectiveDimensions = dimensions > 0 ? dimensions : outputDimensions;
            if (effectiveDimensions <= 0 || effectiveDimensions > outputDimensions) {
                return error(400, "invalid_dimensions",
                    "Requested embedding dimensions exceed model output dimensions.");
            }
            JSONArray data = new JSONArray();
            int promptTokens = 0;
            residency.setBusy(TaiResidency.Kind.EMBEDDING, spec.id, true);
            int priorPriority = throttled ? MnnEmbeddingRuntime.lowerThreadPriority() : Integer.MIN_VALUE;
            try {
                for (int i = 0; i < inputs.size(); i++) {
                    Embedding embedding = embedOne(inputs.get(i), effectiveDimensions, inputType, title);
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
            } finally {
                residency.setBusy(TaiResidency.Kind.EMBEDDING, spec.id, false);
                if (throttled) MnnEmbeddingRuntime.restoreThreadPriority(priorPriority);
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

    private void ensureLoaded(@NonNull TaiModelSpec spec, @NonNull File modelFile, @NonNull File tokenizerFile) throws Exception {
        String modelPath = modelFile.getAbsolutePath();
        String tokenizerPath = tokenizerFile.getAbsolutePath();
        if (interpreter != null && tokenizer != null
            && modelPath.equals(loadedModelPath) && tokenizerPath.equals(loadedTokenizerPath)) {
            return;
        }
        close();
        tokenizer = SentencePieceBpeTokenizer.fromModelFile(tokenizerFile);
        // Stock Interpreter XNNPACK never gets worker threads (one core on pong, as Whisper found);
        // the JNI shim's delegate spawns a real pthreadpool. See TaiXnnpackDelegate.
        xnnpackDelegate = TaiXnnpackDelegate.create(DEFAULT_THREADS);
        Interpreter.Options options = xnnpackDelegate != null
            ? new Interpreter.Options().setNumThreads(DEFAULT_THREADS).setUseXNNPACK(false).addDelegate(xnnpackDelegate)
            : new Interpreter.Options().setNumThreads(DEFAULT_THREADS).setUseXNNPACK(true);
        // The same MemAvailable meter a chat load runs, across the interpreter's construction only.
        TaiLoadMeter meter = TaiLoadMeter.start(appContext);
        long measured;
        try {
            interpreter = new Interpreter(modelFile, options);
        } finally {
            measured = meter.stop();
        }
        int[] inputShape = interpreter.getInputTensor(0).shape();
        int[] outputShape = interpreter.getOutputTensor(0).shape();
        sequenceLength = inputShape.length >= 2 ? inputShape[inputShape.length - 1] : 1024;
        outputDimensions = outputShape.length >= 2 ? outputShape[outputShape.length - 1] : 768;
        // BOS + EOS framing needs at least two slots in every input row.
        if (sequenceLength < 2) {
            close();
            throw new IllegalStateException("Embedding model reports an unusable sequence length: " + sequenceLength);
        }
        if (outputDimensions <= 0) {
            close();
            throw new IllegalStateException("Embedding model reports an unusable output dimension: " + outputDimensions);
        }
        loadedModelId = spec.id;
        loadedModelPath = modelPath;
        loadedTokenizerPath = tokenizerPath;
        residency.register(TaiResidency.Entry.embedding(spec, sequenceLength).withMeasured(measured >= 0L ? measured : null));
        if (measured >= 0L && appContext != null) {
            // Keyed at window 0: an embedding load has no context window to bucket.
            TaiRuntimeHistory.recordMeasuredLoad(appContext, spec, TaiDeviceCapabilities.detect(appContext),
                TaiModelSpec.BACKEND_LITERT_LM, "cpu", 0, measured);
        }
    }

    @NonNull
    private Embedding embedOne(@NonNull String text, int dimensions, @NonNull String inputType, @Nullable String title) {
        if (interpreter == null || tokenizer == null) throw new IllegalStateException("Embedding runtime is not loaded.");
        String prefix = buildPrefix(inputType, title);
        int[] prefixIds = prefix.isEmpty() ? new int[0] : tokenizer.encode(prefix);
        int[] bodyIds = tokenizer.encode(text);
        Budget budget = computeBudget(sequenceLength, prefixIds.length, bodyIds.length);
        int[][] tokenIds = new int[1][sequenceLength];
        int cursor = 0;
        tokenIds[0][cursor++] = TOKEN_BOS;
        for (int i = 0; i < budget.prefixTokens; i++) tokenIds[0][cursor++] = prefixIds[i];
        for (int i = 0; i < budget.bodyTokens; i++) tokenIds[0][cursor++] = bodyIds[i];
        tokenIds[0][Math.min(cursor, sequenceLength - 1)] = TOKEN_EOS;
        float[][] output = new float[1][outputDimensions];
        interpreter.run(tokenIds, output);
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
     * no BOS/EOS framing, just the count dawn's own splitter would otherwise have to estimate.
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
        ensureLoaded(spec, modelFile, tokenizerFile);
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
        if (loadedModelId != null) residency.deregister(TaiResidency.Kind.EMBEDDING, loadedModelId);
        loadedModelId = null;
        loadedModelPath = null;
        loadedTokenizerPath = null;
        sequenceLength = 0;
        outputDimensions = 0;
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
}
