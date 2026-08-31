package com.termux.app.surfaces;

/**
 * Where the surface editor's pill parks, and how many rows it can afford there.
 *
 * <p>The pill carries one property at a time and sits next to the surface it edits, never on top of
 * it: the standoff from its surface is constant, so the pill rides the dock's lift and the
 * keyboard's reveal instead of being placed against whatever else is on screen. Everything else
 * falls out of clamping that one offset inside the terminal region — a keyboard whose only free
 * neighbour is occupied by the dock pushes the pill above the dock without anybody deciding that
 * case, and a surface with no room on its own side ends up on the other one.
 *
 * <p>The shape degrades with the room. A system IME on a cramped phone can collapse the band
 * between the anchors below anything the full pill needs (issue #20), and the answer is fewer rows
 * rather than a clipped or empty editor: the chip row and the footnote go, then the chip row's
 * replacement dropdown moves inline, but the slider never does. One row is always affordable
 * because the floor here is unconditional — {@link Mode#ONE_ROW} is what a region too short for
 * even that still gets.
 *
 * <p>Pure arithmetic on pixels, no views, so the cases that matter — keyboard up, keyboard down, a
 * top-anchored surface, a squeezed screen — are testable without inflating the editor.
 */
public final class SurfaceEditorPillMetrics {

    private SurfaceEditorPillMetrics() {}

    /** How much of itself the pill can draw in the room it has. */
    public enum Mode {
        /** Title, chips, the open property's slider and the inheritance footnote. */
        FULL,
        /** The same without the footnote, at tighter padding — the common keyboard-up case. */
        COMPACT,
        /** Surface, property dropdown, slider, value and Done on one row. */
        ONE_ROW
    }

    /**
     * Where the pill's top edge goes, in the same coordinate space as every argument.
     *
     * @param anchorTopPx    the selected surface's top edge
     * @param anchorBottomPx the selected surface's bottom edge
     * @param topAnchored    true for a surface fixed to the top of the screen, which the pill
     *                       therefore sits below; false for one the pill sits above
     * @param pillHeightPx   the pill's measured height
     * @param standoffPx     the constant gap between the pill and its surface
     * @param regionTopPx    the top of the band the pill must stay inside
     * @param regionBottomPx the bottom of that band
     * @return the pill's top edge, clamped into the region; a region shorter than the pill pins the
     *         pill to its top rather than pushing the pill's own header out of view
     */
    public static int parkTopPx(int anchorTopPx, int anchorBottomPx, boolean topAnchored,
                                int pillHeightPx, int standoffPx,
                                int regionTopPx, int regionBottomPx) {
        int desired = topAnchored
            ? anchorBottomPx + standoffPx
            : anchorTopPx - standoffPx - pillHeightPx;
        int deepest = regionBottomPx - pillHeightPx;
        if (deepest <= regionTopPx)
            return regionTopPx;
        return Math.max(regionTopPx, Math.min(desired, deepest));
    }

    /**
     * Where the pill's top edge goes for a surface that <em>is</em> the region — the canvas.
     *
     * <p>The other three are bands with a free side to sit beside. The canvas is the free side, so
     * there is no edge to stand off from and the honest answer is the middle of it: parking the
     * canvas's pill against the bottom of its own region would read as belonging to the dock.
     */
    public static int parkCenteredTopPx(int pillHeightPx, int regionTopPx, int regionBottomPx) {
        int centered = regionTopPx + (((regionBottomPx - regionTopPx) - pillHeightPx) / 2);
        return Math.max(regionTopPx, centered);
    }

    /**
     * The tallest shape that fits, leaving the standoff free at both ends so the pill never lands
     * flush against the surfaces bounding its region.
     *
     * @param regionHeightPx  the band between the anchors
     * @param fullHeightPx    what {@link Mode#FULL} measures
     * @param compactHeightPx what {@link Mode#COMPACT} measures
     * @return the mode to draw; {@link Mode#ONE_ROW} when neither of the others fits, including
     *         when the region cannot hold one row either
     */
    public static Mode modeFor(int regionHeightPx, int fullHeightPx, int compactHeightPx) {
        if (regionHeightPx >= fullHeightPx)
            return Mode.FULL;
        if (regionHeightPx >= compactHeightPx)
            return Mode.COMPACT;
        return Mode.ONE_ROW;
    }
}
