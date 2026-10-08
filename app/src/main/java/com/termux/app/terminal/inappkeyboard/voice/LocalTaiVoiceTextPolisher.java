package com.termux.app.terminal.inappkeyboard.voice;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.ai.TaiCallerRequests;
import com.termux.ai.TaiDeviceCapabilities;
import com.termux.ai.TaiFeaturePlan;
import com.termux.ai.TaiFeaturePlans;
import com.termux.ai.TaiFunction;
import com.termux.ai.TaiFunctionModels;
import com.termux.ai.TaiManager;
import com.termux.ai.TaiModelSpec;
import com.termux.ai.TaiModelStore;
import com.termux.ai.TaiRuntimePresence;
import com.termux.ai.TaiTierPolicy;
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
 * <p><b>Model.</b> Cleanup's {@link TaiFeaturePlan} decides it (the "Cleanup model" keyboard setting is
 * its entry in the picker sheet): the TIDY_DICTATION pick, the remote provider when the pick or the
 * "When to use it" routing says so, or nothing. Light and Polished differ only in the prompt.
 * Automatic is Gemma 4 E2B on Tier 2 and 3 (the 2026-09-27 benchmark on pong: E2B cleans a 111 s
 * dictation in ~10 s and keeps the speaker's words, E4B is three times slower), and raw text on
 * Tier 1. A remote plan never loads a model; a raw-text plan skips polishing. With nothing
 * installed the session stays as heard and the log says {@code fallback:no_model}. With the routing
 * "Only when no local model fits", a local model that cannot load hands the session to the provider.
 * Resolution happens in {@link #warm}, off the main thread, because the plan reads files.
 *
 * <p><b>Load.</b> Requests name the feature, and TAI loads by the plan: its accelerator, its
 * speculative decoding and its 2048-token window. Nothing about the load is decided here.
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
    /** The refusal after which a local-first routing hands the session to the remote provider. */
    private static final String NO_ROOM = "insufficient_memory";

    private final Context appContext;
    /** {@link VoicePolishRules#LEVEL_LIGHT} or {@link VoicePolishRules#LEVEL_POLISHED}. */
    @NonNull private final String level;
    /** Resolved in {@link #warm}; empty until then. */
    @NonNull private volatile String modelId = "";
    /** Once set, every request falls back with this reason and the runtime is not asked again. */
    @Nullable private volatile String unavailableReason;
    /** Set in {@link #warm}: the plan is a remote model, so requests wait for no load. */
    private volatile boolean remote;
    /** The provider to switch to when the local model cannot load; {@code null} without one. */
    @Nullable private volatile String remoteFallback;

    /** @param level the "Cleanup level" setting; anything unknown reads as Polished. */
    public LocalTaiVoiceTextPolisher(@NonNull Context context, @Nullable String level) {
        this.appContext = context.getApplicationContext();
        this.level = VoicePolishRules.normalizeLevel(level);
    }

    /** What one session does: ask a local model, ask the remote provider, or leave the text alone. */
    static final class Plan {
        /** The {@code model} the requests name: a local id, or {@code remote/<id>}; empty when polishing is skipped. */
        @NonNull final String model;
        final boolean remote;
        /** Why polishing is skipped ({@code raw_text}, {@code no_model}), else {@code null}. */
        @Nullable final String skipReason;
        /** {@code remote/<id>} to ask when the local model cannot load; {@code null} without one. */
        @Nullable final String remoteFallback;

        Plan(@NonNull String model, boolean remote, @Nullable String skipReason, @Nullable String remoteFallback) {
            this.model = model;
            this.remote = remote;
            this.skipReason = skipReason;
            this.remoteFallback = remoteFallback;
        }
    }

    /** The pure mapping from cleanup's feature load plan to what a session does. */
    @NonNull
    static Plan plan(@NonNull TaiFeaturePlan feature) {
        String model = feature.requestModel();
        switch (feature.where) {
            case REMOTE:
                return new Plan(model == null ? "" : model, true, null, null);
            case ON_DEVICE:
                return new Plan(model == null ? "" : model, false, null, feature.remoteFallback);
            default:
                boolean raw = feature.without == TaiTierPolicy.WithoutModel.RAW_TEXT
                    || feature.without == TaiTierPolicy.WithoutModel.OFF;
                return new Plan("", false, raw ? "raw_text" : "no_model", null);
        }
    }

    /** What this session would do: cleanup's plan. Reads the model store and settings: not for the main thread. */
    @NonNull
    public static Plan resolvePlan(@NonNull Context context) {
        return plan(TaiFeaturePlans.forContext(context).plan(TaiFunction.TIDY_DICTATION));
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
            plan = new Plan("", false, "no_model", null);
        }
        if (plan.skipReason != null) {
            unavailableReason = plan.skipReason;
            Logger.logInfo(LOG_TAG, "polish off: " + plan.skipReason);
            return;
        }
        final String resolved = plan.model;
        modelId = resolved;
        remote = plan.remote;
        remoteFallback = plan.remoteFallback;
        // A remote model loads nothing here: the request goes to the provider when the text is ready.
        if (plan.remote) {
            Logger.logInfo(LOG_TAG, "polish warm skipped: remote model " + resolved);
            return;
        }
        TaiRuntimePresence.Snapshot presence = TaiRuntimePresence.read(appContext);
        if (presence.loading || presence.generating) {
            // Mid-load or mid-answer: someone else's model is not ours to evict, and our own is
            // already there. The first rewrite autoloads if the runtime lets it, and falls back if not.
            Logger.logInfo(LOG_TAG, "polish warm skipped: runtime busy with " + presence.modelId);
            return;
        }
        long start = System.nanoTime();
        try {
            // The plan's load: TAI reuses a resident model whose window is cleanup's or larger.
            JSONObject request = new JSONObject();
            request.put("model", resolved);
            request.put(TaiCallerRequests.FUNCTION, TaiFunction.TIDY_DICTATION.id());
            JSONObject result = TaiManager.getInstance(appContext).loadModel(request.toString());
            VoiceInputSession.Failure failure = VoiceInputSession.Failure.of(result);
            long loadMs = (System.nanoTime() - start) / 1_000_000L;
            if (failure != null) {
                if (useRemoteFallback(failure.code)) return;
                unavailableReason = failure.code;
                Logger.logWarn(LOG_TAG, "polish off: load refused after " + loadMs + " ms: "
                    + failure.code + ": " + failure.message);
                return;
            }
            Logger.logInfo(LOG_TAG, "polish warm: model=" + resolved + " loadMs=" + loadMs
                + (result.optBoolean("reused", false) ? " (resident)" : ""));
        } catch (JSONException | RuntimeException e) {
            unavailableReason = "load_failed";
            Logger.logWarn(LOG_TAG, "polish off: load failed: " + e.getMessage());
        }
    }

    /**
     * "Only when no local model fits": a local model that has no room hands the session to the remote
     * provider, once. True when it did.
     */
    private boolean useRemoteFallback(@NonNull String code) {
        String fallback = remoteFallback;
        if (remote || fallback == null || !NO_ROOM.equals(code)) return false;
        modelId = fallback;
        remote = true;
        remoteFallback = null;
        Logger.logInfo(LOG_TAG, "polish on the remote model: no room for the local one");
        return true;
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
                .openAiChatCompletions(request(model, level, text).toString(), timeoutMs);
            VoiceInputSession.Failure failure = VoiceInputSession.Failure.of(response);
            if (failure != null) {
                if (useRemoteFallback(failure.code)) return polish(text, timeoutMs);
                if (disablesForSession(failure.code)) unavailableReason = failure.code;
                return Result.fallback(text, failure.code);
            }
            String content = VoicePolishRules.contentOf(response);
            String accepted = VoicePolishRules.accept(text, content, level);
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
     * The cleanup request. A local one names the feature, so TAI loads by cleanup's plan (accelerator,
     * speculative decoding, window), and turns thinking off; a remote one carries none of TAI's own fields.
     * The feature check sends this same request ({@code TaiFeatureCheckRunner}).
     */
    @NonNull
    public static JSONObject request(@NonNull String model, @Nullable String level, @NonNull String text) throws JSONException {
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
        request.put("max_tokens", VoicePolishRules.maxTokens(text, level));
        request.put("stream", false);
        // The TAI-only keys stay off a request to an OpenAI-compatible provider.
        if (!TaiFunctionModels.isRemote(model)) {
            request.put("thinking", false);
            request.put(TaiCallerRequests.FUNCTION, TaiFunction.TIDY_DICTATION.id());
        }
        return request;
    }
}
