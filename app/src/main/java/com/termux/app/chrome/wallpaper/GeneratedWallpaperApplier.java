package com.termux.app.chrome.wallpaper;

import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The shared plumbing under the wallpaper slots: the single apply worker and the main-thread
 * result post. Shared by the in-app picker and {@code POST /v1/wallpaper}. No view is touched.
 */
public final class GeneratedWallpaperApplier {

    /** Result of an apply, always on the main thread. */
    public interface Callback {
        void onDone(boolean ok, @Nullable String error);
    }

    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "generated-wallpaper-apply");
        t.setDaemon(true);
        return t;
    });
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private GeneratedWallpaperApplier() {}

    /** Runs {@code job} on the apply worker, behind any apply already queued. */
    static void onWorker(@NonNull Runnable job) {
        WORKER.execute(job);
    }

    /** Posts {@code cb}'s result to the main thread. */
    static void post(@Nullable Callback cb, boolean ok, @Nullable String error) {
        if (cb == null) return;
        MAIN.post(() -> cb.onDone(ok, error));
    }
}
