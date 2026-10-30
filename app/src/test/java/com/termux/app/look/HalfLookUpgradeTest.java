package com.termux.app.look;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.termux.app.wall.WallPages.Page;
import com.termux.app.wall.WallPages.PageKind;
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
        String flag = LookKeys.inheritKey(SurfaceSlot.DOCK, SurfaceProperty.GRAIN);
        store.putBoolean(flag + "_home", false);
        store.putBoolean(flag + "_workspace", false);
    }

    private boolean upgrade() {
        return HalfLookUpgrade.run(store, TERMINAL_ID, -1);
    }

    private PageLook.Snapshot snapshot(int id, PageKind kind) {
        PageLook look = new PageLook(store);
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
    public void differingHalvesGiveHomeTheWallAndTheTerminalPageItsOverride() {
        detachDockGrainOnBothHalves();
        store.putInt(GRAIN + "_home", 10);
        store.putInt(GRAIN + "_workspace", 50);
        assertTrue(upgrade());
        assertEquals(10, store.getInt(GRAIN, -1));
        assertEquals(50, store.getInt(LookKeys.pageKey(GRAIN, TERMINAL_ID), -1));
        assertEquals(10, snapshot(1, PageKind.HOME).dockGlassGrain());
        assertEquals(50, snapshot(TERMINAL_ID, PageKind.TERMINAL).dockGlassGrain());
    }

    @Test
    public void aDisplayPageGetsTheWorkspacesValueToo() {
        detachDockGrainOnBothHalves();
        store.putInt(GRAIN + "_home", 10);
        store.putInt(GRAIN + "_workspace", 50);
        assertTrue(HalfLookUpgrade.run(store, TERMINAL_ID, DISPLAY_ID));
        assertEquals(50, store.getInt(LookKeys.pageKey(GRAIN, DISPLAY_ID), -1));
        assertEquals(50, snapshot(DISPLAY_ID, PageKind.DISPLAY).dockGlassGrain());
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
        store.putBoolean(LookKeys.inheritKey(SurfaceSlot.CANVAS, SurfaceProperty.TINT), false);
        store.putInt(TERMUX_APP.KEY_TERMINAL_TINT_STRENGTH, 60);
        store.putInt(GRAIN + "_home", 10);
        store.putInt(GRAIN + "_workspace", 50);
        assertTrue(upgrade());
        assertEquals(60, store.getInt(TERMUX_APP.KEY_TERMINAL_TINT_STRENGTH, -1));
        assertFalse(store.has(LookKeys.pageKey(TERMUX_APP.KEY_TERMINAL_TINT_STRENGTH,
            TERMINAL_ID)));
        assertEquals(60, snapshot(TERMINAL_ID, PageKind.TERMINAL).canvasTint());
    }

    @Test
    public void aWorkspaceThatFollowedItsOwnBaseKeepsItsLook() {
        // Home detached dock blur at 30; the Workspace follows a Base of 12 that Home does not use.
        String flag = LookKeys.inheritKey(SurfaceSlot.DOCK, SurfaceProperty.BLUR);
        String blur = LookKeys.surfaceKey(SurfaceSlot.DOCK, SurfaceProperty.BLUR);
        store.putBoolean(flag + "_home", false);
        store.putBoolean(flag + "_workspace", true);
        store.putInt(blur + "_home", 30);
        store.putInt(TERMUX_APP.KEY_SURFACE_BASE_BLUR + "_home", 4);
        store.putInt(TERMUX_APP.KEY_SURFACE_BASE_BLUR + "_workspace", 12);
        assertTrue(upgrade());
        assertEquals(30, snapshot(1, PageKind.HOME).extraKeysBlurRadius());
        assertEquals(12, snapshot(TERMINAL_ID, PageKind.TERMINAL).extraKeysBlurRadius());
        // The status bar follows Base on both: Home shows 4, the Workspace 12.
        assertEquals(4, snapshot(1, PageKind.HOME).statusBarBlurRadius());
        assertEquals(12, snapshot(TERMINAL_ID, PageKind.TERMINAL).statusBarBlurRadius());
    }

    @Test
    public void keyboardBackdropGoesToThePagesWhenItDiffers() {
        String key = TERMUX_APP.KEY_IN_APP_KEYBOARD_BACKDROP_OPACITY;
        store.putInt(key + "_home", 80);
        store.putInt(key + "_workspace", 40);
        assertTrue(upgrade());
        assertEquals(80, store.getInt(key, -1));
        assertEquals(40, snapshot(TERMINAL_ID, PageKind.TERMINAL).inAppKeyboardBackdropOpacity());
        assertEquals(80, snapshot(1, PageKind.HOME).inAppKeyboardBackdropOpacity());
    }

    @Test
    public void aDifferingMaterialGoesToTheTerminalPage() {
        store.putString(TERMUX_APP.KEY_SURFACE_MATERIAL + "_home", "stone");
        store.putString(TERMUX_APP.KEY_SURFACE_MATERIAL + "_workspace", "other");
        store.putInt(TERMUX_APP.KEY_SURFACE_MATERIAL_INTENSITY + "_home", 70);
        assertTrue(upgrade());
        assertEquals("stone", store.getString(TERMUX_APP.KEY_SURFACE_MATERIAL, ""));
        assertEquals("other", store.getString(
            LookKeys.pageKey(TERMUX_APP.KEY_SURFACE_MATERIAL, TERMINAL_ID), ""));
        assertEquals("other", snapshot(TERMINAL_ID, PageKind.TERMINAL).surfaceMaterial());
        assertEquals("stone", snapshot(1, PageKind.HOME).surfaceMaterial());
        assertEquals(70, snapshot(1, PageKind.HOME).surfaceMaterialIntensity());
        // The Workspace's intensity was the default, which differs from Home's 70.
        assertEquals(TERMUX_APP.DEFAULT_SURFACE_MATERIAL_INTENSITY,
            snapshot(TERMINAL_ID, PageKind.TERMINAL).surfaceMaterialIntensity());
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
}
