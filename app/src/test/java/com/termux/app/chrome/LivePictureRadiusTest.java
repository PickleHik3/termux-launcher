package com.termux.app.chrome;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class LivePictureRadiusTest {

    /** Full-scale sigma the live blur lands on for a RenderEffect radius R on the quarter node. */
    private static float liveSigma(float radius) {
        return LivePicture.SKIA_SIGMA_SLOPE * radius + LivePicture.SKIA_SIGMA_BASE * LivePicture.SCALE;
    }

    @Test
    public void matchesTheStoredSigmaUnderTheScriptCap() {
        float stored = 18.375f; // 7 dp at 2.625
        assertEquals(0.4f * stored + 0.6f, liveSigma(LivePicture.renderEffectRadius(stored)), 1e-3f);
    }

    @Test
    public void countsTheStoredDownsampleAboveTheCap() {
        float stored = 60f; // f = 3
        assertEquals(0.4f * stored + 0.6f * 3f, liveSigma(LivePicture.renderEffectRadius(stored)), 1e-3f);
    }

    @Test
    public void neverNegativeAndZeroStaysZero() {
        assertEquals(0f, LivePicture.renderEffectRadius(0f), 0f);
        assertEquals(0f, LivePicture.renderEffectRadius(1f), 0f);
    }
}
