package com.termux.app.terminal.inappkeyboard.voice;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.ai.TaiDeviceCapabilities;
import com.termux.ai.TaiManager;
import com.termux.ai.TaiModelRegistry;
import com.termux.ai.TaiModelSpec;
import com.termux.ai.TaiModelStore;
import com.termux.ai.TaiRuntimePresence;
import com.termux.shared.logger.Logger;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Map;

/**
 * {@link VoiceTextPolisher} on the local Gemma chat model through the TAI runtime: one
 * non-streamed {@code /v1/chat/completions} per segment at temperature 0, thinking off, with the
 * tight budgets {@link VoicePolishRules} computes. Chat requests run on the runtime's serial chat
 * lane and STT on its own, so a rewrite never holds up the next transcription.
 *
 * <p><b>Model.</b> Gemma 4 E4B when it is installed and the phone meets its RAM recommendation
 * (or E2B is not there to fall back on), else E2B — the same rule the app-drawer category sort
 * uses ({@code CategorySortDialogs.resolveModel}); with neither installed every segment is typed
 * as heard and the log says {@code fallback:no_model}. Resolution happens in {@link #warm}, off
 * the main thread, because the model store reads files.
 *
 * <p><b>Residency.</b> {@link #warm} loads the model as the microphone opens so the first
 * rewrite does not pay the ~12 s load, unless the runtime is loading or generating with another
 * model — then nothing is evicted and the first request autoloads (or is refused) on its own. A
 * refusal ({@code insufficient_memory}, the 409 {@code model_not_loaded} when autoload is off)
 * or a timeout disables polish for the rest of the session rather than paying it per phrase.
 * After the session the model is <em>left resident</em> for the runtime's ordinary idle unload:
 * dictation comes in bursts, and reloading whatever was there before (as the category sort
 * does, once, at the end of a minutes-long job) would thrash a 12 s load on every session.
 */
public final class LocalTaiVoiceTextPolisher implements VoiceTextPolisher {

    private static final String LOG_TAG = "VoiceTextPolisher";

    private final Context appContext;
    /** Resolved in {@link #warm}; empty until then. */
    @NonNull private volatile String modelId = "";
    /** Once set, every segment falls back with this reason and the runtime is not asked again. */
    @Nullable private volatile String unavailableReason;

    public LocalTaiVoiceTextPolisher(@NonNull Context context) {
        this.appContext = context.getApplicationContext();
    }

    /**
     * The installed Gemma this feature would use — E4B when the phone meets its RAM
     * recommendation (or E2B is not there to fall back on), else E2B — or {@code null} when neither
     * is installed. Reads the model store: not for the main thread.
     */
    @Nullable
    static String resolveModelId(@NonNull Context context) {
        Map<String, TaiModelSpec> installed = new TaiModelStore(context).getDownloadedReadableModels();
        TaiModelSpec e4b = installed.get(TaiModelRegistry.MODEL_GEMMA_4_E4B_IT);
        TaiModelSpec e2b = installed.get(TaiModelRegistry.MODEL_GEMMA_4_E2B_IT);
        if (e4b != null && (e2b == null
                || TaiDeviceCapabilities.detect(context).checkModelCapability(e4b).warning == null)) return e4b.id;
        if (e2b != null) return e2b.id;
        return e4b != null ? e4b.id : null;
    }

    @Override
    public void warm() {
        String resolved;
        try {
            resolved = resolveModelId(appContext);
        } catch (RuntimeException e) {
            resolved = null;
        }
        if (resolved == null) {
            unavailableReason = "no_model";
            Logger.logInfo(LOG_TAG, "polish off: no Gemma model installed");
            return;
        }
        modelId = resolved;
        TaiRuntimePresence.Snapshot presence = TaiRuntimePresence.read(appContext);
        if (presence.loaded && resolved.equals(presence.modelId)) return;
        if ((presence.loading || presence.generating) && !resolved.equals(presence.modelId)) {
            // Someone else's model is mid-load or mid-answer: not ours to evict. The first
            // rewrite autoloads if the runtime lets it, and falls back if not.
            Logger.logInfo(LOG_TAG, "polish warm skipped: runtime busy with " + presence.modelId);
            return;
        }
        long start = System.nanoTime();
        try {
            JSONObject request = new JSONObject();
            request.put("model", resolved);
            JSONObject result = TaiManager.getInstance(appContext).loadModel(request.toString());
            VoiceInputSession.Failure failure = VoiceInputSession.Failure.of(result);
            long loadMs = (System.nanoTime() - start) / 1_000_000L;
            if (failure != null) {
                unavailableReason = failure.code;
                Logger.logWarn(LOG_TAG, "polish off: load refused after " + loadMs + " ms: "
                    + failure.code + ": " + failure.message);
                return;
            }
            Logger.logInfo(LOG_TAG, "polish warm: model=" + resolved + " loadMs=" + loadMs);
        } catch (JSONException | RuntimeException e) {
            unavailableReason = "load_failed";
            Logger.logWarn(LOG_TAG, "polish off: load failed: " + e.getMessage());
        }
    }

    @NonNull
    @Override
    public Result polish(@NonNull String text, long timeoutMs) {
        String reason = unavailableReason;
        if (reason != null) return Result.fallback(text, reason);
        String model = modelId;
        if (model.isEmpty()) return Result.fallback(text, "not_warmed");
        TaiRuntimePresence.Snapshot presence = TaiRuntimePresence.read(appContext);
        if ((presence.loading || presence.generating) && !model.equals(presence.modelId)) {
            return Result.fallback(text, "runtime_busy");
        }
        try {
            JSONObject response = TaiManager.getInstance(appContext)
                .openAiChatCompletions(request(model, text).toString(), timeoutMs);
            VoiceInputSession.Failure failure = VoiceInputSession.Failure.of(response);
            if (failure != null) {
                if (disablesForSession(failure.code)) unavailableReason = failure.code;
                return Result.fallback(text, failure.code);
            }
            String accepted = VoicePolishRules.accept(text, VoicePolishRules.contentOf(response));
            return accepted == null ? Result.fallback(text, "unusable_output") : Result.polished(accepted);
        } catch (JSONException | RuntimeException e) {
            return Result.fallback(text, "exception");
        }
    }

    /**
     * Refusals that would come back the same for every later segment — and a timeout, after
     * which the runtime is still busy with this very request, so the next one would only queue
     * behind it and time out too.
     */
    private static boolean disablesForSession(@NonNull String code) {
        switch (code) {
            case "insufficient_memory":
            case "model_not_loaded":
            case "model_not_found":
            case "tai_runtime_timeout":
            case "tai_runtime_unavailable":
            case "runtime_client_unavailable":
                return true;
            default:
                return false;
        }
    }

    @NonNull
    static JSONObject request(@NonNull String model, @NonNull String text) throws JSONException {
        JSONObject message = new JSONObject();
        message.put("role", "user");
        message.put("content", VoicePolishRules.prompt(text));
        JSONArray messages = new JSONArray();
        messages.put(message);
        JSONObject request = new JSONObject();
        request.put("model", model);
        request.put("messages", messages);
        request.put("temperature", 0);
        request.put("max_tokens", VoicePolishRules.maxTokens(text));
        request.put("stream", false);
        request.put("thinking", false);
        return request;
    }
}
