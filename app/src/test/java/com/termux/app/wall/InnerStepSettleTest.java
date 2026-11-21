package com.termux.app.wall;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** A released step between windows carries on at the finger's speed and still lands. */
public class InnerStepSettleTest {

    @Test
    public void startsWhereAndAsFastAsTheReleaseLeftIt() {
        // herdr -> home on pong: arriving page 529 px left of rest, wall moving right.
        SettleSpring spring = PaneWallPolicy.innerStepSettle(-529f, 3413f, 1f);
        assertEquals(-529f, spring.displacementAt(0f), 0.01f);
        assertEquals(3413f, spring.velocityAt(0f), 1f);
    }

    @Test
    public void aThrowFasterThanTheClampStartsAtTheClamp() {
        SettleSpring spring = PaneWallPolicy.innerStepSettle(400f, -30000f, 1f);
        assertEquals(-SettleSpring.PAGE_MAX_VELOCITY_PX_PER_SEC, spring.velocityAt(0f), 1f);
    }

    @Test
    public void landsWithinABoundAtEverySpeed() {
        float[] velocities = {0f, 1000f, 3413f, 8273f, 12000f};
        for (float v : velocities) {
            for (float from : new float[] {-1080f, -529f, -160f}) {
                SettleSpring spring = PaneWallPolicy.innerStepSettle(from, v, 1f);
                long ms = spring.durationMs(SettleSpring.PAGE_REST_PX);
                assertTrue("from " + from + " at " + v + " took " + ms, ms > 0 && ms <= 800L);
                assertTrue(Math.abs(spring.displacementAt(ms / 1000f)) <= 1f);
            }
        }
    }

    @Test
    public void aSlowerAnimationSpeedStillStartsAtTheReleaseSpeed() {
        SettleSpring spring = PaneWallPolicy.innerStepSettle(-529f, 3413f, 2f);
        assertEquals(3413f, spring.velocityAt(0f), 1f);
    }
}
