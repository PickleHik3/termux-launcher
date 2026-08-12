package com.termux.app.launcher.widget;

import android.content.ComponentName;
import android.os.Bundle;

import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import android.app.Application;
import android.os.Build;

import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.S, application = Application.class)
public class LauncherWidgetRepositoryV2Test {
    @Test public void exactGridCellPendingRoundTripAndRevision() throws Exception {
        WidgetTestFixtures.Memory storage = new WidgetTestFixtures.Memory();
        LauncherWidgetRepository repository = new LauncherWidgetRepository(storage);
        WidgetCellRect cell = new WidgetCellRect(1, 2, 3, 4);
        WidgetAddTransaction pending = transaction(20, cell, repository.revision());
        assertTrue(repository.reservePending(0, pending));
        JSONObject root = new JSONObject(storage.value);
        assertEquals(2, root.getInt("version"));
        assertEquals(WidgetGridDefinition.DEFAULT_ROWS,
            root.getJSONObject("grid").getInt("rows"));
        LauncherWidgetRepository restored = new LauncherWidgetRepository(storage);
        assertEquals(cell, restored.pending().cell); assertEquals(1, restored.revision());
    }

    @Test public void reservationFinalizeIsAtomicAndConsumesExactRect() {
        LauncherWidgetRepository repository = WidgetTestFixtures.repository();
        WidgetCellRect cell = new WidgetCellRect(0, 0, 2, 2);
        WidgetAddTransaction tx = transaction(20, cell, 0);
        assertTrue(repository.reservePending(0, tx));
        LauncherWidgetRecord active = new LauncherWidgetRecord(20, tx.provider, 0,
            LauncherWidgetRecord.State.ACTIVE, cell, new Bundle(), null);
        assertTrue(repository.finalizeActive(tx.token, active));
        assertNull(repository.pending()); assertEquals(cell, repository.get(20).cell);
    }

    @Test public void staleAndCollisionReservationsAreRejectedWithoutMutation() {
        LauncherWidgetRepository repository = WidgetTestFixtures.repository();
        assertTrue(repository.putRecord(record(1, new WidgetCellRect(0, 0, 2, 2))));
        String before = repository.serialize();
        assertFalse(repository.reservePending(0, transaction(20,
            new WidgetCellRect(2, 0, 3, 1), 0)));
        assertFalse(repository.reservePending(repository.revision(), transaction(21,
            new WidgetCellRect(1, 1, 3, 3), repository.revision())));
        assertEquals(before, repository.serialize());
    }

    @Test public void storageFailurePreservesOldSnapshotAndReturnedValuesAreImmutable() {
        WidgetTestFixtures.Memory storage = new WidgetTestFixtures.Memory();
        LauncherWidgetRepository repository = new LauncherWidgetRepository(storage);
        assertTrue(repository.putRecord(record(1, new WidgetCellRect(0, 0, 1, 1))));
        long revision = repository.revision(); storage.fail = true;
        assertFalse(repository.putRecord(record(2, new WidgetCellRect(1, 0, 2, 1))));
        assertEquals(revision, repository.revision()); assertNull(repository.get(2));
        try { repository.records().clear(); fail(); } catch (UnsupportedOperationException expected) { }
    }

    @Test public void atomicLayoutSeamRejectsOverlapAndAcceptsRevisionedGrid() {
        LauncherWidgetRepository repository = WidgetTestFixtures.repository();
        LauncherWidgetRecord one = record(1, new WidgetCellRect(0, 0, 1, 1));
        LauncherWidgetRecord two = record(2, new WidgetCellRect(0, 0, 1, 1));
        assertFalse(repository.updateLayout(0, WidgetGridDefinition.DEFAULT,
            java.util.Arrays.asList(one, two)));
        assertTrue(repository.updateLayout(0, WidgetGridDefinition.DEFAULT,
            java.util.Collections.singletonList(one)));
    }

    @Test public void pendingDeletionSnapshotSurvivesProcessRecreation() {
        WidgetTestFixtures.Memory storage = new WidgetTestFixtures.Memory();
        LauncherWidgetRepository repository = new LauncherWidgetRepository(storage);
        WidgetCellRect cell = new WidgetCellRect(0, 0, 2, 2);
        WidgetAddTransaction transaction = transaction(20, cell, repository.revision());
        assertTrue(repository.reservePending(repository.revision(), transaction));
        assertTrue(repository.beginPendingDeletion(transaction));

        LauncherWidgetRepository restored = new LauncherWidgetRepository(storage);
        assertNotNull(restored.pending());
        assertEquals(LauncherWidgetRecord.State.DELETING, restored.get(20).state);
        assertEquals(cell, restored.pending().cell);
        assertEquals(cell, restored.get(20).cell);
    }

    private static WidgetAddTransaction transaction(int id, WidgetCellRect cell, long revision) {
        return new WidgetAddTransaction("token-" + id, id, new ComponentName("pkg", "P"), 0,
            WidgetAddTransaction.Stage.ALLOCATED, cell, revision, "origin", new Bundle(), 10);
    }
    private static LauncherWidgetRecord record(int id, WidgetCellRect cell) {
        return new LauncherWidgetRecord(id, new ComponentName("pkg", "P" + id), 0,
            LauncherWidgetRecord.State.ACTIVE, cell, new Bundle(), null);
    }
}
