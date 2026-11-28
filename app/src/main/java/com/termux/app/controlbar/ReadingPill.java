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

    /** The backing's opacity under the reading, of 255. */
    public static final int BACKING_ALPHA = 0x73;

    /** The backing under pale ink: the near-black the glass halos stroke with (GlassInk's HALO_DARK). */
    public static final int BACKING_UNDER_PALE = 0x1A1F2A;

    /** The backing under dark ink: its pale mirror (GlassInk's HALO_PALE). */
    public static final int BACKING_UNDER_DARK = 0xF1F4FA;

    /**
     * The pill's own backing under a reading in {@code ink} (ARGB): the side away from the ink, a
     * veil that keeps the reading legible where the wallpaper under its end of the bar is unlike
     * the face the bar's ink was chosen on (round 6, emulator 50: the bar's pale ink of the dark
     * theme, chosen on the face of the whole bar, was faint on the bright end the pill stands on,
     * and the pill's own tint, the ink itself, made it fainter still).
     */
    public static int backingColor(int ink) {
        int rgb = luminance(ink) >= 0.18d ? BACKING_UNDER_PALE : BACKING_UNDER_DARK;
        return (BACKING_ALPHA << 24) | rgb;
    }

    /** The relative luminance of {@code color}'s RGB (WCAG), 0 to 1. */
    static double luminance(int color) {
        return 0.2126d * linear((color >> 16) & 0xFF) + 0.7152d * linear((color >> 8) & 0xFF)
            + 0.0722d * linear(color & 0xFF);
    }

    private static double linear(int channel) {
        double c = channel / 255d;
        return c <= 0.03928d ? c / 12.92d : Math.pow((c + 0.055d) / 1.055d, 2.4d);
    }
}
