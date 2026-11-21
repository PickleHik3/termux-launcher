package com.termux.app.wall.pageswitch;

/**
 * Where Home's page bar stands in its pane, from the facts that move it: whether the indicator is
 * on, whether dock rows stand below the pill, where the pane sits in the window and whether a
 * keyboard is up. Pure; {@link PageBarLayout} owns the pill's own sizes and the air numbers,
 * the views only apply these answers.
 *
 * <p>The band is what the grid gives up above the dock's reserve. With the indicator off it is
 * nothing at all. With the indicator on it is the air above the pill and the pill, and the air
 * below the pill: {@link PageBarLayout#BOTTOM_AIR_DP} while dock rows stand under it, the dock's
 * own edge gap without them, so the pill takes the dock's place with its bottom on the line the
 * dock's bottom would stand on. A keyboard hides the pill and leaves
 * the band, so the grid is not cut again on every keyboard toggle.
 */
public final class PageBarBand {

    private PageBarBand() {}

    /** Whether dock rows stand below the pill: the pane keeps a reserve for them. */
    public static boolean dockRowsBelow(int dockReservePx) {
        return dockReservePx > 0;
    }

    /**
     * The band's height in dp before the air under the pill that is not {@link
     * PageBarLayout#BOTTOM_AIR_DP}: 0 with the indicator off. With no dock rows below, the air
     * under the pill is the dock's edge gap, which is in pixels ({@link #bandHeightPx}).
     */
    public static float bandHeightDp(boolean indicatorOn, boolean dockRowsBelow) {
        if (!indicatorOn) return 0f;
        return PageBarLayout.TOP_AIR_DP + PageBarLayout.PILL_HEIGHT_DP
            + (dockRowsBelow ? PageBarLayout.BOTTOM_AIR_DP : 0f);
    }

    /**
     * The band's height in px, the part of the pane's height the grid gives up. The air under the
     * pill is {@link PageBarLayout#BOTTOM_AIR_DP} while dock rows stand below it, and the dock's
     * own edge gap ({@code edgeGapPx}, what the dock keeps above the pane's bottom) without them:
     * the pill then stands where the dock's bottom would.
     */
    public static int bandHeightPx(boolean indicatorOn, boolean dockRowsBelow, int edgeGapPx,
                                   float density) {
        if (!indicatorOn) return 0;
        return Math.round(bandHeightDp(true, dockRowsBelow) * density)
            + (dockRowsBelow ? 0 : Math.max(0, edgeGapPx));
    }

    /** The pill's top within the band. */
    public static float pillTopPx(float density) {
        return PageBarLayout.TOP_AIR_DP * density;
    }

    /** How far above the pane's bottom the band ends: the dock's reserve. */
    public static int bottomMarginPx(int dockReservePx) {
        return Math.max(0, dockReservePx);
    }

    /** Whether the pill shows: the indicator is on and no keyboard covers the dock's place. */
    public static boolean shown(boolean indicatorOn, boolean keyboardUp) {
        return indicatorOn && !keyboardUp;
    }

    /**
     * The pill's left within its pane: centred on the window's centre, kept
     * {@link PageBarLayout#SIDE_INSET_DP} inside the pane on both sides. Pane-centred when the pane
     * is centred in the window, when the window's width is unknown or when the pane is too narrow
     * to hold the pill with its insets.
     *
     * @param paneLeftPx the pane's left in the window
     */
    public static float pillLeftPx(float paneLeftPx, float paneWidthPx, float windowWidthPx,
                                   float pillWidthPx, float density) {
        float paneCentred = (paneWidthPx - pillWidthPx) / 2f;
        if (!(windowWidthPx > 0f)) return paneCentred;
        float inset = PageBarLayout.SIDE_INSET_DP * density;
        float min = inset;
        float max = paneWidthPx - inset - pillWidthPx;
        if (max < min) return paneCentred;
        float centred = windowWidthPx / 2f - paneLeftPx - pillWidthPx / 2f;
        return Math.max(min, Math.min(max, centred));
    }
}
