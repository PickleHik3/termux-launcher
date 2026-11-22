package com.termux.app.chrome;

import android.graphics.Bitmap;
import android.graphics.Rect;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * The wallpaper frames {@link WallpaperBlurCache} publishes, held in GPU memory
 * ({@link Bitmap.Config#HARDWARE}), each with a small software copy that pixel reads use instead.
 *
 * <p>The glass draws its frames through image shaders, and hwui uploads a software bitmap behind
 * an image shader again on every draw: during a Home ⇄ Terminal swipe that was three full-size
 * uploads a frame on the RenderThread (pong, 2026-11-20). A {@code HARDWARE} bitmap already is a
 * texture, so it is uploaded once, when it is made. It cannot be read or drawn into a software
 * canvas, so whoever reads pixels asks {@link #readable} for the copy kept here, an eighth of the
 * frame each way, and maps its rect with {@link #mapToReadable}.</p>
 *
 * <p>The full-size software frame is not kept: the cache recycles it once its twin and copy
 * exist, so a frame's pixels move from the heap to graphics memory rather than doubling.</p>
 */
public final class HardwareFrame {

    /** The sampling copy is the frame divided by this each way: 1/64 of its pixels. */
    public static final int SAMPLE_DIVISOR = 8;

    /** Each published twin's sampling copy; weak, so a twin the collector takes drops its copy. */
    private static final Map<Bitmap, Bitmap> sSamples =
        Collections.synchronizedMap(new WeakHashMap<>());

    private HardwareFrame() {}

    /**
     * Any thread, the blur worker in practice: {@code frame}'s {@code HARDWARE} twin, with its
     * sampling copy registered, or null when either could not be made (the caller then keeps
     * publishing the software frame, which every consumer can still use). Never recycles
     * {@code frame}; a frame that is already {@code HARDWARE} is returned as it is.
     */
    @Nullable
    public static Bitmap publish(@Nullable Bitmap frame) {
        if (frame == null || frame.isRecycled()) return null;
        if (frame.getConfig() == Bitmap.Config.HARDWARE) return frame;
        Bitmap sample = null;
        Bitmap twin = null;
        try {
            sample = Bitmap.createScaledBitmap(frame, sampleSize(frame.getWidth()),
                sampleSize(frame.getHeight()), true);
            if (sample == frame) sample = frame.copy(Bitmap.Config.ARGB_8888, false);
            twin = frame.copy(Bitmap.Config.HARDWARE, false);
        } catch (RuntimeException | OutOfMemoryError failed) {
            twin = null;
        }
        if (twin == null || sample == null || twin.getConfig() != Bitmap.Config.HARDWARE) {
            if (sample != null && sample != frame) sample.recycle();
            if (twin != null && twin != frame) twin.recycle();
            return null;
        }
        sSamples.put(twin, sample);
        return twin;
    }

    /** One side of the sampling copy for a frame side of {@code size} pixels; at least 1. */
    @VisibleForTesting
    static int sampleSize(int size) {
        return Math.max(1, (size + SAMPLE_DIVISOR / 2) / SAMPLE_DIVISOR);
    }

    /**
     * What a pixel read of {@code frame} reads: {@code frame} itself when it is software, its
     * sampling copy when it is a published {@code HARDWARE} twin, or null when nothing can be read
     * (recycled, or a {@code HARDWARE} bitmap this class never published).
     */
    @Nullable
    public static Bitmap readable(@Nullable Bitmap frame) {
        if (frame == null || frame.isRecycled()) return null;
        if (frame.getConfig() != Bitmap.Config.HARDWARE) return frame;
        Bitmap sample = sSamples.get(frame);
        return sample == null || sample.isRecycled() ? null : sample;
    }

    /**
     * {@code region}, in {@code frame}'s pixels, in {@code readable}'s pixels: the same rect when
     * they are one bitmap, else scaled by their size ratio, rounded outward and kept at least one
     * pixel each way so a sliver of the frame still reads the copy's pixel under it.
     */
    public static void mapToReadable(@NonNull Bitmap frame, @NonNull Bitmap readable,
                                     @NonNull Rect region, @NonNull Rect out) {
        if (readable == frame || (readable.getWidth() == frame.getWidth()
                && readable.getHeight() == frame.getHeight())) {
            out.set(region);
            return;
        }
        float sx = readable.getWidth() / (float) frame.getWidth();
        float sy = readable.getHeight() / (float) frame.getHeight();
        int left = (int) Math.floor(region.left * sx);
        int top = (int) Math.floor(region.top * sy);
        int right = (int) Math.ceil(region.right * sx);
        int bottom = (int) Math.ceil(region.bottom * sy);
        out.set(left, top, Math.max(left + 1, right), Math.max(top + 1, bottom));
    }

    /** Visible for tests: forgets every registered copy. */
    @VisibleForTesting
    static void clearForTest() {
        sSamples.clear();
    }
}
