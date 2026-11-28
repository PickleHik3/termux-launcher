package com.termux.app.controlbar;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** The full-screen handle's geometry and its slide (#426), pure. */
public class FullScreenHandleTest {

    @Test public void theVisiblePillIsThinAndTheTouchBandTallerAndWider() {
        assertTrue(FullScreenHandle.PILL_HEIGHT_DP >= 6f && FullScreenHandle.PILL_HEIGHT_DP <= 8f);
        assertTrue(FullScreenHandle.BAND_HEIGHT_DP > FullScreenHandle.PILL_HEIGHT_DP);
        assertTrue(FullScreenHandle.BAND_WIDTH_DP > FullScreenHandle.PILL_WIDTH_DP);
    }

    @Test public void thePillStandsCentredAtTheBandsFootInsideIt() {
        float[] pill = FullScreenHandle.pillRect(300f, 80f, 2f);
        assertEquals(300f / 2 - 64f, pill[0], 0.01f);
        assertEquals(300f / 2 + 64f, pill[2], 0.01f);
        assertEquals(80f - FullScreenHandle.PILL_FOOT_DP * 2f, pill[3], 0.01f);
        assertEquals(pill[3] - FullScreenHandle.PILL_HEIGHT_DP * 2f, pill[1], 0.01f);
        assertTrue(pill[1] >= 0f);
    }

    @Test public void aNarrowBandKeepsThePillInsideIt() {
        float[] pill = FullScreenHandle.pillRect(40f, 80f, 2f);
        assertEquals(0f, pill[0], 0.01f);
        assertEquals(40f, pill[2], 0.01f);
    }

    @Test public void theHandleStandsWhereTheBarsBottomWasAboveTheGestureLine() {
        // The same number the bar is laid out from: navigation 48, no stack, 12 of air.
        assertEquals(ControlBarPlacement.bottomAbovePx(48, 0f, 12),
            FullScreenHandle.bottomAbovePx(48, 0f, 12), 0.01f);
        // The keyboard's stack over the navigation: its top plus the air.
        assertEquals(300f + 12f, FullScreenHandle.bottomAbovePx(48, 300f, 12), 0.01f);
    }

    @Test public void aSlideUpIsFarEnoughAndMostlyVertical() {
        assertTrue(FullScreenHandle.isSlideUp(-60f, 5f, 2f));
        assertFalse(FullScreenHandle.isSlideUp(-10f, 0f, 2f));
        assertFalse(FullScreenHandle.isSlideUp(60f, 0f, 2f));
        assertFalse(FullScreenHandle.isSlideUp(-60f, 90f, 2f));
    }

    @Test public void theBandTakesATouchInsideItOnly() {
        assertTrue(FullScreenHandle.bandContains(150f, 40f, 300f, 80f));
        assertFalse(FullScreenHandle.bandContains(-1f, 40f, 300f, 80f));
        assertFalse(FullScreenHandle.bandContains(150f, 81f, 300f, 80f));
    }

    @Test public void theBarRisesFromItsOwnHeightBelowAndJumpsWithReducedMotion() {
        assertEquals(200f, FullScreenHandle.raiseOffsetPx(200f, 0f, false), 0.01f);
        assertEquals(100f, FullScreenHandle.raiseOffsetPx(200f, 0.5f, false), 0.01f);
        assertEquals(0f, FullScreenHandle.raiseOffsetPx(200f, 0f, true), 0.01f);
    }
}
