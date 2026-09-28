package com.termux.ai;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/**
 * The battery/thermal/cool-down rules of {@code project-docs/benchmark/SPEC.md}'s Safety table,
 * as table-driven cases against the pure functions in {@link TaiBenchGuardRules}.
 */
public class TaiBenchGuardRulesTest {

    private static TaiBenchGuardRules.Snapshot snapshot(int battery, boolean charging, int thermal, float headroom) {
        return new TaiBenchGuardRules.Snapshot(battery, charging, thermal, headroom);
    }

    // ---- start check ---------------------------------------------------------------------------

    @Test
    public void startCheck_batteryAndThermalPassOrFail() {
        assertNull(TaiBenchGuardRules.startCheck(snapshot(30, false, 0, Float.NaN)));
        assertNull(TaiBenchGuardRules.startCheck(snapshot(100, false, 1, Float.NaN)));
        assertEquals("battery_low", TaiBenchGuardRules.startCheck(snapshot(29, false, 0, Float.NaN)));
        // Charging clears the battery floor entirely.
        assertNull(TaiBenchGuardRules.startCheck(snapshot(5, true, 0, Float.NaN)));
        assertEquals("too_hot", TaiBenchGuardRules.startCheck(snapshot(100, false, 2, Float.NaN)));
        assertEquals("too_hot", TaiBenchGuardRules.startCheck(snapshot(100, true, 3, Float.NaN)));
        // Unknown battery or thermal passes each check on its own.
        assertNull(TaiBenchGuardRules.startCheck(snapshot(-1, false, 0, Float.NaN)));
        assertNull(TaiBenchGuardRules.startCheck(snapshot(100, false, -1, Float.NaN)));
        assertNull(TaiBenchGuardRules.startCheck(TaiBenchGuardRules.Snapshot.UNKNOWN));
    }

    // ---- while running: battery ------------------------------------------------------------------

    @Test
    public void runningBattery_stopsBelow15UnlessCharging() {
        // A non-load phase and a first entry: cool-down never enters into it.
        assertEquals(TaiBenchGuard.STOP, decide(TaiBenchSuite.PHASE_WRITING, true, snapshot(14, false, 0, Float.NaN), null, 0L, 0L, false).action);
        assertEquals("battery_low", decide(TaiBenchSuite.PHASE_WRITING, true, snapshot(14, false, 0, Float.NaN), null, 0L, 0L, false).reason);
        assertEquals(TaiBenchGuard.CONTINUE, decide(TaiBenchSuite.PHASE_WRITING, true, snapshot(14, true, 0, Float.NaN), null, 0L, 0L, false).action);
        assertEquals(TaiBenchGuard.CONTINUE, decide(TaiBenchSuite.PHASE_WRITING, true, snapshot(-1, false, 0, Float.NaN), null, 0L, 0L, false).action);
        assertEquals(TaiBenchGuard.CONTINUE, decide(TaiBenchSuite.PHASE_WRITING, true, snapshot(15, false, 0, Float.NaN), null, 0L, 0L, false).action);
    }

    // ---- while running: thermal ------------------------------------------------------------------

    @Test
    public void severeOrCritical_stopsImmediately() {
        assertEquals(TaiBenchGuard.STOP, decide(TaiBenchSuite.PHASE_WRITING, true, snapshot(100, false, 3, Float.NaN), null, 0L, 0L, false).action);
        assertEquals("thermal", decide(TaiBenchSuite.PHASE_WRITING, true, snapshot(100, false, 3, Float.NaN), null, 0L, 0L, false).reason);
        assertEquals(TaiBenchGuard.STOP, decide(TaiBenchSuite.PHASE_WRITING, true, snapshot(100, false, 4, Float.NaN), null, 0L, 0L, false).action);
    }

    @Test
    public void moderate_pausesForFiveSecondsUntilTenMinutesThenStops() {
        TaiBenchGuard.Decision pause = decide(TaiBenchSuite.PHASE_WRITING, true, snapshot(100, false, 2, Float.NaN), null, 0L, 60_000L, false);
        assertEquals(TaiBenchGuard.PAUSE, pause.action);
        assertEquals(5_000L, pause.pauseMs);
        assertEquals("thermal", pause.reason);

        // Started at t=0, still MODERATE at t = 10 minutes exactly: stop, not another pause.
        TaiBenchGuard.Decision timedOut = decide(TaiBenchSuite.PHASE_WRITING, true, snapshot(100, false, 2, Float.NaN),
            null, 1L, 1L + 10 * 60_000L, false);
        assertEquals(TaiBenchGuard.STOP, timedOut.action);
        assertEquals("thermal_timeout", timedOut.reason);

        // Just under the cap still pauses.
        TaiBenchGuard.Decision stillWaiting = decide(TaiBenchSuite.PHASE_WRITING, true, snapshot(100, false, 2, Float.NaN),
            null, 1L, 1L + 10 * 60_000L - 1L, false);
        assertEquals(TaiBenchGuard.PAUSE, stillWaiting.action);
    }

    // ---- cool-down --------------------------------------------------------------------------------

    @Test
    public void cooldown_firstEntryNeverWaits() {
        // Hotter than the baseline, but below MODERATE: only the cool-down could hold it.
        TaiBenchGuardRules.Snapshot baseline = snapshot(100, false, 0, 0.1f);
        TaiBenchGuardRules.Snapshot hot = snapshot(100, false, TaiBenchGuardRules.THERMAL_STATUS_LIGHT, 0.9f);
        TaiBenchGuardRules.Result result = TaiBenchGuardRules.beforePhase(
            TaiBenchSuite.PHASE_LOAD, true, hot, baseline, 0L, 0L, false);
        assertEquals(TaiBenchGuard.CONTINUE, result.decision.action);
        assertEquals(false, result.warmStart);
    }

    @Test
    public void cooldown_laterEntryWaitsWhileHotterThanBaseline() {
        TaiBenchGuardRules.Snapshot baseline = snapshot(100, false, TaiBenchGuardRules.THERMAL_STATUS_LIGHT, 0.1f);
        TaiBenchGuardRules.Snapshot stillHot = snapshot(100, false, TaiBenchGuardRules.THERMAL_STATUS_MODERATE, 0.9f);
        TaiBenchGuardRules.Result result = TaiBenchGuardRules.beforePhase(
            TaiBenchSuite.PHASE_LOAD, false, stillHot, baseline, 0L, 0L, false);
        assertEquals(TaiBenchGuard.PAUSE, result.decision.action);
        assertEquals(2_000L, result.decision.pauseMs);
        assertEquals("cooldown", result.decision.reason);
        assertEquals(false, result.warmStart);
    }

    @Test
    public void cooldown_recoversByStatusAndHeadroomTolerance() {
        TaiBenchGuardRules.Snapshot baseline = snapshot(100, false, TaiBenchGuardRules.THERMAL_STATUS_LIGHT, 0.10f);
        // Status back at or below baseline, headroom within 0.05 of baseline: recovered.
        TaiBenchGuardRules.Snapshot recovered = snapshot(100, false, TaiBenchGuardRules.THERMAL_STATUS_LIGHT, 0.15f);
        assertEquals(TaiBenchGuard.CONTINUE,
            TaiBenchGuardRules.beforePhase(TaiBenchSuite.PHASE_LOAD, false, recovered, baseline, 0L, 0L, false).decision.action);
        // Status recovered but headroom still too far above baseline: not recovered yet.
        TaiBenchGuardRules.Snapshot statusOnlyRecovered = snapshot(100, false, TaiBenchGuardRules.THERMAL_STATUS_LIGHT, 0.20f);
        assertEquals(TaiBenchGuard.PAUSE,
            TaiBenchGuardRules.beforePhase(TaiBenchSuite.PHASE_LOAD, false, statusOnlyRecovered, baseline, 0L, 0L, false).decision.action);
    }

    @Test
    public void cooldown_fiveMinuteCapProceedsWarm() {
        TaiBenchGuardRules.Snapshot baseline = snapshot(100, false, TaiBenchGuardRules.THERMAL_STATUS_LIGHT, 0.1f);
        TaiBenchGuardRules.Snapshot stillHot = snapshot(100, false, TaiBenchGuardRules.THERMAL_STATUS_MODERATE, 0.9f);
        TaiBenchGuardRules.Result underCap = TaiBenchGuardRules.beforePhase(
            TaiBenchSuite.PHASE_LOAD, false, stillHot, baseline, 1L, 1L + 5 * 60_000L - 1L, false);
        assertEquals(TaiBenchGuard.PAUSE, underCap.decision.action);
        assertEquals(false, underCap.warmStart);

        TaiBenchGuardRules.Result atCap = TaiBenchGuardRules.beforePhase(
            TaiBenchSuite.PHASE_LOAD, false, stillHot, baseline, 1L, 1L + 5 * 60_000L, false);
        assertEquals(TaiBenchGuard.CONTINUE, atCap.decision.action);
        assertEquals(true, atCap.warmStart);
    }

    @Test
    public void cooldown_skipProceedsWarmEvenWhileHot() {
        TaiBenchGuardRules.Snapshot baseline = snapshot(100, false, TaiBenchGuardRules.THERMAL_STATUS_LIGHT, 0.1f);
        TaiBenchGuardRules.Snapshot stillHot = snapshot(100, false, TaiBenchGuardRules.THERMAL_STATUS_MODERATE, 0.9f);
        TaiBenchGuardRules.Result result = TaiBenchGuardRules.beforePhase(
            TaiBenchSuite.PHASE_LOAD, false, stillHot, baseline, 0L, 0L, true);
        assertEquals(TaiBenchGuard.CONTINUE, result.decision.action);
        assertEquals(true, result.warmStart);
    }

    @Test
    public void cooldown_unknownBaselineThermalNeverWaits() {
        TaiBenchGuardRules.Snapshot unknownBaseline = snapshot(100, false, -1, Float.NaN);
        TaiBenchGuardRules.Snapshot now = snapshot(100, false, TaiBenchGuardRules.THERMAL_STATUS_MODERATE, Float.NaN);
        TaiBenchGuardRules.Result result = TaiBenchGuardRules.beforePhase(
            TaiBenchSuite.PHASE_LOAD, false, now, unknownBaseline, 0L, 0L, false);
        assertEquals(TaiBenchGuard.CONTINUE, result.decision.action);
        assertEquals(false, result.warmStart);
    }

    @Test
    public void cooldown_batteryStopBeatsTheCooldownWait() {
        TaiBenchGuardRules.Snapshot baseline = snapshot(100, false, TaiBenchGuardRules.THERMAL_STATUS_LIGHT, 0.1f);
        // Would otherwise pause for cool-down (hotter than baseline), but battery is critical.
        TaiBenchGuardRules.Snapshot lowBattery = snapshot(14, false, TaiBenchGuardRules.THERMAL_STATUS_MODERATE, 0.9f);
        TaiBenchGuardRules.Result result = TaiBenchGuardRules.beforePhase(
            TaiBenchSuite.PHASE_LOAD, false, lowBattery, baseline, 0L, 0L, false);
        assertEquals(TaiBenchGuard.STOP, result.decision.action);
        assertEquals("battery_low", result.decision.reason);
    }

    @Test
    public void thermalStatusName_mapsTheKnownValuesAndNullsUnknown() {
        assertEquals("none", TaiBenchGuardRules.thermalStatusName(0));
        assertEquals("light", TaiBenchGuardRules.thermalStatusName(1));
        assertEquals("moderate", TaiBenchGuardRules.thermalStatusName(2));
        assertEquals("severe", TaiBenchGuardRules.thermalStatusName(3));
        assertEquals("critical", TaiBenchGuardRules.thermalStatusName(4));
        assertEquals("emergency", TaiBenchGuardRules.thermalStatusName(5));
        assertEquals("shutdown", TaiBenchGuardRules.thermalStatusName(6));
        assertNull(TaiBenchGuardRules.thermalStatusName(-1));
        assertNull(TaiBenchGuardRules.thermalStatusName(7));
    }

    private static TaiBenchGuard.Decision decide(String phase, boolean isFirstEntry, TaiBenchGuardRules.Snapshot now,
                                                   TaiBenchGuardRules.Snapshot baseline, long waitStartedMs, long nowMs,
                                                   boolean skipRequested) {
        return TaiBenchGuardRules.beforePhase(phase, isFirstEntry, now, baseline, waitStartedMs, nowMs, skipRequested).decision;
    }
}
