package com.termux.ai;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;

/**
 * The {@link TaiRuntimeLink} the app process uses: each call goes to {@link TaiRuntimeService} in
 * {@code :tai_runtime} through {@link TaiRuntimeServiceClient}. The JSON bodies and the
 * {@link TaiRuntimeIpc} operations stay inside this class. A generation carries its id and
 * priority in the message, and its cancel is an {@link TaiRuntimeIpc#OP_CANCEL} with that id.
 */
final class MessengerTaiRuntimeLink implements TaiRuntimeLink {
    /**
     * Turns an app-side load or keep-warm body into the one the runtime process receives: the model
     * resolved here, where the picks live, with its spec and options attached. A generation is not
     * prepared here: the Feature request already built its body.
     */
    interface BodyPreparer {
        @NonNull String prepare(@NonNull String body) throws JSONException;
    }

    /** Status reads return quickly; a busy or hung runtime must not stall the caller. */
    static final long STATUS_TIMEOUT_MS = 8_000L;

    private final TaiRuntimeServiceClient client;
    private final BodyPreparer preparer;

    MessengerTaiRuntimeLink(@NonNull TaiRuntimeServiceClient client, @NonNull BodyPreparer preparer) {
        this.client = client;
        this.preparer = preparer;
    }

    @NonNull
    static String operation(@NonNull Kind kind, boolean stream) {
        if (kind == Kind.CHAT) return stream ? TaiRuntimeIpc.OP_OPENAI_CHAT_STREAM : TaiRuntimeIpc.OP_OPENAI_CHAT;
        return stream ? TaiRuntimeIpc.OP_OPENAI_COMPLETION_STREAM : TaiRuntimeIpc.OP_OPENAI_COMPLETION;
    }

    @NonNull
    @Override
    public Generation generate(@NonNull GenerationRequest request, @NonNull GenerationSink sink) throws JSONException {
        // The body is the Feature request's: its model, options and plan are already in it (plan once).
        String body = request.body;
        TaiRuntimeServiceClient.PendingRequest call = client.startGeneration(
            operation(request.kind, request.stream), body, request.id, request.priority);
        return new Generation() {
            @NonNull
            @Override
            public String id() {
                return request.id;
            }

            @Override
            public void cancel() {
                client.cancelGeneration(request.id);
            }

            @Nullable
            @Override
            public JSONObject await() throws JSONException, IOException {
                if (!request.stream) return client.awaitGeneration(call, sink, request.timeoutMs, body);
                client.drainGeneration(call, sink);
                return null;
            }
        };
    }

    @Override
    public void cancel(@NonNull String generationId) {
        client.cancelGeneration(generationId);
    }

    @NonNull
    @Override
    public JSONObject cancelAll(@NonNull String stopReason) throws JSONException {
        // Over IPC a cancel is always the user's; the runtime records its own stop reasons.
        return client.request(TaiRuntimeIpc.OP_CANCEL, "{}");
    }

    @NonNull
    @Override
    public JSONObject load(@NonNull String body) throws JSONException {
        return client.request(TaiRuntimeIpc.OP_LOAD_MODEL, preparer.prepare(body));
    }

    @NonNull
    @Override
    public JSONObject unload() throws JSONException {
        return client.request(TaiRuntimeIpc.OP_UNLOAD_MODEL, "{}");
    }

    @NonNull
    @Override
    public JSONObject unloadChat(@NonNull String modelId) throws JSONException {
        return client.request(TaiRuntimeIpc.OP_UNLOAD_CHAT_MODEL, new JSONObject().put("model", modelId).toString());
    }

    @NonNull
    @Override
    public JSONObject keepWarm(@NonNull String body) throws JSONException {
        return client.request(TaiRuntimeIpc.OP_KEEP_WARM, preparer.prepare(body));
    }

    @NonNull
    @Override
    public JSONObject status() throws JSONException {
        return client.request(TaiRuntimeIpc.OP_STATUS, "{}", STATUS_TIMEOUT_MS);
    }

    @NonNull
    @Override
    public RuntimeSnapshot runtimeStatus() {
        JSONObject reply = runtimeStatusReply();
        JSONArray residents = reply == null ? null : reply.optJSONArray("residents");
        return new RuntimeSnapshot(stateOf(reply), residents == null ? new JSONArray() : residents);
    }

    @NonNull
    @Override
    public TaiRuntimeState runtimeState() {
        return stateOf(runtimeStatusReply());
    }

    /** The runtime process's full status reply, or {@code null} when it could not be reached. */
    @Nullable
    private JSONObject runtimeStatusReply() {
        try {
            return client.request(TaiRuntimeIpc.OP_RUNTIME_STATUS, "{}", STATUS_TIMEOUT_MS);
        } catch (JSONException e) {
            return null;
        }
    }

    /** The state a runtime status reply carries; nothing loaded for no reply. */
    @NonNull
    static TaiRuntimeState stateOf(@Nullable JSONObject status) {
        if (status == null) return TaiRuntimeState.fromJson(null);
        JSONObject runtimeJson = status.optJSONObject("runtime");
        if (runtimeJson == null) runtimeJson = status.optJSONObject("state");
        return TaiRuntimeState.fromJson(runtimeJson);
    }
}
