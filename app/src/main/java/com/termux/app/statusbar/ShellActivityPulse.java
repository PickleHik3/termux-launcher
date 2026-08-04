package com.termux.app.statusbar;

/**
 * The clock behind the one "working" surface this fork has: the rim of a window pill whose shell is
 * busy. A single breath drives both the rim's brightness and its weight, so the pill looks like it is
 * breathing rather than like two properties animating at once.
 *
 * <p>Deliberately a relative of the keybind-hint breath on the in-app keyboard — the same idea of a
 * slow swell rather than a spinner — and deliberately not the same curve: this one is faster and cuts
 * deeper, because it has one thin outline to be noticed on rather than a whole keyboard of caps.
 *
 * <p>Free of Android imports, so every curve here is unit-testable.
 */
public final class ShellActivityPulse {

    /** One full breath. Slow enough to read as breathing rather than as a blink. */
    public static final long CYCLE_MS = 2200L;

    /** The rim never goes out entirely: an unlit rim would read as the pill losing its border. */
    private static final float RIM_FLOOR = 0.28f;

    private ShellActivityPulse() {}

    /** Position in the breath, in [0, 1). */
    public static float phase(long elapsedMs) {
        float cycles = elapsedMs / (float) CYCLE_MS;
        float phase = cycles - (float) Math.floor(cycles);
        return phase < 0f ? phase + 1f : phase;
    }

    /**
     * Strength of the rim at {@code phase}, in {@code [RIM_FLOOR, 1]}, peaking at the start of the
     * breath. A cosine rather than a triangle: the turn at each end has to be smooth, or the swell
     * reads as two linear ramps meeting at a corner.
     */
    public static float rimWeight(float phase) {
        double cosine = Math.cos(2 * Math.PI * phase);
        float lit = (float) (0.5 + 0.5 * cosine);
        return RIM_FLOOR + (1f - RIM_FLOOR) * lit;
    }

    /** A rim that never dims far, so a window waiting on the user does not fade out between breaths. */
    private static final float GLOW_FLOOR = 0.55f;

    /**
     * Strength of the attention rim at {@code phase}, in {@code [GLOW_FLOOR, 1]}. Shares the working
     * rim's period so the two never beat against each other in one bar, and sharpens the same cosine
     * so the peak arrives as a flare rather than a swell: this state is a request, not a status.
     */
    public static float glowWeight(float phase) {
        double cosine = Math.cos(2 * Math.PI * phase);
        float lit = (float) (0.5 + 0.5 * cosine);
        // Squaring narrows the bright part of the cycle without moving where the peak lands.
        float flared = lit * lit;
        return GLOW_FLOOR + (1f - GLOW_FLOOR) * flared;
    }
}
