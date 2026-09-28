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
    private final AtomicBoolean skipRequested = new AtomicBoolean();
    private final Set<TaiBenchSuite.EntryPlan> warmEntries =
        java.util.Collections.newSetFromMap(new IdentityHashMap<>());
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
        boolean isFirst = entry == firstEntry;
        boolean cooldownApplies = TaiBenchSuite.PHASE_LOAD.equals(phase) && !isFirst;
        boolean skip = cooldownApplies && skipRequested.getAndSet(false);
        long waitStart = waitStartedMs == NO_WAIT ? now : waitStartedMs;
        TaiBenchGuardRules.Result result = TaiBenchGuardRules.beforePhase(
            phase, isFirst, snapshot, baseline, waitStart, now, skip);
        if (TaiBenchGuard.PAUSE.equals(result.decision.action)) {
            waitStartedMs = waitStart;
            return withDetail(result.decision, snapshot);
        }
        waitStartedMs = NO_WAIT;
        if (result.warmStart) warmEntries.add(entry);
        return result.decision;
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
        startSnapshots.put(entry, reader.get());
    }

    @Override
    public void entryFinished(@NonNull TaiBenchSuite.EntryPlan entry) {
        endSnapshots.put(entry, reader.get());
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
}
