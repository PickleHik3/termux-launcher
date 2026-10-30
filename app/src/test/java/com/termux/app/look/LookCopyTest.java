package com.termux.app.look;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import com.termux.shared.termux.settings.preferences.TermuxPreferenceConstants.TERMUX_APP;

import org.junit.Test;

public class LookCopyTest {

    @Test
    public void copiesEveryOverrideOfOnePageToAnotherAndLeavesTheRest() {
        MemoryLookStore store = new MemoryLookStore();
        String intensity = TERMUX_APP.KEY_SURFACE_MATERIAL_INTENSITY;
        String material = TERMUX_APP.KEY_SURFACE_MATERIAL;
        String dim = TERMUX_APP.KEY_WALLPAPER_BACKDROP_DIM;
        store.putInt(intensity, 10);
        store.putInt(intensity + "@3", 42);
        store.putString(material + "@3", "frost");
        store.putInt(dim + "@5", 9);
        store.putInt(dim + "@4", 1);

        int n = PageLook.copyOverrides(store, 3, 4);

        assertEquals(2, n);
        assertEquals(42, store.getInt(intensity + "@4", -1));
        assertEquals("frost", store.getString(material + "@4", ""));
        // The target's own value for a key the source lacks stays; the source and others stay.
        assertEquals(1, store.getInt(dim + "@4", -1));
        assertEquals(42, store.getInt(intensity + "@3", -1));
        assertEquals(9, store.getInt(dim + "@5", -1));
        assertEquals(10, store.getInt(intensity, -1));
    }

    @Test
    public void overwritesTheTargetsValueAndIgnoresNothingToCopyOrTheSamePage() {
        MemoryLookStore store = new MemoryLookStore();
        String intensity = TERMUX_APP.KEY_SURFACE_MATERIAL_INTENSITY;
        store.putInt(intensity + "@3", 42);
        store.putInt(intensity + "@4", 1);
        assertEquals(1, PageLook.copyOverrides(store, 3, 4));
        assertEquals(42, store.getInt(intensity + "@4", -1));
        assertEquals(0, PageLook.copyOverrides(store, 7, 8));
        assertFalse(store.has(intensity + "@8"));
        assertEquals(0, PageLook.copyOverrides(store, 3, 3));
        assertEquals(0, PageLook.copyOverrides(store, -1, 4));
    }
}
