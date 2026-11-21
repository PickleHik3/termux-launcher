package com.termux.app.terminal.extrakeys;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class ExtraKeyGesturePolicyTest {

    private static final float W = 1000f;

    @Test
    public void flipAt29PercentStaysAndAt31Flips() {
        assertEquals(1, ExtraKeyGesturePolicy.landingPage(1, 3, -290f, W));
        assertEquals(2, ExtraKeyGesturePolicy.landingPage(1, 3, -310f, W));
        assertEquals(1, ExtraKeyGesturePolicy.landingPage(1, 3, 290f, W));
        assertEquals(0, ExtraKeyGesturePolicy.landingPage(1, 3, 310f, W));
    }

    @Test
    public void noFlipPastTheEnds() {
        assertEquals(0, ExtraKeyGesturePolicy.landingPage(0, 3, 600f, W));
        assertEquals(2, ExtraKeyGesturePolicy.landingPage(2, 3, -600f, W));
    }

    @Test
    public void resistanceAtTheEndsOnly() {
        assertEquals(25f, ExtraKeyGesturePolicy.dragOffsetPx(0, 3, 100f), 0.001f);
        assertEquals(-25f, ExtraKeyGesturePolicy.dragOffsetPx(2, 3, -100f), 0.001f);
        assertEquals(-100f, ExtraKeyGesturePolicy.dragOffsetPx(0, 3, -100f), 0.001f);
        assertEquals(100f, ExtraKeyGesturePolicy.dragOffsetPx(2, 3, 100f), 0.001f);
        assertEquals(100f, ExtraKeyGesturePolicy.dragOffsetPx(1, 3, 100f), 0.001f);
    }

    @Test
    public void swipeProgressClamps() {
        assertEquals(0f, ExtraKeyGesturePolicy.swipeProgress(-5f, 48f), 0f);
        assertEquals(0.5f, ExtraKeyGesturePolicy.swipeProgress(24f, 48f), 0.001f);
        assertEquals(1f, ExtraKeyGesturePolicy.swipeProgress(100f, 48f), 0f);
    }

    @Test
    public void bubbleCurves() {
        assertEquals(0.8f, ExtraKeyGesturePolicy.bubbleAlpha(0.5f), 0.001f);
        assertEquals(1f, ExtraKeyGesturePolicy.bubbleAlpha(0.9f), 0f);
        assertEquals(0.5f, ExtraKeyGesturePolicy.bubbleScale(0f), 0f);
        assertEquals(1f, ExtraKeyGesturePolicy.bubbleScale(1f), 0f);
        assertEquals(20f, ExtraKeyGesturePolicy.bubbleLiftPx(40f, 60f), 0f);
        assertEquals(30f, ExtraKeyGesturePolicy.bubbleLiftPx(200f, 60f), 0f);
        assertEquals(0f, ExtraKeyGesturePolicy.bubbleLiftPx(-4f, 60f), 0f);
    }
}
