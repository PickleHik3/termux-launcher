package com.termux.app.statusbar;


import org.junit.Test;
import com.termux.app.page.PageKind;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Which of the row's readings show beyond their own switch. The dots: StatusRowOrderTest. */
public class StatusStatsClusterPolicyTest {

    @Test public void theFontToggleShowsOnlyWithItsSwitchOnTheTerminalOverAPane() {
        assertTrue(StatusStatsClusterPolicy.fontToggleShown(true, PageKind.TERMINAL, true));
        assertFalse(StatusStatsClusterPolicy.fontToggleShown(true, PageKind.HOME, true));
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
}
