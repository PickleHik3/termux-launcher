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

    @Test
    public void nextTickDelay_primesFastUntilTheFirstCpuReadingExists() {
        // No reading yet, budget left: chase the second sample at the priming cadence.
        assertEquals(SystemStatsController.PRIME_INTERVAL_MS,
            SystemStatsController.nextTickDelayMs(false, SystemStatsController.PRIME_BUDGET_TICKS, 6000L));
        // A reading exists: the set cadence, however large the remaining budget.
        assertEquals(6000L,
            SystemStatsController.nextTickDelayMs(true, SystemStatsController.PRIME_BUDGET_TICKS, 6000L));
        // Budget exhausted with still no reading (no backend, hardened /proc): fall back to the
        // set cadence rather than polling fast forever.
        assertEquals(18000L, SystemStatsController.nextTickDelayMs(false, 0, 18000L));
        // The card's own cadence is already faster than priming; never stretch it.
        assertEquals(700L, SystemStatsController.nextTickDelayMs(false, 3, 700L));
    }

    @Test
    public void shouldStartSample_overridesAWedgedInFlightRequest() {
        // Nothing in flight: always sample.
        assertTrue(SystemStatsController.shouldStartSample(false, 10_000L, 0L, 6_000L));
        // In flight and inside its deadline: wait, do not stack a second privileged command.
        assertFalse(SystemStatsController.shouldStartSample(true, 10_000L, 8_000L, 6_000L));
        assertFalse(SystemStatsController.shouldStartSample(true, 10_000L, 4_001L, 6_000L));
        // Past the deadline the request is treated as gone. Without this a single wedged su call
        // left mInFlight true forever and the card simply stopped updating.
        assertTrue(SystemStatsController.shouldStartSample(true, 10_000L, 4_000L, 6_000L));
        assertTrue(SystemStatsController.shouldStartSample(true, 10_000L, 0L, 6_000L));
    }

    @Test
    public void deviceCpuPercent_putsTopsPerCoreReadingOnTheSameScaleAsTheWidget() {
        // top counts to 100 per core (its own header says "800%cpu" on eight), while the widget and
        // the card header read /proc/stat's 0-100 aggregate. Unscaled, a process showing 22% sat in a
        // card whose header said 10%, which read as the card contradicting itself.
        assertEquals(2.75d, SystemStatsController.deviceCpuPercent(22d, 8), .0001d);
        assertEquals(11d, SystemStatsController.deviceCpuPercent(22d, 2), .0001d);
        // A single core, or a core count that was never resolved, leaves the reading alone.
        assertEquals(22d, SystemStatsController.deviceCpuPercent(22d, 1), .0001d);
        assertEquals(22d, SystemStatsController.deviceCpuPercent(22d, 0), .0001d);
    }

    @Test
    public void mergeProcessRows_keepsThePreviousListWhenTheBackendReturnedNothing() {
        // The definitive cause of "the process list disappears": a failed read parses to zero rows,
        // and the list was assigned unconditionally, so the card hid the section entirely.
        List<SystemStatsController.Proc> previous = Arrays.asList(
            new SystemStatsController.Proc(1, "init", 1.0, 2048L, false),
            new SystemStatsController.Proc(2, "system_server", 9.5, 60_000L, false));

        assertEquals(previous, SystemStatsController.mergeProcessRows(
            previous, java.util.Collections.<SystemStatsController.Proc>emptyList()));

        List<SystemStatsController.Proc> fresh = Arrays.asList(
            new SystemStatsController.Proc(3, "zygote", 0.5, 1024L, false));
        List<SystemStatsController.Proc> merged =
            SystemStatsController.mergeProcessRows(previous, fresh);
        assertEquals(1, merged.size());
        assertEquals("zygote", merged.get(0).name);
        // A copy, not the caller's collection: the selection map is reused between samples.
        assertFalse(merged == fresh);
    }
}
