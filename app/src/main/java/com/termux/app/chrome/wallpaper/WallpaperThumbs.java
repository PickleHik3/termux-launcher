package com.termux.app.chrome.wallpaper;

import android.graphics.Bitmap;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

import com.termux.shared.logger.Logger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;

/**
 * Rest-pose stills of the preshipped backgrounds for the picker, rendered once per (background,
 * size) on one worker thread through {@link AnimatedWallpaperStill} and kept in memory only until
 * {@link #release()}. Nothing animates here. Below API 34 nothing renders and every request
 * simply never answers.
 */
public final class WallpaperThumbs {

    /** Called on the main thread with the still, never with null. */
    public interface Sink {
        void onThumb(@NonNull Bitmap bitmap);
    }

    private static final String LOG_TAG = "WallpaperThumbs";

    private final Handler mMain = new Handler(Looper.getMainLooper());
    private final ExecutorService mRenderer = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "wallpaper-picker-thumbs");
        t.setDaemon(true);
        return t;
    });
    private final Map<String, Bitmap> mCache = new HashMap<>();
    private final Map<String, List<Sink>> mWaiting = new HashMap<>();
    private boolean mReleased;

    /** The still of {@code w} at {@code widthPx} × {@code heightPx}, now if cached, else later. */
    @MainThread
    public void request(@NonNull AnimatedWallpaper w, int widthPx, int heightPx, @NonNull Sink sink) {
        if (mReleased || widthPx <= 0 || heightPx <= 0 || Build.VERSION.SDK_INT < 34) return;
        final String key = w.id() + "@" + widthPx + "x" + heightPx;
        Bitmap cached = mCache.get(key);
        if (cached != null) {
            sink.onThumb(cached);
            return;
        }
        List<Sink> waiting = mWaiting.get(key);
        if (waiting != null) {
            waiting.add(sink);
            return;
        }
        waiting = new ArrayList<>();
        waiting.add(sink);
        mWaiting.put(key, waiting);
        render(w, key, widthPx, heightPx);
    }

    /** A cached still, or null. */
    @Nullable
    @MainThread
    public Bitmap cached(@NonNull AnimatedWallpaper w, int widthPx, int heightPx) {
        return mCache.get(w.id() + "@" + widthPx + "x" + heightPx);
    }

    @RequiresApi(34)
    private void render(@NonNull AnimatedWallpaper w, @NonNull String key, int widthPx, int heightPx) {
        // Theme attributes would be read here on the main thread; the worker gets plain colours.
        final int[] palette;
        try {
            palette = WallpaperPaletteCapture.own(w);
        } catch (RuntimeException e) {
            Logger.logStackTraceWithMessage(LOG_TAG, "Thumbnail palette failed", e);
            mWaiting.remove(key);
            return;
        }
        try {
            mRenderer.execute(() -> {
                Bitmap bmp = null;
                try {
                    bmp = AnimatedWallpaperStill.render(w, palette, widthPx, heightPx);
                } catch (RuntimeException | OutOfMemoryError e) {
                    Logger.logStackTraceWithMessage(LOG_TAG, "Thumbnail render failed", e);
                }
                final Bitmap result = bmp;
                mMain.post(() -> deliver(key, result));
            });
        } catch (RejectedExecutionException ignored) {
            mWaiting.remove(key);
        }
    }

    private void deliver(@NonNull String key, @Nullable Bitmap result) {
        List<Sink> waiting = mWaiting.remove(key);
        if (mReleased || result == null) return;
        mCache.put(key, result);
        if (waiting == null) return;
        for (Sink sink : waiting) sink.onThumb(result);
    }

    /** Stops the worker and forgets the stills (not recycled: views may still draw them). */
    @MainThread
    public void release() {
        mReleased = true;
        mRenderer.shutdown();
        mCache.clear();
        mWaiting.clear();
    }
}
