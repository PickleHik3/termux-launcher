package com.termux.app.launcher.widget;

import androidx.annotation.NonNull;

/** Pure reconciliation choice; the controller owns platform calls and durable commits. */
public final class WidgetProviderReconcilePolicy {
    public enum Decision {
        KEEP,
        REFRESH_AFTER_UPDATE,
        TOMBSTONE_AND_DELETE_ID,
        RESUME_DELETION,
        RETRY_TOMBSTONE_DELETE_ID,
        RETAIN_PENDING,
        RESUME_CONFIGURATION,
        RESUME_ACTIVE_COMMIT,
        EXPIRE_PENDING_AND_DELETE_ID,
        IGNORE_FOREIGN_HOST_ID
    }

    public static final long PENDING_MAX_AGE_MS = 24L * 60L * 60L * 1000L;

    private WidgetProviderReconcilePolicy() {}

    @NonNull
    public static Decision forRecord(@NonNull LauncherWidgetRecord record,
                                     boolean repositoryOwnsId, boolean providerMatches,
                                     boolean providerChanged) {
        return forRecord(record, repositoryOwnsId, true, providerMatches, providerChanged);
    }

    @NonNull
    public static Decision forRecord(@NonNull LauncherWidgetRecord record,
                                     boolean repositoryOwnsId, boolean hostOwnsId,
                                     boolean providerMatches, boolean providerChanged) {
        if (!repositoryOwnsId) return Decision.IGNORE_FOREIGN_HOST_ID;
        if (record.state == LauncherWidgetRecord.State.DELETING) return Decision.RESUME_DELETION;
        if (record.state == LauncherWidgetRecord.State.PROVIDER_MISSING) {
            return hostOwnsId ? Decision.RETRY_TOMBSTONE_DELETE_ID : Decision.KEEP;
        }
        if (!hostOwnsId || !providerMatches) return Decision.TOMBSTONE_AND_DELETE_ID;
        return providerChanged ? Decision.REFRESH_AFTER_UPDATE : Decision.KEEP;
    }

    @NonNull
    public static Decision forPending(@NonNull WidgetAddTransaction pending,
                                      boolean boundAndMatching, long nowMillis) {
        if (pending.stage == WidgetAddTransaction.Stage.COMMITTING) {
            return boundAndMatching ? Decision.RESUME_ACTIVE_COMMIT
                : Decision.EXPIRE_PENDING_AND_DELETE_ID;
        }
        if (nowMillis - pending.startedAtMillis >= PENDING_MAX_AGE_MS) {
            return Decision.EXPIRE_PENDING_AND_DELETE_ID;
        }
        if (boundAndMatching && (pending.stage == WidgetAddTransaction.Stage.ALLOCATED
            || pending.stage == WidgetAddTransaction.Stage.WAITING_FOR_BIND_CONSENT
            || pending.stage == WidgetAddTransaction.Stage.BOUND
            || pending.stage == WidgetAddTransaction.Stage.WAITING_FOR_CONFIGURATION)) {
            return Decision.RESUME_CONFIGURATION;
        }
        return Decision.RETAIN_PENDING;
    }
}
