package com.termux.app.look;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import com.termux.shared.termux.settings.preferences.TermuxPreferenceConstants.TERMUX_APP;

import org.junit.Test;

public class LookCopyTest {

    @Test
    public void copiesThePagesDimAloneAndLeavesTheRest() {
        MemoryLookStore store = new MemoryLookStore();
        String intensity = TERMUX_APP.KEY_SURFACE_MATERIAL_INTENSITY;
        String dim = TERMUX_APP.KEY_WALLPAPER_BACKDROP_DIM;
        store.putInt(intensity + "@3", 42);
        store.putInt("look.opacity.dock@3", 12);
        store.putInt(dim + "@3", 30);
        store.putInt(dim + "@5", 9);

        assertEquals(1, PageLook.copyOverrides(store, 3, 4));

        assertEquals(30, store.getInt(dim + "@4", -1));
        // An older build's page looks are no page's any more (ADR 0031): not carried.
        assertFalse(store.has(intensity + "@4"));
        assertFalse(store.has("look.opacity.dock@4"));
        assertEquals(9, store.getInt(dim + "@5", -1));
    }

    @Test
    public void overwritesTheTargetsDimAndIgnoresNothingToCopyOrTheSamePage() {
        MemoryLookStore store = new MemoryLookStore();
        String dim = TERMUX_APP.KEY_WALLPAPER_BACKDROP_DIM;
        store.putInt(dim + "@3", 42);
        store.putInt(dim + "@4", 1);
        assertEquals(1, PageLook.copyOverrides(store, 3, 4));
        assertEquals(42, store.getInt(dim + "@4", -1));
        assertEquals(0, PageLook.copyOverrides(store, 7, 8));
        assertFalse(store.has(dim + "@8"));
        assertEquals(0, PageLook.copyOverrides(store, 3, 3));
        assertEquals(0, PageLook.copyOverrides(store, -1, 4));
    }

    @Test
    public void carriesTheWallpaperDimToANewPage() {
        MemoryLookStore store = new MemoryLookStore();
        String dim = TERMUX_APP.KEY_WALLPAPER_BACKDROP_DIM;
        store.putInt(dim + "@3", 61);
        assertEquals(1, PageLook.copyOverrides(store, 3, 9));
        assertEquals(61, store.getInt(dim + "@9", -1));
    }
}
