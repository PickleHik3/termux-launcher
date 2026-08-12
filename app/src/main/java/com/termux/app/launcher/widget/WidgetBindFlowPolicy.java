package com.termux.app.launcher.widget;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/** Pure, one-way transition policy for the durable bind/configure transaction. */
public final class WidgetBindFlowPolicy {
    public enum Outcome {
        CONTINUE,
        READY,
        DECLINED,
        FAILED_DELETE_ID,
        IGNORE_FOREIGN_RESULT
    }

    public static final class Decision {
        @NonNull public final Outcome outcome;
        @Nullable public final WidgetAddTransaction.Stage nextStage;
        public final boolean deleteId;

        private Decision(@NonNull Outcome outcome,
                         @Nullable WidgetAddTransaction.Stage nextStage,
                         boolean deleteId) {
            this.outcome = outcome;
            this.nextStage = nextStage;
            this.deleteId = deleteId;
        }
    }

    private WidgetBindFlowPolicy() {}

    @NonNull
    public static Decision afterAllocation(boolean directlyBound) {
        return continueAt(directlyBound ? WidgetAddTransaction.Stage.BOUND
            : WidgetAddTransaction.Stage.WAITING_FOR_BIND_CONSENT);
    }

    @NonNull
    public static Decision onBindResult(@Nullable WidgetAddTransaction pending,
                                        int persistedId, int returnedId,
                                        boolean resultOk, boolean providerMatches) {
        if (pending == null || pending.stage != WidgetAddTransaction.Stage.WAITING_FOR_BIND_CONSENT
            || pending.appWidgetId != persistedId) {
            return decision(Outcome.IGNORE_FOREIGN_RESULT, null, false);
        }
        if (returnedId > 0 && returnedId != persistedId)
            return decision(Outcome.IGNORE_FOREIGN_RESULT, null, false);
        if (!resultOk) return decision(Outcome.DECLINED, null, true);
        if (!providerMatches) return decision(Outcome.FAILED_DELETE_ID, null, true);
        return continueAt(WidgetAddTransaction.Stage.BOUND);
    }

    @NonNull
    public static Decision afterConfigureDecision(@NonNull WidgetConfigurePolicy.Decision configure) {
        switch (configure) {
            case NONE:
                return continueAt(WidgetAddTransaction.Stage.COMMITTING);
            case REQUIRED:
                return continueAt(WidgetAddTransaction.Stage.WAITING_FOR_CONFIGURATION);
            default:
                return decision(Outcome.FAILED_DELETE_ID, null, true);
        }
    }

    @NonNull
    public static Decision onConfigureResult(@Nullable WidgetAddTransaction pending,
                                             int persistedId, int returnedId,
                                             boolean resultOk, boolean providerMatches) {
        if (pending == null || pending.stage != WidgetAddTransaction.Stage.WAITING_FOR_CONFIGURATION
            || pending.appWidgetId != persistedId) {
            return decision(Outcome.IGNORE_FOREIGN_RESULT, null, false);
        }
        if (returnedId > 0 && returnedId != persistedId)
            return decision(Outcome.IGNORE_FOREIGN_RESULT, null, false);
        if (!resultOk) return decision(Outcome.DECLINED, null, true);
        if (!providerMatches) return decision(Outcome.FAILED_DELETE_ID, null, true);
        return decision(Outcome.READY, WidgetAddTransaction.Stage.COMMITTING, false);
    }

    @NonNull
    public static Decision cancel(@Nullable WidgetAddTransaction pending) {
        return pending == null
            ? decision(Outcome.IGNORE_FOREIGN_RESULT, null, false)
            : decision(Outcome.DECLINED, null, true);
    }

    private static Decision continueAt(WidgetAddTransaction.Stage stage) {
        return decision(Outcome.CONTINUE, stage, false);
    }

    private static Decision decision(Outcome outcome, WidgetAddTransaction.Stage stage,
                                     boolean delete) {
        return new Decision(outcome, stage, delete);
    }
}
