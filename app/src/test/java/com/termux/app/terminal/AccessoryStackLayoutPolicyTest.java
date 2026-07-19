package com.termux.app.terminal;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class AccessoryStackLayoutPolicyTest {

    @Test
    public void combinedHeight_sumsAllVisibleSegments() {
        int combined = AccessoryStackLayoutPolicy.computeCombinedHeight(120, 42, 20, 6);
        assertEquals(188, combined);
    }

    @Test
    public void combinedHeight_clampsNegativeValues() {
        int combined = AccessoryStackLayoutPolicy.computeCombinedHeight(120, -5, 20, -2);
        assertEquals(140, combined);
    }

    @Test
    public void appsBarInterRowGap_isZeroWhenAzDisabled() {
        int gap = AccessoryStackLayoutPolicy.computeAppsBarInterRowGapPx(false, 3f, 1.5f);
        assertEquals(0, gap);
    }

    @Test
    public void appsBarInterRowGap_scalesWithIconScaleWhenAzEnabled() {
        int gapDefaultScale = AccessoryStackLayoutPolicy.computeAppsBarInterRowGapPx(true, 3f, 1f);
        int gapLargeScale = AccessoryStackLayoutPolicy.computeAppsBarInterRowGapPx(true, 3f, 1.5f);
        assertEquals(9, gapDefaultScale);
        assertEquals(12, gapLargeScale);
    }

    @Test
    public void dockIconFillRatio_clampsAtEstablishedBounds() {
        assertEquals(0.68f, AccessoryStackLayoutPolicy.computeDockIconFillRatio(1f), 0f);
        assertEquals(0.76f, AccessoryStackLayoutPolicy.computeDockIconFillRatio(1.4f), 0.0001f);
        assertEquals(0.84f, AccessoryStackLayoutPolicy.computeDockIconFillRatio(2f), 0.0001f);
    }

    @Test
    public void presetCurve_hitsEveryControlPoint() {
        float[] progress = {0.27f, 0.50f, 0.73f, 1f};
        float[] values = {1.72f, 1.96f, 2.21f, 2.51f};
        for (int i = 0; i < progress.length; i++) {
            assertEquals(values[i], AccessoryStackLayoutPolicy.interpolatePresetCurve(
                progress[i], progress, values), 0.0001f);
        }
    }

    @Test
    public void pageIndicatorBandHeight_usesFixedStrip() {
        assertEquals(9, AccessoryStackLayoutPolicy.computePageIndicatorBandHeightPx(true, 3f));
        assertEquals(0, AccessoryStackLayoutPolicy.computePageIndicatorBandHeightPx(false, 3f));
    }

    @Test
    public void azRowHeight_usesFixedHeight() {
        assertEquals(57, AccessoryStackLayoutPolicy.computeAzRowHeightPx(true, 3f));
        assertEquals(0, AccessoryStackLayoutPolicy.computeAzRowHeightPx(false, 3f));
    }

    @Test
    public void terminalToolbarHeight_scalesWithRowsAndScale() {
        assertEquals(228, AccessoryStackLayoutPolicy.computeTerminalToolbarHeightPx(38, 2, 3f));
    }
}
