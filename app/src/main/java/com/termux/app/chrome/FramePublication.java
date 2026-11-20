package com.termux.app.chrome;

import android.graphics.Bitmap;

import androidx.annotation.Nullable;

/**
 * How a wallpaper frame reaches the surfaces that draw it: finished immutable on the blur worker
 * ({@link #frozen}), then filed and warmed on the main thread ({@link #publish}). Once handed out a
 * frame is never written again: every bake, re-bake and new radius allocates a bitmap of its own,
 * and a frame dropped from the cache is left to the collector.
 *
 * <p>Immutable, the RenderThread uploads a frame to the GPU once and draws every later frame from
 * that texture. A mutable software bitmap is uploaded again on every frame that draws it: on pong
 * a page-sized slice of the pane glass's 1620x2412 frame went up per frame mid-swipe
 * ({@code Texture upload 1038x2013} in the trace) and the RenderThread overran. The public SDK
 * cannot mark a bitmap immutable in place, so a frame finished on a canvas is copied once, on the
 * worker, and the mutable one recycled; a bake that can produce an immutable bitmap directly (the
 * GPU blur's copy out of its buffer) costs nothing here.</p>
 *
 * <p>Pixels stay readable: the inks still sample an immutable frame with {@code getPixel(s)}.</p>
 */
public final class FramePublication {

    private FramePublication() {}

    /**
     * Worker thread. {@code frame} as an immutable bitmap: itself when it already is one, otherwise
     * an immutable copy, and {@code frame} is recycled. The caller must own {@code frame} (nothing
     * else holds it). When the copy cannot be made the mutable frame is returned and draws as it
     * did before, uploaded per draw.
     */
    @Nullable
    public static Bitmap frozen(@Nullable Bitmap frame) {
        if (frame == null || frame.isRecycled() || !frame.isMutable()
                || frame.getConfig() == Bitmap.Config.HARDWARE) {
            return frame;
        }
        Bitmap copy;
        try {
            Bitmap.Config config = frame.getConfig() != null ? frame.getConfig()
                : Bitmap.Config.ARGB_8888;
            copy = frame.copy(config, false);
        } catch (Throwable t) {
            copy = null;
        }
        if (copy == null || copy == frame) return frame;
        frame.recycle();
        return copy;
    }

    /** Main thread, as the frame is filed: asks for its texture before the first draw needs it. */
    public static void publish(@Nullable Bitmap frame) {
        if (frame == null || frame.isRecycled()) return;
        try {
            frame.prepareToDraw();
        } catch (RuntimeException ignored) {
            // A hint only: the first draw uploads it instead.
        }
    }
}
