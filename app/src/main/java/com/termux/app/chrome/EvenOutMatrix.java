package com.termux.app.chrome;

import android.graphics.Color;
import android.graphics.ColorMatrix;

import androidx.annotation.ColorInt;
import androidx.annotation.Nullable;

import com.google.android.material.color.utilities.ColorUtils;

/**
 * The even-out {@link EvenOut.Mapping} as a {@link ColorMatrix}, so it rides in the frost
 * {@code ColorFilter} every glass band already draws its frame with: no shader, no extra pass and
 * nothing per frame, on every supported Android.
 *
 * <p>The map is affine on the sRGB-encoded value, {@code v' = a*v + b}. sRGB encoding and CIE L*
 * are both near-perceptual, so an affine map in one tracks the linear map in the other to within
 * about half a tone across dark, mid and light ranges, which sits inside
 * {@link EvenOut#BAND_MARGIN}. {@code a} and {@code b} are solved so the greys at the mapping's
 * p2 and p98 land on its band ends.
 *
 * <p>A plain affine map would also scale every channel's distance from grey by {@code a}, draining
 * the wallpaper's colour exactly where it was compressed. Instead the luma part of each channel is
 * scaled by {@code a} and a share {@code s} of its spread around luma is kept: the output channel
 * is {@code a*luma + b + s*(c - luma)}, so a grey is mapped exactly and a colour keeps most of its
 * saturation. The filter clamps what leaves the gamut.
 */
public final class EvenOutMatrix {

    private EvenOutMatrix() {}

    // The luma weights GlassFilters.frost's saturation matrix uses, so the two compose coherently.
    private static final float LUMA_R = 0.213f;
    private static final float LUMA_G = 0.715f;
    private static final float LUMA_B = 0.072f;

    /**
     * The share of each channel's spread around luma that is kept is {@code (a + 1) / 2}: halfway
     * between a plain affine map (share {@code a}, colour drained as hard as the lightness is
     * compressed) and leaving the spread alone (share 1, which on a strongly compressed band
     * leaves colour far more vivid than the ground it sits on). To be tuned on pong shots.
     */
    static float defaultChromaKeep(float a) {
        return (a + 1f) / 2f;
    }

    /** The sRGB-encoded 0..255 value of the grey at {@code tone}. */
    private static float encoded(double tone) {
        return Color.red(ColorUtils.argbFromLstar(Math.max(0d, Math.min(100d, tone))));
    }

    /** The slope {@code a} of {@code mapping} in sRGB-encoded value, or 1 for a degenerate range. */
    static float slope(EvenOut.Mapping mapping) {
        float span = encoded(mapping.srcHi) - encoded(mapping.srcLo);
        if (span < 1e-3f) return 1f;
        return (encoded(mapping.dstHi) - encoded(mapping.dstLo)) / span;
    }

    /**
     * The matrix for {@code mapping}, or null for the identity: the stage then draws nothing
     * extra. Uses {@link #defaultChromaKeep}.
     */
    @Nullable
    public static ColorMatrix of(EvenOut.Mapping mapping) {
        return of(mapping, defaultChromaKeep(slope(mapping)));
    }

    /** {@link #of(EvenOut.Mapping)} with an explicit chroma-keep share {@code s}, 0..1. */
    @Nullable
    public static ColorMatrix of(EvenOut.Mapping mapping, float chromaKeep) {
        if (mapping.isIdentity()) return null;
        float a = slope(mapping);
        float b = encoded(mapping.dstLo) - a * encoded(mapping.srcLo);
        float s = Math.max(0f, Math.min(1f, chromaKeep));
        float k = a - s;
        return new ColorMatrix(new float[] {
            s + k * LUMA_R, k * LUMA_G, k * LUMA_B, 0f, b,
            k * LUMA_R, s + k * LUMA_G, k * LUMA_B, 0f, b,
            k * LUMA_R, k * LUMA_G, s + k * LUMA_B, 0f, b,
            0f, 0f, 0f, 1f, 0f,
        });
    }

    /**
     * {@code color} as {@code matrix} draws it, opaque and clamped, for the band ends the palette
     * is solved against: the same arithmetic as the filter, so the palette is solved against what
     * is on screen. A null matrix is the identity.
     */
    @ColorInt
    public static int applyTo(@Nullable ColorMatrix matrix, @ColorInt int color) {
        if (matrix == null) return color;
        float[] m = matrix.getArray();
        float r = Color.red(color);
        float g = Color.green(color);
        float b = Color.blue(color);
        return Color.rgb(
            clamp(m[0] * r + m[1] * g + m[2] * b + m[4]),
            clamp(m[5] * r + m[6] * g + m[7] * b + m[9]),
            clamp(m[10] * r + m[11] * g + m[12] * b + m[14]));
    }

    private static int clamp(float value) {
        int rounded = Math.round(value);
        return rounded < 0 ? 0 : (rounded > 255 ? 255 : rounded);
    }
}
