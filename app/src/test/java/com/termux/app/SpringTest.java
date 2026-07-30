package com.termux.app;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Stability of the shared spring integrator.
 *
 * <p>The point of these tests is the dropped-frame case. Semi-implicit Euler on this system is
 * stable only while {@code stiffness·step² + 2·damping·step < 4}; integrating a whole
 * {@link Spring#MAX_DT} frame in one jump puts every stiffness the app actually uses past that
 * bound, and a diverged channel never reports settled. The command palette hides its backdrop
 * blur when its channels settle, so divergence there left a dark pane sitting over the space bar
 * until something else forced a redraw.
 */
public class SpringTest {

    /** Every (stiffness, damping) pair in use, so a new one cannot be added past the bound. */
    private static final float[][] CONSTANTS_IN_USE = {
        {170f, 17f},    // dock plank tilt
        {320f, 22f},    // dock plank press
        {130f, 24f},    // dock plank glow
        {210f, 23f},    // dock plank light
        {900f, 50f},    // palette sprout
        {820f, 55f},    // palette height
    };

    private static final int FRAMES = 600;

    @Test
    public void settlesAtWorstCaseTimestepForEveryConstantInUse() {
        for (float[] constants : CONSTANTS_IN_USE) {
            assertSettles(constants[0], constants[1], Spring.MAX_DT);
            assertSettles(constants[0], constants[1], 1f / 60f);
            assertSettles(constants[0], constants[1], Spring.MIN_DT);
        }
    }

    @Test
    public void doesNotDivergeWhenEveryFrameIsADroppedFrame() {
        Spring spring = new Spring(0f, 900f, 50f);
        spring.target = 1f;
        float peak = 0f;
        for (int i = 0; i < FRAMES; i++) {
            spring.tick(false, Spring.MAX_DT);
            peak = Math.max(peak, Math.abs(spring.value));
        }
        // A diverging channel reaches absurd magnitudes within a handful of frames.
        assertTrue("value blew up to " + peak, peak < 2f);
    }

    @Test
    public void reducedMotionSnapsAndReportsSettled() {
        Spring spring = new Spring(0f, 900f, 50f);
        spring.target = 3f;
        assertFalse(spring.tick(true, Spring.MAX_DT));
        assertTrue(Math.abs(spring.value - 3f) < 1e-6f);
    }

    @Test
    public void recoversFromANonFiniteState() {
        Spring spring = new Spring(0f, 900f, 50f);
        spring.target = 1f;
        spring.vel = Float.POSITIVE_INFINITY;
        assertFalse(spring.tick(false, Spring.MAX_DT));
        assertTrue(Math.abs(spring.value - 1f) < 1e-6f);
    }

    private static void assertSettles(float stiffness, float damping, float dt) {
        Spring spring = new Spring(0f, stiffness, damping);
        spring.target = 1f;
        for (int i = 0; i < FRAMES; i++) {
            if (!spring.tick(false, dt)) return;
        }
        throw new AssertionError("k=" + stiffness + " c=" + damping + " dt=" + dt
            + " never settled; value=" + spring.value + " vel=" + spring.vel);
    }
}
