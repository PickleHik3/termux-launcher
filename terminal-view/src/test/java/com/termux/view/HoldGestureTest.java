package com.termux.view;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.termux.view.HoldGesture.Outcome;
import com.termux.view.HoldGesture.Phase;

import org.junit.Test;

/**
 * Every row of the terminal's touch grammar, driven as the view drives it: a tap, a drag, the
 * loupe's window, the hold, and each of the four things a held finger may do next.
 */
public class HoldGestureTest {

    private static final float SLOP = 20f;

    /** A finger lands where the loupe and the hold would both start. */
    private HoldGesture down() {
        HoldGesture hold = new HoldGesture();
        hold.down(100f, 200f, SLOP, true);
        return hold;
    }

    /** A finger that held on a terminal reading the mouse, with or without motion reporting. */
    private HoldGesture held(boolean motionReported) {
        HoldGesture hold = down();
        assertEquals(Outcome.HOLD_AIMED, hold.holdElapsed(true, motionReported));
        return hold;
    }

    @Test
    public void aTapIsNeitherAHoldNorAnythingElse() {
        HoldGesture hold = down();
        assertEquals(Outcome.NOTHING, hold.move(103f, 204f));
        assertEquals(Outcome.NOTHING, hold.up());
        assertEquals(Phase.DONE, hold.phase());
        assertFalse(hold.isHeld());
    }

    @Test
    public void anImmediateDragIsAScrollAndNeverBecomesAHold() {
        HoldGesture hold = down();
        assertEquals(Outcome.NOTHING, hold.move(100f, 320f));
        assertEquals(Phase.DONE, hold.phase());
        assertFalse(hold.isPending());
        assertEquals(Outcome.NOTHING, hold.holdElapsed(true, true));
        assertFalse(hold.isHeld());
    }

    @Test
    public void theLoupesOwnWindowLeavesTheHoldStillPending() {
        // The loupe opens well before the hold and decides nothing: the finger is still pending.
        HoldGesture hold = down();
        assertEquals(Outcome.NOTHING, hold.move(104f, 203f));
        assertTrue(hold.isPending());
        assertEquals(Phase.PENDING, hold.phase());
    }

    @Test
    public void aStillFingerHoldsAndKeepsTheLoupe() {
        HoldGesture hold = held(false);
        assertEquals(Phase.HELD, hold.phase());
        assertTrue(hold.isHeld());
        assertTrue(hold.showsHint());
    }

    @Test
    public void theHoldOnlyEverRecognisesAPendingFinger() {
        HoldGesture hold = new HoldGesture();
        assertEquals(Outcome.NOTHING, hold.holdElapsed(true, true));
        hold.down(10f, 20f, SLOP, false);
        assertEquals(Phase.IDLE, hold.phase());
        assertEquals(Outcome.NOTHING, hold.holdElapsed(true, true));
    }

    @Test
    public void aHoldExemptFingerDoesNothingAtAll() {
        HoldGesture hold = new HoldGesture();
        hold.down(100f, 200f, SLOP, false);
        assertEquals(Phase.IDLE, hold.phase());
        assertEquals(Outcome.NOTHING, hold.move(100f, 320f));
        assertEquals(Outcome.NOTHING, hold.holdElapsed(true, true));
        assertEquals(Outcome.NOTHING, hold.selectElapsed());
        assertEquals(Outcome.NOTHING, hold.pointerDown());
        assertEquals(Outcome.NOTHING, hold.up());
        assertFalse(hold.isHeld());
        assertFalse(hold.reachesSelect());
    }

    @Test
    public void liftingAfterTheHoldClicksWhereItWasAiming() {
        HoldGesture hold = held(false);
        assertEquals(Outcome.CLICK, hold.up());
        assertEquals(Phase.DONE, hold.phase());
        assertEquals(100f, hold.x(), 0.001f);
        assertEquals(200f, hold.y(), 0.001f);
    }

    @Test
    public void draggingAfterTheHoldHoldsTheMouseButtonDownWhenMotionIsWanted() {
        HoldGesture hold = held(true);
        assertEquals(Outcome.NOTHING, hold.move(105f, 205f));
        assertEquals(Outcome.DRAG_STARTED, hold.move(100f, 400f));
        assertEquals(Phase.DRAGGING, hold.phase());
        // The button went down at the cell held, not at the cell the drag has reached.
        assertEquals(100f, hold.holdX(), 0.001f);
        assertEquals(200f, hold.holdY(), 0.001f);
        assertEquals(Outcome.DRAG_MOVED, hold.move(140f, 460f));
        assertEquals(140f, hold.x(), 0.001f);
        assertEquals(Outcome.DRAG_ENDED, hold.up());
        assertFalse(hold.showsHint());
    }

    @Test
    public void draggingAfterTheHoldOnlyMovesTheAimWhenMotionIsNotWanted() {
        HoldGesture hold = held(false);
        assertEquals(Outcome.AIM_MOVED, hold.move(100f, 400f));
        assertEquals(Phase.HELD, hold.phase());
        assertFalse(hold.showsHint());
        assertEquals(Outcome.AIM_MOVED, hold.move(180f, 500f));
        // The lift still clicks, now at wherever the aim was carried to.
        assertEquals(Outcome.CLICK, hold.up());
        assertEquals(180f, hold.x(), 0.001f);
        assertEquals(500f, hold.y(), 0.001f);
    }

    @Test
    public void aFingerThatKeepsHoldingSelectsText() {
        HoldGesture hold = held(false);
        // A tremor under slop is still a still finger.
        assertEquals(Outcome.NOTHING, hold.move(103f, 204f));
        assertTrue(hold.reachesSelect());
        assertEquals(Outcome.HOLD_SELECTED, hold.selectElapsed());
        assertEquals(Phase.DONE, hold.phase());
        // The selection owns the gesture; the lift that follows adds nothing.
        assertEquals(Outcome.NOTHING, hold.up());
        assertEquals(Outcome.NOTHING, hold.selectElapsed());
    }

    @Test
    public void aDragAfterTheHoldNeverReachesTheSecondStage() {
        HoldGesture dragging = held(true);
        assertEquals(Outcome.DRAG_STARTED, dragging.move(100f, 400f));
        assertFalse(dragging.reachesSelect());
        assertEquals(Outcome.NOTHING, dragging.selectElapsed());
        assertEquals(Phase.DRAGGING, dragging.phase());

        // The same holds when the program wants no motion and the drag only moves the aim.
        HoldGesture aiming = held(false);
        assertEquals(Outcome.AIM_MOVED, aiming.move(100f, 400f));
        assertFalse(aiming.reachesSelect());
        assertEquals(Outcome.NOTHING, aiming.selectElapsed());
    }

    @Test
    public void aDragBeforeTheHoldNeverReachesTheSecondStageEither() {
        HoldGesture hold = down();
        assertTrue(hold.reachesSelect());
        assertEquals(Outcome.NOTHING, hold.move(100f, 320f));
        assertFalse(hold.reachesSelect());
        assertEquals(Outcome.NOTHING, hold.selectElapsed());
    }

    @Test
    public void aLiftBeforeTheSecondStageClicksInstead() {
        HoldGesture hold = held(false);
        assertEquals(Outcome.CLICK, hold.up());
        assertEquals(Outcome.NOTHING, hold.selectElapsed());
        assertEquals(Phase.DONE, hold.phase());
    }

    @Test
    public void aSecondFingerAfterTheHoldGivesBothFingersBack() {
        HoldGesture hold = held(false);
        assertEquals(Outcome.ABANDONED, hold.pointerDown());
        assertEquals(Phase.DONE, hold.phase());
        assertFalse(hold.reachesSelect());
        assertEquals(Outcome.NOTHING, hold.selectElapsed());
    }

    @Test
    public void aSecondFingerBeforeTheHoldIsTheWheelOrThePinch() {
        HoldGesture hold = down();
        assertEquals(Outcome.ABANDONED, hold.pointerDown());
        assertEquals(Phase.DONE, hold.phase());
        assertEquals(Outcome.NOTHING, hold.holdElapsed(true, true));
    }

    @Test
    public void aSecondFingerDuringADragLetsTheButtonUp() {
        HoldGesture hold = held(true);
        assertEquals(Outcome.DRAG_STARTED, hold.move(100f, 400f));
        assertEquals(Outcome.DRAG_ENDED, hold.pointerDown());
        assertEquals(Phase.DONE, hold.phase());
    }

    @Test
    public void holdingAPlainShellSelectsText() {
        HoldGesture hold = down();
        assertEquals(Outcome.HOLD_SELECTED, hold.holdElapsed(false, false));
        // The selection owns the gesture from here; nothing else is decided by the finger.
        assertEquals(Phase.DONE, hold.phase());
        assertFalse(hold.showsHint());
        assertEquals(Outcome.NOTHING, hold.move(100f, 400f));
        assertEquals(Outcome.NOTHING, hold.up());
        // A plain shell has no second stage: the first buzz already selected.
        assertEquals(Outcome.NOTHING, hold.selectElapsed());
    }

    @Test
    public void aCancelMidHoldClearsEverything() {
        HoldGesture hold = held(false);
        assertEquals(Outcome.NOTHING, hold.cancel());
        assertEquals(Phase.DONE, hold.phase());
        assertFalse(hold.isHeld());
        assertFalse(hold.showsHint());
        assertEquals(Outcome.NOTHING, hold.move(400f, 600f));
        assertEquals(Outcome.NOTHING, hold.up());
        assertEquals(Outcome.NOTHING, hold.selectElapsed());
    }

    @Test
    public void aCancelMidDragStillLetsTheMouseButtonUp() {
        HoldGesture hold = held(true);
        assertEquals(Outcome.DRAG_STARTED, hold.move(100f, 400f));
        assertEquals(Outcome.DRAG_ENDED, hold.cancel());
        assertEquals(Phase.DONE, hold.phase());
        assertEquals(Outcome.NOTHING, hold.cancel());
    }

    @Test
    public void theHintLeavesWithTheFirstThingTheFingerSays() {
        assertFalse(down().showsHint());
        HoldGesture dragged = held(true);
        assertTrue(dragged.showsHint());
        dragged.move(100f, 400f);
        assertFalse(dragged.showsHint());
    }

    @Test
    public void aSecondGestureStartsClean() {
        HoldGesture hold = held(true);
        hold.move(100f, 400f);
        hold.up();
        hold.reset();
        assertEquals(Phase.IDLE, hold.phase());
        hold.down(10f, 20f, SLOP, true);
        assertEquals(Outcome.HOLD_AIMED, hold.holdElapsed(true, false));
        assertTrue(hold.showsHint());
        assertEquals(10f, hold.holdX(), 0.001f);
    }
}
