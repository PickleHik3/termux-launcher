package com.termux.app.chrome;

import androidx.annotation.ColorInt;

/**
 * The glass a surface pressed into the page wears: a well, not a card. The well keeps the Look's
 * glass as it is (blur, opacity and grain are the Base look's, ADR 0031) and takes the raised
 * cues away (no drop shadow, no raised rim, no light inside it). Depth comes from shadow:
 *
 * <ul>
 *   <li>a light shade toward the shadow colour over the whole well ({@link #wellShade}), kept low
 *   so the inner shadow still shows on a dark fill;</li>
 *   <li>an inner shadow following the whole outline, as if lit from above: strongest under the
 *   top edge, {@link #INNER_SHADOW_SIDE_SHARE} of that along the sides at mid height and nothing
 *   at the bottom, fading inward over {@link #shadowReachPx}. It is drawn as concentric rings
 *   {@link #INNER_SHADOW_STEP_DP} apart, each at {@link #ringAlpha} (no blur, no bitmap);</li>
 *   <li>outside the outline, the rim of the hole in the page: a thin dark hairline across the
 *   top ({@link #rimShadow}) and a thin light one across the bottom arc, the far rim catching the
 *   light ({@link #rimLight}), each fading out up or down the round ends.</li>
 * </ul>
 *
 * <p>On a light ground the shadows are weaker and the light rim stronger, so the dip reads on
 * dark and light wallpapers and the scheme's chrome alike. The page bar's Home pill wears it.</p>
 *
 * <p>Pure: answers colours and lengths at dress time; the shaders built from them are made on a
 * size or look change only, never per frame.</p>
 */
public final class InsetGlass {

    /** The colour the well is shaded toward and its shadows are drawn in. */
    @ColorInt public static final int SHADOW_RGB = 0x000000;
    /** The colour of the light rim across the bottom arc. */
    @ColorInt public static final int LIGHT_RGB = 0xFFFFFF;

    /** The shade over the whole well: about 6 % on a dark ground, 4 % on a light one. */
    public static final int WELL_SHADE_ALPHA_DARK = 0x0F;
    public static final int WELL_SHADE_ALPHA_LIGHT = 0x0A;

    /** The inner shadow's alpha at the top edge: about 55 % dark, 30 % light. */
    public static final int INNER_SHADOW_ALPHA_DARK = 0x8C;
    public static final int INNER_SHADOW_ALPHA_LIGHT = 0x4D;
    /** How far in the inner shadow reaches, at most {@link #INNER_SHADOW_MAX_SHARE} of the well's height. */
    public static final float INNER_SHADOW_REACH_DP = 8f;
    public static final float INNER_SHADOW_MAX_SHARE = 0.4f;
    /** The inner shadow along the sides at mid height, as a share of its top strength. */
    public static final float INNER_SHADOW_SIDE_SHARE = 0.45f;
    /** The rings the inner shadow is drawn as stand this far apart; each is twice as wide, overlapping. */
    public static final float INNER_SHADOW_STEP_DP = 0.5f;

    /** The rim hairlines' width, outside the outline, at most {@link #RIM_MAX_SHARE} of the well's height. */
    public static final float RIM_WIDTH_DP = 1f;
    public static final float RIM_MAX_SHARE = 0.1f;
    /** The dark hairline across the top: about 40 % dark, 25 % light; clear below {@link #RIM_SHADOW_TO_SHARE}. */
    public static final int RIM_SHADOW_ALPHA_DARK = 0x66;
    public static final int RIM_SHADOW_ALPHA_LIGHT = 0x40;
    public static final float RIM_SHADOW_TO_SHARE = 0.4f;
    /** The light hairline across the bottom arc: about 35 % dark, 80 % light; clear above {@link #RIM_LIGHT_FROM_SHARE}. */
    public static final int RIM_LIGHT_ALPHA_DARK = 0x59;
    public static final int RIM_LIGHT_ALPHA_LIGHT = 0xCC;
    public static final float RIM_LIGHT_FROM_SHARE = 0.6f;

    private InsetGlass() {}

    /** The shade laid over the whole well, translucent. */
    @ColorInt
    public static int wellShade(boolean darkGround) {
        return argb(darkGround ? WELL_SHADE_ALPHA_DARK : WELL_SHADE_ALPHA_LIGHT, SHADOW_RGB);
    }

    /** The inner shadow's colour under the top edge; the sides take {@link #innerShadowSide}. */
    @ColorInt
    public static int innerShadow(boolean darkGround) {
        return argb(darkGround ? INNER_SHADOW_ALPHA_DARK : INNER_SHADOW_ALPHA_LIGHT, SHADOW_RGB);
    }

    /** The inner shadow's colour along the sides at mid height; clear at the bottom. */
    @ColorInt
    public static int innerShadowSide(boolean darkGround) {
        int top = darkGround ? INNER_SHADOW_ALPHA_DARK : INNER_SHADOW_ALPHA_LIGHT;
        return argb(Math.round(top * INNER_SHADOW_SIDE_SHARE), SHADOW_RGB);
    }

    /** The dark hairline outside the top edge. */
    @ColorInt
    public static int rimShadow(boolean darkGround) {
        return argb(darkGround ? RIM_SHADOW_ALPHA_DARK : RIM_SHADOW_ALPHA_LIGHT, SHADOW_RGB);
    }

    /** The light hairline outside the bottom arc. */
    @ColorInt
    public static int rimLight(boolean darkGround) {
        return argb(darkGround ? RIM_LIGHT_ALPHA_DARK : RIM_LIGHT_ALPHA_LIGHT, LIGHT_RGB);
    }

    /** How far in the inner shadow reaches in a well {@code heightPx} tall: small wells clamp it. */
    public static float shadowReachPx(float density, float heightPx) {
        return Math.max(0f, Math.min(INNER_SHADOW_REACH_DP * density,
            heightPx * INNER_SHADOW_MAX_SHARE));
    }

    /** The rings' spacing in px, never under half a pixel. */
    public static float ringStepPx(float density) {
        return Math.max(0.5f, INNER_SHADOW_STEP_DP * density);
    }

    /** How many rings draw the inner shadow over {@link #shadowReachPx}; 0 for none. */
    public static int ringCount(float density, float heightPx) {
        return (int) Math.ceil(shadowReachPx(density, heightPx) / ringStepPx(density));
    }

    /**
     * Ring {@code index} of {@code count}'s alpha (0..255), multiplying the shadow's colour: full
     * at the outline, falling off with the square of the distance in.
     */
    public static int ringAlpha(int index, int count) {
        if (count <= 0 || index < 0 || index >= count) return 0;
        float out = 1f - index / (float) count;
        return Math.round(255f * out * out);
    }

    /** The rim hairlines' width in px: small wells clamp it. */
    public static float rimWidthPx(float density, float heightPx) {
        return Math.max(0f, Math.min(RIM_WIDTH_DP * density, heightPx * RIM_MAX_SHARE));
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
