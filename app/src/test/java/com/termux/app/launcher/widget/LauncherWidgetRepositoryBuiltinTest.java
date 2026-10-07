package com.termux.app.launcher.widget;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.os.Build;
import android.os.Bundle;

import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/** Built-in widgets in the durable store: negative keys, a {@code builtin} field, version 5. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.S, application = android.app.Application.class)
public class LauncherWidgetRepositoryBuiltinTest {
    @Test public void builtinIdsAreAllocatedBelowEveryExistingKey() {
        LauncherWidgetRepository repository = WidgetTestFixtures.repository();
        assertEquals(-1, repository.allocateBuiltinId());
        assertTrue(repository.putRecord(LauncherWidgetRecord.builtin(-1, "clock.analog",
            new WidgetCellRect(0, 0, 1, 1), 0, null)));
        assertEquals(-2, repository.allocateBuiltinId());
        assertTrue(repository.putRecord(LauncherWidgetRecord.builtin(-5, "weather",
            new WidgetCellRect(1, 0, 3, 1), 0, null)));
        assertEquals(-6, repository.allocateBuiltinId());
    }

    @Test public void aWallWithABuiltinIsWrittenAsVersionFiveAndReadBack() throws Exception {
        WidgetTestFixtures.Memory memory = new WidgetTestFixtures.Memory();
        LauncherWidgetRepository repository = new LauncherWidgetRepository(memory);
        Bundle config = new Bundle();
        config.putString("title", "uptime");
        assertTrue(repository.putRecord(LauncherWidgetRecord.builtin(-1, "battery",
            new WidgetCellRect(0, 1, 2, 2), 0, config)));
        assertEquals(5, new JSONObject(memory.value).getInt("version"));

        LauncherWidgetRepository reloaded = new LauncherWidgetRepository(memory);
        LauncherWidgetRecord record = reloaded.get(-1);
        assertNotNull(record);
        assertTrue(record.isBuiltin());
        assertEquals("battery", record.builtinKind);
        assertEquals(LauncherWidgetRecord.builtinProvider("battery"), record.provider);
        assertEquals(new WidgetCellRect(0, 1, 2, 2), record.cell);
        assertEquals("uptime", record.sizeOptions().getString("title"));
        assertEquals(1, reloaded.records().size());
    }

    @Test public void aWallWithoutBuiltinsStaysVersionFour() throws Exception {
        WidgetTestFixtures.Memory memory = new WidgetTestFixtures.Memory();
        LauncherWidgetRepository repository = new LauncherWidgetRepository(memory);
        assertTrue(repository.putRecord(new LauncherWidgetRecord(7, WidgetTestFixtures.PROVIDER, 0,
            LauncherWidgetRecord.State.ACTIVE, new WidgetCellRect(0, 0, 1, 1), null, null)));
        assertEquals(4, new JSONObject(memory.value).getInt("version"));
    }

    @Test public void removingABuiltinNeedsNoDeletionHandshake() {
        LauncherWidgetRepository repository = WidgetTestFixtures.repository();
        assertTrue(repository.putRecord(LauncherWidgetRecord.builtin(-1, "battery",
            new WidgetCellRect(0, 0, 1, 1), 0, null)));
        assertTrue(repository.removeRecord(-1));
        assertNull(repository.get(-1));
        assertEquals(-1, repository.allocateBuiltinId());
    }

    @Test public void builtinsAndAppWidgetsCollideOnCellsLikeAnyTwoWidgets() {
        LauncherWidgetRepository repository = WidgetTestFixtures.repository();
        assertTrue(repository.putRecord(LauncherWidgetRecord.builtin(-1, "notes",
            new WidgetCellRect(0, 0, 2, 2), 0, null)));
        assertFalse(repository.canReserve(repository.revision(), new WidgetCellRect(1, 1, 2, 2), 0));
        assertTrue(repository.canReserve(repository.revision(), new WidgetCellRect(2, 0, 3, 1), 0));
    }

    @Test(expected = IllegalArgumentException.class)
    public void aBuiltinRecordRefusesAPositiveKey() {
        LauncherWidgetRecord.builtin(3, "notes", new WidgetCellRect(0, 0, 1, 1), 0, null);
    }
}
