package com.termux.app.statusbar;

import com.termux.app.wall.PaneWallPage;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** The CPU/RAM/weather cluster's order, centring and dot visibility. */
public class StatusStatsClusterPolicyTest {

    @Test public void onlyTheWidgetsPlaceCentresAndReverses() {
        assertTrue(StatusStatsClusterPolicy.centeredReversed(PaneWallPage.WIDGETS));
        assertFalse(StatusStatsClusterPolicy.centeredReversed(PaneWallPage.TERMINAL));
        assertFalse(StatusStatsClusterPolicy.centeredReversed(PaneWallPage.DISPLAY));
    }

    @Test public void theWeatherSaysAsMuchAsTheRoomHolds() {
        float[] forms = {120f, 70f, 20f};
        // Everything else takes 100, the widget's icon and margin 16, its floor 34.
        assertEquals(0, StatusStatsClusterPolicy.weatherFormThatFits(forms, 100f, 16f, 34f, 236f));
        assertEquals(1, StatusStatsClusterPolicy.weatherFormThatFits(forms, 100f, 16f, 34f, 235f));
        assertEquals(1, StatusStatsClusterPolicy.weatherFormThatFits(forms, 100f, 16f, 34f, 186f));
        assertEquals(2, StatusStatsClusterPolicy.weatherFormThatFits(forms, 100f, 16f, 34f, 185f));
        // Nothing fits: the shortest form, and the cluster is moved in rather than shortened.
        assertEquals(2, StatusStatsClusterPolicy.weatherFormThatFits(forms, 100f, 16f, 34f, 10f));
    }

    @Test public void theShortestWeatherStillKeepsTheWidgetsFloor() {
        // 16 + 10 is under the 34 floor, so the floor is what has to fit.
        float[] forms = {10f};
        assertEquals(0, StatusStatsClusterPolicy.weatherFormThatFits(forms, 100f, 16f, 34f, 134f));
    }

    @Test public void roomIsTwiceTheDistanceToTheNearerBound() {
        assertEquals(200f, StatusStatsClusterPolicy.centeredRoom(500f, 100f, 600f), 0f);
        assertEquals(0f, StatusStatsClusterPolicy.centeredRoom(500f, 100f, 400f), 0f);
    }

    @Test public void theClusterCentresWhereItFitsAndKeepsOffTheEndWidgets() {
        // Fits: centred.
        assertEquals(450f, StatusStatsClusterPolicy.clusterStart(500f, 100f, 100f, 600f, true), 0f);
        // Would run into the AI glyph at 520: pulled in to end there.
        assertEquals(380f, StatusStatsClusterPolicy.clusterStart(500f, 140f, 100f, 520f, true), 0f);
        // Wider than the whole gap: the end widgets' side wins, in both directions.
        assertEquals(20f, StatusStatsClusterPolicy.clusterStart(500f, 500f, 100f, 520f, true), 0f);
        assertEquals(480f, StatusStatsClusterPolicy.clusterStart(500f, 500f, 480f, 900f, false), 0f);
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
