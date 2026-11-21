package com.termux.app.terminal;

/**
 * The shape a pane may actually wear, given how big it is.
 *
 * <p>A pane's corner radius is one number for the whole window — the surface editor's radius, or
 * the 10dp glass default — and it is chosen against a full-height pane. Split a window four or five
 * times and a pane is a few text rows tall, at which point that same radius is half the pane: the
 * outline clip turns the slab into a lozenge, the lit rim's four arcs meet in the middle with no
 * straight edge between them, and the cells behind the clip lose their corners. Capping the radius
 * at a third of the shorter side keeps at least a third of every edge straight, which is what makes
 * a two-row pane read as a small rectangle rather than as a pill.
 *
 * <p>It also answers how far in from the pane's edges the content must sit so no glyph is cut by
 * the corner arc ({@link #verticalInsetPx}, {@link #sideInsetPx}).
 *
 * <p>Pure arithmetic, so the cap and the clearance can be asserted without a view tree or a live
 * split.
 */
public final class PaneShape {

    private PaneShape() {}

    /** Fraction of the shorter side a corner arc may claim. */
    private static final float MAX_RADIUS_SHARE = 1f / 3f;

    /**
     * How far in from a square corner a corner arc of radius r reaches, as a share of r.
     *
     * <p>The arc's furthest point from the corner it replaces lies on the 45° diagonal, at
     * r·(1 − 1/√2) ≈ 0.293r along both axes.
     */
    private static final float ARC_DEPTH_SHARE = 1f - (float) (1d / Math.sqrt(2d));

    /**
     * The radius to draw a pane of this size with.
     *
     * @return {@code requestedPx} for any pane big enough to wear it, the capped radius for a pane
     *     that is not, and 0 for a pane with no size yet (nothing to round).
     */
    public static float radiusForBounds(float requestedPx, int widthPx, int heightPx) {
        if (requestedPx <= 0f || widthPx <= 0 || heightPx <= 0)
            return 0f;
        float cap = Math.min(widthPx, heightPx) * MAX_RADIUS_SHARE;
        return Math.min(requestedPx, cap);
    }

    /**
     * The even clearance a rounded corner of this radius owes whatever is drawn inside it.
     *
     * <p>Content held this far off every edge has its own four corners land exactly on the arc's
     * 45° point, so nothing behind the clip is cut. A pane does not hold its terminal there: it
     * trades vertical clearance for side clearance ({@link #verticalInsetPx},
     * {@link #sideInsetPx}), since rows are scarcer than columns; this is where that trade starts.
     * Rounded up, so it is never a sub-pixel short of the arc it has to clear.
     *
     * @return 0 for a square corner, which owes its content nothing.
     */
    public static int contentInsetPx(float radiusPx) {
        if (radiusPx <= 0f)
            return 0;
        return (int) Math.ceil(radiusPx * ARC_DEPTH_SHARE);
    }

    /**
     * How far off the top and bottom a pane holds its content: the arc's even clearance
     * ({@link #contentInsetPx}) trimmed toward {@code floorPx}, the rim's own floor, for as long as
     * every pixel given back costs the sides ({@link #sideInsetPx}) no more than one pixel.
     *
     * <p>Rows are what a terminal is short of, so the vertical inset is the one worth trimming —
     * but past the arc's 45° point the arc steepens and each row's worth of height starts costing
     * more width than it gives. A terminal cell is about twice as tall as it is wide, so a trade
     * of one pixel for one pixel spends at most two columns per row gained; the walk stops at the
     * first pixel that would cost more. A square corner trims straight to the floor.
     *
     * @return never below {@code floorPx}, and never above the even clearance unless the floor is
     */
    public static int verticalInsetPx(float radiusPx, int floorPx) {
        int even = contentInsetPx(radiusPx);
        int floor = Math.max(0, floorPx);
        if (floor >= even)
            return floor;
        int inset = even;
        for (int v = even - 1; v >= floor; v--) {
            if (sideInsetPx(radiusPx, v) - even > even - v)
                break;
            inset = v;
        }
        return inset;
    }

    /**
     * The least side clearance that keeps a content box held {@code verticalInsetPx} off the top
     * and bottom out from under a corner arc of this radius: its corner at (h, v) from the square
     * corner lands on or inside the arc where (r − h)² + (r − v)² ≤ r². Rounded up, so it is never
     * a sub-pixel short.
     *
     * @return 0 for a square corner, and for a vertical inset that already clears the whole arc
     */
    public static int sideInsetPx(float radiusPx, int verticalInsetPx) {
        if (radiusPx <= 0f)
            return 0;
        double r = radiusPx;
        double rise = r - Math.max(0, verticalInsetPx);
        if (rise <= 0d)
            return 0;
        return (int) Math.ceil(r - Math.sqrt(r * r - rise * rise) - SUB_PIXEL_SLACK);
    }

    /** Float noise allowed below a whole pixel before rounding up claims another. */
    private static final double SUB_PIXEL_SLACK = 1e-6;

    /** The clearance a pane of this size owes, at the radius it can actually wear. */
    public static int contentInsetForBounds(float requestedRadiusPx, int widthPx, int heightPx) {
        return contentInsetPx(radiusForBounds(requestedRadiusPx, widthPx, heightPx));
    }

    /**
     * The top margin, when the content along the top already starts {@code headroomPx} in from the
     * content box's own edge, so that the first row sits {@code bottomInsetPx} off the top as the
     * last row sits off the bottom.
     *
     * <p>A terminal's first row of cells does not start at its view's top: the renderer sets it
     * {@code mFontLineSpacingAndAscent} down. That headroom is clearance the top edge already has,
     * so the margin only makes up the rest of the inset — the first cell's corner then lands
     * {@code bottomInsetPx} down, level with where the last row's corner lands against the bottom
     * arc, and the side inset that clears one clears the other. Never below 0: headroom past the
     * inset is simply not clearance this edge has to add (the first cell then sits lower, which
     * clears the arc by more).
     *
     * @return the edge's own margin, before the headroom
     */
    public static int topInsetPx(int bottomInsetPx, int headroomPx) {
        return Math.max(0, bottomInsetPx - Math.max(0, headroomPx));
    }
}
