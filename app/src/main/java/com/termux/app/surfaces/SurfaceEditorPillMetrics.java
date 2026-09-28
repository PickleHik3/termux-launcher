package com.termux.app.surfaces;

/**
 * Where the Appearance editor's card and its resting pill park, and how much of the list the card
 * shows at rest.
 *
 * <p>Both stand at the foot of the free room — the band between the launcher's status chrome and
 * the accessory stack — one standoff clear of whatever bounds it below, never on a surface. The
 * card is a sheet anchored by its bottom there: a pull grows it up toward the top of the band, so
 * its bottom never reaches the dock or the keyboard, and the terminal above a resting card is left
 * whole to be touched.
 *
 * <p>The card gives way with the room. A system IME on a cramped phone can collapse the band below
 * anything the card's whole list needs (issue #20), and the answer is a shorter card rather than a
 * clipped one or one pinned over the surfaces bounding it. What gives is the resting list
 * ({@link #bodyCapPx}), which has a floor, so a region too short for anything still leaves a strip
 * of list.
 *
 * <p>Pure arithmetic on pixels, no views, so the cases that matter — keyboard up, keyboard down, a
 * squeezed screen — are testable without inflating the editor.
 */
public final class SurfaceEditorPillMetrics {

    private SurfaceEditorPillMetrics() {}

    /**
     * Where the pill's top edge goes for a pill parked at the region's foot — the resting pill,
     * which the card reads as grown out of.
     *
     * <p>Against the region's foot rather than centred in it: the useful thing to do with the free
     * room is leave as much of it in one piece as possible. Centring cut it into two thin strips
     * with the card between them, and neither strip read as "the terminal, touch it" — which is
     * the one gesture the shared layer exists to invite.
     */
    public static int parkRegionFootTopPx(int pillHeightPx, int standoffPx, int regionTopPx,
                                          int regionBottomPx) {
        return Math.max(regionTopPx, regionBottomPx - standoffPx - pillHeightPx);
    }

    /**
     * The card's bottom margin in a host {@code hostHeightPx} tall, for a card whose bottom edge
     * stands one standoff above the region's foot. A region too short for even the standoff puts
     * the edge at the region's top rather than above it.
     */
    public static int parkBottomMarginPx(int hostHeightPx, int standoffPx, int regionTopPx,
                                         int regionBottomPx) {
        int edge = Math.max(regionTopPx, regionBottomPx - standoffPx);
        return Math.max(0, hostHeightPx - edge);
    }

    /**
     * How much of the list the resting card shows, leaving the standoff free at both ends so the
     * card never lands flush against the surfaces bounding its region.
     *
     * <p>Clamped at both ends and deliberately: the ceiling stops a short list's card from filling
     * the screen on a tablet and leaves the terminal above it, and the floor keeps a usable strip of
     * list on a phone whose region a system IME has squeezed to nothing. Below the floor the card is
     * allowed to be the taller of the two — a card that overhangs its region by a few dp is still
     * usable, and one measured to zero is not. The rest of the list is a pull or a scroll away.
     *
     * @param regionHeightPx the band between the anchors
     * @param chromeHeightPx what the card spends on everything that is not the list
     * @param standoffPx     the gap the card leaves at each end of the region
     */
    public static int bodyCapPx(int regionHeightPx, int chromeHeightPx, int standoffPx,
                                int minCapPx, int maxCapPx) {
        int available = regionHeightPx - chromeHeightPx - (2 * standoffPx);
        return Math.max(minCapPx, Math.min(maxCapPx, available));
    }
}
