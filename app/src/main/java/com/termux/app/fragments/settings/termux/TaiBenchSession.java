package com.termux.app.fragments.settings.termux;

import android.app.ActivityManager;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.ai.TaiBenchGuardRules;
import com.termux.ai.TaiDeviceConditions;
import com.termux.ai.TaiDownloadHub;
import com.termux.ai.TaiManager;
import com.termux.ai.TaiModelStore;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * The benchmark run, app-scoped: a run outlives the screen that started it. One worker thread
 * downloads the models that need downloading (spec decision 2), then calls
 * {@link TaiManager#benchRun}, which blocks while the runtime streams; every event is posted to
 * the main thread, folded into the one {@link TaiBenchRunState}, and the attached listeners
 * (the Run screen, while it is open) are told. Re-opening the benchmark while a run is going
 * shows the Run screen over this same state.
 *
 * <p>Stop is {@link TaiManager#cancelRuntime} on a separate thread (the worker is blocked in
 * the stream); the harness keeps the phases that finished. "Skip the wait" is
 * {@link TaiManager#skipBenchCooldown}. While a run is active the phone's battery, heat and free
 * RAM are sampled every {@link #CONDITIONS_INTERVAL_MS} for the Run screen's tiles.
 *
 * <p>Models that were downloaded for the run are deleted afterwards when the plan says so; their
 * results stay in the store, marked not installed by {@link TaiManager#benchmarks}.
 */
public final class TaiBenchSession {
    /** Told on the main thread after every state change. */
    public interface Listener {
        void onBenchStateChanged(@NonNull TaiBenchRunState state);
    }

    /** One model of a run: installed, or a catalogue entry to download first. */
    public static final class Model {
        @NonNull public final String modelId;
        @NonNull public final String displayName;
        public final boolean installed;
        public final long sizeBytes;

        public Model(@NonNull String modelId, @NonNull String displayName, boolean installed, long sizeBytes) {
            this.modelId = modelId;
            this.displayName = displayName;
            this.installed = installed;
            this.sizeBytes = sizeBytes;
        }
    }

    /** What the Check sheet's Start hands over. */
    public static final class Plan {
        @NonNull public final String presetId;
        @NonNull public final List<Model> models;
        public final boolean removeAfterwards;

        public Plan(@NonNull String presetId, @NonNull List<Model> models, boolean removeAfterwards) {
            this.presetId = presetId;
            this.models = Collections.unmodifiableList(new ArrayList<>(models));
            this.removeAfterwards = removeAfterwards;
        }

        public int downloadCount() {
            int count = 0;
            for (Model model : models) if (!model.installed) count++;
            return count;
        }

        public long downloadBytes() {
            long bytes = 0L;
            for (Model model : models) if (!model.installed) bytes += Math.max(0L, model.sizeBytes);
            return bytes;
        }
    }

    static final long CONDITIONS_INTERVAL_MS = 5_000L;
    static final long DOWNLOAD_POLL_MS = 500L;

    private static volatile TaiBenchSession instance;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final TaiBenchRunState state = new TaiBenchRunState();
    private final CopyOnWriteArrayList<Listener> listeners = new CopyOnWriteArrayList<>();
    private final ExecutorService worker = Executors.newSingleThreadExecutor(runnable -> daemon(runnable, "tai-bench-session"));
    private final ExecutorService control = Executors.newSingleThreadExecutor(runnable -> daemon(runnable, "tai-bench-control"));
    private final ScheduledExecutorService sampler = Executors.newSingleThreadScheduledExecutor(runnable -> daemon(runnable, "tai-bench-sampler"));
    private final AtomicBoolean running = new AtomicBoolean();
    private final AtomicBoolean stopRequested = new AtomicBoolean();
    @Nullable private volatile ScheduledFuture<?> sampling;
    /** The ids of the models this run downloaded, to delete afterwards; the last finished run's until the next starts. */
    @NonNull private volatile List<String> downloadedIds = Collections.emptyList();

    private TaiBenchSession() {
    }

    @NonNull
    public static TaiBenchSession get() {
        TaiBenchSession session = instance;
        if (session == null) {
            synchronized (TaiBenchSession.class) {
                session = instance;
                if (session == null) {
                    session = new TaiBenchSession();
                    instance = session;
                }
            }
        }
        return session;
    }

    @NonNull
    private static Thread daemon(@NonNull Runnable runnable, @NonNull String name) {
        Thread thread = new Thread(runnable, name);
        thread.setDaemon(true);
        return thread;
    }

    /** The one state; read it on the main thread only. */
    @NonNull
    @MainThread
    public TaiBenchRunState state() {
        return state;
    }

    /** Whether a run (downloads included) is going. Any thread. */
    public boolean isActive() {
        return running.get();
    }

    @MainThread
    public void addListener(@NonNull Listener listener) {
        listeners.addIfAbsent(listener);
    }

    @MainThread
    public void removeListener(@NonNull Listener listener) {
        listeners.remove(listener);
    }

    /**
     * Starts a run; ignored (returns {@code false}) while one is active. {@code bestTpsBeforeRun}
     * is the leaderboard's top writing speed now, so an entry that beats it can say "New best".
     */
    @MainThread
    public boolean start(@NonNull Context context, @NonNull Plan plan, double bestTpsBeforeRun) {
        if (!running.compareAndSet(false, true)) return false;
        stopRequested.set(false);
        List<TaiBenchRunState.Planned> planned = new ArrayList<>();
        for (Model model : plan.models) {
            planned.add(new TaiBenchRunState.Planned(model.modelId, model.displayName, !model.installed));
        }
        state.begin(plan.presetId, planned, bestTpsBeforeRun, System.currentTimeMillis());
        notifyListeners();
        Context app = context.getApplicationContext();
        startSampling(app);
        worker.execute(() -> {
            try {
                run(app, plan);
            } finally {
                stopSampling();
                running.set(false);
                post(synthetic("session_end").put("reason", stopRequested.get() ? "cancelled" : "ended"));
            }
        });
        return true;
    }

    /** Stops the run after the current generation; downloads in progress are cancelled. */
    public void stop(@NonNull Context context) {
        if (!running.get()) return;
        stopRequested.set(true);
        post(synthetic("stop_requested"));
        Context app = context.getApplicationContext();
        control.execute(() -> {
            try {
                TaiManager.getInstance(app).cancelRuntime();
            } catch (JSONException | RuntimeException ignored) {
            }
        });
    }

    /** "Skip the wait": ends the cool-down in progress; that entry is marked warm start. */
    public void skipWait(@NonNull Context context) {
        if (!running.get()) return;
        Context app = context.getApplicationContext();
        control.execute(() -> {
            try {
                TaiManager.getInstance(app).skipBenchCooldown();
            } catch (JSONException | RuntimeException ignored) {
            }
        });
    }

    // ---- the worker ------------------------------------------------------------------------

    private void run(@NonNull Context app, @NonNull Plan plan) {
        TaiManager manager = TaiManager.getInstance(app);
        List<String> downloaded = new ArrayList<>();
        List<String> toBench = new ArrayList<>();
        for (Model model : plan.models) {
            if (stopRequested.get()) break;
            if (model.installed) {
                toBench.add(model.modelId);
                continue;
            }
            if (download(app, manager, model)) {
                downloaded.add(model.modelId);
                toBench.add(model.modelId);
            }
        }
        downloadedIds = downloaded;
        if (stopRequested.get()) {
            post(synthetic("done").put("stopped", "cancelled"));
        } else if (toBench.isEmpty()) {
            post(synthetic("error").put("code", "nothing_to_run").put("message", "None of the models could be downloaded."));
        } else {
            bench(app, manager, plan, toBench);
        }
        if (plan.removeAfterwards) {
            for (String modelId : downloaded) {
                try {
                    manager.deleteModel(new JSONObject().put("modelId", modelId).put("confirm", true).toString());
                } catch (JSONException | RuntimeException ignored) {
                }
            }
            TaiDownloadHub.get(app).refresh();
        }
    }

    /** Starts one catalogue download and waits for it to land; {@code false} when it did not. */
    private boolean download(@NonNull Context app, @NonNull TaiManager manager, @NonNull Model model) {
        post(synthetic("download_start").put("modelId", model.modelId).put("displayName", model.displayName)
            .put("totalBytes", model.sizeBytes));
        JSONObject started;
        try {
            started = manager.downloadCatalogModel(model.modelId);
        } catch (JSONException | RuntimeException e) {
            started = null;
        }
        if (started == null || !started.optBoolean("ok", false)) {
            post(synthetic("download_done").put("modelId", model.modelId).put("ok", false)
                .put("reason", started == null ? "download failed" : started.optString("message", "download failed")));
            return false;
        }
        TaiDownloadHub hub = TaiDownloadHub.get(app);
        while (true) {
            if (stopRequested.get()) {
                try {
                    manager.cancelDownload(new JSONObject().put("modelId", model.modelId).toString());
                } catch (JSONException | RuntimeException ignored) {
                }
                post(synthetic("download_done").put("modelId", model.modelId).put("ok", false).put("reason", "stopped"));
                return false;
            }
            TaiDownloadHub.Snapshot snapshot = null;
            for (TaiDownloadHub.Snapshot item : hub.snapshot()) {
                if (item.modelId.equals(model.modelId)) snapshot = item;
            }
            if (snapshot == null) {
                // No record any more: installed (the record was folded in) or gone.
                boolean installed = new TaiModelStore(app).getInstalledUserModels().containsKey(model.modelId);
                post(synthetic("download_done").put("modelId", model.modelId).put("ok", installed)
                    .put("reason", installed ? JSONObject.NULL : "download disappeared"));
                return installed;
            }
            if (TaiModelStore.STATE_INSTALLED.equals(snapshot.status)) {
                post(synthetic("download_done").put("modelId", model.modelId).put("ok", true));
                return true;
            }
            if (TaiModelStore.STATE_FAILED.equals(snapshot.status) || TaiModelStore.STATE_CANCELLED.equals(snapshot.status)
                || TaiModelStore.STATE_UNAVAILABLE.equals(snapshot.status)) {
                String reason = snapshot.error == null || snapshot.error.isEmpty() ? snapshot.status : snapshot.error;
                post(synthetic("download_done").put("modelId", model.modelId).put("ok", false).put("reason", reason));
                return false;
            }
            post(synthetic("download_progress").put("modelId", model.modelId)
                .put("bytesRead", snapshot.bytesRead).put("totalBytes", snapshot.totalBytes));
            try {
                Thread.sleep(DOWNLOAD_POLL_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
    }

    private void bench(@NonNull Context app, @NonNull TaiManager manager, @NonNull Plan plan, @NonNull List<String> modelIds) {
        JSONObject body = new JSONObject();
        try {
            JSONArray models = new JSONArray();
            for (String id : modelIds) models.put(new JSONObject().put("model", id));
            body.put("models", models);
            body.put("preset", plan.presetId);
        } catch (JSONException e) {
            post(synthetic("error").put("code", "bad_request").put("message", e.getMessage() == null ? "bad request" : e.getMessage()));
            return;
        }
        try {
            manager.benchRun(body.toString(), new TaiManager.OpenAiStreamSink() {
                @Override
                public void onEvent(@NonNull JSONObject event) {
                    post(event);
                    if ("entry_done".equals(event.optString("event", ""))) postLeaderboard(manager);
                }

                @Override
                public void onDone() {
                }
            });
        } catch (IOException | JSONException | RuntimeException e) {
            String message = e.getMessage() == null || e.getMessage().isEmpty() ? e.getClass().getSimpleName() : e.getMessage();
            post(synthetic("error").put("code", "benchmark_failed").put("message", message));
        }
    }

    /** The leaderboard after an entry landed, reduced to what the state needs: key, rank, speed. */
    private void postLeaderboard(@NonNull TaiManager manager) {
        try {
            JSONObject board = manager.benchmarks().optJSONObject("leaderboard");
            JSONArray ranked = board == null ? null : board.optJSONArray("ranked");
            if (ranked == null) return;
            JSONArray slim = new JSONArray();
            for (int i = 0; i < ranked.length(); i++) {
                JSONObject row = ranked.optJSONObject(i);
                if (row == null) continue;
                slim.put(new JSONObject().put("key", row.optString("key", "")).put("rank", row.optInt("rank", i + 1))
                    .put("writingTps", row.optDouble("writingTps", 0.0)));
            }
            post(synthetic("leaderboard").put("ranked", slim));
        } catch (JSONException | RuntimeException ignored) {
        }
    }

    // ---- conditions --------------------------------------------------------------------------

    private void startSampling(@NonNull Context app) {
        stopSampling();
        TaiDeviceConditions reader = new TaiDeviceConditions(app);
        sampling = sampler.scheduleAtFixedRate(() -> {
            try {
                TaiBenchGuardRules.Snapshot snapshot = reader.snapshot();
                Ev event = synthetic("conditions")
                    .put("batteryPercent", snapshot.batteryPercent)
                    .put("charging", snapshot.charging)
                    .put("thermalStatus", orNull(TaiBenchGuardRules.thermalStatusName(snapshot.thermalStatus)))
                    .put("headroom", Float.isNaN(snapshot.headroom) ? JSONObject.NULL : (double) snapshot.headroom)
                    .put("freeRamBytes", freeRamBytes(app));
                post(event);
            } catch (RuntimeException ignored) {
            }
        }, 0L, CONDITIONS_INTERVAL_MS, TimeUnit.MILLISECONDS);
    }

    private void stopSampling() {
        ScheduledFuture<?> future = sampling;
        sampling = null;
        if (future != null) future.cancel(false);
    }

    static long freeRamBytes(@NonNull Context context) {
        try {
            ActivityManager manager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
            if (manager == null) return -1L;
            ActivityManager.MemoryInfo info = new ActivityManager.MemoryInfo();
            manager.getMemoryInfo(info);
            return info.availMem;
        } catch (RuntimeException e) {
            return -1L;
        }
    }

    // ---- plumbing -------------------------------------------------------------------------------

    /** The session's own events, shaped like the harness's: a builder whose {@code put} never throws. */
    private static final class Ev {
        @NonNull final JSONObject json = new JSONObject();

        Ev(@NonNull String name) {
            put("event", name);
            put("at", System.currentTimeMillis());
        }

        @NonNull
        Ev put(@NonNull String name, @Nullable Object value) {
            try {
                json.put(name, value == null ? JSONObject.NULL : value);
            } catch (JSONException ignored) {
            }
            return this;
        }
    }

    @NonNull
    private static Ev synthetic(@NonNull String name) {
        return new Ev(name);
    }

    private void post(@NonNull Ev event) {
        post(event.json);
    }

    @NonNull
    private static Object orNull(@Nullable String value) {
        return value == null ? JSONObject.NULL : value;
    }

    private void post(@NonNull JSONObject event) {
        handler.post(() -> {
            state.apply(event);
            notifyListeners();
        });
    }

    @MainThread
    private void notifyListeners() {
        for (Listener listener : listeners) listener.onBenchStateChanged(state);
    }
}
