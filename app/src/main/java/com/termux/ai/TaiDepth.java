package com.termux.ai;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.util.List;
import java.util.Map;

/**
 * What the photo wallpaper's "Make depth" asks of the AI side: which depth model, whether it is on
 * the phone, and one call that downloads it when missing and then makes the depth image of a photo.
 * The model runs in the runtime process ({@link TaiDepthRuns}); nothing from a run stays resident.
 *
 * <p>Threading: {@link #start} may be called from any thread and returns at once; all work runs on
 * short-lived worker threads and every {@link Listener} callback arrives on the main thread. After
 * {@link Handle#cancel}, or once the last callback has run, no callback fires and nothing keeps a
 * reference to the listener. {@link #modelId}, {@link #modelReady} and {@link #downloadBytes} read
 * preferences and stat one file: cheap, but not for a hot path.
 *
 * <p>Failure codes in {@link Listener#onFailed}: {@code depth_model_missing} (the model is not
 * installed any more), {@code download_start_failed} and {@code download_failed} (the model could
 * not be downloaded; try again), {@code no_space} (the phone has too little free storage for the
 * download), {@code runtime_busy} (a chat or image model is answering; try again after it),
 * {@code insufficient_memory} (close some apps), {@code depth_busy} (another depth image is being
 * made), {@code photo_unreadable}, {@code model_unusable}, {@code run_failed}, {@code write_failed},
 * {@code out_of_memory} (from the run itself), {@code runtime_unavailable} (the AI runtime could
 * not be reached) and {@code bad_request} (no photo or output given). A cancel is never a failure.
 */
public final class TaiDepth {
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private TaiDepth() {}

    /** Callbacks, in order: optional downloading percents, optional making percents, then done or failed. */
    public interface Listener {
        /** The model is being downloaded; 0-100 of its size. */
        void onDownloading(int percent);

        /** The depth image is being made; 0-100 of the run. */
        void onMaking(int percent);

        /** The depth image is at {@code out}. */
        void onDone(@NonNull File out);

        /** The request ended without an image; see the class comment for {@code code}. Nothing is left at {@code out}. */
        void onFailed(@NonNull String code);
    }

    /** One running request. */
    public static final class Handle {
        private final Run run;

        Handle(@Nullable Run run) {
            this.run = run;
        }

        /** Stops the download or the run, whichever is going. Safe from any thread, and more than once. */
        public void cancel() {
            if (run != null) run.cancel();
        }
    }

    /** The catalogue id of the depth model this phone uses; a depth model's id even when {@code context} is null. */
    @NonNull
    public static String modelId(@Nullable Context context) {
        long ramClass = 0L;
        if (context != null) {
            try {
                ramClass = TaiDeviceCapabilities.detect(context.getApplicationContext()).memoryBytes;
            } catch (RuntimeException ignored) {
            }
        }
        return TaiDepthModels.choose(ramClass);
    }

    /** True when {@link #modelId} is downloaded and its file is readable. */
    public static boolean modelReady(@Nullable Context context) {
        if (context == null) return false;
        try {
            return ready(context.getApplicationContext(), modelId(context));
        } catch (RuntimeException e) {
            return false;
        }
    }

    /** The catalogue size in bytes of {@link #modelId}, for naming the download; 0 when unknown. */
    public static long downloadBytes(@Nullable Context context) {
        TaiModelCatalog.CatalogEntry entry = TaiModelCatalog.get(modelId(context));
        return entry == null ? 0L : Math.max(0L, entry.sizeBytes);
    }

    private static boolean ready(@NonNull Context app, @NonNull String id) {
        TaiModelStore store = new TaiModelStore(app);
        Map<String, TaiModelSpec> installed = store.getInstalledUserModels();
        if (installed.containsKey(id)) return true;
        return store.getDownloadedReadableModels().containsKey(id);
    }

    /**
     * Downloads the depth model when it is missing, then makes the depth image of {@code photo} at
     * {@code out}, reporting on the main thread. Returns a handle that cancels whichever phase is
     * going. With no context, photo, or output the listener hears {@code bad_request}; with no
     * listener nothing runs.
     */
    @NonNull
    public static Handle start(@Nullable Context context, @Nullable File photo, @Nullable File out,
                               @Nullable Listener listener) {
        if (listener == null) return new Handle(null);
        if (context == null || photo == null || out == null) {
            MAIN.post(() -> listener.onFailed(TaiDepthRuns.CODE_BAD_REQUEST));
            return new Handle(null);
        }
        Run run = new Run(context.getApplicationContext(), photo, out, listener);
        run.begin();
        return new Handle(run);
    }

    /** One request: the flow, the hub subscription and the worker threads. */
    private static final class Run implements TaiDownloadHub.Listener, TaiDepthFlow.Sink {
        private final Context app;
        private final File photo;
        private final File out;
        private final String modelId;
        private final TaiDepthFlow flow = new TaiDepthFlow(this);
        /** Cleared when the request ends or is cancelled, so nothing is retained and nothing more is heard. */
        @Nullable private volatile Listener listener;
        private volatile boolean released;
        /** The last callback (done or failed) has run on the main thread. */
        private volatile boolean delivered;
        private volatile boolean downloadSeen;
        @Nullable private volatile Thread makeThread;

        Run(@NonNull Context app, @NonNull File photo, @NonNull File out, @NonNull Listener listener) {
            this.app = app;
            this.photo = photo;
            this.out = out;
            this.listener = listener;
            this.modelId = TaiDepth.modelId(app);
        }

        void begin() {
            worker("tai-depth-start", () -> {
                TaiDepthFlow.Action action = flow.begin(ready(app, modelId));
                perform(action);
            });
        }

        private void perform(@NonNull TaiDepthFlow.Action action) {
            switch (action) {
                case START_DOWNLOAD:
                    startDownload();
                    break;
                case START_MAKE:
                    detachHub();
                    makeOnThisThread();
                    break;
                default:
                    break;
            }
        }

        private void startDownload() {
            boolean queued = false;
            try {
                JSONObject result = TaiManager.getInstance(app).modelManagement().downloadCatalogModel(modelId);
                queued = result.optBoolean("ok", false);
            } catch (JSONException | RuntimeException ignored) {
            }
            if (!queued) {
                flow.onDownloadStartFailed();
                return;
            }
            TaiDownloadHub.get(app).addListener(this);
            if (flow.finished()) {
                // Cancelled while the download was being queued: the cancel call may have come first.
                detachHub();
                cancelDownloadNow();
            }
        }

        @Override
        public void onDownloadsChanged(@NonNull List<TaiDownloadHub.Snapshot> downloads) {
            TaiDownloadHub.Snapshot mine = null;
            for (TaiDownloadHub.Snapshot item : downloads) {
                if (modelId.equals(item.modelId) && (mine == null || item.updatedAtMs >= mine.updatedAtMs)) mine = item;
            }
            boolean seen = downloadSeen;
            if (mine != null) downloadSeen = true;
            TaiDepthFlow.Action action = flow.onDownloadSnapshot(mine, seen);
            if (action == TaiDepthFlow.Action.START_MAKE) {
                detachHub();
                // Off the main thread: the run blocks until it is done.
                worker("tai-depth-make", this::makeOnThisThread);
            } else if (flow.finished()) {
                detachHub();
            }
        }

        private void makeOnThisThread() {
            makeThread = Thread.currentThread();
            if (released) return;
            TaiDepthRuns.Outcome outcome;
            try {
                outcome = TaiManager.getInstance(app).depth().make(photo.getPath(), out.getPath(), modelId,
                    (stage, percent) -> flow.onMakeStage(percent));
            } catch (RuntimeException e) {
                outcome = TaiDepthRuns.Outcome.failure(TaiDepthRuns.CODE_RUNTIME_UNAVAILABLE, "");
            }
            if (released && outcome.ok) {
                // Cancelled just as it finished: a cancelled request leaves no file.
                //noinspection ResultOfMethodCallIgnored
                out.delete();
            }
            flow.onMakeResult(outcome.ok, outcome.code);
            makeThread = null;
        }

        void cancel() {
            boolean doneUnheard = flow.phase() == TaiDepthFlow.Phase.DONE && !delivered;
            released = true;
            TaiDepthFlow.Action action = flow.cancel();
            listener = null;
            detachHub();
            if (doneUnheard) {
                worker("tai-depth-cancel", () -> {
                    //noinspection ResultOfMethodCallIgnored
                    out.delete();
                });
            } else if (action == TaiDepthFlow.Action.CANCEL_DOWNLOAD) {
                worker("tai-depth-cancel", this::cancelDownloadNow);
            } else if (action == TaiDepthFlow.Action.CANCEL_MAKE) {
                worker("tai-depth-cancel", this::cancelMakeNow);
            }
        }

        private void cancelDownloadNow() {
            try {
                TaiManager.getInstance(app).modelManagement()
                    .cancelDownload(new JSONObject().put("modelId", modelId).toString());
            } catch (JSONException | RuntimeException ignored) {
            }
        }

        /** The run may not have reached the runtime yet: ask until it has taken the cancel or has ended. */
        private void cancelMakeNow() {
            for (int attempt = 0; attempt < 15; attempt++) {
                try {
                    if (TaiManager.getInstance(app).depth().cancel().optBoolean("cancelled", false)) return;
                } catch (JSONException | RuntimeException ignored) {
                }
                if (makeThread == null && attempt > 0) return;
                try {
                    Thread.sleep(150L);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }

        private void detachHub() {
            try {
                TaiDownloadHub.get(app).removeListener(this);
            } catch (RuntimeException ignored) {
            }
        }

        // ---- the flow's sink: post to the main thread, dropped once released ----

        @Override
        public void onDownloading(int percent) {
            post(l -> l.onDownloading(percent), false);
        }

        @Override
        public void onMaking(int percent) {
            post(l -> l.onMaking(percent), false);
        }

        @Override
        public void onDone() {
            post(l -> l.onDone(out), true);
        }

        @Override
        public void onFailed(@NonNull String code) {
            post(l -> l.onFailed(code), true);
        }

        private void post(@NonNull java.util.function.Consumer<Listener> call, boolean last) {
            MAIN.post(() -> {
                Listener target = released ? null : listener;
                if (target == null) return;
                if (last) {
                    delivered = true;
                    released = true;
                    listener = null;
                }
                call.accept(target);
            });
        }

        private static void worker(@NonNull String name, @NonNull Runnable job) {
            Thread thread = new Thread(job, name);
            thread.setDaemon(true);
            thread.start();
        }
    }
}
