package com.termux.ai;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Consulted by {@link TaiBenchHarness} between phases: whether the phone is in a state to go on.
 * Slice 1 ships only {@link #ALWAYS_CONTINUE}; slice 2 implements the battery and thermal rules
 * of the spec (pause until the headroom recovers, stop below 15 %) behind this interface without
 * the harness changing.
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

        private Decision(@NonNull String action, long pauseMs, @Nullable String reason) {
            this.action = action;
            this.pauseMs = pauseMs;
            this.reason = reason;
        }

        @NonNull
        static Decision proceed() {
            return new Decision(CONTINUE, 0L, null);
        }

        @NonNull
        static Decision pause(long pauseMs, @Nullable String reason) {
            return new Decision(PAUSE, Math.max(0L, pauseMs), reason);
        }

        @NonNull
        static Decision stop(@NonNull String reason) {
            return new Decision(STOP, 0L, reason);
        }
    }

    /**
     * @param phase the phase about to start, one of the {@code TaiBenchSuite.PHASE_*} names
     * @param entry the entry being run
     */
    @NonNull
    Decision beforePhase(@NonNull String phase, @NonNull TaiBenchSuite.EntryPlan entry);

    /** A guard that lets everything through; what slice 1 runs with. */
    TaiBenchGuard ALWAYS_CONTINUE = (phase, entry) -> Decision.proceed();
}
