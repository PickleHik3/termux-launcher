package com.termux.app.launcher.az;

/**
 * Sizes for the alphabets bar when it stands somewhere other than the dock: how thick its host is,
 * how far in from a side it reaches, and how far down a shared column it starts.
 *
 * <p>The bar keeps the same letter band it has on the dock and the same chin beside it, so a bar
 * on any edge is the same thing to hit as the one along the bottom. On a side edge the host is a
 * column like the apps rail and the extra keys column, and it is the innermost of them: its own
 * edge inset is whatever already holds that side, so three or four columns can share one edge with
 * none of them drawn over.
 *
 * <p>Pure: no views, no resources, only densities and pixels.
 */
public final class AzBarHostGeometry {

    private AzBarHostGeometry() {}

    /**
     * The letters' band — the glyphs and the air the row draws them in. The same 19dp the dock's
     * own row uses, so the letters are the same size wherever the bar stands.
     */
    public static final float LETTER_BAND_DP = 19f;

    /**
     * Dead space beside the letters, on the side the bar stands on. The dock's row carries this
     * for the same reason: a 19dp strip is not a thing a thumb can find.
     */
    public static final float CHIN_DP = 10f;

    public static int letterBandPx(float density) {
        return Math.round(Math.max(0f, density) * LETTER_BAND_DP);
    }

    public static int chinPx(float density) {
        return Math.round(Math.max(0f, density) * CHIN_DP);
    }

    /** The bar's thickness across itself: the letter band and the chin beside it. */
    public static int thicknessPx(float density) {
        return letterBandPx(density) + chinPx(density);
    }

    /**
     * How far in from its side a column host reaches — what the content there is inset by: the
     * inset it starts past, a margin either side, and the bar itself.
     */
    public static int footprintPx(int edgeInsetPx, int marginPx, int thicknessPx) {
        return Math.max(0, edgeInsetPx) + 2 * Math.max(0, marginPx) + Math.max(0, thicknessPx);
    }

    /** A top host's height: the bar plus a margin above and below it. */
    public static int rowHeightPx(int marginPx, int thicknessPx) {
        return 2 * Math.max(0, marginPx) + Math.max(0, thicknessPx);
    }

    /**
     * How far down a column host the bar starts. The bar stands <em>beside</em> whatever else
     * holds its edge rather than under it — {@link #edgeInsetPx} already carries it past those
     * columns — so the only thing above it is chrome that crosses the whole container: a status
     * bar standing along the top. Nothing about a column on the same side belongs here.
     */
    public static int columnTopPaddingPx(int marginPx, int topChromeHeightPx) {
        return Math.max(0, marginPx) + Math.max(0, topChromeHeightPx);
    }

    /** The same at the other end: the margin, and the dock and whatever stands under it. */
    public static int columnBottomPaddingPx(int marginPx, int bottomChromeHeightPx) {
        return Math.max(0, marginPx) + Math.max(0, bottomChromeHeightPx);
    }

    /**
     * How far the bar's glass and letters run down a column of this height — what the container
     * leaves between the two paddings. Zero only when the chrome really does fill the container.
     */
    public static int columnLengthPx(int containerHeightPx, int marginPx, int topChromeHeightPx,
                                     int bottomChromeHeightPx) {
        return Math.max(0, Math.max(0, containerHeightPx)
            - columnTopPaddingPx(marginPx, topChromeHeightPx)
            - columnBottomPaddingPx(marginPx, bottomChromeHeightPx));
    }

    /**
     * Where a column host starts, in from its side: past the display cutout, and past the rail,
     * the extra keys column and the status bar's own column wherever they hold the same side. Each
     * of those is given as the footprint it already claims, so the bar lands just inside the last
     * of them.
     */
    public static int edgeInsetPx(int cutoutPx, int railFootprintPx, int extraKeysFootprintPx,
                                  int statusColumnFootprintPx) {
        int inset = Math.max(0, cutoutPx);
        inset = Math.max(inset, railFootprintPx);
        inset = Math.max(inset, extraKeysFootprintPx);
        inset = Math.max(inset, statusColumnFootprintPx);
        return Math.max(0, inset);
    }
}
