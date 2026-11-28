package com.termux.app.tiling.navigation;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** Navigation mode's one finger (#433): a pan, a tap or a long press that picks a window up. */
public class NavigationGestureTest {

    private static final float SLOP = 10f;
    private static final long LONG_PRESS = 400L;

    private static NavigationGesture gesture() {
        return new NavigationGesture(SLOP, LONG_PRESS);
    }

    @Test
    public void aFingerThatStaysStillAndLiftsIsATap() {
        NavigationGesture g = gesture();
        g.down(100f, 100f, 0L);
        assertEquals(NavigationGesture.Phase.PENDING, g.move(104f, 103f, 50L));
        assertEquals(NavigationGesture.Outcome.TAP, g.up(104f, 103f, 120L));
        assertEquals(NavigationGesture.Phase.IDLE, g.phase());
    }

    @Test
    public void aFingerThatMovesPastTheSlopPansAndItsLiftSettles() {
        NavigationGesture g = gesture();
        g.down(100f, 100f, 0L);
        assertEquals(NavigationGesture.Phase.PANNING, g.move(100f, 120f, 30L));
        assertEquals(NavigationGesture.Outcome.SETTLE, g.up(100f, 160f, 90L));
    }

    @Test
    public void thePanReportsEachStepFromWhereTheLastLeftOff() {
        NavigationGesture g = gesture();
        g.down(100f, 100f, 0L);
        g.move(130f, 100f, 10L);
        // The first step counts from the finger's landing, so no part of the slide is lost.
        assertEquals(30f, g.stepX(), 0.001f);
        assertEquals(0f, g.stepY(), 0.001f);
        g.move(125f, 140f, 20L);
        assertEquals(-5f, g.stepX(), 0.001f);
        assertEquals(40f, g.stepY(), 0.001f);
    }

    @Test
    public void aStepInsideTheSlopMovesNothing() {
        NavigationGesture g = gesture();
        g.down(100f, 100f, 0L);
        g.move(105f, 100f, 10L);
        assertEquals(0f, g.stepX(), 0.001f);
        assertEquals(0f, g.stepY(), 0.001f);
    }

    @Test
    public void aLongPressOnAWindowPicksItUpAndItsLiftDrops() {
        NavigationGesture g = gesture();
        g.down(100f, 100f, 0L);
        assertFalse(g.longPress(LONG_PRESS - 1, true));
        assertTrue(g.longPress(LONG_PRESS, true));
        assertEquals(NavigationGesture.Phase.CARRYING, g.phase());
        assertEquals(NavigationGesture.Phase.CARRYING, g.move(300f, 500f, 600L));
        assertEquals(NavigationGesture.Outcome.DROP, g.up(300f, 500f, 700L));
    }

    @Test
    public void aLongPressOffAnyWindowPicksNothingUp() {
        NavigationGesture g = gesture();
        g.down(100f, 100f, 0L);
        assertFalse(g.longPress(LONG_PRESS, false));
        assertEquals(NavigationGesture.Phase.PENDING, g.phase());
        // Still a tap when it lifts in place.
        assertEquals(NavigationGesture.Outcome.TAP, g.up(100f, 100f, 900L));
    }

    @Test
    public void aPanThatHasBegunIsNeverPickedUp() {
        NavigationGesture g = gesture();
        g.down(100f, 100f, 0L);
        g.move(100f, 140f, 50L);
        assertFalse(g.longPress(LONG_PRESS, true));
        assertEquals(NavigationGesture.Phase.PANNING, g.phase());
    }

    @Test
    public void aCarriedWindowNeverPans() {
        NavigationGesture g = gesture();
        g.down(100f, 100f, 0L);
        g.longPress(LONG_PRESS, true);
        g.move(300f, 100f, 500L);
        assertEquals(0f, g.stepX(), 0.001f);
        assertEquals(0f, g.stepY(), 0.001f);
    }

    @Test
    public void aCancelSettlesAPanAndPutsACarriedWindowBack() {
        NavigationGesture pan = gesture();
        pan.down(0f, 0f, 0L);
        pan.move(0f, 50f, 10L);
        assertEquals(NavigationGesture.Outcome.SETTLE, pan.cancel());

        NavigationGesture carry = gesture();
        carry.down(0f, 0f, 0L);
        carry.longPress(LONG_PRESS, true);
        assertEquals(NavigationGesture.Outcome.CANCEL, carry.cancel());

        NavigationGesture still = gesture();
        still.down(0f, 0f, 0L);
        assertEquals(NavigationGesture.Outcome.NONE, still.cancel());
        assertEquals(NavigationGesture.Phase.IDLE, still.phase());
    }

    @Test
    public void nothingHappensWithoutAFingerDown() {
        NavigationGesture g = gesture();
        assertEquals(NavigationGesture.Phase.IDLE, g.move(10f, 10f, 0L));
        assertFalse(g.longPress(LONG_PRESS, true));
        assertEquals(NavigationGesture.Outcome.NONE, g.up(10f, 10f, 0L));
    }

    @Test
    public void theLongPressIsDueItsTimeAfterTheFingerLands() {
        NavigationGesture g = gesture();
        g.down(0f, 0f, 1000L);
        assertEquals(1000L + LONG_PRESS, g.longPressAt());
    }
}
