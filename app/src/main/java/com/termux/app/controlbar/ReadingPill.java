package com.termux.app.controlbar;

/**
 * The reading slot's pill fits its text (round 5): every form shows the reading whole, the
 * number with its unit ("100%"), on one line. Along a row the pill grows past its design length
 * when the text needs more; down the rail its width is the rail's, so the text is made smaller
 * until it fits. Pure arithmetic over measured lengths in pixels.
 */
public final class ReadingPill {

    private ReadingPill() {}

    /** The room left at each end of the pill's content, in dp. */
    public static final float END_PAD_DP = 8f;

    /**
     * The pill's length along a row: its design length, or the icon, the gap and the text with
     * {@code padPx} at each end when that is longer.
     */
    public static float rowLengthPx(float designPx, float iconPx, float gapPx, float textPx, float padPx) {
        float needed = 2f * padPx + iconPx + (textPx > 0f ? gapPx + textPx : 0f);
        return Math.max(designPx, needed);
    }

    /**
     * The scale the text is drawn at so {@code textPx} (measured at scale 1) fits in
     * {@code roomPx}: 1 when it fits, smaller when it does not, never below {@code minScale}.
     */
    public static float fitScale(float textPx, float roomPx, float minScale) {
        if (textPx <= 0f || textPx <= roomPx) return 1f;
        return Math.max(minScale, Math.max(0f, roomPx) / textPx);
    }

    /**
     * Whether the reading whole ({@code fullPx}, measured at scale 1) fits {@code roomPx} at or
     * above {@code minScale}: down a narrow rail at a large font it does not, and the pill shows
     * the number alone ({@code ReadingValue#compact}) rather than clip it (round 5 review P3-6).
     */
    public static boolean wholeFits(float fullPx, float roomPx, float minScale) {
        return fullPx * minScale <= roomPx;
    }
}
