package com.termux.app.chrome;

import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;

import androidx.annotation.NonNull;

/** The colour filters the chrome paints its blurred backdrops through. */
public final class GlassFilters {

    private GlassFilters() {}

    /** Cached light-scatter filter applied to the blurred wallpaper backdrop. */
    private static ColorMatrixColorFilter sFrostFilter;

    /**
     * "Liquid glass" vibrancy applied to the blurred backdrop (cheap GPU colour filter). Apple-style
     * glass does NOT desaturate and lift the backdrop toward grey — that reads as milky plastic.
     * Instead it keeps the content vivid: boost saturation and DEEPEN contrast so darks stay dark and
     * colours pop through the blur, so the dock reads as a vivid see-through pane, not a flat slab.
     */
    @NonNull
    public static synchronized ColorMatrixColorFilter frost() {
        if (sFrostFilter == null) {
            ColorMatrix frost = new ColorMatrix();
            frost.setSaturation(1.30f);   // vibrancy boost (was desaturating -> milk)
            float c = 1.06f;   // slight contrast boost (>1); opposite of the milky compression
            float t = -6f;     // no brightness lift; tiny deepen so darks don't haze to grey
            ColorMatrix vibrancy = new ColorMatrix(new float[] {
                c, 0, 0, 0, t,
                0, c, 0, 0, t,
                0, 0, c, 0, t,
                0, 0, 0, 1, 0
            });
            frost.postConcat(vibrancy);
            sFrostFilter = new ColorMatrixColorFilter(frost);
        }
        return sFrostFilter;
    }
}
