package com.termux.app.chrome;

import android.graphics.Bitmap;
import android.graphics.Rect;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/** A wallpaper the cache can capture from without a window, a wallpaper, or a blur. */
final class FakeWallpaperBlurSource implements WallpaperBlurCache.Source {

    @NonNull final Rect frameRect = new Rect(0, 0, 100, 200);
    boolean managedSource;
    int systemWallpaperId = 7;
    int orientation = android.content.res.Configuration.ORIENTATION_PORTRAIT;
    @NonNull final File managedFile = new File("/nonexistent/managed-wallpaper.png");

    /** Frames handed out, oldest first — the test's stand-in for "what did we capture". */
    @NonNull final List<Bitmap> captured = new ArrayList<>();
    int captureCount;
    int clearedCount;
    /** Frames the outside world claims to be drawing, so the cache must not recycle them. */
    @NonNull final List<Bitmap> inUse = new ArrayList<>();
    /** How much smaller than the capture a blurred frame comes back, as the real renderer's does. */
    int blurScale = 1;

    @NonNull
    @Override
    public Rect wallpaperFrameRect() {
        return new Rect(frameRect);
    }

    @Override
    public boolean useManagedWallpaperSource() {
        return managedSource;
    }

    @Override
    public int systemWallpaperId() {
        return systemWallpaperId;
    }

    @NonNull
    @Override
    public File managedWallpaperExactFile() {
        return managedFile;
    }

    @Override
    public int orientation() {
        return orientation;
    }

    @Nullable
    @Override
    public WallpaperBlurCache.FrameCapture beginCapture(@NonNull Rect frameRect, @NonNull View wallpaperFrame) {
        captureCount++;
        Bitmap bitmap = Bitmap.createBitmap(Math.max(1, frameRect.width()),
            Math.max(1, frameRect.height()), Bitmap.Config.ARGB_8888);
        captured.add(bitmap);
        if (borrowing) {
            // As the managed wallpaper's capture does: the decode reads a picture lent for it.
            loansOut++;
            return WallpaperBlurCache.FrameCapture.deferred(() -> bitmap, () -> {
                loansOut--;
                loansReturned++;
            });
        }
        return WallpaperBlurCache.FrameCapture.ready(bitmap);
    }

    /** When set, each capture is a deferred decode that borrows, and gives back, a loan. */
    boolean borrowing;
    /** Loans taken and not yet given back. */
    int loansOut;
    /** Times a loan was given back; more than one per capture is a double release. */
    int loansReturned;

    @Nullable
    @Override
    public Bitmap preBlur(@NonNull Bitmap sourceBitmap, int blurRadiusDp) {
        // The real renderer returns the source itself at radius 0 and a new bitmap otherwise; the
        // cache's recycling bookkeeping differs between those, so mirror both.
        if (blurRadiusDp <= 0) return sourceBitmap;
        if (blurScale <= 1) return sourceBitmap.copy(Bitmap.Config.ARGB_8888, false);
        return Bitmap.createBitmap(Math.max(1, sourceBitmap.getWidth() / blurScale),
            Math.max(1, sourceBitmap.getHeight() / blurScale), Bitmap.Config.ARGB_8888);
    }

    @Override
    public boolean isFrameInUse(@Nullable Bitmap frame) {
        return frame != null && inUse.contains(frame);
    }

    @Override
    public void onCacheCleared() {
        clearedCount++;
    }

    /** The frame tint the glass asks the frames for; none unless a test sets one. */
    int frameTint = GlassFrameTint.NONE;

    @Override
    public int glassFrameTint() {
        return frameTint;
    }

    /** The Vibrancy the glass asks the frames for; 100, the default, bakes nothing. */
    int vibrancyPercent = 100;

    @Override
    public int glassVibrancyPercent() {
        return vibrancyPercent;
    }
}
