package com.termux.app;

/**
 * The A-Z scrub bar's wave, shared with the page bar: how strongly a slot is pulled up by a
 * finger some slots away, how far the lift carries and how long it takes to settle. Pure.
 */
public final class ScrubWave {

    public static final float FALLOFF = 0.85f;
    /** The wave's lift at full strength, in dp. */
    public static final int LIFT_DP = 15;
    /** The letter under the finger rises this much further than the wave carries it. */
    public static final float FOCUS_LIFT_GAIN = 1.2f;
    /** The wave settling back to rest once the finger leaves. */
    public static final long SETTLE_MS = 165L;

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
