package com.termux.ai;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;

/**
 * The bring-your-own-key provider as the rest of the app sees it: one OpenAI-compatible server
 * the user configured in TAI settings, addressed by the model name {@code remote/<model id>}.
 * That namespace stays outside {@link TaiModelSpec}/{@link TaiModelStore} on purpose: a remote
 * model has no file, no backend and no catalogue entry.
 *
 * <p>TODO(remote routing, phase 1d): nothing calls this yet. The seam is
 * {@code TaiManager.openAiChatCompletions} and its stream variant, before
 * {@code shouldDelegateRuntime()}: a request whose model is {@code remote/<id>} (or a function
 * whose pick is remote) goes to {@link #chatCompletions}/{@link #stream} here, in the app
 * process, and the {@code :tai_runtime} process is never woken. The feature callers (categories,
 * wallpaper reader, dictation cleanup) follow through the per-function picker.
 */
public final class TaiRemoteProvider {
    public static final String MODEL_PREFIX = "remote/";

    private final Context context;
    private final TaiRemoteSettings settings;

    public TaiRemoteProvider(@NonNull Context context) {
        this.context = context.getApplicationContext();
        this.settings = new TaiRemoteSettings(this.context);
    }

    @NonNull
    public TaiRemoteSettings settings() {
        return settings;
    }

    public boolean isConfigured() {
        return settings.isConfigured();
    }

    /** The server's own model id, without the {@code remote/} prefix; empty when unset. */
    @NonNull
    public String modelId() {
        return settings.modelId();
    }

    public boolean understandsImages() {
        return settings.understandsImages();
    }

    public boolean prefersRemote() {
        return settings.prefersRemote();
    }

    /** {@code remote/<model id>}, or empty when the provider is not configured. */
    @NonNull
    public String requestModelName() {
        return isConfigured() ? MODEL_PREFIX + modelId() : "";
    }

    public static boolean isRemoteModelName(@Nullable String model) {
        return model != null && model.startsWith(MODEL_PREFIX) && model.length() > MODEL_PREFIX.length();
    }

    /** A client for the saved server and key. Reads the key from the keystore; not for the UI thread. */
    @NonNull
    public TaiRemoteClient client() {
        return new TaiRemoteClient(context, settings.baseUrl(), settings.apiKey());
    }

    /**
     * One completion against the remote model; a missing {@code model} becomes the saved one,
     * {@code remote/<id>} becomes {@code <id>}. Errors come back in
     * {@link TaiManager#openAiError}'s shape. Blocking: call it off the main thread.
     */
    @NonNull
    public JSONObject chatCompletions(@NonNull String body, long timeoutMs) throws JSONException {
        if (!isConfigured()) return TaiManager.openAiError(notConfigured());
        return client().chatCompletions(withModel(body), timeoutMs);
    }

    /** The streaming twin of {@link #chatCompletions}, feeding the same sink TaiManager uses. */
    public void stream(@NonNull String body, long timeoutMs, @NonNull TaiManager.OpenAiStreamSink sink)
        throws JSONException, IOException {
        if (!isConfigured()) {
            sink.onEvent(TaiManager.openAiError(notConfigured()));
            sink.onDone();
            return;
        }
        client().chatCompletionsStream(withModel(body), timeoutMs, sink);
    }

    @NonNull
    private String withModel(@NonNull String body) {
        try {
            JSONObject request = new JSONObject(body);
            String model = request.optString("model", "");
            // A named remote/<id> keeps its id (the client drops the prefix); none gets the saved one.
            if (model.isEmpty() || MODEL_PREFIX.equals(model)) request.put("model", modelId());
            return request.toString();
        } catch (JSONException e) {
            return body; // The client answers a non-JSON body with its own 400.
        }
    }

    @NonNull
    private static JSONObject notConfigured() throws JSONException {
        return TaiRemoteClient.source(503, "remote_not_configured", "No remote model is set up.");
    }
}
