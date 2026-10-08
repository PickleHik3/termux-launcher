package com.termux.ai;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.launcher.data.LauncherCategorySortPrompt;
import com.termux.app.terminal.inappkeyboard.voice.LocalTaiVoiceTextPolisher;
import com.termux.app.terminal.inappkeyboard.voice.VoicePolishRules;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Runs the feature check in the app process (tai-feature-load-plan spec, step 2): for each feature in
 * use, the real request its caller sends, named by its {@code function} so it loads by the feature's
 * plan, on each setup {@link TaiFeatureCheck#runs} asks for; the setup's accelerator and speculative
 * decoding are explicit fields of the request. Every run is stored ({@link TaiFeatureCheckStore}) and its
 * GPU outcome settles the phone-wide verdict ({@link TaiGpuVerdict}).
 *
 * <p>The workloads:
 * <ul>
 *   <li>Cleanup: {@link LocalTaiVoiceTextPolisher#request} at the user's level over a 150-word and a
 *       500-word dictation ({@code assets/tai/check}).</li>
 *   <li>App sorting: {@link LauncherCategorySortPrompt} for ten installed apps at the sort's window.</li>
 *   <li>Assistant and Dawn chat: the bench's chat and long-input prompts.</li>
 *   <li>Dawn search: 64 short notes in one embeddings batch.</li>
 *   <li>Read aloud: one sentence, timed to its first sound (synthesised, not played).</li>
 * </ul>
 *
 * <p>The bench's guard keeps the phone safe between runs (battery, heat, a cool-down, the screen left);
 * its events have the harness's shape, so the bench's Run screen shows the check. A runtime that dies
 * under a run is recorded as a crash and the check goes on; an app that dies leaves the store's marker,
 * which the next check records. Blocks; not for the main thread.
 */
public final class TaiFeatureCheckRunner {
    /** Receives the harness-shaped events. */
    public interface Sink {
        void onEvent(@NonNull JSONObject event);
    }

    /** The phase of a run's workload; the load is {@link TaiBenchSuite#PHASE_LOAD}. */
    public static final String PHASE_FEATURE = "feature";
    /** The preset id the Run screen knows a feature check by. */
    public static final String PRESET_ID = "features";
    /** Apps sorted per run. */
    static final int SORT_APPS = 10;
    static final String DICTATION_SHORT_ASSET = "tai/check/dictation_150.txt";
    static final String DICTATION_LONG_ASSET = "tai/check/dictation_500.txt";
    static final String NOTES_ASSET = "tai/check/notes_64.txt";
    /** Read aloud's workload: one ordinary sentence. */
    static final String READ_SENTENCE = "The weather will be dry this afternoon, with a light breeze from the west.";
    private static final String CRASH_CODE = TaiBenchCrashRecovery.CRASH_CODE;

    @NonNull private final Context app;
    @NonNull private final TaiManager manager;
    @NonNull private final TaiFeatureCheckStore store;
    @NonNull private final Sink sink;
    @NonNull private final TaiDeviceConditions conditions;
    @NonNull private final TaiBenchConditionsGuard guard;
    @Nullable private volatile String stopReason;
    /** The window the run's chat model was loaded with ({@link TaiBenchHarness#loadedContextWindow}); {@code 0} when unknown. */
    private int loadedWindow;

    public TaiFeatureCheckRunner(@NonNull Context context, @NonNull Sink sink) {
        this.app = context.getApplicationContext();
        this.manager = TaiManager.getInstance(app);
        this.store = TaiFeatureCheckStore.in(app.getFilesDir());
        this.sink = sink;
        this.conditions = new TaiDeviceConditions(app);
        this.guard = new TaiBenchConditionsGuard(conditions::snapshot, System::currentTimeMillis);
    }

    /**
     * The features the check can measure on this phone now: each one whose plan runs a model on the
     * phone. Dawn chat counts only beside Dawn search, since it is the Dawn notes pairing and otherwise
     * repeats the assistant's runs on the same model.
     */
    @NonNull
    public static List<TaiFunction> featuresInUse(@NonNull TaiFeaturePlans plans) {
        List<TaiFunction> out = new ArrayList<>();
        boolean search = inUse(plans.plan(TaiFunction.EMBEDDINGS));
        for (TaiFunction feature : TaiFeatureCheck.FEATURES) {
            if (feature == TaiFunction.DAWN_CHAT && !search) continue;
            if (inUse(plans.plan(feature))) out.add(feature);
        }
        return out;
    }

    private static boolean inUse(@NonNull TaiFeaturePlan plan) {
        return plan.where == TaiFeaturePlan.Where.ON_DEVICE && plan.modelId != null && plan.backend != null;
    }

    /** Ends the check after the request in progress; that run is recorded as stopped. */
    public void requestStop(@NonNull String reason) {
        if (stopReason == null) stopReason = reason;
        try {
            manager.cancelRuntime();
        } catch (JSONException | RuntimeException ignored) {
        }
    }

    /** Whether the screen that owns the check has left the foreground; see {@link TaiBenchGuard#setHeld}. */
    public void setHeld(boolean held) {
        guard.setHeld(held);
    }

    public void skipCooldown() {
        guard.skipCooldown();
    }

    // ------------------------------------------------------------------------------------ run

    /** One planned run. */
    private static final class Planned {
        @NonNull final TaiFunction feature;
        @NonNull final TaiFeaturePlan plan;
        @NonNull final TaiModelSpec spec;
        @NonNull final TaiFeatureCheck.Variant variant;

        Planned(@NonNull TaiFunction feature, @NonNull TaiFeaturePlan plan, @NonNull TaiModelSpec spec,
                @NonNull TaiFeatureCheck.Variant variant) {
            this.feature = feature;
            this.plan = plan;
            this.spec = spec;
            this.variant = variant;
        }
    }

    /**
     * Checks {@code features} in order and emits the events, then {@code done}. {@code force} skips the
     * start check (battery, heat), as the bench's does.
     */
    public void run(@NonNull List<TaiFunction> features, boolean force) {
        try {
            store.recoverStaleMarker("The app closed during this check.");
        } catch (IOException ignored) {
        }
        if (!force) {
            TaiBenchGuardRules.Snapshot start = conditions.snapshot();
            String reason = TaiBenchGuardRules.startCheck(start);
            if (reason != null) {
                emit(event("error").put("code", "conditions_not_met").put("reason", reason)
                    .put("message", reason)
                    .put("batteryPercent", start.batteryPercent >= 0 ? start.batteryPercent : JSONObject.NULL)
                    .put("charging", start.charging));
                emit(event("done").put("stopped", "conditions_not_met"));
                return;
            }
        }
        List<Planned> runs = plan(features);
        conditions.startThermalListener(() -> requestStop("thermal"));
        try {
            int index = 0;
            int i = 0;
            while (i < runs.size()) {
                TaiFunction feature = runs.get(i).feature;
                List<TaiFeatureCheck.Outcome> outcomes = new ArrayList<>();
                for (; i < runs.size() && runs.get(i).feature == feature; i++) {
                    if (stopReason != null) break;
                    Outcome outcome = runOne(runs.get(i), index++, runs.size());
                    if (outcome.gpu != null) outcomes.add(outcome.gpu);
                    // A runtime that died gives its memory back for about a second before the next load.
                    if (outcome.gpu != null && outcome.gpu.crashed) pause(TaiBenchCrashRecovery.DEFAULT_RESTART_PAUSE_MS);
                }
                settleGpuVerdict(outcomes);
                if (stopReason != null) break;
            }
        } finally {
            conditions.stopThermalListener();
            // The check leaves no chat model of its own behind; the next feature loads what it needs.
            try {
                manager.unloadModel();
            } catch (JSONException | RuntimeException ignored) {
            }
        }
        emit(event("done").put("ok", true).put("preset", PRESET_ID).put("entries", runs.size())
            .put("stopped", stopReason == null ? JSONObject.NULL : stopReason));
    }

    private static void pause(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /** Every run of every feature, in order; a feature whose model is gone is left out. */
    @NonNull
    private List<Planned> plan(@NonNull List<TaiFunction> features) {
        TaiFeaturePlans plans = TaiFeaturePlans.forContext(app);
        TaiModelStore models = new TaiModelStore(app);
        Map<String, TaiModelSpec> installed = new LinkedHashMap<>(models.getDownloadedReadableModels());
        installed.putAll(models.getInstalledUserModels());
        TaiPlatformCaps.GpuPath path = plans.models().env().gpuPath;
        TaiGpuVerdict.State verdict = new TaiEvidenceFiles(app).gpuVerdict();
        boolean gpuUsable = TaiFeatureCheck.gpuUsable(path, verdict);
        List<Planned> out = new ArrayList<>();
        for (TaiFunction feature : features) {
            TaiFeaturePlan plan = plans.plan(feature);
            if (!inUse(plan)) continue;
            TaiModelSpec spec = installed.get(TaiModelVariants.baseModelId(plan.modelId));
            if (spec == null) spec = installed.get(plan.modelId);
            if (spec == null) continue;
            TaiFunctionModels.Resolution resolution = plans.models().resolve(feature);
            boolean declares = resolution.info != null && resolution.info.speculative;
            TaiFeatureCheck.Runs runs = TaiFeatureCheck.runs(feature, plan.accelerator, plan.speculative, declares, gpuUsable);
            for (TaiFeatureCheck.Variant variant : runs.variants) out.add(new Planned(feature, plan, spec, variant));
        }
        return out;
    }

    /** What one run tells the feature's GPU verdict; {@code gpu} is {@code null} when it never loaded. */
    private static final class Outcome {
        @Nullable TaiFeatureCheck.Outcome gpu;
    }

    @NonNull
    private Outcome runOne(@NonNull Planned run, int index, int total) {
        Outcome outcome = new Outcome();
        TaiFeatureCheck.Measurement m = new TaiFeatureCheck.Measurement();
        m.feature = run.feature;
        m.modelId = run.spec.id;
        m.backend = run.spec.backend;
        m.accelerator = run.variant.accelerator;
        m.speculative = run.variant.speculative;
        m.displayName = run.spec.displayName;
        m.timestamp = System.currentTimeMillis();
        m.status = "stopped:incomplete";
        TaiBenchSuite.EntryPlan entry = new TaiBenchSuite.EntryPlan(m.modelId, m.backend, m.accelerator, m.speculative);
        try {
            m.staleKey = TaiFeatureCheckStore.stalenessKey(run.spec);
            emit(event("entry_start").put("index", index).put("total", total).put("entry", new JSONObject()
                .put("modelId", m.modelId).put("backend", m.backend).put("accelerator", m.accelerator)
                .put("speculative", m.speculative).put("feature", m.feature.id()).put("displayName", m.displayName)
                .put("key", TaiFeatureCheck.keyOf(m.feature, m.modelId, m.backend, m.accelerator, m.speculative))));
            if (!consultGuard(TaiBenchSuite.PHASE_LOAD, entry, m)) return outcome;
            guard.entryStarted(entry);
            try {
                store.markInProgress(TaiFeatureCheck.record(m));
            } catch (IOException ignored) {
            }
            boolean crashed = false;
            boolean correct = false;
            try {
                if (!load(run, m)) {
                    crashed = TaiFeatureCheck.STATUS_CRASHED.equals(m.status);
                } else if (consultGuard(PHASE_FEATURE, entry, m)) {
                    correct = workload(run, m);
                    crashed = TaiFeatureCheck.STATUS_CRASHED.equals(m.status);
                }
            } finally {
                guard.entryFinished(entry);
            }
            // Only a run that answered, or a crash, speaks for the GPU: a stopped or refused one says nothing.
            if (crashed || TaiFeatureCheck.STATUS_COMPLETE.equals(m.status)) {
                String ranOn = m.ranOn.isEmpty() ? m.accelerator : m.ranOn;
                outcome.gpu = new TaiFeatureCheck.Outcome(ranOn, crashed, correct && m.passed, m.speculative);
            }
            return outcome;
        } catch (JSONException | RuntimeException e) {
            m.status = "stopped:check_error";
            m.reason = message(e);
            return outcome;
        } finally {
            finish(entry, m);
        }
    }

    /** Stores the run, clears the marker and tells the screen. */
    private void finish(@NonNull TaiBenchSuite.EntryPlan entry, @NonNull TaiFeatureCheck.Measurement m) {
        try {
            m.conditions = guard.entryConditions(entry);
        } catch (JSONException | RuntimeException ignored) {
        }
        JSONObject record;
        try {
            record = TaiFeatureCheck.record(m).put("id", UUID.randomUUID().toString());
        } catch (JSONException e) {
            return;
        }
        boolean stored;
        try {
            store.append(record);
            stored = true;
        } catch (IOException e) {
            stored = false;
        }
        store.clearInProgress();
        emit(event("entry_done").put("record", record).put("stored", stored));
    }

    /** {@code false} when the guard or a stop ended the run; {@code m} says why. */
    private boolean consultGuard(@NonNull String phase, @NonNull TaiBenchSuite.EntryPlan entry,
                                 @NonNull TaiFeatureCheck.Measurement m) {
        while (true) {
            String outside = stopReason;
            if (outside != null) {
                m.status = "stopped:" + outside;
                return false;
            }
            TaiBenchGuard.Decision decision = guard.beforePhase(phase, entry);
            if (TaiBenchGuard.CONTINUE.equals(decision.action)) return true;
            if (TaiBenchGuard.STOP.equals(decision.action)) {
                String reason = decision.reason == null ? "guard" : decision.reason;
                m.status = "stopped:" + reason;
                if (stopReason == null) stopReason = reason;
                return false;
            }
            Ev paused = event("paused").put("phase", phase).put("ms", decision.pauseMs)
                .put("reason", decision.reason == null ? JSONObject.NULL : decision.reason);
            if (decision.detail != null) {
                java.util.Iterator<String> keys = decision.detail.keys();
                while (keys.hasNext()) {
                    String key = keys.next();
                    paused.put(key, decision.detail.opt(key));
                }
            }
            emit(paused);
            try {
                Thread.sleep(decision.pauseMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                m.status = "stopped:interrupted";
                return false;
            }
        }
    }

    // ----------------------------------------------------------------------------------- load

    /**
     * Loads the run's model on its setup and times it: a chat model by an explicit load after the resident
     * one is closed, so the time is a cold load; Dawn search's embedder by one short input, and read
     * aloud's voice by its warm call. {@code false} when the model did not come up; {@code m} says why.
     */
    private boolean load(@NonNull Planned run, @NonNull TaiFeatureCheck.Measurement m) throws JSONException {
        emit(event("phase_start").put("phase", TaiBenchSuite.PHASE_LOAD).put("runs", 1));
        long started = System.nanoTime();
        loadedWindow = 0;
        JSONObject result;
        if (run.feature.usesChatModel()) {
            manager.unloadModel();
            result = manager.loadModel(featureBody(run).toString());
        } else if (run.feature == TaiFunction.EMBEDDINGS) {
            result = manager.embeddings(featureBody(run).put("input", new JSONArray().put("warm up")).toString());
        } else {
            result = manager.ttsWarm(featureBody(run).toString());
        }
        m.loadMs = (System.nanoTime() - started) / 1_000_000L;
        String code = errorCode(result);
        if (code != null) {
            failed(m, code, result);
            emit(event("skipped").put("code", code).put("reason", m.reason == null ? code : m.reason));
            return false;
        }
        m.fits = true;
        if (run.feature.usesChatModel()) {
            loadedWindow = TaiBenchHarness.loadedContextWindow(result);
            m.ranOn = TaiFeatureCheck.acceleratorOf(result.optString("backend", ""), m.accelerator);
            if (m.speculative) {
                m.speculativeRan = result.isNull("speculativeRan") || !result.has("speculativeRan")
                    ? null : Boolean.valueOf(result.optBoolean("speculativeRan"));
            }
        } else {
            m.ranOn = TaiTierPolicy.ACCEL_CPU;
        }
        emit(event("phase_done").put("phase", TaiBenchSuite.PHASE_LOAD).put("status", "ok")
            .put("metrics", new JSONObject().put("ms", m.loadMs)));
        return true;
    }

    /** The request every call of a run starts from: the model, the feature, and for a chat model the setup. */
    @NonNull
    private static JSONObject featureBody(@NonNull Planned run) throws JSONException {
        JSONObject body = new JSONObject().put("model", run.spec.id).put(TaiCallerRequests.FUNCTION, run.feature.id());
        if (run.feature.usesChatModel()) {
            body.put("accelerator", run.variant.accelerator).put("speculative_decoding", run.variant.speculative);
        }
        return body;
    }

    /** Marks {@code m} for a failed call: a crash, a refusal for memory (it does not fit), or another failure. */
    private static void failed(@NonNull TaiFeatureCheck.Measurement m, @NonNull String code, @NonNull JSONObject result) {
        m.reason = messageOf(result, code);
        if (CRASH_CODE.equals(code)) {
            m.status = TaiFeatureCheck.STATUS_CRASHED;
        } else if ("insufficient_memory".equals(code)) {
            m.fits = false;
            m.status = "skipped:" + code;
        } else {
            m.status = (m.loadMs >= 0L && m.fits ? "stopped:" : "skipped:") + code;
        }
    }

    // ------------------------------------------------------------------------------- workloads

    /** Runs the feature's workload; returns whether its answers passed the feature's sanity check. */
    private boolean workload(@NonNull Planned run, @NonNull TaiFeatureCheck.Measurement m)
            throws JSONException {
        switch (run.feature) {
            case TIDY_DICTATION: return cleanup(run, m);
            case APP_CATEGORIES: return sorting(run, m);
            case EMBEDDINGS: return search(run, m);
            case READ_ALOUD: return readAloud(run, m);
            default: return chat(run, m);
        }
    }

    private boolean cleanup(@NonNull Planned run, @NonNull TaiFeatureCheck.Measurement m) throws JSONException {
        TermuxAppSharedPreferences prefs = TermuxAppSharedPreferences.build(app, true);
        String level = prefs == null ? VoicePolishRules.LEVEL_POLISHED : prefs.getInAppKeyboardVoicePolishLevel();
        String[] dictations = {asset(DICTATION_SHORT_ASSET), asset(DICTATION_LONG_ASSET)};
        Totals totals = new Totals();
        int words = 0;
        boolean correct = true;
        startFeaturePhase(dictations.length, dictations[0]);
        for (int i = 0; i < dictations.length; i++) {
            JSONObject body = LocalTaiVoiceTextPolisher.request(run.spec.id, level, dictations[i]);
            Reply reply = stream(run, body, i + 1, dictations.length);
            if (!totals.add(reply, m)) return false;
            words += VoicePolishRules.wordCount(dictations[i]);
            correct &= TaiFeatureCheck.cleanupOutputOk(dictations[i], reply.text());
        }
        m.speed = TaiFeatureCheck.cleanupSpeed(words, totals.elapsedMs);
        return done(m, totals, correct);
    }

    private boolean sorting(@NonNull Planned run, @NonNull TaiFeatureCheck.Measurement m) throws JSONException {
        List<String[]> apps = installedApps(SORT_APPS);
        if (apps.isEmpty()) {
            m.status = "skipped:no_apps";
            return false;
        }
        Totals totals = new Totals();
        boolean correct = true;
        List<String> slugs = LauncherCategorySortPrompt.categorySlugs();
        startFeaturePhase(apps.size(), LauncherCategorySortPrompt.singleAppPrompt(apps.get(0)[1], apps.get(0)[0]));
        for (int i = 0; i < apps.size(); i++) {
            JSONObject body = TaiCallerRequests.categoryBody(run.spec.id,
                LauncherCategorySortPrompt.singleAppPrompt(apps.get(i)[1], apps.get(i)[0]), LauncherCategorySortPrompt.MAX_TOKENS);
            Reply reply = stream(run, body, i + 1, apps.size());
            if (!totals.add(reply, m)) return false;
            correct &= TaiFeatureCheck.sortAnswerOk(reply.text(), slugs);
        }
        m.speed = TaiFeatureCheck.perSecond(apps.size(), totals.elapsedMs);
        return done(m, totals, correct);
    }

    /** The assistant and Dawn chat: the bench's chat question, then its long page fitted to the loaded window. */
    private boolean chat(@NonNull Planned run, @NonNull TaiFeatureCheck.Measurement m) throws JSONException {
        String log;
        try {
            log = assetText(TaiBenchSuite.LONG_INPUT_ASSET);
        } catch (IOException e) {
            m.status = "stopped:asset_missing";
            m.reason = message(e);
            return false;
        }
        // Fitted to the window the model came up with, as the bench does; the budget's floor when it did not say.
        String longPrompt = TaiBenchSuite.longInput(log, loadedWindow > 0 ? loadedWindow : TaiLoadBudget.FLOOR_CONTEXT).prompt;
        String[] prompts = {TaiBenchSuite.CHAT_PROMPT, longPrompt};
        int[] caps = {TaiBenchSuite.CHAT_MAX_TOKENS, TaiBenchSuite.LONG_INPUT_MAX_TOKENS};
        Totals totals = new Totals();
        boolean correct = true;
        startFeaturePhase(prompts.length, TaiBenchSuite.CHAT_PROMPT);
        for (int i = 0; i < prompts.length; i++) {
            JSONObject body = new JSONObject()
                .put("messages", new JSONArray()
                    .put(new JSONObject().put("role", "system").put("content", TaiBenchSuite.SYSTEM_PROMPT))
                    .put(new JSONObject().put("role", "user").put("content", prompts[i])))
                .put("temperature", 0).put("top_k", 1).put("max_tokens", caps[i]).put("thinking", false);
            Reply reply = stream(run, body, i + 1, prompts.length);
            if (!totals.add(reply, m)) return false;
            correct &= !TaiBenchHarness.isDegenerateReply(reply.text());
        }
        // The chat features are measured on decode speed: the figure is the rate a reply is written at.
        m.speed = totals.decodeTps();
        return done(m, totals, correct);
    }

    private boolean search(@NonNull Planned run, @NonNull TaiFeatureCheck.Measurement m) throws JSONException {
        List<String> notes = new ArrayList<>();
        for (String line : asset(NOTES_ASSET).split("\n")) {
            if (!line.trim().isEmpty()) notes.add(line.trim());
        }
        startFeaturePhase(1, notes.get(0));
        emit(event("run_start").put("phase", PHASE_FEATURE).put("run", 1).put("runs", 1));
        long started = System.nanoTime();
        JSONObject result = manager.embeddings(featureBody(run).put("input", new JSONArray(notes)).toString());
        long elapsedMs = (System.nanoTime() - started) / 1_000_000L;
        String code = errorCode(result);
        if (code != null) {
            failed(m, code, result);
            return false;
        }
        JSONArray data = result.optJSONArray("data");
        boolean correct = data != null && data.length() == notes.size();
        for (int i = 0; correct && i < data.length(); i++) {
            JSONObject item = data.optJSONObject(i);
            JSONArray vector = item == null ? null : item.optJSONArray("embedding");
            // A base64 answer is a string; this request asks for floats, so anything else is wrong.
            correct = vector != null && vector.length() > 0 && !Double.isNaN(vector.optDouble(0, Double.NaN));
        }
        m.speed = TaiFeatureCheck.perSecond(notes.size(), elapsedMs);
        Totals totals = new Totals();
        totals.elapsedMs = elapsedMs;
        return done(m, totals, correct);
    }

    private boolean readAloud(@NonNull Planned run, @NonNull TaiFeatureCheck.Measurement m) throws JSONException {
        startFeaturePhase(1, READ_SENTENCE);
        emit(event("run_start").put("phase", PHASE_FEATURE).put("run", 1).put("runs", 1));
        long started = System.nanoTime();
        AtomicLong firstSound = new AtomicLong(-1L);
        JSONObject result;
        try {
            result = manager.synthesizeSpeech(featureBody(run).put("input", READ_SENTENCE).toString(),
                READ_SENTENCE.length(), (pcm, sampleRate) -> {
                    if (pcm.length > 0) firstSound.compareAndSet(-1L, (System.nanoTime() - started) / 1_000_000L);
                });
        } catch (IOException e) {
            m.status = "stopped:" + message(e);
            return false;
        }
        String code = errorCode(result);
        if (code != null) {
            failed(m, code, result);
            return false;
        }
        long firstSoundMs = firstSound.get();
        m.speed = TaiFeatureCheck.firstSoundSpeed(firstSoundMs);
        Totals totals = new Totals();
        totals.elapsedMs = Math.max(0L, firstSoundMs);
        return done(m, totals, firstSoundMs > 0L);
    }

    /** Closes a workload that ran to the end: the decode speed, the sanity result and the phase's figures. */
    private boolean done(@NonNull TaiFeatureCheck.Measurement m, @NonNull Totals totals, boolean correct) throws JSONException {
        m.decodeTps = totals.decodeTps();
        m.passed = correct;
        m.status = TaiFeatureCheck.STATUS_COMPLETE;
        emit(event("phase_done").put("phase", PHASE_FEATURE).put("status", "ok").put("metrics", new JSONObject()
            .put("speed", m.speed).put("figure", TaiFeatureCheck.figure(m.feature, m.speed))
            .put("decodeTps", m.decodeTps).put("passed", correct)));
        return correct;
    }

    private void startFeaturePhase(int runs, @NonNull String prompt) throws JSONException {
        emit(event("phase_start").put("phase", PHASE_FEATURE).put("runs", runs).put("prompt", prompt));
    }

    // ------------------------------------------------------------------------------ streaming

    /** The replies of a workload, added up: their wall time and their decode tokens and time. */
    private static final class Totals {
        long elapsedMs;
        long decodeTokens;
        long decodeMs;

        /** Adds one reply; on a failed one marks {@code m} and returns {@code false}. */
        boolean add(@NonNull Reply reply, @NonNull TaiFeatureCheck.Measurement m) {
            if (reply.errorCode != null) {
                failed(m, reply.errorCode, reply.error == null ? new JSONObject() : reply.error);
                return false;
            }
            elapsedMs += reply.elapsedMs();
            int tokens = reply.tokens();
            long interval = reply.lastMs - reply.firstMs;
            if (tokens > 1 && interval > 0L) {
                decodeTokens += tokens - 1;
                decodeMs += interval;
            }
            return true;
        }

        /** Tokens after each reply's first, over the time from its first token to its last ({@link TaiBenchStats#decodeTps}). */
        double decodeTps() {
            return decodeMs > 0L ? decodeTokens * 1000.0 / decodeMs : 0.0;
        }
    }

    /** One streamed reply with its first and last token stamped. */
    private static final class Reply {
        final long submitMs = System.currentTimeMillis();
        long firstMs;
        long lastMs;
        int chunks;
        int usageTokens = -1;
        @Nullable String errorCode;
        @Nullable JSONObject error;
        final StringBuilder text = new StringBuilder();

        long elapsedMs() {
            return Math.max(0L, (lastMs > 0L ? lastMs : System.currentTimeMillis()) - submitMs);
        }

        int tokens() {
            return usageTokens > 0 ? usageTokens : chunks;
        }

        @NonNull
        String text() {
            return text.toString();
        }
    }

    /**
     * Sends one chat request streamed, with the run's setup and the usage figures asked for, and stamps
     * every chunk; the reply's text is the answer without its thinking. Token events go to the live view.
     */
    @NonNull
    private Reply stream(@NonNull Planned run, @NonNull JSONObject body, int index, int runs) throws JSONException {
        body.put("model", run.spec.id).put(TaiCallerRequests.FUNCTION, run.feature.id())
            .put("accelerator", run.variant.accelerator).put("speculative_decoding", run.variant.speculative)
            .put("stream", true).put("stream_options", new JSONObject().put("include_usage", true));
        emit(event("run_start").put("phase", PHASE_FEATURE).put("run", index).put("runs", runs));
        Reply reply = new Reply();
        AtomicLong lastEvent = new AtomicLong();
        try {
            manager.openAiChatCompletionsStream(body.toString(), new TaiManager.OpenAiStreamSink() {
                @Override
                public void onEvent(@NonNull JSONObject event) {
                    String code = event.has("choices") || event.has("usage") ? null : errorCode(event);
                    if (code != null) {
                        reply.errorCode = code;
                        reply.error = event;
                        return;
                    }
                    JSONObject usage = event.optJSONObject("usage");
                    if (usage != null) reply.usageTokens = usage.optInt("completion_tokens", -1);
                    JSONArray choices = event.optJSONArray("choices");
                    JSONObject choice = choices == null ? null : choices.optJSONObject(0);
                    JSONObject delta = choice == null ? null : choice.optJSONObject("delta");
                    if (delta == null) return;
                    String content = delta.optString("content", "");
                    String thinking = delta.optString("reasoning_content", "");
                    if (content.isEmpty() && thinking.isEmpty()) return;
                    long now = System.currentTimeMillis();
                    if (reply.firstMs == 0L) reply.firstMs = now;
                    reply.lastMs = now;
                    reply.chunks++;
                    reply.text.append(content);
                    if (now - lastEvent.get() >= TaiBenchHarness.TOKEN_EVENT_INTERVAL_MS) {
                        lastEvent.set(now);
                        long decodeMs = reply.lastMs - reply.firstMs;
                        emit(event("token").put("phase", PHASE_FEATURE).put("run", index).put("runs", runs)
                            .put("text", content).put("tokens", reply.chunks)
                            .put("tps", reply.chunks > 1 && decodeMs > 0L ? (reply.chunks - 1) * 1000.0 / decodeMs : 0.0)
                            .put("ttftMs", reply.firstMs - reply.submitMs));
                    }
                }

                @Override
                public void onDone() {
                }
            });
        } catch (IOException | RuntimeException e) {
            reply.errorCode = "stream_failed";
            reply.error = new JSONObject().put("message", message(e));
        }
        if (reply.errorCode == null && stopReason != null) reply.errorCode = "cancelled";
        return reply;
    }

    // --------------------------------------------------------------------------------- verdict

    /** The feature's GPU outcome, written as the phone-wide verdict when it says something (decision 8). */
    private void settleGpuVerdict(@NonNull List<TaiFeatureCheck.Outcome> outcomes) {
        TaiGpuVerdict.State outcome = TaiFeatureCheck.gpuOutcome(outcomes);
        try {
            if (outcome == TaiGpuVerdict.State.VERIFIED) TaiGpuVerdict.markVerified(app);
            else if (outcome == TaiGpuVerdict.State.FAILED) TaiGpuVerdict.markFailed(app);
        } catch (RuntimeException ignored) {
        }
    }

    // ---------------------------------------------------------------------------------- helpers

    /**
     * Up to {@code count} installed apps with a launcher icon, other than this one, as {@code {package,
     * label}}, in package order so a phone checks the same apps each time.
     */
    @NonNull
    private List<String[]> installedApps(int count) {
        List<String[]> out = new ArrayList<>();
        try {
            PackageManager pm = app.getPackageManager();
            Intent launcher = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
            List<ResolveInfo> found = new ArrayList<>(pm.queryIntentActivities(launcher, 0));
            Collections.sort(found, (a, b) -> a.activityInfo.packageName.compareTo(b.activityInfo.packageName));
            String self = app.getPackageName();
            String last = null;
            for (ResolveInfo info : found) {
                String pkg = info.activityInfo.packageName;
                if (pkg.equals(self) || pkg.equals(last)) continue;
                last = pkg;
                CharSequence label = info.loadLabel(pm);
                out.add(new String[] {pkg, label == null ? pkg : label.toString()});
                if (out.size() >= count) break;
            }
        } catch (RuntimeException ignored) {
        }
        return out;
    }

    @NonNull
    private String asset(@NonNull String path) {
        try {
            return assetText(path).trim();
        } catch (IOException e) {
            throw new IllegalStateException("Missing check asset " + path, e);
        }
    }

    @NonNull
    private String assetText(@NonNull String path) throws IOException {
        try (InputStream input = app.getAssets().open(path); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) != -1) output.write(buffer, 0, read);
            return new String(output.toByteArray(), StandardCharsets.UTF_8);
        }
    }

    /**
     * The error code of a TAI answer in any of its shapes (the runtime's {@code {error: code}}, the OpenAI
     * {@code {error: {code}}} with the original under {@code tai}); {@code null} for a success.
     */
    @Nullable
    static String errorCode(@NonNull JSONObject result) {
        JSONObject tai = result.optJSONObject("tai");
        if (tai != null && !tai.optString("error", "").isEmpty() && !(tai.opt("error") instanceof JSONObject)) {
            return tai.optString("error");
        }
        Object error = result.opt("error");
        if (error instanceof JSONObject) return ((JSONObject) error).optString("code", "tai_error");
        if (error instanceof String && !((String) error).isEmpty()) return (String) error;
        if (result.has("ok") && !result.optBoolean("ok", true)) return result.optString("code", "tai_error");
        int status = result.optInt("_statusCode", 200);
        return status >= 400 ? "http_" + status : null;
    }

    @NonNull
    private static String messageOf(@NonNull JSONObject result, @NonNull String fallback) {
        JSONObject error = result.optJSONObject("error");
        String message = error != null ? error.optString("message", "") : result.optString("message", "");
        return message.isEmpty() ? fallback : message;
    }

    @NonNull
    private static String message(@NonNull Throwable throwable) {
        String message = throwable.getMessage();
        return message == null || message.trim().isEmpty() ? throwable.getClass().getSimpleName() : message;
    }

    /** A harness-shaped event; its {@code put} never throws. */
    @NonNull
    private static Ev event(@NonNull String name) {
        return new Ev(name);
    }

    private static final class Ev {
        @NonNull final JSONObject json = new JSONObject();

        Ev(@NonNull String name) {
            put("event", name);
            put("at", System.currentTimeMillis());
        }

        @NonNull
        Ev put(@NonNull String key, @Nullable Object value) {
            try {
                json.put(key, value == null ? JSONObject.NULL : value);
            } catch (JSONException ignored) {
            }
            return this;
        }
    }

    private void emit(@NonNull Ev event) {
        try {
            sink.onEvent(event.json);
        } catch (RuntimeException ignored) {
        }
    }
}
