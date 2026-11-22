package com.termux.app.chrome;

import androidx.annotation.NonNull;

/**
 * A round-rect cut as {@code {left, top, right, bottom, radius}}: what a glass host is clipped to
 * and what its glass program draws its rim along. A crossing blends one rest's cut into the
 * other's ({@link #blend}), so the landing finds the arriving cut already drawn.
 */
public final class RoundCut {

    public static final int LEFT = 0;
    public static final int TOP = 1;
    public static final int RIGHT = 2;
    public static final int BOTTOM = 3;
    public static final int RADIUS = 4;
    public static final int SIZE = 5;

    private RoundCut() {}

    /**
     * {@code from} at {@code share} 0, {@code to} at 1, a straight blend between and clamped
     * outside, into {@code out} (which may be either input).
     */
    public static void blend(@NonNull float[] from, @NonNull float[] to, float share,
                             @NonNull float[] out) {
        float w = share <= 0f ? 0f : share >= 1f ? 1f : share;
        for (int i = 0; i < SIZE; i++) {
            out[i] = w <= 0f ? from[i] : w >= 1f ? to[i] : from[i] + (to[i] - from[i]) * w;
        }
    }

    /** Sets {@code out} to the cut given. */
    public static void set(@NonNull float[] out, float left, float top, float right, float bottom,
                           float radius) {
        out[LEFT] = left;
        out[TOP] = top;
        out[RIGHT] = right;
        out[BOTTOM] = bottom;
        out[RADIUS] = radius;
    }
}
