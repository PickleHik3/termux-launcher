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
    public Bitmap captureWallpaperFrame(@NonNull Rect frameRect, @NonNull View wallpaperFrame) {
        captureCount++;
        Bitmap bitmap = Bitmap.createBitmap(Math.max(1, frameRect.width()),
            Math.max(1, frameRect.height()), Bitmap.Config.ARGB_8888);
        captured.add(bitmap);
        return bitmap;
    }

    @Nullable
    @Override
    public Bitmap preBlur(@NonNull Bitmap sourceBitmap, int blurRadiusDp) {
        // The real renderer returns the source itself at radius 0 and a new bitmap otherwise; the
        // cache's recycling bookkeeping differs between those, so mirror both.
        return blurRadiusDp <= 0 ? sourceBitmap : sourceBitmap.copy(Bitmap.Config.ARGB_8888, false);
    }

    @Override
    public boolean isFrameInUse(@Nullable Bitmap frame) {
        return frame != null && inUse.contains(frame);
    }

    @Override
    public void onCacheCleared() {
        clearedCount++;
    }
}
