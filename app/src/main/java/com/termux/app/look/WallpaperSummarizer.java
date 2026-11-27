package com.termux.app.look;

import android.app.WallpaperColors;
import android.app.WallpaperManager;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import com.termux.app.chrome.WallpaperPictureReader;
import com.termux.shared.logger.Logger;

import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Reads the wallpaper into a {@link WallpaperSummary} off the main thread and keeps the three
 * Looks made from it ({@link WallpaperLooks#store}). The app's own picture is decoded at a small
 * size (the longest side down to about 64 px, the bitmap recycled at once); Android's wallpaper
 * is read from its colours, which costs no pixels.
 */
public final class WallpaperSummarizer {

    private static final String LOG_TAG = "WallpaperSummarizer";
    private static final int DECODE_MIN_SIDE_PX = 64;

    @Nullable private static ExecutorService sWorker;
    /** The stamp of the picture last decoded, so an arrival with the same picture decodes nothing. */
    private static long sDecodedStamp;

    private WallpaperSummarizer() { }

    /** Summarises the wallpaper now showing on a worker and stores the Looks; one run at a time. */
    public static synchronized void refreshInBackground(@NonNull Context context,
                                                        boolean appOnlyPicture,
                                                        @NonNull LookStore store) {
        Context app = context.getApplicationContext();
        if (sWorker == null) sWorker = Executors.newSingleThreadExecutor();
        sWorker.execute(() -> {
            WallpaperSummary summary = summarize(app, appOnlyPicture);
            if (summary != null) WallpaperLooks.store(store, summary);
        });
    }

    @WorkerThread
    @Nullable
    private static WallpaperSummary summarize(@NonNull Context context, boolean appOnlyPicture) {
        if (appOnlyPicture) {
            File exact = WallpaperPictureReader.managedWallpaperExactFile(context);
            if (exact.isFile()) {
                long stamp = exact.lastModified() * 31 + exact.length();
                if (stamp == sDecodedStamp) return null;
                WallpaperSummary decoded = ofFile(exact);
                if (decoded != null) {
                    sDecodedStamp = stamp;
                    return decoded;
                }
            }
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O_MR1) return null;
        try {
            WallpaperManager manager = WallpaperManager.getInstance(context);
            WallpaperColors colors = manager.getWallpaperColors(WallpaperManager.FLAG_SYSTEM);
            if (colors == null) return null;
            Integer secondary = colors.getSecondaryColor() == null ? null
                : colors.getSecondaryColor().toArgb();
            Integer tertiary = colors.getTertiaryColor() == null ? null
                : colors.getTertiaryColor().toArgb();
            return WallpaperSummary.ofColors(colors.getPrimaryColor().toArgb(), secondary, tertiary);
        } catch (RuntimeException e) {
            Logger.logStackTraceWithMessage(LOG_TAG, "Cannot read the wallpaper colours", e);
            return null;
        }
    }

    @WorkerThread
    @Nullable
    private static WallpaperSummary ofFile(@NonNull File file) {
        Bitmap bitmap = null;
        try {
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(file.getAbsolutePath(), bounds);
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null;
            int longest = Math.max(bounds.outWidth, bounds.outHeight);
            int sample = 1;
            while (longest / (sample * 2) >= DECODE_MIN_SIDE_PX) sample *= 2;
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inSampleSize = sample;
            options.inPreferredConfig = Bitmap.Config.ARGB_8888;
            bitmap = BitmapFactory.decodeFile(file.getAbsolutePath(), options);
            if (bitmap == null) return null;
            int width = bitmap.getWidth();
            int height = bitmap.getHeight();
            int[] pixels = new int[width * height];
            bitmap.getPixels(pixels, 0, width, 0, 0, width, height);
            return WallpaperSummary.ofPixels(pixels, width, height);
        } catch (Exception | OutOfMemoryError e) {
            Logger.logStackTraceWithMessage(LOG_TAG, "Cannot summarise " + file.getName(), e);
            return null;
        } finally {
            if (bitmap != null) bitmap.recycle();
        }
    }
}
