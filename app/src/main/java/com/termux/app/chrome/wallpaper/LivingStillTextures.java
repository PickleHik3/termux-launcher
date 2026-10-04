package com.termux.app.chrome.wallpaper;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;

import com.termux.app.chrome.wallpaper.living.Manifest;

import java.io.File;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.Map;

/**
 * The decoded pictures of one living still: the photo, the depth map and the three mask PNGs, in
 * software bitmaps, decoded once per process and shared by every shader that plays that still (the
 * live frames, the backdrop, the picker card, the lock engine).
 *
 * <p>Nothing here recycles a bitmap and the cache holds them weakly: a set lives while a shader
 * holds it, and goes with the last shader (the renderer's release, 30 s after the launcher
 * stops, or {@link #trim}). The photo is decoded at most {@link #MAX_IMAGE_SIDE} px on its long
 * side, never enlarged; the maps are decoded at their own size. Decoding runs on the caller's
 * thread (the first shader of a still pays it, tens of milliseconds).</p>
 */
@RequiresApi(33)
final class LivingStillTextures {

    /** The photo's long side cap, px: a 1.5-screen-wide portrait frame is about 1620 x 2400. */
    static final int MAX_IMAGE_SIDE = 2400;

    final Bitmap image;
    final Bitmap depth;
    final Bitmap maskA;
    final Bitmap maskB;
    final Bitmap maskC;
    /** Wind and still; a 1x1 black picture for a version 1 still, which has no such file. */
    final Bitmap maskD;

    private LivingStillTextures(Bitmap image, Bitmap depth, Bitmap maskA, Bitmap maskB, Bitmap maskC, Bitmap maskD) {
        this.image = image;
        this.depth = depth;
        this.maskA = maskA;
        this.maskB = maskB;
        this.maskC = maskC;
        this.maskD = maskD;
    }

    private static final Map<String, WeakReference<LivingStillTextures>> CACHE = new HashMap<>();

    /** The pictures of {@code manifest}: the cached set while some shader holds it, else decoded now. */
    @NonNull
    static synchronized LivingStillTextures acquire(@NonNull Manifest manifest) throws IOException {
        String key = manifest.hash() + "@" + manifest.recipeFile().lastModified();
        WeakReference<LivingStillTextures> ref = CACHE.get(key);
        LivingStillTextures cached = ref == null ? null : ref.get();
        if (cached != null) return cached;
        LivingStillTextures fresh = new LivingStillTextures(
            decode(manifest.image(), MAX_IMAGE_SIDE), decode(manifest.depth(), 0),
            decode(manifest.maskA(), 0), decode(manifest.maskB(), 0), decode(manifest.maskC(), 0),
            decodeOrBlack(manifest.maskD()));
        CACHE.put(key, new WeakReference<>(fresh));
        return fresh;
    }

    /** Drops the cache's references (memory pressure); sets a shader still holds stay alive. */
    static synchronized void trim() {
        CACHE.clear();
    }

    @NonNull
    private static Bitmap decodeOrBlack(@NonNull File file) {
        if (file.isFile()) {
            try {
                return decode(file, 0);
            } catch (IOException ignored) {
                // fall through: an unreadable mask D is no mask D
            }
        }
        Bitmap black = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888);
        black.eraseColor(0xFF000000);
        return black;
    }

    @NonNull
    private static Bitmap decode(@NonNull File file, int maxSide) throws IOException {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(file.getAbsolutePath(), bounds);
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw new IOException("Not an image: " + file);
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inPreferredConfig = Bitmap.Config.ARGB_8888;
        int longSide = Math.max(bounds.outWidth, bounds.outHeight);
        int sample = 1;
        if (maxSide > 0) {
            while (longSide / (sample * 2) >= maxSide) sample *= 2;
        }
        o.inSampleSize = sample;
        Bitmap b = BitmapFactory.decodeFile(file.getAbsolutePath(), o);
        if (b == null) throw new IOException("Cannot decode " + file);
        return b;
    }

    /** Pixel size of the photo as decoded, {@code {w, h}}. */
    @NonNull
    float[] imageSize() {
        return new float[] {image.getWidth(), image.getHeight()};
    }
}
