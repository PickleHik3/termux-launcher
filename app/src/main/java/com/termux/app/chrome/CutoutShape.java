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
 * cutout in landscape stands off the top edge, so it is never one); on API 31+ narrowed to the
 * bounds of the cutout's own path, which traces a punch hole's circle inside its rect.
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
                // Only a trace that stands inside the rect and fills most of its width: a path
                // in another space than the window's (pong's reads 511..540 against 454..540)
                // would put the box beside the camera.
                if (CameraHoleGeometry.pathTraceFits(hole.left, hole.right, traced.left,
                        traced.right)) {
                    traced.intersect(hole);
                    hole = traced;
                }
            }
        }
        return hole;
    }
}
