package com.termux.app.terminal;

import android.graphics.RectF;

import com.termux.app.terminal.PaneSwapPolicy.Direction;
import com.termux.app.terminal.PaneSwapPolicy.TwoFingerSwipe;
import com.termux.app.terminal.PaneSwapPolicy.TwoFingerSwipe.State;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

@RunWith(RobolectricTestRunner.class)
public class PaneSwapPolicyTest {

    /** A 4px divider between every pair of panes, as the gap setting lays them out. */
    private static final float GAP = 4f;
    private static final float TOLERANCE = GAP + 2f;

    // --- neighbourAcross ---

    @Test
    public void sideBySide_eachPaneFindsTheOtherOnlyAcrossTheSharedEdge() {
        RectF left = new RectF(0, 0, 298, 1000);
        RectF right = new RectF(302, 0, 600, 1000);
        List<RectF> panes = Arrays.asList(left, right);

        assertEquals(1, PaneSwapPolicy.neighbourAcross(left, panes, Direction.RIGHT, TOLERANCE));
        assertEquals(0, PaneSwapPolicy.neighbourAcross(right, panes, Direction.LEFT, TOLERANCE));
        assertEquals(-1, PaneSwapPolicy.neighbourAcross(left, panes, Direction.LEFT, TOLERANCE));
        assertEquals(-1, PaneSwapPolicy.neighbourAcross(right, panes, Direction.RIGHT, TOLERANCE));
        assertEquals(-1, PaneSwapPolicy.neighbourAcross(left, panes, Direction.UP, TOLERANCE));
        assertEquals(-1, PaneSwapPolicy.neighbourAcross(left, panes, Direction.DOWN, TOLERANCE));
    }

    @Test
    public void theDividerGapMustBeCoveredByTheTolerance() {
        RectF top = new RectF(0, 0, 600, 498);
        RectF bottom = new RectF(0, 502, 600, 1000);
        List<RectF> panes = Arrays.asList(top, bottom);

        assertEquals(1, PaneSwapPolicy.neighbourAcross(top, panes, Direction.DOWN, TOLERANCE));
        assertEquals(0, PaneSwapPolicy.neighbourAcross(bottom, panes, Direction.UP, TOLERANCE));
        // A tolerance narrower than the gap sees no neighbour at all.
        assertEquals(-1, PaneSwapPolicy.neighbourAcross(top, panes, Direction.DOWN, GAP - 1f));
    }

    @Test
    public void tLayout_eitherColumnGoesUpToTheFullWidthPane_andItComesDownToTheLargerShare() {
        RectF top = new RectF(0, 0, 600, 498);
        RectF lowerLeft = new RectF(0, 502, 248, 1000);
        RectF lowerRight = new RectF(252, 502, 600, 1000);
        List<RectF> panes = Arrays.asList(top, lowerLeft, lowerRight);

        assertEquals(0, PaneSwapPolicy.neighbourAcross(lowerLeft, panes, Direction.UP, TOLERANCE));
        assertEquals(0, PaneSwapPolicy.neighbourAcross(lowerRight, panes, Direction.UP, TOLERANCE));
        assertEquals(2, PaneSwapPolicy.neighbourAcross(lowerLeft, panes, Direction.RIGHT, TOLERANCE));
        // Going down, the top pane borders both columns; the one sharing most of the edge wins.
        assertEquals(2, PaneSwapPolicy.neighbourAcross(top, panes, Direction.DOWN, TOLERANCE));
        assertEquals(-1, PaneSwapPolicy.neighbourAcross(top, panes, Direction.UP, TOLERANCE));
    }

    @Test
    public void equalShares_goToTheNearestCentre_andCornersOnlyTouchingAreNotNeighbours() {
        // Both lower panes share 198px of the top pane's edge; the left one's centre is nearer.
        RectF top = new RectF(100, 0, 500, 498);
        RectF lowerLeft = new RectF(0, 502, 298, 1000);
        RectF lowerRight = new RectF(302, 502, 700, 1000);
        // A pane diagonally across a corner overlaps nothing along the edge.
        RectF farLeft = new RectF(0, 0, 298, 498);
        RectF farRight = new RectF(302, 502, 600, 1000);

        assertEquals(1, PaneSwapPolicy.neighbourAcross(top,
            Arrays.asList(top, lowerLeft, lowerRight), Direction.DOWN, TOLERANCE));
        assertEquals(-1, PaneSwapPolicy.neighbourAcross(farLeft,
            Arrays.asList(farLeft, farRight), Direction.RIGHT, TOLERANCE));
        assertEquals(-1, PaneSwapPolicy.neighbourAcross(farLeft,
            Arrays.asList(farLeft, farRight), Direction.DOWN, TOLERANCE));
    }

    @Test
    public void nullCandidatesAreSkipped() {
        RectF left = new RectF(0, 0, 298, 1000);
        RectF right = new RectF(302, 0, 600, 1000);
        assertEquals(2, PaneSwapPolicy.neighbourAcross(left,
            Arrays.asList(left, null, right), Direction.RIGHT, TOLERANCE));
    }

    // --- TwoFingerSwipe ---

    private static final float DENSITY = 2f;
    private static final float SLOP = 16f;
    /** {@link TwoFingerSwipe#SWIPE_DISTANCE_DP} at {@link #DENSITY}, in pixels. */
    private static final float DISTANCE = TwoFingerSwipe.SWIPE_DISTANCE_DP * DENSITY;
    private static final float SPREAD = 200f;

    private static TwoFingerSwipe landTwoFingers(long at) {
        TwoFingerSwipe swipe = new TwoFingerSwipe(DENSITY, SLOP);
        swipe.reset();
        assertEquals(State.IDLE, swipe.update(1, 300f, 500f, 0f, at - 40));
        assertEquals(State.TRACKING, swipe.update(2, 300f, 500f, SPREAD, at));
        return swipe;
    }

    @Test
    public void briskFlick_inEachDirectionNamesThatDirection() {
        float[][] travel = { {DISTANCE, 0f}, {-DISTANCE, 5f}, {4f, -DISTANCE}, {-3f, DISTANCE} };
        Direction[] expected = { Direction.RIGHT, Direction.LEFT, Direction.UP, Direction.DOWN };
        for (int i = 0; i < travel.length; i++) {
            TwoFingerSwipe swipe = landTwoFingers(1000);
            // Partway there it is still undecided, and the terminal keeps the stream meanwhile.
            assertEquals(State.TRACKING, swipe.update(2, 300f + travel[i][0] / 2,
                500f + travel[i][1] / 2, SPREAD, 1080));
            assertEquals(State.SWIPED, swipe.update(2, 300f + travel[i][0],
                500f + travel[i][1], SPREAD + 3f, 1160));
            assertEquals(expected[i], swipe.direction());
            // Final: later events change nothing.
            assertEquals(State.SWIPED, swipe.update(2, 0f, 0f, SPREAD, 1200));
        }
    }

    @Test
    public void pinchStaysAPinchForTheRestOfTheStream() {
        TwoFingerSwipe swipe = landTwoFingers(1000);
        assertEquals(State.PINCH, swipe.update(2, 302f, 501f, SPREAD + 40f, 1050));
        // Even a big quick midpoint travel afterwards never turns it into a flick.
        assertEquals(State.PINCH, swipe.update(2, 300f + DISTANCE * 2, 500f, SPREAD + 40f, 1100));
        assertNull(swipe.direction());
    }

    @Test
    public void slowTwoFingerDragPassesThrough() {
        TwoFingerSwipe swipe = landTwoFingers(1000);
        assertEquals(State.TRACKING, swipe.update(2, 300f, 520f, SPREAD, 1200));
        // The time ran out before the travel did: a drag, the terminal's scroll.
        assertEquals(State.PASS_THROUGH, swipe.update(2, 300f, 540f, SPREAD,
            1000 + TwoFingerSwipe.SWIPE_TIME_MS + 1));
        assertEquals(State.PASS_THROUGH, swipe.update(2, 300f, 500f + DISTANCE * 3, SPREAD, 1400));

        TwoFingerSwipe late = landTwoFingers(1000);
        // Far enough, but too late to be brisk.
        assertEquals(State.PASS_THROUGH, late.update(2, 300f, 500f + DISTANCE, SPREAD,
            1000 + TwoFingerSwipe.SWIPE_TIME_MS + 50));
        assertNull(late.direction());
    }

    @Test
    public void diagonalFlickIsNoDirection() {
        TwoFingerSwipe swipe = landTwoFingers(1000);
        assertEquals(State.PASS_THROUGH, swipe.update(2, 300f + DISTANCE * .8f,
            500f + DISTANCE * .7f, SPREAD, 1100));
        assertNull(swipe.direction());
    }

    @Test
    public void aThirdFingerOrALiftedOneAbandonsTheStream() {
        TwoFingerSwipe third = landTwoFingers(1000);
        assertEquals(State.PASS_THROUGH, third.update(3, 300f, 500f, 0f, 1020));
        assertEquals(State.PASS_THROUGH, third.update(2, 300f + DISTANCE, 500f, SPREAD, 1060));

        TwoFingerSwipe lifted = landTwoFingers(1000);
        assertEquals(State.PASS_THROUGH, lifted.update(1, 300f, 500f, 0f, 1020));
        // A finger landing again does not restart the watch within the same stream.
        assertEquals(State.PASS_THROUGH, lifted.update(2, 300f, 500f, SPREAD, 1040));
        assertEquals(State.PASS_THROUGH, lifted.update(2, 300f + DISTANCE, 500f, SPREAD, 1080));

        // A new stream starts clean.
        lifted.reset();
        assertEquals(State.IDLE, lifted.state());
        assertEquals(State.TRACKING, lifted.update(2, 300f, 500f, SPREAD, 2000));
    }

    @Test
    public void oneFingerAloneNeverEngages() {
        TwoFingerSwipe swipe = new TwoFingerSwipe(DENSITY, SLOP);
        assertEquals(State.IDLE, swipe.update(1, 0f, 0f, 0f, 0));
        assertEquals(State.IDLE, swipe.update(1, DISTANCE * 3, 0f, 0f, 50));
    }
}
