package com.termux.ai;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * Consulted by {@link TaiBenchHarness} between phases: whether the phone is in a state to go on.
 * Slice 1 shipped only {@link #ALWAYS_CONTINUE}; slice 2 is {@link TaiBenchConditionsGuard}, the
 * battery and thermal rules of the spec (pause until the headroom recovers, stop below 15 %,
 * cool down between entries) behind this same interface, decided by {@link TaiBenchGuardRules}.
 */
interface TaiBenchGuard {
    /** Go on with the next phase. */
    String CONTINUE = "continue";
    /** Wait {@link Decision#pauseMs} and ask again. */
    String PAUSE = "pause";
    /** End the run; the entry is recorded as {@code stopped:<reason>}. */
    String STOP = "stop";

    final class Decision {
        @NonNull final String action;
        final long pauseMs;
        @Nullable final String reason;
        /** Extra fields a pause wants on its {@code paused} event, e.g. thermal status and headroom. */
        @Nullable final JSONObject detail;

        private Decision(@NonNull String action, long pauseMs, @Nullable String reason, @Nullable JSONObject detail) {
            this.action = action;
            this.pauseMs = pauseMs;
            this.reason = reason;
            this.detail = detail;
        }

        @NonNull
        static Decision proceed() {
            return new Decision(CONTINUE, 0L, null, null);
        }

        @NonNull
        static Decision pause(long pauseMs, @Nullable String reason) {
            return new Decision(PAUSE, Math.max(0L, pauseMs), reason, null);
        }

        @NonNull
        static Decision pause(long pauseMs, @Nullable String reason, @Nullable JSONObject detail) {
            return new Decision(PAUSE, Math.max(0L, pauseMs), reason, detail);
        }

        @NonNull
        static Decision stop(@NonNull String reason) {
            return new Decision(STOP, 0L, reason, null);
        }
    }

    /**
     * @param phase the phase about to start, one of the {@code TaiBenchSuite.PHASE_*} names
     * @param entry the entry being run
     */
    @NonNull
    Decision beforePhase(@NonNull String phase, @NonNull TaiBenchSuite.EntryPlan entry);

    /** Called once {@link #beforePhase} has let {@code entry}'s load phase go ahead: the start snapshot. */
    default void entryStarted(@NonNull TaiBenchSuite.EntryPlan entry) {
    }

    /** Called once in the harness's {@code finally}, before the entry's model is unloaded: the end snapshot. */
    default void entryFinished(@NonNull TaiBenchSuite.EntryPlan entry) {
    }

    /**
     * The record's {@code conditions} field for {@code entry}: {@code {batteryStart, batteryEnd,
     * charging, thermalStart, thermalEnd, headroomStart, headroomEnd, warmStart}}. The default
     * (what {@link #ALWAYS_CONTINUE} answers) is every field {@code null} except {@code warmStart},
     * which is {@code false} — the shape slice 1's callers already expect.
     */
    @NonNull
    default JSONObject entryConditions(@NonNull TaiBenchSuite.EntryPlan entry) throws JSONException {
        JSONObject json = new JSONObject();
        for (String key : new String[] {"batteryStart", "batteryEnd", "charging", "thermalStart", "thermalEnd",
                "headroomStart", "headroomEnd"}) {
            json.put(key, JSONObject.NULL);
        }
        json.put("warmStart", false);
        return json;
    }

    /** Ends the current or next cool-down wait at once; that entry's record is marked {@code warmStart}. */
    default void skipCooldown() {
    }

    /** A guard that lets everything through; what slice 1 ran with, and what tests reach for. */
    TaiBenchGuard ALWAYS_CONTINUE = (phase, entry) -> Decision.proceed();
}
