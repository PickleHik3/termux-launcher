package com.termux.app.chrome;

import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;

import androidx.annotation.NonNull;

/** The colour filters the chrome paints its blurred backdrops through. */
public final class GlassFilters {

    private GlassFilters() {}

    /** Cached light-scatter filter applied to the blurred wallpaper backdrop. */
    private static ColorMatrixColorFilter sFrostFilter;

    /** The same filter without its brightness offset, for a surface so transparent that the offset would be its only tint. */
    private static ColorMatrixColorFilter sFrostFilterClear;

    /**
     * "Liquid glass" vibrancy applied to the blurred backdrop (cheap GPU colour filter). Apple-style
     * glass does NOT desaturate and lift the backdrop toward grey — that reads as milky plastic.
     * Instead it keeps the content vivid: boost saturation and DEEPEN contrast so darks stay dark and
     * colours pop through the blur, so the dock reads as a vivid see-through pane, not a flat slab.
     */
    @NonNull
    public static synchronized ColorMatrixColorFilter frost() {
        if (sFrostFilter == null) sFrostFilter = build(-6f);
        return sFrostFilter;
    }

    /**
     * {@link #frost()} for a base opacity (percent): the same filter, except that a surface at or
     * under {@link LowOpacityGlass#CLEAR_UNDER} (Clear) gets no -6 brightness offset, which on a
     * 2% glass is the darkest thing about it.
     */
    @NonNull
    public static synchronized ColorMatrixColorFilter frost(int opacityPercent) {
        if (LowOpacityGlass.keep(opacityPercent / 100f) > 0f) return frost();
        if (sFrostFilterClear == null) sFrostFilterClear = build(0f);
        return sFrostFilterClear;
    }

    private static ColorMatrixColorFilter build(float t) {
        {
            ColorMatrix frost = new ColorMatrix();
            frost.setSaturation(1.30f);   // vibrancy boost (was desaturating -> milk)
            float c = 1.06f;   // slight contrast boost (>1); opposite of the milky compression
            // t: no brightness lift; tiny deepen so darks don't haze to grey
            ColorMatrix vibrancy = new ColorMatrix(new float[] {
                c, 0, 0, 0, t,
                0, c, 0, 0, t,
                0, 0, c, 0, t,
                0, 0, 0, 1, 0
            });
            frost.postConcat(vibrancy);
            return new ColorMatrixColorFilter(frost);
        }
    }
}
