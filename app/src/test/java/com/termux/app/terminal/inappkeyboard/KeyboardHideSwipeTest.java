package com.termux.app.terminal.inappkeyboard;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.termux.app.terminal.inappkeyboard.KeyboardHideSwipe.Outcome;
import com.termux.app.terminal.inappkeyboard.KeyboardHideSwipe.Phase;

import org.junit.Test;

/**
 * The rules of the swipe that puts the keyboard away: where it may start, what abandons it, how
 * far it has to go, and how fast a shorter one has to be.
 */
public class KeyboardHideSwipeTest {

    private static final float SLOP = 8f;
    private static final float THRESHOLD = 28f;
    private static final float FLING = 500f;
    private static final float FLING_MIN = 12f;

    private final KeyboardHideSwipe swipe = new KeyboardHideSwipe(SLOP, THRESHOLD, FLING, FLING_MIN);

    // Band

    @Test
    public void aThickNullStripIsTheBandAsItIs() {
        assertEquals(20f, KeyboardHideSwipe.bandBottomPx(20f, 23f, 12f), 1e-4f);
    }

    @Test
    public void aThinNullStripGrowsToTheMinimumButNeverOntoTheCaps() {
        // Room under the minimum: grow to it.
        assertEquals(12f, KeyboardHideSwipe.bandBottomPx(3f, 40f, 12f), 1e-4f);
        // The caps are drawn before the minimum: stop at the caps.
        assertEquals(3.75f, KeyboardHideSwipe.bandBottomPx(3f, 3.75f, 12f), 1e-4f);
    }

    @Test
    public void bandContainsIsHalfOpenAtTheBottom() {
        assertTrue(KeyboardHideSwipe.bandContains(0f, 3f, 40f, 12f, 0f));
        assertTrue(KeyboardHideSwipe.bandContains(11.9f, 3f, 40f, 12f, 0f));
        assertFalse(KeyboardHideSwipe.bandContains(12f, 3f, 40f, 12f, 0f));
    }

    @Test
    public void bandReachesAboveTheKeyboardByTheReachAndNoFurther() {
        // No reach: the keyboard's top edge is the band's.
        assertFalse(KeyboardHideSwipe.bandContains(-1f, 3f, 40f, 12f, 0f));
        // With one, the space above counts, closed at the reach.
        assertTrue(KeyboardHideSwipe.bandContains(-1f, 3f, 40f, 12f, 24f));
        assertTrue(KeyboardHideSwipe.bandContains(-24f, 3f, 40f, 12f, 24f));
        assertFalse(KeyboardHideSwipe.bandContains(-24.1f, 3f, 40f, 12f, 24f));
        // A negative reach is no reach.
        assertFalse(KeyboardHideSwipe.bandContains(-1f, 3f, 40f, 12f, -5f));
    }

    // Direction

    @Test
    public void sidewaysFirstAbandonsAndStaysAbandoned() {
        swipe.begin(0f, 0f, 0L);
        assertEquals(Phase.ABANDONED, swipe.move(20f, 3f, 16L));
        assertEquals(Phase.ABANDONED, swipe.move(20f, 80f, 32L));
        assertEquals(0f, swipe.dragPx(), 1e-4f);
        assertEquals(Outcome.NONE, swipe.release(20f, 80f, 48L));
        assertEquals(Phase.IDLE, swipe.phase());
    }

    @Test
    public void upwardFirstAbandons() {
        swipe.begin(0f, 0f, 0L);
        assertEquals(Phase.ABANDONED, swipe.move(0f, -10f, 16L));
        assertEquals(Outcome.NONE, swipe.release(0f, 40f, 200L));
    }

    @Test
    public void wanderingInsideTheSlopIsStillArmed() {
        swipe.begin(0f, 0f, 0L);
        assertEquals(Phase.ARMED, swipe.move(5f, 3f, 16L));
        assertEquals(Phase.ARMED, swipe.move(-6f, -4f, 32L));
        assertEquals(Phase.DRAGGING, swipe.move(-2f, 12f, 48L));
    }

    @Test
    public void aDiagonalThatIsMostlyDownDrags() {
        swipe.begin(0f, 0f, 0L);
        assertEquals(Phase.DRAGGING, swipe.move(9f, 12f, 16L));
    }

    @Test
    public void aTapOnTheBandIsReportedAsOne() {
        swipe.begin(0f, 0f, 0L);
        assertEquals(Outcome.TAP, swipe.release(0f, 0f, 80L));
        assertEquals(Phase.IDLE, swipe.phase());
    }

    @Test
    public void aReleaseInsideTheSlopIsStillATapHoweverLongItWasHeld() {
        swipe.begin(0f, 0f, 0L);
        assertEquals(Phase.ARMED, swipe.move(4f, 5f, 16L));
        assertEquals(Outcome.TAP, swipe.release(4f, 5f, 3000L));
    }

    @Test
    public void anAbandonedSwipeIsNotATap() {
        swipe.begin(0f, 0f, 0L);
        swipe.move(30f, 2f, 16L);
        assertEquals(Outcome.NONE, swipe.release(30f, 2f, 32L));
    }

    // Drag

    @Test
    public void theDragTakesHoldWithoutAJumpAndFollowsTheFinger() {
        swipe.begin(0f, 0f, 0L);
        swipe.move(0f, 10f, 16L);
        assertEquals(Phase.DRAGGING, swipe.phase());
        assertEquals(0f, swipe.dragPx(), 1e-4f);
        swipe.move(0f, 30f, 32L);
        assertEquals(20f, swipe.dragPx(), 1e-4f);
    }

    @Test
    public void draggingBackAboveWhereItTookHoldClampsAtRest() {
        swipe.begin(0f, 0f, 0L);
        swipe.move(0f, 10f, 16L);
        swipe.move(0f, 4f, 32L);
        assertEquals(Phase.DRAGGING, swipe.phase());
        assertEquals(0f, swipe.dragPx(), 1e-4f);
    }

    // Release

    @Test
    public void releasePastTheThresholdHides() {
        swipe.begin(0f, 0f, 0L);
        swipe.move(0f, 10f, 100L);
        swipe.move(0f, 28f, 600L);
        assertEquals(Outcome.HIDE, swipe.release(0f, 28f, 900L));
    }

    @Test
    public void aSlowShortReleaseSpringsBack() {
        swipe.begin(0f, 0f, 0L);
        swipe.move(0f, 10f, 100L);
        swipe.move(0f, 20f, 300L);
        assertEquals(Outcome.SPRING_BACK, swipe.release(0f, 20f, 700L));
        assertEquals(Phase.IDLE, swipe.phase());
    }

    @Test
    public void aFastShortFlingHides() {
        swipe.begin(0f, 0f, 0L);
        swipe.move(0f, 10f, 8L);
        swipe.move(0f, 20f, 16L);
        // 24px in 24ms is 1000px/s, past the fling speed, and past the fling distance.
        assertEquals(Outcome.HIDE, swipe.release(0f, 24f, 24L));
    }

    @Test
    public void aFastTwitchUnderTheFlingDistanceSpringsBack() {
        swipe.begin(0f, 0f, 0L);
        swipe.move(0f, 9f, 4L);
        assertEquals(Outcome.SPRING_BACK, swipe.release(0f, 11f, 8L));
    }

    @Test
    public void velocityReadsOnlyTheRecentStretch() {
        swipe.begin(0f, 0f, 0L);
        swipe.move(0f, 10f, 16L);
        swipe.move(0f, 20f, 32L);
        // A long hold, then the release: the fast start is older than the window and does not count.
        assertEquals(Outcome.SPRING_BACK, swipe.release(0f, 20f, 500L));
    }

    @Test
    public void velocityIsTheSlopeOverTheWindow() {
        swipe.begin(0f, 0f, 0L);
        swipe.move(0f, 10f, 50L);
        swipe.move(0f, 40f, 100L);
        assertEquals(400f, swipe.velocityPxPerS(), 1e-3f);
    }

    @Test
    public void cancelWhileDraggingSpringsBackAndWhileArmedDoesNothing() {
        swipe.begin(0f, 0f, 0L);
        swipe.move(0f, 20f, 16L);
        assertEquals(Outcome.SPRING_BACK, swipe.cancel());
        assertEquals(Phase.IDLE, swipe.phase());
        swipe.begin(0f, 0f, 100L);
        assertEquals(Outcome.NONE, swipe.cancel());
    }

    @Test
    public void movesBeforeBeginAreIgnored() {
        assertEquals(Phase.IDLE, swipe.move(0f, 50f, 16L));
        assertEquals(0f, swipe.dragPx(), 1e-4f);
        assertEquals(Outcome.NONE, swipe.release(0f, 50f, 32L));
    }
}
