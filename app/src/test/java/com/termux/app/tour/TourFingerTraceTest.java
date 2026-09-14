package com.termux.app.tour;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** The finger has to start and end on the control, whatever the control's size. */
public class TourFingerTraceTest {

    private static final float DENSITY = 3f;
    private static final float LEFT = 40f;
    private static final float TOP = 100f;
    private static final float RIGHT = 1040f;
    private static final float BOTTOM = 220f;

    private final float[] point = new float[2];

    private void at(TourGesture gesture, float progress) {
        TourFingerTrace.pointAt(gesture, LEFT, TOP, RIGHT, BOTTOM, DENSITY, progress, point);
    }

    @Test
    public void aHorizontalSwipeCrossesTheControlAndStaysOnItsRow() {
        at(TourGesture.SWIPE_LEFT, 0f);
        float startX = point[0];
        float centerY = (TOP + BOTTOM) / 2f;
        assertEquals(centerY, point[1], 0.01f);
        at(TourGesture.SWIPE_LEFT, 1f);
        assertTrue("swipe left must travel left", point[0] < startX);
        assertEquals(centerY, point[1], 0.01f);

        at(TourGesture.SWIPE_RIGHT, 0f);
        float rightStart = point[0];
        at(TourGesture.SWIPE_RIGHT, 1f);
        assertTrue("swipe right must travel right", point[0] > rightStart);
    }

    @Test
    public void theCornerSwipesLeaveTheCapDiagonallyAndDisagreeOnlyOnTheVertical() {
        float centerX = (LEFT + RIGHT) / 2f;
        float centerY = (TOP + BOTTOM) / 2f;

        at(TourGesture.SWIPE_DOWN_LEFT, 0f);
        assertEquals(centerX, point[0], 0.01f);
        assertEquals(centerY, point[1], 0.01f);
        at(TourGesture.SWIPE_DOWN_LEFT, 1f);
        float downLeftX = point[0];
        float downLeftY = point[1];
        assertTrue("bottom-left must travel left", downLeftX < centerX);
        assertTrue("bottom-left must travel down", downLeftY > centerY);

        at(TourGesture.SWIPE_UP_LEFT, 0f);
        assertEquals(centerX, point[0], 0.01f);
        assertEquals(centerY, point[1], 0.01f);
        at(TourGesture.SWIPE_UP_LEFT, 1f);
        assertTrue("top-left must travel left", point[0] < centerX);
        assertTrue("top-left must travel up", point[1] < centerY);
        // The two are the same path mirrored, so a user reads them as one pair of corners.
        assertEquals(downLeftX, point[0], 0.01f);
        assertEquals(centerY - (downLeftY - centerY), point[1], 0.01f);
    }

    @Test
    public void aSwipeStaysInsideTheControlItPointsAt() {
        for (float progress = 0f; progress <= 1f; progress += 0.05f) {
            at(TourGesture.SWIPE_LEFT, progress);
            assertTrue("finger left the control at " + progress,
                point[0] >= LEFT && point[0] <= RIGHT);
        }
    }

    @Test
    public void dragDownAndDragUpAreTheSamePathInOppositeDirections() {
        at(TourGesture.DRAG_DOWN, 0f);
        float downStart = point[1];
        at(TourGesture.DRAG_DOWN, 1f);
        float downEnd = point[1];
        at(TourGesture.DRAG_UP, 0f);
        float upStart = point[1];
        at(TourGesture.DRAG_UP, 1f);
        float upEnd = point[1];
        assertTrue(downEnd > downStart);
        assertEquals(downEnd, upStart, 0.01f);
        assertEquals(downStart, upEnd, 0.01f);
    }

    @Test
    public void aSwipeUpLeavesTheControlUpwards() {
        at(TourGesture.SWIPE_UP, 0f);
        float start = point[1];
        at(TourGesture.SWIPE_UP, 1f);
        assertTrue(point[1] < start);
    }

    @Test
    public void aTapDoesNotMove() {
        at(TourGesture.TAP, 0f);
        float x = point[0];
        float y = point[1];
        at(TourGesture.TAP, 0.5f);
        assertEquals(x, point[0], 0.01f);
        assertEquals(y, point[1], 0.01f);
        at(TourGesture.NONE, 1f);
        assertEquals(x, point[0], 0.01f);
        assertEquals(y, point[1], 0.01f);
    }

    @Test
    public void theTapPulseOpensAndCloses() {
        assertEquals(0f, TourFingerTrace.tapPulse(0f), 0.01f);
        assertEquals(1f, TourFingerTrace.tapPulse(0.25f), 0.01f);
        assertEquals(0f, TourFingerTrace.tapPulse(1f), 0.01f);
        assertEquals(0f, TourFingerTrace.tapPulse(-3f), 0.01f);
        assertEquals(0f, TourFingerTrace.tapPulse(7f), 0.01f);
    }

    @Test
    public void theScrubSlidesAlongTheRowThenLiftsOffIt() {
        at(TourGesture.SCRUB, 0f);
        float startX = point[0];
        float rowY = point[1];
        at(TourGesture.SCRUB, 0.5f);
        assertTrue(point[0] > startX);
        assertEquals(rowY, point[1], 0.01f);
        at(TourGesture.SCRUB, 1f);
        assertTrue("the scrub has to lift off the row", point[1] < rowY);
    }

    @Test
    public void aControlNarrowerThanTheTravelStillGetsATrace() {
        float[] narrow = new float[2];
        TourFingerTrace.pointAt(TourGesture.SWIPE_LEFT, 0f, 0f, 20f, 20f, DENSITY, 0f, narrow);
        float start = narrow[0];
        TourFingerTrace.pointAt(TourGesture.SWIPE_LEFT, 0f, 0f, 20f, 20f, DENSITY, 1f, narrow);
        assertTrue(narrow[0] < start);
    }
}
