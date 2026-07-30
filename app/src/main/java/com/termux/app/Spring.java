package com.termux.app;

/**
 * A single critically-damped spring channel integrated with a clamped, substepped timestep.
 *
 * <p>Extracted from {@link DockPlankController} so every spring-driven surface in the app —
 * the dock plank, the command palette sprout — shares one integrator, one settle epsilon, and
 * one reduce-motion rule (when the animator duration scale is 0 the channel snaps to target).
 *
 * <p>The frame delta is integrated in substeps of at most {@link #MAX_STEP} rather than in one
 * jump. Semi-implicit Euler on this system is only stable while
 * {@code stiffness·step² + 2·damping·step < 4}, which for a whole dropped frame
 * ({@link #MAX_DT}) caps stiffness near 300 — soft enough that every surface in the app would
 * have to crawl. Substepping decouples the two: stiffness is bounded by the substep, so a slow
 * frame costs extra iterations instead of blowing the channel up. It matters more than it
 * sounds, because a diverged channel never reports settled, and a caller that hides itself on
 * settle would then never hide.
 */
public final class Spring {

    public static final float MIN_DT = 1f / 120f;
    public static final float MAX_DT = 1f / 30f;
    /** Largest step the integrator is stable over for the stiffnesses this app uses. */
    public static final float MAX_STEP = 1f / 120f;
    public static final float SETTLE_EPSILON = 4e-4f;

    public float value;
    public float target;
    public float vel;
    public final float stiffness;
    public final float damping;

    public Spring(float init, float stiffness, float damping) {
        this.value = init;
        this.target = init;
        this.stiffness = stiffness;
        this.damping = damping;
    }

    public void reset(float v) {
        value = v;
        target = v;
        vel = 0f;
    }

    /** Clamps a raw frame delta into the range the integrator stays stable over. */
    public static float clampDelta(float dt) {
        return Math.max(MIN_DT, Math.min(MAX_DT, dt));
    }

    /** @return true if the spring is still in motion and needs another frame. */
    public boolean tick(boolean reduced, float dt) {
        if (reduced) {
            value = target;
            vel = 0f;
            return false;
        }
        int steps = Math.max(1, (int) Math.ceil(dt / MAX_STEP));
        float step = dt / steps;
        for (int i = 0; i < steps; i++) {
            float accel = stiffness * (target - value) - damping * vel;
            vel += accel * step;
            value += vel * step;
        }
        // Belt and braces: a caller that hides itself once every channel settles must never be
        // held open by a channel that went non-finite.
        if (Float.isNaN(value) || Float.isInfinite(value)
            || Float.isNaN(vel) || Float.isInfinite(vel)) {
            value = target;
            vel = 0f;
            return false;
        }
        return Math.abs(target - value) > SETTLE_EPSILON || Math.abs(vel) > SETTLE_EPSILON;
    }
}
