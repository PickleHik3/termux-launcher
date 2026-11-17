package com.termux.app;

/**
 * The A-Z scrub bar's wave, shared with the page switch panel: how strongly a slot is pulled up
 * by a finger some slots away. Pure.
 */
public final class ScrubWave {

    public static final float FALLOFF = 0.85f;

    private ScrubWave() {}

    /** Bell envelope of a slot {@code distanceInSlots} from the finger: 1 under it, then falling. */
    public static float envelope(float distanceInSlots) {
        return (float) Math.exp(-(distanceInSlots * distanceInSlots) * FALLOFF);
    }

    /** Sine-shaped lift 0..1 for an envelope; callers multiply by their amplitude and strength. */
    public static float lift(float envelope) {
        return (float) Math.sin(Math.min(1f, envelope) * (Math.PI * 0.5f));
    }
}
