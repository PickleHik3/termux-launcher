package com.termux.shared.termux.settings.preferences;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.termux.shared.termux.settings.preferences.LookGrid.Element;
import com.termux.shared.termux.settings.preferences.LookGrid.Kind;
import com.termux.shared.termux.settings.preferences.LookGrid.Property;
import com.termux.shared.termux.settings.preferences.TermuxPreferenceConstants.TERMUX_APP;

import org.json.JSONObject;
import org.junit.Test;

public class LookGridKindSeedTest {

    /** The keyboard's kind looks as the app ships them: Mist on Home, Clear on Display. */
    private static final LookGrid.ShippedLooks SHIPPED = (property, element, kind) -> {
        if (element != Element.KEYBOARD || kind == null || kind == Kind.TERMINAL) return null;
        boolean home = kind == Kind.HOME;
        switch (property) {
            case FROST: return home ? FrostStops.MIST : FrostStops.CLEAR;
            case OPACITY: return home ? 60 : 10;
            case TINT: return 0;
            default: return null;
        }
    };

    private static void assertKind(LookGrid.MemoryStore store, Kind kind, int frost, int opacity,
                                   int tint) {
        assertEquals(kind + " frost", frost,
            store.getInt(LookGrid.key(Property.FROST, Element.KEYBOARD, kind), -1));
        assertEquals(kind + " opacity", opacity,
            store.getInt(LookGrid.key(Property.OPACITY, Element.KEYBOARD, kind), -1));
        assertEquals(kind + " tint", tint,
            store.getInt(LookGrid.key(Property.TINT, Element.KEYBOARD, kind), -1));
        assertFalse(kind + " grain follows the stop",
            store.contains(LookGrid.key(Property.GRAIN, Element.KEYBOARD, kind)));
    }

    @Test
    public void aFreshStoreGetsBothKinds() {
        LookGrid.MemoryStore store = new LookGrid.MemoryStore();
        store.putString(TERMUX_APP.KEY_LOG_LEVEL, "1");
        assertEquals(6, LookGridKindSeed.run(store, SHIPPED));
        assertKind(store, Kind.HOME, 5, 60, 0);
        assertKind(store, Kind.DISPLAY, 2, 10, 0);
        assertFalse(store.contains("look.opacity.keyboard@kind:terminal"));
        assertFalse(store.contains("look.opacity.dock@kind:home"));
        assertTrue(store.getBoolean(LookGridKindSeed.KEY_SEEDED, false));
    }

    @Test
    public void pongKeepsItsHomeCellsAndGetsClearOnDisplay() throws Exception {
        LookGrid.MemoryStore store = new LookGrid.MemoryStore();
        store.putString(TERMUX_APP.KEY_LOG_LEVEL, "1");
        store.putBoolean(LookGridMigration.KEY_MIGRATED, true);
        for (String property : new String[] {"opacity", "frost", "grain", "tint"})
            store.putInt("look." + property + ".keyboard", 0);
        store.putInt("look.opacity.terminal", 43);
        store.putInt("look.opacity.dock@4", 55);
        store.putString(TERMUX_APP.KEY_SURFACE_CUSTOM_PRESET, new JSONObject()
            .put("look.opacity.keyboard_home", 0).put("look.frost.keyboard_home", 0)
            .put("look.grain.keyboard_home", 0).put("look.tint.keyboard_home", 0).toString());
        LookGridKindMigration.run(store);

        assertEquals(3, LookGridKindSeed.run(store, SHIPPED));
        assertKind(store, Kind.DISPLAY, 2, 10, 0);
        assertEquals(0, store.getInt("look.frost.keyboard@kind:home", -1));
        assertEquals(0, store.getInt("look.opacity.keyboard@kind:home", -1));
        assertEquals(0, store.getInt("look.tint.keyboard@kind:home", -1));
        assertEquals(0, store.getInt("look.grain.keyboard@kind:home", -1));
        assertEquals(43, store.getInt("look.opacity.terminal", -1));
        assertEquals(55, store.getInt("look.opacity.dock@4", -1));
    }

    @Test
    public void aCellTheUserHoldsIsNeverOverwritten() {
        LookGrid.MemoryStore store = new LookGrid.MemoryStore();
        store.putInt("look.opacity.keyboard@kind:display", 33);
        assertEquals(5, LookGridKindSeed.run(store, SHIPPED));
        assertEquals(33, store.getInt("look.opacity.keyboard@kind:display", -1));
    }

    @Test
    public void runningTwiceWritesNothingMore() {
        LookGrid.MemoryStore store = new LookGrid.MemoryStore();
        LookGridKindSeed.run(store, SHIPPED);
        java.util.Set<String> keys = store.keys();
        store.remove("look.opacity.keyboard@kind:home");
        assertEquals(0, LookGridKindSeed.run(store, SHIPPED));
        assertFalse(store.contains("look.opacity.keyboard@kind:home"));
        assertEquals(keys.size() - 1, store.keys().size());
    }
}
