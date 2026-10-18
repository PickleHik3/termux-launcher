package com.termux.app.fragments.settings;

import androidx.annotation.NonNull;

import com.termux.app.launcher.widget.WidgetGridMetrics;
import com.termux.app.place.PlaceLayout.Edge;
import com.termux.app.statusbar.StatusBarEdgeGeometry;

/**
 * Home's page on the layout canvas, as pure arithmetic: the widget grid inside the page's opening
 * and the band its two status pills float in. Every figure is the launcher's own in dp times the
 * canvas scale (canvas pixels per real dp), so the picture is Home's geometry to scale.
 *
 * <ul>
 *   <li>The grid's rim is Home's Margin and the air between its cells is the grid's own gap
 *       ({@link WidgetGridMetrics#GAP_DP}), the same two numbers the live grid and
 *       {@link com.termux.app.launcher.widget.WidgetGridCaps} work with.</li>
 *   <li>The pills — the windows pill at the start, the system readout (CPU, RAM, weather) at the
 *       end — stand a Margin in from the page's top corners and float over the grid, which keeps
 *       its size whether they are there or not, as Home's status always has
 *       ({@link com.termux.app.place.HomeChromePolicy#statusOverlays}). Their height is the
 *       compact floating status bar's row, which they stand in for on Home.</li>
 * </ul>
 *
 * <p>Rects are written into a caller's {@code float[]} (left, top, right, bottom), so a draw pass
 * allocates nothing. Nothing here touches a view.
 */
final class HomeCanvasGeometry {

    private HomeCanvasGeometry() {}

    /** The air between two cells, in dp: the live grid's. */
    static final float GAP_DP = WidgetGridMetrics.GAP_DP;

    /** How much of the width inside the rim the windows pill takes, at the start. */
    static final float WINDOWS_PILL_SHARE = 0.34f;
    /** How much of it the system readout pill takes, at the end. */
    static final float SYSTEM_PILL_SHARE = 0.46f;

    /** The pills' height, in dp: the compact floating status bar's row. */
    static float pillsBandDp() {
        return StatusBarEdgeGeometry.thicknessDp(Edge.TOP, true, true);
    }

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

    /**
     * The two pills inside the page's opening, written to {@code out} as eight floats: the start
     * pill's rect, then the end pill's, in physical left-to-right order (the canvas is pinned left
     * to right). A Margin in from the top corners, the pills' height tall, or held to what the
     * opening has inside its rim. False, with {@code out} untouched, when there is no room.
     */
    static boolean pills(float left, float top, float right, float bottom, float scale,
                         float marginDp, @NonNull float[] out) {
        float rim = rimPx(marginDp, scale);
        float width = right - left - 2f * rim;
        float room = bottom - top - 2f * rim;
        if (width <= 0f || room <= 0f) return false;
        float height = Math.min(room, pillsBandDp() * Math.max(0f, scale));
        if (height <= 0f) return false;
        float pillTop = top + rim;
        float start = left + rim;
        float end = right - rim;
        out[0] = start;
        out[1] = pillTop;
        out[2] = start + width * WINDOWS_PILL_SHARE;
        out[3] = pillTop + height;
        out[4] = end - width * SYSTEM_PILL_SHARE;
        out[5] = pillTop;
        out[6] = end;
        out[7] = pillTop + height;
        return true;
    }
}
