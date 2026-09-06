package com.termux.app.x11;

/**
 * Sizes for the extra keys when the Display place stands them in a column on a screen edge. Pure,
 * so the activity only applies the answer: how much of the edge the column claims, and how tall
 * each key gets so the whole column fits between the system bars.
 */
public final class DisplayExtraKeysColumnGeometry {

    /** A key's preferred height in the column; keys shrink below it only when they would not fit. */
    public static final float KEY_HEIGHT_DP = 52f;

    private DisplayExtraKeysColumnGeometry() {
    }

    /**
     * How far in from the screen edge the column reaches: the inset it starts past (the cutout, or
     * the rail when they share the edge), a margin either side, and the keys themselves. Content on
     * that side is inset by this much.
     */
    public static int footprintPx(int edgeInsetPx, int marginPx, int keysWidthPx) {
        return Math.max(0, edgeInsetPx) + 2 * Math.max(0, marginPx) + Math.max(0, keysWidthPx);
    }

    /**
     * The height of one key so that {@code keyCount} of them fit in {@code availablePx}, never
     * taller than {@code preferredPx}. Before first layout the available height is unknown (zero)
     * and the preferred height stands; no keys means no height.
     */
    public static int keyHeightPx(int availablePx, int keyCount, int preferredPx) {
        if (keyCount <= 0 || preferredPx <= 0) return 0;
        if (availablePx <= 0) return preferredPx;
        return Math.max(1, Math.min(preferredPx, availablePx / keyCount));
    }
}
