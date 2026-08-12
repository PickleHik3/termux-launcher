package com.termux.app.launcher.widget;

import android.app.Application;
import android.content.ComponentName;
import android.os.Build;
import android.os.Bundle;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import static org.junit.Assert.assertEquals;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.S, application = Application.class)
public class WidgetProviderReconcilePolicyTest {
    @Test public void activeUpdateMissingProfileDeletingAndForeign() {
        LauncherWidgetRecord active = record(LauncherWidgetRecord.State.ACTIVE);
        assertEquals(WidgetProviderReconcilePolicy.Decision.KEEP,
            WidgetProviderReconcilePolicy.forRecord(active, true, true, false));
        assertEquals(WidgetProviderReconcilePolicy.Decision.REFRESH_AFTER_UPDATE,
            WidgetProviderReconcilePolicy.forRecord(active, true, true, true));
        assertEquals(WidgetProviderReconcilePolicy.Decision.TOMBSTONE_AND_DELETE_ID,
            WidgetProviderReconcilePolicy.forRecord(active, true, false, false));
        assertEquals(WidgetProviderReconcilePolicy.Decision.RESUME_DELETION,
            WidgetProviderReconcilePolicy.forRecord(record(LauncherWidgetRecord.State.DELETING),
                true, true, false));
        assertEquals(WidgetProviderReconcilePolicy.Decision.IGNORE_FOREIGN_HOST_ID,
            WidgetProviderReconcilePolicy.forRecord(active, false, true, false));
    }

    @Test public void pendingBoundUnboundAndExpired() {
        WidgetAddTransaction pending = new WidgetAddTransaction("t", 3,
            new ComponentName("pkg", "Provider"), 0,
            WidgetAddTransaction.Stage.WAITING_FOR_BIND_CONSENT, new Bundle(), 100);
        assertEquals(WidgetProviderReconcilePolicy.Decision.EXPIRE_PENDING_AND_DELETE_ID,
            WidgetProviderReconcilePolicy.forPending(pending, false, 200));
        assertEquals(WidgetProviderReconcilePolicy.Decision.RESUME_CONFIGURATION,
            WidgetProviderReconcilePolicy.forPending(pending, true, 200));
        assertEquals(WidgetProviderReconcilePolicy.Decision.EXPIRE_PENDING_AND_DELETE_ID,
            WidgetProviderReconcilePolicy.forPending(pending, false,
                100 + WidgetProviderReconcilePolicy.PENDING_MAX_AGE_MS));
    }

    @Test public void committingRecoversImmediatelyAndNeverWaitsForExpiry() {
        WidgetAddTransaction committing = new WidgetAddTransaction("t", 3,
            new ComponentName("pkg", "Provider"), 0,
            WidgetAddTransaction.Stage.COMMITTING, new Bundle(), 100);
        assertEquals(WidgetProviderReconcilePolicy.Decision.RESUME_ACTIVE_COMMIT,
            WidgetProviderReconcilePolicy.forPending(committing, true, 101));
        assertEquals(WidgetProviderReconcilePolicy.Decision.EXPIRE_PENDING_AND_DELETE_ID,
            WidgetProviderReconcilePolicy.forPending(committing, false, 101));
    }

    @Test public void ownedProviderMissingTombstoneRetriesPerIdDeletion() {
        LauncherWidgetRecord missing = record(LauncherWidgetRecord.State.PROVIDER_MISSING);
        assertEquals(WidgetProviderReconcilePolicy.Decision.RETRY_TOMBSTONE_DELETE_ID,
            WidgetProviderReconcilePolicy.forRecord(missing, true, true, false, false));
        assertEquals(WidgetProviderReconcilePolicy.Decision.KEEP,
            WidgetProviderReconcilePolicy.forRecord(missing, true, false, false, false));
    }

    private static LauncherWidgetRecord record(LauncherWidgetRecord.State state) {
        return new LauncherWidgetRecord(3, new ComponentName("pkg", "Provider"), 0,
            state, new Bundle(), null);
    }
}
