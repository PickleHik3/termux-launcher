package com.termux.app.chrome.wallpaper.living;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.ai.TaiCallerRequests;
import com.termux.ai.TaiManager;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;

/**
 * The real {@link SceneReader.Chat}: one non-streaming call through
 * {@link TaiManager#openAiChatCompletions(String, long)}. The only place the living-still code
 * touches the TAI. Blocking; call it off the main thread.
 */
public final class TaiGemmaChat implements SceneReader.Chat {
    @NonNull private final TaiManager mManager;
    @Nullable private volatile String mLastAccelerator;
    @Nullable private volatile String mLastFallbackReason;

    public TaiGemmaChat(@NonNull Context context) {
        mManager = TaiManager.getInstance(context.getApplicationContext());
    }

    @NonNull
    @Override
    public String complete(@NonNull String requestBody, long timeoutMs) throws IOException {
        String visionId = null;
        String before = null;
        try {
            visionId = new JSONObject(requestBody).optString("model", null);
            // A remote reader never touches the :tai_runtime process: no status read, no unload.
            if (TaiCallerRequests.isRemoteModel(visionId)) {
                mLastAccelerator = null;
                mLastFallbackReason = null;
                return ask(requestBody, timeoutMs);
            }
            before = loadedModelId();
        } catch (JSONException ignored) {
            // the call below reports a malformed body
        }
        try {
            String text = ask(requestBody, timeoutMs);
            // Read before the unload below: the status describes the load this call used.
            noteBackend();
            return text;
        } finally {
            // Gemma 4 E4B with its vision encoder held about 3.9 GB on pong (CPU, 4096 window).
            // When this step loaded it, free it now instead of at the idle unload ten minutes on.
            if (visionId != null && !visionId.equals(before)) unloadIfLoaded(visionId);
        }
    }

    @Nullable
    @Override
    public String lastAccelerator() {
        return mLastAccelerator;
    }

    @Nullable
    @Override
    public String lastFallbackReason() {
        return mLastFallbackReason;
    }

    private void noteBackend() {
        mLastAccelerator = null;
        mLastFallbackReason = null;
        try {
            JSONObject runtime = mManager.runtimeStatus().optJSONObject("runtime");
            if (runtime == null) return;
            String backend = runtime.optString("backend", "");
            if (!backend.isEmpty() && !"null".equals(backend)) mLastAccelerator = backend;
            String reason = runtime.isNull("backendFallbackReason") ? "" : runtime.optString("backendFallbackReason", "");
            if (!reason.isEmpty()) mLastFallbackReason = reason;
        } catch (JSONException | RuntimeException ignored) {
            // the recipe simply omits the backend
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
