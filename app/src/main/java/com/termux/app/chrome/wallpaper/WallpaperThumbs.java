package com.termux.app.chrome.wallpaper;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.annotation.VisibleForTesting;
import androidx.annotation.WorkerThread;

import com.termux.shared.logger.Logger;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;

/**
 * Pictures for the picker, made off the main thread and kept in memory only until
 * {@link #release()}, which recycles them:
 * <ul>
 *   <li>rest-pose stills of the preshipped backgrounds, rendered once per (background, size)
 *   through {@link AnimatedWallpaperStill}. Below API 34 nothing renders and such a request
 *   never answers;</li>
 *   <li>photos (the slots' pictures, a pending crop, the recent photos), decoded once per
 *   (file, size) at about that size, on every API level.</li>
 * </ul>
 * Nothing animates here.
 */
public final class WallpaperThumbs {

    /** Called on the main thread with the picture, never with null. */
    public interface Sink {
        void onThumb(@NonNull Bitmap bitmap);
    }

    /** Decodes a photo at about {@code widthPx} × {@code heightPx}; null when it cannot. */
    public interface PhotoDecoder {
        @WorkerThread
        @Nullable Bitmap decode(@NonNull File file, int widthPx, int heightPx);
    }

    private static final String LOG_TAG = "WallpaperThumbs";

    private final Handler mMain = new Handler(Looper.getMainLooper());
    private final ExecutorService mRenderer = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "wallpaper-picker-thumbs");
        t.setDaemon(true);
        return t;
    });
    @NonNull private final Executor mPhotoWorker;
    @NonNull private final PhotoDecoder mDecoder;
    private final Map<String, Bitmap> mCache = new HashMap<>();
    private final Map<String, List<Sink>> mWaiting = new HashMap<>();
    private boolean mReleased;

    public WallpaperThumbs() {
        this(Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "wallpaper-picker-photos");
            t.setDaemon(true);
            return t;
        }), WallpaperThumbs::decodeSampled);
    }

    /** Tests pass a same-thread executor and a decoder that needs no real picture. */
    @VisibleForTesting
    public WallpaperThumbs(@NonNull Executor photoWorker, @NonNull PhotoDecoder decoder) {
        mPhotoWorker = photoWorker;
        mDecoder = decoder;
    }

    /** The still of {@code w} at {@code widthPx} × {@code heightPx}, now if cached, else later. */
    @MainThread
    public void request(@NonNull AnimatedWallpaper w, int widthPx, int heightPx, @NonNull Sink sink) {
        if (mReleased || widthPx <= 0 || heightPx <= 0 || Build.VERSION.SDK_INT < 34) return;
        final String key = key(w, widthPx, heightPx);
        if (answerOrQueue(key, sink)) return;
        render(w, key, widthPx, heightPx);
    }

    /** A cached still, or null. */
    @Nullable
    @MainThread
    public Bitmap cached(@NonNull AnimatedWallpaper w, int widthPx, int heightPx) {
        return mCache.get(key(w, widthPx, heightPx));
    }

    /** The photo in {@code file} at about {@code widthPx} × {@code heightPx}, now if cached, else later. */
    @MainThread
    public void requestPhoto(@NonNull File file, int widthPx, int heightPx, @NonNull Sink sink) {
        if (mReleased || widthPx <= 0 || heightPx <= 0) return;
        final String key = key(file, widthPx, heightPx);
        if (answerOrQueue(key, sink)) return;
        try {
            mPhotoWorker.execute(() -> {
                Bitmap bmp = null;
                try {
                    bmp = mDecoder.decode(file, widthPx, heightPx);
                } catch (RuntimeException | OutOfMemoryError e) {
                    Logger.logStackTraceWithMessage(LOG_TAG, "Photo decode failed", e);
                }
                final Bitmap result = bmp;
                mMain.post(() -> deliver(key, result));
            });
        } catch (RejectedExecutionException ignored) {
            mWaiting.remove(key);
        }
    }

    /** A cached photo, or null. */
    @Nullable
    @MainThread
    public Bitmap cachedPhoto(@NonNull File file, int widthPx, int heightPx) {
        return mCache.get(key(file, widthPx, heightPx));
    }

    @NonNull
    private static String key(@NonNull AnimatedWallpaper w, int widthPx, int heightPx) {
        return w.id() + "@" + widthPx + "x" + heightPx;
    }

    /** A photo's key names its content's age and size too, so a replaced file decodes afresh. */
    @NonNull
    private static String key(@NonNull File file, int widthPx, int heightPx) {
        return "photo:" + file.getAbsolutePath() + ":" + file.lastModified() + ":" + file.length()
            + "@" + widthPx + "x" + heightPx;
    }

    /** True when {@code sink} was answered from the cache or joined a request already running. */
    private boolean answerOrQueue(@NonNull String key, @NonNull Sink sink) {
        Bitmap cached = mCache.get(key);
        if (cached != null) {
            sink.onThumb(cached);
            return true;
        }
        List<Sink> waiting = mWaiting.get(key);
        if (waiting != null) {
            waiting.add(sink);
            return true;
        }
        waiting = new ArrayList<>();
        waiting.add(sink);
        mWaiting.put(key, waiting);
        return false;
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
        if (result == null) return;
        if (mReleased) {
            result.recycle();
            return;
        }
        mCache.put(key, result);
        if (waiting == null) return;
        for (Sink sink : waiting) sink.onThumb(result);
    }

    /**
     * Stops the workers and recycles every picture. The page lets go of them in its views first:
     * it is closed, so nothing draws them again.
     */
    @MainThread
    public void release() {
        mReleased = true;
        mRenderer.shutdown();
        if (mPhotoWorker instanceof ExecutorService) ((ExecutorService) mPhotoWorker).shutdown();
        for (Bitmap b : mCache.values()) if (!b.isRecycled()) b.recycle();
        mCache.clear();
        mWaiting.clear();
    }

    /**
     * Decodes {@code file} at the largest power-of-two step down that still covers
     * {@code widthPx} × {@code heightPx}, so a wide cropped photo costs about its thumbnail.
     */
    @WorkerThread
    @Nullable
    public static Bitmap decodeSampled(@NonNull File file, int widthPx, int heightPx) {
        if (!file.isFile()) return null;
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(file.getAbsolutePath(), bounds);
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null;
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight, widthPx, heightPx);
        return BitmapFactory.decodeFile(file.getAbsolutePath(), options);
    }

    /** The largest power of two that keeps {@code w} × {@code h} at least {@code reqW} × {@code reqH}. */
    static int sampleSize(int w, int h, int reqW, int reqH) {
        int sample = 1;
        while (w / (sample * 2) >= reqW && h / (sample * 2) >= reqH) sample *= 2;
        return sample;
    }
}
