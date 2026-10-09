package com.termux.app.wall;

/**
 * The weight a held border gives its page: when the hold claims the finger the page sinks away
 * from the viewer — a little smaller, a little darker — and stays down through the drag, then
 * springs back up with a small overshoot when the finger lets go. Every mode has it; the plank's
 * tip ({@link PlankTilt}) rides on the same number under Fancier Glass.
 *
 * <p>The motion is one number, the sink: 0 at rest, 1 fully down. It moves on a damped spring
 * (unit mass, starting still), so the way down lands with weight and the way up overshoots rest a
 * hair before it settles — a sink a little below 0, which reads as the page rising a hair past
 * flush. Pure, so the curve and the numbers it drives can be tested without a view.</p>
 */
public final class PageSink {

    /** How small a fully sunk page is drawn, about its centre. */
    public static final float SCALE = 0.94f;

    /** How much of its brightness a fully sunk page loses. */
    public static final float DIM = 0.14f;

    /** The way down: firm and nearly critical, so it lands with weight and a trace of give. */
    public static final float SINK_STIFFNESS = 380f;
    public static final float SINK_DAMPING = 0.72f;

    /** The way up: softer and looser, so it rises a little past flush before it settles. */
    public static final float RISE_STIFFNESS = 320f;
    public static final float RISE_DAMPING = 0.55f;

    /** How close to its target, as a fraction of the way, the spring counts as settled. */
    private static final float SETTLED_FRACTION = 0.005f;

    private PageSink() {}

    /**
     * Where a spring of {@code stiffness} (per second squared) and {@code damping} (the damping
     * ratio, below 1) that started still at {@code from} stands {@code elapsedMs} later on its way
     * to {@code to}.
     */
    public static float value(float from, float to, long elapsedMs, float stiffness,
                              float damping) {
        if (elapsedMs <= 0L) return from;
        float zeta = clampDamping(damping);
        double omega = Math.sqrt(Math.max(1f, stiffness));
        double decay = zeta * omega;
        double omegaD = omega * Math.sqrt(1.0 - zeta * zeta);
        double t = elapsedMs / 1000.0;
        double x = Math.exp(-decay * t)
            * (Math.cos(omegaD * t) + decay / omegaD * Math.sin(omegaD * t));
        return (float) (to + (from - to) * x);
    }

    /** How long that spring takes to come within {@link #SETTLED_FRACTION} of its target. */
    public static long durationMs(float stiffness, float damping) {
        float zeta = clampDamping(damping);
        double omega = Math.sqrt(Math.max(1f, stiffness));
        double decay = zeta * omega;
        double omegaD = omega * Math.sqrt(1.0 - zeta * zeta);
        double envelope = Math.sqrt(1.0 + (decay / omegaD) * (decay / omegaD));
        return Math.round(1000.0 * Math.log(envelope / SETTLED_FRACTION) / decay);
    }

    /** The page's scale for a sink: overshoot past rest draws it a hair larger than flush. */
    public static float scale(float sink) {
        return 1f - (1f - SCALE) * sink;
    }

    /** The fraction of brightness the page loses for a sink; never brighter than at rest. */
    public static float dim(float sink) {
        return DIM * Math.max(0f, Math.min(1f, sink));
    }

    /**
     * The same dim as a colour-filter multiplier ({@code 0xRRGGBB}, each channel the brightness
     * kept), quantised to {@code steps} levels so a view can cache one filter per level.
     *
     * @return the level, from 0 (no dim) to {@code steps - 1} (fully sunk)
     */
    public static int dimLevel(float sink, int steps) {
        if (steps <= 1) return 0;
        float fraction = Math.max(0f, Math.min(1f, sink));
        return Math.round(fraction * (steps - 1));
    }

    /** The multiplier for {@code level} of {@code steps}: grey at the brightness that level keeps. */
    public static int dimMultiplier(int level, int steps) {
        float fraction = steps <= 1 ? 0f : Math.max(0f, Math.min(1f, level / (float) (steps - 1)));
        int channel = Math.round(255f * (1f - DIM * fraction));
        return (channel << 16) | (channel << 8) | channel;
    }

    private static float clampDamping(float damping) {
        return Math.max(0.05f, Math.min(0.95f, damping));
    }
}
