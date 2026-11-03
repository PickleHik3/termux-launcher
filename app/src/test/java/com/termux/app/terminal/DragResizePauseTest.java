package com.termux.app.terminal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * A divider or corner drag resizes its panes when the finger has been still 100 ms, not while it
 * moves, and once more at the lift only if it moved since that pause (ADR 0019).
 */
public class DragResizePauseTest {

    private static final float SLOP = 8f;

    @Test public void aMovingFingerKeepsTheHold() {
        DragResizePause pause = new DragResizePause(SLOP);
        pause.start(0f, 0f, 0L);
        long t = 0L;
        for (int i = 1; i <= 20; i++) {
            t += 16L;
            assertEquals(DragResizePause.MOVE_REARM, pause.onMove(i * 20f, 0f, t));
            // A timer armed 100 ms before a later move finds the finger still moving.
            assertFalse(pause.onTimer(t + 50L));
        }
        assertTrue(pause.isHeld());
    }

    @Test public void stillFor100msAppliesOnceAndTheNextMoveHoldsAgain() {
        DragResizePause pause = new DragResizePause(SLOP);
        pause.start(0f, 0f, 0L);
        assertEquals(DragResizePause.MOVE_REARM, pause.onMove(50f, 0f, 10L));
        // Jitter inside the slop does not restart the pause.
        assertEquals(DragResizePause.MOVE_STILL, pause.onMove(53f, 2f, 60L));
        assertFalse("not yet 100 ms", pause.onTimer(109L));
        assertTrue(pause.onTimer(110L));
        assertFalse("applies once per pause", pause.onTimer(300L));
        assertFalse(pause.isHeld());
        assertEquals(DragResizePause.MOVE_STILL, pause.onMove(55f, 0f, 320L));
        assertEquals(DragResizePause.MOVE_HOLD_AGAIN, pause.onMove(90f, 0f, 340L));
        assertTrue(pause.isHeld());
        assertTrue(pause.onTimer(440L));
    }

    @Test public void theLiftAppliesOnlyIfTheFingerMovedSinceThePause() {
        DragResizePause pause = new DragResizePause(SLOP);
        pause.start(0f, 0f, 0L);
        pause.onMove(40f, 0f, 16L);
        assertTrue(pause.onTimer(116L));
        assertFalse("still where it paused: nothing changed", pause.onRelease());

        pause.start(0f, 0f, 0L);
        pause.onMove(40f, 0f, 16L);
        assertTrue(pause.onTimer(116L));
        pause.onMove(80f, 0f, 200L);
        assertTrue("moved since the pause", pause.onRelease());
    }

    @Test public void aDragReleasedWithoutAPauseAppliesAtTheLift() {
        DragResizePause pause = new DragResizePause(SLOP);
        pause.start(0f, 0f, 0L);
        pause.onMove(40f, 0f, 16L);
        assertTrue(pause.onRelease());
        assertFalse("after the lift nothing applies", pause.onTimer(1000L));
        assertEquals(DragResizePause.MOVE_STILL, pause.onMove(100f, 0f, 1000L));
    }

    @Test public void theTimerOnlyEverActsDuringADrag() {
        // Keyboard, resize keys and focus grow never start one: a timer firing then is inert.
        DragResizePause pause = new DragResizePause(SLOP);
        assertFalse(pause.onTimer(10_000L));
        assertFalse(pause.onRelease());
        assertEquals(100L, DragResizePause.PAUSE_MS);
    }
}
