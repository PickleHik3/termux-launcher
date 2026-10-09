package com.termux.app.chrome;

/**
 * The Docked insert's tone floor (appearance-layout-editor SPEC 3.7, amended 2026-10-01): the
 * insert always stands at least one tone step darker than the frame glass round it, so the gutter
 * reads as a surface and the insert as a window in it, whatever the Look and however low Darkness
 * is. Darkness adds on top: a tint that already stands further down is left as it is.
 *
 * <p>One tone step is {@link #STEP}: the insert's relative luminance, once its tint is laid over the
 * frame glass, is at most {@code (1 - STEP)} of the frame glass's. A tint that falls short gets the
 * least extra black that closes the gap, laid over it. Pure and view-free: colours in, colour out.
 */
public final class InsertTone {

    private InsertTone() {}

    /** One tone step: a fifth less luminance than the frame glass beside it. */
    public static final float STEP = 0.20f;

    /**
     * The tint to dress the insert with.
     *
     * @param tint the insert's tint as the Look and Darkness give it, ARGB (any alpha, 0 included)
     * @param frameGlass the frame glass's effective colour, ARGB; read as opaque
     * @return {@code tint}, or {@code tint} with the least black laid over it that keeps the insert
     *     one {@link #STEP} darker than {@code frameGlass} once laid over it
     */
    public static int floorTint(int tint, int frameGlass) {
        return floorTint(tint, frameGlass, 1f);
    }

    /**
     * {@link #floorTint(int, int)} with the step scaled by {@code keep} (0..1, see
     * {@link LowOpacityGlass#keep}): a Look whose glass is nearly clear leaves the insert as
     * clear, instead of laying a fifth of the frame's light over it as black.
     */
    public static int floorTint(int tint, int frameGlass, float keep) {
        if (!(keep > 0f)) return tint;
        float frame = luma(frameGlass | 0xFF000000);
        float target = frame * (1f - STEP * Math.min(1f, keep));
        float now = luma(over(tint, frameGlass | 0xFF000000));
        if (now <= target || now <= 0f) return tint;
        // Black at alpha a laid over the tint scales the result's luminance by (1 - a).
        float a = 1f - target / now;
        int alpha = Math.min(255, (int) Math.ceil(a * 255f));
        return over(alpha << 24, tint);
    }

    /** Relative luminance of an opaque colour, 0 to 1, on the sRGB bytes (a perceptual stand-in). */
    public static float luma(int argb) {
        float r = ((argb >> 16) & 0xFF) / 255f;
        float g = ((argb >> 8) & 0xFF) / 255f;
        float b = (argb & 0xFF) / 255f;
        return 0.2126f * r + 0.7152f * g + 0.0722f * b;
    }

    /** {@code top} laid over {@code bottom}, both ARGB, straight alpha. */
    static int over(int top, int bottom) {
        int ta = top >>> 24;
        int ba = bottom >>> 24;
        int a = ta + ba * (255 - ta) / 255;
        if (a == 0) return 0;
        int out = a << 24;
        for (int shift = 16; shift >= 0; shift -= 8) {
            int t = (top >> shift) & 0xFF;
            int b = (bottom >> shift) & 0xFF;
            int c = (t * ta * 255 + b * ba * (255 - ta)) / (a * 255);
            out |= Math.min(255, c) << shift;
        }
        return out;
    }
}
