package com.termux.app.dock;

import androidx.annotation.NonNull;

/**
 * The numbers behind the Peek reflection's polished-glass look, held pure so the live program
 * ({@link PeekGlassReflection}'s AGSL) and the tests read the same ones: how the mirror blurs and
 * fades with depth, the static ripple, the thin specular line along the card's top edge, and the
 * band behind the A–Z letters where the reflection softens so they stay readable. Nothing here
 * moves with time: the look is drawn again only when the card, the icons or the backdrop change.
 */
public final class PeekReflectionLook {

    /** The ripple's largest sideways push, at the band's far end, in dp. */
    public static final float RIPPLE_AMPLITUDE_DP = 1.4f;
    /** The ripple's wavelength down the band, in dp, and along it. */
    public static final float RIPPLE_WAVELENGTH_Y_DP = 13f;
    public static final float RIPPLE_WAVELENGTH_X_DP = 47f;
    /** The specular line's thickness, in dp. */
    public static final float SPECULAR_THICKNESS_DP = 1.5f;
    /** The specular line's strength at the card's top, as white's alpha. */
    public static final float SPECULAR_STRENGTH = 0.85f;
    /** How far down the card's sides the specular line carries, as a share of the corner radius. */
    public static final float SPECULAR_SIDE_REACH = 1.5f;
    /** What the A–Z band takes of the reflection at its centre: darkening and desaturation. */
    public static final float AZ_DARKEN = 0.65f;
    public static final float AZ_DESATURATE = 0.8f;
    /** The letters' band: this share of the letters' height each side of the centre, and the feather beyond. */
    public static final float AZ_HALF_HEIGHT = 0.9f;
    public static final float AZ_FEATHER = 0.7f;

    private PeekReflectionLook() {}

    /** How much of the blur's largest radius applies {@code t} down the band (0 at the icon, 1 at the far end). */
    public static float blurFraction(float t) {
        float c = clamp01(t);
        return 0.6f * c + 0.4f * c * c;
    }

    /** What is left of the reflection's strength {@code t} down the band: 1 at the icon, 0 at the far end. */
    public static float fadeFactor(float t) {
        float f = 1f - clamp01(t);
        return f * (float) Math.sqrt(f);
    }

    /** The ripple's sideways push in px {@code t} down the band: none at the icon. */
    public static float rippleAmplitudePx(float t, float density) {
        return RIPPLE_AMPLITUDE_DP * Math.max(0f, density) * clamp01(t);
    }

    /** The specular line's thickness in px; never under one pixel. */
    public static float specularThicknessPx(float density) {
        return Math.max(1f, SPECULAR_THICKNESS_DP * Math.max(0f, density));
    }

    /**
     * The specular line's strength at a point {@code insidePx} inside the card's edge (measured
     * along the rounded outline) and {@code belowTopPx} under the card's top edge, for a card whose
     * corners have {@code radiusPx}: strongest on the edge at the top, thinning inward, and
     * letting go down the sides after the corners.
     */
    public static float specular(float insidePx, float belowTopPx, float thicknessPx, float radiusPx) {
        if (insidePx < 0f) return 0f;
        float edge = 1f - smoothstep(0f, Math.max(0.5f, thicknessPx), insidePx);
        float reach = Math.max(1f, radiusPx * SPECULAR_SIDE_REACH);
        float side = 1f - smoothstep(0f, reach, Math.max(0f, belowTopPx));
        return SPECULAR_STRENGTH * edge * side;
    }

    /**
     * The A–Z letters' band, with its feather, in the host's coordinates: {@code out} takes the
     * top and the bottom of the span the reflection is softened across (full in the middle,
     * feathering to nothing at the ends). False with no letters ({@code centerY} NaN or no height).
     */
    public static boolean azBand(float centerY, float letterPx, float density, @NonNull float[] out) {
        if (Float.isNaN(centerY) || !(letterPx > 0f)) return false;
        float half = letterPx * (AZ_HALF_HEIGHT + AZ_FEATHER);
        out[0] = centerY - half;
        out[1] = centerY + half;
        return true;
    }

    /**
     * How much of the A–Z softening applies at row {@code y} for a band from {@code top} to
     * {@code bottom} ({@link #azBand}): 1 across the middle, easing to 0 at the ends. The
     * program's weight.
     */
    public static float azWeight(float y, float top, float bottom) {
        if (!(bottom > top)) return 0f;
        float mid = (top + bottom) * 0.5f;
        float half = (bottom - top) * 0.5f;
        float feather = half * (AZ_FEATHER / (AZ_HALF_HEIGHT + AZ_FEATHER));
        float d = Math.abs(y - mid);
        return 1f - smoothstep(half - feather, half, d);
    }

    private static float clamp01(float v) {
        return Math.max(0f, Math.min(1f, v));
    }

    private static float smoothstep(float e0, float e1, float x) {
        if (!(e1 > e0)) return x < e0 ? 0f : 1f;
        float t = clamp01((x - e0) / (e1 - e0));
        return t * t * (3f - 2f * t);
    }
}
