package com.termux.app.chrome.scene;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import org.junit.Test;

/** The pure parts of the scene clock, the still's size and the thumbnail's render scale. */
public class SceneClockTest {

    @Test
    public void firstStepAfterARestartAddsNothing() {
        assertEquals(0d, DesignSceneProgram.advance(0d, Long.MIN_VALUE, 123_456L, 1f), 0d);
    }

    @Test
    public void stepsScaleBySpeed() {
        assertEquals(100d, DesignSceneProgram.advance(0d, 1000L, 1100L, 1f), 1e-9);
        assertEquals(200d, DesignSceneProgram.advance(0d, 1000L, 1100L, 2f), 1e-9);
        assertEquals(25d, DesignSceneProgram.advance(0d, 1000L, 1100L, 0.25f), 1e-9);
    }

    @Test
    public void speedChangeDoesNotJumpTheTotal() {
        double t = DesignSceneProgram.advance(0d, 0L, 1000L, 1f);      // 1000 ms at 1x
        double same = DesignSceneProgram.advance(t, 1000L, 1000L, 2f); // speed changes, no time passed
        assertEquals(t, same, 0d);
        double next = DesignSceneProgram.advance(same, 1000L, 1100L, 2f);
        assertEquals(1200d, next, 1e-9);
    }

    @Test
    public void backwardsAndStalledStepsAddNothing() {
        assertEquals(50d, DesignSceneProgram.advance(50d, 2000L, 2000L, 1f), 0d);
        assertEquals(50d, DesignSceneProgram.advance(50d, 2000L, 1000L, 1f), 0d);
    }

    @Test
    public void aLongGapCountsAtMostTheStepCap() {
        assertEquals(DesignSceneProgram.MAX_STEP_MS,
            DesignSceneProgram.advance(0d, 0L, 3_600_000L, 1f), 1e-9);
    }

    @Test
    public void stillSizeKeepsAspectAndCaps() {
        assertArrayEquals(new int[] {1080, 2400}, SceneStill.fitSize(1080, 2400));
        assertArrayEquals(new int[] {1, 1}, SceneStill.fitSize(0, -5));
        int[] big = SceneStill.fitSize(8192, 4096);
        assertEquals(SceneStill.MAX_SIDE_PX, big[0]);
        assertEquals(SceneStill.MAX_SIDE_PX / 2, big[1]);
    }

    @Test
    public void thumbRendersAtMostAtTheCap() {
        assertEquals(1f, SceneThumbView.renderScale(200, 300), 0f);
        assertEquals(0.5f, SceneThumbView.renderScale(960, 480), 1e-6f);
    }
}
