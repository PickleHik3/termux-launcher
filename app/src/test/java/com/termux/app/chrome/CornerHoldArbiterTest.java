package com.termux.app.chrome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Which of the two things that want a resting finger gets it: the pane corner it landed in, or the
 * content under it with its own long press. The rule is where the press landed, decided once, so
 * the two timers underneath can never both answer the same finger.
 */
public class CornerHoldArbiterTest {

    private static final float WIDTH = 600f;
    private static final float HEIGHT = 800f;
    private static final float DENSITY = 1f;
    private static final float SLOP = 24f;
    /** The square each corner keeps at this density. */
    private static final float SQUARE = CornerZones.PANE_SIZE_DP;

    private static CornerHoldArbiter downAt(float x, float y) {
        CornerHoldArbiter arbiter = new CornerHoldArbiter();
        arbiter.down(x, y, WIDTH, HEIGHT, DENSITY, false, SLOP);
        return arbiter;
    }

    // ------------------------------------------------------------------ where the finger landed

    @Test
    public void aPressInACornerArmsThatCornerAndHoldsTheContentsLongPress() {
        CornerHoldArbiter arbiter = new CornerHoldArbiter();

        assertEquals(CornerZones.TOP_RIGHT,
            arbiter.down(WIDTH - 2f, 2f, WIDTH, HEIGHT, DENSITY, false, SLOP));
        assertEquals(CornerHoldArbiter.Owner.PENDING, arbiter.owner());
        assertFalse("the grid must not race the corner", arbiter.contentMayLongPress());
        assertTrue("and it still sees every event", arbiter.contentKeepsEvents());
        assertFalse("nothing is claimed until the hold fires", arbiter.isClaimed());
    }

    @Test
    public void aPressInTheMiddleIsTheContentsWhole() {
        CornerHoldArbiter arbiter = new CornerHoldArbiter();

        assertEquals(CornerZones.NONE,
            arbiter.down(WIDTH / 2f, HEIGHT / 2f, WIDTH, HEIGHT, DENSITY, false, SLOP));
        assertEquals(CornerHoldArbiter.Owner.CONTENT, arbiter.owner());
        assertTrue("the content's own long press runs", arbiter.contentMayLongPress());
        assertFalse(arbiter.isTracking());
    }

    @Test
    public void theEdgesBetweenTheCornersAreTheContents() {
        assertEquals(CornerZones.NONE,
            downAt(2f, HEIGHT / 2f).corner());
        assertEquals(CornerZones.NONE,
            downAt(WIDTH / 2f, 2f).corner());
        assertEquals("the last pixel of the square is still the corner's",
            CornerZones.TOP_LEFT, downAt(SQUARE, SQUARE).corner());
        assertEquals("one pixel past it is not",
            CornerZones.NONE, downAt(SQUARE + 1f, SQUARE + 1f).corner());
    }

    /** A widget's own remove chip sits inside the page's square, and it is the widget's. */
    @Test
    public void contentThatOwnsThePointTakesTheCornerBack() {
        CornerHoldArbiter arbiter = new CornerHoldArbiter();

        assertEquals(CornerZones.NONE,
            arbiter.down(2f, 2f, WIDTH, HEIGHT, DENSITY, true, SLOP));
        assertEquals(CornerHoldArbiter.Owner.CONTENT, arbiter.owner());
        assertTrue(arbiter.contentMayLongPress());
        assertEquals(CornerZones.TOP_LEFT,
            CornerHoldArbiter.cornerFor(2f, 2f, WIDTH, HEIGHT, DENSITY, false));
        assertEquals(CornerZones.NONE,
            CornerHoldArbiter.cornerFor(2f, 2f, WIDTH, HEIGHT, DENSITY, true));
    }

    // ------------------------------------------------------------------ the ways the wait ends

    @Test
    public void theHoldFiringGivesTheCornerTheGestureAndCancelsTheContent() {
        CornerHoldArbiter arbiter = downAt(WIDTH - 2f, 2f);

        assertTrue("the content is owed a cancel", arbiter.holdElapsed());
        assertEquals(CornerHoldArbiter.Owner.CORNER, arbiter.owner());
        assertFalse(arbiter.contentKeepsEvents());
        assertFalse(arbiter.contentMayLongPress());
        assertEquals(CornerHold.Lift.OPEN_TAB, arbiter.lift());
        assertEquals(CornerHoldArbiter.Owner.NONE, arbiter.owner());
    }

    @Test
    public void leavingTheSquareBeforeTheHoldHandsTheLongPressBack() {
        CornerHoldArbiter arbiter = downAt(WIDTH - 2f, 2f);

        assertEquals(CornerHold.Move.ABANDONED, arbiter.move(WIDTH - 2f - SLOP * 4f, 2f));
        assertEquals(CornerHoldArbiter.Owner.CONTENT, arbiter.owner());
        assertTrue("the gesture is the content's again", arbiter.contentMayLongPress());
        assertFalse("and the hold that was waiting fires on nobody", arbiter.holdElapsed());
        assertEquals(CornerHold.Lift.NOTHING, arbiter.lift());
    }

    @Test
    public void aSecondFingerHandsTheLongPressBack() {
        CornerHoldArbiter arbiter = downAt(2f, HEIGHT - 2f);

        assertTrue(arbiter.secondFinger());
        assertEquals(CornerHoldArbiter.Owner.CONTENT, arbiter.owner());
        assertTrue(arbiter.contentMayLongPress());
        assertFalse(arbiter.holdElapsed());
    }

    @Test
    public void aTapInTheSquareOpensNothingAndCostsTheContentNothing() {
        CornerHoldArbiter arbiter = downAt(2f, 2f);

        assertEquals(CornerHold.Move.FORWARD, arbiter.move(3f, 3f));
        assertTrue(arbiter.contentKeepsEvents());
        assertEquals(CornerHold.Lift.NOTHING, arbiter.lift());
        assertEquals(CornerHoldArbiter.Owner.NONE, arbiter.owner());
        assertTrue(arbiter.contentMayLongPress());
    }

    @Test
    public void aResetForgetsThePressEntirely() {
        CornerHoldArbiter arbiter = downAt(2f, 2f);
        assertTrue(arbiter.holdElapsed());

        arbiter.reset();

        assertEquals(CornerHoldArbiter.Owner.NONE, arbiter.owner());
        assertEquals(CornerZones.NONE, arbiter.corner());
        assertTrue(arbiter.contentMayLongPress());
        assertFalse(arbiter.isClaimed());
    }

    /** One press at a time: a new landing decides again, whatever the last one was. */
    @Test
    public void aNewPressDecidesAgain() {
        CornerHoldArbiter arbiter = downAt(2f, 2f);
        assertTrue(arbiter.holdElapsed());

        assertEquals(CornerZones.NONE,
            arbiter.down(WIDTH / 2f, HEIGHT / 2f, WIDTH, HEIGHT, DENSITY, false, SLOP));
        assertEquals(CornerHoldArbiter.Owner.CONTENT, arbiter.owner());
        assertTrue(arbiter.contentMayLongPress());
    }
}
