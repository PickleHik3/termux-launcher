package com.termux.ai;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * The pure rules the feature callers share when they talk to {@link TaiManager} (tai-device-tiers
 * spec §4.5): which requests go to the remote provider, the private flag that keeps the user's
 * system prompt out of a request, the body the app-category sort sends, and when a sort must load
 * its model. Kept apart from {@code TaiManager} so each rule is a plain function to test.
 */
public final class TaiCallerRequests {
    private TaiCallerRequests() {}

    /**
     * Private request flag: {@code true} keeps the user's TAI system prompt out of this request.
     * {@code TaiManager} reads it where it adds the system prompt and strips it before the body
     * reaches a backend or the remote provider.
     */
    public static final String NO_SYSTEM_PROMPT = "_tai_no_system_prompt";

    /**
     * The request field that names the feature ({@link TaiFunction#id()}): TAI then loads by that
     * feature's {@link TaiFeaturePlan}. Dawn and the CLI may send it too.
     */
    public static final String FUNCTION = "function";

    /** The TAI-local extensions a remote server has no use for. */
    private static final String[] LOCAL_ONLY_KEYS = {"accelerator", "speculative_decoding", "thinking", "load_class", FUNCTION};

    /** The feature a request names, or {@code null} when it names none (it then loads as it always did). */
    @Nullable
    public static TaiFunction featureOf(@Nullable JSONObject request) {
        return request == null ? null : TaiFunction.fromId(request.optString(FUNCTION, ""));
    }

    /** True for a model name in the remote provider's namespace ({@code remote/<id>}). */
    public static boolean isRemoteModel(@Nullable String model) {
        return model != null && model.startsWith(TaiRemoteProvider.MODEL_PREFIX);
    }

    /** True when the request body names a remote model: the routing seam in {@code TaiManager}. */
    public static boolean isRemoteRequest(@Nullable JSONObject request) {
        return request != null && isRemoteModel(request.optString("model", ""));
    }

    /** As {@link #isRemoteRequest(JSONObject)} on a body string; an unparseable body is local. */
    public static boolean isRemoteRequest(@Nullable String body) {
        if (body == null) return false;
        try {
            return isRemoteRequest(new JSONObject(body));
        } catch (JSONException e) {
            return false;
        }
    }

    /** True when the request asks for no user system prompt. */
    public static boolean wantsNoSystemPrompt(@Nullable JSONObject request) {
        return request != null && request.optBoolean(NO_SYSTEM_PROMPT, false);
    }

    /** Removes the private flag from {@code request}, in place. */
    @NonNull
    public static JSONObject stripPrivateFlags(@NonNull JSONObject request) {
        request.remove(NO_SYSTEM_PROMPT);
        return request;
    }

    /**
     * The body handed to the remote provider: a copy without the private flag and without the
     * TAI-local fields ({@code accelerator}, {@code speculative_decoding}, {@code thinking},
     * {@code load_class}). {@code TaiRemoteClient} also drops {@code context_window} and every {@code _tai*} key.
     */
    @NonNull
    public static String remoteBody(@NonNull JSONObject request) throws JSONException {
        JSONObject copy = new JSONObject(request.toString());
        stripPrivateFlags(copy);
        for (String key : LOCAL_ONLY_KEYS) copy.remove(key);
        return copy.toString();
    }

    /**
     * The app-category request for one app. Thinking is always off (the 24-token cap would cut a
     * thinking answer short, whatever the global switch says) and the user's system prompt is kept out
     * of the classification. Speculative decoding and the window are load settings the sort sets when
     * it loads the model ({@code CategorySortLoadPolicy}); a chat on a resident model never reloads, so
     * they are not sent here.
     *
     * @param model the local model id or {@code remote/<id>}; empty for the default
     * @param accelerator {@code gpu} or {@code cpu}, or {@code null} to leave it to the runtime
     */
    @NonNull
    public static JSONObject categoryBody(@Nullable String model, @Nullable String accelerator,
                                          @NonNull String prompt, int maxTokens) throws JSONException {
        JSONObject message = new JSONObject();
        message.put("role", "user");
        message.put("content", prompt);
        JSONObject request = new JSONObject();
        if (model != null && !model.trim().isEmpty()) request.put("model", model);
        request.put("messages", new JSONArray().put(message));
        request.put("temperature", 0);
        request.put("max_tokens", maxTokens);
        request.put("stream", false);
        request.put("thinking", false);
        request.put(NO_SYSTEM_PROMPT, true);
        if (accelerator != null && !accelerator.trim().isEmpty() && !isRemoteModel(model)) {
            request.put("accelerator", accelerator);
        }
        return request;
    }

    /**
     * Whether a sort has to load its model: not when the resident chat model is the very one it
     * wants. {@code TaiManager.loadModel} always reloads, which for a model already warm costs
     * seconds and a second memory peak.
     */
    public static boolean needsLoad(@Nullable String residentModelId, @Nullable String wantedModelId) {
        if (wantedModelId == null || wantedModelId.trim().isEmpty()) return false;
        return !wantedModelId.equals(residentModelId);
    }

    /**
     * What a sort does with the runtime once done: {@code UNLOAD} when nothing was resident before,
     * {@code RELOAD} when another model was, {@code KEEP} when the resident model was the sort's own.
     */
    public enum Restore { KEEP, UNLOAD, RELOAD }

    /**
     * {@code sortModel} {@code null} means a remote sort, which never touched the runtime: keep.
     * A sort that found its own model resident keeps it; the sort cannot tell it loaded it.
     */
    @NonNull
    public static Restore restoreAfterSort(@Nullable String residentBefore, @Nullable String sortModel) {
        if (sortModel == null || sortModel.trim().isEmpty() || isRemoteModel(sortModel)) return Restore.KEEP;
        if (residentBefore == null) return Restore.UNLOAD;
        return residentBefore.equals(sortModel) ? Restore.KEEP : Restore.RELOAD;
    }
}
