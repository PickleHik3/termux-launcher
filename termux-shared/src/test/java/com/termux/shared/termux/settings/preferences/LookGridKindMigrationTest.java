package com.termux.shared.termux.settings.preferences;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.termux.shared.termux.settings.preferences.TermuxPreferenceConstants.TERMUX_APP;

import org.json.JSONObject;
import org.junit.Test;

public class LookGridKindMigrationTest {

    /** Pong's store on 2026-10-31: the 10-30 fold's keyboard cells, a Terminal cell, page 4. */
    private static LookGrid.MemoryStore pong() throws Exception {
        LookGrid.MemoryStore store = new LookGrid.MemoryStore();
        store.putString(TERMUX_APP.KEY_LOG_LEVEL, "1");
        store.putBoolean(LookGridMigration.KEY_MIGRATED, true);
        store.putInt("look.opacity.keyboard", 0);
        store.putInt("look.frost.keyboard", 0);
        store.putInt("look.grain.keyboard", 0);
        store.putInt("look.tint.keyboard", 0);
        store.putInt("look.opacity.terminal", 43);
        store.putInt("look.opacity.dock@4", 55);
        store.putInt("look.frost.dock@4", 3);
        store.putString(TERMUX_APP.KEY_SURFACE_CUSTOM_PRESET, new JSONObject()
            .put("format_version", 5)
            .put("look.opacity.keyboard_home", 0)
            .put("look.frost.keyboard_home", 0)
            .put("look.grain.keyboard_home", 0)
            .put("look.tint.keyboard_home", 0)
            .put("look.opacity.terminal", 43)
            .put("look.opacity.dock_home", 55)
            .toString());
        return store;
    }

    @Test
    public void pongsHomeKeyboardCellsMoveToTheHomeKind() throws Exception {
        LookGrid.MemoryStore store = pong();
        assertEquals(4, LookGridKindMigration.run(store));
        for (String property : new String[] {"opacity", "frost", "grain", "tint"}) {
            assertFalse(property, store.contains("look." + property + ".keyboard"));
            assertTrue(property, store.contains("look." + property + ".keyboard@kind:home"));
            assertEquals(property, 0, store.getInt("look." + property + ".keyboard@kind:home", -1));
        }
        // No _home twin: stays everywhere.
        assertEquals(43, store.getInt("look.opacity.terminal", -1));
        assertFalse(store.contains("look.opacity.terminal@kind:home"));
        // Page cells are never moved.
        assertEquals(55, store.getInt("look.opacity.dock@4", -1));
        assertEquals(3, store.getInt("look.frost.dock@4", -1));
        assertFalse(store.contains("look.opacity.dock@kind:home"));
        assertTrue(store.getBoolean(LookGridKindMigration.KEY_MIGRATED, false));
    }

    @Test
    public void theHomeKeyboardReadsZeroAndOtherKindsTheirShippedLook() throws Exception {
        LookGrid.MemoryStore store = pong();
        LookGridKindMigration.run(store);
        java.util.Map<Integer, LookGrid.Kind> kinds = new java.util.HashMap<>();
        kinds.put(4, LookGrid.Kind.HOME);
        kinds.put(6, LookGrid.Kind.DISPLAY);
        LookGrid grid = new LookGrid(store, kinds::get, null);
        LookGrid.Resolved home = grid.resolve(LookGrid.Property.OPACITY, LookGrid.Element.KEYBOARD, 4);
        assertEquals(0, home.value);
        assertEquals(LookGrid.Cell.ELEMENT_ON_KIND, home.cell);
        assertEquals(LookGrid.Cell.SHIPPED,
            grid.resolve(LookGrid.Property.OPACITY, LookGrid.Element.KEYBOARD, 6).cell);
    }

    @Test
    public void aValueChangedSinceStaysEverywhere() throws Exception {
        LookGrid.MemoryStore store = pong();
        store.putInt("look.opacity.keyboard", 35);
        assertEquals(3, LookGridKindMigration.run(store));
        assertEquals(35, store.getInt("look.opacity.keyboard", -1));
        assertFalse(store.contains("look.opacity.keyboard@kind:home"));
    }

    @Test
    public void itRunsOnce() throws Exception {
        LookGrid.MemoryStore store = pong();
        LookGridKindMigration.run(store);
        store.putInt("look.opacity.keyboard", 0);
        assertEquals(0, LookGridKindMigration.run(store));
        assertTrue(store.contains("look.opacity.keyboard"));
    }

    @Test
    public void noCustomLookMovesNothing() throws Exception {
        LookGrid.MemoryStore store = pong();
        store.remove(TERMUX_APP.KEY_SURFACE_CUSTOM_PRESET);
        assertEquals(0, LookGridKindMigration.run(store));
        assertEquals(0, store.getInt("look.opacity.keyboard", -1));
        assertTrue(store.getBoolean(LookGridKindMigration.KEY_MIGRATED, false));
    }

    @Test
    public void aFreshInstallStaysFresh() {
        LookGrid.MemoryStore store = new LookGrid.MemoryStore();
        store.putString(TERMUX_APP.KEY_LOG_LEVEL, "1");
        assertEquals(0, LookGridKindMigration.run(store));
        assertEquals(1, store.keys().size());
    }

    @Test
    public void anUnreadableCustomLookMovesNothing() throws Exception {
        LookGrid.MemoryStore store = pong();
        store.putString(TERMUX_APP.KEY_SURFACE_CUSTOM_PRESET, "{not json");
        assertEquals(0, LookGridKindMigration.run(store));
        assertEquals(0, store.getInt("look.opacity.keyboard", -1));
    }
}
