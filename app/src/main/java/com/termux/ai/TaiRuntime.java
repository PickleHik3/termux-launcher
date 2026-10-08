package com.termux.ai;

import androidx.annotation.NonNull;

import org.json.JSONException;
import org.json.JSONObject;

public interface TaiRuntime {
    @NonNull TaiRuntimeState getState();
    boolean isModelLoaded(@NonNull String modelId);
    @NonNull JSONObject load(@NonNull TaiModelSpec modelSpec, @NonNull TaiRuntimeOptions options) throws JSONException;
    @NonNull JSONObject unload() throws JSONException;

    /**
     * Unloads the chat model only, and only while it is {@code modelId}; anything else resident stays.
     * A runtime that holds nothing but the chat model unloads as {@link #unload()} does.
     * {@link MultiBackendTaiRuntime} keeps its embeddings, speech and image models.
     */
    @NonNull
    default JSONObject unloadChatModel(@NonNull String modelId) throws JSONException {
        return isModelLoaded(modelId) ? unload() : keptChatModel(this, modelId);
    }

    /** The answer when {@link #unloadChatModel} leaves the runtime as it is: ok, nothing unloaded. */
    @NonNull
    static JSONObject keptChatModel(@NonNull TaiRuntime runtime, @NonNull String modelId) throws JSONException {
        JSONObject result = new JSONObject();
        result.put("ok", true);
        result.put("unloaded", false);
        result.put("modelId", modelId);
        result.put("runtime", runtime.getState().toJson());
        return result;
    }
    @NonNull JSONObject keepWarm(@NonNull TaiModelSpec modelSpec, @NonNull TaiRuntimeOptions options, int minutes) throws JSONException;
    @NonNull JSONObject cancel() throws JSONException;
    @NonNull JSONObject chat(@NonNull String modelId, @NonNull String systemPrompt, @NonNull String userPrompt, @NonNull TaiRuntimeOptions options) throws JSONException;
    @NonNull JSONObject chat(@NonNull String modelId, @NonNull String systemPrompt, @NonNull String userPrompt, @NonNull TaiRuntimeOptions options, @NonNull TaiGenerationCallback callback) throws JSONException;
    @NonNull JSONObject chat(@NonNull String modelId, @NonNull TaiChatRequest request, @NonNull TaiRuntimeOptions options) throws JSONException;
    @NonNull JSONObject chat(@NonNull String modelId, @NonNull TaiChatRequest request, @NonNull TaiRuntimeOptions options, @NonNull TaiGenerationCallback callback) throws JSONException;
    @NonNull JSONObject complete(@NonNull String modelId, @NonNull String prompt, @NonNull TaiRuntimeOptions options) throws JSONException;
    @NonNull JSONObject complete(@NonNull String modelId, @NonNull String prompt, @NonNull TaiRuntimeOptions options, @NonNull TaiGenerationCallback callback) throws JSONException;
}
