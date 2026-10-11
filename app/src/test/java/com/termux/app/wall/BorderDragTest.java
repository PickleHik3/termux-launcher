package com.termux.app.wall;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * The border a press finds, the hold that claims it, and the drag that follows. The border lives
 * in the air between the page and the wall's edge, never on the page: a press on the page's first
 * or last pixel is the content's. A finger on a border claims nothing until it has held still
 * through the hold, set off up or down from the top or bottom border, or set off sideways from the
 * air beyond the top or bottom edge, and is the wall's from there to the lift, whichever of the
 * four borders it started on.
 */
public class BorderDragTest {

    private static final float DENSITY = 3f;
    // The page, as the wall lays it out: Margin (12 dp at 3x) inside the wall on every side.
    private static final float LEFT = 100f;
    private static final float TOP = 200f;
    private static final float RIGHT = 1000f;
    private static final float BOTTOM = 1800f;
    private static final float GAP = 36f;          // 12 dp at 3x, the default Margin
    private static final float MIN_BAND = 18f;     // MIN_BAND_DP at 3x
    private static final float SIDE_REACH = 6f;    // SIDE_REACH_DP at 3x
    private static final float CORNER = 120f;      // 40 dp at 3x
    private static final float SLOP = 24f;
    private static final int WIDTH = 1080;
    private static final float MID_X = 550f;
    private static final float MID_Y = 1000f;
    /** No gesture strip of the phone's own reaching down into the top zone. */
    private static final float NO_LIMIT = Float.NEGATIVE_INFINITY;

    private static final float PILL_WIDTH = 360f;   // PILL_SWIPE_WIDTH_DP at 3x
    private static final float PILL_REACH = 48f;    // PILL_SWIPE_REACH_DP at 3x
    /** The pills' box on the default page, centred on its middle: x in [370, 730). */
    private static final float PILL_LEFT = MID_X - PILL_WIDTH / 2f;
    private static final float PILL_RIGHT = MID_X + PILL_WIDTH / 2f;

    private static final float COMMIT = 96f;         // KEYBOARD_COMMIT_DP at 3x
    private static final float FLING = 1500f;        // KEYBOARD_FLING_DP_PER_SEC at 3x

    /** The page with {@code gap} of air on every side. */
    private static BorderDrag.Zone zone(float gap) {
        return zone(gap, gap, gap, gap);
    }

    private static BorderDrag.Zone zone(float left, float top, float right, float bottom) {
        return new BorderDrag.Zone().page(LEFT, TOP, RIGHT, BOTTOM).gaps(left, top, right, bottom)
            .minBand(MIN_BAND).sideReach(SIDE_REACH).corner(CORNER);
    }

    private static BorderDrag.Border at(float x, float y) {
        return BorderDrag.borderAt(x, y, zone(GAP));
    }

    /** A press on the default page, with every gesture on and a place to page to. */
    private static BorderDrag armed(float x, float y) {
        return armed(x, y, zone(GAP), true, true, true, NO_LIMIT);
    }

    private static BorderDrag armed(float x, float y, BorderDrag.Zone zone, boolean canPage,
                                    boolean keyboard, boolean status, float topLimit) {
        BorderDrag drag = new BorderDrag();
        drag.down(x, y, zone, SLOP, canPage, keyboard, status, topLimit);
        return drag;
    }

    /** One press per side, just outside the page, away from the corners. */
    private static final float[][] IN_THE_GAP = {
        {MID_X, TOP - 1f}, {MID_X, BOTTOM}, {LEFT - 1f, MID_Y}, {RIGHT, MID_Y},
    };
    /**
     * One press per side, on the first pixel of the page past its zone: the page's own outermost
     * pixel at the top and bottom, the first one past the side reach on the left and right.
     */
    private static final float[][] ONE_PX_INSIDE = {
        {MID_X, TOP}, {MID_X, BOTTOM - 1f}, {LEFT + SIDE_REACH, MID_Y},
        {RIGHT - SIDE_REACH - 1f, MID_Y},
    };
    private static final BorderDrag.Border[] SIDES = {BorderDrag.Border.TOP,
        BorderDrag.Border.BOTTOM, BorderDrag.Border.LEFT, BorderDrag.Border.RIGHT};

    @Test
    public void theShippedNumbersAreWhatTheseTestsAssume() {
        assertEquals(MIN_BAND, BorderDrag.MIN_BAND_DP * DENSITY, 0.01f);
        assertEquals(SIDE_REACH, BorderDrag.SIDE_REACH_DP * DENSITY, 0.01f);
        assertTrue("the side reach is thinner than the band", SIDE_REACH < MIN_BAND);
        assertEquals(COMMIT, BorderDrag.KEYBOARD_COMMIT_DP * DENSITY, 0.01f);
        assertEquals(FLING, BorderDrag.KEYBOARD_FLING_DP_PER_SEC * DENSITY, 0.01f);
        assertEquals(PILL_WIDTH, BorderDrag.PILL_SWIPE_WIDTH_DP * DENSITY, 0.01f);
        assertEquals(PILL_REACH, BorderDrag.PILL_SWIPE_REACH_DP * DENSITY, 0.01f);
        assertTrue("the default Margin leaves more air than the minimum band", GAP >= MIN_BAND);
    }

    // ---- Where the borders are: outside the page ---------------------------------------------

    @Test
    public void aPressInTheGapOnEachSideIsThatSidesBorderAndAHoldPages() {
        for (int i = 0; i < SIDES.length; i++) {
            float[] p = IN_THE_GAP[i];
            assertEquals(SIDES[i], at(p[0], p[1]));
            BorderDrag drag = armed(p[0], p[1]);
            assertTrue(SIDES[i] + " arms", drag.isArmed());
            assertEquals(SIDES[i], drag.border());
            assertTrue(SIDES[i] + " pages on the hold", drag.holdElapsed());
            assertTrue(drag.isPaging());
        }
    }

    @Test
    public void thePagePastEachSidesZoneIsNeverArmed() {
        for (int i = 0; i < SIDES.length; i++) {
            float[] p = ONE_PX_INSIDE[i];
            assertEquals(SIDES[i] + ": 1px inside", BorderDrag.Border.NONE, at(p[0], p[1]));
            BorderDrag drag = armed(p[0], p[1]);
            assertFalse(SIDES[i] + ": nothing arms on the page", drag.isArmed());
            assertFalse(drag.holdElapsed());
            assertEquals(BorderDrag.Claim.NONE, drag.move(p[0], p[1] - 200f));
            assertEquals(BorderDrag.Claim.NONE, drag.move(p[0], p[1] + 200f));
        }
        assertEquals("the interior", BorderDrag.Border.NONE, at(MID_X, MID_Y));
    }

    @Test
    public void theZoneRunsOutToTheWallsEdgeAndNoFurther() {
        assertEquals(BorderDrag.Border.TOP, at(MID_X, TOP - GAP));
        assertEquals(BorderDrag.Border.BOTTOM, at(MID_X, BOTTOM + GAP));
        assertEquals(BorderDrag.Border.LEFT, at(LEFT - GAP, MID_Y));
        assertEquals(BorderDrag.Border.RIGHT, at(RIGHT + GAP, MID_Y));
        assertEquals(BorderDrag.Border.NONE, at(MID_X, TOP - GAP - 1f));
        assertEquals(BorderDrag.Border.NONE, at(MID_X, BOTTOM + GAP + 1f));
        assertEquals(BorderDrag.Border.NONE, at(LEFT - GAP - 1f, MID_Y));
        assertEquals(BorderDrag.Border.NONE, at(RIGHT + GAP + 1f, MID_Y));
        // Each side's own air: a deep strip below the page, as when the dock stands well clear.
        BorderDrag.Zone deep = zone(GAP, GAP, GAP, 300f);
        assertEquals(BorderDrag.Border.BOTTOM, BorderDrag.borderAt(MID_X, BOTTOM + 300f, deep));
        assertEquals(BorderDrag.Border.NONE, BorderDrag.borderAt(MID_X, BOTTOM + 301f, deep));
        assertEquals(BorderDrag.Border.NONE, BorderDrag.borderAt(LEFT - GAP - 1f, MID_Y, deep));
    }

    @Test
    public void theCornerSquaresStayTheCornerTabsAllTheWayOut() {
        BorderDrag.Border none = BorderDrag.Border.NONE;
        // Inside the page's corners, and out through the air beyond them.
        assertEquals(none, at(LEFT - 1f, TOP - 1f));
        assertEquals(none, at(LEFT + CORNER - 1f, TOP - 1f));
        assertEquals(none, at(LEFT - 1f, TOP + CORNER - 1f));
        assertEquals(none, at(RIGHT, TOP - GAP));
        assertEquals(none, at(RIGHT + GAP, BOTTOM - CORNER));
        assertEquals(none, at(LEFT + 10f, BOTTOM + GAP));
        assertEquals(none, at(RIGHT - CORNER, BOTTOM));
        // The first pixel past the square along either edge is the border again.
        assertEquals(BorderDrag.Border.TOP, at(LEFT + CORNER, TOP - 1f));
        assertEquals(BorderDrag.Border.LEFT, at(LEFT - 1f, TOP + CORNER));
        assertEquals(BorderDrag.Border.BOTTOM, at(RIGHT - CORNER - 1f, BOTTOM));
        // And no corner arms any gesture.
        assertFalse(armed(LEFT - 1f, TOP - 1f).isArmed());
        assertFalse(armed(RIGHT, BOTTOM).isArmed());
        assertFalse(armed(LEFT + 10f, BOTTOM + 10f).isArmed());
        assertFalse(armed(RIGHT - 10f, TOP - 10f).isArmed());
    }

    @Test
    public void noPageNoBorder() {
        BorderDrag.Zone flat = new BorderDrag.Zone().page(LEFT, TOP, LEFT, BOTTOM)
            .gaps(GAP, GAP, GAP, GAP).minBand(MIN_BAND).corner(CORNER);
        assertEquals(BorderDrag.Border.NONE, BorderDrag.borderAt(LEFT - 1f, MID_Y, flat));
        BorderDrag.Zone noAir = new BorderDrag.Zone().page(LEFT, TOP, RIGHT, BOTTOM)
            .corner(CORNER);
        assertEquals("no air and no minimum band: no border at all", BorderDrag.Border.NONE,
            BorderDrag.borderAt(MID_X, TOP, noAir));
    }

    // ---- The minimum band, where Margin leaves too little air --------------------------------

    @Test
    public void atMarginZeroTheBandReachesInsideByTheMinimumAndNoMore() {
        BorderDrag.Zone flush = zone(0f);
        float[][] inside = {
            {MID_X, TOP + MIN_BAND - 1f}, {MID_X, BOTTOM - MIN_BAND},
            {LEFT + MIN_BAND - 1f, MID_Y}, {RIGHT - MIN_BAND, MID_Y},
        };
        float[][] beyond = {
            {MID_X, TOP + MIN_BAND}, {MID_X, BOTTOM - MIN_BAND - 1f},
            {LEFT + MIN_BAND, MID_Y}, {RIGHT - MIN_BAND - 1f, MID_Y},
        };
        for (int i = 0; i < SIDES.length; i++) {
            assertEquals(SIDES[i] + " within the band", SIDES[i],
                BorderDrag.borderAt(inside[i][0], inside[i][1], flush));
            assertEquals(SIDES[i] + " past the band", BorderDrag.Border.NONE,
                BorderDrag.borderAt(beyond[i][0], beyond[i][1], flush));
            assertEquals(MIN_BAND, flush.inside(SIDES[i]), 0.01f);
        }
        // The keyboard swipe and the hold never vanish at Margin 0.
        BorderDrag swipe = armed(MID_X, BOTTOM - 1f, flush, true, true, true, NO_LIMIT);
        assertEquals(BorderDrag.Claim.KEYBOARD, swipe.move(MID_X, BOTTOM - 200f));
        assertTrue(armed(MID_X, TOP + 1f, flush, true, true, true, NO_LIMIT).holdElapsed());
    }

    @Test
    public void aThinGapReachesInsideByExactlyItsShortfall() {
        float gap = 8f;  // thinner than the band: 10 px short of it
        BorderDrag.Zone thin = zone(gap);
        float shortfall = MIN_BAND - gap;
        for (BorderDrag.Border side : SIDES) {
            assertEquals(side + "", shortfall, thin.inside(side), 0.01f);
        }
        assertEquals(BorderDrag.Border.TOP,
            BorderDrag.borderAt(MID_X, TOP + shortfall - 1f, thin));
        assertEquals(BorderDrag.Border.NONE, BorderDrag.borderAt(MID_X, TOP + shortfall, thin));
        assertEquals(BorderDrag.Border.BOTTOM,
            BorderDrag.borderAt(MID_X, BOTTOM - shortfall, thin));
        assertEquals(BorderDrag.Border.NONE,
            BorderDrag.borderAt(MID_X, BOTTOM - shortfall - 1f, thin));
        assertEquals(BorderDrag.Border.LEFT,
            BorderDrag.borderAt(LEFT + shortfall - 1f, MID_Y, thin));
        assertEquals(BorderDrag.Border.NONE, BorderDrag.borderAt(LEFT + shortfall, MID_Y, thin));
        assertEquals(BorderDrag.Border.RIGHT,
            BorderDrag.borderAt(RIGHT - shortfall, MID_Y, thin));
        assertEquals(BorderDrag.Border.NONE,
            BorderDrag.borderAt(RIGHT - shortfall - 1f, MID_Y, thin));
        // The air itself is still the border, out to the wall's edge.
        assertEquals(BorderDrag.Border.TOP, BorderDrag.borderAt(MID_X, TOP - gap, thin));
        assertEquals(BorderDrag.Border.NONE, BorderDrag.borderAt(MID_X, TOP - gap - 1f, thin));
    }

    @Test
    public void eachSidesShortfallIsItsOwn() {
        // Air at the top and bottom, none at the sides (a page flush with the screen's edges).
        BorderDrag.Zone sides = zone(0f, GAP, 0f, GAP);
        assertEquals(0f, sides.inside(BorderDrag.Border.TOP), 0.01f);
        assertEquals(0f, sides.inside(BorderDrag.Border.BOTTOM), 0.01f);
        assertEquals(MIN_BAND, sides.inside(BorderDrag.Border.LEFT), 0.01f);
        assertEquals(BorderDrag.Border.NONE, BorderDrag.borderAt(MID_X, TOP, sides));
        assertEquals(BorderDrag.Border.LEFT, BorderDrag.borderAt(LEFT + 1f, MID_Y, sides));
    }

    @Test
    public void theBandNeverReachesPastTheMiddleOfASmallPage() {
        BorderDrag.Zone tiny = new BorderDrag.Zone().page(0f, 0f, 20f, 20f).gaps(0f, 0f, 0f, 0f)
            .minBand(MIN_BAND).corner(0f);
        assertEquals(10f, tiny.inside(BorderDrag.Border.TOP), 0.01f);
        assertEquals(BorderDrag.Border.TOP, BorderDrag.borderAt(10f, 9f, tiny));
        assertEquals(BorderDrag.Border.BOTTOM, BorderDrag.borderAt(10f, 11f, tiny));
    }

    @Test
    public void theZonesBoundsAreTheStripFromTheWallsEdgeToThePagesLessTheCorners() {
        float[] out = new float[4];
        BorderDrag.Zone zone = zone(GAP);
        // The side strips run on over the page's edge by the side reach.
        assertTrue(zone.bounds(BorderDrag.Border.LEFT, out));
        assertArrayEquals(new float[] {LEFT - GAP, TOP + CORNER, LEFT + SIDE_REACH,
            BOTTOM - CORNER}, out, 0.01f);
        assertTrue(zone.bounds(BorderDrag.Border.BOTTOM, out));
        assertArrayEquals(new float[] {LEFT + CORNER, BOTTOM, RIGHT - CORNER, BOTTOM + GAP}, out,
            0.01f);
        assertTrue(zone.bounds(BorderDrag.Border.TOP, out));
        assertArrayEquals(new float[] {LEFT + CORNER, TOP - GAP, RIGHT - CORNER, TOP}, out, 0.01f);
        assertTrue(zone.bounds(BorderDrag.Border.RIGHT, out));
        assertArrayEquals(new float[] {RIGHT - SIDE_REACH, TOP + CORNER, RIGHT + GAP,
            BOTTOM - CORNER}, out, 0.01f);
        // At Margin 0 the strip is the band inside the page.
        assertTrue(zone(0f).bounds(BorderDrag.Border.LEFT, out));
        assertArrayEquals(new float[] {LEFT, TOP + CORNER, LEFT + MIN_BAND, BOTTOM - CORNER},
            out, 0.01f);
        assertFalse("no air, no band and no reach: no strip",
            zone(0f).minBand(0f).sideReach(0f).bounds(BorderDrag.Border.LEFT, out));
    }

    @Test
    public void onlyAPressOnABorderOfAWallThatCanPageHasAHoldThatPages() {
        assertTrue(armed(LEFT - 1f, MID_Y).holdPages());
        assertTrue(armed(MID_X, BOTTOM).holdPages());
        BorderDrag onePlace = armed(MID_X, BOTTOM, zone(GAP), false, true, true, NO_LIMIT);
        assertTrue("armed for the keyboard's swipe", onePlace.isArmed());
        assertFalse(onePlace.holdPages());
        BorderDrag.Zone pills = zone(GAP).pills(MID_X, PILL_WIDTH, PILL_REACH);
        BorderDrag byPill = armed(MID_X, BOTTOM - 1f, pills, true, true, true, NO_LIMIT);
        assertTrue("armed for the keyboard's swipe", byPill.isArmed());
        assertFalse("never pages from the pill's box", byPill.holdPages());
        BorderDrag lifted = armed(LEFT - 1f, MID_Y);
        lifted.reset();
        assertFalse(lifted.holdPages());
    }

    // ---- The side reach, into the page's clearance -------------------------------------------

    @Test
    public void theSideZonesReachTwoDpIntoThePageAndTheTopAndBottomDoNot() {
        BorderDrag.Zone zone = zone(GAP);
        assertEquals(SIDE_REACH, zone.inside(BorderDrag.Border.LEFT), 0.01f);
        assertEquals(SIDE_REACH, zone.inside(BorderDrag.Border.RIGHT), 0.01f);
        assertEquals(0f, zone.inside(BorderDrag.Border.TOP), 0.01f);
        assertEquals(0f, zone.inside(BorderDrag.Border.BOTTOM), 0.01f);
        float threeDp = 3f * DENSITY;
        // The last pixel of the 2dp is the side's border, and its hold pages.
        float[][] within = {{LEFT + SIDE_REACH - 1f, MID_Y}, {RIGHT - SIDE_REACH, MID_Y}};
        // The first pixel past it, and 3dp in, are the page's.
        float[][] past = {{LEFT + SIDE_REACH, MID_Y}, {RIGHT - SIDE_REACH - 1f, MID_Y},
            {LEFT + threeDp, MID_Y}, {RIGHT - threeDp - 1f, MID_Y}};
        BorderDrag.Border[] sides = {BorderDrag.Border.LEFT, BorderDrag.Border.RIGHT};
        for (int i = 0; i < within.length; i++) {
            BorderDrag drag = armed(within[i][0], within[i][1]);
            assertTrue(sides[i] + " arms 2dp in", drag.isArmed());
            assertEquals(sides[i], drag.border());
            assertTrue(sides[i] + " pages on the hold", drag.holdElapsed());
        }
        for (float[] p : past) {
            assertFalse("nothing arms at " + p[0], armed(p[0], p[1]).isArmed());
        }
        // The top and bottom keep the page's edge rows for the content.
        assertEquals(BorderDrag.Border.NONE, at(MID_X, TOP));
        assertEquals(BorderDrag.Border.NONE, at(MID_X, BOTTOM - 1f));
    }

    @Test
    public void aThinSideGapReachesInByTheLargerOfItsShortfallAndTheSideReach() {
        // 15 px of air: 3 px short of the band, less than the side reach.
        BorderDrag.Zone slight = zone(15f);
        assertEquals(SIDE_REACH, slight.inside(BorderDrag.Border.LEFT), 0.01f);
        assertEquals(SIDE_REACH, slight.inside(BorderDrag.Border.RIGHT), 0.01f);
        assertEquals(3f, slight.inside(BorderDrag.Border.TOP), 0.01f);
        // 8 px of air: 10 px short, more than the side reach, and the two never add.
        BorderDrag.Zone thin = zone(8f);
        assertEquals(MIN_BAND - 8f, thin.inside(BorderDrag.Border.LEFT), 0.01f);
        assertEquals(MIN_BAND - 8f, thin.inside(BorderDrag.Border.RIGHT), 0.01f);
        assertEquals(BorderDrag.Border.NONE,
            BorderDrag.borderAt(LEFT + MIN_BAND - 8f, MID_Y, thin));
    }

    // ---- The hold ----------------------------------------------------------------------------

    @Test
    public void aPressOffTheBorderArmsNothing() {
        BorderDrag drag = armed(MID_X, MID_Y);
        assertFalse(drag.isArmed());
        assertEquals(BorderDrag.Claim.NONE, drag.claim());
        assertEquals(BorderDrag.Border.NONE, drag.border());
        assertFalse("nothing to claim", drag.holdElapsed());
        assertEquals(BorderDrag.Claim.NONE, drag.move(600f, MID_Y));
    }

    @Test
    public void aStillFingerIsClaimedWhenTheHoldElapses() {
        BorderDrag drag = armed(MID_X, TOP - 10f);
        assertTrue(drag.isArmed());
        assertEquals(BorderDrag.Claim.PENDING, drag.claim());
        assertEquals(BorderDrag.Border.TOP, drag.border());
        assertFalse(drag.isPaging());
        // Inside the slop is still holding still.
        assertEquals(BorderDrag.Claim.PENDING, drag.move(MID_X + SLOP, TOP - 10f));
        assertTrue("the claim, once", drag.holdElapsed());
        assertTrue(drag.isPaging());
        assertFalse("a second elapse claims nothing new", drag.holdElapsed());
    }

    @Test
    public void aFingerThatSetsOffSidewaysFromASideBorderBeforeTheHoldIsGivenUp() {
        float[][] presses = {{LEFT - 10f, MID_Y}, {RIGHT + 10f, MID_Y}, {LEFT + 2f, MID_Y}};
        for (float[] p : presses) {
            BorderDrag sideways = armed(p[0], p[1]);
            assertFalse("the back swipe's edges never slide", sideways.slidePages());
            assertEquals(BorderDrag.Claim.ABANDONED, sideways.move(p[0] + SLOP + 1f, p[1]));
            assertFalse("the hold finds nothing to claim", sideways.holdElapsed());
            assertEquals(BorderDrag.Claim.ABANDONED, sideways.claim());
            assertTrue("armed to the lift, so the rest of the stream is known",
                sideways.isArmed());
        }
        // Without the status swipe, a vertical start from the top is given up as well.
        BorderDrag downward = armed(MID_X, TOP - 10f, zone(GAP), true, true, false, NO_LIMIT);
        assertEquals(BorderDrag.Claim.ABANDONED, downward.move(MID_X, TOP - 10f + SLOP + 1f));
        assertFalse(downward.holdElapsed());
    }

    @Test
    public void aSecondFingerBeforeTheHoldGivesTheGestureUpAndAfterItChangesNothing() {
        BorderDrag pinch = armed(MID_X, BOTTOM + 10f);
        assertEquals(BorderDrag.Claim.ABANDONED, pinch.secondPointer());
        assertFalse(pinch.holdElapsed());

        BorderDrag held = armed(MID_X, BOTTOM + 10f);
        assertTrue(held.holdElapsed());
        assertEquals(BorderDrag.Claim.PAGING, held.secondPointer());
        assertTrue(held.isPaging());
    }

    // ---- The drag ----------------------------------------------------------------------------

    @Test
    public void afterTheHoldTheDragIsTheFingersSidewaysTravel() {
        BorderDrag drag = armed(MID_X, TOP - 10f);
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
        for (int i = 0; i < SIDES.length; i++) {
            float[] press = IN_THE_GAP[i];
            BorderDrag drag = armed(press[0], press[1]);
            assertEquals(SIDES[i], drag.border());
            assertTrue(drag.holdElapsed());
            float half = WIDTH * 0.5f;
            // Pulled right by half a width: the wall shows the place to the left and lands on it.
            float right = PaneWallPolicy.offsetForDrag(drag.travel(press[0] + half),
                WIDTH, true, true);
            assertEquals(SIDES[i] + " pulled right", -1,
                PaneWallPolicy.settle(right, 0f, WIDTH, DENSITY, true, true));
            // Pulled left: the place to the right.
            float left = PaneWallPolicy.offsetForDrag(drag.travel(press[0] - half),
                WIDTH, true, true);
            assertEquals(SIDES[i] + " pulled left", 1,
                PaneWallPolicy.settle(left, 0f, WIDTH, DENSITY, true, true));
        }
    }

    @Test
    public void aQuickHoldAndFlickPagesOnSpeedAlone() {
        // Held, then flicked a short way: well short of the commit distance, over the speed.
        BorderDrag drag = armed(MID_X, TOP - 10f);
        assertTrue(drag.holdElapsed());
        float flick = PaneWallPolicy.DRAG_COMMIT_FLING_MIN_DISTANCE_DP * DENSITY + 10f;
        float offset = PaneWallPolicy.offsetForDrag(drag.travel(MID_X - flick), WIDTH, true, true);
        assertTrue(Math.abs(offset) < WIDTH * PaneWallPolicy.DRAG_COMMIT_FRACTION);
        assertEquals(1, PaneWallPolicy.settle(offset,
            -(PaneWallPolicy.DRAG_COMMIT_VELOCITY_DP_PER_SEC * DENSITY + 1f), WIDTH, DENSITY,
            true, true));
    }

    @Test
    public void aWallThatCannotPageArmsOnlyItsSwipes() {
        // No place to go and no swipes: nothing arms anywhere.
        for (float[] p : IN_THE_GAP) {
            assertFalse(armed(p[0], p[1], zone(GAP), false, false, false, NO_LIMIT).isArmed());
        }
        // With the swipes on, the bottom and top arm for them, the sides arm nothing.
        assertFalse(armed(LEFT - 1f, MID_Y, zone(GAP), false, true, true, NO_LIMIT).isArmed());
        assertFalse(armed(RIGHT, MID_Y, zone(GAP), false, true, true, NO_LIMIT).isArmed());
        BorderDrag swipe = armed(MID_X, BOTTOM + 4f, zone(GAP), false, true, true, NO_LIMIT);
        assertTrue(swipe.isArmed());
        assertEquals(BorderDrag.Claim.KEYBOARD, swipe.move(MID_X, BOTTOM - 200f));
        // A still finger there pages nothing and is given up.
        BorderDrag held = armed(MID_X, BOTTOM + 4f, zone(GAP), false, true, true, NO_LIMIT);
        assertFalse(held.holdElapsed());
        assertEquals(BorderDrag.Claim.ABANDONED, held.claim());
        assertEquals("and a move after the hold is still not a swipe",
            BorderDrag.Claim.ABANDONED, held.move(MID_X, BOTTOM - 200f));
    }

    // ---- Cancel semantics --------------------------------------------------------------------

    @Test
    public void abandonSwallowsTheRestOfTheGestureWhicheverStateItWasIn() {
        BorderDrag pending = armed(MID_X, TOP - 10f);
        assertEquals(BorderDrag.Claim.ABANDONED, pending.abandon());
        assertFalse(pending.holdElapsed());

        BorderDrag paging = armed(MID_X, TOP - 10f);
        assertTrue(paging.holdElapsed());
        assertEquals(BorderDrag.Claim.ABANDONED, paging.abandon());
        assertFalse(paging.isPaging());
        assertTrue("still armed, so the stream is known to the lift", paging.isArmed());
        assertEquals(BorderDrag.Claim.ABANDONED, paging.move(100f, TOP - 10f));

        BorderDrag none = armed(MID_X, MID_Y);
        assertEquals("nothing to abandon", BorderDrag.Claim.NONE, none.abandon());
    }

    @Test
    public void resetPutsTheDragBackToNothing() {
        BorderDrag drag = armed(MID_X, TOP - 10f);
        assertTrue(drag.holdElapsed());
        drag.reset();
        assertFalse(drag.isArmed());
        assertFalse(drag.isPaging());
        assertEquals(BorderDrag.Claim.NONE, drag.claim());
        assertEquals(BorderDrag.Border.NONE, drag.border());
        assertFalse(drag.holdElapsed());
        // And the next press arms afresh.
        assertTrue(drag.down(LEFT - 5f, MID_Y, zone(GAP), SLOP, true, true, true, NO_LIMIT));
        assertEquals(BorderDrag.Border.LEFT, drag.border());
    }

    // ---- The keyboard swipe off the bottom border --------------------------------------------

    @Test
    public void anUpSwipeFromTheBottomGapIsTheKeyboardsAndOpensIt() {
        float downY = BOTTOM + 10f;
        BorderDrag drag = armed(MID_X, downY);
        assertTrue(drag.isKeyboardEligible());
        assertFalse(drag.isStatusEligible());
        assertEquals(BorderDrag.Claim.PENDING, drag.claim());
        // Inside the slop it is still undecided.
        assertEquals(BorderDrag.Claim.PENDING, drag.move(MID_X, downY - SLOP));
        assertEquals(BorderDrag.Claim.KEYBOARD, drag.move(MID_X + 5f, downY - SLOP - 1f));
        assertTrue(drag.isKeyboardSwipe());
        assertFalse(drag.isPaging());
        assertFalse("claimed before the hold, the hold claims nothing", drag.holdElapsed());
        assertEquals(BorderDrag.Claim.KEYBOARD, drag.move(700f, BOTTOM - 400f));
        assertEquals(BorderDrag.KeyboardSwipe.OPEN,
            drag.keyboardRelease(downY - COMMIT, 0f, COMMIT, FLING));
    }

    @Test
    public void aDownSwipeFromTheBottomGapClosesIt() {
        BorderDrag drag = armed(MID_X, BOTTOM + GAP - 1f);
        assertEquals(BorderDrag.Border.BOTTOM, drag.border());
        assertEquals(BorderDrag.Claim.KEYBOARD, drag.move(MID_X, BOTTOM + GAP + 40f));
        assertEquals(BorderDrag.KeyboardSwipe.CLOSE,
            drag.keyboardRelease(BOTTOM + GAP - 1f + COMMIT, 0f, COMMIT, FLING));
    }

    @Test
    public void aShortSwipeNeedsAFlingTheSameWay() {
        float downY = BOTTOM + 10f;
        float shortUp = downY - COMMIT / 2f;
        BorderDrag drag = armed(MID_X, downY);
        assertEquals(BorderDrag.Claim.KEYBOARD, drag.move(MID_X, shortUp));
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
    public void aSidewaysStartFromTheBottomGapPagesAtOnce() {
        BorderDrag drag = armed(MID_X, BOTTOM + 10f);
        assertTrue(drag.slidePages());
        float claimX = MID_X + SLOP + 5f;
        assertEquals(BorderDrag.Claim.PAGING, drag.move(claimX, BOTTOM + 10f - 4f));
        assertTrue("the wall's, with no hold", drag.isPaging());
        assertFalse(drag.isKeyboardSwipe());
        assertFalse("claimed before the hold, the hold claims nothing new", drag.holdElapsed());
        assertEquals("measured from the claim, so the wall does not jump by the slop", 0f,
            drag.travel(claimX), 0.01f);
        assertEquals(-300f, drag.travel(claimX - 300f), 0.01f);
        assertEquals("a vertical move after the claim is still the drag's",
            BorderDrag.Claim.PAGING, drag.move(claimX, BOTTOM - 400f));
        assertEquals(BorderDrag.KeyboardSwipe.NONE,
            drag.keyboardRelease(BOTTOM - 400f, 0f, COMMIT, FLING));
        // A diagonal exactly as much across as up is not a clear vertical swipe: it slides.
        BorderDrag diagonal = armed(MID_X, BOTTOM + 10f);
        assertEquals(BorderDrag.Claim.PAGING, diagonal.move(MID_X + 30f, BOTTOM + 10f - 30f));
        assertEquals(BorderDrag.KeyboardSwipe.NONE,
            diagonal.keyboardRelease(BOTTOM + 10f - COMMIT, 0f, COMMIT, FLING));
    }

    /**
     * Where the content could be under the finger, or the back swipe or the phone's own strip
     * share the edge, sideways is never taken: only the air beyond the top and bottom edges,
     * below the strip, on a wall with somewhere to go.
     */
    @Test
    public void onlyTheTopAndBottomAirBeyondThePageSlides() {
        float[][] slide = {{MID_X, TOP - 1f}, {MID_X, TOP - GAP}, {MID_X, BOTTOM},
            {MID_X, BOTTOM + GAP}};
        for (float[] p : slide) {
            assertTrue(p[1] + " slides", armed(p[0], p[1]).slidePages());
        }
        // The minimum band where it reaches into the page, at Margin 0 or a thin gap.
        BorderDrag.Zone flush = zone(0f);
        float[][] band = {{MID_X, TOP + 1f}, {MID_X, BOTTOM - 1f}};
        for (float[] p : band) {
            BorderDrag drag = armed(p[0], p[1], flush, true, true, true, NO_LIMIT);
            assertTrue("the band is armed", drag.isArmed());
            assertFalse("but its sideways drag is the content's", drag.slidePages());
            assertEquals(BorderDrag.Claim.ABANDONED, drag.move(p[0] + 100f, p[1]));
        }
        BorderDrag.Zone thin = zone(GAP, 6f, GAP, 6f);
        assertTrue("its air still slides", armed(MID_X, TOP - 3f, thin, true, true, true,
            NO_LIMIT).slidePages());
        assertFalse("the shortfall inside does not", armed(MID_X, TOP + 3f, thin, true, true,
            true, NO_LIMIT).slidePages());
        // The boxes by the pills, inside the page.
        assertFalse(pressed(MID_X, BOTTOM - 20f).slidePages());
        assertFalse(pressed(MID_X, TOP + 20f).slidePages());
        // A wall of one place: the bottom and top swipes arm, sideways goes to the content.
        BorderDrag onePlace = armed(MID_X, BOTTOM + 10f, zone(GAP), false, true, true, NO_LIMIT);
        assertTrue(onePlace.isArmed());
        assertFalse(onePlace.slidePages());
        assertEquals(BorderDrag.Claim.ABANDONED, onePlace.move(MID_X + 100f, BOTTOM + 10f));
        // The phone's own strip at the top: its hold pages as before, its slide is never taken.
        float limit = TOP - 20f;
        BorderDrag inStrip = armed(MID_X, TOP - 30f, zone(GAP), true, true, true, limit);
        assertFalse(inStrip.slidePages());
        assertEquals(BorderDrag.Claim.ABANDONED, inStrip.move(MID_X + 100f, TOP - 30f));
        assertTrue("just below it slides", armed(MID_X, TOP - 10f, zone(GAP), true, true, true,
            limit).slidePages());
        // With both swipes off, the slide is still there, and the strip still never slides.
        assertTrue(armed(MID_X, BOTTOM + 10f, zone(GAP), true, false, false, NO_LIMIT)
            .slidePages());
        assertFalse(armed(MID_X, TOP - 30f, zone(GAP), true, false, false, limit).slidePages());
    }

    @Test
    public void aSlidePressStillSwipesUpOrDownAndStillPagesOnTheHold() {
        BorderDrag up = armed(MID_X, BOTTOM + 10f);
        assertTrue(up.slidePages());
        assertEquals(BorderDrag.Claim.KEYBOARD, up.move(MID_X + 5f, BOTTOM - 200f));
        BorderDrag down = armed(MID_X, TOP - 10f);
        assertTrue(down.slidePages());
        assertEquals(BorderDrag.Claim.STATUS, down.move(MID_X - 5f, TOP + 200f));
        // Held still: the hold pages exactly as before, measured from the DOWN.
        BorderDrag held = armed(MID_X, BOTTOM + 10f);
        assertEquals(BorderDrag.Claim.PENDING, held.move(MID_X + SLOP, BOTTOM + 10f));
        assertTrue(held.holdElapsed());
        assertEquals(-200f, held.travel(MID_X - 200f), 0.01f);
        // A second finger before it goes anywhere is still a pinch.
        assertEquals(BorderDrag.Claim.ABANDONED, armed(MID_X, BOTTOM + 10f).secondPointer());
        // And reset forgets it.
        held.reset();
        assertFalse(held.slidePages());
    }

    @Test
    public void theHoldWinsOverALaterVerticalMove() {
        BorderDrag drag = armed(MID_X, BOTTOM + 10f);
        assertTrue(drag.holdElapsed());
        assertEquals("held first, a vertical move is the drag's", BorderDrag.Claim.PAGING,
            drag.move(MID_X, BOTTOM - 200f));
        assertEquals(BorderDrag.KeyboardSwipe.NONE,
            drag.keyboardRelease(BOTTOM - 200f, 0f, COMMIT, FLING));
        // And a hold then a sideways drag still pages by the sideways travel.
        assertEquals(-300f, drag.travel(250f), 0.01f);
    }

    @Test
    public void onlyTheBottomBorderCarriesTheKeyboard() {
        float[][] presses = {{MID_X, TOP - 10f}, {LEFT - 10f, MID_Y}, {RIGHT + 10f, MID_Y}};
        for (float[] press : presses) {
            BorderDrag drag = armed(press[0], press[1], zone(GAP), true, true, false, NO_LIMIT);
            assertFalse(drag.isKeyboardEligible());
            assertEquals(BorderDrag.Claim.ABANDONED, drag.move(press[0], press[1] - 200f));
        }
    }

    @Test
    public void theKeyboardsZoneIsKnownFromTheDownToTheLift() {
        // What the grabber lights for: a finger in the zone, before it has gone anywhere.
        BorderDrag drag = armed(MID_X, BOTTOM + 10f);
        assertTrue(drag.isKeyboardEligible());
        drag.move(MID_X, BOTTOM - 200f);
        assertTrue(drag.isKeyboardEligible());
        drag.reset();
        assertFalse(drag.isKeyboardEligible());
        assertFalse("the last row is not the zone", armed(MID_X, BOTTOM - 1f).isKeyboardEligible());
        assertFalse(armed(MID_X, TOP - 10f).isKeyboardEligible());
    }

    @Test
    public void withoutTheKeyboardSwipeTheBottomBorderOnlyPages() {
        BorderDrag drag = armed(MID_X, BOTTOM + 10f, zone(GAP), true, false, true, NO_LIMIT);
        assertFalse(drag.isKeyboardEligible());
        assertEquals(BorderDrag.Claim.ABANDONED, drag.move(MID_X, BOTTOM - 300f));
    }

    @Test
    public void aSecondFingerOrAnAbandonEndsTheKeyboardSwipe() {
        BorderDrag pinch = armed(MID_X, BOTTOM + 10f);
        assertEquals(BorderDrag.Claim.ABANDONED, pinch.secondPointer());
        assertEquals(BorderDrag.Claim.ABANDONED, pinch.move(MID_X, BOTTOM - 300f));

        BorderDrag claimed = armed(MID_X, BOTTOM + 10f);
        assertEquals(BorderDrag.Claim.KEYBOARD, claimed.move(MID_X, BOTTOM - 300f));
        assertEquals("a finger joining a claimed swipe changes nothing",
            BorderDrag.Claim.KEYBOARD, claimed.secondPointer());
        assertEquals(BorderDrag.Claim.ABANDONED, claimed.abandon());
        assertEquals(BorderDrag.KeyboardSwipe.NONE,
            claimed.keyboardRelease(BOTTOM - 300f, 0f, COMMIT, FLING));
    }

    // ---- The status bar's swipe off the top border -------------------------------------------

    @Test
    public void aDownSwipeFromTheTopGapIsTheStatusBarsAndExpandsIt() {
        float downY = TOP - 10f;
        BorderDrag drag = armed(MID_X, downY);
        assertTrue(drag.isStatusEligible());
        assertFalse(drag.isKeyboardEligible());
        assertEquals(BorderDrag.Claim.PENDING, drag.move(MID_X, downY + SLOP));
        assertEquals(BorderDrag.Claim.STATUS, drag.move(MID_X + 5f, downY + SLOP + 1f));
        assertTrue(drag.isStatusSwipe());
        assertFalse(drag.isKeyboardSwipe());
        assertFalse("claimed before the hold, the hold claims nothing", drag.holdElapsed());
        assertEquals(COMMIT, drag.verticalTravel(downY + COMMIT), 0.01f);
        assertEquals(BorderDrag.StatusSwipe.EXPAND,
            drag.statusRelease(downY + COMMIT, 0f, COMMIT, FLING));
        assertEquals("the keyboard's release asks nothing of it", BorderDrag.KeyboardSwipe.NONE,
            drag.keyboardRelease(downY - COMMIT, 0f, COMMIT, FLING));
    }

    @Test
    public void anUpSwipeFromTheTopGapCollapsesIt() {
        BorderDrag drag = armed(MID_X, TOP - GAP);
        assertEquals(BorderDrag.Border.TOP, drag.border());
        assertEquals(BorderDrag.Claim.STATUS, drag.move(MID_X, TOP - GAP - 40f));
        assertEquals(BorderDrag.StatusSwipe.COLLAPSE,
            drag.statusRelease(TOP - GAP - COMMIT, 0f, COMMIT, FLING));
    }

    @Test
    public void theStatusSwipeKeepsTheKeyboardsCommitRule() {
        float downY = TOP - 10f;
        float shortDown = downY + COMMIT / 2f;
        BorderDrag drag = armed(MID_X, downY);
        assertEquals(BorderDrag.Claim.STATUS, drag.move(MID_X, shortDown));
        assertEquals("short and slow is nothing", BorderDrag.StatusSwipe.NONE,
            drag.statusRelease(shortDown, FLING / 2f, COMMIT, FLING));
        assertEquals("short and flicked on down expands", BorderDrag.StatusSwipe.EXPAND,
            drag.statusRelease(shortDown, FLING + 1f, COMMIT, FLING));
        assertEquals("far but flicked back up is a change of mind", BorderDrag.StatusSwipe.NONE,
            drag.statusRelease(downY + COMMIT * 2f, -FLING - 1f, COMMIT, FLING));
    }

    @Test
    public void thePhonesOwnStripAtTheTopIsNeverTaken() {
        // The shade's pull reaches down to TOP - 20 here: a press above that is the phone's.
        float limit = TOP - 20f;
        BorderDrag inStrip = armed(MID_X, TOP - 30f, zone(GAP), true, true, true, limit);
        assertEquals(BorderDrag.Border.TOP, inStrip.border());
        assertFalse(inStrip.isStatusEligible());
        assertEquals(BorderDrag.Claim.ABANDONED, inStrip.move(MID_X, TOP + 200f));
        assertTrue("a hold there still pages",
            armed(MID_X, TOP - 30f, zone(GAP), true, true, true, limit).holdElapsed());
        BorderDrag belowStrip = armed(MID_X, TOP - 10f, zone(GAP), true, true, true, limit);
        assertEquals(BorderDrag.Claim.STATUS, belowStrip.move(MID_X, TOP + 200f));
        // And a wall that cannot page arms nothing in the strip at all.
        assertFalse(armed(MID_X, TOP - 30f, zone(GAP), false, true, true, limit).isArmed());
        // Nor, at Margin 0, does the band inside the page reach above the strip's foot.
        BorderDrag.Zone flush = zone(0f);
        assertFalse(armed(MID_X, TOP + 1f, flush, false, true, true, TOP + 5f).isArmed());
        assertTrue(armed(MID_X, TOP + 6f, flush, false, true, true, TOP + 5f).isStatusEligible());
    }

    @Test
    public void aSidewaysStartOnTheTopBorderPagesAndAHoldStaysAsItWas() {
        BorderDrag sideways = armed(MID_X, TOP - 10f);
        assertEquals(BorderDrag.Claim.PAGING, sideways.move(MID_X + SLOP + 5f, TOP - 6f));
        assertFalse(sideways.isStatusSwipe());

        BorderDrag held = armed(MID_X, TOP - 10f);
        assertTrue("the hold still pages", held.holdElapsed());
        assertEquals(BorderDrag.Claim.PAGING, held.move(MID_X, TOP + 300f));
        assertEquals(BorderDrag.StatusSwipe.NONE,
            held.statusRelease(TOP + 300f, 0f, COMMIT, FLING));
        assertEquals(-300f, held.travel(250f), 0.01f);

        BorderDrag onePlace = armed(MID_X, TOP - 10f, zone(GAP), false, true, true, NO_LIMIT);
        assertTrue("the status swipe arms a wall of one place", onePlace.isArmed());
        assertFalse("where the hold cannot page it is given up", onePlace.holdElapsed());
    }

    @Test
    public void theTopAndBottomSwipesNeverCross() {
        // Up off the top border is the status bar's fold, never the keyboard's open.
        BorderDrag top = armed(MID_X, TOP - 10f);
        assertEquals(BorderDrag.Claim.STATUS, top.move(MID_X, TOP - 200f));
        assertEquals(BorderDrag.KeyboardSwipe.NONE,
            top.keyboardRelease(TOP - 200f, 0f, COMMIT, FLING));
        // Down off the bottom border is the keyboard's close, never the bar's unfold.
        BorderDrag bottom = armed(MID_X, BOTTOM + 10f);
        assertFalse(bottom.isStatusEligible());
        assertEquals(BorderDrag.Claim.KEYBOARD, bottom.move(MID_X, BOTTOM + 200f));
        assertEquals(BorderDrag.StatusSwipe.NONE,
            bottom.statusRelease(BOTTOM + 200f, 0f, COMMIT, FLING));
        // The side borders carry neither.
        BorderDrag side = armed(LEFT - 10f, MID_Y);
        assertEquals(BorderDrag.Claim.ABANDONED, side.move(LEFT - 10f, 1200f));
    }

    @Test
    public void aSecondFingerOrAnAbandonEndsTheStatusSwipe() {
        BorderDrag pinch = armed(MID_X, TOP - 10f);
        assertEquals(BorderDrag.Claim.ABANDONED, pinch.secondPointer());
        BorderDrag claimed = armed(MID_X, TOP - 10f);
        assertEquals(BorderDrag.Claim.STATUS, claimed.move(MID_X, TOP + 300f));
        assertEquals(BorderDrag.Claim.STATUS, claimed.secondPointer());
        assertEquals(BorderDrag.Claim.ABANDONED, claimed.abandon());
        assertEquals(BorderDrag.StatusSwipe.NONE,
            claimed.statusRelease(TOP + 300f, 0f, COMMIT, FLING));
        claimed.reset();
        assertFalse(claimed.isStatusEligible());
    }

    // ---- The boxes by the grabber pills, a row inside the page -------------------------------

    /** The default page with the pills' boxes, centred on {@code centreX}. */
    private static BorderDrag.Zone pillZone(float centreX) {
        return zone(GAP).pills(centreX, PILL_WIDTH, PILL_REACH);
    }

    /** A press on the default page with its pills' boxes, every gesture on, a place to page to. */
    private static BorderDrag pressed(float x, float y) {
        return armed(x, y, pillZone(MID_X), true, true, true, NO_LIMIT);
    }

    @Test
    public void aVerticalSwipeFromTheBottomPillsBoxIsTheKeyboards() {
        float y = BOTTOM - 20f;
        assertEquals("the box is not the border", BorderDrag.Border.NONE,
            BorderDrag.borderAt(MID_X, y, pillZone(MID_X)));
        assertEquals(BorderDrag.Border.BOTTOM, BorderDrag.pillAt(MID_X, y, pillZone(MID_X)));
        BorderDrag up = pressed(MID_X, y);
        assertTrue(up.isArmed());
        assertTrue(up.isKeyboardEligible());
        assertFalse(up.isStatusEligible());
        assertEquals(BorderDrag.Border.BOTTOM, up.border());
        assertEquals(BorderDrag.Claim.PENDING, up.move(MID_X, y - SLOP));
        assertEquals(BorderDrag.Claim.KEYBOARD, up.move(MID_X + 5f, y - 200f));
        assertEquals(BorderDrag.KeyboardSwipe.OPEN,
            up.keyboardRelease(y - 200f, 0f, COMMIT, FLING));

        BorderDrag down = pressed(MID_X, y);
        assertEquals(BorderDrag.Claim.KEYBOARD, down.move(MID_X, y + 200f));
        assertEquals(BorderDrag.KeyboardSwipe.CLOSE,
            down.keyboardRelease(y + 200f, 0f, COMMIT, FLING));
    }

    @Test
    public void aVerticalSwipeFromTheTopPillsBoxIsTheStatusBars() {
        float y = TOP + 20f;
        assertEquals(BorderDrag.Border.TOP, BorderDrag.pillAt(MID_X, y, pillZone(MID_X)));
        BorderDrag down = pressed(MID_X, y);
        assertTrue(down.isStatusEligible());
        assertFalse(down.isKeyboardEligible());
        assertEquals(BorderDrag.Border.TOP, down.border());
        assertEquals(BorderDrag.Claim.STATUS, down.move(MID_X, y + 200f));
        assertEquals(BorderDrag.StatusSwipe.EXPAND,
            down.statusRelease(y + 200f, 0f, COMMIT, FLING));

        BorderDrag up = pressed(MID_X, y);
        assertEquals(BorderDrag.Claim.STATUS, up.move(MID_X, y - 200f));
        assertEquals(BorderDrag.StatusSwipe.COLLAPSE,
            up.statusRelease(y - 200f, 0f, COMMIT, FLING));
    }

    @Test
    public void aStillFingerInAPillsBoxIsTheContentsAtTheHoldAndNeverPages() {
        float[][] presses = {{MID_X, BOTTOM - 20f}, {MID_X, TOP + 20f}};
        for (float[] p : presses) {
            BorderDrag drag = pressed(p[0], p[1]);
            assertTrue(drag.isArmed());
            assertFalse("the hold never pages from inside the page", drag.holdElapsed());
            assertFalse(drag.isPaging());
            assertEquals(BorderDrag.Claim.ABANDONED, drag.claim());
            // Its long press goes on to a selection drag, never the swipe.
            assertEquals(BorderDrag.Claim.ABANDONED, drag.move(p[0], p[1] - 300f));
            assertEquals(BorderDrag.KeyboardSwipe.NONE,
                drag.keyboardRelease(p[1] - 300f, 0f, COMMIT, FLING));
        }
    }

    @Test
    public void aSidewaysStartOrASecondFingerInAPillsBoxIsGivenUp() {
        float[][] presses = {{MID_X, BOTTOM - 20f}, {MID_X, TOP + 20f}};
        for (float[] p : presses) {
            BorderDrag sideways = pressed(p[0], p[1]);
            assertEquals(BorderDrag.Claim.ABANDONED, sideways.move(p[0] + SLOP + 5f, p[1] - 6f));
            assertEquals(BorderDrag.Claim.ABANDONED, sideways.move(p[0] + 50f, p[1] - 300f));
            assertFalse(sideways.holdElapsed());
            assertEquals(BorderDrag.Claim.ABANDONED, pressed(p[0], p[1]).secondPointer());
        }
    }

    @Test
    public void theBoxIsAsWideAndAsDeepAsItSaysAndNoMore() {
        BorderDrag.Zone zone = pillZone(MID_X);
        // Sideways: from its left edge up to just short of its right one, at either pill.
        for (float y : new float[] {BOTTOM - 1f, TOP}) {
            assertTrue(pressed(PILL_LEFT, y).isArmed());
            assertTrue(pressed(PILL_RIGHT - 1f, y).isArmed());
            assertFalse("just left of the box", pressed(PILL_LEFT - 1f, y).isArmed());
            assertFalse("just right of the box", pressed(PILL_RIGHT, y).isArmed());
            assertEquals(BorderDrag.Border.NONE, BorderDrag.pillAt(PILL_LEFT - 1f, y, zone));
        }
        // Into the page: the reach, and not a pixel past it.
        assertTrue(pressed(MID_X, BOTTOM - PILL_REACH).isArmed());
        assertFalse(pressed(MID_X, BOTTOM - PILL_REACH - 1f).isArmed());
        assertTrue(pressed(MID_X, TOP + PILL_REACH - 1f).isArmed());
        assertFalse(pressed(MID_X, TOP + PILL_REACH).isArmed());
        assertFalse("the page's middle", pressed(MID_X, MID_Y).isArmed());

        float[] box = new float[4];
        assertTrue(zone.pillBounds(BorderDrag.Border.BOTTOM, box));
        assertArrayEquals(new float[] {PILL_LEFT, BOTTOM - PILL_REACH, PILL_RIGHT, BOTTOM}, box,
            0.01f);
        assertTrue(zone.pillBounds(BorderDrag.Border.TOP, box));
        assertArrayEquals(new float[] {PILL_LEFT, TOP, PILL_RIGHT, TOP + PILL_REACH}, box, 0.01f);
        assertFalse("the sides have no pill", zone.pillBounds(BorderDrag.Border.LEFT, box));
        assertFalse("no pills set, no box", zone(GAP).pillBounds(BorderDrag.Border.BOTTOM, box));
    }

    @Test
    public void theBoxFollowsThePillAndStaysBetweenTheCornerSquares() {
        // A pill drawn 100 right of the page's middle, as on a page caught mid-slide.
        float centre = MID_X + 100f;
        BorderDrag.Zone moved = pillZone(centre);
        assertEquals(BorderDrag.Border.BOTTOM,
            BorderDrag.pillAt(centre + PILL_WIDTH / 2f - 1f, BOTTOM - 1f, moved));
        assertEquals(BorderDrag.Border.NONE,
            BorderDrag.pillAt(centre - PILL_WIDTH / 2f - 1f, BOTTOM - 1f, moved));
        // A pill near the page's left end: the box stops at the corner square.
        BorderDrag.Zone nearCorner = pillZone(LEFT + 60f);
        assertEquals(BorderDrag.Border.NONE,
            BorderDrag.pillAt(LEFT + CORNER - 1f, BOTTOM - 1f, nearCorner));
        assertEquals(BorderDrag.Border.BOTTOM,
            BorderDrag.pillAt(LEFT + CORNER, BOTTOM - 1f, nearCorner));
        float[] box = new float[4];
        assertTrue(nearCorner.pillBounds(BorderDrag.Border.BOTTOM, box));
        assertEquals(LEFT + CORNER, box[0], 0.01f);
        assertEquals(LEFT + 60f + PILL_WIDTH / 2f, box[2], 0.01f);
        // A page no wider than its two corner squares has no room for a box at all.
        BorderDrag.Zone narrow = new BorderDrag.Zone().page(LEFT, TOP, LEFT + 2f * CORNER, BOTTOM)
            .gaps(GAP, GAP, GAP, GAP).minBand(MIN_BAND).corner(CORNER)
            .pills(LEFT + CORNER, PILL_WIDTH, PILL_REACH);
        assertFalse(narrow.pillBounds(BorderDrag.Border.BOTTOM, box));
    }

    @Test
    public void theBoxNeverReachesPastTheMiddleOfAShortPage() {
        BorderDrag.Zone shortPage = new BorderDrag.Zone().page(LEFT, TOP, RIGHT, TOP + 60f)
            .gaps(GAP, GAP, GAP, GAP).minBand(MIN_BAND).corner(0f)
            .pills(MID_X, PILL_WIDTH, PILL_REACH);
        assertEquals(BorderDrag.Border.BOTTOM, BorderDrag.pillAt(MID_X, TOP + 30f, shortPage));
        assertEquals(BorderDrag.Border.TOP, BorderDrag.pillAt(MID_X, TOP + 29f, shortPage));
    }

    @Test
    public void aSwipeThatIsOffHasNoBox() {
        float bottom = BOTTOM - 20f;
        float top = TOP + 20f;
        // The keyboard's off: its box arms nothing, even on a wall that can page.
        assertFalse(armed(MID_X, bottom, pillZone(MID_X), true, false, true, NO_LIMIT).isArmed());
        assertTrue(armed(MID_X, top, pillZone(MID_X), true, false, true, NO_LIMIT).isArmed());
        // The status bar's off: likewise for its box.
        assertFalse(armed(MID_X, top, pillZone(MID_X), true, true, false, NO_LIMIT).isArmed());
        assertTrue(armed(MID_X, bottom, pillZone(MID_X), true, true, false, NO_LIMIT).isArmed());
        // Both off: nothing in either box.
        assertFalse(armed(MID_X, bottom, pillZone(MID_X), true, false, false, NO_LIMIT)
            .isArmed());
        assertFalse(armed(MID_X, top, pillZone(MID_X), true, false, false, NO_LIMIT).isArmed());
    }

    @Test
    public void theTopPillsBoxStillStaysBelowThePhonesStrip() {
        // The phone's own strip reaches down to TOP + 30, into the box.
        float limit = TOP + 30f;
        BorderDrag inStrip = armed(MID_X, TOP + 20f, pillZone(MID_X), true, true, true, limit);
        assertFalse("never taken for the bar", inStrip.isArmed());
        BorderDrag below = armed(MID_X, TOP + 35f, pillZone(MID_X), true, true, true, limit);
        assertTrue(below.isStatusEligible());
        assertEquals(BorderDrag.Claim.STATUS, below.move(MID_X, TOP + 235f));
    }

    @Test
    public void theMinimumBandStillPagesWhereItOverlapsTheBox() {
        // Margin 0: the band reaches MIN_BAND into the page, the box PILL_REACH.
        BorderDrag.Zone flush = zone(0f).pills(MID_X, PILL_WIDTH, PILL_REACH);
        BorderDrag inBand = armed(MID_X, BOTTOM - MIN_BAND + 1f, flush, true, true, true,
            NO_LIMIT);
        assertTrue("the band's hold pages as before", inBand.holdElapsed());
        BorderDrag pastBand = armed(MID_X, BOTTOM - MIN_BAND - 1f, flush, true, true, true,
            NO_LIMIT);
        assertTrue(pastBand.isKeyboardEligible());
        assertFalse("past the band only the swipe is taken", pastBand.holdElapsed());
    }
}
