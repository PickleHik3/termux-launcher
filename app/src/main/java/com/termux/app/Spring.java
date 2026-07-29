package com.termux.app;

/**
 * A single critically-damped spring channel integrated with a clamped timestep.
 *
 * <p>Extracted from {@link DockPlankController} so every spring-driven surface in the app —
 * the dock plank, the command palette sprout — shares one integrator, one settle epsilon, and
 * one reduce-motion rule (when the animator duration scale is 0 the channel snaps to target).
 */
public final class Spring {

    public static final float MIN_DT = 1f / 120f;
    public static final float MAX_DT = 1f / 30f;
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
        float accel = stiffness * (target - value) - damping * vel;
        vel += accel * dt;
        value += vel * dt;
        return Math.abs(target - value) > SETTLE_EPSILON || Math.abs(vel) > SETTLE_EPSILON;
    }
}
