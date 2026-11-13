package com.termux.app.statusbar;

import com.termux.app.wall.PaneWallPage;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** The CPU/RAM/weather cluster's order, centring and dot visibility. */
public class StatusStatsClusterPolicyTest {

    /** One bar across the wall (#90): the readings keep the row's end on every page. */
    @Test public void noPlaceCentresOrReverses() {
        for (PaneWallPage page : PaneWallPage.values())
            assertFalse(page.name(), StatusStatsClusterPolicy.centeredReversed(page));
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
        assertTrue(StatusStatsClusterPolicy.cpuRamDotVisible(true, true, false, false));
        assertTrue(StatusStatsClusterPolicy.cpuRamDotVisible(true, false, true, false));
        assertTrue(StatusStatsClusterPolicy.cpuRamDotVisible(true, false, false, true));
        assertTrue(StatusStatsClusterPolicy.cpuRamDotVisible(true, true, true, true));
        assertFalse(StatusStatsClusterPolicy.cpuRamDotVisible(true, false, false, false));
        assertFalse(StatusStatsClusterPolicy.cpuRamDotVisible(false, true, true, true));
    }

    @Test public void cpuShowsOnlyWithItsSwitchAndPrivilegedAccess() {
        assertTrue(StatusStatsClusterPolicy.cpuShown(true, true));
        assertFalse(StatusStatsClusterPolicy.cpuShown(true, false));
        assertFalse(StatusStatsClusterPolicy.cpuShown(false, true));
        assertFalse(StatusStatsClusterPolicy.cpuShown(false, false));
    }

    @Test public void aCpuHiddenForWantOfAccessLeavesNoDotBehind() {
        // Switch on, no access, RAM and weather on: the CPU dot goes with the CPU.
        boolean cpuOn = StatusStatsClusterPolicy.cpuShown(true, false);
        assertFalse(StatusStatsClusterPolicy.cpuRamDotVisible(cpuOn, true, false, true));
        assertTrue(StatusStatsClusterPolicy.ramWeatherDotVisible(true, false, true));
    }

    @Test public void ramDotShowsOnlyWhenRamShowsAndSomethingFollows() {
        assertTrue(StatusStatsClusterPolicy.ramWeatherDotVisible(true, true, false));
        assertTrue(StatusStatsClusterPolicy.ramWeatherDotVisible(true, false, true));
        assertTrue(StatusStatsClusterPolicy.ramWeatherDotVisible(true, true, true));
        assertFalse(StatusStatsClusterPolicy.ramWeatherDotVisible(true, false, false));
        assertFalse(StatusStatsClusterPolicy.ramWeatherDotVisible(false, true, true));
        assertFalse(StatusStatsClusterPolicy.ramWeatherDotVisible(false, false, false));
    }

    @Test public void batteryDotShowsOnlyWhenBothBatteryAndWeatherShow() {
        assertTrue(StatusStatsClusterPolicy.batteryWeatherDotVisible(true, true));
        assertFalse(StatusStatsClusterPolicy.batteryWeatherDotVisible(true, false));
        assertFalse(StatusStatsClusterPolicy.batteryWeatherDotVisible(false, true));
        assertFalse(StatusStatsClusterPolicy.batteryWeatherDotVisible(false, false));
    }

    /**
     * Over every on/off combination of the four widgets, in the row's order: a dot is shown
     * exactly when its widget shows and a later one does, so between two shown widgets there is
     * one dot and none is doubled or left dangling at the end.
     */
    @Test public void everyCombinationHasOneDotBetweenNeighboursAndNoneDangling() {
        for (int mask = 0; mask < 16; mask++) {
            boolean cpu = (mask & 1) != 0, ram = (mask & 2) != 0;
            boolean battery = (mask & 4) != 0, weather = (mask & 8) != 0;
            boolean[] shown = {cpu, ram, battery, weather};
            boolean[] dots = {
                StatusStatsClusterPolicy.cpuRamDotVisible(cpu, ram, battery, weather),
                StatusStatsClusterPolicy.ramWeatherDotVisible(ram, battery, weather),
                StatusStatsClusterPolicy.batteryWeatherDotVisible(battery, weather), };
            int widgets = 0, dotsShown = 0;
            for (boolean w : shown) if (w) widgets++;
            for (boolean d : dots) if (d) dotsShown++;
            assertEquals("mask " + mask, Math.max(0, widgets - 1), dotsShown);
            // Dot i sits after widget i: shown only after a shown widget with a later one shown.
            for (int i = 0; i < 3; i++) {
                boolean laterShown = false;
                for (int n = i + 1; n < 4; n++) laterShown |= shown[n];
                assertEquals("mask " + mask + " dot " + i, shown[i] && laterShown, dots[i]);
            }
        }
    }
}
