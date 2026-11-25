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
