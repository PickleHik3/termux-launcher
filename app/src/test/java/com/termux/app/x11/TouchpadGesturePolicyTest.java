package com.termux.app.x11;

import static org.junit.Assert.assertEquals;

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
    public void pinchClicks_countDoublingsInSteps() {
        assertEquals(0, TouchpadGesturePolicy.pinchClicks(100f, 105f, 0.25f));
        assertEquals(4, TouchpadGesturePolicy.pinchClicks(100f, 200f, 0.25f));
        assertEquals(-4, TouchpadGesturePolicy.pinchClicks(200f, 100f, 0.25f));
        // The same growth earns the same clicks whatever the starting gap.
        assertEquals(TouchpadGesturePolicy.pinchClicks(80f, 120f, 0.25f),
            TouchpadGesturePolicy.pinchClicks(160f, 240f, 0.25f));
        assertEquals(0, TouchpadGesturePolicy.pinchClicks(0f, 120f, 0.25f));
    }
}
