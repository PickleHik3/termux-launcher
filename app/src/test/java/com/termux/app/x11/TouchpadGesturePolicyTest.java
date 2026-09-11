package com.termux.app.x11;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.termux.app.x11.TouchpadGesturePolicy.Swipe;
import com.termux.app.x11.TouchpadGesturePolicy.TwoFingerMode;
import com.termux.x11.input.InputStub;

import org.junit.Test;

public class TouchpadGesturePolicyTest {

    @Test
    public void tapButton_followsTheFingerCount() {
        assertEquals(InputStub.BUTTON_LEFT, TouchpadGesturePolicy.tapButton(1));
        assertEquals(InputStub.BUTTON_RIGHT, TouchpadGesturePolicy.tapButton(2));
        assertEquals(InputStub.BUTTON_MIDDLE, TouchpadGesturePolicy.tapButton(3));
        assertEquals(InputStub.BUTTON_MIDDLE, TouchpadGesturePolicy.tapButton(4));
    }

    @Test
    public void twoFingers_stayUndecidedUnderSlop_thenScrollOrPinch() {
        assertEquals(TwoFingerMode.UNDECIDED, TouchpadGesturePolicy.decideTwoFingers(4f, 3f, 8f));
        assertEquals(TwoFingerMode.SCROLL, TouchpadGesturePolicy.decideTwoFingers(12f, 3f, 8f));
        assertEquals(TwoFingerMode.PINCH, TouchpadGesturePolicy.decideTwoFingers(5f, -14f, 8f));
        // Fingers that both scroll and drift apart a little are a scroll: travel wins a tie.
        assertEquals(TwoFingerMode.SCROLL, TouchpadGesturePolicy.decideTwoFingers(20f, 12f, 8f));
    }

    @Test
    public void swipe_namesTheDominantAxisPastTheThreshold() {
        assertEquals(Swipe.NONE, TouchpadGesturePolicy.swipe(10f, -10f, 40f));
        assertEquals(Swipe.LEFT, TouchpadGesturePolicy.swipe(-50f, 10f, 40f));
        assertEquals(Swipe.RIGHT, TouchpadGesturePolicy.swipe(45f, -44f, 40f));
        assertEquals(Swipe.UP, TouchpadGesturePolicy.swipe(5f, -60f, 40f));
        assertEquals(Swipe.DOWN, TouchpadGesturePolicy.swipe(0f, 41f, 40f));
    }

    @Test
    public void tapDrag_armsOnlySoonAfterARealTap() {
        // A pad nobody has tapped yet arms nothing: the marker is not a time to count from.
        assertFalse(TouchpadGesturePolicy.tapDragArmed(123_456_789L, TouchpadGesturePolicy.NO_TAP, 280L));
        assertTrue(TouchpadGesturePolicy.tapDragArmed(1_000L, 900L, 280L));
        assertTrue(TouchpadGesturePolicy.tapDragArmed(1_280L, 1_000L, 280L));
        assertFalse(TouchpadGesturePolicy.tapDragArmed(1_281L, 1_000L, 280L));
        assertFalse(TouchpadGesturePolicy.tapDragArmed(900L, 1_000L, 280L));
    }

    @Test
    public void pinchClicks_countDoublingsInSteps() {
        assertEquals(0, TouchpadGesturePolicy.pinchClicks(100f, 105f, 0.25f));
        assertEquals(4, TouchpadGesturePolicy.pinchClicks(100f, 200f, 0.25f));
        assertEquals(-4, TouchpadGesturePolicy.pinchClicks(200f, 100f, 0.25f));
        // The same growth earns the same clicks whatever the starting gap.
        assertEquals(TouchpadGesturePolicy.pinchClicks(80f, 120f, 0.25f),
            TouchpadGesturePolicy.pinchClicks(160f, 240f, 0.25f));
        assertEquals(0, TouchpadGesturePolicy.pinchClicks(0f, 120f, 0.25f));
    }

    @Test
    public void stripHit_isWithinTheHitBandOfTheTrailingEdge() {
        assertTrue(TouchpadGesturePolicy.stripHit(800f, 800f, 40f));
        assertTrue(TouchpadGesturePolicy.stripHit(760f, 800f, 40f));
        assertFalse(TouchpadGesturePolicy.stripHit(759f, 800f, 40f));
        assertFalse(TouchpadGesturePolicy.stripHit(400f, 800f, 40f));
        // A drag can carry the finger past the physical edge without leaving the strip.
        assertTrue(TouchpadGesturePolicy.stripHit(801f, 800f, 40f));
    }

    @Test
    public void notchCount_truncatesTowardZeroAndKeepsTheSign() {
        assertEquals(2, TouchpadGesturePolicy.notchCount(65f, 26f));
        assertEquals(-2, TouchpadGesturePolicy.notchCount(-65f, 26f));
        assertEquals(0, TouchpadGesturePolicy.notchCount(10f, 26f));
        assertEquals(0, TouchpadGesturePolicy.notchCount(10f, 0f));
    }

    @Test
    public void stripFits_leavesRoomAtTheNarrowestSplitGap() {
        // DisplayTouchpadPlacement.MIN_GAP_DP (160) minus a 30dp strip is still 130dp to point
        // in, comfortably past a 60dp minimum, so the strip stays up even at the narrowest gap.
        assertTrue(TouchpadGesturePolicy.stripFits(160f, 30f, 60f));
        assertFalse(TouchpadGesturePolicy.stripFits(80f, 30f, 60f));
        assertTrue(TouchpadGesturePolicy.stripFits(90f, 30f, 60f));
    }
}
