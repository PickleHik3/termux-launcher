package com.termux.app.launcher.drawer;

/**
 * Where every drawer view type ends its content above the glass card's usable bottom.
 *
 * <p>The usable bottom is the card's visible bottom edge when a keyboard is up, and the top of the
 * navigation or gesture inset when it is down (the card runs on under the gesture bar). The last
 * row's label, the last overview card and the page dots end one content inset above it. The
 * inset itself is the drawer's own side padding, owned by {@link AppDrawerContentView}; it comes in
 * as a number so there is one copy of it.
 *
 * <p>All of it is derived at the keyboard decision and never per frame.
 */
public final class AppDrawerContentInsetPolicy {

    private AppDrawerContentInsetPolicy() {}

    /**
     * How far the content's own bottom padding has to reach so that its last element ends one
     * content inset above the usable bottom.
     *
     * @param contentBottomPx the bottom edge of the content view, in the card's coordinates
     * @param cardBottomPx    the card's visible bottom (screen bottom keyboard down, the shortened
     *                        plane's bottom keyboard up)
     * @param navInsetPx      the navigation or gesture inset the card runs under; ignored while a
     *                        keyboard is up
     * @param contentInsetPx  the drawer's side padding
     */
    public static float contentBottomInsetPx(float contentBottomPx, float cardBottomPx,
                                             float navInsetPx, boolean keyboardUp,
                                             float contentInsetPx) {
        float usableBottom = keyboardUp ? cardBottomPx : cardBottomPx - Math.max(0f, navInsetPx);
        return Math.max(0f, contentBottomPx - usableBottom) + Math.max(0f, contentInsetPx);
    }

    /**
     * The padding a scrolling list needs when its last row already carries {@code trailingPx} of
     * empty space under its label (or card), so the visible ink lands on the inset exactly.
     */
    public static int listBottomPaddingPx(float bottomInsetPx, float trailingPx) {
        return Math.max(0, Math.round(bottomInsetPx - Math.max(0f, trailingPx)));
    }

    /** The strip under the pages: the dots, a row gap above them, and the inset below them. */
    public static float pagerBottomBandPx(float bottomInsetPx, float dotsHeightPx,
                                          float rowGapPx) {
        return Math.max(0f, bottomInsetPx) + Math.max(0f, dotsHeightPx) + Math.max(0f, rowGapPx);
    }

    /** What the page rows share: the content height less the top gap and the dots strip. */
    public static float pagerUsableHeightPx(float contentHeightPx, float topMarginPx,
                                            float bottomBandPx) {
        return Math.max(0f, contentHeightPx - topMarginPx - bottomBandPx);
    }
}
