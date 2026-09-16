package com.termux.app.launcher.paging;

import com.termux.app.place.PlaceLayout;

/**
 * The "minimal ticks" page indicator's geometry: how long each tick is at a given fractional page
 * position, how much air goes between them, and where each one's centre lands along the strip.
 *
 * <p>One copy for both axes. A row's ticks run across its width and a rail's run down its height,
 * and the arithmetic never learns which — it is given a length and answers in the same units, the
 * way {@link com.termux.app.launcher.az.AzLetterTrack} does for the alphabets bar.
 *
 * <p>The numbers are the dock's own, so a row moved to another edge keeps the indicator it had:
 * the tick nearest the active page widens, everything keys off proximity to the fractional
 * position so the morph is continuous across a swipe, and the gap is constant unless the ticks
 * would not otherwise fit.
 *
 * <p>Pure: no {@code View}, no {@code Canvas}, only densities, pixels and proportions.
 */
public final class PageTickStrip {

    private PageTickStrip() {}

    /** A tick at rest, along the strip. */
    public static final float INACTIVE_LENGTH_DP = 13f;
    /** The active page's tick, along the strip. */
    public static final float ACTIVE_LENGTH_DP = 24f;
    /** A tick across the strip. */
    public static final float THICKNESS_DP = 2.5f;
    /** The air between two ticks, before the fit-to-length squeeze. */
    public static final float GAP_DP = 4f;
    /** The air the strip keeps at each end, so the ticks never run into the bar's own corners. */
    public static final float END_MARGIN_DP = 18f;
    /** The band the strip claims across itself: the tick and the air either side of it. */
    public static final float BAND_DP = 9f;

    /** The band a strip claims across itself, which is what its host reserves for it. */
    public static int bandPx(float density) {
        return Math.round(Math.max(0f, density) * BAND_DP);
    }

    /**
     * Each tick's length along the strip, the one nearest {@code position} widened.
     *
     * @param position the fractional page position; clamped into {@code [0, pageCount - 1]}
     */
    public static float[] lengthsPx(int pageCount, float position, float density) {
        int count = Math.max(1, pageCount);
        float pos = Math.max(0f, Math.min(position, count - 1f));
        float inactive = INACTIVE_LENGTH_DP * density;
        float active = ACTIVE_LENGTH_DP * density;
        float[] lengths = new float[count];
        for (int page = 0; page < count; page++) {
            float proximity = Math.max(0f, 1f - Math.abs(page - pos));
            lengths[page] = inactive + ((active - inactive) * proximity);
        }
        return lengths;
    }

    /**
     * The air between two ticks: the constant gap, squeezed only when the ticks and their gaps
     * would not fit the strip's usable length.
     */
    public static float gapPx(float[] lengthsPx, float stripLengthPx, float density) {
        float gap = GAP_DP * density;
        int count = lengthsPx == null ? 0 : lengthsPx.length;
        if (count <= 1) return gap;
        float sum = 0f;
        for (float length : lengthsPx) sum += length;
        float usable = Math.max(1f, stripLengthPx - (2f * END_MARGIN_DP * density));
        if (sum + ((count - 1) * gap) <= usable) return gap;
        return Math.max(GAP_DP * density * 0.5f, (usable - sum) / (count - 1));
    }

    /** Every tick's centre along the strip, the whole run centred on the strip's middle. */
    public static float[] centersPx(float[] lengthsPx, float gapPx, float stripLengthPx) {
        int count = lengthsPx == null ? 0 : lengthsPx.length;
        float[] centers = new float[count];
        if (count == 0) return centers;
        float total = (count - 1) * Math.max(0f, gapPx);
        for (float length : lengthsPx) total += length;
        float cursor = (stripLengthPx * 0.5f) - (total * 0.5f);
        for (int page = 0; page < count; page++) {
            centers[page] = cursor + (lengthsPx[page] * 0.5f);
            cursor += lengthsPx[page] + Math.max(0f, gapPx);
        }
        return centers;
    }

    /**
     * Which side of the bar the strip stands on, as the index it takes in the bar's own host.
     *
     * <p>One rule for all four edges: the strip stands on the row's <em>centre-facing</em> side,
     * the same side the drawer, the status lens and the A-Z match band all open towards. Above the
     * row on the bottom edge, below it on the top, right of a left-hand rail and left of a
     * right-hand one — so the ticks always read as the row's inner rim rather than as something
     * wedged between the row and whatever band comes next. The strip therefore leads its host on
     * the two edges whose inner side is the lower coordinate: the bottom edge and a right-hand
     * rail.
     */
    public static boolean leadsRow(PlaceLayout.Edge edge) {
        return edge == PlaceLayout.Edge.RIGHT || edge == PlaceLayout.Edge.BOTTOM;
    }

    /** Which way the ticks run: down the strip for a rail, across it for a row. */
    public static boolean verticalOn(PlaceLayout.Edge edge) {
        return edge != null && edge.isOnSide();
    }

    /**
     * The tick at rest, as a fraction of the accent's opacity, and the one under the active page at
     * full. Everything keys off proximity to the fractional position, so the morph is continuous
     * across a swipe and two adjacent ticks share it at the midpoint.
     */
    public static final float INACTIVE_ALPHA = 0.40f;

    /** How near a tick is to the active page: 1 on it, 0 a whole page away. */
    public static float proximity(int page, float position) {
        return Math.max(0f, 1f - Math.abs(page - position));
    }

    /** A tick's opacity at that proximity: muted at rest, the accent's own at the active page. */
    public static float alphaFor(float proximity) {
        float p = Math.max(0f, Math.min(1f, proximity));
        return INACTIVE_ALPHA + ((1f - INACTIVE_ALPHA) * p);
    }

    /**
     * The warm tint the "most-used" page's tick carries: distinguishable from the accent ticks
     * beside it, harmonised rather than neon. Damped further while that page is not the active one,
     * so a sleeping page never steals attention.
     */
    public static final int DYNAMIC_TICK_COLOR = 0xFFE0A338;

    /** The extra damp a dynamic tick carries while its page is not the one being shown. */
    public static float dynamicDampFor(float proximity) {
        float p = Math.max(0f, Math.min(1f, proximity));
        return 0.55f + (0.45f * p);
    }

}
