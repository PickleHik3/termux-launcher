package com.termux.app.tour;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * The demonstration's keyframes, its curve, how many passes it plays and how far it reaches past
 * its control.
 */
public class TourDemoLoopTest {

    private static final float EPSILON = 0.002f;

    private static TourDemoLoop.Frame frame(TourGesture gesture, float progress) {
        TourDemoLoop.Frame frame = new TourDemoLoop.Frame();
        TourDemoLoop.frame(gesture, progress, frame);
        return frame;
    }

    @Test public void theCurveIsTheCssCubicBezier() {
        float[] linear = {0f, 0f, 1f, 1f};
        assertEquals(0.5f, TourDemoLoop.bezier(linear, 0.5f), EPSILON);
        float[] swipe = {0.5f, 0f, 0.3f, 1f};
        assertEquals(0f, TourDemoLoop.bezier(swipe, 0f), EPSILON);
        assertEquals(1f, TourDemoLoop.bezier(swipe, 1f), EPSILON);
        // Slow out of the start and into the end, so the middle is about half way.
        assertTrue(TourDemoLoop.bezier(swipe, 0.2f) < 0.2f);
        assertTrue(TourDemoLoop.bezier(swipe, 0.8f) > 0.8f);
    }

    @Test public void aSwipeLandsPressesTravelsAndLifts() {
        TourDemoLoop.Frame start = frame(TourGesture.DRAG_DOWN, 0f);
        assertEquals(0f, start.fingerAlpha, EPSILON);
        assertEquals(1.35f, start.fingerScale, EPSILON);
        TourDemoLoop.Frame pressed = frame(TourGesture.DRAG_DOWN, 0.24f);
        assertEquals(1f, pressed.fingerAlpha, EPSILON);
        assertEquals(0.92f, pressed.fingerScale, EPSILON);
        assertEquals(0f, pressed.travel, EPSILON);
        TourDemoLoop.Frame arrived = frame(TourGesture.DRAG_DOWN, 0.70f);
        assertEquals(1f, arrived.travel, EPSILON);
        assertEquals(1f, arrived.trailLength, EPSILON);
        TourDemoLoop.Frame gone = frame(TourGesture.DRAG_DOWN, 0.9f);
        assertEquals(0f, gone.fingerAlpha, EPSILON);
        assertEquals(0f, gone.trailAlpha, EPSILON);
        assertEquals(0f, gone.ringAlpha, EPSILON);
    }

    @Test public void aHoldPressesAndSendsARingOut() {
        TourDemoLoop.Frame down = frame(TourGesture.HOLD, 0.5f);
        assertEquals(1f, down.fingerAlpha, EPSILON);
        assertEquals(0.86f, down.fingerScale, EPSILON);
        assertEquals(0f, down.travel, EPSILON);
        assertTrue(down.ringScale > 0.7f && down.ringScale < 2.3f);
        assertTrue(down.ringAlpha > 0f);
        assertEquals(2.3f, frame(TourGesture.HOLD, 0.9f).ringScale, EPSILON);
        assertEquals(0f, frame(TourGesture.HOLD, 1f).fingerAlpha, EPSILON);
    }

    @Test public void theZoneBreathesBetweenItsFaintestAndFull() {
        assertEquals(TourDemoLoop.GLOW_MIN, TourDemoLoop.glowAlpha(0L), EPSILON);
        assertEquals(1f, TourDemoLoop.glowAlpha(TourDemoLoop.SWIPE_PASS_MS / 2), EPSILON);
        assertEquals(TourDemoLoop.GLOW_MIN, TourDemoLoop.glowAlpha(TourDemoLoop.SWIPE_PASS_MS),
            EPSILON);
    }

    @Test public void theLoopIsBoundedAndPlaysTheHandoffsTimings() {
        assertEquals(3, TourDemoLoop.PASSES);
        assertEquals(1900L, TourDemoLoop.passMs(TourGesture.SWIPE_UP));
        assertEquals(1900L, TourDemoLoop.passMs(TourGesture.HOLD_DRAG));
        assertEquals(1800L, TourDemoLoop.passMs(TourGesture.HOLD));
        assertEquals(1800L, TourDemoLoop.passMs(TourGesture.TAP));
    }

    @Test public void theCardIsKeptClearOfWhereTheFingerTravels() {
        float travel = TourDemoLoop.reachDp(TourGesture.DRAG_DOWN, true);
        float still = TourDemoLoop.reachDp(TourGesture.DRAG_DOWN, false);
        assertTrue("a pull down reaches below its control", travel > still);
        assertTrue("a swipe up reaches above its control",
            TourDemoLoop.reachDp(TourGesture.SWIPE_UP, false)
                > TourDemoLoop.reachDp(TourGesture.SWIPE_UP, true));
        assertEquals(TourDemoLoop.reachDp(TourGesture.HOLD, true),
            TourDemoLoop.reachDp(TourGesture.HOLD, false), EPSILON);
    }
}
