package com.termux.app.chrome;

/**
 * How the fixed darkening floors give way when a surface's own opacity is very low (the Clear
 * Look sets 2%). A sheet that never goes under 92%, a chip under 88%, an insert always a tone step
 * darker than the frame and a frost that always takes 6 off every channel were each right for a
 * dock at its usual tint, and each turned a see-through Look back into a dark slab.
 *
 * <p>One ramp serves them all: at or under {@link #CLEAR_UNDER} of opacity a floor is gone and the
 * surface is exactly as opaque as it says; from {@link #FULL_FROM} up the floor is whole, as it
 * always was; between the two it fades in linearly, so a slider drag never jumps. Pure and
 * view-free.
 */
public final class LowOpacityGlass {

    private LowOpacityGlass() {}

    /** Opacity (0..1) up to which every floor is off. */
    public static final float CLEAR_UNDER = 0.10f;

    /** Opacity (0..1) from which every floor is whole. */
    public static final float FULL_FROM = 0.25f;

    /** How much of its floor a surface at {@code opacity} (0..1) keeps: 0 when very transparent, 1 from {@link #FULL_FROM}. */
    public static float keep(float opacity) {
        if (!(opacity > CLEAR_UNDER)) return 0f;
        if (opacity >= FULL_FROM) return 1f;
        return (opacity - CLEAR_UNDER) / (FULL_FROM - CLEAR_UNDER);
    }

    /** {@code Math.max(floor, opacity)} with the floor scaled by {@link #keep}: the alpha a sheet or chip draws at. */
    public static float floored(float opacity, float floor) {
        return Math.max(floor * keep(opacity), opacity);
    }
}
