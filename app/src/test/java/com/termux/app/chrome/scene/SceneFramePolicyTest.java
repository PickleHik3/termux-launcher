package com.termux.app.chrome.scene;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class SceneFramePolicyTest {

    @Test
    public void parseRateKnowsTheChoiceRates() {
        assertEquals(15, SceneFramePolicy.parseRate("15"));
        assertEquals(30, SceneFramePolicy.parseRate("30"));
        assertEquals(60, SceneFramePolicy.parseRate("60"));
        assertEquals(90, SceneFramePolicy.parseRate("90"));
        assertEquals(0, SceneFramePolicy.parseRate("0"));
        assertEquals(30, SceneFramePolicy.parseRate("45"));
        assertEquals(30, SceneFramePolicy.parseRate(null));
        assertEquals(30, SceneFramePolicy.parseRate(""));
    }

    @Test
    public void glassIsHalfTheSceneRate() {
        assertEquals(7, SceneFramePolicy.parseGlassRate("", 15));
        assertEquals(15, SceneFramePolicy.parseGlassRate("", 30));
        assertEquals(30, SceneFramePolicy.parseGlassRate("", 60));
    }

    @Test
    public void ambientPeriodsForTheChoiceRates() {
        assertEquals(66_666_666L, SceneFramePolicy.ambientPeriodNs(15));
        assertEquals(33_333_333L, SceneFramePolicy.ambientPeriodNs(30));
        assertEquals(16_666_666L, SceneFramePolicy.ambientPeriodNs(60));
    }

    @Test
    public void batterySaverHoldsTheStill() {
        assertTrue(SceneFramePolicy.holdAtStill(true, 0));
        assertTrue(SceneFramePolicy.holdAtStill(true, 3));
    }

    @Test
    public void severeHeatOrWorseHoldsTheStill() {
        assertFalse(SceneFramePolicy.holdAtStill(false, 0));   // none
        assertFalse(SceneFramePolicy.holdAtStill(false, 1));   // light
        assertFalse(SceneFramePolicy.holdAtStill(false, 2));   // moderate
        assertTrue(SceneFramePolicy.holdAtStill(false, 3));    // severe
        assertTrue(SceneFramePolicy.holdAtStill(false, 4));    // critical
        assertTrue(SceneFramePolicy.holdAtStill(false, 6));    // shutdown
    }
}
