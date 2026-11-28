package com.termux.app.tiling.navigation;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** A carried window held at the screen's edge keeps the view moving (#433). */
public class NavigationEdgePanTest {

    private static final float BAND = 100f;
    private static final float SPEED = 2f;
    private static final long DWELL = 200L;

    private final float[] out = new float[2];

    private boolean step(NavigationEdgePan pan, float x, float y, long now) {
        return pan.step(x, y, 0f, 0f, 1000f, 2000f, now, out);
    }

    private static NavigationEdgePan pan() {
        return new NavigationEdgePan(BAND, SPEED, DWELL);
    }

    @Test
    public void awayFromTheEdgesNothingMoves() {
        NavigationEdgePan pan = pan();
        assertFalse(step(pan, 500f, 1000f, 0L));
        assertEquals(0f, out[0], 0.001f);
        assertEquals(0f, out[1], 0.001f);
    }

    @Test
    public void theEdgeWaitsItsDwellBeforeMoving() {
        NavigationEdgePan pan = pan();
        assertTrue(step(pan, 990f, 1000f, 0L));
        assertEquals(0f, out[0], 0.001f);
        assertTrue(step(pan, 990f, 1000f, DWELL - 1));
        assertEquals(0f, out[0], 0.001f);
        assertTrue(step(pan, 990f, 1000f, DWELL + 16));
        // 90% into the band: 90% of the speed, for the frame's 17 ms.
        assertEquals(0.9f * SPEED * 17f, out[0], 0.01f);
        assertEquals(0f, out[1], 0.001f);
    }

    @Test
    public void eachEdgeMovesItsOwnWay() {
        NavigationEdgePan pan = pan();
        step(pan, 0f, 1000f, 0L);
        step(pan, 0f, 1000f, DWELL);
        step(pan, 0f, 1000f, DWELL + 10);
        assertEquals(-SPEED * 10f, out[0], 0.01f);

        NavigationEdgePan top = pan();
        step(top, 500f, 0f, 0L);
        step(top, 500f, 0f, DWELL);
        step(top, 500f, 0f, DWELL + 10);
        assertEquals(-SPEED * 10f, out[1], 0.01f);

        NavigationEdgePan bottom = pan();
        step(bottom, 500f, 1950f, 0L);
        step(bottom, 500f, 1950f, DWELL);
        step(bottom, 500f, 1950f, DWELL + 10);
        assertEquals(0.5f * SPEED * 10f, out[1], 0.01f);
    }

    @Test
    public void leavingTheEdgeStopsAndStartsTheDwellAgain() {
        NavigationEdgePan pan = pan();
        step(pan, 990f, 1000f, 0L);
        step(pan, 990f, 1000f, DWELL + 10);
        assertFalse(step(pan, 500f, 1000f, DWELL + 20));
        assertTrue(step(pan, 990f, 1000f, DWELL + 30));
        assertEquals(0f, out[0], 0.001f);
    }

    @Test
    public void aLongFrameMovesNoMoreThanAShortOne() {
        NavigationEdgePan pan = pan();
        step(pan, 1000f, 1000f, 0L);
        step(pan, 1000f, 1000f, DWELL);
        step(pan, 1000f, 1000f, DWELL + 5000L);
        assertEquals(SPEED * NavigationEdgePan.MAX_FRAME_MS, out[0], 0.01f);
    }

    @Test
    public void resetForgetsTheDwell() {
        NavigationEdgePan pan = pan();
        step(pan, 990f, 1000f, 0L);
        pan.reset();
        step(pan, 990f, 1000f, DWELL);
        assertEquals(0f, out[0], 0.001f);
    }
}
