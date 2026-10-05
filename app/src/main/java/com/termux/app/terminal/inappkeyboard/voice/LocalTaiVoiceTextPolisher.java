package com.termux.app.terminal.inappkeyboard.voice;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.ai.TaiDeviceCapabilities;
import com.termux.ai.TaiFunction;
import com.termux.ai.TaiFunctionModels;
import com.termux.ai.TaiManager;
import com.termux.ai.TaiModelSpec;
import com.termux.ai.TaiModelStore;
import com.termux.ai.TaiRuntimePresence;
import com.termux.shared.logger.Logger;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * {@link VoiceTextPolisher} on the local Gemma chat model through the TAI runtime: one
 * non-streamed {@code /v1/chat/completions} for the whole session at temperature 0, thinking off,
 * with the budgets and the prompt for the chosen cleanup level that {@link VoicePolishRules}
 * computes. Chat requests run on the runtime's serial chat lane and STT on its own, so the load
 * never holds up a transcription.
 *
 * <p><b>Model.</b> The TIDY_DICTATION function's pick, resolved by {@link TaiFunctionModels} (the
 * "Cleanup model" keyboard setting is its entry in the picker sheet): Automatic is Gemma 4 E2B on
 * Tier 2 and 3 (the 2026-09-27 benchmark on pong: E2B cleans a 111 s dictation in ~10 s and keeps the
 * speaker's words, E4B is three times slower), and raw text on Tier 1. A {@code remote/<id>} pick
 * sends the request to the remote provider and never loads a model; a raw-text pick skips polishing.
 * With nothing installed the session stays as heard and the log says {@code fallback:no_model}.
 * Resolution happens in {@link #warm}, off the main thread, because the model store reads files.
 * The request asks for speculative decoding, which applies when the call loads the model: the pong
 * benchmark of 2026-10-05 had cleanup 1.9 times faster (E2B, 146 words: 7.3 s to 3.6 s) for about
 * 1.5 s more load. The context window stays Automatic.
 *
 * <p><b>Residency.</b> {@link #warm} loads the model as the microphone opens so the pass at the
 * end does not pay the load, unless the runtime is loading or generating with another model —
 * then nothing is evicted and the request autoloads (or is refused) on its own. After the session
 * the model is <em>left resident</em> for the runtime's ordinary idle unload: dictation comes in
 * bursts, and reloading whatever was there before would thrash a multi-second load on every
 * session.
 */
public final class LocalTaiVoiceTextPolisher implements VoiceTextPolisher {

    private static final String LOG_TAG = "VoiceTextPolisher";

    private final Context appContext;
    /** {@link VoicePolishRules#LEVEL_LIGHT} or {@link VoicePolishRules#LEVEL_POLISHED}. */
    @NonNull private final String level;
    /** Resolved in {@link #warm}; empty until then. */
    @NonNull private volatile String modelId = "";
    /** Once set, every request falls back with this reason and the runtime is not asked again. */
    @Nullable private volatile String unavailableReason;
    /** Set in {@link #warm}: the pick is a remote model, so requests carry no accelerator and wait for no load. */
    private volatile boolean remote;
    /** The accelerator the local pick resolved to; {@code null} for the runtime's own choice. */
    @Nullable private volatile String accelerator;

    /** @param level the "Cleanup level" setting; anything unknown reads as Polished. */
    public LocalTaiVoiceTextPolisher(@NonNull Context context, @Nullable String level) {
        this.appContext = context.getApplicationContext();
        this.level = VoicePolishRules.normalizeLevel(level);
    }

    /** What one session does: ask a local model, ask the remote provider, or leave the text alone. */
    static final class Plan {
        /** The {@code model} the requests name: a local id, or {@code remote/<id>}; empty when polishing is skipped. */
        @NonNull final String model;
        /** {@code gpu} or {@code cpu} for a local model, else {@code null}. */
        @Nullable final String accelerator;
        final boolean remote;
        /** Why polishing is skipped ({@code raw_text}, {@code no_model}), else {@code null}. */
        @Nullable final String skipReason;

        Plan(@NonNull String model, @Nullable String accelerator, boolean remote, @Nullable String skipReason) {
            this.model = model;
            this.accelerator = accelerator;
            this.remote = remote;
            this.skipReason = skipReason;
        }
    }

    /** The pure mapping from the function's resolution to what a session does. */
    @NonNull
    static Plan plan(@NonNull TaiFunctionModels.Resolution resolution) {
        if (resolution.isRemote()) return new Plan(resolution.remoteModel, null, true, null);
        if (resolution.modelId != null) return new Plan(resolution.modelId, resolution.accelerator, false, null);
        boolean raw = resolution.without == com.termux.ai.TaiTierPolicy.WithoutModel.RAW_TEXT
            || resolution.without == com.termux.ai.TaiTierPolicy.WithoutModel.OFF;
        return new Plan("", null, false, raw ? "raw_text" : "no_model");
    }

    /**
     * What this session would do: the TIDY_DICTATION function's resolution. Reads the model store and
     * settings: not for the main thread.
     */
    @NonNull
    public static Plan resolvePlan(@NonNull Context context) {
        return plan(TaiFunctionModels.forContext(context).resolve(TaiFunction.TIDY_DICTATION));
    }

    /**
     * Every installed model this feature could be pointed at: readable downloads plus imported
     * user models, filtered to a runnable text-chat endpoint that is not speech-to-text — the same
     * filter {@code TaiManager.openAiModels} applies (MNN skipped when the device cannot run it).
     * Reads the model store: not for the main thread.
     */
    @NonNull
    public static Map<String, TaiModelSpec> installedChatModels(@NonNull Context context) {
        TaiModelStore store = new TaiModelStore(context);
        Map<String, TaiModelSpec> all = new LinkedHashMap<>();
        all.putAll(store.getDownloadedReadableModels());
        all.putAll(store.getInstalledUserModels());
        boolean mnnSupported = TaiDeviceCapabilities.detect(context).mnnSupported;
        Map<String, TaiModelSpec> chatModels = new LinkedHashMap<>();
        for (Map.Entry<String, TaiModelSpec> entry : all.entrySet()) {
            TaiModelSpec spec = entry.getValue();
            if (TaiModelSpec.BACKEND_MNN_LLM.equals(spec.backend) && !mnnSupported) continue;
            if (!spec.endpointCapabilities.contains(TaiModelSpec.CAPABILITY_TEXT_CHAT)) continue;
            if (spec.endpointCapabilities.contains(TaiModelSpec.CAPABILITY_SPEECH_TO_TEXT)) continue;
            chatModels.put(entry.getKey(), spec);
        }
        return chatModels;
    }

    @Override
    public void warm() {
        Plan plan;
        try {
            plan = resolvePlan(appContext);
        } catch (RuntimeException e) {
            plan = new Plan("", null, false, "no_model");
        }
        if (plan.skipReason != null) {
            unavailableReason = plan.skipReason;
            Logger.logInfo(LOG_TAG, "polish off: " + plan.skipReason);
            return;
        }
        final String resolved = plan.model;
        modelId = resolved;
        remote = plan.remote;
        accelerator = plan.accelerator;
        // A remote model loads nothing here: the request goes to the provider when the text is ready.
        if (plan.remote) {
            Logger.logInfo(LOG_TAG, "polish warm skipped: remote model " + resolved);
            return;
        }
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
            if (plan.accelerator != null) request.put("accelerator", plan.accelerator);
            request.put("speculative_decoding", true);
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
        // A remote request never touches the local runtime, so a busy runtime is no reason to wait.
        if (!remote) {
            TaiRuntimePresence.Snapshot presence = TaiRuntimePresence.read(appContext);
            if ((presence.loading || presence.generating) && !model.equals(presence.modelId)) {
                return Result.fallback(text, "runtime_busy");
            }
        }
        try {
            JSONObject response = TaiManager.getInstance(appContext)
                .openAiChatCompletions(request(model, accelerator, level, text).toString(), timeoutMs);
            VoiceInputSession.Failure failure = VoiceInputSession.Failure.of(response);
            if (failure != null) {
                if (disablesForSession(failure.code)) unavailableReason = failure.code;
                return Result.fallback(text, failure.code);
            }
            String content = VoicePolishRules.contentOf(response);
            String accepted = VoicePolishRules.accept(text, content);
            if (accepted != null) return Result.polished(accepted);
            // Kept apart in the log: a refusal or an answer is the model misbehaving, not an empty reply.
            boolean guarded = content != null && !content.trim().isEmpty()
                && VoicePolishRules.looksLikeRefusalOrAnswer(text, content);
            return Result.fallback(text, guarded ? "refusal_or_answer" : "unusable_output");
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

    /**
     * The cleanup request. Speculative decoding is on (it only takes effect when this call loads the
     * model); the context window is left Automatic; {@code accelerator} is the function's resolved one
     * and is left out for a remote model.
     */
    @NonNull
    static JSONObject request(@NonNull String model, @Nullable String accelerator, @Nullable String level,
                              @NonNull String text) throws JSONException {
        JSONObject system = new JSONObject();
        system.put("role", "system");
        system.put("content", VoicePolishRules.instructions(level, text));
        JSONObject message = new JSONObject();
        message.put("role", "user");
        message.put("content", VoicePolishRules.prompt(text));
        JSONArray messages = new JSONArray();
        messages.put(system);
        messages.put(message);
        JSONObject request = new JSONObject();
        request.put("model", model);
        request.put("messages", messages);
        request.put("temperature", 0);
        request.put("max_tokens", VoicePolishRules.maxTokens(text));
        request.put("stream", false);
        request.put("thinking", false);
        request.put("speculative_decoding", true);
        if (accelerator != null && !TaiFunctionModels.isRemote(model)) request.put("accelerator", accelerator);
        return request;
    }
}
