package com.termux.app.place;

/**
 * The widths of the side bands under a joined Docked frame. Pure, so the activity only applies the
 * answer and the shape model is fed the same number the host is laid out with.
 *
 * <p>The frame reads as one sheet of glass, so a side column keeps almost no air round its
 * content, and both sides stand the same width: a narrower column centres in the band instead of
 * leaving the two sides lopsided.
 */
public final class DockedFrameBands {

    /** The air either side of a side column's content inside a joined frame; a token, not a margin. */
    public static final float FRAME_SIDE_AIR_DP = 2f;

    private DockedFrameBands() {
    }

    /** A side column's own thin band: its content and the frame air either side of it. */
    public static int thinBandPx(int contentPx, int airPx) {
        return 2 * Math.max(0, airPx) + Math.max(0, contentPx);
    }

    /**
     * The one width both side bands take. A pinned-apps rail on a side has a width the user set, so
     * its band is the shared one; without it the wider of the two thin bands is. A side with no
     * column passes 0 and does not count.
     */
    public static int sharedBandPx(int appsRailBandPx, int leftThinBandPx, int rightThinBandPx) {
        if (appsRailBandPx > 0) return appsRailBandPx;
        return Math.max(0, Math.max(leftThinBandPx, rightThinBandPx));
    }

    /** The padding each side of content {@code contentPx} wide that centres it in {@code bandPx}. */
    public static int centringPadPx(int bandPx, int contentPx) {
        return Math.max(0, bandPx - Math.max(0, contentPx)) / 2;
    }
}
