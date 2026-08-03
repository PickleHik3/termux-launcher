package com.termux.app.statusbar;

/**
 * The one clock both "working" surfaces read: the status row's dots and the window pill's sweeping
 * underline. Sharing a phase is what makes them animate as one system rather than two coincidental
 * loops at slightly different rates.
 *
 * <p>Free of Android imports, so every curve here is unit-testable.
 */
public final class ShellActivityPulse {

    /** One full loop. Slow enough to read as breathing rather than as a spinner. */
    public static final long CYCLE_MS = 1400L;
    public static final int DOT_COUNT = 3;
    /** Fraction of the pill's width the underline covers. */
    public static final float SWEEP_WIDTH_FRACTION = 0.40f;
    /** Dots never go fully dark: an unlit dot would read as a gap in the row. */
    private static final float DOT_FLOOR = 0.35f;

    private ShellActivityPulse() {}

    /** Position in the loop, in [0, 1). */
    public static float phase(long elapsedMs) {
        float cycles = elapsedMs / (float) CYCLE_MS;
        float phase = cycles - (float) Math.floor(cycles);
        return phase < 0f ? phase + 1f : phase;
    }

    /**
     * Brightness of dot {@code index} at {@code phase}, in [0, 1]. Each dot peaks at its own point in
     * the loop, so the row reads as motion travelling along it rather than as three dots blinking
     * together.
     */
    public static float dotWeight(int index, float phase) {
        float peak = (index % DOT_COUNT) / (float) DOT_COUNT;
        double cosine = Math.cos(2 * Math.PI * (phase - peak));
        float lit = (float) (0.5 + 0.5 * cosine);
        return DOT_FLOOR + (1f - DOT_FLOOR) * lit;
    }

    /**
     * Left edge of the underline as a fraction of the pill's width, in
     * {@code [0, 1 - SWEEP_WIDTH_FRACTION]} — so the sweep can never reach past either end of the
     * pill it belongs to.
     *
     * <p>A triangle rather than a saw: the underline travels out and back, which is continuous across
     * the wrap. A saw would snap from the far end to the near end once a cycle, and that snap reads
     * as a dropped frame.
     */
    public static float sweepStartFraction(float phase) {
        float wrapped = phase - (float) Math.floor(phase);
        float triangle = wrapped < 0.5f ? wrapped * 2f : (1f - wrapped) * 2f;
        return (1f - SWEEP_WIDTH_FRACTION) * triangle;
    }
}
