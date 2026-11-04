package com.termux.app.chrome;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * What a live picture needs, on top of its blur, to come out as the stored frame does at rest, so
 * the hand-off from one to the other (and back) never changes the glass's look.
 *
 * <p>The stored frame is the wallpaper alone, blurred, with Vibrancy and the frame tint baked in
 * ({@link GlassVibrancy#bakeMatrix}); the frost and the slab's even-out are the paint's, on both
 * routes. A live recording is what the window draws there, and the launcher-drawn wallpaper
 * ({@link WallpaperBackdropView}) draws the wall's dim over itself, which the stored frame never
 * carries. So the live picture takes the dim back out ({@code in = (out - d a) / (1 - a)}, exact
 * for a dim drawn source-over an opaque wallpaper) and then the same bake. Blur and these colour
 * matrices are both linear, so applying them after the blur rather than before changes nothing
 * but the clipping of the brightest few. Pure floats; a picture builds its filter from this only
 * when an input moves, never per draw.</p>
 */
public final class LiveStoredParity {

    private LiveStoredParity() {}

    /**
     * The dim the parity will undo at most: past it the dim leaves too little of the wallpaper to
     * lift back, and the picture keeps whatever dim is beyond.
     */
    static final float MAX_UNDIM_ALPHA = 0.9f;

    /**
     * The 4x5 matrix that takes a live recording made under {@code dimColor} to the stored frame's
     * colours at {@code vibrancyPercent} and {@code frameTint}; null when nothing is to be done
     * (no dim, nothing baked). {@code blurs} false (a radius of 0) bakes nothing, as the stored
     * frame at radius 0 is the wallpaper itself.
     */
    @Nullable
    public static float[] matrix(int dimColor, int vibrancyPercent, int frameTint, boolean blurs) {
        float[] undim = undim(dimColor);
        float[] bake = blurs && (GlassVibrancy.clampPercent(vibrancyPercent) != 100
            || frameTint != GlassFrameTint.NONE)
            ? GlassVibrancy.bakeMatrix(vibrancyPercent, frameTint) : null;
        if (undim == null) return bake;
        if (bake == null) return undim;
        return concat(bake, undim);
    }

    /** The matrix that takes {@code dimColor}, drawn source-over, back off; null for no dim. */
    @Nullable
    static float[] undim(int dimColor) {
        int alpha = dimColor >>> 24;
        if (alpha <= 0) return null;
        float a = Math.min(MAX_UNDIM_ALPHA, alpha / 255f);
        float gain = 1f / (1f - a);
        float[] m = new float[20];
        m[0] = gain;
        m[6] = gain;
        m[12] = gain;
        m[18] = 1f;
        m[4] = -((dimColor >> 16) & 0xFF) * a * gain;
        m[9] = -((dimColor >> 8) & 0xFF) * a * gain;
        m[14] = -(dimColor & 0xFF) * a * gain;
        return m;
    }

    /**
     * {@code outer} after {@code inner}, as {@code ColorMatrix.setConcat(outer, inner)}: a colour
     * goes through {@code inner} first.
     */
    @NonNull
    static float[] concat(@NonNull float[] outer, @NonNull float[] inner) {
        float[] out = new float[20];
        for (int row = 0; row < 4; row++) {
            for (int col = 0; col < 5; col++) {
                float sum = col == 4 ? outer[row * 5 + 4] : 0f;
                for (int k = 0; k < 4; k++) sum += outer[row * 5 + k] * inner[k * 5 + col];
                out[row * 5 + col] = sum;
            }
        }
        return out;
    }

    /** {@code rgb} (0..255 each) through {@code m}, unclamped; what a test models. */
    @NonNull
    static float[] apply(@NonNull float[] m, float r, float g, float b) {
        float[] in = {r, g, b, 255f};
        float[] out = new float[3];
        for (int row = 0; row < 3; row++) {
            float sum = m[row * 5 + 4];
            for (int k = 0; k < 4; k++) sum += m[row * 5 + k] * in[k];
            out[row] = sum;
        }
        return out;
    }
}
