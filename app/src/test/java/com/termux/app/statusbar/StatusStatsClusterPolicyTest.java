package com.termux.app.statusbar;


import org.junit.Test;
import com.termux.app.page.PageKind;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** The CPU/RAM/weather cluster's order, centring and dot visibility. */
public class StatusStatsClusterPolicyTest {

    /** One bar across the wall (#90): the readings keep the row's end on every page. */
    @Test public void noPlaceCentresOrReverses() {
        for (PageKind page : PageKind.values())
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

    @Test public void cpuRamDotHidesOnlyWhenNothingFollowsCpuOrTheGpuTakesIt() {
        assertTrue(StatusStatsClusterPolicy.cpuRamDotVisible(true, false, true, false, false));
        assertTrue(StatusStatsClusterPolicy.cpuRamDotVisible(true, false, false, true, false));
        assertTrue(StatusStatsClusterPolicy.cpuRamDotVisible(true, false, false, false, true));
        assertTrue(StatusStatsClusterPolicy.cpuRamDotVisible(true, false, true, true, true));
        assertFalse(StatusStatsClusterPolicy.cpuRamDotVisible(true, false, false, false, false));
        assertFalse(StatusStatsClusterPolicy.cpuRamDotVisible(false, false, true, true, true));
        // With the GPU showing, the CPU-GPU dot carries that pair.
        assertFalse(StatusStatsClusterPolicy.cpuRamDotVisible(true, true, true, true, true));
    }

    @Test public void theFontToggleShowsOnlyWithItsSwitchOnHomeOrTheTerminalOverAPane() {
        assertTrue(StatusStatsClusterPolicy.fontToggleShown(true, PageKind.TERMINAL, true));
        assertTrue(StatusStatsClusterPolicy.fontToggleShown(true, PageKind.HOME, true));
        // Off by switch, or no pane to toggle (Home with no window, the scratchpad).
        assertFalse(StatusStatsClusterPolicy.fontToggleShown(false, PageKind.TERMINAL, true));
        assertFalse(StatusStatsClusterPolicy.fontToggleShown(true, PageKind.TERMINAL, false));
        assertFalse(StatusStatsClusterPolicy.fontToggleShown(true, PageKind.HOME, false));
        // Pages with no terminal pane of their own.
        assertFalse(StatusStatsClusterPolicy.fontToggleShown(true, PageKind.DISPLAY, true));
        assertFalse(StatusStatsClusterPolicy.fontToggleShown(true, PageKind.ASSISTANT, true));
    }

    @Test public void gpuShowsOnlyWithItsSwitchAndAReading() {
        assertTrue(StatusStatsClusterPolicy.gpuShown(true, true));
        assertFalse(StatusStatsClusterPolicy.gpuShown(true, false));
        assertFalse(StatusStatsClusterPolicy.gpuShown(false, true));
        assertFalse(StatusStatsClusterPolicy.gpuShown(false, false));
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
        assertFalse(StatusStatsClusterPolicy.cpuRamDotVisible(cpuOn, false, true, false, true));
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
     * Over every on/off combination of the five widgets, in the row's order (CPU, GPU, RAM,
     * battery, weather): a dot is shown exactly when its pair stands next to each other among the
     * shown widgets, so between two shown widgets there is one dot and none is doubled or left
     * dangling at the end.
     */
    @Test public void everyCombinationHasOneDotBetweenNeighboursAndNoneDangling() {
        for (int mask = 0; mask < 32; mask++) {
            boolean cpu = (mask & 1) != 0, gpu = (mask & 2) != 0, ram = (mask & 4) != 0;
            boolean battery = (mask & 8) != 0, weather = (mask & 16) != 0;
            boolean[] shown = {cpu, gpu, ram, battery, weather};
            // The row's dots in layout order: CPU-GPU, GPU-RAM, CPU-RAM, RAM-weather, battery-weather.
            boolean[] dots = {
                StatusStatsClusterPolicy.cpuGpuDotVisible(cpu, gpu),
                StatusStatsClusterPolicy.gpuRamDotVisible(gpu, ram, battery, weather),
                StatusStatsClusterPolicy.cpuRamDotVisible(cpu, gpu, ram, battery, weather),
                StatusStatsClusterPolicy.ramWeatherDotVisible(ram, battery, weather),
                StatusStatsClusterPolicy.batteryWeatherDotVisible(battery, weather), };
            int widgets = 0, dotsShown = 0;
            for (boolean w : shown) if (w) widgets++;
            for (boolean d : dots) if (d) dotsShown++;
            assertEquals("mask " + mask, Math.max(0, widgets - 1), dotsShown);
            // CPU's dot is the CPU-GPU one when the GPU follows, else the CPU-RAM one; every later
            // widget owns exactly its own dot, shown when a later widget shows.
            boolean laterThanCpu = gpu || ram || battery || weather;
            assertEquals("mask " + mask + " cpu dots", cpu && laterThanCpu, dots[0] || dots[2]);
            assertFalse("mask " + mask + " cpu dots are never both", dots[0] && dots[2]);
            assertEquals("mask " + mask + " gpu dot", gpu && (ram || battery || weather), dots[1]);
            assertEquals("mask " + mask + " ram dot", ram && (battery || weather), dots[3]);
            assertEquals("mask " + mask + " battery dot", battery && weather, dots[4]);
        }
    }
}
