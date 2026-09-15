package com.termux.view;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.termux.view.HoldGesture.Outcome;
import com.termux.view.HoldGesture.Phase;

import org.junit.Test;

/**
 * Every row of the terminal's touch grammar, driven as the view drives it: a tap, a drag, the
 * the hold itself, and each of the things a held finger may do next.
 */
public class HoldGestureTest {

    private static final float SLOP = 20f;

    /** A finger lands, starting the hold's own clock. */
    private HoldGesture down() {
        HoldGesture hold = new HoldGesture();
        hold.down(100f, 200f, SLOP, true);
        return hold;
    }

    /** A finger that held on a terminal reading the mouse, with or without motion reporting. */
    private HoldGesture held(boolean motionReported) {
        HoldGesture hold = down();
        assertEquals(Outcome.HOLD_MOUSE, hold.holdElapsed(true, motionReported));
        return hold;
    }

    /** The lift, when nothing in the row cares where it landed. */
    private Outcome lift(HoldGesture hold) {
        return hold.up(hold.x(), hold.y());
    }

    @Test
    public void aTapIsNeitherAHoldNorAnythingElse() {
        HoldGesture hold = down();
        assertEquals(Outcome.NOTHING, hold.move(103f, 204f));
        assertEquals(Outcome.NOTHING, hold.up(hold.x(), hold.y()));
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
    public void aTremorBeforeTheHoldTimeLeavesItStillPending() {
        // Under slop is not movement, so the hold is still on its way.
        HoldGesture hold = down();
        assertEquals(Outcome.NOTHING, hold.move(104f, 203f));
        assertTrue(hold.isPending());
        assertEquals(Phase.PENDING, hold.phase());
    }

    @Test
    public void aStillFingerHoldsAndIsHandedTheMouse() {
        HoldGesture hold = held(false);
        assertEquals(Phase.HELD, hold.phase());
        assertTrue(hold.isHeld());
        assertTrue(hold.heldAndStill());
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
        assertEquals(Outcome.NOTHING, hold.up(hold.x(), hold.y()));
        assertFalse(hold.isHeld());
        assertFalse(hold.reachesSelect());
    }

    @Test
    public void liftingAfterTheHoldClicksWhereTheFingerEnded() {
        HoldGesture hold = held(false);
        assertEquals(Outcome.CLICK, hold.up(104f, 203f));
        assertEquals(Phase.DONE, hold.phase());
        // The landing is kept too: together they are what TapPrecision reads.
        assertEquals(100f, hold.holdX(), 0.001f);
        assertEquals(200f, hold.holdY(), 0.001f);
        assertEquals(104f, hold.x(), 0.001f);
        assertEquals(203f, hold.y(), 0.001f);
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
        assertEquals(Outcome.DRAG_ENDED, hold.up(hold.x(), hold.y()));
        assertFalse(hold.heldAndStill());
    }

    @Test
    public void draggingAfterTheHoldTellsAProgramWantingNoMotionNothingUntilTheLift() {
        HoldGesture hold = held(false);
        assertEquals(Outcome.NOTHING, hold.move(100f, 400f));
        assertEquals(Phase.HELD, hold.phase());
        assertEquals(Outcome.NOTHING, hold.move(180f, 500f));
        // The lift still clicks, at wherever the finger ended up.
        assertEquals(Outcome.CLICK, hold.up(180f, 500f));
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
        assertEquals(Outcome.NOTHING, hold.up(hold.x(), hold.y()));
        assertEquals(Outcome.NOTHING, hold.selectElapsed());
    }

    @Test
    public void aDragAfterTheHoldNeverReachesTheSecondStage() {
        HoldGesture dragging = held(true);
        assertEquals(Outcome.DRAG_STARTED, dragging.move(100f, 400f));
        assertFalse(dragging.reachesSelect());
        assertEquals(Outcome.NOTHING, dragging.selectElapsed());
        assertEquals(Phase.DRAGGING, dragging.phase());

        // The same holds when the program wants no motion and the drag reports nothing at all.
        HoldGesture quiet = held(false);
        assertEquals(Outcome.NOTHING, quiet.move(100f, 400f));
        assertFalse(quiet.reachesSelect());
        assertEquals(Outcome.NOTHING, quiet.selectElapsed());
        assertEquals(Phase.HELD, quiet.phase());
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
        assertEquals(Outcome.CLICK, hold.up(hold.x(), hold.y()));
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
        assertFalse(hold.heldAndStill());
        assertEquals(Outcome.NOTHING, hold.move(100f, 400f));
        assertEquals(Outcome.NOTHING, hold.up(hold.x(), hold.y()));
        // A plain shell has no second stage: the first buzz already selected.
        assertEquals(Outcome.NOTHING, hold.selectElapsed());
    }

    @Test
    public void aCancelMidHoldClearsEverything() {
        HoldGesture hold = held(false);
        assertEquals(Outcome.NOTHING, hold.cancel());
        assertEquals(Phase.DONE, hold.phase());
        assertFalse(hold.isHeld());
        assertFalse(hold.heldAndStill());
        assertEquals(Outcome.NOTHING, hold.move(400f, 600f));
        assertEquals(Outcome.NOTHING, hold.up(hold.x(), hold.y()));
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
    public void heldAndStillIsOnlyTrueBetweenTheHoldAndTheFingersNextWord() {
        assertFalse(down().heldAndStill());
        HoldGesture dragged = held(true);
        assertTrue(dragged.heldAndStill());
        dragged.move(100f, 400f);
        assertFalse(dragged.heldAndStill());
    }

    @Test
    public void aSecondGestureStartsClean() {
        HoldGesture hold = held(true);
        hold.move(100f, 400f);
        hold.up(hold.x(), hold.y());
        hold.reset();
        assertEquals(Phase.IDLE, hold.phase());
        hold.down(10f, 20f, SLOP, true);
        assertEquals(Outcome.HOLD_MOUSE, hold.holdElapsed(true, false));
        assertTrue(hold.heldAndStill());
        assertEquals(10f, hold.holdX(), 0.001f);
    }
}
