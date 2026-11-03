package com.termux.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;

import com.termux.app.launcher.LauncherEntries;
import com.termux.app.launcher.data.LauncherConfigRepository;
import com.termux.app.launcher.data.TerminalEntryPin;
import com.termux.app.launcher.model.AppRef;
import com.termux.app.launcher.model.PinnedAppItem;
import com.termux.app.launcher.model.PinnedItem;
import com.termux.app.wall.WallRow;
import com.termux.shared.termux.settings.preferences.TermuxPreferenceConstants.TERMUX_APP;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** An install from before the wall rework lands in the new model once, and only once (ADR 0008). */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P)
public class WallReworkMigrationTest {

    private SharedPreferences store;

    @Before public void setUp() {
        store = RuntimeEnvironment.getApplication()
            .getSharedPreferences("wall_rework_migration_test", Context.MODE_PRIVATE);
        store.edit().clear().commit();
    }

    private String startInAfterMigrating(String usageMode) {
        store.edit().putString(TERMUX_APP.KEY_APP_LAUNCHER_USE_CASE_MODE, usageMode).commit();
        assertTrue(WallReworkMigration.migrateIfNeeded(store));
        assertFalse(store.contains(TERMUX_APP.KEY_APP_LAUNCHER_USE_CASE_MODE));
        return store.getString(TERMUX_APP.KEY_LAUNCHER_START_IN, null);
    }

    @Test public void theTerminalAloneStartsInTheWorkspace() {
        assertEquals(WallRow.START_IN_WORKSPACE,
            startInAfterMigrating(TERMUX_APP.APP_LAUNCHER_USE_CASE_MODE_TERMINAL));
    }

    @Test public void withHomeStartsOnHome() {
        assertEquals(WallRow.START_IN_HOME,
            startInAfterMigrating(TERMUX_APP.APP_LAUNCHER_USE_CASE_MODE_HOME));
    }

    @Test public void withDisplayStartsOnHome() {
        assertEquals(WallRow.START_IN_HOME,
            startInAfterMigrating(TERMUX_APP.APP_LAUNCHER_USE_CASE_MODE_DISPLAY));
    }

    @Test public void theOlderSwitchAndUnknownValuesStartOnHome() {
        assertEquals(WallRow.START_IN_HOME,
            startInAfterMigrating(TERMUX_APP.APP_LAUNCHER_USE_CASE_MODE_LAUNCHER));
        assertEquals(WallRow.START_IN_HOME, WallReworkMigration.startInFor("something"));
        assertEquals(WallRow.START_IN_HOME, WallReworkMigration.startInFor(null));
        assertEquals(WallRow.START_IN_WORKSPACE, WallReworkMigration.startInFor(" terminal "));
    }

    @Test public void aStartInAlreadyChosenStands() {
        store.edit().putString(TERMUX_APP.KEY_LAUNCHER_START_IN, WallRow.START_IN_HOME).commit();
        assertEquals(WallRow.START_IN_HOME,
            startInAfterMigrating(TERMUX_APP.APP_LAUNCHER_USE_CASE_MODE_TERMINAL));
    }

    @Test public void theRetiredKeysGo() {
        store.edit()
            .putString(TERMUX_APP.KEY_APP_LAUNCHER_USE_CASE_SNAPSHOT, "dock=1")
            .putBoolean(TERMUX_APP.KEY_X11_DISPLAY_ENABLED, true)
            .putString(TERMUX_APP.KEY_WALL_LAST_PAGE, "DISPLAY")
            .commit();
        WallReworkMigration.migrateIfNeeded(store);
        assertFalse(store.contains(TERMUX_APP.KEY_APP_LAUNCHER_USE_CASE_SNAPSHOT));
        assertFalse(store.contains(TERMUX_APP.KEY_X11_DISPLAY_ENABLED));
        assertFalse(store.contains(TERMUX_APP.KEY_WALL_LAST_PAGE));
    }

    @Test public void autostartUnderADisplaySwitchThatWasOffStaysOff() {
        store.edit()
            .putBoolean(TERMUX_APP.KEY_X11_DISPLAY_ENABLED, false)
            .putBoolean(TERMUX_APP.KEY_X11_DISPLAY_AUTOSTART, true)
            .commit();
        WallReworkMigration.migrateIfNeeded(store);
        assertFalse(store.getBoolean(TERMUX_APP.KEY_X11_DISPLAY_AUTOSTART, false));
    }

    @Test public void autostartWithNoDisplaySwitchStoredStaysOff() {
        store.edit().putBoolean(TERMUX_APP.KEY_X11_DISPLAY_AUTOSTART, true).commit();
        WallReworkMigration.migrateIfNeeded(store);
        assertFalse(store.getBoolean(TERMUX_APP.KEY_X11_DISPLAY_AUTOSTART, false));
    }

    @Test public void autostartUnderADisplaySwitchThatWasOnStands() {
        store.edit()
            .putBoolean(TERMUX_APP.KEY_X11_DISPLAY_ENABLED, true)
            .putBoolean(TERMUX_APP.KEY_X11_DISPLAY_AUTOSTART, true)
            .commit();
        WallReworkMigration.migrateIfNeeded(store);
        assertTrue(store.getBoolean(TERMUX_APP.KEY_X11_DISPLAY_AUTOSTART, false));
    }

    @Test public void leftoverPlaceKeysGoOnceThePlaceStoreHasFoldedThem() {
        store.edit()
            .putInt(WallReworkMigration.KEY_PLACE_MIGRATED, 8)
            .putBoolean("place.minimal", true)
            .putBoolean("place.terminal.keyboard_open", true)
            .putBoolean("place.display.keyboard_open", true)
            .putFloat("place.terminal.look.app_launcher_dock_opacity", 0.5f)
            .putString("place.home.portrait.apps_row", "bottom")
            .putBoolean("place.display.minimal", true)
            .putBoolean("place.home.keyboard_open", true)
            .putString("layout.portrait.apps_row", "top")
            .commit();
        WallReworkMigration.migrateIfNeeded(store);
        assertTrue(store.getBoolean("place.minimal", false));
        assertTrue(store.getBoolean("place.terminal.keyboard_open", false));
        assertTrue(store.getBoolean("place.display.keyboard_open", false));
        assertEquals(8, store.getInt(WallReworkMigration.KEY_PLACE_MIGRATED, 0));
        assertEquals("top", store.getString("layout.portrait.apps_row", null));
        assertFalse(store.contains("place.terminal.look.app_launcher_dock_opacity"));
        assertFalse(store.contains("place.home.portrait.apps_row"));
        assertFalse(store.contains("place.display.minimal"));
        assertFalse(store.contains("place.home.keyboard_open"));
    }

    @Test public void placeKeysThePlaceStoreHasNotFoldedYetAreLeftForIt() {
        store.edit()
            .putInt(WallReworkMigration.KEY_PLACE_MIGRATED, 5)
            .putFloat("place.terminal.look.app_launcher_dock_opacity", 0.5f)
            .putString("place.terminal.portrait.apps_row", "bottom")
            .commit();
        WallReworkMigration.migrateIfNeeded(store);
        assertTrue(store.contains("place.terminal.look.app_launcher_dock_opacity"));
        assertTrue(store.contains("place.terminal.portrait.apps_row"));
    }

    @Test public void aSecondRunChangesNothing() {
        store.edit().putString(TERMUX_APP.KEY_APP_LAUNCHER_USE_CASE_MODE,
            TERMUX_APP.APP_LAUNCHER_USE_CASE_MODE_TERMINAL).commit();
        assertTrue(WallReworkMigration.migrateIfNeeded(store));
        Map<String, ?> after = new HashMap<>(store.getAll());
        assertFalse(WallReworkMigration.migrateIfNeeded(store));
        assertEquals(after, store.getAll());

        // Even an old key that reappears (a restored backup) is not read again.
        store.edit().putString(TERMUX_APP.KEY_APP_LAUNCHER_USE_CASE_MODE,
            TERMUX_APP.APP_LAUNCHER_USE_CASE_MODE_HOME).commit();
        assertFalse(WallReworkMigration.migrateIfNeeded(store));
        assertEquals(WallRow.START_IN_WORKSPACE,
            store.getString(TERMUX_APP.KEY_LAUNCHER_START_IN, null));
    }

    @Test public void aFreshInstallOnlyRecordsTheVersion() {
        assertTrue(WallReworkMigration.migrateIfNeeded(store));
        assertEquals(1, store.getAll().size());
        assertEquals(WallReworkMigration.VERSION,
            store.getInt(TERMUX_APP.KEY_WALL_MIGRATION_VERSION, 0));
        assertFalse(store.contains(TERMUX_APP.KEY_LAUNCHER_START_IN));
    }

    @Test public void theMigrationLeavesTheTerminalEntryPinToItsOwnOnceFlag() {
        LauncherConfigRepository dock = new LauncherConfigRepository(new Dock());
        List<PinnedItem> items = new ArrayList<>();
        items.add(new PinnedAppItem(new AppRef("com.example.one", "A")));
        dock.savePinnedItems(items);

        WallReworkMigration.migrateIfNeeded(store);
        assertTrue(TerminalEntryPin.pinOnce(store, dock));
        assertEquals(1, countTerminalEntries(dock));

        // The user takes it off the dock; neither a later start nor another migration run puts
        // it back.
        items = dock.loadPinnedItems();
        items.remove(items.size() - 1);
        dock.savePinnedItems(items);
        WallReworkMigration.migrateIfNeeded(store);
        assertFalse(TerminalEntryPin.pinOnce(store, dock));
        assertEquals(0, countTerminalEntries(dock));
    }

    private static int countTerminalEntries(LauncherConfigRepository dock) {
        int count = 0;
        for (PinnedItem item : dock.loadPinnedItems()) {
            if (item instanceof PinnedAppItem && LauncherEntries.terminal().stableId()
                .equals(((PinnedAppItem) item).appRef.stableId())) count++;
        }
        return count;
    }

    private static final class Dock implements LauncherConfigRepository.PreferencesStore {
        String pinned = "";
        int schema;

        @Override public String getPinnedItemsV2() { return pinned; }
        @Override public int getPinnedItemsSchemaVersion() { return schema; }
        @Override public boolean commitPinnedItems(String value, int version) {
            pinned = value;
            schema = version;
            return true;
        }
        @Override public String getLegacyDefaultButtons() { return ""; }
    }
}
