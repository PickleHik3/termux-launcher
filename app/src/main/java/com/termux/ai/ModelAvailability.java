package com.termux.ai;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;

/**
 * Model availability (ADR 0027 section 2): the one answer to "which models are on this phone" and,
 * for a Feature, "which model serves it, and in what state". Every screen and Launcherctl's model
 * routes read it; none keeps a cache of its own.
 *
 * <p>The on-phone list is the downloaded models together with the imported ones, built here and
 * nowhere else (an imported model of the same id wins). Inspecting every package on disk is slow, so
 * the list is kept until something says the models changed: {@link TaiModelStore}'s write methods
 * call {@link #modelsChanged()}. A short backstop age covers files changed behind the store's back
 * and writes from the other process. What is loaded is never cached: it is one small read of the
 * runtime presence file ({@link TaiRuntimePresence}), so it cannot drift.
 *
 * <p>Listeners hear on the main thread that something may have changed (a model list change, a
 * download event or a presence broadcast) and re-read what they show.
 */
public final class ModelAvailability {
    /** Where one Feature's model stands. */
    public enum State {
        /** No model on the phone and none downloading, or the Feature has no model to run. */
        MISSING,
        DOWNLOADING,
        /** On the phone, not in the runtime. */
        ON_PHONE,
        LOADING,
        LOADED,
        REMOTE_READY,
        REMOTE_NOT_SET_UP
    }

    /** The model that serves a Feature and its state. */
    public static final class FeatureModel {
        @NonNull public final TaiFunction feature;
        /** The model id (a {@code remote/<id>} name for a remote one); null when the Feature runs without a model. */
        @Nullable public final String modelId;
        @NonNull public final State state;
        /** What {@link TaiFunctionModels#resolve} answered, for callers that want its accelerator, chain or source. */
        @NonNull public final TaiFunctionModels.Resolution resolution;

        FeatureModel(@NonNull TaiFunction feature, @Nullable String modelId, @NonNull State state,
                     @NonNull TaiFunctionModels.Resolution resolution) {
            this.feature = feature;
            this.modelId = modelId;
            this.state = state;
            this.resolution = resolution;
        }
    }

    public interface Listener {
        /** Something about the models changed; re-read what you show. Main thread. */
        void onModelAvailabilityChanged();
    }

    /** What the module reads; the real one is {@link #contextBackend}. Tests fake it. */
    interface Backend {
        /** Downloaded models, then imported ones over them; reads every package on disk. */
        @NonNull Map<String, TaiModelSpec> readModels();

        @NonNull TaiRuntimePresence.Snapshot presence();

        @NonNull TaiFunctionModels.Resolution resolve(@NonNull TaiFunction feature);

        /** The stored pick of {@code feature}: "", a model id, {@code remote/<id>} or {@code off}. */
        @NonNull String pick(@NonNull TaiFunction feature);

        boolean remoteConfigured();

        /** The Default model's id. */
        @NonNull String defaultModelId();

        /** The newest download record for the model, or null. */
        @Nullable TaiDownloadHub.Snapshot download(@NonNull String modelId);

        /**
         * Starts calling {@code onChange} when a download starts, finishes, fails or is cancelled and on
         * presence broadcasts, and {@code onProgress} for a download's byte ticks; returns the stop.
         */
        @NonNull Runnable watch(@NonNull Runnable onChange, @NonNull Runnable onProgress);
    }

    /** The list is re-read after this long even without a notification. */
    static final long BACKSTOP_MS = 5_000L;
    /** Listeners hear a download's progress ticks no more often than this. */
    static final long PROGRESS_INTERVAL_MS = 1_000L;

    private static volatile ModelAvailability instance;

    private final Backend backend;
    @Nullable private Context owner;
    private final Executor mainThread;
    private final java.util.function.LongSupplier clock;
    private final CopyOnWriteArrayList<Listener> listeners = new CopyOnWriteArrayList<>();
    private final Object lock = new Object();
    @Nullable private Map<String, TaiModelSpec> models;
    private long modelsReadAt;
    private long generation;
    @Nullable private Runnable stopWatching;
    private boolean dispatchPending;
    private long lastProgressAt = Long.MIN_VALUE;
    private final Runnable dispatch = this::dispatchNow;

    ModelAvailability(@NonNull Backend backend, @NonNull Executor mainThread,
                      @NonNull java.util.function.LongSupplier clock) {
        this.backend = backend;
        this.mainThread = mainThread;
        this.clock = clock;
    }

    @NonNull
    public static ModelAvailability get(@NonNull Context context) {
        Context app = context.getApplicationContext();
        ModelAvailability current = instance;
        if (current == null || current.owner != app) {
            synchronized (ModelAvailability.class) {
                current = instance;
                // The Application only changes between tests; one over a dead one reads the wrong preferences.
                if (current == null || current.owner != app) {
                    if (current != null) current.stopWatchingNow();
                    Handler handler = new Handler(Looper.getMainLooper());
                    instance = current = new ModelAvailability(contextBackend(app), handler::post,
                        SystemClock::elapsedRealtime);
                    current.owner = app;
                }
            }
        }
        return current;
    }

    /** Forgets the process instance so a test gets a fresh one over a fresh application. */
    @androidx.annotation.VisibleForTesting
    public static synchronized void resetForTesting() {
        ModelAvailability old = instance;
        instance = null;
        if (old != null) old.stopWatchingNow();
    }

    /**
     * {@link TaiModelStore}'s write methods call this: the models, or a download's status, changed.
     * Drops the list and tells the listeners. Any thread; does nothing before anyone has asked.
     */
    public static void modelsChanged() {
        ModelAvailability current = instance;
        if (current != null) current.onModelsChanged();
    }

    void onModelsChanged() {
        synchronized (lock) {
            models = null;
            generation++;
        }
        scheduleDispatch();
    }

    // ------------------------------------------------------------------------------- the models

    /**
     * The models on the phone by id: downloaded ones, then imported ones (which replace a download of
     * the same id). Read-only. Can take a moment the first time after a change; not for hot paths.
     */
    @NonNull
    public Map<String, TaiModelSpec> models() {
        long now = clock.getAsLong();
        long seen;
        synchronized (lock) {
            if (models != null && !isStale(now, modelsReadAt, BACKSTOP_MS)) return models;
            seen = generation;
        }
        Map<String, TaiModelSpec> read = Collections.unmodifiableMap(new LinkedHashMap<>(backend.readModels()));
        synchronized (lock) {
            // A change that landed while the disk was being read leaves the list unset for the next call.
            if (generation == seen) {
                models = read;
                modelsReadAt = now;
            }
        }
        return read;
    }

    /**
     * Whether a cache read at {@code readAt} is older than {@code ttlMs} at {@code now}. One never
     * read ({@code Long.MIN_VALUE}) is stale: {@code now - Long.MIN_VALUE} overflows to a negative
     * number, which once kept the Assistant's downloaded set empty for good (54495b6df).
     */
    static boolean isStale(long now, long readAt, long ttlMs) {
        return readAt == Long.MIN_VALUE || now - readAt > ttlMs;
    }

    /** True when {@code modelId} is on the phone. */
    public boolean isOnPhone(@Nullable String modelId) {
        if (modelId == null || modelId.isEmpty()) return false;
        return models().containsKey(modelId);
    }

    // ------------------------------------------------------------------------------- the states

    /** What the runtime has loaded or is loading, read fresh. */
    @NonNull
    public TaiRuntimePresence.Snapshot presence() {
        return backend.presence();
    }

    /** The Default model's id. */
    @NonNull
    public String defaultModelId() {
        return backend.defaultModelId();
    }

    /** The newest download record of {@code modelId}, or null. */
    @Nullable
    public TaiDownloadHub.Snapshot download(@NonNull String modelId) {
        return backend.download(modelId);
    }

    /** The state of one model: remote, loaded, loading, downloading, on the phone or missing. */
    @NonNull
    public State stateOf(@Nullable String modelId) {
        if (modelId == null || modelId.isEmpty()) return State.MISSING;
        if (TaiRemoteProvider.isRemoteModelName(modelId) || TaiFunctionModels.isRemote(modelId)) {
            return backend.remoteConfigured() ? State.REMOTE_READY : State.REMOTE_NOT_SET_UP;
        }
        TaiRuntimePresence.Snapshot presence = backend.presence();
        String running = presence.modelId;
        boolean isRuntimeModel = running != null && sameModel(running, modelId);
        if (presence.loaded && isRuntimeModel) return State.LOADED;
        // The runtime's file does not always say which model it is loading; the Default model is the
        // one a load or an answer starts, so it takes the unnamed case.
        if (presence.loading && (isRuntimeModel
            || running == null && sameModel(backend.defaultModelId(), modelId))) {
            return State.LOADING;
        }
        TaiDownloadHub.Snapshot download = backend.download(modelId);
        if (download != null && (download.isLive() || download.isPaused())) return State.DOWNLOADING;
        if (isOnPhone(modelId)) return State.ON_PHONE;
        return State.MISSING;
    }

    /** The model that serves {@code feature} and its state. */
    @NonNull
    public FeatureModel forFeature(@NonNull TaiFunction feature) {
        TaiFunctionModels.Resolution resolution = backend.resolve(feature);
        String pick = backend.pick(feature);
        if (TaiFunctionModels.isRemote(pick) && !resolution.isRemote() && !backend.remoteConfigured()) {
            // The pick is the remote model and the provider is not set up; resolve fell through to a local one.
            return new FeatureModel(feature, pick, State.REMOTE_NOT_SET_UP, resolution);
        }
        String id = resolution.requestModel();
        if (id == null) return new FeatureModel(feature, null, State.MISSING, resolution);
        State state = resolution.isRemote()
            ? (backend.remoteConfigured() ? State.REMOTE_READY : State.REMOTE_NOT_SET_UP)
            : stateOf(id);
        return new FeatureModel(feature, id, state, resolution);
    }

    /** The runtime reports the text-only or scoped id of the same file; they are one model. */
    public static boolean sameModel(@NonNull String a, @NonNull String b) {
        return stripScope(a).equals(stripScope(b));
    }

    @NonNull
    private static String stripScope(@NonNull String id) {
        if (id.endsWith("-vision")) return id.substring(0, id.length() - "-vision".length());
        if (id.endsWith("-audio")) return id.substring(0, id.length() - "-audio".length());
        return id;
    }

    // ---------------------------------------------------------------------------------- listeners

    /** Main thread. The first listener starts watching downloads and the presence broadcast. */
    public void addListener(@NonNull Listener listener) {
        synchronized (lock) {
            if (!listeners.addIfAbsent(listener)) return;
            if (stopWatching == null) stopWatching = backend.watch(this::scheduleDispatch, this::progressTick);
        }
    }

    /** Main thread. The last listener out stops the watching. */
    public void removeListener(@NonNull Listener listener) {
        synchronized (lock) {
            if (!listeners.remove(listener)) return;
            if (listeners.isEmpty()) stopWatchingNow();
        }
    }

    private void stopWatchingNow() {
        Runnable stop;
        synchronized (lock) {
            stop = stopWatching;
            stopWatching = null;
        }
        if (stop != null) stop.run();
    }

    /** Progress-only events reach the listeners at most once a second; screens that draw a bar use the hub. */
    private void progressTick() {
        long now = clock.getAsLong();
        synchronized (lock) {
            if (lastProgressAt != Long.MIN_VALUE && now - lastProgressAt < PROGRESS_INTERVAL_MS) return;
            lastProgressAt = now;
        }
        scheduleDispatch();
    }

    private void scheduleDispatch() {
        synchronized (lock) {
            if (dispatchPending || listeners.isEmpty()) return;
            dispatchPending = true;
        }
        mainThread.execute(dispatch);
    }

    private void dispatchNow() {
        synchronized (lock) {
            dispatchPending = false;
        }
        for (Listener listener : listeners) listener.onModelAvailabilityChanged();
    }

    // ------------------------------------------------------------------------------ real backend

    @NonNull
    private static Backend contextBackend(@NonNull Context app) {
        final TaiModelStore store = new TaiModelStore(app);
        final TaiSettings settings = new TaiSettings(app);
        final TaiRemoteProvider remote = new TaiRemoteProvider(app);
        return new Backend() {
            @NonNull
            @Override
            public Map<String, TaiModelSpec> readModels() {
                LinkedHashMap<String, TaiModelSpec> all = new LinkedHashMap<>(store.getDownloadedReadableModels());
                all.putAll(store.getInstalledUserModels());
                return all;
            }

            @NonNull
            @Override
            public TaiRuntimePresence.Snapshot presence() {
                return TaiRuntimePresence.read(app);
            }

            @NonNull
            @Override
            public TaiFunctionModels.Resolution resolve(@NonNull TaiFunction feature) {
                return TaiFunctionModels.forContext(app).resolve(feature);
            }

            @NonNull
            @Override
            public String pick(@NonNull TaiFunction feature) {
                return TaiFunctionModels.forContext(app).pick(feature);
            }

            @Override
            public boolean remoteConfigured() {
                return remote.isConfigured();
            }

            @NonNull
            @Override
            public String defaultModelId() {
                return settings.getDefaultAssistantModel();
            }

            @Nullable
            @Override
            public TaiDownloadHub.Snapshot download(@NonNull String modelId) {
                List<TaiDownloadHub.Snapshot> all = TaiDownloadHub.get(app).snapshot();
                for (int i = all.size() - 1; i >= 0; i--) {
                    if (modelId.equals(all.get(i).modelId)) return all.get(i);
                }
                return null;
            }

            @NonNull
            @Override
            public Runnable watch(@NonNull Runnable onEvent, @NonNull Runnable onProgress) {
                TaiDownloadHub hub = TaiDownloadHub.get(app);
                final String[] lastStatuses = {null};
                TaiDownloadHub.Listener downloads = snapshots -> {
                    StringBuilder statuses = new StringBuilder();
                    for (TaiDownloadHub.Snapshot item : snapshots) {
                        statuses.append(item.id).append(':').append(item.status).append(';');
                    }
                    String now = statuses.toString();
                    boolean moved = !now.equals(lastStatuses[0]);
                    lastStatuses[0] = now;
                    if (moved) onEvent.run();
                    else onProgress.run();
                };
                hub.addListener(downloads);
                BroadcastReceiver receiver = new BroadcastReceiver() {
                    @Override
                    public void onReceive(Context context, Intent intent) {
                        onEvent.run();
                    }
                };
                IntentFilter filter = new IntentFilter(TaiRuntimePresence.ACTION_PRESENCE_CHANGED);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    app.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED);
                } else {
                    app.registerReceiver(receiver, filter);
                }
                return () -> {
                    hub.removeListener(downloads);
                    try {
                        app.unregisterReceiver(receiver);
                    } catch (IllegalArgumentException ignored) {
                    }
                };
            }
        };
    }
}
