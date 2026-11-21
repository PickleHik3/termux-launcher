package com.termux.app.wall.pageswitch;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.core.graphics.ColorUtils;

/**
 * How solid the page bar's non-current glyphs are. The current glyph is the accent and the others
 * the ink; where the two colours are close (the default light scheme's navy on navy) the current
 * one would not stand out, so the others are dimmed until it is the only solid glyph.
 */
public final class PageBarInk {

    /**
     * The CIELAB distance below which two colours read as one family: about 25 is where a pair
     * stops looking like a variant of the same colour. The mock's peach against cream is far past
     * it, navy against a slightly different navy far under.
     */
    public static final double CLOSE = 25.0;
    /** The share of its alpha a neutral glyph keeps when the accent and the ink are close. */
    public static final float DIMMED = 0.5f;

    private PageBarInk() {}

    public static float neutralAlpha(@ColorInt int accent, @ColorInt int ink) {
        return neutralAlpha(accent, ink, new double[3], new double[3]);
    }

    /** {@link #neutralAlpha(int, int)} reusing two 3-element scratch arrays, for drawing. */
    public static float neutralAlpha(@ColorInt int accent, @ColorInt int ink,
                                     @NonNull double[] scratchA, @NonNull double[] scratchB) {
        ColorUtils.colorToLAB(accent, scratchA);
        ColorUtils.colorToLAB(ink, scratchB);
        return ColorUtils.distanceEuclidean(scratchA, scratchB) < CLOSE ? DIMMED : 1f;
    }
}
