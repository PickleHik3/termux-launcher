package com.termux.app.chrome.wallpaper.living;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.ai.TaiManager;
import com.termux.ai.TaiModelSpec;
import com.termux.ai.TaiModelStore;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The real {@link GemmaSceneReader.Chat}: one non-streaming call through
 * {@link TaiManager#openAiChatCompletions(String, long)}. The only place the living-still code
 * touches the TAI. Blocking; call it off the main thread.
 */
public final class TaiGemmaChat implements GemmaSceneReader.Chat {
    @NonNull private final TaiManager mManager;

    public TaiGemmaChat(@NonNull Context context) {
        mManager = TaiManager.getInstance(context.getApplicationContext());
    }

    /** True when the Gemma 4 E4B files are on the phone (downloaded or imported). */
    public static boolean installed(@NonNull Context context) {
        TaiModelStore store = new TaiModelStore(context.getApplicationContext());
        Map<String, TaiModelSpec> models = new LinkedHashMap<>(store.getDownloadedReadableModels());
        models.putAll(store.getInstalledUserModels());
        for (TaiModelSpec spec : models.values()) {
            if (GemmaSceneReader.MODEL_ID.equals(spec.id)) return true;
        }
        return false;
    }

    @NonNull
    @Override
    public String complete(@NonNull String requestBody, long timeoutMs) throws IOException {
        String visionId = null;
        String before = null;
        try {
            visionId = new JSONObject(requestBody).optString("model", null);
            before = loadedModelId();
        } catch (JSONException ignored) {
            // the call below reports a malformed body
        }
        try {
            return ask(requestBody, timeoutMs);
        } finally {
            // Gemma 4 E4B with its vision encoder held about 3.9 GB on pong (CPU, 4096 window).
            // When this step loaded it, free it now instead of at the idle unload ten minutes on.
            if (visionId != null && !visionId.equals(before)) unloadIfLoaded(visionId);
        }
    }

    @Nullable
    private String loadedModelId() {
        try {
            JSONObject runtime = mManager.runtimeStatus().optJSONObject("runtime");
            String id = runtime == null ? null : runtime.optString("loadedModelId", null);
            return id == null || id.isEmpty() || "null".equals(id) ? null : id;
        } catch (JSONException | RuntimeException e) {
            return null;
        }
    }

    private void unloadIfLoaded(@NonNull String modelId) {
        if (!modelId.equals(loadedModelId())) return;
        try {
            mManager.unloadModel();
        } catch (JSONException | RuntimeException ignored) {
            // the idle unload still frees it
        }
    }

    @NonNull
    private String ask(@NonNull String requestBody, long timeoutMs) throws IOException {
        try {
            JSONObject response = mManager.openAiChatCompletions(requestBody, timeoutMs);
            JSONObject error = response.optJSONObject("error");
            if (error != null) throw new IOException(error.optString("message", "Gemma error"));
            JSONArray choices = response.optJSONArray("choices");
            JSONObject choice = choices == null ? null : choices.optJSONObject(0);
            JSONObject message = choice == null ? null : choice.optJSONObject("message");
            if (message == null) throw new IOException("Gemma sent no message");
            Object content = message.opt("content");
            if (content instanceof String) return (String) content;
            if (content instanceof JSONArray) {
                StringBuilder sb = new StringBuilder();
                JSONArray parts = (JSONArray) content;
                for (int i = 0; i < parts.length(); i++) {
                    JSONObject part = parts.optJSONObject(i);
                    if (part != null) sb.append(part.optString("text", ""));
                }
                return sb.toString();
            }
            throw new IOException("Gemma sent no text");
        } catch (JSONException e) {
            throw new IOException(e);
        }
    }
}
