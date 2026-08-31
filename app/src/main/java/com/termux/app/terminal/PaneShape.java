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
 * <p>Pure arithmetic, so the cap can be asserted without a view tree or a live split.
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
     * The clearance a rounded corner of this radius owes whatever is drawn inside it.
     *
     * <p>Content held this far off every edge has its own four corners land exactly on the arc, so
     * nothing behind the clip is cut — and no more than that is spent, which matters when the
     * clearance is paid for in terminal columns and rows. Rounded up, so it is never a sub-pixel
     * short of the arc it has to clear.
     *
     * @return 0 for a square corner, which owes its content nothing.
     */
    public static int contentInsetPx(float radiusPx) {
        if (radiusPx <= 0f)
            return 0;
        return (int) Math.ceil(radiusPx * ARC_DEPTH_SHARE);
    }

    /** The clearance a pane of this size owes, at the radius it can actually wear. */
    public static int contentInsetForBounds(float requestedRadiusPx, int widthPx, int heightPx) {
        return contentInsetPx(radiusForBounds(requestedRadiusPx, widthPx, heightPx));
    }
}
