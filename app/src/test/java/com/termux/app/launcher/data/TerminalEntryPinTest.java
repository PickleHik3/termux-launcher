package com.termux.app.launcher.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;

import com.termux.app.launcher.LauncherEntries;
import com.termux.app.launcher.model.AppRef;
import com.termux.app.launcher.model.PinnedAppItem;
import com.termux.app.launcher.model.PinnedItem;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.util.ArrayList;
import java.util.List;

/** The Terminal entry is pinned to the dock once, at the end, and never again (ADR 0008). */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P)
public class TerminalEntryPinTest {

    private static final class Store implements LauncherConfigRepository.PreferencesStore {
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

    private SharedPreferences flags;
    private LauncherConfigRepository dock;

    @Before public void setUp() {
        flags = RuntimeEnvironment.getApplication()
            .getSharedPreferences("terminal_entry_pin_test", Context.MODE_PRIVATE);
        flags.edit().clear().commit();
        dock = new LauncherConfigRepository(new Store());
        List<PinnedItem> items = new ArrayList<>();
        items.add(new PinnedAppItem(new AppRef("com.example.one", "A")));
        dock.savePinnedItems(items);
    }

    private List<String> dockIds() {
        List<String> ids = new ArrayList<>();
        for (PinnedItem item : dock.loadPinnedItems()) {
            if (item instanceof PinnedAppItem) ids.add(((PinnedAppItem) item).appRef.stableId());
        }
        return ids;
    }

    @Test public void theFirstRunPinsTheTerminalEntryAtTheEndOfTheDock() {
        assertTrue(TerminalEntryPin.pinOnce(flags, dock));
        List<String> ids = dockIds();
        assertEquals(2, ids.size());
        assertEquals("com.example.one/A", ids.get(0));
        assertEquals(LauncherEntries.terminal().stableId(), ids.get(1));
    }

    @Test public void itPinsOnlyOnce() {
        assertTrue(TerminalEntryPin.pinOnce(flags, dock));
        assertFalse(TerminalEntryPin.pinOnce(flags, dock));
        assertEquals(2, dockIds().size());
    }

    @Test public void anEntryTakenOffTheDockStaysOff() {
        TerminalEntryPin.pinOnce(flags, dock);
        List<PinnedItem> items = dock.loadPinnedItems();
        items.remove(items.size() - 1);
        dock.savePinnedItems(items);

        assertFalse(TerminalEntryPin.pinOnce(flags, dock));
        assertFalse(dockIds().contains(LauncherEntries.terminal().stableId()));
    }

    @Test public void anEntryAlreadyPinnedIsNotPinnedTwice() {
        List<PinnedItem> items = dock.loadPinnedItems();
        items.add(0, new PinnedAppItem(LauncherEntries.terminal()));
        dock.savePinnedItems(items);

        assertFalse(TerminalEntryPin.pinOnce(flags, dock));
        assertEquals(2, dockIds().size());
        assertEquals(LauncherEntries.terminal().stableId(), dockIds().get(0));
        assertTrue("the offer is spent either way",
            flags.getBoolean(TerminalEntryPin.KEY_PINNED_ONCE, false));
    }
}
