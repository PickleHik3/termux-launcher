package com.termux.app.chrome;

import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.DisplayCutoutCompat;
import androidx.core.view.WindowInsetsCompat;

/**
 * Reads the camera's shape on the screen's top edge from a window's insets, for
 * {@link CameraHoleGeometry}: Android's bounding rects that touch the edge, joined (a side
 * cutout in landscape stands off the top edge, so it is never one); on API 31+ the bounds of the
 * cutout's own path where it meets those rects, which trace the punch hole itself.
 * Allocates, so callers keep the answer per cutout instead of asking per inset dispatch.
 */
public final class CutoutShape {

    private CutoutShape() {}

    /** The shape in window pixels, or null when the top edge has none. */
    @Nullable
    public static Rect top(@Nullable DisplayCutoutCompat cutout,
                           @NonNull WindowInsetsCompat insets) {
        Rect hole = null;
        if (cutout != null) {
            for (Rect rect : cutout.getBoundingRects()) {
                if (rect == null || rect.isEmpty() || rect.top > 0) continue;
                if (hole == null) hole = new Rect(rect);
                else hole.union(rect);
            }
        }
        if (hole != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            android.view.WindowInsets platform = insets.toWindowInsets();
            android.view.DisplayCutout real = platform == null ? null : platform.getDisplayCutout();
            Path path = real == null ? null : real.getCutoutPath();
            if (path != null) {
                RectF bounds = new RectF();
                path.computeBounds(bounds, true);
                Rect traced = new Rect();
                bounds.roundOut(traced);
                // CAMHOLE-DBG temporary: the raw cutout values.
                android.util.Log.d("CAMHOLE-DBG", "t=" + android.os.SystemClock.uptimeMillis()
                    + " cutout rects=" + cutout.getBoundingRects() + " joined=" + hole
                    + " rectTop=" + real.getBoundingRectTop() + " safe=" + real.getSafeInsetLeft()
                    + "," + real.getSafeInsetTop() + "," + real.getSafeInsetRight() + ","
                    + real.getSafeInsetBottom() + " pathRaw=" + bounds + " traced=" + traced
                    + " stands=" + CameraHoleGeometry.traceStandsForRect(hole.left, hole.right,
                        traced.left, traced.right));
                // The traced path is the camera, whole: a rect that stands beside part of it
                // (pong's rect is 454..540 while its hole runs on past 551) must not cut it.
                if (CameraHoleGeometry.traceStandsForRect(hole.left, hole.right, traced.left,
                        traced.right)) {
                    hole = traced;
                }
            }
        }
        return hole;
    }
}
