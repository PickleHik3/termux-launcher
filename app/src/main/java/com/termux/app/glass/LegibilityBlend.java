package com.termux.app.glass;

import androidx.annotation.NonNull;

/**
 * The look blend's legibility half (ADR 0029 sections 8 and 9): a value for page A, one for page B
 * and the crossing's progress give the value in between. Pure arithmetic over stored corrections;
 * nothing here reads a wallpaper or a view, so a crossing that uses it measures nothing, and no
 * method allocates.
 *
 * <p>Ink blends with the glass between its two ends, pale and dark, on the crossing's own curve:
 * the caller passes the progress the glass blends with, so ink and glass never disagree. The ink
 * of each end is chosen at rest from that end's face and blended as a colour; choosing an ink from
 * a blended face would flip at the middle of the crossing, which is the flip this exists to stop.
 */
public final class LegibilityBlend {
    private LegibilityBlend() {}

    /**
     * {@code from} at progress 0 and {@code to} at 1, channel by channel in ARGB; exactly each end
     * at its end. A progress outside 0..1 is clamped and a NaN is 0.
     */
    public static int color(int from, int to, float progress) {
        float t = clamp01(progress);
        if (t <= 0f) return from;
        if (t >= 1f) return to;
        int a = lerp((from >>> 24) & 0xFF, (to >>> 24) & 0xFF, t);
        int r = lerp((from >>> 16) & 0xFF, (to >>> 16) & 0xFF, t);
        int g = lerp((from >>> 8) & 0xFF, (to >>> 8) & 0xFF, t);
        int b = lerp(from & 0xFF, to & 0xFF, t);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    /**
     * Each row of {@code out} blended from {@code from}'s row to {@code to}'s, for as many rows as
     * {@code out} has; a correction with fewer rows repeats its last. Returns {@code out}.
     */
    @NonNull
    public static int[] faces(@NonNull LegibilityCorrection from, @NonNull LegibilityCorrection to,
                              float progress, @NonNull int[] out) {
        for (int row = 0; row < out.length; row++) {
            out[row] = color(from.face(row), to.face(row), progress);
        }
        return out;
    }

    /**
     * The legend tone at progress: the leaving end's until the middle, the arriving end's after.
     * For a consumer that can only choose a side (a palette); a colour consumer uses
     * {@link #color} and drifts.
     */
    public static int tone(@NonNull LegibilityCorrection from, @NonNull LegibilityCorrection to,
                           float progress) {
        return clamp01(progress) < 0.5f ? from.tone() : to.tone();
    }

    private static float clamp01(float value) {
        if (value != value) return 0f;
        return value < 0f ? 0f : (value > 1f ? 1f : value);
    }

    private static int lerp(int from, int to, float t) {
        return Math.round(from + (to - from) * t);
    }
}
