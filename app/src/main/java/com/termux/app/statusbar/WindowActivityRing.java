package com.termux.app.statusbar;

/**
 * Geometry of the progress ring a working window's pill draws where its process glyph normally sits
 * — the way Windows Terminal swaps a tab's icon for a progress ring while the shell in it reports
 * progress. Indeterminate work is an arc that turns; a reported percentage is a ring that fills
 * clockwise from twelve o'clock over a faint track.
 *
 * <p>Free of Android imports, so every angle here is unit-testable.
 */
public final class WindowActivityRing {

    /** One full turn of the indeterminate arc. Quick enough to read as working, not as a clock. */
    public static final long SPIN_MS = 1280L;

    /**
     * The indeterminate arc, in degrees. Open enough that the turn is visible at glyph size, closed
     * enough that the shape still reads as a ring rather than a comma.
     */
    public static final float INDETERMINATE_SWEEP_DEG = 270f;

    /** Twelve o'clock, in {@code Canvas.drawArc} degrees (which count clockwise from three). */
    public static final float START_DEG = -90f;

    private WindowActivityRing() {}

    /** Position in the turn, in [0, 1). */
    public static float phase(long elapsedMs) {
        float turns = elapsedMs / (float) SPIN_MS;
        float phase = turns - (float) Math.floor(turns);
        return phase < 0f ? phase + 1f : phase;
    }

    /**
     * How many positions the arc visits per turn in lazy mode, and how often it moves. Eight stops
     * at ~6 Hz reads as a spinner — the CLI kind — at a fraction of the cost of one frame per vsync,
     * which is what lazy mode exists to avoid; a ring that did not move at all read as stuck.
     */
    public static final int LAZY_STEPS = 8;
    public static final long LAZY_TICK_MS = SPIN_MS / LAZY_STEPS;

    /** {@code phase} quantised to {@code steps} equal stops per turn, so the arc jumps rather than glides. */
    public static float steppedPhase(float phase, int steps) {
        int safeSteps = Math.max(1, steps);
        float clamped = Math.max(0f, Math.min(0.999999f, phase));
        return (float) Math.floor(clamped * safeSteps) / safeSteps;
    }

    /** Where the indeterminate arc starts at {@code phase}: one full clockwise turn per cycle. */
    public static float indeterminateStartDeg(float phase) {
        return START_DEG + 360f * phase;
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
