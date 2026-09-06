package com.termux.app.statusbar;

import com.termux.app.wall.PaneWallPage;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** The CPU/RAM/weather cluster's order, centring and dot visibility. */
public class StatusStatsClusterPolicyTest {

    @Test public void onlyTheWidgetsPlaceCentresAndReverses() {
        assertTrue(StatusStatsClusterPolicy.centeredReversed(PaneWallPage.WIDGETS));
        assertFalse(StatusStatsClusterPolicy.centeredReversed(PaneWallPage.TERMINAL));
        assertFalse(StatusStatsClusterPolicy.centeredReversed(PaneWallPage.DISPLAY));
    }

    @Test public void cpuRamDotHidesOnlyWhenNothingFollowsCpu() {
        assertTrue(StatusStatsClusterPolicy.cpuRamDotVisible(true, true, false));
        assertTrue(StatusStatsClusterPolicy.cpuRamDotVisible(true, false, true));
        assertTrue(StatusStatsClusterPolicy.cpuRamDotVisible(true, true, true));
        assertFalse(StatusStatsClusterPolicy.cpuRamDotVisible(true, false, false));
        assertFalse(StatusStatsClusterPolicy.cpuRamDotVisible(false, true, true));
    }

    @Test public void ramWeatherDotShowsOnlyWhenBothShow() {
        assertTrue(StatusStatsClusterPolicy.ramWeatherDotVisible(true, true));
        assertFalse(StatusStatsClusterPolicy.ramWeatherDotVisible(true, false));
        assertFalse(StatusStatsClusterPolicy.ramWeatherDotVisible(false, true));
        assertFalse(StatusStatsClusterPolicy.ramWeatherDotVisible(false, false));
    }
}
