package com.termux.app.assistant;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.ai.TaiModelRegistry;

import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * What the page's model chip and sheet show: the state of the default assistant model and of the two
 * catalogue chat models (Gemma 4 E2B and E4B), and the actions that change it. It reads existing
 * state only; the runtime offers no observer, so while at least one observer is attached a poller
 * reads it about once a second, and with none attached nothing runs. Observers hear of a change
 * only, on the main thread, and an observer added later is told the current state at once.
 */
public final class AssistantModelStatus {
    public static final String MODEL_E2B = TaiModelRegistry.MODEL_GEMMA_4_E2B_IT;
    public static final String MODEL_E4B = TaiModelRegistry.MODEL_GEMMA_4_E4B_IT;
    static final long POLL_INTERVAL_MS = 1000L;

    public enum Kind {
        /** No chat model is downloaded at all. Only the default model's entry is ever this. */
        NONE,
        NOT_DOWNLOADED,
        DOWNLOADING,
        DOWNLOADED,
        LOADING,
        READY
    }

    public static final class State {
        @NonNull public final Kind kind;
        /** Download or load progress, 0 to 100; -1 when it is not known or does not apply. */
        public final int percent;

        State(@NonNull Kind kind, int percent) {
            this.kind = kind;
            this.percent = percent;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof State && ((State) o).kind == kind && ((State) o).percent == percent;
        }

        @Override
        public int hashCode() {
            return Objects.hash(kind, percent);
        }

        @Override
        public String toString() {
            return kind + (percent >= 0 ? "(" + percent + ")" : "");
        }
    }

    public static final class Entry {
        @NonNull public final String modelId;
        @NonNull public final State state;

        Entry(@NonNull String modelId, @NonNull State state) {
            this.modelId = modelId;
            this.state = state;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Entry && ((Entry) o).modelId.equals(modelId) && ((Entry) o).state.equals(state);
        }

        @Override
        public int hashCode() {
            return Objects.hash(modelId, state);
        }
    }

    public static final class Snapshot {
        /** The launcher's default assistant model. */
        @NonNull public final Entry defaultModel;
        @NonNull public final Entry gemmaE2b;
        @NonNull public final Entry gemmaE4b;

        Snapshot(@NonNull Entry defaultModel, @NonNull Entry gemmaE2b, @NonNull Entry gemmaE4b) {
            this.defaultModel = defaultModel;
            this.gemmaE2b = gemmaE2b;
            this.gemmaE4b = gemmaE4b;
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof Snapshot)) return false;
            Snapshot s = (Snapshot) o;
            return defaultModel.equals(s.defaultModel) && gemmaE2b.equals(s.gemmaE2b)
                && gemmaE4b.equals(s.gemmaE4b);
        }

        @Override
        public int hashCode() {
            return Objects.hash(defaultModel, gemmaE2b, gemmaE4b);
        }
    }

    /** A model's download record, as far as the status needs it. */
    public static final class Download {
        /** Queued, downloading, verifying or paused: the file is not complete yet. */
        public final boolean inProgress;
        public final long bytesRead;
        public final long totalBytes;

        public Download(boolean inProgress, long bytesRead, long totalBytes) {
            this.inProgress = inProgress;
            this.bytesRead = bytesRead;
            this.totalBytes = totalBytes;
        }
    }

    /** Where the status reads from; the real one is {@link AssistantModelSource}. */
    public interface Source {
        @NonNull String defaultModelId();

        boolean runtimeLoaded();

        boolean runtimeLoading();

        /** The model the runtime has loaded or is loading, or null. */
        @Nullable String runtimeModelId();

        boolean isDownloaded(@NonNull String modelId);

        @Nullable Download download(@NonNull String modelId);
    }

    public interface Observer {
        void onModelStatus(@NonNull Snapshot snapshot);
    }

    /** The result of an action, from the runtime's own reply. */
    public static final class ActionResult {
        public final boolean ok;
        /** The runtime's error code, or empty. */
        @NonNull public final String code;
        @NonNull public final String message;

        public ActionResult(boolean ok, @NonNull String code, @NonNull String message) {
            this.ok = ok;
            this.code = code;
            this.message = message;
        }
    }

    public interface Callback {
        void onResult(@NonNull ActionResult result);
    }

    /** The actions, over the existing APIs; the real one is {@link AssistantModelActions}. */
    public interface Actions {
        @NonNull ActionResult setDefaultAssistantModel(@NonNull String modelId);

        @NonNull ActionResult download(@NonNull String modelId);

        @NonNull ActionResult cancelDownload(@NonNull String modelId);

        /** Loads the default assistant model. */
        @NonNull ActionResult load();

        /** Unloads the chat model. */
        @NonNull ActionResult unload();
    }

    private static AssistantModelStatus sInstance;

    private final Source source;
    private final Actions actions;
    private final Executor notifier;
    private final Executor actionExecutor;
    private final CopyOnWriteArrayList<Observer> observers = new CopyOnWriteArrayList<>();
    private final Object pollLock = new Object();
    @Nullable private ScheduledExecutorService poller;
    @Nullable private ScheduledFuture<?> pollTask;
    @Nullable private Snapshot last;

    AssistantModelStatus(@NonNull Source source, @NonNull Actions actions, @NonNull Executor notifier,
                         @NonNull Executor actionExecutor) {
        this.source = source;
        this.actions = actions;
        this.notifier = notifier;
        this.actionExecutor = actionExecutor;
    }

    public static synchronized AssistantModelStatus getInstance(@NonNull Context context) {
        if (sInstance == null) {
            Context app = context.getApplicationContext();
            ExecutorService actionsThread = Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "assistant-model-actions");
                t.setDaemon(true);
                return t;
            });
            sInstance = new AssistantModelStatus(new AssistantModelSource(app), new AssistantModelActions(app),
                new Handler(Looper.getMainLooper())::post, actionsThread);
        }
        return sInstance;
    }

    /** Drops the singleton. Intended for unit tests. */
    public static synchronized void resetForTesting() {
        if (sInstance != null) sInstance.stopPolling();
        sInstance = null;
    }

    // ------------------------------------------------------------------------------ the mapping

    @NonNull
    public static Snapshot compute(@NonNull Source source) {
        String defaultId = source.defaultModelId();
        State e2b = stateOf(source, MODEL_E2B);
        State e4b = stateOf(source, MODEL_E4B);
        State def = stateOf(source, defaultId);
        if (def.kind == Kind.NOT_DOWNLOADED && e2b.kind == Kind.NOT_DOWNLOADED
            && e4b.kind == Kind.NOT_DOWNLOADED) {
            def = new State(Kind.NONE, -1);
        }
        return new Snapshot(new Entry(defaultId, def), new Entry(MODEL_E2B, e2b), new Entry(MODEL_E4B, e4b));
    }

    @NonNull
    static State stateOf(@NonNull Source source, @NonNull String modelId) {
        String running = source.runtimeModelId();
        boolean isRuntimeModel = running != null && sameModel(running, modelId);
        if (source.runtimeLoaded() && isRuntimeModel) return new State(Kind.READY, -1);
        // The runtime's file does not always say which model it is loading; the default is the one
        // a load or an answer starts, so it takes the unnamed case.
        boolean loadingThis = source.runtimeLoading()
            && (isRuntimeModel || running == null && sameModel(source.defaultModelId(), modelId));
        if (loadingThis) return new State(Kind.LOADING, -1);
        Download download = source.download(modelId);
        if (download != null && download.inProgress) {
            int percent = download.totalBytes > 0
                ? (int) Math.max(0L, Math.min(100L, download.bytesRead * 100L / download.totalBytes))
                : -1;
            return new State(Kind.DOWNLOADING, percent);
        }
        if (source.isDownloaded(modelId)) return new State(Kind.DOWNLOADED, -1);
        return new State(Kind.NOT_DOWNLOADED, -1);
    }

    /** The runtime reports the text-only or vision-scoped id of the same file; they are one model. */
    static boolean sameModel(@NonNull String a, @NonNull String b) {
        return stripScope(a).equals(stripScope(b));
    }

    private static String stripScope(String id) {
        if (id.endsWith("-vision")) return id.substring(0, id.length() - "-vision".length());
        if (id.endsWith("-audio")) return id.substring(0, id.length() - "-audio".length());
        return id;
    }

    // ----------------------------------------------------------------------------------- polling

    /** The state as of now. Reads the stores; call it off the main thread. */
    @NonNull
    public Snapshot current() {
        return compute(source);
    }

    public void addObserver(@NonNull Observer observer) {
        if (!observers.addIfAbsent(observer)) return;
        synchronized (pollLock) {
            if (poller == null) {
                poller = Executors.newSingleThreadScheduledExecutor(r -> {
                    Thread t = new Thread(r, "assistant-model-status");
                    t.setDaemon(true);
                    return t;
                });
                last = null;
                pollTask = poller.scheduleWithFixedDelay(this::pollOnce, 0L, POLL_INTERVAL_MS, TimeUnit.MILLISECONDS);
            } else {
                // A new observer is told the current state without waiting for a change.
                Snapshot known = last;
                if (known != null) notifier.execute(() -> observer.onModelStatus(known));
            }
        }
    }

    public void removeObserver(@NonNull Observer observer) {
        if (!observers.remove(observer)) return;
        if (observers.isEmpty()) stopPolling();
    }

    public boolean isPolling() {
        synchronized (pollLock) {
            return poller != null;
        }
    }

    private void stopPolling() {
        synchronized (pollLock) {
            if (pollTask != null) pollTask.cancel(false);
            if (poller != null) poller.shutdownNow();
            pollTask = null;
            poller = null;
            last = null;
        }
    }

    /** One read; tells the observers when it differs from the last. */
    void pollOnce() {
        final Snapshot now;
        try {
            now = compute(source);
        } catch (RuntimeException e) {
            return;
        }
        synchronized (pollLock) {
            if (poller == null || now.equals(last)) return;
            last = now;
        }
        notifier.execute(() -> {
            for (Observer o : observers) o.onModelStatus(now);
        });
    }

    // ----------------------------------------------------------------------------------- actions

    /** Makes this the launcher's default assistant model, the pick the Model centre and the API use. */
    public void setDefaultAssistantModel(@NonNull String modelId, @Nullable Callback callback) {
        run(() -> actions.setDefaultAssistantModel(modelId), callback);
    }

    public void download(@NonNull String modelId, @Nullable Callback callback) {
        run(() -> actions.download(modelId), callback);
    }

    public void cancelDownload(@NonNull String modelId, @Nullable Callback callback) {
        run(() -> actions.cancelDownload(modelId), callback);
    }

    /** Loads the default assistant model. */
    public void load(@Nullable Callback callback) {
        run(actions::load, callback);
    }

    /** Unloads the chat model. */
    public void unload(@Nullable Callback callback) {
        run(actions::unload, callback);
    }

    private void run(@NonNull java.util.function.Supplier<ActionResult> action, @Nullable Callback callback) {
        actionExecutor.execute(() -> {
            ActionResult result;
            try {
                result = action.get();
            } catch (RuntimeException e) {
                result = new ActionResult(false, "error", String.valueOf(e.getMessage()));
            }
            if (callback == null) return;
            final ActionResult delivered = result;
            notifier.execute(() -> callback.onResult(delivered));
        });
    }
}
