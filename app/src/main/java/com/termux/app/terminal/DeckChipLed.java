package com.termux.app.terminal;

import androidx.annotation.ColorInt;

/**
 * What a deck chip's 5 dp LED shows (wave 8, the deck look). Every light is a state: lit on the
 * window in front; on another window lit while it wants the user, breathing between 25 % and
 * 100 % of the light while it works, dark otherwise. Pure, so the rule is JVM-tested.
 */
public final class DeckChipLed {

    /** The faintest a working LED breathes down to, as a share of the light. */
    public static final float BREATH_FLOOR = 0.25f;

    private DeckChipLed() {}

    /**
     * The LED's colour.
     *
     * @param lit       the deck's light
     * @param dark      an unlit LED: the key's foot
     * @param current   whether this is the window in front
     * @param attention whether the window wants the user (its attention mark)
     * @param working   whether the window shows the working ring
     * @param breath    the breath's level in [0, 1]; clamped to [{@link #BREATH_FLOOR}, 1]
     */
    @ColorInt
    public static int colour(@ColorInt int lit, @ColorInt int dark, boolean current,
                             boolean attention, boolean working, float breath) {
        if (current || attention) return lit;
        if (!working) return dark;
        float level = breath < BREATH_FLOOR ? BREATH_FLOOR : breath > 1f ? 1f : breath;
        return mix(dark, lit, level);
    }

    /** {@code from} toward {@code to} by {@code t} per channel, opaque result for opaque inputs. */
    @ColorInt
    static int mix(@ColorInt int from, @ColorInt int to, float t) {
        int a = channel(from >>> 24, to >>> 24, t);
        int r = channel((from >> 16) & 0xFF, (to >> 16) & 0xFF, t);
        int g = channel((from >> 8) & 0xFF, (to >> 8) & 0xFF, t);
        int b = channel(from & 0xFF, to & 0xFF, t);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private static int channel(int from, int to, float t) {
        return Math.round(from + (to - from) * t);
    }
}
