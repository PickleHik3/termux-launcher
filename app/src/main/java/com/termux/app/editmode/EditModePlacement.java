package com.termux.app.editmode;

/**
 * Where edit mode's card stands, in the overlay's pixels. The strip holds the very bottom (it never
 * moves for a card, round 3); a card stands low, in thumb reach, just above the surface it edits so
 * the change is judged on the real thing, and never lower than the strip.
 */
public final class EditModePlacement {

    /** A surface that is not on screen. */
    public static final int OFF_SCREEN = Integer.MIN_VALUE;

    private EditModePlacement() { }

    /**
     * The card's distance from the overlay's bottom edge: above the surface's top when the surface
     * is on screen, else above the strip; {@code gapPx} clear of either.
     *
     * @param surfaceTopPx the edited surface's top in the overlay, or {@link #OFF_SCREEN}
     */
    public static int cardBottomMarginPx(int overlayHeightPx, int surfaceTopPx, int stripHeightPx,
                                         int gapPx) {
        int clear = stripHeightPx;
        if (surfaceTopPx != OFF_SCREEN && surfaceTopPx < overlayHeightPx) {
            clear = Math.max(clear, overlayHeightPx - Math.max(0, surfaceTopPx));
        }
        return clear + gapPx;
    }

    /**
     * How far edit mode lifts the control bar so it stands {@code gapPx} above the strip (as the
     * prototype: the strip keeps the very bottom, the bar stays in sight and in reach above it):
     * 0 when the bar's bottom ({@code barBottomPx}, where it is laid out) is above that already,
     * as with the keyboard up. Overlay pixels from the top.
     */
    public static int barLiftPx(int barBottomPx, int stripTopPx, int gapPx) {
        return Math.max(0, barBottomPx - (stripTopPx - Math.max(0, gapPx)));
    }

    /**
     * The higher of two surfaces' tops (the smaller number), either {@link #OFF_SCREEN}: what a
     * card must stand above when the keyboard and the bar are both in sight.
     */
    public static int highest(int aTopPx, int bTopPx) {
        if (aTopPx == OFF_SCREEN) return bTopPx;
        if (bTopPx == OFF_SCREEN) return aTopPx;
        return Math.min(aTopPx, bTopPx);
    }
}
