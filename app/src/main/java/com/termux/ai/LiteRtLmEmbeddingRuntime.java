package com.termux.ai;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.ai.edge.litertlm.Backend;
import com.google.ai.edge.litertlm.EmbeddingEngine;
import com.google.ai.edge.litertlm.EmbeddingEngineConfig;
import com.google.ai.edge.litertlm.EmbeddingOptions;
import com.google.ai.edge.litertlm.EmbeddingResponse;
import com.google.ai.edge.litertlm.InputData;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Text embeddings for EmbeddingGemma 2 {@code .litertlm} files (litert-community's
 * {@code embeddinggemma-2-text-270m} and {@code embeddinggemma-2-text-vision-440m}), served by
 * LiteRT-LM's own {@link EmbeddingEngine}. The {@code .litertlm} bundle carries its tokenizer and
 * graph together, so there is no sidecar to fetch and no window graph to pick: one engine, built
 * on the CPU with an input cap of {@link #MAX_INPUT_TOKENS}, and reloaded only when the model file
 * changes.
 *
 * <p>Mirrors {@link LiteRtEmbeddingRuntime}'s response shape so {@code /v1/embeddings} stays
 * backend-agnostic, with two differences the engine forces: it exposes no tokenizer or token count,
 * so items carry {@code truncated: false} and no {@code tokens}, and {@code usage} is zero. The task
 * prefixes are the caller's job with this engine as with v1, so every input is prefixed with
 * {@link LiteRtEmbeddingRuntime#buildPrefix}. Text input only; image input is a later change.
 *
 * <p>If the bundled LiteRT-LM library lacks the embedding JNI, loading raises
 * {@link UnsatisfiedLinkError} or {@link NoClassDefFoundError} and this runtime answers {@code 501
 * litertlm_embeddings_unavailable} instead of taking the runtime process down.
 */
class LiteRtLmEmbeddingRuntime implements AutoCloseable {
    /**
     * The engine's input cap, in tokens, and the window {@code /v1/models} reports for a
     * {@code .litertlm} embedder. The model's own window is 8192; 2048 keeps the engine's buffers
     * (and so its resident memory) at the size dawn's note chunks actually need.
     */
    public static final int MAX_INPUT_TOKENS = 2048;
    /** EmbeddingGemma 2's native output width; {@code dimensions} may only shorten it. */
    static final int MAX_OUTPUT_DIMENSIONS = 768;

    private final TaiResidency residency;
    /** For the load meter and its history; {@code null} in the router's test seam (nothing is measured). */
    @Nullable private final Context appContext;
    @Nullable private EmbeddingEngine engine;
    /** Read without the monitor by the router's eviction and its one-LiteRT-embedder rule. */
    @Nullable private volatile String loadedModelId;
    @Nullable private String loadedModelPath;

    LiteRtLmEmbeddingRuntime(@NonNull TaiResidency residency) {
        this(residency, null);
    }

    LiteRtLmEmbeddingRuntime(@NonNull TaiResidency residency, @Nullable Context context) {
        this.residency = residency;
        this.appContext = context == null ? null : context.getApplicationContext();
    }

    /** The model this runtime's engine holds, or {@code null}. Lock-free: never waits on a batch. */
    @Nullable
    String loadedModelId() {
        return loadedModelId;
    }

    /**
     * @param inputType {@link LiteRtEmbeddingRuntime#INPUT_TYPE_QUERY} or
     *                  {@link LiteRtEmbeddingRuntime#INPUT_TYPE_DOCUMENT}; selects the task prefix.
     * @param title     an optional document heading for the document prefix.
     * @param throttled true while a chat generation runs elsewhere in this process: the calling
     *                  thread drops to background priority for the batch.
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
        if (dimensions > MAX_OUTPUT_DIMENSIONS) {
            return error(400, "invalid_dimensions",
                "Requested embedding dimensions exceed model output dimensions (" + MAX_OUTPUT_DIMENSIONS + ").");
        }
        File modelFile = new File(spec.localPath);
        if (!modelFile.exists()) {
            return error(404, "model_file_missing", "Embedding model file is missing.");
        }
        if (!modelFile.isFile() || !modelFile.canRead()) {
            return error(404, "model_file_not_readable", "Embedding model file is unreadable.");
        }
        try {
            ensureLoaded(spec, modelFile);
        } catch (UnsatisfiedLinkError | NoClassDefFoundError e) {
            return error(501, "litertlm_embeddings_unavailable",
                "This build's LiteRT-LM library does not expose embeddings. Update to a build with LiteRT-LM embedding support.");
        } catch (Throwable t) {
            return error(500, "embedding_inference_failed",
                "LiteRT-LM embedding model failed to load: " + describe(t));
        }
        EmbeddingEngine current = engine;
        if (current == null) {
            return error(500, "embedding_inference_failed", "LiteRT-LM embedding engine is not loaded.");
        }
        String prefix = LiteRtEmbeddingRuntime.buildPrefix(inputType, title);
        List<List<InputData>> batch = new ArrayList<>(inputs.size());
        for (String input : inputs) {
            batch.add(Collections.<InputData>singletonList(new InputData.Text(prefix + input)));
        }
        EmbeddingOptions options = new EmbeddingOptions(Boolean.TRUE, null, dimensions > 0 ? dimensions : null, null);
        List<EmbeddingResponse> responses;
        residency.setBusy(TaiResidency.Kind.EMBEDDING, spec.id, true);
        int priorPriority = throttled ? MnnEmbeddingRuntime.lowerThreadPriority() : Integer.MIN_VALUE;
        try {
            responses = current.computeEmbeddingBatch(batch, options);
        } catch (Throwable t) {
            if (namesOutputSize(t)) {
                return error(400, "invalid_dimensions",
                    "The model refused the requested embedding dimensions: " + describe(t));
            }
            return error(500, "embedding_inference_failed", "LiteRT-LM embedding inference failed: " + describe(t));
        } finally {
            residency.setBusy(TaiResidency.Kind.EMBEDDING, spec.id, false);
            if (throttled) MnnEmbeddingRuntime.restoreThreadPriority(priorPriority);
        }
        if (responses == null || responses.size() != inputs.size()) {
            return error(500, "embedding_inference_failed", "LiteRT-LM returned "
                + (responses == null ? 0 : responses.size()) + " embeddings for " + inputs.size() + " inputs.");
        }
        JSONArray data = new JSONArray();
        for (int i = 0; i < responses.size(); i++) {
            EmbeddingResponse response = responses.get(i);
            float[] vector = response == null ? null : response.getEmbedding();
            if (vector == null || vector.length == 0) {
                return error(500, "embedding_inference_failed", "LiteRT-LM returned an empty embedding.");
            }
            float[] shaped = shapeVector(vector, dimensions);
            JSONObject item = new JSONObject();
            item.put("object", "embedding");
            item.put("index", i);
            JSONArray json = new JSONArray();
            for (float value : shaped) json.put((double) value);
            item.put("embedding", json);
            // The engine exposes no token count: no "tokens", and dawn reads a missing count as
            // "not truncated, unknown length" (the MNN runtime's contract).
            item.put("truncated", false);
            data.put(item);
        }
        JSONObject usage = new JSONObject();
        usage.put("prompt_tokens", 0);
        usage.put("total_tokens", 0);
        JSONObject response = new JSONObject();
        response.put("object", "list");
        response.put("data", data);
        response.put("model", spec.id);
        response.put("usage", usage);
        response.put("_backend", TaiModelSpec.BACKEND_LITERT_LM);
        response.put("_runtime", "litertlm-embedding");
        return response;
    }

    private void ensureLoaded(@NonNull TaiModelSpec spec, @NonNull File modelFile) throws Exception {
        String modelPath = modelFile.getAbsolutePath();
        if (engine != null && modelPath.equals(loadedModelPath) && spec.id.equals(loadedModelId)) return;
        close();
        EmbeddingEngineConfig config = new EmbeddingEngineConfig(modelPath, new Backend.CPU(), null, null, null, null,
            MAX_INPUT_TOKENS, null);
        EmbeddingEngine created = new EmbeddingEngine(config);
        // The same MemAvailable meter a chat load runs, across native initialization only.
        TaiLoadMeter meter = TaiLoadMeter.start(appContext);
        long measured;
        try {
            created.initialize();
        } catch (Throwable t) {
            try { created.close(); } catch (Throwable ignored) { }
            throw t;
        } finally {
            measured = meter.stop();
        }
        engine = created;
        loadedModelId = spec.id;
        loadedModelPath = modelPath;
        long estimate = modelFile.length() * TaiResidency.LITERT_EMBEDDING_FACTOR_TENTHS / 10L;
        residency.register(TaiResidency.Entry.embedding(spec, MAX_INPUT_TOKENS, estimate)
            .withMeasured(measured >= 0L ? measured : null));
        if (measured >= 0L && appContext != null) {
            TaiRuntimeHistory.recordMeasuredLoad(appContext, spec, TaiDeviceCapabilities.detect(appContext),
                TaiModelSpec.BACKEND_LITERT_LM, "cpu", 0, measured);
        }
    }

    /**
     * Shortens to {@code dimensions} if the engine returned a wider vector, then L2-normalises, so
     * {@code _endpoint_normalized} holds whatever the engine did with {@code normalize}/{@code outputSize}.
     */
    @NonNull
    static float[] shapeVector(@NonNull float[] vector, int dimensions) {
        float[] shaped = vector;
        if (dimensions > 0 && dimensions < vector.length) {
            shaped = new float[dimensions];
            System.arraycopy(vector, 0, shaped, 0, dimensions);
        }
        double sumSquares = 0.0;
        for (float value : shaped) sumSquares += (double) value * value;
        double norm = Math.sqrt(sumSquares);
        if (norm > 0.0) {
            for (int i = 0; i < shaped.length; i++) shaped[i] = (float) (shaped[i] / norm);
        }
        return shaped;
    }

    /** Whether the engine's failure is about the requested output size rather than the input. */
    static boolean namesOutputSize(@NonNull Throwable t) {
        for (Throwable cause = t; cause != null; cause = cause.getCause()) {
            String message = cause.getMessage();
            if (message == null) continue;
            String lower = message.toLowerCase(Locale.ROOT);
            if (lower.contains("output_size") || lower.contains("output size") || lower.contains("outputsize")) return true;
            if (cause.getCause() == cause) break;
        }
        return false;
    }

    @NonNull
    private static String describe(@NonNull Throwable t) {
        return t.getMessage() == null ? t.getClass().getSimpleName() : t.getMessage();
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
        if (engine != null) {
            try { engine.close(); } catch (Throwable ignored) { }
            engine = null;
        }
        String modelId = loadedModelId;
        if (modelId != null) residency.deregister(TaiResidency.Kind.EMBEDDING, modelId);
        loadedModelId = null;
        loadedModelPath = null;
    }
}
