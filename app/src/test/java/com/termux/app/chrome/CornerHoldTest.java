package com.termux.app.chrome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Who owns a finger that lands in a pane corner. The square is the program's until the hold fires,
 * so most of this is about the ways the corner gives the gesture up without ever taking it.
 */
public class CornerHoldTest {

    private static final float HOLD_SLOP = 24f;
    private static final float DRAG_SLOP = 6f;

    private CornerHold down(boolean atSeam) {
        CornerHold hold = new CornerHold();
        hold.down(100f, 200f, HOLD_SLOP, DRAG_SLOP, atSeam);
        return hold;
    }

    // ------------------------------------------------------------------ before the hold

    @Test
    public void aFingerDownGivesTheProgramEveryEvent() {
        CornerHold hold = down(false);
        assertEquals(CornerHold.Phase.PENDING, hold.phase());
        assertTrue(hold.forwardsToTerminal());
        assertFalse(hold.isClaimed());
    }

    @Test
    public void aTapInTheSquareNeverClaimsAnything() {
        CornerHold hold = down(false);
        assertEquals(CornerHold.Move.FORWARD, hold.move(102f, 203f));
        assertEquals(CornerHold.Lift.NOTHING, hold.lift());
        assertEquals(CornerHold.Phase.IDLE, hold.phase());
        // And the hold that was still pending when the finger left fires on nobody.
        assertFalse(hold.holdElapsed());
    }

    @Test
    public void aStillHoldClaimsTheGestureWhenTheTimeElapses() {
        CornerHold hold = down(false);
        hold.move(104f, 197f);
        assertTrue(hold.holdElapsed());
        assertEquals(CornerHold.Phase.HELD, hold.phase());
        assertTrue(hold.isClaimed());
        // Claimed means the terminal has been cancelled: it gets nothing more.
        assertFalse(hold.forwardsToTerminal());
    }

    @Test
    public void travellingBeforeTheHoldLeavesTheGestureWithTheProgram() {
        CornerHold hold = down(false);
        assertEquals(CornerHold.Move.ABANDONED, hold.move(100f, 260f));
        assertEquals(CornerHold.Phase.ABANDONED, hold.phase());
        assertFalse(hold.holdElapsed());
        assertFalse(hold.isClaimed());
        // The overlay still holds the touch stream, so the program is still owed every event.
        assertTrue(hold.forwardsToTerminal());
        assertEquals(CornerHold.Move.FORWARD, hold.move(100f, 300f));
        assertEquals(CornerHold.Lift.NOTHING, hold.lift());
    }

    @Test
    public void aSecondFingerBeforeTheHoldLeavesItWithTheProgramToo() {
        CornerHold hold = down(false);
        assertTrue(hold.secondFinger());
        assertEquals(CornerHold.Phase.ABANDONED, hold.phase());
        assertFalse(hold.holdElapsed());
        assertTrue(hold.forwardsToTerminal());
        assertEquals(CornerHold.Lift.NOTHING, hold.lift());
    }

    @Test
    public void aSecondFingerAfterTheHoldChangesNothing() {
        CornerHold hold = down(false);
        assertTrue(hold.holdElapsed());
        assertFalse(hold.secondFinger());
        assertEquals(CornerHold.Phase.HELD, hold.phase());
    }

    // ------------------------------------------------------------------ after the hold

    @Test
    public void aLiftAfterTheHoldOpensTheTab() {
        CornerHold hold = down(false);
        hold.holdElapsed();
        assertEquals(CornerHold.Move.NONE, hold.move(102f, 202f));
        assertEquals(CornerHold.Lift.OPEN_TAB, hold.lift());
        assertEquals(CornerHold.Phase.IDLE, hold.phase());
    }

    @Test
    public void aDragAtASeamResizesTheSplitAndCommitsOnce() {
        CornerHold hold = down(true);
        hold.holdElapsed();
        assertEquals(CornerHold.Move.COMMITTED, hold.move(140f, 200f));
        assertEquals(CornerHold.Phase.RESIZING, hold.phase());
        assertEquals("only the first move commits", CornerHold.Move.DRAGGING, hold.move(180f, 200f));
        assertEquals(CornerHold.Lift.COMMIT_RESIZE, hold.lift());
    }

    @Test
    public void aDragAwayFromASeamCarriesTheTabOutInstead() {
        CornerHold hold = down(false);
        hold.holdElapsed();
        assertEquals(CornerHold.Move.COMMITTED, hold.move(140f, 200f));
        assertEquals(CornerHold.Phase.CARRYING, hold.phase());
        assertEquals(CornerHold.Move.DRAGGING, hold.move(220f, 260f));
        assertEquals(CornerHold.Lift.OPEN_TAB, hold.lift());
    }

    @Test
    public void aHoldThatHasNotBeenDraggedFarEnoughIsStillJustHeld() {
        CornerHold hold = down(true);
        hold.holdElapsed();
        assertEquals(CornerHold.Move.NONE, hold.move(104f, 202f));
        assertEquals(CornerHold.Phase.HELD, hold.phase());
        assertEquals(CornerHold.Lift.OPEN_TAB, hold.lift());
    }

    // ------------------------------------------------------------------ housekeeping

    @Test
    public void aResetForgetsTheGestureEntirely() {
        CornerHold hold = down(true);
        hold.holdElapsed();
        hold.move(200f, 200f);
        hold.reset();
        assertEquals(CornerHold.Phase.IDLE, hold.phase());
        assertFalse(hold.isTracking());
        assertFalse(hold.forwardsToTerminal());
        assertEquals(CornerHold.Move.NONE, hold.move(300f, 300f));
        assertEquals(CornerHold.Lift.NOTHING, hold.lift());
    }

    @Test
    public void theHoldOnlyEverClaimsAPendingFinger() {
        CornerHold hold = new CornerHold();
        assertFalse(hold.holdElapsed());
        assertEquals(CornerHold.Phase.IDLE, hold.phase());
    }
}
