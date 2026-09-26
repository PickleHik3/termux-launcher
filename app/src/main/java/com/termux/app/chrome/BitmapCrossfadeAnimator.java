package com.termux.app.chrome;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.View;

import androidx.annotation.NonNull;

/**
 * Crossfades an {@code ImageView}'s bitmap over {@link FrameCrossfade#DURATION_MS} on the settle
 * curve, for the wallpaper-change swaps a plain {@code ImageView.setImageBitmap} would otherwise
 * land outright — the frost bands and the dock's own backdrop.
 *
 * <p>A {@code TransitionDrawable} does the same job for a stock two-layer swap, but it eases
 * linearly and its layers are opaque to the rest of the chrome's "is this bitmap still on screen"
 * scan ({@code isFrameInUse}), which only knows {@code BitmapDrawable}. This instead composites
 * {@code from} and {@code to} into one fresh {@code ARGB_8888} bitmap per tick and hands the result
 * to {@code sink} as an ordinary bitmap, so every other invariant here — the height-compatibility
 * check, a {@code RenderEffect} already installed on the view — keeps seeing exactly what it always
 * has. The last tick hands {@code to} itself, never a composite, so the resident bitmap afterward is
 * the same object identity the rest of the cache and its in-use scan already reason about.</p>
 */
public final class BitmapCrossfadeAnimator {

    private BitmapCrossfadeAnimator() {}

    /** Where each tick's frame goes; {@code finished} true only on the last call, with {@code to}. */
    public interface Sink {
        void onFrame(@NonNull Bitmap frame, boolean finished);
    }

    /**
     * Starts the fade, ticking on {@code driver}'s animation frames. {@code from} and {@code to}
     * must already be the same size — every caller here cuts both from the same target rect — or
     * this hands {@code to} straight to {@code sink} and stops, the same as reduced motion does.
     */
    public static void run(@NonNull View driver, @NonNull Bitmap from, @NonNull Bitmap to,
                           boolean reducedMotion, @NonNull Sink sink) {
        if (reducedMotion || from.isRecycled() || to.isRecycled()
            || from.getWidth() != to.getWidth() || from.getHeight() != to.getHeight()) {
            sink.onFrame(to, true);
            return;
        }
        FrameCrossfade crossfade = new FrameCrossfade();
        crossfade.start();
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        tick(driver, from, to, crossfade, paint, sink);
    }

    private static void tick(@NonNull View driver, @NonNull Bitmap from, @NonNull Bitmap to,
                             @NonNull FrameCrossfade crossfade, @NonNull Paint paint,
                             @NonNull Sink sink) {
        if (from.isRecycled() || to.isRecycled()) {
            // Something else recycled one of these mid-fade (the view was torn down, a further
            // clear landed) — stop compositing into a bitmap that can no longer draw.
            return;
        }
        float progress = crossfade.progress();
        boolean finished = progress >= 1f;
        if (finished) {
            sink.onFrame(to, true);
            return;
        }
        Bitmap composite = Bitmap.createBitmap(to.getWidth(), to.getHeight(), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(composite);
        paint.setAlpha(255);
        canvas.drawBitmap(from, 0f, 0f, paint);
        paint.setAlpha(Math.round(255f * progress));
        canvas.drawBitmap(to, 0f, 0f, paint);
        sink.onFrame(composite, false);
        driver.postOnAnimation(() -> tick(driver, from, to, crossfade, paint, sink));
    }
}
