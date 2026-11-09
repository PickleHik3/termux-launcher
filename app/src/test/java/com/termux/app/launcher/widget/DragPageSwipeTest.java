package com.termux.app.launcher.widget;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** A second finger's swipe during a drag turns pages by the pager's slop and commit distance. */
public class DragPageSwipeTest {
    private static final float SLOP = 16f;
    private static final float PAGE = 1000f;
    private static final float COMMIT = 400f; // PagePolicy.COMMIT_FRACTION of the page
    private static final float DENSITY = 2f;

    @Test public void aLeftSwipePastTheDistanceBringsTheNextPage() {
        DragPageSwipe swipe = new DragPageSwipe();
        swipe.down(1, 800f, 500f);
        assertEquals(DragPageSwipe.NONE, swipe.move(700f, 500f, SLOP, COMMIT));
        assertEquals(DragPageSwipe.NEXT, swipe.move(390f, 505f, SLOP, COMMIT));
    }

    @Test public void aRightSwipeBringsThePreviousPage() {
        DragPageSwipe swipe = new DragPageSwipe();
        swipe.down(1, 100f, 500f);
        assertEquals(DragPageSwipe.PREVIOUS, swipe.move(520f, 500f, SLOP, COMMIT));
    }

    @Test public void oneSwipeTurnsOnePageAndItsLiftNoMore() {
        DragPageSwipe swipe = new DragPageSwipe();
        swipe.down(1, 900f, 500f);
        assertEquals(DragPageSwipe.NEXT, swipe.move(450f, 500f, SLOP, COMMIT));
        assertEquals("still short of a second distance from where it turned",
            DragPageSwipe.NONE, swipe.move(200f, 500f, SLOP, COMMIT));
        assertEquals("a flick on the lift after a turn is not another",
            DragPageSwipe.NONE, swipe.lift(150f, -5000f, PAGE, DENSITY));
        assertFalse(swipe.tracking());
    }

    @Test public void aFreshCrossingTurnsAgain() {
        DragPageSwipe swipe = new DragPageSwipe();
        swipe.down(1, 950f, 500f);
        assertEquals(DragPageSwipe.NEXT, swipe.move(540f, 500f, SLOP, COMMIT));
        assertEquals(DragPageSwipe.NEXT, swipe.move(130f, 500f, SLOP, COMMIT));
    }

    @Test public void aPressedAgainFingerSwipesAfresh() {
        DragPageSwipe swipe = new DragPageSwipe();
        swipe.down(1, 900f, 500f);
        assertEquals(DragPageSwipe.NEXT, swipe.move(450f, 500f, SLOP, COMMIT));
        swipe.lift(450f, 0f, PAGE, DENSITY);
        swipe.down(2, 900f, 500f);
        assertTrue(swipe.tracking());
        assertEquals(2, swipe.pointerId());
        assertEquals(DragPageSwipe.NEXT, swipe.move(450f, 500f, SLOP, COMMIT));
    }

    @Test public void aVerticalStartIsNeverATurn() {
        DragPageSwipe swipe = new DragPageSwipe();
        swipe.down(1, 500f, 500f);
        assertEquals(DragPageSwipe.NONE, swipe.move(500f, 540f, SLOP, COMMIT));
        assertEquals(DragPageSwipe.NONE, swipe.move(0f, 540f, SLOP, COMMIT));
        assertEquals(DragPageSwipe.NONE, swipe.lift(0f, -5000f, PAGE, DENSITY));
    }

    @Test public void travelInsideTheSlopIsNotYetSideways() {
        DragPageSwipe swipe = new DragPageSwipe();
        swipe.down(1, 500f, 500f);
        assertEquals(DragPageSwipe.NONE, swipe.move(490f, 500f, SLOP, COMMIT));
        assertEquals("a lift inside the slop turns nothing, however quick",
            DragPageSwipe.NONE, swipe.lift(490f, -5000f, PAGE, DENSITY));
    }

    @Test public void aShortFlickTurnsAsTheSwipeWould() {
        DragPageSwipe swipe = new DragPageSwipe();
        swipe.down(1, 500f, 500f);
        assertEquals(DragPageSwipe.NONE, swipe.move(440f, 500f, SLOP, COMMIT));
        // 2000 px/s at density 2 is 1000 dp/s, past the pager's fling.
        assertEquals(DragPageSwipe.NEXT, swipe.lift(430f, -2000f, PAGE, DENSITY));
    }

    @Test public void aShortSlowLiftReturns() {
        DragPageSwipe swipe = new DragPageSwipe();
        swipe.down(1, 500f, 500f);
        assertEquals(DragPageSwipe.NONE, swipe.move(560f, 500f, SLOP, COMMIT));
        assertEquals(DragPageSwipe.NONE, swipe.lift(560f, 0f, PAGE, DENSITY));
    }

    @Test public void nothingWatchedNothingTurns() {
        DragPageSwipe swipe = new DragPageSwipe();
        assertFalse(swipe.tracking());
        assertEquals(DragPageSwipe.NONE, swipe.move(0f, 0f, SLOP, COMMIT));
        assertEquals(DragPageSwipe.NONE, swipe.lift(0f, -5000f, PAGE, DENSITY));
    }
}
