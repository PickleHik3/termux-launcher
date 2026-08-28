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
}
