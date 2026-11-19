package com.termux.app.look;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.termux.app.page.PageKind;
import com.termux.app.wall.WallPages.Page;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences.SurfaceProperty;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences.SurfaceSlot;
import com.termux.shared.termux.settings.preferences.TermuxPreferenceConstants.TERMUX_APP;

import org.junit.Before;
import org.junit.Test;

public class HalfLookUpgradeTest {

    private static final int TERMINAL_ID = 2;
    private static final int DISPLAY_ID = 5;
    private static final String GRAIN = TERMUX_APP.KEY_DOCK_GLASS_GRAIN;

    private MemoryLookStore store;

    @Before
    public void setUp() {
        store = new MemoryLookStore();
    }

    private void detachDockGrainOnBothHalves() {
        String flag = ("surface_inherit_" + SurfaceSlot.DOCK.key + "_" + SurfaceProperty.GRAIN.key);
        store.putBoolean(flag + "_home", false);
        store.putBoolean(flag + "_workspace", false);
    }

    private boolean upgrade() {
        return HalfLookUpgrade.run(store, TERMINAL_ID, -1);
    }

    private PageLook.Snapshot snapshot(int id, PageKind kind) {
        // The half upgrade writes the old keys; start-up folds them into the grid next.
        MemoryLookStore folded = store.copy();
        fold(folded);
        PageLook look = new PageLook(folded);
        try {
            return look.of(new Page(id, kind, null, null));
        } finally {
            look.close();
        }
    }

    @Test
    public void equalHalvesBecomeTheWallValue() {
        detachDockGrainOnBothHalves();
        store.putInt(GRAIN + "_home", 20);
        store.putInt(GRAIN + "_workspace", 20);
        assertTrue(upgrade());
        assertEquals(20, store.getInt(GRAIN, -1));
        assertFalse(store.has(LookKeys.pageKey(GRAIN, TERMINAL_ID)));
        assertEquals(20, snapshot(1, PageKind.HOME).dockGlassGrain());
        assertEquals(20, snapshot(TERMINAL_ID, PageKind.TERMINAL).dockGlassGrain());
    }

    @Test
    public void aSingleHalfKeyReadsTheOtherHalfAsTheWallValue() {
        detachDockGrainOnBothHalves();
        store.putInt(GRAIN, 30);
        store.putInt(GRAIN + "_workspace", 60);
        assertTrue(upgrade());
        assertEquals(30, store.getInt(GRAIN, -1));
        assertEquals(60, store.getInt(LookKeys.pageKey(GRAIN, TERMINAL_ID), -1));
    }

    @Test
    public void absentHalfKeysWriteNothingButTheVersion() {
        store.putInt(GRAIN, 30);
        assertTrue(HalfLookUpgrade.plan(store, TERMINAL_ID, DISPLAY_ID).isEmpty());
        assertTrue(upgrade());
        assertEquals(HalfLookUpgrade.VERSION, store.getInt(HalfLookUpgrade.KEY_VERSION, 0));
        assertEquals(30, store.getInt(GRAIN, -1));
        assertEquals(2, store.keys().size());
    }

    @Test
    public void canvasKeysStayWallValues() {
        store.putBoolean(("surface_inherit_" + SurfaceSlot.CANVAS.key + "_" + SurfaceProperty.TINT.key), false);
        store.putInt(TERMUX_APP.KEY_TERMINAL_TINT_STRENGTH, 60);
        store.putInt(GRAIN + "_home", 10);
        store.putInt(GRAIN + "_workspace", 50);
        assertTrue(upgrade());
        assertEquals(60, store.getInt(TERMUX_APP.KEY_TERMINAL_TINT_STRENGTH, -1));
        assertFalse(store.has(LookKeys.pageKey(TERMUX_APP.KEY_TERMINAL_TINT_STRENGTH,
            TERMINAL_ID)));
        assertEquals(60, snapshot(TERMINAL_ID, PageKind.TERMINAL)
            .value(SurfaceSlot.CANVAS, SurfaceProperty.TINT));
    }

    @Test
    public void equalMaterialsMakeNoOverride() {
        store.putString(TERMUX_APP.KEY_SURFACE_MATERIAL + "_home", "stone");
        store.putString(TERMUX_APP.KEY_SURFACE_MATERIAL + "_workspace", "stone");
        assertTrue(upgrade());
        assertEquals("stone", store.getString(TERMUX_APP.KEY_SURFACE_MATERIAL, ""));
        assertFalse(store.has(LookKeys.pageKey(TERMUX_APP.KEY_SURFACE_MATERIAL, TERMINAL_ID)));
    }

    @Test
    public void halfKeysAreNotDeleted() {
        detachDockGrainOnBothHalves();
        store.putInt(GRAIN + "_home", 10);
        store.putInt(GRAIN + "_workspace", 50);
        upgrade();
        assertEquals(10, store.getInt(GRAIN + "_home", -1));
        assertEquals(50, store.getInt(GRAIN + "_workspace", -1));
    }

    @Test
    public void runningTwiceChangesNothingTheSecondTime() {
        detachDockGrainOnBothHalves();
        store.putInt(GRAIN + "_home", 10);
        store.putInt(GRAIN + "_workspace", 50);
        assertTrue(upgrade());
        MemoryLookStore snapshot = store.copy();
        // A later wall edit must survive a second call.
        store.putInt(GRAIN, 99);
        assertFalse(upgrade());
        assertEquals(99, store.getInt(GRAIN, -1));
        assertEquals(snapshot.getInt(LookKeys.pageKey(GRAIN, TERMINAL_ID), -1),
            store.getInt(LookKeys.pageKey(GRAIN, TERMINAL_ID), -1));
    }

    @Test
    public void planIsPureAndRepeatable() {
        detachDockGrainOnBothHalves();
        store.putInt(GRAIN + "_home", 10);
        store.putInt(GRAIN + "_workspace", 50);
        int keysBefore = store.keys().size();
        String first = HalfLookUpgrade.plan(store, TERMINAL_ID, DISPLAY_ID).toString();
        String second = HalfLookUpgrade.plan(store, TERMINAL_ID, DISPLAY_ID).toString();
        assertEquals(first, second);
        assertEquals(keysBefore, store.keys().size());
        assertFalse(HalfLookUpgrade.done(store));
    }

    @Test
    public void applyingThePlanAgainReachesTheSameState() {
        detachDockGrainOnBothHalves();
        store.putInt(GRAIN + "_home", 10);
        store.putInt(GRAIN + "_workspace", 50);
        upgrade();
        MemoryLookStore once = store.copy();
        store.remove(HalfLookUpgrade.KEY_VERSION);
        upgrade();
        assertEquals(once.keys(), store.keys());
        for (String key : once.keys())
            if (!key.contains("inherit"))
                assertEquals(key, once.getInt(key, -7), store.getInt(key, -7));
    }

    /** The grid upgrade start-up runs next, over the pages the wall has. */
    private void fold(MemoryLookStore store) {
        store.putBoolean(com.termux.shared.termux.settings.preferences.TermuxPreferenceConstants
            .TERMUX_APP.KEY_SURFACE_INHERITANCE_MIGRATED, true);
        store.putBoolean(com.termux.shared.termux.settings.preferences.TermuxPreferenceConstants
            .TERMUX_APP.KEY_KEYBOARD_GLASS_SLOT_FOLDED, true);
        store.putBoolean(com.termux.shared.termux.settings.preferences.TermuxPreferenceConstants
            .TERMUX_APP.KEY_KEYBOARD_OPACITY_SENTINEL_HEALED, true);
        com.termux.shared.termux.settings.preferences.LookGridMigration.run(
            LookKeys.gridStore(store), java.util.Arrays.asList(TERMINAL_ID, DISPLAY_ID, 1, 3));
    }

    private static final int HOME_ID = 1;

}
