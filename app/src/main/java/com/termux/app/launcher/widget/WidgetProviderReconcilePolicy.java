package com.termux.app.launcher.widget;

import androidx.annotation.NonNull;

import java.util.Collection;
import java.util.Set;

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

    /**
     * How many widgets a wall must hold before losing every one of them at once is read as the
     * wall itself being gone rather than as ordinary removals. With a single widget there is
     * nothing to tell the two apart, and one placeholder is not a screen of them.
     */
    public static final int LOST_WALL_MIN_RECORDS = 2;

    /**
     * Whether the host has forgotten this whole wall: a restore onto another device, a wipe of
     * the host's own data, a downgrade. Every widget the repository holds has an ID the host no
     * longer knows, so not one of them can be reconnected and none of the placements mean
     * anything any more.
     *
     * <p>This is deliberately the wholesale case only. One provider going while its neighbours
     * stay is still a per-widget matter, decided by {@link #forRecord}, and an add in flight
     * defers the question to the reconciliation after it settles.
     *
     * @param records      every record the repository holds.
     * @param hostOwnedIds the IDs the host says are allocated to it right now.
     * @param addInFlight  whether a pending add transaction is live.
     */
    public static boolean isWallLost(@NonNull Collection<LauncherWidgetRecord> records,
                                     @NonNull Set<Integer> hostOwnedIds, boolean addInFlight) {
        if (addInFlight) return false;
        int live = 0;
        for (LauncherWidgetRecord record : records) {
            // A record already on its way out proves nothing either way; it is leaving regardless.
            if (record.state == LauncherWidgetRecord.State.DELETING) continue;
            if (hostOwnedIds.contains(record.appWidgetId)) return false;
            live++;
        }
        return live >= LOST_WALL_MIN_RECORDS;
    }

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
        if (!boundAndMatching && (pending.stage == WidgetAddTransaction.Stage.ALLOCATED
            || pending.stage == WidgetAddTransaction.Stage.WAITING_FOR_BIND_CONSENT)) {
            return Decision.EXPIRE_PENDING_AND_DELETE_ID;
        }
        if (boundAndMatching && (pending.stage == WidgetAddTransaction.Stage.ALLOCATED
            || pending.stage == WidgetAddTransaction.Stage.WAITING_FOR_BIND_CONSENT
            || pending.stage == WidgetAddTransaction.Stage.BOUND)) {
            return Decision.RESUME_CONFIGURATION;
        }
        return Decision.RETAIN_PENDING;
    }
}
