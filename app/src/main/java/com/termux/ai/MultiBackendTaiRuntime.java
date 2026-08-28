package com.termux.ai;

import android.content.Context;

import androidx.annotation.NonNull;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.List;
import java.util.Locale;

public class MultiBackendTaiRuntime implements TaiRuntime {
    private final LiteRtTaiRuntime liteRt;
    private final MnnTaiRuntime mnn;
    private final LiteRtEmbeddingRuntime embeddings;
    private final MnnEmbeddingRuntime mnnEmbeddings;
    private TaiRuntime activeAssistant;

    public MultiBackendTaiRuntime(@NonNull Context context) {
        liteRt = new LiteRtTaiRuntime(context);
        mnn = new MnnTaiRuntime(context);
        embeddings = new LiteRtEmbeddingRuntime();
        mnnEmbeddings = new MnnEmbeddingRuntime();
        activeAssistant = liteRt;
    }

    @NonNull @Override public synchronized TaiRuntimeState getState() {
        return activeAssistant.getState();
    }

    @Override public synchronized boolean isModelLoaded(@NonNull String modelId) {
        return runtimeForId(modelId).isModelLoaded(modelId);
    }

    @NonNull @Override public synchronized JSONObject load(@NonNull TaiModelSpec model, @NonNull TaiRuntimeOptions options) throws JSONException {
        TaiRuntime target = runtimeForModel(model);
        if (target != activeAssistant) {
            TaiRuntimeState current = activeAssistant.getState();
            if (current.activeGeneration) return error("generation_active", "Cancel active generation before switching AI backends.");
            activeAssistant.unload();
            activeAssistant = target;
        }
        return target.load(model, options);
    }

    @NonNull @Override public synchronized JSONObject unload() throws JSONException {
        JSONObject result = activeAssistant.unload();
        embeddings.close();
        mnnEmbeddings.close();
        return result;
    }

    @NonNull @Override public synchronized JSONObject keepWarm(@NonNull TaiModelSpec model, @NonNull TaiRuntimeOptions options, int minutes) throws JSONException {
        TaiRuntime target = runtimeForModel(model);
        if (target != activeAssistant) {
            TaiRuntimeState current = activeAssistant.getState();
            if (current.activeGeneration) return error("generation_active", "Cancel active generation before switching AI backends.");
            activeAssistant.unload();
            activeAssistant = target;
        }
        return target.keepWarm(model, options, minutes);
    }

    @NonNull @Override public synchronized JSONObject cancel() throws JSONException {
        return activeAssistant.cancel();
    }

    // Native generation is long-running. Do not hold this router monitor while it runs, otherwise
    // cancel/unload cannot reach the active backend until generation has already finished.
    @NonNull @Override public JSONObject chat(@NonNull String id, @NonNull String system, @NonNull String user, @NonNull TaiRuntimeOptions options) throws JSONException { return runtimeForId(id).chat(id, system, user, options); }
    @NonNull @Override public JSONObject chat(@NonNull String id, @NonNull String system, @NonNull String user, @NonNull TaiRuntimeOptions options, @NonNull TaiGenerationCallback callback) throws JSONException { return runtimeForId(id).chat(id, system, user, options, callback); }
    @NonNull @Override public JSONObject chat(@NonNull String id, @NonNull TaiChatRequest request, @NonNull TaiRuntimeOptions options) throws JSONException { return runtimeForId(id).chat(id, request, options); }
    @NonNull @Override public JSONObject chat(@NonNull String id, @NonNull TaiChatRequest request, @NonNull TaiRuntimeOptions options, @NonNull TaiGenerationCallback callback) throws JSONException { return runtimeForId(id).chat(id, request, options, callback); }
    @NonNull @Override public JSONObject complete(@NonNull String id, @NonNull String prompt, @NonNull TaiRuntimeOptions options) throws JSONException { return runtimeForId(id).complete(id, prompt, options); }
    @NonNull @Override public JSONObject complete(@NonNull String id, @NonNull String prompt, @NonNull TaiRuntimeOptions options, @NonNull TaiGenerationCallback callback) throws JSONException { return runtimeForId(id).complete(id, prompt, options, callback); }

    @NonNull
    public synchronized JSONObject embed(@NonNull String modelId, @NonNull String input) throws JSONException {
        JSONObject error = new JSONObject();
        error.put("message", "Embeddings are not available for the active LiteRT/MNN backends.");
        error.put("type", "invalid_request_error");
        error.put("code", "capability_not_supported");
        JSONObject response = new JSONObject();
        response.put("error", error);
        response.put("_statusCode", 400);
        return response;
    }

    @NonNull
    public synchronized JSONObject embed(@NonNull TaiModelSpec model, @NonNull List<String> inputs, int dimensions) throws JSONException {
        if (isLiteRtEmbeddingFlatbuffer(model)) return embeddings.embed(model, inputs, dimensions);
        if (isMnnEmbeddingModel(model)) return mnnEmbeddings.embed(model, inputs, dimensions);
        if (inputs.size() == 1 && dimensions <= 0) return embed(model.id, inputs.get(0));
        JSONObject error = new JSONObject();
        error.put("message", "Embeddings are not available for model '" + model.id + "'.");
        error.put("type", "invalid_request_error");
        error.put("param", "model");
        error.put("code", "capability_not_supported");
        JSONObject response = new JSONObject();
        response.put("error", error);
        response.put("_statusCode", 400);
        return response;
    }

    private synchronized TaiRuntime runtimeForId(String id) {
        TaiRuntimeState mnnState = mnn.getState();
        if (mnnState.loadedModelId != null && mnnState.loadedModelId.equals(id)) return mnn;
        TaiModelCatalog.CatalogEntry entry = TaiModelCatalog.get(id);
        if (entry != null && TaiModelSpec.BACKEND_MNN_LLM.equals(entry.backend)) return mnn;
        return activeAssistant;
    }

    private TaiRuntime runtimeForModel(TaiModelSpec model) {
        if (TaiModelSpec.BACKEND_MNN_LLM.equals(model.backend)) return mnn;
        return liteRt;
    }

    private boolean isLiteRtEmbeddingFlatbuffer(@NonNull TaiModelSpec model) {
        String path = model.localPath == null ? "" : model.localPath.toLowerCase(Locale.ROOT);
        return TaiModelSpec.BACKEND_LITERT_LM.equals(model.backend)
            && model.capabilities.contains(TaiModelSpec.CAPABILITY_TEXT_EMBEDDINGS)
            && path.endsWith(".tflite");
    }

    private boolean isMnnEmbeddingModel(@NonNull TaiModelSpec model) {
        String path = model.localPath == null ? "" : model.localPath.toLowerCase(Locale.ROOT);
        return TaiModelSpec.BACKEND_MNN_LLM.equals(model.backend)
            && model.capabilities.contains(TaiModelSpec.CAPABILITY_TEXT_EMBEDDINGS)
            && path.endsWith("config.json");
    }

    private JSONObject error(String code, String message) throws JSONException {
        JSONObject result = new JSONObject();
        result.put("ok", false); result.put("error", code); result.put("message", message); result.put("_statusCode", 409);
        return result;
    }
}
