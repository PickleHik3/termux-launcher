package com.termux.app.editmode;

/**
 * Where edit mode's card stands, in the overlay's pixels. The strip holds the very bottom; a card
 * stands low, in thumb reach, just above the surface it edits so the change is judged on the real
 * thing, and never lower than the strip.
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
}
