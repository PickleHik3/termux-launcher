package com.termux.app.launcher.widget;

import org.json.JSONArray;
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
public class LauncherWidgetRepositoryMigrationTest {
    @Test public void emptyStateUsesSixByFourV2Default() throws Exception {
        WidgetTestFixtures.Memory storage = new WidgetTestFixtures.Memory();
        LauncherWidgetRepository repository = new LauncherWidgetRepository(storage);
        assertEquals(WidgetGridDefinition.DEFAULT, repository.gridDefinition());
        assertEquals(3, new JSONObject(repository.serialize()).getInt("version"));
    }

    @Test public void v1RecordsMigrateStableRowMajorAndExpandWithoutDroppingIds() throws Exception {
        JSONObject v1 = new JSONObject().put("version", 1);
        JSONArray records = new JSONArray();
        for (int id = 1; id <= 26; id++) records.put(legacyRecord(id));
        v1.put("records", records);
        WidgetTestFixtures.Memory storage = new WidgetTestFixtures.Memory(); storage.value = v1.toString();
        LauncherWidgetRepository repository = new LauncherWidgetRepository(storage);
        assertEquals(26, repository.records().size()); assertEquals(7, repository.gridDefinition().rows);
        assertEquals(new WidgetCellRect(0, 0, 1, 1), repository.get(1).cell);
        assertEquals(new WidgetCellRect(1, 6, 2, 7), repository.get(26).cell);
        assertEquals(3, new JSONObject(storage.value).getInt("version"));
    }

    @Test public void pendingStageTokenAndTimeSurviveOneAtomicMigrationWrite() throws Exception {
        JSONObject v1 = new JSONObject().put("version", 1)
            .put("records", new JSONArray().put(legacyRecord(1)))
            .put("pending", legacyPending(20));
        CountingStorage storage = new CountingStorage(v1.toString());
        LauncherWidgetRepository repository = new LauncherWidgetRepository(storage);
        assertEquals(1, storage.writes); assertEquals("legacy-token", repository.pending().token);
        assertEquals(WidgetAddTransaction.Stage.WAITING_FOR_CONFIGURATION,
            repository.pending().stage); assertEquals(1234, repository.pending().startedAtMillis);
        assertEquals(new WidgetCellRect(1, 0, 2, 1), repository.pending().cell);
    }

    @Test public void failedMigrationWriteRetainsIdentitiesAndRetriesNextConstruction() throws Exception {
        JSONObject v1 = new JSONObject().put("version", 1)
            .put("records", new JSONArray().put(legacyRecord(7)));
        CountingStorage storage = new CountingStorage(v1.toString()); storage.fail = true;
        LauncherWidgetRepository first = new LauncherWidgetRepository(storage);
        assertNotNull(first.get(7)); assertEquals(1, storage.writes);
        storage.fail = false;
        LauncherWidgetRepository retry = new LauncherWidgetRepository(storage);
        assertNotNull(retry.get(7)); assertEquals(2, storage.writes);
        assertFalse(storage.value.contains("deleteHost"));
    }

    private static JSONObject legacyRecord(int id) throws Exception {
        return new JSONObject().put("id", id).put("provider", "pkg/P" + id)
            .put("profile", 0).put("state", "ACTIVE").put("options", new JSONObject());
    }
    private static JSONObject legacyPending(int id) throws Exception {
        return new JSONObject().put("token", "legacy-token").put("id", id)
            .put("provider", "pkg/Pending").put("profile", 0)
            .put("stage", "WAITING_FOR_CONFIGURATION").put("options", new JSONObject())
            .put("started", 1234);
    }
    private static final class CountingStorage implements LauncherWidgetRepository.Storage {
        String value; int writes; boolean fail;
        CountingStorage(String value) { this.value = value; }
        @Override public String read() { return value; }
        @Override public boolean write(String value) {
            writes++; if (fail) return false; this.value = value; return true;
        }
    }
}
