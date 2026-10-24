package com.termux.app.sound;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.LruCache;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.File;

/**
 * The media notification's artwork, decoded straight to notification size and kept in a small
 * cache with an explicit byte budget (AGENTS.md: anything that holds pixels is bounded). Cost is
 * the decoded bitmap's own allocation, never the file's size. Worker thread only.
 */
final class MediaArtCache {

    /** The longest side art is decoded to: what the system's media player draws, and no more. */
    static final int TARGET_PX = 384;
    /** A few tracks' art: 384² × 4 bytes is about 590 KB, so this holds three. */
    static final int BUDGET_BYTES = 2 * 1024 * 1024;

    private final LruCache<String, Bitmap> cache = new LruCache<String, Bitmap>(BUDGET_BYTES) {
        @Override
        protected int sizeOf(@NonNull String key, @NonNull Bitmap value) {
            return value.getAllocationByteCount();
        }
    };

    /** The art in {@code file}, decoded to {@link #TARGET_PX} at most; null when it is not a picture. */
    @Nullable
    Bitmap get(@NonNull File file) {
        String key = file.getPath() + ':' + file.lastModified() + ':' + file.length();
        Bitmap cached = cache.get(key);
        if (cached != null) return cached;
        Bitmap decoded = decode(file);
        if (decoded != null) cache.put(key, decoded);
        return decoded;
    }

    void clear() {
        cache.evictAll();
    }

    @Nullable
    private static Bitmap decode(@NonNull File file) {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(file.getPath(), bounds);
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null;
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight, TARGET_PX);
        Bitmap sampled;
        try {
            sampled = BitmapFactory.decodeFile(file.getPath(), options);
        } catch (OutOfMemoryError e) {
            return null;
        }
        if (sampled == null) return null;
        int longest = Math.max(sampled.getWidth(), sampled.getHeight());
        if (longest <= TARGET_PX) return sampled;
        int width = Math.max(1, sampled.getWidth() * TARGET_PX / longest);
        int height = Math.max(1, sampled.getHeight() * TARGET_PX / longest);
        Bitmap scaled = Bitmap.createScaledBitmap(sampled, width, height, true);
        if (scaled != sampled) sampled.recycle();
        return scaled;
    }

    /**
     * The largest power of two that still leaves both sides at least {@code target}, so the
     * decoder reads no more pixels than the final scale needs. Pure.
     */
    static int sampleSize(int width, int height, int target) {
        int sample = 1;
        while (width / (sample * 2) >= target && height / (sample * 2) >= target) sample *= 2;
        return sample;
    }
}
