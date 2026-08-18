package com.termux.app.launcher.widget;

import android.app.Application;
import android.content.ComponentName;
import android.os.Build;
import android.os.Bundle;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.S, application = Application.class)
public class LauncherWidgetRepositoryV3Test {
    @Test public void v3RoundTripPersistsPageCountAndPerRecordPages() throws Exception {
        WidgetTestFixtures.Memory storage = new WidgetTestFixtures.Memory();
        LauncherWidgetRepository repository = new LauncherWidgetRepository(storage);
        assertEquals(1, repository.pageCount());
        assertEquals(1, repository.addPage());
        assertTrue(repository.putRecord(
            record(1, new WidgetCellRect(0, 0, 2, 2), 1)));
        JSONObject root = new JSONObject(storage.value);
        assertEquals(3, root.getInt("version"));
        assertEquals(2, root.getInt("pages"));
        assertEquals(1, root.getJSONArray("records").getJSONObject(0).getInt("page"));
        LauncherWidgetRepository restored = new LauncherWidgetRepository(storage);
        assertEquals(2, restored.pageCount());
        assertEquals(1, restored.get(1).page);
        assertEquals(1, restored.recordsOnPage(1).size());
        assertTrue(restored.recordsOnPage(0).isEmpty());
    }

    @Test public void v2PayloadMigratesEveryEntryToPageZeroWithOneAtomicWrite() throws Exception {
        JSONObject v2 = new JSONObject().put("version", 2).put("revision", 5)
            .put("grid", new JSONObject().put("rows", 5).put("columns", 4))
            .put("records", new JSONArray().put(encodedRecord(9, 0, 0, 2, 2)))
            .put("pending", encodedPending(20, 2, 0, 3, 1));
        CountingStorage storage = new CountingStorage(v2.toString());
        LauncherWidgetRepository repository = new LauncherWidgetRepository(storage);
        assertEquals(1, storage.writes);
        assertEquals(1, repository.pageCount());
        assertEquals(0, repository.get(9).page);
        assertEquals(0, repository.pending().page);
        assertEquals(5, repository.revision());
        JSONObject migrated = new JSONObject(storage.value);
        assertEquals(3, migrated.getInt("version"));
        assertEquals(1, migrated.getInt("pages"));
        assertEquals(0, migrated.getJSONArray("records").getJSONObject(0).getInt("page"));
    }

    @Test public void failedV2MigrationWriteRetainsIdentitiesAndRetriesNextConstruction()
        throws Exception {
        JSONObject v2 = new JSONObject().put("version", 2).put("revision", 1)
            .put("grid", new JSONObject().put("rows", 5).put("columns", 4))
            .put("records", new JSONArray().put(encodedRecord(7, 0, 0, 1, 1)));
        CountingStorage storage = new CountingStorage(v2.toString());
        storage.fail = true;
        LauncherWidgetRepository first = new LauncherWidgetRepository(storage);
        assertNotNull(first.get(7));
        assertFalse("commits stay blocked until the migration write lands",
            first.putRecord(record(8, new WidgetCellRect(1, 0, 2, 1), 0)));
        storage.fail = false;
        LauncherWidgetRepository retry = new LauncherWidgetRepository(storage);
        assertNotNull(retry.get(7));
        assertEquals(3, new JSONObject(storage.value).getInt("version"));
    }

    @Test public void collisionsAreScopedPerPage() {
        LauncherWidgetRepository repository = new LauncherWidgetRepository(
            new WidgetTestFixtures.Memory());
        assertEquals(1, repository.addPage());
        WidgetCellRect cell = new WidgetCellRect(0, 0, 2, 2);
        assertTrue(repository.putRecord(record(1, cell, 0)));
        assertTrue("same cell on another page must not collide",
            repository.putRecord(record(2, cell, 1)));
        assertFalse("same cell on the same page must collide",
            repository.putRecord(record(3, cell, 0)));
        assertFalse("records beyond the page count are invalid",
            repository.putRecord(record(4, new WidgetCellRect(2, 0, 3, 1), 2)));
    }

    @Test public void removePageRenumbersLaterPagesAndRefusesPopulatedOrLastPage() {
        LauncherWidgetRepository repository = new LauncherWidgetRepository(
            new WidgetTestFixtures.Memory());
        assertEquals(1, repository.addPage());
        assertEquals(2, repository.addPage());
        assertTrue(repository.putRecord(record(1, new WidgetCellRect(0, 0, 1, 1), 2)));
        assertFalse("populated page is not removable", repository.removePage(2));
        assertTrue("empty middle page removes", repository.removePage(1));
        assertEquals(2, repository.pageCount());
        assertEquals("later pages renumber down", 1, repository.get(1).page);
        assertTrue(repository.removePage(0));
        assertEquals(1, repository.pageCount());
        assertEquals(0, repository.get(1).page);
        assertFalse("the last page always stays", repository.removePage(0));
    }

    @Test public void reservationsArePageScopedAndUnknownFutureVersionStaysReadOnly()
        throws Exception {
        WidgetTestFixtures.Memory storage = new WidgetTestFixtures.Memory();
        LauncherWidgetRepository repository = new LauncherWidgetRepository(storage);
        assertEquals(1, repository.addPage());
        assertTrue(repository.putRecord(record(1, new WidgetCellRect(0, 0, 2, 2), 0)));
        long revision = repository.revision();
        assertFalse("page-0 collision blocks a page-0 reservation",
            repository.canReserve(revision, new WidgetCellRect(1, 1, 2, 2), 0));
        assertTrue("page 1 is free at the same cells",
            repository.canReserve(revision, new WidgetCellRect(1, 1, 2, 2), 1));
        WidgetAddTransaction reservation = new WidgetAddTransaction("token", 20,
            new ComponentName("pkg", "P"), 0, WidgetAddTransaction.Stage.ALLOCATED,
            new WidgetCellRect(1, 1, 2, 2), 1, revision, null, new Bundle(), 10);
        assertTrue(repository.reservePending(revision, reservation));
        assertEquals(1, new JSONObject(storage.value).getJSONObject("pending").getInt("page"));

        WidgetTestFixtures.Memory future = new WidgetTestFixtures.Memory();
        future.value = new JSONObject().put("version", 4).toString();
        LauncherWidgetRepository readOnly = new LauncherWidgetRepository(future);
        assertFalse(readOnly.putRecord(record(5, new WidgetCellRect(0, 0, 1, 1), 0)));
        assertEquals("an unknown newer payload is never overwritten",
            new JSONObject().put("version", 4).toString(), future.value);
    }

    private static LauncherWidgetRecord record(int id, WidgetCellRect cell, int page) {
        return new LauncherWidgetRecord(id, new ComponentName("pkg", "P" + id), 0,
            LauncherWidgetRecord.State.ACTIVE, cell, page, new Bundle(), null);
    }

    private static JSONObject encodedRecord(int id, int left, int top, int right, int bottom)
        throws Exception {
        return new JSONObject()
            .put("id", id)
            .put("provider", new ComponentName("pkg", "P" + id).flattenToString())
            .put("profile", 0)
            .put("state", LauncherWidgetRecord.State.ACTIVE.name())
            .put("cell", new JSONObject().put("left", left).put("top", top)
                .put("right", right).put("bottom", bottom))
            .put("options", new JSONObject());
    }

    private static JSONObject encodedPending(int id, int left, int top, int right, int bottom)
        throws Exception {
        return new JSONObject()
            .put("token", "legacy-token")
            .put("id", id)
            .put("provider", new ComponentName("pkg", "Pending").flattenToString())
            .put("profile", 0)
            .put("stage", WidgetAddTransaction.Stage.WAITING_FOR_CONFIGURATION.name())
            .put("cell", new JSONObject().put("left", left).put("top", top)
                .put("right", right).put("bottom", bottom))
            .put("gridRevision", 5)
            .put("options", new JSONObject())
            .put("started", 1234);
    }

    private static final class CountingStorage implements LauncherWidgetRepository.Storage {
        String value; boolean fail; int writes;
        CountingStorage(String value) { this.value = value; }
        @Override public String read() { return value; }
        @Override public boolean write(String next) {
            writes++;
            if (fail) return false;
            value = next;
            return true;
        }
    }
}
