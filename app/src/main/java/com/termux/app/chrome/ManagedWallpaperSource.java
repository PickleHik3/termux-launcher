package com.termux.app.chrome;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import java.io.File;
import java.util.concurrent.Executor;

/**
 * The managed wallpaper file, decoded once and kept at the size it is drawn at.
 *
 * <p>The blur cache asked for the wallpaper region afresh on every miss, and each time the whole
 * file was decoded on the main thread - a 15 MB PNG of 2102x4696 took seconds a go, and with a
 * few radii cycling through the cache the launcher stopped answering input. Here the file is read
 * on a background thread, scaled to just cover the frame it is drawn into, and held; a miss while
 * the read is in flight gets nothing and a call once it lands. The file's path, size and mtime
 * are the identity: a new wallpaper reads again.
 */
public final class ManagedWallpaperSource {

    /** Told, on the caller's thread, when a read that a caller waited on has landed. */
    public interface Listener {
        void onManagedWallpaperReady();
    }

    /** Runs a callback on the thread the source is used from. */
    public interface MainThread {
        void post(@NonNull Runnable runnable);
    }

    @NonNull private final Executor mExecutor;
    @NonNull private final MainThread mMainThread;

    @Nullable private Bitmap mBitmap;
    @Nullable private String mPath;
    private long mLastModified = -1L;
    private long mLength = -1L;
    private int mCoverWidth, mCoverHeight;
    /** The identity being read right now, or null when nothing is in flight. */
    @Nullable private String mReadingKey;
    /** The identity of the last read that yielded no bitmap, so a broken file is not reread each miss. */
    @Nullable private String mFailedKey;

    public ManagedWallpaperSource(@NonNull Executor executor, @NonNull MainThread mainThread) {
        mExecutor = executor;
        mMainThread = mainThread;
    }

    /**
     * The decoded wallpaper scaled to cover {@code coverWidth}x{@code coverHeight}, or null while
     * it is being read - the listener is called when it is - or when the file cannot be decoded.
     */
    @Nullable
    public Bitmap obtain(@NonNull File file, int coverWidth, int coverHeight, @NonNull Listener listener) {
        coverWidth = Math.max(1, coverWidth);
        coverHeight = Math.max(1, coverHeight);
        String path = file.getAbsolutePath();
        long lastModified = file.lastModified();
        long length = file.length();
        String key = identity(path, lastModified, length, coverWidth, coverHeight);
        if (mBitmap != null && !mBitmap.isRecycled() && key.equals(identity(mPath, mLastModified,
                mLength, mCoverWidth, mCoverHeight))) {
            return mBitmap;
        }
        if (key.equals(mFailedKey)) return null;
        if (key.equals(mReadingKey)) return null;
        mReadingKey = key;
        final int width = coverWidth, height = coverHeight;
        mExecutor.execute(() -> {
            Bitmap decoded = decodeCover(file, width, height);
            mMainThread.post(() -> {
                if (!key.equals(mReadingKey)) {
                    // Superseded by a newer read; this result is nobody's.
                    if (decoded != null) decoded.recycle();
                    return;
                }
                mReadingKey = null;
                if (decoded == null) {
                    mFailedKey = key;
                    return;
                }
                clear();
                mBitmap = decoded;
                mPath = path;
                mLastModified = lastModified;
                mLength = length;
                mCoverWidth = width;
                mCoverHeight = height;
                listener.onManagedWallpaperReady();
            });
        });
        return null;
    }

    /** True while a read is in flight: the caller should draw nothing rather than fall back. */
    public boolean isReading() {
        return mReadingKey != null;
    }

    /** Drops the held bitmap; the next call reads again. */
    public void clear() {
        if (mBitmap != null && !mBitmap.isRecycled()) mBitmap.recycle();
        mBitmap = null;
        mPath = null;
    }

    /**
     * Decodes {@code file} at the smallest power-of-two subsampling that still covers
     * {@code coverWidth}x{@code coverHeight}, then scales it to exactly cover them - the size the
     * shader draws it at, so nothing bigger is ever kept.
     */
    @Nullable
    @VisibleForTesting
    static Bitmap decodeCover(@NonNull File file, int coverWidth, int coverHeight) {
        if (!file.isFile()) return null;
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(file.getAbsolutePath(), bounds);
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null;
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight, coverWidth, coverHeight);
        options.inPreferredConfig = Bitmap.Config.ARGB_8888;
        Bitmap decoded;
        try {
            decoded = BitmapFactory.decodeFile(file.getAbsolutePath(), options);
        } catch (OutOfMemoryError e) {
            return null;
        }
        if (decoded == null) return null;
        float scale = Math.max((float) coverWidth / decoded.getWidth(),
            (float) coverHeight / decoded.getHeight());
        if (scale >= 1f) return decoded;
        int width = Math.max(1, Math.round(decoded.getWidth() * scale));
        int height = Math.max(1, Math.round(decoded.getHeight() * scale));
        Bitmap scaled = Bitmap.createScaledBitmap(decoded, width, height, true);
        if (scaled != decoded) decoded.recycle();
        return scaled;
    }

    /** The largest power of two that leaves the subsampled image still covering the frame. */
    @VisibleForTesting
    static int sampleSize(int sourceWidth, int sourceHeight, int coverWidth, int coverHeight) {
        int sample = 1;
        while (sourceWidth / (sample * 2) >= coverWidth && sourceHeight / (sample * 2) >= coverHeight) {
            sample *= 2;
        }
        return sample;
    }

    @NonNull
    private static String identity(@Nullable String path, long lastModified, long length,
                                   int coverWidth, int coverHeight) {
        return path + "|" + lastModified + "|" + length + "|" + coverWidth + "x" + coverHeight;
    }
}
