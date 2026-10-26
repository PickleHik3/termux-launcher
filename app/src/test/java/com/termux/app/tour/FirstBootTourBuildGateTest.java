package com.termux.app.tour;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class FirstBootTourBuildGateTest {
    @Test
    public void preReleaseNamesNeverOfferTheTour() {
        assertFalse(FirstBootTour.offeredOnBuild("1.1.0-pre.6"));
        assertFalse(FirstBootTour.offeredOnBuild("1.1.0-pre.6+dev.abc123"));
    }

    @Test
    public void stableNamesOfferTheTour() {
        assertTrue(FirstBootTour.offeredOnBuild("1.2.0"));
        assertTrue(FirstBootTour.offeredOnBuild("1.2.0+dev.abc"));
    }
}
