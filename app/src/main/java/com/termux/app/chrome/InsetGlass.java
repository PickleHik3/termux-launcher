package com.termux.app.chrome;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;

/**
 * The glass a surface pressed into the page wears: a well that goes away from the user, where
 * every other glass here is a convex card that comes toward them. The well keeps the Look's glass
 * as it is (blur, opacity and grain are the Base look's, ADR 0031) and wears none of the convex
 * cues: no drop shadow, no raised rim, no refraction edge light, no lift, no light on top. Its
 * depth is the light of a recess lit from above:
 *
 * <ul>
 *   <li>the <b>floor</b>: a vertical shade across the whole well, darkest under the near (top)
 *   lip and clear by {@link #FLOOR_CLEAR_SHARE} of its height, with a faint light at the bottom
 *   where the light from above lands ({@link #floorColors}, {@link #FLOOR_POSITIONS}). It is the
 *   long, soft shadow of the top edge, smooth because it is one gradient;</li>
 *   <li>a <b>contact shadow</b> following the whole outline, short and strongest under the top,
 *   weaker down the sides and none at the bottom ({@link #innerShadow},
 *   {@link #innerShadowSide}), drawn as concentric rings {@link #ringStepPx} apart, each at
 *   {@link #ringAlpha} (no blur, no bitmap);</li>
 *   <li>outside the outline, the <b>rim of the hole</b>: a subtle dark hairline across the top
 *   ({@link #rimShadow}) and a light one across the bottom arc, the far edge catching the light,
 *   strongest at the centre bottom and fading up the round ends ({@link #rimLight},
 *   {@link #RIM_LIGHT_MID}, {@link #RIM_LIGHT_REACH_SHARE}).</li>
 * </ul>
 *
 * <p>On a light ground the shadows are weaker and the light rim stronger, so the dip reads on
 * dark and light wallpapers and the scheme's chrome alike. The page bar's Home pill wears it.</p>
 *
 * <p>Pure: answers colours and lengths at dress time; the shaders built from them are made on a
 * size or look change only, never per frame.</p>
 */
public final class InsetGlass {

    /** The colour the well's shadows are drawn in. */
    @ColorInt public static final int SHADOW_RGB = 0x000000;
    /** The colour of the light at the floor's bottom and on the rim's bottom arc. */
    @ColorInt public static final int LIGHT_RGB = 0xFFFFFF;

    /** The floor's shade under the top lip: about 40 % on a dark ground, 18 % on a light one. */
    public static final int FLOOR_ALPHA_DARK = 0x66;
    public static final int FLOOR_ALPHA_LIGHT = 0x2E;
    /** The faint light at the floor's bottom: about 5 % dark, 8 % light. */
    public static final int FLOOR_LIGHT_ALPHA_DARK = 0x0D;
    public static final int FLOOR_LIGHT_ALPHA_LIGHT = 0x14;
    /** Where the floor's shade is gone, as a share of the well's height: the lower part is lit. */
    public static final float FLOOR_CLEAR_SHARE = 0.7f;
    /**
     * The floor's stops down the well and the shade's share at each: a smooth fall-off from the
     * top lip (1, 0.45, 0.12) to clear, then the light at the bottom.
     */
    public static final float[] FLOOR_POSITIONS = {0f, 0.25f, 0.5f, FLOOR_CLEAR_SHARE, 1f};
    static final float[] FLOOR_SHADE = {1f, 0.45f, 0.12f, 0f, 0f};

    /** The contact shadow's alpha under the top edge: about 35 % dark, 20 % light. */
    public static final int INNER_SHADOW_ALPHA_DARK = 0x59;
    public static final int INNER_SHADOW_ALPHA_LIGHT = 0x33;
    /** How far in the contact shadow reaches, at most {@link #INNER_SHADOW_MAX_SHARE} of the well's height. */
    public static final float INNER_SHADOW_REACH_DP = 4f;
    public static final float INNER_SHADOW_MAX_SHARE = 0.2f;
    /** The contact shadow along the sides at mid height, as a share of its top strength. */
    public static final float INNER_SHADOW_SIDE_SHARE = 0.35f;
    /** The rings stand this far apart (two thirds of a pixel at 2.625); each is twice as wide, overlapping. */
    public static final float INNER_SHADOW_STEP_DP = 0.25f;

    /** The rim hairlines' width, outside the outline, at most {@link #RIM_MAX_SHARE} of the well's height. */
    public static final float RIM_WIDTH_DP = 1f;
    public static final float RIM_MAX_SHARE = 0.1f;
    /** The subtle dark hairline across the top: about 25 % dark, 15 % light; clear below {@link #RIM_SHADOW_TO_SHARE}. */
    public static final int RIM_SHADOW_ALPHA_DARK = 0x40;
    public static final int RIM_SHADOW_ALPHA_LIGHT = 0x26;
    public static final float RIM_SHADOW_TO_SHARE = 0.35f;
    /** The light hairline's alpha at the centre bottom: about 45 % dark, 85 % light. */
    public static final int RIM_LIGHT_ALPHA_DARK = 0x73;
    public static final int RIM_LIGHT_ALPHA_LIGHT = 0xD9;
    /** The light's share of its centre strength half way out along the bottom arc. */
    public static final float RIM_LIGHT_MID = 0.55f;
    /** How far up the light reaches from the bottom, as a share of the well's height. */
    public static final float RIM_LIGHT_REACH_SHARE = 0.5f;

    private InsetGlass() {}

    /** The floor's colours at {@link #FLOOR_POSITIONS}, into {@code out} (five long). */
    @NonNull
    public static int[] floorColors(boolean darkGround, @NonNull int[] out) {
        int shade = darkGround ? FLOOR_ALPHA_DARK : FLOOR_ALPHA_LIGHT;
        for (int i = 0; i < FLOOR_POSITIONS.length; i++) {
            out[i] = argb(Math.round(shade * FLOOR_SHADE[i]), SHADOW_RGB);
        }
        out[FLOOR_POSITIONS.length - 1] = argb(
            darkGround ? FLOOR_LIGHT_ALPHA_DARK : FLOOR_LIGHT_ALPHA_LIGHT, LIGHT_RGB);
        return out;
    }

    /**
     * The floor's colour at {@code share} of the well's height (0 the top, 1 the bottom), as the
     * gradient draws it: linear between the stops. A dress-pass answer (it allocates).
     */
    @ColorInt
    public static int floorAt(float share, boolean darkGround) {
        float s = Math.max(0f, Math.min(1f, share));
        int[] colors = floorColors(darkGround, new int[FLOOR_POSITIONS.length]);
        for (int i = 1; i < FLOOR_POSITIONS.length; i++) {
            if (s <= FLOOR_POSITIONS[i]) {
                float t = (s - FLOOR_POSITIONS[i - 1]) / (FLOOR_POSITIONS[i] - FLOOR_POSITIONS[i - 1]);
                return lerp(colors[i - 1], colors[i], t);
            }
        }
        return colors[colors.length - 1];
    }

    /** The contact shadow's colour under the top edge; the sides take {@link #innerShadowSide}. */
    @ColorInt
    public static int innerShadow(boolean darkGround) {
        return argb(darkGround ? INNER_SHADOW_ALPHA_DARK : INNER_SHADOW_ALPHA_LIGHT, SHADOW_RGB);
    }

    /** The contact shadow's colour along the sides at mid height; clear at the bottom. */
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

    /** The light hairline outside the bottom arc, at its centre. */
    @ColorInt
    public static int rimLight(boolean darkGround) {
        return argb(darkGround ? RIM_LIGHT_ALPHA_DARK : RIM_LIGHT_ALPHA_LIGHT, LIGHT_RGB);
    }

    /** How far in the contact shadow reaches in a well {@code heightPx} tall: small wells clamp it. */
    public static float shadowReachPx(float density, float heightPx) {
        return Math.max(0f, Math.min(INNER_SHADOW_REACH_DP * density,
            heightPx * INNER_SHADOW_MAX_SHARE));
    }

    /** The rings' spacing in px, never under half a pixel. */
    public static float ringStepPx(float density) {
        return Math.max(0.5f, INNER_SHADOW_STEP_DP * density);
    }

    /** How many rings draw the contact shadow over {@link #shadowReachPx}; 0 for none. */
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
     * The opaque ground content stands on at {@code share} of the well's height: {@code ground}
     * (opaque) under the floor there. What the glyph inks are solved and checked on.
     */
    @ColorInt
    public static int groundAt(@ColorInt int ground, boolean darkGround, float share) {
        int floor = floorAt(share, darkGround);
        float a = (floor >>> 24) / 255f;
        int red = over((floor >> 16) & 0xFF, (ground >> 16) & 0xFF, a);
        int green = over((floor >> 8) & 0xFF, (ground >> 8) & 0xFF, a);
        int blue = over(floor & 0xFF, ground & 0xFF, a);
        return 0xFF000000 | (red << 16) | (green << 8) | blue;
    }

    private static int over(int source, int under, float alpha) {
        return Math.max(0, Math.min(255, Math.round(source * alpha + under * (1f - alpha))));
    }

    /** Straight-alpha interpolation, as a gradient between two stops draws it. */
    @ColorInt
    private static int lerp(@ColorInt int from, @ColorInt int to, float t) {
        int a = Math.round((from >>> 24) + ((to >>> 24) - (from >>> 24)) * t);
        int r = Math.round(((from >> 16) & 0xFF) + (((to >> 16) & 0xFF) - ((from >> 16) & 0xFF)) * t);
        int g = Math.round(((from >> 8) & 0xFF) + (((to >> 8) & 0xFF) - ((from >> 8) & 0xFF)) * t);
        int b = Math.round((from & 0xFF) + ((to & 0xFF) - (from & 0xFF)) * t);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    @ColorInt
    private static int argb(int alpha, @ColorInt int rgb) {
        return (Math.max(0, Math.min(255, alpha)) << 24) | (rgb & 0x00FFFFFF);
    }
}
