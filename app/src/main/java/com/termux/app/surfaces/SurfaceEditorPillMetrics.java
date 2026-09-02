package com.termux.app.surfaces;

/**
 * Where the surface editor's pill parks, and how many rows it can afford there.
 *
 * <p>The card sits next to the surface it edits, never on top of it: the standoff from its surface is constant, so the pill rides the dock's lift and the
 * keyboard's reveal instead of being placed against whatever else is on screen. Everything else
 * falls out of clamping that one offset inside the terminal region — a keyboard whose only free
 * neighbour is occupied by the dock pushes the pill above the dock without anybody deciding that
 * case, and a surface with no room on its own side ends up on the other one.
 *
 * <p>The body gives way with the room. A system IME on a cramped phone can collapse the band
 * between the anchors below anything the card's whole list needs (issue #20), and the answer is a
 * shorter scrolling body rather than a clipped card or a card pinned over the surfaces bounding it:
 * {@link #bodyCapPx} is that height, and it has a floor, so a region too short for anything still
 * leaves a usable strip of list.
 *
 * <p>Pure arithmetic on pixels, no views, so the cases that matter — keyboard up, keyboard down, a
 * top-anchored surface, a squeezed screen — are testable without inflating the editor.
 */
public final class SurfaceEditorPillMetrics {

    private SurfaceEditorPillMetrics() {}

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
     * Where the pill's top edge goes for a target that <em>is</em> the region — the shared layer,
     * and the canvas.
     *
     * <p>Against the region's foot rather than centred in it. The other three targets are bands
     * with a free side to sit beside; these two have the whole terminal, and the useful thing to do
     * with it is leave as much of it in one piece as possible. Centring cut the free room into two
     * thin strips with the card between them, and neither strip read as "the terminal, touch it" —
     * which is the one gesture the shared layer exists to invite.
     */
    public static int parkRegionFootTopPx(int pillHeightPx, int standoffPx, int regionTopPx,
                                          int regionBottomPx) {
        return Math.max(regionTopPx, regionBottomPx - standoffPx - pillHeightPx);
    }

    /**
     * How tall the card's scrolling body may grow, leaving the standoff free at both ends so the
     * card never lands flush against the surfaces bounding its region.
     *
     * <p>Clamped at both ends and deliberately: the ceiling stops a short list's card from filling
     * the screen on a tablet, and the floor keeps a usable strip of list on a phone whose region a
     * system IME has squeezed to nothing. Below the floor the card is allowed to be the taller of
     * the two — a card that overhangs its region by a few dp is still usable, and one measured to
     * zero is not.
     *
     * @param regionHeightPx the band between the anchors
     * @param chromeHeightPx what the card spends on everything that is not the body
     * @param standoffPx     the gap the card leaves at each end of the region
     */
    public static int bodyCapPx(int regionHeightPx, int chromeHeightPx, int standoffPx,
                                int minCapPx, int maxCapPx) {
        int available = regionHeightPx - chromeHeightPx - (2 * standoffPx);
        return Math.max(minCapPx, Math.min(maxCapPx, available));
    }
}
