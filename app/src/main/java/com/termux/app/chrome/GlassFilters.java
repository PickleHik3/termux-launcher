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
        else {
            for (Evened evened : sEvened) {
                if (evened.filter != frost) continue;
                ColorMatrixColorFilter lifted = evened.undimmed[keptBrightness];
                if (lifted == null) {
                    ColorMatrix matrix = new ColorMatrix(evened.matrix);
                    ColorMatrix gain = new ColorMatrix();
                    float lift = 0xFF / (float) keptBrightness;
                    gain.setScale(lift, lift, lift, 1f);
                    matrix.postConcat(gain);
                    lifted = new ColorMatrixColorFilter(matrix);
                    evened.undimmed[keptBrightness] = lifted;
                }
                return lifted;
            }
            return frost;
        }
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

    /** One even-out filter built over a frost base, kept so {@link #undimmed} can lift it too. */
    private static final class Evened {
        final float[] evenOut;
        final ColorFilter base;
        final ColorMatrix matrix;
        final ColorMatrixColorFilter filter;
        final ColorMatrixColorFilter[] undimmed = new ColorMatrixColorFilter[0xFF];

        Evened(float[] evenOut, ColorFilter base, ColorMatrix matrix) {
            this.evenOut = evenOut;
            this.base = base;
            this.matrix = matrix;
            this.filter = new ColorMatrixColorFilter(matrix);
        }
    }

    private static final java.util.ArrayList<Evened> sEvened = new java.util.ArrayList<>();
    private static final int MAX_EVENED = 8;

    /**
     * {@code frost} with the even-out matrix applied first: the even-out acts on the blurred
     * wallpaper, then frost, then the surface's tint, opacity and grain as before (ADR 0014).
     * The same matrix over the same base is the same filter instance, so a pane re-fed with what
     * it already wears stays un-dressed; a null matrix, or a base that is not one of this
     * class's, is handed back as it is.
     */
    @Nullable
    public static synchronized ColorFilter withEvenOut(@Nullable ColorMatrix evenOut,
                                                       @Nullable ColorFilter frost) {
        if (evenOut == null || (frost != null && frost != sFrostFilter && frost != sFrostFilterClear)) {
            return frost;
        }
        float[] key = evenOut.getArray();
        for (Evened known : sEvened) {
            if (known.base == frost && java.util.Arrays.equals(known.evenOut, key)) return known.filter;
        }
        ColorMatrix composed = new ColorMatrix();
        if (frost == null) {
            composed.set(evenOut);
        } else {
            composed.setConcat(frostMatrix(frost == sFrostFilter ? FROST_OFFSET : CLEAR_OFFSET), evenOut);
        }
        Evened made = new Evened(key.clone(), frost, composed);
        if (sEvened.size() >= MAX_EVENED) sEvened.remove(0);
        sEvened.add(made);
        return made.filter;
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
