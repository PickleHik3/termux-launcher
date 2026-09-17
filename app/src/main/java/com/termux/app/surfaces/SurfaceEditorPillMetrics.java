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
 * <p>The card gives way with the room, from both ends. A system IME on a cramped phone can collapse
 * the band between the anchors below anything the card's whole list needs (issue #20), and the
 * answer is a shorter card rather than a clipped one or one pinned over the surfaces bounding it.
 * The preset strip goes first ({@link #presetCardHeightPx}) and the scrolling body second
 * ({@link #bodyCapPx}), because thumbnails are what a short card can most afford to lose and the
 * rows are the controls. Both have floors, so a region too short for anything still leaves a strip
 * of list and a strip of presets.
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

    /**
     * How tall the preset cards may stand in a region of this height.
     *
     * <p>The body was the only thing that ever gave way, and below a point it had nothing left to
     * give: {@link #bodyCapPx} sits on its floor while the header, the preset strip and the pills
     * keep every pixel they asked for, so a short region loses rows — the controls — to keep the
     * thumbnails. Both sides give way now, in the order that costs the user least: the cards shrink
     * first, down to {@code minHeightPx}, and only what that cannot find comes off the rows.
     *
     * <p>Stated as the room left rather than a height threshold, so it holds at any density and any
     * row count: what is not spent on the standoffs, on the chrome that is not the strip, and on
     * the body's own floor is what the strip may have.
     *
     * @param regionHeightPx      the band between the anchors
     * @param chromeBesideCardsPx what the card spends on everything that is neither the preset
     *                            cards nor the body
     * @param bodyFloorPx         the body's floor — the strip gives way before this does
     * @param standoffPx          the gap the card leaves at each end of the region
     * @param fullHeightPx        the cards' full height, which a region with room for it gets
     * @param minHeightPx         the shortest card still worth drawing
     */
    public static int presetCardHeightPx(int regionHeightPx, int chromeBesideCardsPx,
                                         int bodyFloorPx, int standoffPx,
                                         int fullHeightPx, int minHeightPx) {
        int floor = Math.min(minHeightPx, fullHeightPx);
        int room = regionHeightPx - chromeBesideCardsPx - bodyFloorPx - (2 * standoffPx);
        return Math.max(floor, Math.min(fullHeightPx, room));
    }
}
