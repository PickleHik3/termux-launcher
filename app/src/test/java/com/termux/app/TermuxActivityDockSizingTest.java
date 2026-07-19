package com.termux.app;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class TermuxActivityDockSizingTest {

    @Test
    public void defaultDockCurve_preservesSmallestAndHitsRequestedPresetGrowth() {
        float[] progress = {0.54f, 0.77f, 1.00f, 1.18f};
        float[] previous = {1.3068f, 1.4034f, 1.50f, 1.5756f};
        float[] growth = {1f, 1.06f, 1.12f, 1.20f};
        for (int i = 0; i < progress.length; i++) {
            assertEquals(previous[i] * growth[i],
                TermuxActivity.resolveDefaultDockIconScaleForProgress(progress[i]), 0.0001f);
        }
    }

    @Test
    public void capsuleDockCurve_preservesSmallestAndSpreadsTenPercentGrowthProportionally() {
        float[] progress = {0.27f, 0.50f, 0.73f, 1.00f};
        float[] previous = {1.7252f, 1.90f, 2.0748f, 2.28f};
        float[] growth = {1f, 1f + (0.10f / 3f), 1f + (0.20f / 3f), 1.10f};
        for (int i = 0; i < progress.length; i++) {
            assertEquals(previous[i] * growth[i],
                TermuxActivity.resolveCapsuleDockIconScaleForProgress(progress[i]), 0.0001f);
        }
    }
}
