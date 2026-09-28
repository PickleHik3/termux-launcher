package com.termux.ai;

import org.json.JSONObject;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * {@link TaiBenchConditionsGuard} against a fake clock and a scripted reader: the state it holds
 * (the baseline, the wait clock, which entry started warm, each entry's start/end snapshot) on
 * top of {@link TaiBenchGuardRules}'s pure decisions.
 */
public class TaiBenchConditionsGuardTest {

    private long nowMs;
    private TaiBenchGuardRules.Snapshot current;

    private long clock() {
        return nowMs;
    }

    private TaiBenchGuardRules.Snapshot read() {
        return current;
    }

    private static TaiBenchSuite.EntryPlan entry(String id) {
        return new TaiBenchSuite.EntryPlan(id, TaiModelSpec.BACKEND_LITERT_LM, "cpu", false);
    }

    @Test
    public void twoEntries_secondEntryCoolsDownThenConditionsRecordTheStartAndEndSnapshots() throws Exception {
        TaiBenchConditionsGuard guard = new TaiBenchConditionsGuard(this::read, this::clock);
        TaiBenchSuite.EntryPlan first = entry("m1");
        TaiBenchSuite.EntryPlan second = entry("m2");

        // Entry 1, the run's first: no cool-down, and its snapshot becomes the baseline.
        nowMs = 0L;
        current = new TaiBenchGuardRules.Snapshot(80, false, TaiBenchGuardRules.THERMAL_STATUS_LIGHT, 0.10f);
        assertEquals(TaiBenchGuard.CONTINUE, guard.beforePhase(TaiBenchSuite.PHASE_LOAD, first).action);
        guard.entryStarted(first);
        assertEquals(TaiBenchGuard.CONTINUE, guard.beforePhase(TaiBenchSuite.PHASE_WRITING, first).action);
        guard.entryFinished(first);

        JSONObject conditions1 = guard.entryConditions(first);
        assertEquals(80, conditions1.getInt("batteryStart"));
        assertEquals(80, conditions1.getInt("batteryEnd"));
        assertFalse(conditions1.getBoolean("charging"));
        assertEquals("light", conditions1.getString("thermalStart"));
        assertEquals("light", conditions1.getString("thermalEnd"));
        assertEquals(0.10, conditions1.getDouble("headroomStart"), 1e-9);
        assertFalse(conditions1.getBoolean("warmStart"));

        // Entry 2, after the first: still MODERATE and hotter than the baseline, so its load waits.
        current = new TaiBenchGuardRules.Snapshot(78, false, TaiBenchGuardRules.THERMAL_STATUS_MODERATE, 0.90f);
        TaiBenchGuard.Decision paused = guard.beforePhase(TaiBenchSuite.PHASE_LOAD, second);
        assertEquals(TaiBenchGuard.PAUSE, paused.action);
        assertEquals("cooldown", paused.reason);
        assertEquals(2_000L, paused.pauseMs);
        assertEquals("moderate", paused.detail.getString("thermalStatus"));

        nowMs += 2_000L;
        assertEquals(TaiBenchGuard.PAUSE, guard.beforePhase(TaiBenchSuite.PHASE_LOAD, second).action);

        // Recovers: status back at the baseline's LIGHT and headroom within the 0.05 tolerance.
        nowMs += 2_000L;
        current = new TaiBenchGuardRules.Snapshot(77, true, TaiBenchGuardRules.THERMAL_STATUS_LIGHT, 0.12f);
        assertEquals(TaiBenchGuard.CONTINUE, guard.beforePhase(TaiBenchSuite.PHASE_LOAD, second).action);
        guard.entryStarted(second);
        assertEquals(TaiBenchGuard.CONTINUE, guard.beforePhase(TaiBenchSuite.PHASE_WRITING, second).action);
        current = new TaiBenchGuardRules.Snapshot(76, false, TaiBenchGuardRules.THERMAL_STATUS_LIGHT, 0.11f);
        guard.entryFinished(second);

        JSONObject conditions2 = guard.entryConditions(second);
        assertEquals(77, conditions2.getInt("batteryStart"));
        assertEquals(76, conditions2.getInt("batteryEnd"));
        // Charging at either end counts, even though it was only true at the start.
        assertTrue(conditions2.getBoolean("charging"));
        assertFalse(conditions2.getBoolean("warmStart"));
    }

    @Test
    public void skipCooldown_endsTheWaitAtOnceAndMarksThatEntryWarm() throws Exception {
        TaiBenchConditionsGuard guard = new TaiBenchConditionsGuard(this::read, this::clock);
        TaiBenchSuite.EntryPlan first = entry("m1");
        TaiBenchSuite.EntryPlan second = entry("m2");

        nowMs = 0L;
        current = new TaiBenchGuardRules.Snapshot(90, false, TaiBenchGuardRules.THERMAL_STATUS_LIGHT, 0.10f);
        guard.beforePhase(TaiBenchSuite.PHASE_LOAD, first);
        guard.entryStarted(first);
        guard.entryFinished(first);

        // Still hot; without a skip this would pause.
        current = new TaiBenchGuardRules.Snapshot(88, false, TaiBenchGuardRules.THERMAL_STATUS_MODERATE, 0.90f);
        guard.skipCooldown();
        TaiBenchGuard.Decision decision = guard.beforePhase(TaiBenchSuite.PHASE_LOAD, second);
        assertEquals(TaiBenchGuard.CONTINUE, decision.action);
        guard.entryStarted(second);
        guard.entryFinished(second);

        assertTrue(guard.entryConditions(second).getBoolean("warmStart"));
        // The skip is spent: a third entry hitting the same hot state waits normally.
        TaiBenchSuite.EntryPlan third = entry("m3");
        assertEquals(TaiBenchGuard.PAUSE, guard.beforePhase(TaiBenchSuite.PHASE_LOAD, third).action);
    }

    @Test
    public void fiveMinuteCap_proceedsAndMarksWarmWithoutASkip() throws Exception {
        TaiBenchConditionsGuard guard = new TaiBenchConditionsGuard(this::read, this::clock);
        TaiBenchSuite.EntryPlan first = entry("m1");
        TaiBenchSuite.EntryPlan second = entry("m2");

        nowMs = 0L;
        current = new TaiBenchGuardRules.Snapshot(90, false, TaiBenchGuardRules.THERMAL_STATUS_LIGHT, 0.10f);
        guard.beforePhase(TaiBenchSuite.PHASE_LOAD, first);
        guard.entryStarted(first);
        guard.entryFinished(first);

        current = new TaiBenchGuardRules.Snapshot(88, false, TaiBenchGuardRules.THERMAL_STATUS_MODERATE, 0.90f);
        assertEquals(TaiBenchGuard.PAUSE, guard.beforePhase(TaiBenchSuite.PHASE_LOAD, second).action);
        nowMs += 5 * 60_000L; // the cool-down cap, still hot
        TaiBenchGuard.Decision decision = guard.beforePhase(TaiBenchSuite.PHASE_LOAD, second);
        assertEquals(TaiBenchGuard.CONTINUE, decision.action);
        guard.entryStarted(second);
        guard.entryFinished(second);

        assertTrue(guard.entryConditions(second).getBoolean("warmStart"));
    }

    @Test
    public void batteryLow_stopsEvenWhileACooldownWaitIsInProgress() {
        TaiBenchConditionsGuard guard = new TaiBenchConditionsGuard(this::read, this::clock);
        TaiBenchSuite.EntryPlan first = entry("m1");
        TaiBenchSuite.EntryPlan second = entry("m2");

        nowMs = 0L;
        current = new TaiBenchGuardRules.Snapshot(90, false, TaiBenchGuardRules.THERMAL_STATUS_LIGHT, 0.10f);
        guard.beforePhase(TaiBenchSuite.PHASE_LOAD, first);
        guard.entryStarted(first);
        guard.entryFinished(first);

        current = new TaiBenchGuardRules.Snapshot(88, false, TaiBenchGuardRules.THERMAL_STATUS_MODERATE, 0.90f);
        assertEquals(TaiBenchGuard.PAUSE, guard.beforePhase(TaiBenchSuite.PHASE_LOAD, second).action);

        nowMs += 2_000L;
        current = new TaiBenchGuardRules.Snapshot(14, false, TaiBenchGuardRules.THERMAL_STATUS_MODERATE, 0.90f);
        TaiBenchGuard.Decision decision = guard.beforePhase(TaiBenchSuite.PHASE_LOAD, second);
        assertEquals(TaiBenchGuard.STOP, decision.action);
        assertEquals("battery_low", decision.reason);
    }

    @Test
    public void unknownEntry_conditionsAreTheAllNullDefaultShape() throws Exception {
        TaiBenchConditionsGuard guard = new TaiBenchConditionsGuard(this::read, this::clock);
        JSONObject conditions = guard.entryConditions(entry("never-ran"));
        assertTrue(conditions.isNull("batteryStart"));
        assertTrue(conditions.isNull("batteryEnd"));
        assertTrue(conditions.isNull("charging"));
        assertTrue(conditions.isNull("thermalStart"));
        assertTrue(conditions.isNull("headroomStart"));
        assertFalse(conditions.getBoolean("warmStart"));
    }
}
