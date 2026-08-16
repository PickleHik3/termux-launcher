package com.termux.ai;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.alibaba.mnnllm.android.llm.MnnEmbeddingSession;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.util.List;

/**
 * Text embeddings for MNN-format models (config.json packages that declare {@code text_embeddings}),
 * backed by MNN's native {@code Transformer::Embedding} engine through {@link MnnEmbeddingSession}.
 *
 * <p>Mirrors {@link LiteRtEmbeddingRuntime}'s response shape so {@code /v1/embeddings} and
 * {@code /api/embed} are backend-agnostic. If the installed {@code libmnnllmapp.so} predates the TAI
 * embedding JNI, the native call raises {@link UnsatisfiedLinkError} and this runtime reports a clear
 * {@code mnn_embeddings_unavailable} error instead of crashing the runtime process.
 */
final class MnnEmbeddingRuntime implements AutoCloseable {
    @Nullable private MnnEmbeddingSession session;
    @Nullable private String loadedConfigPath;
    private int outputDimensions = 0;

    @NonNull
    synchronized JSONObject embed(@NonNull TaiModelSpec spec, @NonNull List<String> inputs, int dimensions) throws JSONException {
        if (spec.localPath == null || spec.localPath.trim().isEmpty()) {
            return error(404, "model_file_missing", "MNN embedding model config path is missing.");
        }
        if (dimensions < 0) {
            return error(400, "invalid_dimensions", "Embedding dimensions must be positive.");
        }
        File config = new File(spec.localPath);
        if (!config.isFile() || !config.canRead() || !"config.json".equals(config.getName())) {
            return error(404, "model_file_not_readable", "MNN embedding models must point to a readable config.json file.");
        }
        try {
            ensureLoaded(config);
        } catch (UnsatisfiedLinkError e) {
            return error(501, "mnn_embeddings_unavailable",
                "This build's MNN native library does not expose embeddings. Update to a build with MNN embedding support.");
        } catch (Throwable t) {
            return error(500, "embedding_inference_failed",
                "MNN embedding model failed to load: " + (t.getMessage() == null ? t.getClass().getSimpleName() : t.getMessage()));
        }
        int effectiveDimensions = dimensions > 0 ? dimensions : outputDimensions;
        if (outputDimensions > 0 && (effectiveDimensions <= 0 || effectiveDimensions > outputDimensions)) {
            return error(400, "invalid_dimensions", "Requested embedding dimensions exceed model output dimensions.");
        }
        try {
            JSONArray data = new JSONArray();
            for (int i = 0; i < inputs.size(); i++) {
                float[] vector = session.embed(inputs.get(i));
                float[] shaped = shapeVector(vector, dimensions);
                JSONObject item = new JSONObject();
                item.put("object", "embedding");
                item.put("index", i);
                JSONArray json = new JSONArray();
                for (float value : shaped) json.put((double) value);
                item.put("embedding", json);
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
            response.put("_backend", TaiModelSpec.BACKEND_MNN_LLM);
            response.put("_runtime", "mnn-embedding");
            return response;
        } catch (Throwable t) {
            return error(500, "embedding_inference_failed",
                "MNN embedding inference failed: " + (t.getMessage() == null ? t.getClass().getSimpleName() : t.getMessage()));
        }
    }

    private void ensureLoaded(@NonNull File config) {
        String configPath = config.getAbsolutePath();
        if (session != null && configPath.equals(loadedConfigPath) && session.isLoaded()) return;
        close();
        MnnEmbeddingSession created = new MnnEmbeddingSession();
        created.load(configPath);
        session = created;
        loadedConfigPath = configPath;
        outputDimensions = created.dim();
    }

    /**
     * Optional Matryoshka-style truncation: shorten to the requested size when asked. The returned
     * vector is always L2-normalized, truncated or not, matching {@link LiteRtEmbeddingRuntime}.
     */
    @NonNull
    private float[] shapeVector(@NonNull float[] vector, int dimensions) {
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
        if (session != null) {
            try { session.release(); } catch (Throwable ignored) { }
            session = null;
        }
        loadedConfigPath = null;
        outputDimensions = 0;
    }
}
