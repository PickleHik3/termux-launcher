package com.termux.app.controlbar;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/** Which way a finger on the control bar is going (#429). */
public class BarGestureTest {

    @Test
    public void undecidedUntilItMovesPastTheSlop() {
        BarGesture gesture = new BarGesture(8f);
        gesture.down(100f, 100f);
        assertEquals(BarGesture.Kind.UNDECIDED, gesture.move(104f, 95f));
    }

    @Test
    public void aMostlyUpwardMoveIsTheListSlide() {
        BarGesture gesture = new BarGesture(8f);
        gesture.down(100f, 100f);
        assertEquals(BarGesture.Kind.SLIDE_UP, gesture.move(104f, 80f));
    }

    @Test
    public void theSlopIsADistanceSoADiagonalDriftPastItIsDecided() {
        BarGesture gesture = new BarGesture(8f);
        gesture.down(100f, 100f);
        assertEquals(BarGesture.Kind.SLIDE_UP, gesture.move(103f, 92.3f));
    }

    @Test
    public void aSidewaysMoveIsTheBarsOwn() {
        BarGesture gesture = new BarGesture(8f);
        gesture.down(100f, 100f);
        assertEquals(BarGesture.Kind.OTHER, gesture.move(120f, 96f));
    }

    @Test
    public void aDownwardMoveIsTheBarsOwn() {
        BarGesture gesture = new BarGesture(8f);
        gesture.down(100f, 100f);
        assertEquals(BarGesture.Kind.OTHER, gesture.move(100f, 130f));
    }

    @Test
    public void theFirstDecisionHoldsForTheRestOfTheTouch() {
        BarGesture gesture = new BarGesture(8f);
        gesture.down(100f, 100f);
        gesture.move(100f, 60f);
        assertEquals(BarGesture.Kind.SLIDE_UP, gesture.move(300f, 60f));
        gesture.down(100f, 100f);
        gesture.move(140f, 100f);
        assertEquals(BarGesture.Kind.OTHER, gesture.move(100f, 20f));
    }

    @Test
    public void aNewTouchStartsUndecided() {
        BarGesture gesture = new BarGesture(8f);
        gesture.down(0f, 0f);
        gesture.move(0f, -50f);
        gesture.down(0f, 0f);
        assertEquals(BarGesture.Kind.UNDECIDED, gesture.kind());
    }
}
