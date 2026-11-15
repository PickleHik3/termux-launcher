package com.termux.app.wall.switcher;

/**
 * The timing of the picker's one-shot opening: each element runs its own window of
 * {@link #OPEN_MS}, starting {@link #STAGGER_MS} after the one before it. Pure, so the view only
 * asks "how far along is element n at this time".
 */
public final class PickerMotion {

    public static final long OPEN_MS = 220L;
    public static final long STAGGER_MS = 30L;
    public static final long CLOSE_MS = 100L;
    /** An element's alpha reaches full in this share of its window. */
    public static final float ALPHA_SHARE = 0.7f;

    private PickerMotion() {
    }

    /** The whole opening: one window plus the stagger of the last element. */
    public static long totalOpenMs(int largestStaggerIndex) {
        return OPEN_MS + STAGGER_MS * Math.max(0, largestStaggerIndex);
    }

    /** How far through its own window element {@code staggerIndex} is, 0..1, linear. */
    public static float windowProgress(long elapsedMs, int staggerIndex) {
        float t = (elapsedMs - STAGGER_MS * Math.max(0, staggerIndex)) / (float) OPEN_MS;
        return Math.min(1f, Math.max(0f, t));
    }

    /** The alpha share of an element whose window progress is {@code linear}. */
    public static float alphaOf(float linear) {
        return Math.min(1f, Math.max(0f, linear / ALPHA_SHARE));
    }
}
