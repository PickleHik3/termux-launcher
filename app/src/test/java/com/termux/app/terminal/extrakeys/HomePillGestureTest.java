package com.termux.app.terminal.extrakeys;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class HomePillGestureTest {

    private final HomePillGesture g = new HomePillGesture(10f, 400L);

    @Test
    public void aHoldInsideTheSlopPicksUpAndAReleaseDrops() {
        g.down(100f, 100f);
        assertEquals(HomePillGesture.Move.NONE, g.move(103f, 104f));
        assertEquals(HomePillGesture.Hold.PICK_UP, g.holdElapsed());
        assertTrue(g.isPickedUp());
        assertEquals(HomePillGesture.Up.DROP, g.up());
        assertFalse(g.isPickedUp());
    }

    @Test
    public void aMovePastTheSlopBeforeTheHoldHandsTheStreamBack() {
        g.down(100f, 100f);
        assertEquals(HomePillGesture.Move.CANCEL_HOLD, g.move(100f, 120f));
        assertEquals(HomePillGesture.Move.NONE, g.move(100f, 140f));
        assertEquals(HomePillGesture.Hold.NONE, g.holdElapsed());
        assertEquals(HomePillGesture.Up.NONE, g.up());
    }

    @Test
    public void movesAfterThePickUpDragFromWhereItWasPickedUp() {
        g.down(100f, 100f);
        g.move(104f, 103f);
        g.holdElapsed();
        assertEquals(HomePillGesture.Move.DRAG, g.move(154f, 203f));
        assertEquals(50f, g.dragDx(), 0.001f);
        assertEquals(100f, g.dragDy(), 0.001f);
        assertEquals(HomePillGesture.Move.DRAG, g.move(84f, 103f));
        assertEquals(-20f, g.dragDx(), 0.001f);
        assertEquals(0f, g.dragDy(), 0.001f);
    }

    @Test
    public void aHoldAfterTheFingerLeftDoesNothing() {
        g.down(1f, 1f);
        assertEquals(HomePillGesture.Up.NONE, g.up());
        assertEquals(HomePillGesture.Hold.NONE, g.holdElapsed());
    }

    @Test
    public void aTapNeverDrops() {
        g.down(1f, 1f);
        assertEquals(HomePillGesture.Up.NONE, g.up());
    }

    @Test
    public void cancelEndsTheStreamAndAHoldAfterItDoesNothing() {
        g.down(1f, 1f);
        g.holdElapsed();
        g.cancel();
        assertFalse(g.isPickedUp());
        assertEquals(HomePillGesture.Up.NONE, g.up());
        assertEquals(HomePillGesture.Hold.NONE, g.holdElapsed());
    }

    @Test
    public void theHoldTimeIsTheViewsOwn() {
        assertEquals(400L, g.holdMillis());
    }
}
