package com.termux.app.chrome;

import android.graphics.ColorFilter;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/** The colour filters the chrome paints its blurred backdrops through. */
public final class GlassFilters {

    private GlassFilters() {}

    /** The brightness offset of {@link #frost()}, and the none of {@link #frost(int)}'s Clear. */
    private static final float FROST_OFFSET = -6f;
    private static final float CLEAR_OFFSET = 0f;

    /** Cached light-scatter filter applied to the blurred wallpaper backdrop. */
    private static ColorMatrixColorFilter sFrostFilter;

    /** The same filter without its brightness offset, for a surface so transparent that the offset would be its only tint. */
    private static ColorMatrixColorFilter sFrostFilterClear;

    /**
     * {@link #undimmed} per base filter — none, {@link #frost()}, the Clear one — and per
     * brightness kept: at most one filter per sink dim level, built once.
     */
    private static final ColorMatrixColorFilter[][] sUndimmed = new ColorMatrixColorFilter[3][];

    /**
     * "Liquid glass" vibrancy applied to the blurred backdrop (cheap GPU colour filter). Apple-style
     * glass does NOT desaturate and lift the backdrop toward grey — that reads as milky plastic.
     * Instead it keeps the content vivid: boost saturation and DEEPEN contrast so darks stay dark and
     * colours pop through the blur, so the dock reads as a vivid see-through pane, not a flat slab.
     */
    @NonNull
    public static synchronized ColorMatrixColorFilter frost() {
        if (sFrostFilter == null) sFrostFilter = new ColorMatrixColorFilter(frostMatrix(FROST_OFFSET));
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
        if (sFrostFilterClear == null) sFrostFilterClear = new ColorMatrixColorFilter(frostMatrix(CLEAR_OFFSET));
        return sFrostFilterClear;
    }

    /**
     * {@code frost} with a wall page's sink dim undone in advance: drawn through it into a page
     * whose layer keeps {@code keptBrightness} of 255 per channel, the wallpaper comes out of the
     * dim as it went in, so a sinking page's glass still shows the wallpaper behind it while the
     * page's own layers darken. Exact for every channel at or under {@code keptBrightness}; the
     * brightest few, which the layer's 8 bits cannot lift, come out that much dimmer.
     *
     * <p>{@code frost} is one of this class's filters or null; any other filter is handed back as
     * it is, undimmed by nothing.</p>
     */
    @Nullable
    public static synchronized ColorFilter undimmed(@Nullable ColorFilter frost, int keptBrightness) {
        if (keptBrightness >= 0xFF || keptBrightness <= 0) return frost;
        int base;
        if (frost == null) base = 0;
        else if (frost == sFrostFilter) base = 1;
        else if (frost == sFrostFilterClear) base = 2;
        else return frost;
        ColorMatrixColorFilter[] levels = sUndimmed[base];
        if (levels == null) {
            levels = new ColorMatrixColorFilter[0xFF];
            sUndimmed[base] = levels;
        }
        ColorMatrixColorFilter filter = levels[keptBrightness];
        if (filter == null) {
            ColorMatrix matrix = base == 0 ? new ColorMatrix()
                : frostMatrix(base == 1 ? FROST_OFFSET : CLEAR_OFFSET);
            float lift = 0xFF / (float) keptBrightness;
            ColorMatrix gain = new ColorMatrix();
            gain.setScale(lift, lift, lift, 1f);
            matrix.postConcat(gain);
            filter = new ColorMatrixColorFilter(matrix);
            levels[keptBrightness] = filter;
        }
        return filter;
    }

    @NonNull
    private static ColorMatrix frostMatrix(float t) {
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
        return frost;
    }
}
