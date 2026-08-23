package com.termux.app.surfaces;

/**
 * How tall the surface editor's card is allowed to be, and how much of that the slider region gets.
 *
 * <p>The card is a live editor pinned above the accessory stack: the dock, and the in-app keyboard
 * when it is up. How much room that leaves changes while the editor is open — hiding the keyboard
 * frees several hundred pixels — and the card has to spend that room rather than keep the height it
 * happened to have when it opened, which left it sitting in the bottom corner scrolling three
 * sliders at a time under a band of empty terminal. Three rules do the spending:
 *
 * <ul>
 *   <li>The card always gets a workable height — a header, the tabs, a few sliders and the action
 *       row — even when that is most of the room there is.
 *   <li>Beyond that it may grow, but keeps a slice of the surface it is editing on screen above
 *       itself. A card grown to the status bar hides the thing being tuned, which is worse than
 *       scrolling, so the growth stops short of the full space and of a fraction of the display.
 *   <li>Within that ceiling the slider region takes the height its current section needs. A card
 *       fixed at the ceiling left empty glass under the short sections.
 * </ul>
 *
 * <p>Pure arithmetic on pixels, no views, so the cases that matter — keyboard up, keyboard down, a
 * short section, a tall one, a cramped screen — are testable without inflating the editor.
 */
public final class SurfaceEditorCardMetrics {

    private SurfaceEditorCardMetrics() {}

    /** Hard ceiling on the card, as a fraction of the display, however much room there is. */
    private static final float MAX_SCREEN_FRACTION = 0.62f;
    /**
     * Height the card claims before the preview slice is considered at all. This is the share of
     * the screen the editor had when it was fixed-height, so tight layouts — the keyboard up, a
     * short screen — end up no smaller than they were.
     */
    private static final float COMFORT_SCREEN_FRACTION = 0.45f;
    /** Preview slice the card leaves above itself once there is room to spare for it. */
    private static final float PREVIEW_FRACTION = 0.14f;
    private static final int PREVIEW_MIN_DP = 88;
    /** Comfort floor in absolute terms, for displays where the fraction is not enough to edit in. */
    private static final int CARD_MIN_DP = 240;
    /** Floor for the scroll region itself, so the sliders never collapse to nothing. */
    private static final int SCROLL_MIN_DP = 96;

    /**
     * The height to give the editor's scrolling slider region.
     *
     * @param availablePx room between the launcher's own status bar and the accessory stack
     * @param chromePx    everything in the card outside the scroll region: padding, header, the
     *                    section tabs and the action row, as measured
     * @param contentPx   measured height of the section on show, or 0 while it is unmeasured
     * @param screenPx    display height, which the ceiling is a fraction of
     * @param pxPerDp     display density, for the dp minimums
     * @return the scroll height in pixels; {@code chromePx} plus this is the resulting card height
     */
    public static int scrollHeightPx(int availablePx, int chromePx, int contentPx, int screenPx,
                                     float pxPerDp) {
        int maxScroll = maxScrollPx(availablePx, chromePx, screenPx, pxPerDp);
        if (contentPx <= 0)
            return maxScroll;
        return Math.min(contentPx, maxScroll);
    }

    /** The ceiling {@link #scrollHeightPx} caps the section's own height against. */
    public static int maxScrollPx(int availablePx, int chromePx, int screenPx, float pxPerDp) {
        int room = maxCardPx(availablePx, screenPx, pxPerDp) - chromePx;
        // The floor may not push the card past what the screen actually has: overflowing here is
        // what shoves the card's own header up behind the launcher's status bar.
        int floor = Math.min(Math.round(SCROLL_MIN_DP * pxPerDp),
            Math.max(0, availablePx - chromePx));
        return Math.max(room, floor);
    }

    /** The tallest the whole card may be, preview slice already accounted for. */
    public static int maxCardPx(int availablePx, int screenPx, float pxPerDp) {
        if (availablePx <= 0)
            return 0;
        int preview = Math.max(Math.round(PREVIEW_MIN_DP * pxPerDp),
            Math.round(screenPx * PREVIEW_FRACTION));
        int comfortable = Math.min(availablePx,
            Math.max(Math.round(screenPx * COMFORT_SCREEN_FRACTION),
                Math.round(CARD_MIN_DP * pxPerDp)));
        int ceiling = Math.min(availablePx,
            Math.max(Math.round(screenPx * MAX_SCREEN_FRACTION), comfortable));
        return Math.min(Math.max(availablePx - preview, comfortable), ceiling);
    }
}
