package com.termux.ai;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/**
 * The battery/thermal guard bench v1 runs with: {@link TaiBenchGuardRules} decides, this class
 * only remembers what the rules need remembered — the baseline snapshot taken at the run's first
 * {@link #beforePhase}, which entry is the run's first (cool-down never applies to it), the clock
 * a wait started, a pending {@link #skipCooldown()}, which entries started warm, and each entry's
 * start/end snapshot for {@link #entryConditions}. {@code entry} identity (not equality) is the
 * key throughout, since {@link TaiBenchSuite.EntryPlan} is reused as-is for a self-heal retry.
 */
final class TaiBenchConditionsGuard implements TaiBenchGuard {
    private static final long NO_WAIT = -1L;

    @NonNull private final Supplier<TaiBenchGuardRules.Snapshot> reader;
    @NonNull private final LongSupplier clock;

    @Nullable private TaiBenchGuardRules.Snapshot baseline;
    @Nullable private TaiBenchSuite.EntryPlan firstEntry;
    /** When the current wait began; {@link #NO_WAIT} when none is running (0 is a valid clock reading). */
    private long waitStartedMs = NO_WAIT;
    /** The reason the current wait is paused for, so a change of reason starts a fresh wait clock. */
    @Nullable private String waitReason;
    private final AtomicBoolean skipRequested = new AtomicBoolean();
    /** Whether the screen that owns this run has left the foreground; see {@link #setHeld}. */
    private final AtomicBoolean held = new AtomicBoolean();
    private final Set<TaiBenchSuite.EntryPlan> warmEntries =
        java.util.Collections.newSetFromMap(new IdentityHashMap<>());
    /** Entries whose phases saw the screen off, and the highest thermal status each saw; see {@link #note}. */
    private final Set<TaiBenchSuite.EntryPlan> screenOffEntries =
        java.util.Collections.newSetFromMap(new IdentityHashMap<>());
    private final Map<TaiBenchSuite.EntryPlan, Integer> peakThermal = new IdentityHashMap<>();
    private final Map<TaiBenchSuite.EntryPlan, TaiBenchGuardRules.Snapshot> startSnapshots = new IdentityHashMap<>();
    private final Map<TaiBenchSuite.EntryPlan, TaiBenchGuardRules.Snapshot> endSnapshots = new IdentityHashMap<>();

    TaiBenchConditionsGuard(@NonNull Supplier<TaiBenchGuardRules.Snapshot> reader, @NonNull LongSupplier clock) {
        this.reader = reader;
        this.clock = clock;
    }

    @NonNull
    @Override
    public Decision beforePhase(@NonNull String phase, @NonNull TaiBenchSuite.EntryPlan entry) {
        long now = clock.getAsLong();
        TaiBenchGuardRules.Snapshot snapshot = reader.get();
        if (baseline == null) {
            baseline = snapshot;
            firstEntry = entry;
        }
        // Readings before the entry's load is let go (a cool-down wait) describe the last entry's heat.
        if (startSnapshots.containsKey(entry)) note(entry, snapshot);
        boolean isFirst = entry == firstEntry;
        boolean cooldownApplies = TaiBenchSuite.PHASE_LOAD.equals(phase) && !isFirst;
        boolean skip = cooldownApplies && skipRequested.getAndSet(false);
        boolean isHeld = held.get();
        // A hold wait is its own wait: if the reason a wait is running for is about to change
        // (e.g. a cool-down wait in progress and the screen now leaves), the new wait's clock
        // starts fresh rather than inheriting the old wait's elapsed time.
        String candidateReason = candidateWaitReason(phase, cooldownApplies, isHeld, snapshot);
        if (waitStartedMs != NO_WAIT && !java.util.Objects.equals(candidateReason, waitReason)) {
            waitStartedMs = NO_WAIT;
        }
        long waitStart = waitStartedMs == NO_WAIT ? now : waitStartedMs;
        TaiBenchGuardRules.Result result = TaiBenchGuardRules.beforePhase(
            phase, isFirst, snapshot, baseline, waitStart, now, skip, isHeld);
        if (TaiBenchGuard.PAUSE.equals(result.decision.action)) {
            waitStartedMs = waitStart;
            waitReason = result.decision.reason;
            return withDetail(result.decision, snapshot);
        }
        waitStartedMs = NO_WAIT;
        waitReason = null;
        if (result.warmStart) warmEntries.add(entry);
        return result.decision;
    }

    /**
     * Remembers what one reading says about the entry it was taken during: the screen was off, and
     * the highest thermal status so far. Called from {@link #entryStarted} on, never during the
     * cool-down wait before it.
     */
    private void note(@NonNull TaiBenchSuite.EntryPlan entry, @NonNull TaiBenchGuardRules.Snapshot snapshot) {
        if (snapshot.screenOff) screenOffEntries.add(entry);
        if (snapshot.thermalStatus < 0) return;
        Integer peak = peakThermal.get(entry);
        if (peak == null || snapshot.thermalStatus > peak) peakThermal.put(entry, snapshot.thermalStatus);
    }

    /**
     * The reason a wait would be paused for right now, mirroring {@link TaiBenchGuardRules}'
     * precedence (a battery/SEVERE-thermal stop wins over any wait, held wins over cool-down,
     * cool-down wins over a thermal pause) — without running the full rule, so the wait clock can
     * be reset before the rule is consulted.
     */
    @Nullable
    private static String candidateWaitReason(@NonNull String phase, boolean cooldownApplies, boolean held,
                                               @NonNull TaiBenchGuardRules.Snapshot snapshot) {
        if (snapshot.batteryPercent >= 0 && snapshot.batteryPercent < TaiBenchGuardRules.RUNNING_BATTERY_STOP_PERCENT
                && !snapshot.charging) {
            return null;
        }
        if (snapshot.thermalStatus >= TaiBenchGuardRules.THERMAL_STATUS_SEVERE) return null;
        if (held) return "left";
        if (cooldownApplies) return "cooldown";
        if (snapshot.thermalStatus == TaiBenchGuardRules.THERMAL_STATUS_MODERATE) return "thermal";
        return null;
    }

    /** Adds the thermal status name and headroom to a pause's event, when either is known. */
    @NonNull
    private static Decision withDetail(@NonNull Decision decision, @NonNull TaiBenchGuardRules.Snapshot snapshot) {
        try {
            JSONObject detail = new JSONObject();
            String name = TaiBenchGuardRules.thermalStatusName(snapshot.thermalStatus);
            if (name != null) detail.put("thermalStatus", name);
            if (!Float.isNaN(snapshot.headroom)) detail.put("headroom", round2(snapshot.headroom));
            if (detail.length() == 0) return decision;
            return Decision.pause(decision.pauseMs, decision.reason, detail);
        } catch (JSONException e) {
            return decision;
        }
    }

    @Override
    public void entryStarted(@NonNull TaiBenchSuite.EntryPlan entry) {
        TaiBenchGuardRules.Snapshot snapshot = reader.get();
        note(entry, snapshot);
        startSnapshots.put(entry, snapshot);
    }

    @Override
    public void entryFinished(@NonNull TaiBenchSuite.EntryPlan entry) {
        TaiBenchGuardRules.Snapshot snapshot = reader.get();
        note(entry, snapshot);
        endSnapshots.put(entry, snapshot);
    }

    @NonNull
    @Override
    public JSONObject entryConditions(@NonNull TaiBenchSuite.EntryPlan entry) throws JSONException {
        TaiBenchGuardRules.Snapshot start = startSnapshots.get(entry);
        TaiBenchGuardRules.Snapshot end = endSnapshots.get(entry);
        JSONObject json = new JSONObject();
        json.put("batteryStart", start != null && start.batteryPercent >= 0 ? start.batteryPercent : JSONObject.NULL);
        json.put("batteryEnd", end != null && end.batteryPercent >= 0 ? end.batteryPercent : JSONObject.NULL);
        json.put("charging", chargingField(start, end));
        json.put("thermalStart", start == null ? JSONObject.NULL : orNull(TaiBenchGuardRules.thermalStatusName(start.thermalStatus)));
        json.put("thermalEnd", end == null ? JSONObject.NULL : orNull(TaiBenchGuardRules.thermalStatusName(end.thermalStatus)));
        json.put("headroomStart", start != null && !Float.isNaN(start.headroom) ? round2(start.headroom) : JSONObject.NULL);
        json.put("headroomEnd", end != null && !Float.isNaN(end.headroom) ? round2(end.headroom) : JSONObject.NULL);
        json.put("warmStart", warmEntries.contains(entry));
        // Heat that rose above the run-start baseline during the entry, not only at its start.
        Integer peak = peakThermal.get(entry);
        json.put("thermalRose", baseline != null && baseline.thermalStatus >= 0 && peak != null && peak > baseline.thermalStatus);
        json.put("thermalPeak", peak == null ? JSONObject.NULL : orNull(TaiBenchGuardRules.thermalStatusName(peak)));
        json.put("powerSave", start != null && start.powerSave);
        json.put("screenOff", screenOffEntries.contains(entry));
        return json;
    }

    /** {@code true}/{@code false} if either snapshot had a battery reading, {@code null} if neither did. */
    @NonNull
    private static Object chargingField(@Nullable TaiBenchGuardRules.Snapshot start, @Nullable TaiBenchGuardRules.Snapshot end) {
        boolean known = false;
        boolean charging = false;
        if (start != null && start.batteryPercent >= 0) {
            known = true;
            charging |= start.charging;
        }
        if (end != null && end.batteryPercent >= 0) {
            known = true;
            charging |= end.charging;
        }
        return known ? charging : JSONObject.NULL;
    }

    @NonNull
    private static Object orNull(@Nullable String value) {
        return value == null ? JSONObject.NULL : value;
    }

    private static double round2(float value) {
        return Math.round(value * 100.0) / 100.0;
    }

    @Override
    public void skipCooldown() {
        skipRequested.set(true);
    }

    @Override
    public void setHeld(boolean held) {
        this.held.set(held);
    }
}
