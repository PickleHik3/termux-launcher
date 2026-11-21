package com.termux.app.chrome;

import androidx.annotation.ColorInt;

/**
 * The glass a surface pressed into the page wears: a well, not a card. The well keeps the Look's
 * glass as it is (blur, opacity and grain are the Base look's, ADR 0031) and takes the raised
 * cues away (no drop shadow, no raised rim); what makes it read as a dip is drawn over the glass:
 *
 * <ul>
 *   <li>a shade toward the shadow colour over the whole well, so it is a step denser than a card
 *   standing on the same page, never whiter ({@link #wellShade});</li>
 *   <li>an inner shadow under the top edge, fading out over {@link #shadowDepthPx} as it goes
 *   down, cut to the well's own outline ({@link #innerShadow});</li>
 *   <li>a thin light lip along the inside of the bottom edge, the light the far wall catches
 *   ({@link #lip}), fading in from {@link #LIP_FROM_SHARE} of the well's height.</li>
 * </ul>
 *
 * <p>On a light ground every cue is weaker in shadow and stronger in light, so the dip reads on
 * light, dark and the scheme's chrome alike. The page bar's Home pill wears it first.</p>
 *
 * <p>Pure: answers colours and lengths at dress time; the shaders built from them are made on a
 * size or look change only, never per frame.</p>
 */
public final class InsetGlass {

    /** The colour the well is shaded toward and its inner shadow is drawn in. */
    @ColorInt public static final int SHADOW_RGB = 0x000000;
    /** The colour of the lip along the bottom edge. */
    @ColorInt public static final int LIP_RGB = 0xFFFFFF;

    /** The shade over the whole well: about 14 % on a dark ground, 8 % on a light one. */
    public static final int WELL_SHADE_ALPHA_DARK = 0x24;
    public static final int WELL_SHADE_ALPHA_LIGHT = 0x14;

    /** The inner shadow's alpha at the top edge: about 40 % dark, 20 % light. */
    public static final int INNER_SHADOW_ALPHA_DARK = 0x66;
    public static final int INNER_SHADOW_ALPHA_LIGHT = 0x33;
    /** How far down the inner shadow fades out, at most {@link #INNER_SHADOW_MAX_SHARE} of the well. */
    public static final float INNER_SHADOW_DEPTH_DP = 5f;
    public static final float INNER_SHADOW_MAX_SHARE = 0.4f;

    /** The lip's alpha at the bottom edge: about 18 % dark, 55 % light (white on white needs more). */
    public static final int LIP_ALPHA_DARK = 0x2E;
    public static final int LIP_ALPHA_LIGHT = 0x8C;
    /** The lip's width, at most {@link #LIP_MAX_SHARE} of the well. */
    public static final float LIP_WIDTH_DP = 1f;
    public static final float LIP_MAX_SHARE = 0.08f;
    /** The lip is clear above this share of the well's height and full at its bottom. */
    public static final float LIP_FROM_SHARE = 0.5f;

    private InsetGlass() {}

    /** The shade laid over the whole well, translucent. */
    @ColorInt
    public static int wellShade(boolean darkGround) {
        return argb(darkGround ? WELL_SHADE_ALPHA_DARK : WELL_SHADE_ALPHA_LIGHT, SHADOW_RGB);
    }

    /** The inner shadow's colour at the top edge; it fades to clear at {@link #shadowDepthPx}. */
    @ColorInt
    public static int innerShadow(boolean darkGround) {
        return argb(darkGround ? INNER_SHADOW_ALPHA_DARK : INNER_SHADOW_ALPHA_LIGHT, SHADOW_RGB);
    }

    /** The lip's colour at the bottom edge; it fades to clear at {@link #LIP_FROM_SHARE}. */
    @ColorInt
    public static int lip(boolean darkGround) {
        return argb(darkGround ? LIP_ALPHA_DARK : LIP_ALPHA_LIGHT, LIP_RGB);
    }

    /** How far down the inner shadow reaches in a well {@code heightPx} tall: small wells clamp it. */
    public static float shadowDepthPx(float density, float heightPx) {
        return Math.max(0f, Math.min(INNER_SHADOW_DEPTH_DP * density,
            heightPx * INNER_SHADOW_MAX_SHARE));
    }

    /** The lip's width in a well {@code heightPx} tall: small wells clamp it. */
    public static float lipWidthPx(float density, float heightPx) {
        return Math.max(0f, Math.min(LIP_WIDTH_DP * density, heightPx * LIP_MAX_SHARE));
    }

    /**
     * The opaque ground the well's content stands on: {@code ground} (opaque) under the
     * {@link #wellShade}. What the glyph inks are solved on.
     */
    @ColorInt
    public static int groundIn(@ColorInt int ground, boolean darkGround) {
        int shade = wellShade(darkGround);
        float a = (shade >>> 24) / 255f;
        int red = over((shade >> 16) & 0xFF, (ground >> 16) & 0xFF, a);
        int green = over((shade >> 8) & 0xFF, (ground >> 8) & 0xFF, a);
        int blue = over(shade & 0xFF, ground & 0xFF, a);
        return 0xFF000000 | (red << 16) | (green << 8) | blue;
    }

    private static int over(int source, int under, float alpha) {
        return Math.max(0, Math.min(255, Math.round(source * alpha + under * (1f - alpha))));
    }

    @ColorInt
    private static int argb(int alpha, @ColorInt int rgb) {
        return (Math.max(0, Math.min(255, alpha)) << 24) | (rgb & 0x00FFFFFF);
    }
}
