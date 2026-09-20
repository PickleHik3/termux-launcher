package com.termux.app.launcher.widget;

import android.app.Application;
import android.content.ComponentName;
import android.os.Build;
import android.os.Bundle;
import android.util.SizeF;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.S, application = Application.class)
public class LauncherWidgetRepositoryTest {
    @Test public void emptyV1StableRoundTripAndImmutableSnapshots() {
        Memory storage = new Memory();
        LauncherWidgetRepository repository = new LauncherWidgetRepository(storage);
        assertTrue(repository.records().isEmpty());
        LauncherWidgetRecord active = record(4, LauncherWidgetRecord.State.ACTIVE);
        assertTrue(repository.putRecord(active));
        WidgetAddTransaction pending = transaction(7);
        assertTrue(repository.setPending(pending));
        String serialized = repository.serialize();
        LauncherWidgetRepository restored = new LauncherWidgetRepository(new Memory(serialized));
        assertEquals(serialized, restored.serialize());
        assertEquals(1, restored.records().size());
        assertEquals(7, restored.pending().appWidgetId);
        assertNotSame(active.sizeOptions(), restored.get(4).sizeOptions());
        active.sizeOptions().putInt("mutated", 1);
        assertFalse(restored.get(4).sizeOptions().containsKey("mutated"));
        try {
            restored.records().clear();
            throw new AssertionError("snapshot was mutable");
        } catch (UnsupportedOperationException expected) { }
    }

    @Test public void synchronousFailureAndDuplicateAndOnePendingAreRejected() {
        Memory storage = new Memory();
        LauncherWidgetRepository repository = new LauncherWidgetRepository(storage);
        storage.fail = true;
        assertFalse(repository.putRecord(record(1, LauncherWidgetRecord.State.ACTIVE)));
        assertNull(repository.get(1));
        storage.fail = false;
        assertTrue(repository.putRecord(record(1, LauncherWidgetRecord.State.ACTIVE)));
        try {
            repository.putRecord(new LauncherWidgetRecord(1, new ComponentName("other", "P"), 0,
                LauncherWidgetRecord.State.ACTIVE, new Bundle(), null));
            throw new AssertionError("duplicate ID accepted");
        } catch (IllegalArgumentException expected) { }
        assertTrue(repository.setPending(transaction(2)));
        try {
            repository.setPending(transaction(3));
            throw new AssertionError("second pending accepted");
        } catch (IllegalStateException expected) { }
    }

    @Test public void activeTombstoneDeletingAndPendingRoundTrip() {
        for (LauncherWidgetRecord.State state : LauncherWidgetRecord.State.values()) {
            Memory storage = new Memory();
            LauncherWidgetRepository repository = new LauncherWidgetRepository(storage);
            repository.putRecord(record(state.ordinal() + 1, state));
            LauncherWidgetRepository copy = new LauncherWidgetRepository(new Memory(repository.serialize()));
            assertEquals(state, copy.records().get(0).state);
        }
    }

    @Test public void api31SizeListSurvivesStableRoundTrip() {
        Bundle options = new Bundle();
        java.util.ArrayList<SizeF> sizes = new java.util.ArrayList<>();
        sizes.add(new SizeF(120f, 80f));
        sizes.add(new SizeF(220f, 60f));
        options.putParcelableArrayList(android.appwidget.AppWidgetManager.OPTION_APPWIDGET_SIZES,
            sizes);
        Memory storage = new Memory();
        LauncherWidgetRepository repository = new LauncherWidgetRepository(storage);
        repository.putRecord(new LauncherWidgetRecord(12,
            new ComponentName("pkg", "Provider"), 0, LauncherWidgetRecord.State.ACTIVE,
            options, null));
        LauncherWidgetRepository restored = new LauncherWidgetRepository(new Memory(storage.value));
        assertEquals(sizes, restored.get(12).sizeOptions().getParcelableArrayList(
            android.appwidget.AppWidgetManager.OPTION_APPWIDGET_SIZES));
        assertEquals(storage.value, restored.serialize());
    }

    @Test public void resumedDeletionClearsPendingLockWhenWidgetIdMatches() {
        LauncherWidgetRepository repository = new LauncherWidgetRepository(new Memory());
        WidgetAddTransaction pending = transaction(7);
        assertTrue(repository.setPending(pending));
        assertTrue(repository.beginPendingDeletion(pending));
        assertTrue(repository.completeDeletion(7, null));
        assertNull(repository.pending());
        assertNull(repository.get(7));
    }

    @Test public void putRecordsCommitsTheWholeBatchAtomically() {
        Memory storage = new Memory();
        LauncherWidgetRepository repository = new LauncherWidgetRepository(storage);
        assertTrue(repository.putRecord(placed(1, new WidgetCellRect(0, 0, 1, 1))));
        assertTrue(repository.putRecord(placed(2, new WidgetCellRect(1, 0, 2, 1))));
        long before = repository.revision();
        // A swap: neither half is placeable on its own, the pair is.
        assertTrue(repository.putRecords(java.util.Arrays.asList(
            placed(1, new WidgetCellRect(1, 0, 2, 1)),
            placed(2, new WidgetCellRect(0, 0, 1, 1)))));
        assertEquals(before + 1, repository.revision());
        assertEquals(new WidgetCellRect(1, 0, 2, 1), repository.get(1).cell);
        assertEquals(new WidgetCellRect(0, 0, 1, 1), repository.get(2).cell);
        LauncherWidgetRepository restored = new LauncherWidgetRepository(new Memory(storage.value));
        assertEquals(new WidgetCellRect(1, 0, 2, 1), restored.get(1).cell);
        assertEquals(new WidgetCellRect(0, 0, 1, 1), restored.get(2).cell);
    }

    @Test public void putRecordsRejectsAnOverlappingBatchAndChangesNothing() {
        LauncherWidgetRepository repository = new LauncherWidgetRepository(new Memory());
        assertTrue(repository.putRecord(placed(1, new WidgetCellRect(0, 0, 1, 1))));
        assertTrue(repository.putRecord(placed(2, new WidgetCellRect(1, 0, 2, 1))));
        long before = repository.revision();
        assertFalse(repository.putRecords(java.util.Arrays.asList(
            placed(1, new WidgetCellRect(2, 0, 3, 1)),
            placed(2, new WidgetCellRect(2, 0, 3, 1)))));
        assertEquals(before, repository.revision());
        assertEquals(new WidgetCellRect(0, 0, 1, 1), repository.get(1).cell);
        assertEquals(new WidgetCellRect(1, 0, 2, 1), repository.get(2).cell);
    }

    @Test public void putRecordsRejectsAProviderMismatch() {
        LauncherWidgetRepository repository = new LauncherWidgetRepository(new Memory());
        assertTrue(repository.putRecord(placed(1, new WidgetCellRect(0, 0, 1, 1))));
        try {
            repository.putRecords(java.util.Collections.singletonList(
                new LauncherWidgetRecord(1, new ComponentName("other", "P"), 0,
                    LauncherWidgetRecord.State.ACTIVE, new WidgetCellRect(1, 0, 2, 1),
                    new Bundle(), null)));
            throw new AssertionError("provider mismatch accepted");
        } catch (IllegalArgumentException expected) { }
        assertEquals(new WidgetCellRect(0, 0, 1, 1), repository.get(1).cell);
    }

    private static LauncherWidgetRecord placed(int id, WidgetCellRect cell) {
        return new LauncherWidgetRecord(id, new ComponentName("pkg", "Provider"), 0,
            LauncherWidgetRecord.State.ACTIVE, cell, new Bundle(), null);
    }

    private static LauncherWidgetRecord record(int id, LauncherWidgetRecord.State state) {
        Bundle options = new Bundle();
        options.putInt("width", 42);
        return new LauncherWidgetRecord(id, new ComponentName("pkg", "Provider"), 0, state,
            options, state == LauncherWidgetRecord.State.ACTIVE ? null : "phase");
    }
    private static WidgetAddTransaction transaction(int id) {
        return new WidgetAddTransaction("token-" + id, id, new ComponentName("pkg", "Provider"),
            0, WidgetAddTransaction.Stage.ALLOCATED, new Bundle(), 10);
    }
    private static final class Memory implements LauncherWidgetRepository.Storage {
        String value; boolean fail;
        Memory() { }
        Memory(String value) { this.value = value; }
        @Override public String read() { return value; }
        @Override public boolean write(String value) { if (fail) return false; this.value = value; return true; }
    }
}
