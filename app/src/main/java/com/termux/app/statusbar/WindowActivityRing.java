package com.termux.app.statusbar;

/**
 * Timing and geometry of what a working window's chip shows while its shell works — the way
 * Windows Terminal swaps a tab's icon for a progress ring while the shell in it reports progress.
 * Indeterminate work is a breath: the whole chip brightening and dimming in the ring colour. A
 * reported percentage is a ring that fills clockwise from twelve o'clock over a faint track.
 *
 * <p>Free of Android imports, so every number here is unit-testable.
 */
public final class WindowActivityRing {

    /**
     * The breath: one slow in-and-out of the whole chip, stepped at 4 Hz. A turning arc used to mark
     * indeterminate work, and redrawing it 30 times a second cost close to half a core on the
     * device. A change of brightness hides a coarse tick far better than a change of position does,
     * and a slow breath moves little per step; the dimmest point keeps a quarter of the ring colour,
     * so the chip never reads as having stopped.
     */
    public static final long BREATHE_PERIOD_MS = 3200L;
    public static final long BREATHE_STEP_MS = 250L;
    public static final float BREATHE_FLOOR = 0.25f;

    private WindowActivityRing() {}

    /** How much of the ring colour the chip carries at {@code nowMs}, in [floor, 1]. */
    public static float breatheLevel(long nowMs) {
        double turn = Math.floorMod(nowMs, BREATHE_PERIOD_MS) / (double) BREATHE_PERIOD_MS;
        float wave = (float) (0.5 - 0.5 * Math.cos(2.0 * Math.PI * turn));
        return BREATHE_FLOOR + (1f - BREATHE_FLOOR) * wave;
    }

    /**
     * How long the bar's clock waits before its next redraw of the breathing chips: to the next
     * step boundary, so every chip steps together. The level is always a pure function of time, so
     * this only decides when a redraw is worth having. Never 0.
     */
    public static long nextBreatheTickDelayMs(long nowMs) {
        return BREATHE_STEP_MS - Math.floorMod(nowMs, BREATHE_STEP_MS);
    }

    /**
     * How much of the ring a reported percentage fills. Clamped: a shell that reports 140% has a bug,
     * and the ring should not wrap around and look nearly empty because of it.
     */
    public static float determinateSweepDeg(int percent) {
        int clamped = Math.max(0, Math.min(100, percent));
        return 360f * clamped / 100f;
    }
}
