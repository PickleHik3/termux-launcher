package com.termux.app.launcher.paging;

import com.termux.app.place.PlaceLayout;

/**
 * The dock's page capsules' geometry (the page bar's own look, under its pill): how long each capsule is at a given fractional page
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

    /** A capsule at rest, along the strip. */
    public static final float INACTIVE_LENGTH_DP = 10f;
    /** The active page's capsule, along the strip. */
    public static final float ACTIVE_LENGTH_DP = 28f;
    /** A capsule across the strip. */
    public static final float THICKNESS_DP = 5f;
    /** A capsule's corner radius: a full cap, so it is derived from the thickness. */
    public static final float CORNER_RADIUS_DP = THICKNESS_DP / 2f;
    /** The air between two capsules, before the fit-to-length squeeze. */
    public static final float GAP_DP = 6f;
    /** The air the strip keeps at each end, so the ticks never run into the bar's own corners. */
    public static final float END_MARGIN_DP = 18f;
    /** The band the strip claims across itself: the tick and the air either side of it. */
    public static final float BAND_DP = 9f;

    /**
     * The air between the capsules and the dock's own surface on the side they face: the slab's
     * top edge, or the icons' tops where no edge stands there. The one number the dock reads
     * ({@code DockLayoutPolicy}) for how far below the strip its slab starts.
     */
    public static final float SURFACE_AIR_DP = 6f;

    /** The band a strip claims across itself, which is what its host reserves for it. */
    public static int bandPx(float density) {
        return Math.round(Math.max(0f, density) * BAND_DP);
    }

    /**
     * The air between the ticks and either edge of a band {@code bandPx} across: the strip draws
     * its ticks on the band's middle line. For a strip standing just above a slab of glass (a dock
     * whose dashes float over it) this is the gap between the dashes and the glass's top edge.
     */
    public static float edgeGapPx(int bandPx, float density) {
        return Math.max(0f, (bandPx - THICKNESS_DP * Math.max(0f, density)) * 0.5f);
    }

    /**
     * What a slab standing under a strip {@code bandPx} across needs added below the band so the
     * capsules keep {@link #SURFACE_AIR_DP} from its top edge: the air asked for, less the
     * band's own air under the capsules ({@link #edgeGapPx}).
     */
    public static int surfaceExtraPx(int bandPx, float density) {
        float want = SURFACE_AIR_DP * Math.max(0f, density);
        return Math.round(Math.max(0f, want - edgeGapPx(bandPx, density)));
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
     * Which side of the bar the strip stands on, as the index it takes in the bar's own host:
     * {@code true} when the ticks lead the row along the host's axis (the lower coordinate),
     * {@code false} when they follow it.
     *
     * <p>The ticks stand on the row's <em>outer</em> side — the side of it the screen edge its
     * stack stands on is. Below the row on the bottom edge, above it on the top, outboard on a
     * side rail. There the strip reads as the bar's own rim against the screen rather than as a
     * divider wedged between the row and the band beyond it, which is what it became the moment
     * the arrangement could stand another band there.
     *
     * <p>One arrangement is the exception: the row standing immediately next to the canvas, with
     * nothing between it and the terminal, the widgets or the display pane. Its outer side is
     * then the one with a neighbour on it and its canvas side is the free one, so the ticks take
     * the canvas side — above the icons on the bottom edge, under them on the top, inboard on a
     * rail — which is where they have always stood on the shipped arrangement.
     *
     * @param nextToCanvas the row is the innermost band of its edge's stack
     */
    public static boolean ticksLeadRow(PlaceLayout.Edge edge, boolean nextToCanvas) {
        // The two edges whose canvas-facing side is the lower coordinate, which is the side a
        // leading band takes: the bottom edge and a right-hand rail.
        boolean canvasSideLeads =
            edge == PlaceLayout.Edge.RIGHT || edge == PlaceLayout.Edge.BOTTOM;
        return nextToCanvas == canvasSideLeads;
    }

    /** Which way the ticks run: down the strip for a rail, across it for a row. */
    /**
     * Whether the dock's capsules stand on the bare wallpaper rather than on the dock's slab:
     * above the slab, for a style whose dashes float over it ({@code DockLayout.slabTopInsetPx}
     * above 0, Peek glass's card included, whose top runs through the icons' centres), or on a
     * dock that draws no slab (Lens beads drawn). Their ink is then the bare-text ink measured on
     * the wallpaper behind them; anywhere else, the dock surface's. Off the dock it stays the
     * surface the host hands down.
     */
    public static boolean standsOnWallpaper(boolean onDock, boolean dashesAboveSlab,
                                            boolean beadsDrawn) {
        return onDock && (dashesAboveSlab || beadsDrawn);
    }

    public static boolean verticalOn(PlaceLayout.Edge edge) {
        return edge != null && edge.isOnSide();
    }

    /**
     * A capsule at rest, as a fraction of the ink's opacity, and the one under the active page at
     * full. Everything keys off proximity to the fractional position, so the morph is continuous
     * across a swipe and two adjacent capsules share it at the midpoint.
     *
     * <p>How muted a resting capsule <em>looks</em>, not how faint it is allowed to be: once the
     * glass under the strip has been measured this is the opacity {@code PageTickStripView} asks
     * {@code GlassInk} for, and it is raised where half of it cannot clear the decoration floor.</p>
     */
    public static final float INACTIVE_ALPHA = 0.50f;

    /** While the page bar slides, the current page's capsule falls to this share of its alpha. */
    public static final float DIMMED_ACTIVE_FACTOR = 0.45f;
    /** While the page bar slides, the others fall to this share of their own (0.25 absolute). */
    public static final float DIMMED_INACTIVE_FACTOR = 0.50f;

    /** How near a capsule is to the active page: 1 on it, 0 a whole page away. */
    public static float proximity(int page, float position) {
        return Math.max(0f, 1f - Math.abs(page - position));
    }

    /** A capsule's opacity at that proximity: muted at rest, full at the active page. */
    public static float alphaFor(float proximity) {
        float p = Math.max(0f, Math.min(1f, proximity));
        return INACTIVE_ALPHA + ((1f - INACTIVE_ALPHA) * p);
    }

    /**
     * The share of its alpha a capsule keeps while the page bar slides: the active one 0.45, the
     * others 0.5 (0.25 of full), blended by proximity so a swipe's midpoint stays continuous.
     */
    public static float dimFactor(float proximity) {
        float p = Math.max(0f, Math.min(1f, proximity));
        return DIMMED_INACTIVE_FACTOR + ((DIMMED_ACTIVE_FACTOR - DIMMED_INACTIVE_FACTOR) * p);
    }

}
