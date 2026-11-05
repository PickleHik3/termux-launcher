package com.termux.ai;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Runs the voice benchmark ({@link TaiVoiceBench}) in the app process, through the same speech
 * requests voice input sends ({@link TaiManager#sttWarm}, {@link TaiManager#transcribe}), each
 * naming the processor it runs on. Its events have the bench harness's shape, so the Run screen,
 * Stop, "Skip the wait" and the hold on leaving all work unchanged; the bench's guard
 * ({@link TaiBenchConditionsGuard}) keeps the phone safe between steps (battery, heat, the
 * cool-down, the screen left), and the runtime's own memory check refuses a load that does not fit.
 * Every entry is appended to {@code benchmarks.json} as it finishes. Blocks; not for the main thread.
 */
public final class TaiVoiceBenchRunner {
    /** Receives the harness-shaped events. */
    public interface Sink {
        void onEvent(@NonNull JSONObject event);
    }

    /** One speech model to run. */
    public static final class Target {
        @NonNull public final String modelId;
        @NonNull public final String displayName;
        @Nullable public final String path;
        public final long sizeBytes;
        /** Whether the Speech accelerator row offers the GPU for this model on this phone. */
        public final boolean gpuOffered;
        /** A model that only runs right on the GPU (its default path): it is run there and not on the CPU. */
        public final boolean gpuOnly;

        public Target(@NonNull String modelId, @NonNull String displayName, @Nullable String path, long sizeBytes, boolean gpuOffered) {
            this(modelId, displayName, path, sizeBytes, gpuOffered, false);
        }

        public Target(@NonNull String modelId, @NonNull String displayName, @Nullable String path, long sizeBytes,
                      boolean gpuOffered, boolean gpuOnly) {
            this.modelId = modelId;
            this.displayName = displayName;
            this.path = path;
            this.sizeBytes = sizeBytes;
            this.gpuOffered = gpuOffered;
            this.gpuOnly = gpuOnly;
        }
    }

    /** What the runner needs from the app; {@link #forContext} wires it to {@link TaiManager}. */
    interface Host {
        /** Closes any speech graph, then loads {@code modelId} on {@code accelerator}: the warm answer. */
        @NonNull JSONObject loadCold(@NonNull String modelId, @NonNull String accelerator) throws JSONException;

        /** One transcription of {@code clip} on {@code accelerator}: the transcribe answer. */
        @NonNull JSONObject transcribe(@NonNull String modelId, @NonNull String accelerator, @NonNull TaiVoiceBench.Clip clip)
            throws JSONException;

        /** The clip's real length; {@code -1} when it cannot be read. */
        long clipDurationMs(@NonNull TaiVoiceBench.Clip clip);

        /** {@code null} when the phone may start; else the start check's reason ({@link TaiBenchGuardRules#startCheck}). */
        @Nullable String startRefusal();

        void startThermalWatch(@NonNull Runnable onSevere);

        void stopThermalWatch();

        long nowMs();
    }

    @NonNull private final Host host;
    @NonNull private final TaiBenchGuard guard;
    @Nullable private final TaiBenchStore store;
    @NonNull private final Sink sink;
    @NonNull private final String appVersion;
    @NonNull private final String runtimeVersion;
    @NonNull private final JSONObject device;
    @Nullable private volatile String stopReason;

    TaiVoiceBenchRunner(@NonNull Host host, @NonNull TaiBenchGuard guard, @Nullable TaiBenchStore store, @NonNull Sink sink,
                        @NonNull String appVersion, @NonNull String runtimeVersion, @NonNull JSONObject device) {
        this.host = host;
        this.guard = guard;
        this.store = store;
        this.sink = sink;
        this.appVersion = appVersion;
        this.runtimeVersion = runtimeVersion;
        this.device = device;
    }

    /** The runner against this app's {@link TaiManager}, its results file and the phone's conditions. */
    @NonNull
    public static TaiVoiceBenchRunner forContext(@NonNull Context context, @NonNull Sink sink) {
        Context app = context.getApplicationContext();
        TaiDeviceConditions conditions = new TaiDeviceConditions(app);
        TaiBenchConditionsGuard guard = new TaiBenchConditionsGuard(conditions::snapshot, System::currentTimeMillis);
        JSONObject device = new JSONObject();
        try {
            TaiDeviceCapabilities capabilities = TaiDeviceCapabilities.detect(app);
            device.put("soc", capabilities.socModel == null ? JSONObject.NULL : capabilities.socModel);
            device.put("ramClassGb", capabilities.memoryBytes > 0L
                ? Math.round(capabilities.memoryBytes / (double) (1024L * 1024L * 1024L)) : JSONObject.NULL);
        } catch (JSONException | RuntimeException ignored) {
        }
        return new TaiVoiceBenchRunner(new ManagerHost(app, conditions), guard, TaiBenchStore.in(app.getFilesDir()), sink,
            com.termux.BuildConfig.VERSION_NAME, com.termux.BuildConfig.LITERT_VERSION, device);
    }

    /** Ends the run after the transcription in progress (one cannot be cut short); that entry is recorded as stopped. */
    public void requestStop(@NonNull String reason) {
        if (stopReason == null) stopReason = reason;
    }

    public void setHeld(boolean held) {
        guard.setHeld(held);
    }

    public void skipCooldown() {
        guard.skipCooldown();
    }

    // ---- the run ----------------------------------------------------------------------------

    /** One model on one processor. */
    private static final class Planned {
        @NonNull final Target target;
        @NonNull final String accelerator;
        @NonNull final TaiBenchSuite.EntryPlan entry;

        Planned(@NonNull Target target, @NonNull String accelerator) {
            this.target = target;
            this.accelerator = accelerator;
            this.entry = new TaiBenchSuite.EntryPlan(target.modelId, TaiVoiceBench.BACKEND, accelerator, false);
        }
    }

    /** Every target on the CPU, then the GPU where offered, in order. */
    @NonNull
    static List<TaiBenchSuite.EntryPlan> plan(@NonNull List<Target> targets) {
        List<TaiBenchSuite.EntryPlan> out = new ArrayList<>();
        for (Planned planned : expand(targets)) out.add(planned.entry);
        return out;
    }

    @NonNull
    private static List<Planned> expand(@NonNull List<Target> targets) {
        List<Planned> out = new ArrayList<>();
        for (Target target : targets) {
            List<String> accelerators = target.gpuOnly ? TaiVoiceBench.gpuOnlyAccelerators() : TaiVoiceBench.accelerators(target.gpuOffered);
            for (String accelerator : accelerators) out.add(new Planned(target, accelerator));
        }
        return out;
    }

    /** Runs every entry and emits the events, then {@code done}. {@code force} skips the start check, as the bench's does. */
    public void run(@NonNull List<Target> targets, boolean force) {
        if (store != null) {
            try {
                store.recoverStaleMarker("The app closed during this run.");
            } catch (IOException ignored) {
            }
        }
        if (!force) {
            String reason = host.startRefusal();
            if (reason != null) {
                emit(event("error").put("code", "conditions_not_met").put("reason", reason).put("message", reason));
                emit(event("done").put("stopped", "conditions_not_met"));
                return;
            }
        }
        List<Planned> runs = expand(targets);
        host.startThermalWatch(() -> requestStop("thermal"));
        int index = 0;
        try {
            for (Planned planned : runs) {
                if (stopReason != null) break;
                runOne(planned, index++, runs.size());
            }
        } finally {
            host.stopThermalWatch();
        }
        emit(event("done").put("ok", true).put("benchVersion", TaiVoiceBench.BENCH_VERSION).put("preset", TaiVoiceBench.PRESET_ID)
            .put("entries", runs.size()).put("stopped", stopReason == null ? JSONObject.NULL : stopReason));
    }

    /** What one entry measured, and the record it becomes. */
    private final class Measured {
        @NonNull final Planned planned;
        final long startedMs = host.nowMs();
        long loadMs = -1L;
        long loadWallMs = -1L;
        @NonNull String ranOn = "";
        @NonNull String fallback = "";
        @NonNull final List<TaiVoiceBench.ClipResult> clips = new ArrayList<>();
        @NonNull String status = "stopped:incomplete";
        @Nullable String reason;
        @Nullable JSONObject conditions;

        Measured(@NonNull Planned planned) {
            this.planned = planned;
        }

        @NonNull
        JSONObject record() throws JSONException {
            JSONObject phases = new JSONObject();
            phases.put("load", loadMs < 0L ? JSONObject.NULL : new JSONObject().put("ms", loadMs).put("wallMs", loadWallMs));
            org.json.JSONArray clipsJson = new org.json.JSONArray();
            for (TaiVoiceBench.ClipResult clip : clips) clipsJson.put(clip.toJson());
            phases.put("clips", clipsJson);
            JSONObject totals = TaiVoiceBench.totals(clips);
            phases.put("voice", totals == null ? JSONObject.NULL : totals);
            JSONObject record = new JSONObject()
                .put("id", UUID.randomUUID().toString())
                .put("kind", TaiVoiceBench.KIND)
                .put("benchVersion", TaiVoiceBench.BENCH_VERSION)
                .put("preset", TaiVoiceBench.PRESET_ID)
                .put("timestamp", startedMs)
                .put("durationMs", Math.max(0L, host.nowMs() - startedMs))
                .put("modelId", planned.target.modelId)
                .put("displayName", planned.target.displayName)
                .put("sizeBytes", planned.target.sizeBytes)
                .put("sha256", JSONObject.NULL)
                .put("engine", TaiVoiceBench.engineOf(planned.target.modelId, planned.target.path))
                .put("backend", TaiVoiceBench.BACKEND)
                .put("accelerator", planned.accelerator)
                .put("ranOn", ranOn.isEmpty() ? JSONObject.NULL : ranOn)
                .put("speculative", false)
                .put("runtimeVersion", runtimeVersion)
                .put("appVersion", appVersion)
                .put("device", device)
                .put("conditions", conditions == null ? new JSONObject() : conditions)
                .put("phases", phases)
                .put("status", status)
                .put("verdict", JSONObject.NULL);
            if (!fallback.isEmpty()) record.put("gpuFallback", fallback);
            if (reason != null) record.put("skipReason", reason);
            return record;
        }
    }

    private void runOne(@NonNull Planned planned, int index, int total) {
        Measured m = new Measured(planned);
        try {
            emit(event("entry_start").put("index", index).put("total", total).put("entry", planned.entry.toJson()
                .put("displayName", planned.target.displayName).put("kind", TaiVoiceBench.KIND)));
            if (!consultGuard(TaiVoiceBench.PHASE_LOAD, planned, m)) return;
            guard.entryStarted(planned.entry);
            markInProgress(planned, m);
            try {
                if (load(planned, m) && warmUp(planned, m)) clips(planned, m);
            } finally {
                guard.entryFinished(planned.entry);
            }
        } catch (JSONException | RuntimeException e) {
            m.status = "stopped:bench_error";
            m.reason = message(e);
        } finally {
            finish(planned, m);
        }
    }

    private void markInProgress(@NonNull Planned planned, @NonNull Measured m) throws JSONException {
        if (store == null) return;
        try {
            store.markInProgress(new JSONObject().put("kind", TaiVoiceBench.KIND).put("modelId", planned.target.modelId)
                .put("displayName", planned.target.displayName).put("backend", TaiVoiceBench.BACKEND)
                .put("accelerator", planned.accelerator).put("speculative", false).put("preset", TaiVoiceBench.PRESET_ID)
                .put("benchVersion", TaiVoiceBench.BENCH_VERSION).put("timestamp", m.startedMs).put("appVersion", appVersion)
                .put("device", device));
        } catch (IOException ignored) {
        }
    }

    private void finish(@NonNull Planned planned, @NonNull Measured m) {
        try {
            m.conditions = guard.entryConditions(planned.entry);
        } catch (JSONException | RuntimeException ignored) {
        }
        JSONObject record;
        try {
            record = m.record();
        } catch (JSONException e) {
            return;
        }
        boolean stored = false;
        if (store != null) {
            try {
                store.append(record);
                stored = true;
            } catch (IOException ignored) {
            }
            store.clearInProgress();
        }
        emit(event("entry_done").put("record", record).put("stored", stored));
    }

    /** {@code false} when the guard or a stop ended the entry; {@code m} says why. */
    private boolean consultGuard(@NonNull String phase, @NonNull Planned planned, @NonNull Measured m) {
        while (true) {
            String outside = stopReason;
            if (outside != null) {
                m.status = "stopped:" + outside;
                return false;
            }
            TaiBenchGuard.Decision decision = guard.beforePhase(phase, planned.entry);
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

    /** A cold load on the entry's processor; where it really runs comes back with it. */
    private boolean load(@NonNull Planned planned, @NonNull Measured m) throws JSONException {
        emit(event("phase_start").put("phase", TaiVoiceBench.PHASE_LOAD).put("runs", 1));
        long started = host.nowMs();
        JSONObject answer = host.loadCold(planned.target.modelId, planned.accelerator);
        m.loadWallMs = Math.max(0L, host.nowMs() - started);
        String code = TaiFeatureCheckRunner.errorCode(answer);
        if (code != null) {
            failed(m, code, answer, false);
            emit(event("skipped").put("code", code).put("reason", m.reason == null ? code : m.reason));
            return false;
        }
        m.loadMs = answer.has("loadMs") ? Math.max(0L, answer.optLong("loadMs", m.loadWallMs)) : m.loadWallMs;
        m.ranOn = answer.optString("accelerator", "");
        if (m.ranOn.isEmpty()) m.ranOn = planned.accelerator;
        if (TaiBenchSuite.ACCELERATOR_GPU.equals(planned.accelerator) && !TaiBenchSuite.ACCELERATOR_GPU.equals(m.ranOn)) {
            String why = answer.optString("acceleratorFallback", "");
            m.fallback = why.isEmpty() ? "gpu unavailable" : why;
        }
        emit(event("phase_done").put("phase", TaiVoiceBench.PHASE_LOAD).put("status", "ok").put("metrics", new JSONObject()
            .put("ms", m.loadMs).put("ranOn", m.ranOn).put("gpuFallback", m.fallback.isEmpty() ? JSONObject.NULL : m.fallback)));
        return true;
    }

    /** The shortest clip once, thrown away: it fills the caches the first timed run would otherwise pay for. */
    private boolean warmUp(@NonNull Planned planned, @NonNull Measured m) throws JSONException {
        if (!consultGuard(TaiVoiceBench.PHASE_WARMUP, planned, m)) return false;
        TaiVoiceBench.Clip clip = TaiVoiceBench.CLIPS.get(0);
        emit(event("phase_start").put("phase", TaiVoiceBench.PHASE_WARMUP).put("runs", 1).put("prompt", clip.reference));
        emit(event("run_start").put("phase", TaiVoiceBench.PHASE_WARMUP).put("run", 1).put("runs", 1).put("prompt", clip.reference));
        JSONObject answer = host.transcribe(planned.target.modelId, planned.accelerator, clip);
        String code = TaiFeatureCheckRunner.errorCode(answer);
        if (code != null) {
            failed(m, code, answer, true);
            emit(event("error").put("entry", planned.entry.toJson()).put("phase", TaiVoiceBench.PHASE_WARMUP)
                .put("code", code).put("message", m.reason == null ? code : m.reason));
            return false;
        }
        emit(event("token").put("phase", TaiVoiceBench.PHASE_WARMUP).put("run", 1).put("runs", 1)
            .put("text", answer.optString("text", "").trim()).put("tokens", TaiVoiceBench.words(answer.optString("text", "")).size()));
        emit(event("phase_done").put("phase", TaiVoiceBench.PHASE_WARMUP).put("status", "ok"));
        return true;
    }

    /** Every clip {@link TaiVoiceBench#TIMED_RUNS} times; a stop takes effect between transcriptions. */
    private void clips(@NonNull Planned planned, @NonNull Measured m) throws JSONException {
        if (!consultGuard(TaiVoiceBench.PHASE_VOICE, planned, m)) return;
        int runs = TaiVoiceBench.CLIPS.size() * TaiVoiceBench.TIMED_RUNS;
        emit(event("phase_start").put("phase", TaiVoiceBench.PHASE_VOICE).put("runs", runs)
            .put("prompt", TaiVoiceBench.CLIPS.get(0).reference));
        int run = 0;
        for (TaiVoiceBench.Clip clip : TaiVoiceBench.CLIPS) {
            TaiVoiceBench.ClipResult result = new TaiVoiceBench.ClipResult(clip, host.clipDurationMs(clip));
            m.clips.add(result);
            for (int i = 0; i < TaiVoiceBench.TIMED_RUNS; i++) {
                run++;
                if (i > 0 || run > 1) {
                    if (!consultGuard(TaiVoiceBench.PHASE_VOICE, planned, m)) return;
                }
                emit(event("run_start").put("phase", TaiVoiceBench.PHASE_VOICE).put("run", run).put("runs", runs)
                    .put("clip", clip.id).put("prompt", clip.reference));
                long started = host.nowMs();
                JSONObject answer = host.transcribe(planned.target.modelId, planned.accelerator, clip);
                long wallMs = Math.max(0L, host.nowMs() - started);
                String code = TaiFeatureCheckRunner.errorCode(answer);
                if (code != null) {
                    failed(m, code, answer, true);
                    emit(event("error").put("entry", planned.entry.toJson()).put("phase", TaiVoiceBench.PHASE_VOICE)
                        .put("code", code).put("message", m.reason == null ? code : m.reason));
                    return;
                }
                // A graph that had to load again (another request moved it) does not count its load.
                JSONObject timings = answer.optJSONObject("timings");
                long reload = timings == null ? 0L : Math.max(0L, timings.optLong("loadMs", 0L));
                result.wallMs.add(Math.max(1L, wallMs - reload));
                String text = answer.optString("text", "").trim();
                if (result.text == null) result.text = text;
                JSONObject soFar = TaiVoiceBench.totals(m.clips);
                emit(event("token").put("phase", TaiVoiceBench.PHASE_VOICE).put("run", run).put("runs", runs)
                    .put("text", text).put("tokens", TaiVoiceBench.words(text).size())
                    .put("realtime", soFar == null ? 0.0 : soFar.optDouble("realtime", 0.0)));
            }
        }
        JSONObject totals = TaiVoiceBench.totals(m.clips);
        m.status = TaiBenchStore.STATUS_COMPLETE;
        emit(event("phase_done").put("phase", TaiVoiceBench.PHASE_VOICE).put("status", "ok")
            .put("metrics", totals == null ? new JSONObject() : totals));
    }

    /** Marks {@code m} for a failed call: a crash, a refusal (skipped before it loaded), or a stop. */
    private void failed(@NonNull Measured m, @NonNull String code, @NonNull JSONObject answer, boolean loaded) {
        m.reason = messageOf(answer, code);
        if (TaiBenchCrashRecovery.CRASH_CODE.equals(code)) {
            m.status = TaiBenchStore.STATUS_CRASHED;
        } else if (stopReason != null) {
            m.status = "stopped:" + stopReason;
        } else {
            m.status = (loaded ? "stopped:" : "skipped:") + code;
        }
    }

    // ---- plumbing ---------------------------------------------------------------------------

    /** A harness-shaped event whose {@code put} never throws. */
    private final class Ev {
        @NonNull final JSONObject json = new JSONObject();

        Ev(@NonNull String name) {
            put("event", name);
            put("at", host.nowMs());
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

    @NonNull
    private Ev event(@NonNull String name) {
        return new Ev(name);
    }

    private void emit(@NonNull Ev event) {
        try {
            sink.onEvent(event.json);
        } catch (RuntimeException ignored) {
        }
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

    /** The real host: speech requests through {@link TaiManager}, clips copied out of the APK for each one. */
    private static final class ManagerHost implements Host {
        @NonNull private final Context app;
        @NonNull private final TaiManager manager;
        @NonNull private final TaiDeviceConditions conditions;

        ManagerHost(@NonNull Context app, @NonNull TaiDeviceConditions conditions) {
            this.app = app;
            this.manager = TaiManager.getInstance(app);
            this.conditions = conditions;
        }

        @NonNull
        @Override
        public JSONObject loadCold(@NonNull String modelId, @NonNull String accelerator) throws JSONException {
            return manager.sttWarm(new JSONObject().put("model", modelId).put("accelerator", accelerator)
                .put(TaiManager.STT_COLD_LOAD, true).toString());
        }

        /**
         * The clip goes to the runtime the way voice input's audio does, as a file under
         * {@code cacheDir/tai-ipc} that the runtime deletes once transcribed; a copy is made for each
         * request, before its clock starts.
         */
        @NonNull
        @Override
        public JSONObject transcribe(@NonNull String modelId, @NonNull String accelerator, @NonNull TaiVoiceBench.Clip clip)
                throws JSONException {
            File dir = new File(app.getCacheDir(), TaiManager.STT_IPC_DIR);
            File file = new File(dir, "voice-bench-" + UUID.randomUUID() + ".wav");
            try {
                if (!dir.isDirectory() && !dir.mkdirs() && !dir.isDirectory()) throw new IOException("Could not create " + dir);
                try (InputStream input = app.getAssets().open(clip.asset); FileOutputStream output = new FileOutputStream(file)) {
                    byte[] buffer = new byte[64 * 1024];
                    int read;
                    while ((read = input.read(buffer)) != -1) output.write(buffer, 0, read);
                }
            } catch (IOException e) {
                //noinspection ResultOfMethodCallIgnored
                file.delete();
                return new JSONObject().put("ok", false).put("error", "clip_missing").put("message", message(e));
            }
            try {
                // English clips: the language is named so a multilingual graph does not spend time detecting it.
                return manager.transcribe(new JSONObject().put("model", modelId).put("accelerator", accelerator)
                    .put("file", file.getAbsolutePath()).put("language", "en").toString());
            } finally {
                // The runtime deletes it once read; a request that never reached it leaves it here.
                //noinspection ResultOfMethodCallIgnored
                file.delete();
            }
        }

        @Override
        public long clipDurationMs(@NonNull TaiVoiceBench.Clip clip) {
            // WAV is stored uncompressed in the APK, so the asset has a descriptor and a length.
            try (android.content.res.AssetFileDescriptor descriptor = app.getAssets().openFd(clip.asset);
                 InputStream input = descriptor.createInputStream()) {
                byte[] head = new byte[512];
                int read = 0;
                while (read < head.length) {
                    int n = input.read(head, read, head.length - read);
                    if (n < 0) break;
                    read += n;
                }
                long ms = TaiVoiceBench.wavDurationMs(java.util.Arrays.copyOf(head, read), descriptor.getLength());
                return ms > 0L ? ms : clip.nominalSeconds * 1000L;
            } catch (IOException e) {
                return clip.nominalSeconds * 1000L;
            }
        }

        @Nullable
        @Override
        public String startRefusal() {
            return TaiBenchGuardRules.startCheck(conditions.snapshot());
        }

        @Override
        public void startThermalWatch(@NonNull Runnable onSevere) {
            conditions.startThermalListener(onSevere);
        }

        @Override
        public void stopThermalWatch() {
            conditions.stopThermalListener();
        }

        @Override
        public long nowMs() {
            return System.currentTimeMillis();
        }
    }
}
