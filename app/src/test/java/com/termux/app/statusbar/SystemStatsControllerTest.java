package com.termux.app.statusbar;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class SystemStatsControllerTest {

    @Test
    public void parseTopRows_android16FusedHeader_keepsAppCpuAndResidentMemoryAligned() {
        List<SystemStatsController.Proc> rows = SystemStatsController.parseTopRows(Arrays.asList(
            "  PID USER         PR  NI VIRT  RES  SHR S[%CPU] %MEM     TIME+ ARGS",
            " 2625 system       18  -2  24G 612M 380M S 14.2   5.4  63:44.41 system_server",
            "12345 u0_a123      20   0  12G 4.9M 3.2M S  8.5   2.1   1:02.33 com.example.app"));

        assertEquals(2, rows.size());
        assertEquals("system_server", rows.get(0).name);
        assertEquals(14.2, rows.get(0).cpu, 0.001);
        assertEquals(612L * 1024L, rows.get(0).rssKb);
        assertEquals("com.example.app", rows.get(1).name);
        assertEquals(8.5, rows.get(1).cpu, 0.001);
        assertEquals(Math.round(4.9 * 1024), rows.get(1).rssKb);
    }

    @Test
    public void kernelWorkerDetection_distinguishesZeroRssWorkersFromApps() {
        assertTrue(SystemStatsController.isKernelProcessName("[kworker/0:3-events]", 0));
        assertTrue(SystemStatsController.isKernelProcessName("u16:11-memlat_events", 0));
        assertFalse(SystemStatsController.isKernelProcessName("com.example.app", 0));
        assertFalse(SystemStatsController.isKernelProcessName("kworker-looking-app", 2048));
    }

    @Test
    public void friendlyKernelName_replacesRawSchedulerIdentifiers() {
        assertEquals("Kernel · memory latency",
            SystemStatsCardView.friendlyKernelName("u16:11-memlat_events]"));
        assertEquals("Kernel · events",
            SystemStatsCardView.friendlyKernelName("[0:3-events]"));
    }
}
