package com.termux.app.chrome;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;

/**
 * The Tint amount's colour behind see-through glass: a hue shift toward the accent, baked into
 * the shared blurred frame every glass surface samples, next to Vibrancy ({@link GlassVibrancy}),
 * once per change and never per frame. The face carries the accent only at the surface's opacity,
 * so on Clear (opacity 10) the face alone shows a tenth of it; the frame carries the rest.
 *
 * <p>Each pixel moves toward its own luminance in the accent's hue,
 * {@code out = (1 - s) * in + s * Y(in) * (1 + v)}, where {@code v} is the accent's per-channel
 * deviation from its own luminance, capped at {@link #MAX_CHROMA}. The luminance weights sum
 * {@code v} to zero, so the pixel's luminance is unchanged before the channels clip: no white
 * lift and no flat wash, so it cannot go milky, and the ink vote, which samples this same frame,
 * sees a frame as light as it was. The strength {@code s} grows with the amount up to
 * {@link #MAX_STRENGTH} and falls as the opacity rises, to nothing at
 * {@link #FACE_CARRIES_OPACITY}, where the face shows the accent plainly by itself. The Tint Look
 * (opacity 46) therefore draws exactly what it drew before.</p>
 *
 * <p>It is one int: the strength in the alpha byte (0 is {@link #NONE}) and the hue in the rest,
 * so the frame cache keys on it as it keys on Vibrancy. It follows the shared glass (Base's Tint,
 * hue and Opacity), as Vibrancy does: the frame is one for every surface of a radius. Pure ints
 * and floats, no {@code Context}.</p>
 */
public final class GlassFrameTint {

    private GlassFrameTint() {}

    /** No tint: the frame as Vibrancy leaves it. */
    public static final int NONE = 0;
    /**
     * The opacity, percent, from which the face carries the accent by itself and the frame takes
     * none: the developer's Opacity 50 at Tint 100 already reads as a plain wash, and the Tint
     * Look's 46 sits past it.
     */
    public static final int FACE_CARRIES_OPACITY = 45;
    /**
     * The share of each pixel the full amount moves into the accent's hue on fully clear glass.
     * With {@link #MAX_CHROMA} a mid grey moves at most about 0.25 of itself on its strongest
     * channel: plainly coloured, never neon.
     */
    public static final float MAX_STRENGTH = 0.42f;
    /** How far any channel of the hue may stand off its luminance, as a share of it. */
    public static final float MAX_CHROMA = 0.6f;
    /** {@code ColorMatrix.setSaturation}'s luminance weights, which Vibrancy's matrix uses too. */
    static final float LUMA_R = 0.213f;
    static final float LUMA_G = 0.715f;
    static final float LUMA_B = 0.072f;
    /** A hue whose channels stand closer than this to its luminance is grey: nothing to tint with. */
    private static final float GREY = 0.02f;

    /** The share of each pixel the frame tint moves, 0..{@link #MAX_STRENGTH}. */
    public static float strength(int amountPercent, int opacityPercent) {
        float amount = clampPercent(amountPercent) / 100f;
        float clear = 1f - clampPercent(opacityPercent) / (float) FACE_CARRIES_OPACITY;
        if (amount <= 0f || clear <= 0f) return 0f;
        return MAX_STRENGTH * amount * clear;
    }

    /**
     * The frame tint the shared glass bakes: {@code amountPercent} of the accent behind glass at
     * {@code opacityPercent}, in the hue of the more colourful of {@code container} and
     * {@code accent} (on dark glass the container, on light the role colour, by M3's tones).
     * {@link #NONE} where the strength rounds to nothing or both colours are grey.
     */
    public static int of(int amountPercent, int opacityPercent, @ColorInt int container,
                         @ColorInt int accent) {
        int alpha = Math.round(strength(amountPercent, opacityPercent) * 255f);
        if (alpha <= 0) return NONE;
        int hue = chromaOf(container) >= chromaOf(accent) ? container : accent;
        if (chromaOf(hue) < GREY) return NONE;
        return (alpha << 24) | (hue & 0x00FFFFFF);
    }

    /** Whether a frame blurred at {@code blurRadiusDp} takes {@code tint}: never the bare wallpaper. */
    public static boolean bakes(int blurRadiusDp, int tint) {
        return blurRadiusDp > 0 && tint != NONE;
    }

    /** The share of each pixel {@code tint} moves. */
    public static float strengthOf(int tint) {
        return (tint >>> 24) / 255f;
    }

    /**
     * {@code tint} as a 4x5 {@code ColorMatrix} array: row c is
     * {@code (1 - s) * e_c + s * (1 + v_c) * L}, alpha untouched.
     */
    @NonNull
    public static float[] matrix(int tint) {
        float s = strengthOf(tint);
        float[] v = deviation(tint);
        float[] luma = {LUMA_R, LUMA_G, LUMA_B};
        float[] m = new float[20];
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 3; col++)
                m[row * 5 + col] = (row == col ? 1f - s : 0f) + s * (1f + v[row]) * luma[col];
        }
        m[18] = 1f;
        return m;
    }

    /** {@code color} through {@link #matrix}, as the frame's pixel comes out: what a test or a measurement models. */
    @ColorInt
    public static int apply(int tint, @ColorInt int color) {
        if (tint == NONE) return color;
        float[] m = matrix(tint);
        float r = (color >> 16) & 0xFF;
        float g = (color >> 8) & 0xFF;
        float b = color & 0xFF;
        int out = color & 0xFF000000;
        for (int row = 0; row < 3; row++) {
            float c = m[row * 5] * r + m[row * 5 + 1] * g + m[row * 5 + 2] * b;
            out |= Math.round(Math.max(0f, Math.min(255f, c))) << (16 - 8 * row);
        }
        return out;
    }

    /** The hue's per-channel standing off its luminance, capped at {@link #MAX_CHROMA}. */
    @NonNull
    static float[] deviation(int tint) {
        float y = luma(tint);
        float[] v = new float[3];
        if (y <= 0f) return v;
        float most = 0f;
        for (int c = 0; c < 3; c++) {
            v[c] = ((tint >> (16 - 8 * c)) & 0xFF) / y - 1f;
            most = Math.max(most, Math.abs(v[c]));
        }
        float scale = most > MAX_CHROMA ? MAX_CHROMA / most : 1f;
        for (int c = 0; c < 3; c++) v[c] *= scale;
        return v;
    }

    /** How far {@code color}'s most distant channel stands off its luminance, as a share of it. */
    static float chromaOf(@ColorInt int color) {
        float y = luma(color);
        if (y <= 0f) return 0f;
        float most = 0f;
        for (int c = 0; c < 3; c++)
            most = Math.max(most, Math.abs(((color >> (16 - 8 * c)) & 0xFF) / y - 1f));
        return most;
    }

    static float luma(@ColorInt int color) {
        return LUMA_R * ((color >> 16) & 0xFF) + LUMA_G * ((color >> 8) & 0xFF)
            + LUMA_B * (color & 0xFF);
    }

    private static int clampPercent(int percent) {
        return Math.max(0, Math.min(100, percent));
    }
}
