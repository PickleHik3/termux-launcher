package com.termux.app.glass;

import androidx.annotation.ColorInt;

/** Pure ARGB arithmetic shared by the stack material, the legibility blend and the dock glass. */
public final class Argb {
    private Argb() {}

    /**
     * {@code from} at {@code t} 0 and {@code to} at 1, channel by channel (alpha included), each
     * end exact. A {@code t} outside 0..1 is clamped and a NaN is 0. Allocates nothing.
     */
    @ColorInt
    public static int lerp(@ColorInt int from, @ColorInt int to, float t) {
        if (from == to || t != t || t <= 0f) return from;
        if (t >= 1f) return to;
        int a = channel((from >>> 24) & 0xFF, (to >>> 24) & 0xFF, t);
        int r = channel((from >>> 16) & 0xFF, (to >>> 16) & 0xFF, t);
        int g = channel((from >>> 8) & 0xFF, (to >>> 8) & 0xFF, t);
        int b = channel(from & 0xFF, to & 0xFF, t);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private static int channel(int from, int to, float t) {
        return Math.round(from + (to - from) * t);
    }
}
