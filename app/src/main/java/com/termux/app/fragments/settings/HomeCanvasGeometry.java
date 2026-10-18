package com.termux.app.fragments.settings;

import androidx.annotation.NonNull;

import com.termux.app.launcher.widget.WidgetGridMetrics;
import com.termux.app.statusbar.HomePillsPolicy;

/**
 * Home's page on the layout canvas, as pure arithmetic: the band its two status pills stand in
 * along the page's top, and the widget grid below it. Every figure is the launcher's own in dp
 * times the canvas scale (canvas pixels per real dp), so the picture is Home's geometry to scale.
 *
 * <ul>
 *   <li>The band is the live one ({@link HomePillsPolicy#bandDp}): the compact bar's row on the
 *       style Home wears, which the live widget grid gives its room to, so the grid here starts
 *       below it too and nothing on it lies under a pill.</li>
 *   <li>The grid's rim is Home's Margin and the air between its cells is the grid's own gap
 *       ({@link WidgetGridMetrics#GAP_DP}), the same two numbers the live grid and
 *       {@link com.termux.app.launcher.widget.WidgetGridCaps} work with.</li>
 *   <li>The pills — the windows pill at the start, the system readout (CPU, RAM, weather) at the
 *       end — stand a Margin in from the page's sides, centred in the band at their live height
 *       ({@link HomePillsPolicy#PILL_MAX_HEIGHT_DP} at most, {@link
 *       HomePillsPolicy#PILL_BAND_INSET_DP} clear of the band's edges).</li>
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

    /** The pills' band, in dp: the live one, on Floating ({@code floating}) or Docked. */
    static float pillsBandDp(boolean floating) {
        return HomePillsPolicy.bandDp(floating);
    }

    /** Home's Margin as the grid's rim, in canvas pixels; a negative Margin counts as none. */
    static float rimPx(float marginDp, float scale) {
        return Math.max(0f, marginDp) * Math.max(0f, scale);
    }

    /** The air between two cells, in canvas pixels. */
    static float gapPx(float scale) {
        return GAP_DP * Math.max(0f, scale);
    }

    /** The pills' band in canvas pixels, held to the opening's height. */
    static float bandPx(float top, float bottom, float scale, boolean floating) {
        return Math.max(0f, Math.min(bottom - top, pillsBandDp(floating) * Math.max(0f, scale)));
    }

    /**
     * The grid's area inside the page's opening, written to {@code out}: below the pills' band,
     * then a Margin in on every side. False, with {@code out} untouched, when nothing is left.
     */
    static boolean gridArea(float left, float top, float right, float bottom, float scale,
                            float marginDp, boolean floating, @NonNull float[] out) {
        float rim = rimPx(marginDp, scale);
        float gridTop = top + bandPx(top, bottom, scale, floating);
        if (right - left - 2f * rim <= 0f || bottom - gridTop - 2f * rim <= 0f) return false;
        out[0] = left + rim;
        out[1] = gridTop + rim;
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
     * to right). Centred in the band along the opening's top, a Margin in from its sides, at the
     * live pill's height. False, with {@code out} untouched, when there is no room.
     */
    static boolean pills(float left, float top, float right, float bottom, float scale,
                         float marginDp, boolean floating, @NonNull float[] out) {
        float rim = rimPx(marginDp, scale);
        float width = right - left - 2f * rim;
        float band = bandPx(top, bottom, scale, floating);
        float s = Math.max(0f, scale);
        float height = Math.min(HomePillsPolicy.PILL_MAX_HEIGHT_DP * s,
            band - 2f * HomePillsPolicy.PILL_BAND_INSET_DP * s);
        if (width <= 0f || height <= 0f) return false;
        float pillTop = top + (band - height) / 2f;
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
