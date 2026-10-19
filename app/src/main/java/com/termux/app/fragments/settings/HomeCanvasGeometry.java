package com.termux.app.fragments.settings;

import androidx.annotation.NonNull;

import com.termux.app.launcher.widget.WidgetGridMetrics;

/**
 * Home's page on the layout canvas, as pure arithmetic: the widget grid inside the page's opening.
 * Every figure is the launcher's own in dp times the canvas scale (canvas pixels per real dp), so
 * the picture is Home's geometry to scale.
 *
 * <p>The grid's rim is Home's Margin and the air between its cells is the grid's own gap
 * ({@link WidgetGridMetrics#GAP_DP}), the same two numbers the live grid and
 * {@link com.termux.app.launcher.widget.WidgetGridCaps} work with. Home wears no status bar, so
 * the grid starts at the opening's top.
 *
 * <p>Rects are written into a caller's {@code float[]} (left, top, right, bottom), so a draw pass
 * allocates nothing. Nothing here touches a view.
 */
final class HomeCanvasGeometry {

    private HomeCanvasGeometry() {}

    /** The air between two cells, in dp: the live grid's. */
    static final float GAP_DP = WidgetGridMetrics.GAP_DP;

    /** Home's Margin as the grid's rim, in canvas pixels; a negative Margin counts as none. */
    static float rimPx(float marginDp, float scale) {
        return Math.max(0f, marginDp) * Math.max(0f, scale);
    }

    /** The air between two cells, in canvas pixels. */
    static float gapPx(float scale) {
        return GAP_DP * Math.max(0f, scale);
    }

    /**
     * The grid's area inside the page's opening, written to {@code out}: the opening a Margin in
     * on every side. False, with {@code out} untouched, when the Margin leaves nothing.
     */
    static boolean gridArea(float left, float top, float right, float bottom, float scale,
                            float marginDp, @NonNull float[] out) {
        float rim = rimPx(marginDp, scale);
        if (right - left - 2f * rim <= 0f || bottom - top - 2f * rim <= 0f) return false;
        out[0] = left + rim;
        out[1] = top + rim;
        out[2] = right - rim;
        out[3] = bottom - rim;
        return true;
    }

    /** One cell's extent along an axis of {@code length} holding {@code count} cells. */
    static float cellExtent(float length, int count, float gapPx) {
        int n = Math.max(1, count);
        return Math.max(0f, (length - gapPx * (n - 1)) / n);
    }
}
