package com.termux.app.launcher.az;

/**
 * Sizes for the alphabets bar when it stands somewhere other than the dock: how thick its host is,
 * how far in from a side it reaches, and how far down a shared column it starts.
 *
 * <p>The bar keeps the same letter band it has on the dock and the same chin beside it, so a bar
 * on any edge is the same thing to hit as the one along the bottom. On a side edge the host is a
 * column like the apps rail and the extra keys column, and it is one band on the same edge stack
 * as them, so three or four columns share one edge with none of them drawn over
 * ({@link com.termux.app.place.EdgeStackPolicy#contentInsets} adds the bands up).
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
     * A column host's length: the whole band the stack hands it. Nothing is taken off either end
     * any more — the edge stack it stands in carries the terminal frame's own inset, so the bar
     * begins and ends level with the frame beside it, and the launcher's other chrome is beside
     * the column rather than above or below it.
     *
     * <p>It used to subtract a top status bar's height and the dock's, which was true while the
     * side stacks stood outside the content column. Since they flank the canvas alone, both of
     * those are outside the band already and subtracting them cost the bar two thirds of its
     * length: the letters bunched into the top of the column under a stub of a capsule.
     */
    public static int columnLengthPx(int containerHeightPx) {
        return Math.max(0, containerHeightPx);
    }
}
