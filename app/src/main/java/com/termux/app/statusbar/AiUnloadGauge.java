package com.termux.app.statusbar;

/**
 * The AI glyph as a gauge: how much of the idle window before the model unloads is left, drawn as
 * the robot filling from the bottom and draining from the top.
 *
 * <p>The runtime publishes only the deadline, not the window it was counted from, and the window
 * is the model's load options rather than anything this process can read. So the window is
 * whatever the deadline was ahead of the moment it was seen to move: every use of the model
 * pushes the deadline later and publishes it, and the gauge is full again from there. The first
 * deadline seen — the launcher started while a model was already counting down — has no such
 * moment, so it is measured against the idle-unload setting, or the deadline's own distance if
 * that is longer (a keep-warm running past the idle window).</p>
 */
public final class AiUnloadGauge {

    /**
     * Where the robot's ink starts and ends down its square, as fractions of its height
     * ({@code ic_symbol_smart_toy}: y 80..840 of a 960 viewport). The gauge spans the ink, not
     * the transparent margin around it, so a quarter left reads as a quarter of the robot.
     */
    public static final float ROBOT_INK_TOP = 80f / 960f;
    public static final float ROBOT_INK_BOTTOM = 840f / 960f;

    private long mDeadlineMs;
    private long mWindowMs;

    /**
     * Follows the published deadline. {@code deadlineMs} of 0 or less means no idle timer: no
     * gauge until there is one again.
     */
    public void track(long deadlineMs, long nowMs, long configuredWindowMs) {
        if (deadlineMs <= 0L) {
            mDeadlineMs = 0L;
            mWindowMs = 0L;
            return;
        }
        if (deadlineMs == mDeadlineMs) return;
        long ahead = Math.max(0L, deadlineMs - nowMs);
        mWindowMs = mDeadlineMs == 0L ? Math.max(ahead, Math.max(0L, configuredWindowMs)) : ahead;
        mDeadlineMs = deadlineMs;
    }

    /** How much of the window is left, 0..1, or a negative value while there is no gauge. */
    public float fraction(long nowMs) {
        if (mDeadlineMs <= 0L) return -1f;
        return fraction(nowMs, mDeadlineMs, mWindowMs);
    }

    /** {@code remaining / window}, held to 0..1; a window of nothing is full until the deadline. */
    public static float fraction(long nowMs, long deadlineMs, long windowMs) {
        long remaining = deadlineMs - nowMs;
        if (remaining <= 0L) return 0f;
        if (windowMs <= 0L || remaining >= windowMs) return 1f;
        return remaining / (float) windowMs;
    }

    /**
     * The first pixel row of the filled part, between the ink's top and bottom rows: the fill
     * line. Whole rows, so the icon is redrawn only when this number changes — the fill crossing
     * a pixel row — however often the clock ticks.
     */
    public static int fillTopRow(float fraction, int inkTopPx, int inkBottomPx) {
        float clamped = fraction < 0f ? 0f : Math.min(1f, fraction);
        int height = Math.max(0, inkBottomPx - inkTopPx);
        return inkBottomPx - Math.round(clamped * height);
    }
}
