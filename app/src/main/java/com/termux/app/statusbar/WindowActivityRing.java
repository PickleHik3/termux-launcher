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
     * How many positions the smooth arc visits per turn, and how often it moves. The arc used to be
     * driven one step per vsync, which on a 120 Hz panel is 154 redraws of every working pill per
     * turn for a ring a few pixels wide; 38 stops is past the point where a thin arc reads as
     * gliding, and lands the tick at about 30 a second. The angle itself stays a pure function of
     * elapsed time, so a slower tick changes how many positions the arc visits, never its speed.
     */
    public static final int SMOOTH_STEPS = 38;
    public static final long SMOOTH_TICK_MS = SPIN_MS / SMOOTH_STEPS;

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
