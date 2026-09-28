package com.termux.app.wall;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * The border a press finds, the hold that claims it, and the drag that follows. A finger on a
 * border is the content's until it has held still through the hold — a tap, a scroll, a text
 * selection and a TUI's mouse drag all start the same way and all set off before it — and the
 * wall's from the hold to the lift, whichever of the four borders it started on.
 */
public class BorderDragTest {

    private static final float LEFT = 100f;
    private static final float TOP = 200f;
    private static final float RIGHT = 1000f;
    private static final float BOTTOM = 1800f;
    private static final float BAND = 72f;     // 24 dp at 3x
    private static final float CORNER = 120f;  // 40 dp at 3x
    private static final float SLOP = 24f;
    private static final int WIDTH = 1080;
    private static final float DENSITY = 3f;

    private static BorderDrag.Border at(float x, float y) {
        return BorderDrag.borderAt(x, y, LEFT, TOP, RIGHT, BOTTOM, BAND, CORNER);
    }

    private static BorderDrag armed(float x, float y) {
        BorderDrag drag = new BorderDrag();
        drag.down(x, y, LEFT, TOP, RIGHT, BOTTOM, BAND, CORNER, SLOP);
        return drag;
    }

    // ---- Where the borders are ---------------------------------------------------------------

    @Test
    public void eachEdgeIsItsOwnBorderToEitherSideOfTheLine() {
        assertEquals(BorderDrag.Border.TOP, at(550f, TOP + 1f));
        assertEquals("just outside the line is still the border", BorderDrag.Border.TOP,
            at(550f, TOP - BAND + 1f));
        assertEquals(BorderDrag.Border.TOP, at(550f, TOP + BAND - 1f));
        assertEquals(BorderDrag.Border.BOTTOM, at(550f, BOTTOM - 1f));
        assertEquals(BorderDrag.Border.BOTTOM, at(550f, BOTTOM + BAND - 1f));
        assertEquals(BorderDrag.Border.LEFT, at(LEFT + 1f, 1000f));
        assertEquals(BorderDrag.Border.LEFT, at(LEFT - BAND + 1f, 1000f));
        assertEquals(BorderDrag.Border.RIGHT, at(RIGHT - 1f, 1000f));
        assertEquals(BorderDrag.Border.RIGHT, at(RIGHT + BAND - 1f, 1000f));
    }

    @Test
    public void theInteriorAndTheOutsideAreNobodysBorder() {
        assertEquals(BorderDrag.Border.NONE, at(550f, 1000f));
        assertEquals("the first row inside the top band", BorderDrag.Border.NONE,
            at(550f, TOP + BAND + 1f));
        assertEquals(BorderDrag.Border.NONE, at(550f, TOP - BAND - 1f));
        assertEquals(BorderDrag.Border.NONE, at(LEFT - BAND - 1f, 1000f));
        assertEquals(BorderDrag.Border.NONE, at(RIGHT + BAND + 1f, 1000f));
        assertEquals("no band at all", BorderDrag.Border.NONE,
            BorderDrag.borderAt(550f, TOP + 1f, LEFT, TOP, RIGHT, BOTTOM, 0f, CORNER));
        assertEquals("no frame at all", BorderDrag.Border.NONE,
            BorderDrag.borderAt(550f, TOP + 1f, LEFT, TOP, LEFT, BOTTOM, BAND, CORNER));
    }

    @Test
    public void theCornerSquaresStayTheCornerTabs() {
        assertEquals(BorderDrag.Border.NONE, at(LEFT + 1f, TOP + 1f));
        assertEquals(BorderDrag.Border.NONE, at(LEFT + CORNER - 1f, TOP + 1f));
        assertEquals(BorderDrag.Border.NONE, at(RIGHT - 1f, TOP + CORNER - 1f));
        assertEquals(BorderDrag.Border.NONE, at(RIGHT - 1f, BOTTOM - 1f));
        assertEquals(BorderDrag.Border.NONE, at(LEFT + 1f, BOTTOM - CORNER + 1f));
        // The square reaches out past the frame as far as the band does.
        assertEquals(BorderDrag.Border.NONE, at(LEFT - 10f, TOP - 10f));
        // The first pixel past the square along either edge is the border again.
        assertEquals(BorderDrag.Border.TOP, at(LEFT + CORNER + 1f, TOP + 1f));
        assertEquals(BorderDrag.Border.LEFT, at(LEFT + 1f, TOP + CORNER + 1f));
    }

    @Test
    public void aSmallFrameKeepsABandNoWiderThanHalfOfItself() {
        // A 100 px frame: the band shrinks to 50 so the middle is still the top or the bottom.
        assertEquals(BorderDrag.Border.TOP,
            BorderDrag.borderAt(50f, 49f, 0f, 0f, 100f, 100f, BAND, 0f));
        assertEquals(BorderDrag.Border.BOTTOM,
            BorderDrag.borderAt(50f, 51f, 0f, 0f, 100f, 100f, BAND, 0f));
    }

    // ---- The hold ----------------------------------------------------------------------------

    @Test
    public void aPressOffTheBorderArmsNothing() {
        BorderDrag drag = armed(550f, 1000f);
        assertFalse(drag.isArmed());
        assertEquals(BorderDrag.Claim.NONE, drag.claim());
        assertEquals(BorderDrag.Border.NONE, drag.border());
        assertFalse("nothing to claim", drag.holdElapsed());
        assertEquals(BorderDrag.Claim.NONE, drag.move(600f, 1000f));
    }

    @Test
    public void aStillFingerIsClaimedWhenTheHoldElapses() {
        BorderDrag drag = armed(550f, TOP + 10f);
        assertTrue(drag.isArmed());
        assertEquals(BorderDrag.Claim.PENDING, drag.claim());
        assertEquals(BorderDrag.Border.TOP, drag.border());
        assertFalse(drag.isPaging());
        // Inside the slop is still holding still.
        assertEquals(BorderDrag.Claim.PENDING, drag.move(550f + SLOP, TOP + 10f));
        assertTrue("the claim, once", drag.holdElapsed());
        assertTrue(drag.isPaging());
        assertFalse("a second elapse claims nothing new", drag.holdElapsed());
    }

    @Test
    public void aFingerThatSetsOffBeforeTheHoldIsTheContents() {
        // Sideways: a swipe without a hold is not the wall's, whichever way it goes.
        BorderDrag sideways = armed(550f, TOP + 10f);
        assertEquals(BorderDrag.Claim.ABANDONED, sideways.move(550f + SLOP + 1f, TOP + 10f));
        assertFalse("the hold finds nothing to claim", sideways.holdElapsed());
        assertEquals(BorderDrag.Claim.ABANDONED, sideways.claim());
        assertTrue("armed to the lift, so the rest of the stream is known", sideways.isArmed());
        // Downward: a scroll or a selection.
        BorderDrag downward = armed(550f, TOP + 10f);
        assertEquals(BorderDrag.Claim.ABANDONED, downward.move(550f, TOP + 10f + SLOP + 1f));
        assertFalse(downward.holdElapsed());
    }

    @Test
    public void aSecondFingerBeforeTheHoldGivesTheGestureUpAndAfterItChangesNothing() {
        BorderDrag pinch = armed(550f, BOTTOM - 10f);
        assertEquals(BorderDrag.Claim.ABANDONED, pinch.secondPointer());
        assertFalse(pinch.holdElapsed());

        BorderDrag held = armed(550f, BOTTOM - 10f);
        assertTrue(held.holdElapsed());
        assertEquals(BorderDrag.Claim.PAGING, held.secondPointer());
        assertTrue(held.isPaging());
    }

    // ---- The drag ----------------------------------------------------------------------------

    @Test
    public void afterTheHoldTheDragIsTheFingersSidewaysTravel() {
        BorderDrag drag = armed(550f, TOP + 10f);
        assertTrue(drag.holdElapsed());
        assertEquals(BorderDrag.Claim.PAGING, drag.move(350f, TOP + 300f));
        assertEquals("sideways travel, whatever the finger did vertically", -200f,
            drag.travel(350f), 0.01f);
        assertEquals(150f, drag.travel(700f), 0.01f);
    }

    /**
     * Dragging right brings the place on the left in, dragging left the place on the right, on
     * every border alike: the wall only moves sideways, so a side border pages by how far the
     * finger went across it, not along it.
     */
    @Test
    public void everyBorderPagesTheSameWay() {
        float[][] presses = {
            {550f, TOP + 10f}, {550f, BOTTOM - 10f}, {LEFT + 10f, 1000f}, {RIGHT - 10f, 1000f},
        };
        BorderDrag.Border[] borders = {BorderDrag.Border.TOP, BorderDrag.Border.BOTTOM,
            BorderDrag.Border.LEFT, BorderDrag.Border.RIGHT};
        for (int i = 0; i < presses.length; i++) {
            BorderDrag drag = armed(presses[i][0], presses[i][1]);
            assertEquals(borders[i], drag.border());
            assertTrue(drag.holdElapsed());
            float half = WIDTH * 0.5f;
            // Pulled right by half a width: the wall shows the place to the left and lands on it.
            float right = PaneWallPolicy.offsetForDrag(drag.travel(presses[i][0] + half),
                WIDTH, true, true);
            assertEquals(borders[i] + " pulled right", -1,
                PaneWallPolicy.settle(right, 0f, WIDTH, DENSITY, true, true));
            // Pulled left: the place to the right.
            float left = PaneWallPolicy.offsetForDrag(drag.travel(presses[i][0] - half),
                WIDTH, true, true);
            assertEquals(borders[i] + " pulled left", 1,
                PaneWallPolicy.settle(left, 0f, WIDTH, DENSITY, true, true));
        }
    }

    @Test
    public void aQuickHoldAndFlickPagesOnSpeedAlone() {
        // Held, then flicked a short way: well short of the commit distance, over the speed.
        BorderDrag drag = armed(550f, TOP + 10f);
        assertTrue(drag.holdElapsed());
        float flick = PaneWallPolicy.DRAG_COMMIT_FLING_MIN_DISTANCE_DP * DENSITY + 10f;
        float offset = PaneWallPolicy.offsetForDrag(drag.travel(550f - flick), WIDTH, true, true);
        assertTrue(Math.abs(offset) < WIDTH * PaneWallPolicy.DRAG_COMMIT_FRACTION);
        assertEquals(1, PaneWallPolicy.settle(offset,
            -(PaneWallPolicy.DRAG_COMMIT_VELOCITY_DP_PER_SEC * DENSITY + 1f), WIDTH, DENSITY,
            true, true));
    }

    // ---- Cancel semantics --------------------------------------------------------------------

    @Test
    public void abandonSwallowsTheRestOfTheGestureWhicheverStateItWasIn() {
        BorderDrag pending = armed(550f, TOP + 10f);
        assertEquals(BorderDrag.Claim.ABANDONED, pending.abandon());
        assertFalse(pending.holdElapsed());

        BorderDrag paging = armed(550f, TOP + 10f);
        assertTrue(paging.holdElapsed());
        assertEquals(BorderDrag.Claim.ABANDONED, paging.abandon());
        assertFalse(paging.isPaging());
        assertTrue("still armed, so the stream is known to the lift", paging.isArmed());
        assertEquals(BorderDrag.Claim.ABANDONED, paging.move(100f, TOP + 10f));

        BorderDrag none = armed(550f, 1000f);
        assertEquals("nothing to abandon", BorderDrag.Claim.NONE, none.abandon());
    }

    // ---- The keyboard swipe ------------------------------------------------------------------

    private static final float REACH = 48f;          // KEYBOARD_REACH_DP at 3x
    private static final float COMMIT = 96f;         // KEYBOARD_COMMIT_DP at 3x
    private static final float FLING = 1500f;        // KEYBOARD_FLING_DP_PER_SEC at 3x

    private static BorderDrag keyboardArmed(float x, float y, boolean canPage) {
        BorderDrag drag = new BorderDrag();
        drag.down(x, y, LEFT, TOP, RIGHT, BOTTOM, BAND, CORNER, SLOP, canPage, REACH);
        return drag;
    }

    @Test
    public void theShippedKeyboardNumbersAreWhatTheseTestsAssume() {
        assertEquals(REACH, BorderDrag.KEYBOARD_REACH_DP * DENSITY, 0.01f);
        assertEquals(COMMIT, BorderDrag.KEYBOARD_COMMIT_DP * DENSITY, 0.01f);
        assertEquals(FLING, BorderDrag.KEYBOARD_FLING_DP_PER_SEC * DENSITY, 0.01f);
        assertTrue("the reach inside is no wider than the band",
            BorderDrag.KEYBOARD_REACH_DP <= BorderDrag.BAND_DP);
    }

    @Test
    public void anUpSwipeFromTheBottomBorderIsTheKeyboardsAndOpensIt() {
        BorderDrag drag = keyboardArmed(550f, BOTTOM - 10f, true);
        assertEquals(BorderDrag.Claim.PENDING, drag.claim());
        // Inside the slop it is still undecided.
        assertEquals(BorderDrag.Claim.PENDING, drag.move(550f, BOTTOM - 10f - SLOP));
        assertEquals(BorderDrag.Claim.KEYBOARD, drag.move(555f, BOTTOM - 10f - SLOP - 1f));
        assertTrue(drag.isKeyboardSwipe());
        assertFalse(drag.isPaging());
        assertFalse("claimed before the hold, the hold claims nothing", drag.holdElapsed());
        assertEquals(BorderDrag.Claim.KEYBOARD, drag.move(700f, BOTTOM - 400f));
        assertEquals(BorderDrag.KeyboardSwipe.OPEN,
            drag.keyboardRelease(BOTTOM - 10f - COMMIT, 0f, COMMIT, FLING));
    }

    @Test
    public void aDownSwipeClosesItFromEitherSideOfTheLine() {
        // From just inside the line.
        BorderDrag inside = keyboardArmed(550f, BOTTOM - 10f, true);
        assertEquals(BorderDrag.Claim.KEYBOARD, inside.move(550f, BOTTOM + 30f));
        assertEquals(BorderDrag.KeyboardSwipe.CLOSE,
            inside.keyboardRelease(BOTTOM - 10f + COMMIT, 0f, COMMIT, FLING));
        // From the air just below it, the whole band out.
        BorderDrag outside = keyboardArmed(550f, BOTTOM + BAND - 1f, true);
        assertEquals(BorderDrag.Border.BOTTOM, outside.border());
        assertEquals(BorderDrag.Claim.KEYBOARD, outside.move(550f, BOTTOM + BAND + 40f));
        assertEquals(BorderDrag.KeyboardSwipe.CLOSE,
            outside.keyboardRelease(BOTTOM + BAND - 1f + COMMIT, 0f, COMMIT, FLING));
    }

    @Test
    public void aShortSwipeNeedsAFlingTheSameWay() {
        float downY = BOTTOM - 10f;
        float shortUp = downY - COMMIT / 2f;
        BorderDrag drag = keyboardArmed(550f, downY, true);
        assertEquals(BorderDrag.Claim.KEYBOARD, drag.move(550f, shortUp));
        assertEquals("short and slow is nothing", BorderDrag.KeyboardSwipe.NONE,
            drag.keyboardRelease(shortUp, -FLING / 2f, COMMIT, FLING));
        assertEquals("short and flicked on up opens", BorderDrag.KeyboardSwipe.OPEN,
            drag.keyboardRelease(shortUp, -FLING - 1f, COMMIT, FLING));
        assertEquals("far but flicked back down is a change of mind",
            BorderDrag.KeyboardSwipe.NONE,
            drag.keyboardRelease(downY - COMMIT * 2f, FLING + 1f, COMMIT, FLING));
        assertEquals("back where it started is nothing", BorderDrag.KeyboardSwipe.NONE,
            drag.keyboardRelease(downY, -FLING * 2f, COMMIT, FLING));
    }

    @Test
    public void aSidewaysStartFromTheBottomBorderStaysTheContents() {
        BorderDrag drag = keyboardArmed(550f, BOTTOM - 10f, true);
        assertEquals(BorderDrag.Claim.ABANDONED, drag.move(550f + SLOP + 5f, BOTTOM - 10f - 4f));
        assertFalse(drag.isKeyboardSwipe());
        assertFalse(drag.holdElapsed());
        // A diagonal exactly as much across as up is not a clear vertical swipe either.
        BorderDrag diagonal = keyboardArmed(550f, BOTTOM - 10f, true);
        assertEquals(BorderDrag.Claim.ABANDONED, diagonal.move(550f + 30f, BOTTOM - 10f - 30f));
        assertEquals(BorderDrag.KeyboardSwipe.NONE,
            diagonal.keyboardRelease(BOTTOM - 10f - COMMIT, 0f, COMMIT, FLING));
    }

    @Test
    public void theHoldWinsOverALaterVerticalMove() {
        BorderDrag drag = keyboardArmed(550f, BOTTOM - 10f, true);
        assertTrue(drag.holdElapsed());
        assertEquals("held first, a vertical move is the drag's", BorderDrag.Claim.PAGING,
            drag.move(550f, BOTTOM - 200f));
        assertEquals(BorderDrag.KeyboardSwipe.NONE,
            drag.keyboardRelease(BOTTOM - 200f, 0f, COMMIT, FLING));
        // And a hold then a sideways drag still pages by the sideways travel.
        assertEquals(-300f, drag.travel(250f), 0.01f);
    }

    @Test
    public void onlyTheBottomBorderCarriesTheKeyboard() {
        float[][] presses = {{550f, TOP + 10f}, {LEFT + 10f, 1000f}, {RIGHT - 10f, 1000f}};
        for (float[] press : presses) {
            BorderDrag up = keyboardArmed(press[0], press[1], true);
            assertEquals(BorderDrag.Claim.ABANDONED, up.move(press[0], press[1] - 200f));
            BorderDrag down = keyboardArmed(press[0], press[1], true);
            assertEquals(BorderDrag.Claim.ABANDONED, down.move(press[0], press[1] + 200f));
        }
    }

    @Test
    public void theBandReachesOnlyARowInsideTheLine() {
        // Inside the border band but above the keyboard's reach: the border drag's, not the swipe's.
        float y = BOTTOM - REACH - 1f;
        BorderDrag drag = keyboardArmed(550f, y, true);
        assertEquals(BorderDrag.Border.BOTTOM, drag.border());
        assertEquals(BorderDrag.Claim.ABANDONED, drag.move(550f, y - 200f));
        // At the reach it is.
        BorderDrag edge = keyboardArmed(550f, BOTTOM - REACH, true);
        assertEquals(BorderDrag.Claim.KEYBOARD, edge.move(550f, BOTTOM - REACH - 200f));
    }

    @Test
    public void theKeyboardsBandIsKnownFromTheDownToTheLift() {
        // What the grabber lights for: a finger in the band, before it has gone anywhere.
        BorderDrag drag = keyboardArmed(550f, BOTTOM - 10f, true);
        assertTrue(drag.isKeyboardEligible());
        drag.move(550f, BOTTOM - 200f);
        assertTrue(drag.isKeyboardEligible());
        drag.reset();
        assertFalse(drag.isKeyboardEligible());
        // Above the reach, or on another border, it is not the keyboard's band.
        assertFalse(keyboardArmed(550f, BOTTOM - REACH - 1f, true).isKeyboardEligible());
        assertFalse(keyboardArmed(550f, TOP + 10f, true).isKeyboardEligible());
    }

    @Test
    public void theCornersStayTheCornerTabsForTheKeyboardToo() {
        BorderDrag left = keyboardArmed(LEFT + 10f, BOTTOM - 10f, true);
        assertFalse(left.isArmed());
        assertEquals(BorderDrag.Claim.NONE, left.move(LEFT + 10f, BOTTOM - 300f));
        BorderDrag right = keyboardArmed(RIGHT - 10f, BOTTOM + 10f, true);
        assertFalse(right.isArmed());
    }

    @Test
    public void withoutAKeyboardReachTheBottomBorderOnlyPages() {
        BorderDrag drag = armed(550f, BOTTOM - 10f);
        assertEquals(BorderDrag.Claim.ABANDONED, drag.move(550f, BOTTOM - 300f));
    }

    @Test
    public void aWallThatCannotPageArmsOnlyTheBottomBandAndLeavesAHoldToTheContent() {
        BorderDrag top = keyboardArmed(550f, TOP + 10f, false);
        assertFalse("no page to go to: the top border arms nothing", top.isArmed());
        assertEquals(BorderDrag.Border.NONE, top.border());
        BorderDrag above = keyboardArmed(550f, BOTTOM - REACH - 1f, false);
        assertFalse(above.isArmed());

        BorderDrag swipe = keyboardArmed(550f, BOTTOM - 10f, false);
        assertTrue(swipe.isArmed());
        assertEquals(BorderDrag.Claim.KEYBOARD, swipe.move(550f, BOTTOM - 200f));

        BorderDrag held = keyboardArmed(550f, BOTTOM - 10f, false);
        assertFalse("a still finger is the content's long press", held.holdElapsed());
        assertEquals(BorderDrag.Claim.ABANDONED, held.claim());
        assertEquals("and a move after the hold is still the content's",
            BorderDrag.Claim.ABANDONED, held.move(550f, BOTTOM - 200f));
    }

    @Test
    public void aSecondFingerOrAnAbandonEndsTheKeyboardSwipe() {
        BorderDrag pinch = keyboardArmed(550f, BOTTOM - 10f, true);
        assertEquals(BorderDrag.Claim.ABANDONED, pinch.secondPointer());
        assertEquals(BorderDrag.Claim.ABANDONED, pinch.move(550f, BOTTOM - 300f));

        BorderDrag claimed = keyboardArmed(550f, BOTTOM - 10f, true);
        assertEquals(BorderDrag.Claim.KEYBOARD, claimed.move(550f, BOTTOM - 300f));
        assertEquals("a finger joining a claimed swipe changes nothing",
            BorderDrag.Claim.KEYBOARD, claimed.secondPointer());
        assertEquals(BorderDrag.Claim.ABANDONED, claimed.abandon());
        assertEquals(BorderDrag.KeyboardSwipe.NONE,
            claimed.keyboardRelease(BOTTOM - 300f, 0f, COMMIT, FLING));
    }

    @Test
    public void resetPutsTheDragBackToNothing() {
        BorderDrag drag = armed(550f, TOP + 10f);
        assertTrue(drag.holdElapsed());
        drag.reset();
        assertFalse(drag.isArmed());
        assertFalse(drag.isPaging());
        assertEquals(BorderDrag.Claim.NONE, drag.claim());
        assertEquals(BorderDrag.Border.NONE, drag.border());
        assertFalse(drag.holdElapsed());
        // And the next press arms afresh.
        assertTrue(drag.down(LEFT + 5f, 1000f, LEFT, TOP, RIGHT, BOTTOM, BAND, CORNER, SLOP));
        assertEquals(BorderDrag.Border.LEFT, drag.border());
    }
}
