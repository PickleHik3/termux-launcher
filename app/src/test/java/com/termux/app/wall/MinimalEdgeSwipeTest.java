package com.termux.app.wall;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * The band along a minimal place's pane edge, and the one-way claim a swipe from it makes. A
 * swipe that starts in the band and goes sideways is the wall's; one that starts lower, or turns
 * downward first, stays the content's — a TUI's mouse drag, a scroll, a text selection.
 */
public class MinimalEdgeSwipeTest {

    private static final float LEFT = 0f;
    private static final float TOP = 0f;
    private static final float RIGHT = 1080f;
    private static final float BOTTOM = 2000f;
    private static final float BAND = 96f;     // 32 dp at 3x
    private static final float CORNER = 120f;  // 40 dp at 3x
    private static final float SLOP = 24f;
    private static final float EPS = 0.01f;

    private static boolean inBand(float x, float y) {
        return MinimalEdgeSwipe.inBand(x, y, LEFT, TOP, RIGHT, BOTTOM, BAND, CORNER);
    }

    private static MinimalEdgeSwipe armed(float x, float y) {
        MinimalEdgeSwipe swipe = new MinimalEdgeSwipe();
        swipe.down(x, y, LEFT, TOP, RIGHT, BOTTOM, BAND, CORNER, SLOP);
        return swipe;
    }

    @Test
    public void theBandRunsAlongTheTopAndTheBottomEdgeOnly() {
        assertTrue("just inside the top edge", inBand(540f, 1f));
        assertTrue("the last row of the top band", inBand(540f, BAND - 1f));
        assertFalse("the first row under the top band", inBand(540f, BAND + 1f));
        assertFalse("the middle of the pane", inBand(540f, 1000f));
        assertFalse("the first row over the bottom band", inBand(540f, BOTTOM - BAND - 1f));
        assertTrue("the last rows of the pane", inBand(540f, BOTTOM - 1f));
        assertTrue("the bottom edge itself", inBand(540f, BOTTOM));
    }

    @Test
    public void theCornerSquaresAreNotInTheBand() {
        // A finger down in a corner square is the corner tab's for the whole gesture.
        assertFalse(inBand(CORNER - 1f, 10f));
        assertTrue(inBand(CORNER + 1f, 10f));
        assertFalse(inBand(RIGHT - CORNER + 1f, 10f));
        assertTrue(inBand(RIGHT - CORNER - 1f, 10f));
        assertFalse(inBand(10f, BOTTOM - 10f));
        assertFalse(inBand(RIGHT - 10f, BOTTOM - 10f));
    }

    @Test
    public void outsideThePageIsNeverTheBand() {
        assertFalse(inBand(540f, TOP - 1f));
        assertFalse(inBand(540f, BOTTOM + 1f));
        assertFalse(inBand(LEFT - 1f, 10f));
        assertFalse(inBand(RIGHT + 1f, 10f));
        assertFalse("no band at all", MinimalEdgeSwipe.inBand(540f, 10f, LEFT, TOP, RIGHT, BOTTOM,
            0f, CORNER));
        assertFalse("an empty page", MinimalEdgeSwipe.inBand(0f, 0f, 0f, 0f, 0f, 0f, BAND, CORNER));
    }

    @Test
    public void aShortPageKeepsItsMiddleOutOfTheBands() {
        // Two bands that would meet leave the middle row to the content: each is capped at half
        // the height, so a 100 px page's bands are 50 px each and its centre is nobody's.
        assertTrue(MinimalEdgeSwipe.inBand(540f, 49f, LEFT, TOP, RIGHT, 100f, BAND, 0f));
        assertTrue(MinimalEdgeSwipe.inBand(540f, 51f, LEFT, TOP, RIGHT, 100f, BAND, 0f));
        assertFalse(MinimalEdgeSwipe.inBand(540f, 50f, LEFT, TOP, RIGHT, 100f, BAND, 0f));
    }

    @Test
    public void aDownOutsideTheBandArmsNothingAndLaterMovesAreNoOps() {
        MinimalEdgeSwipe swipe = new MinimalEdgeSwipe();
        assertFalse(swipe.down(540f, 1000f, LEFT, TOP, RIGHT, BOTTOM, BAND, CORNER, SLOP));
        assertFalse(swipe.isArmed());
        assertEquals(MinimalEdgeSwipe.Claim.NONE, swipe.move(900f, 1000f));
        assertEquals(MinimalEdgeSwipe.Claim.NONE, swipe.claim());
    }

    @Test
    public void aDownInTheBandIsPendingUntilTheSlop() {
        MinimalEdgeSwipe swipe = armed(540f, 20f);
        assertTrue(swipe.isArmed());
        assertEquals(MinimalEdgeSwipe.Claim.PENDING, swipe.claim());
        assertEquals(MinimalEdgeSwipe.Claim.PENDING, swipe.move(540f + SLOP, 20f));
        assertEquals(MinimalEdgeSwipe.Claim.PENDING, swipe.move(540f, 20f + SLOP));
        assertEquals(MinimalEdgeSwipe.Claim.PENDING, swipe.move(540f + SLOP, 20f + SLOP));
    }

    @Test
    public void sidewaysPastTheSlopIsTheWallsAndStaysSo() {
        MinimalEdgeSwipe swipe = armed(540f, 20f);
        assertEquals(MinimalEdgeSwipe.Claim.PAGING, swipe.move(540f - SLOP - 1f, 25f));
        // Turning downward afterwards changes nothing: the wall has the finger now.
        assertEquals(MinimalEdgeSwipe.Claim.PAGING, swipe.move(540f - SLOP - 1f, 400f));
        assertEquals(-SLOP - 1f, swipe.travel(540f - SLOP - 1f), EPS);
        // A second finger mid-drag does not take it back either.
        assertEquals(MinimalEdgeSwipe.Claim.PAGING, swipe.secondPointer());
    }

    @Test
    public void verticalPastTheSlopIsTheContentsForGood() {
        MinimalEdgeSwipe swipe = armed(540f, 20f);
        assertEquals(MinimalEdgeSwipe.Claim.ABANDONED, swipe.move(545f, 20f + SLOP + 1f));
        // However far sideways it goes later, the scroll it started is the terminal's.
        assertEquals(MinimalEdgeSwipe.Claim.ABANDONED, swipe.move(900f, 20f + SLOP + 1f));
        assertTrue("still armed: the stream is swallowed to the lift", swipe.isArmed());
    }

    @Test
    public void aDiagonalIsWhicheverAxisWonAtTheSlop() {
        assertEquals(MinimalEdgeSwipe.Claim.PAGING, armed(540f, 20f).move(540f + 40f, 20f + 30f));
        assertEquals(MinimalEdgeSwipe.Claim.ABANDONED, armed(540f, 20f).move(540f + 30f, 20f + 40f));
        // An exact tie past the slop goes to the content: the band claims only a clear swipe.
        assertEquals(MinimalEdgeSwipe.Claim.ABANDONED, armed(540f, 20f).move(540f + 40f, 20f + 40f));
    }

    @Test
    public void aSecondFingerBeforeTheClaimGivesTheGestureUp() {
        MinimalEdgeSwipe swipe = armed(540f, 20f);
        assertEquals(MinimalEdgeSwipe.Claim.ABANDONED, swipe.secondPointer());
        assertEquals(MinimalEdgeSwipe.Claim.ABANDONED, swipe.move(900f, 20f));
    }

    @Test
    public void anInterruptedDragIsAbandonedAndTheLiftClearsIt() {
        MinimalEdgeSwipe swipe = armed(540f, 20f);
        swipe.move(700f, 20f);
        assertEquals(MinimalEdgeSwipe.Claim.ABANDONED, swipe.abandon());
        swipe.reset();
        assertFalse(swipe.isArmed());
        assertEquals(MinimalEdgeSwipe.Claim.NONE, swipe.claim());
        // The same instance serves the next gesture from scratch.
        assertTrue(swipe.down(540f, BOTTOM - 20f, LEFT, TOP, RIGHT, BOTTOM, BAND, CORNER, SLOP));
        assertEquals(MinimalEdgeSwipe.Claim.PENDING, swipe.claim());
    }

    @Test
    public void theBandIsAThumbWide() {
        assertEquals(32f, MinimalEdgeSwipe.BAND_DP, EPS);
    }
}
