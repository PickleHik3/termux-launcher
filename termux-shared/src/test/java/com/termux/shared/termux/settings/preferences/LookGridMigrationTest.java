package com.termux.shared.termux.settings.preferences;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.termux.shared.termux.settings.preferences.LookGrid.Element;
import com.termux.shared.termux.settings.preferences.LookGrid.Property;
import com.termux.shared.termux.settings.preferences.TermuxPreferenceConstants.TERMUX_APP;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class LookGridMigrationTest {

    private static final List<Integer> PAGES = Arrays.asList(0, 3, 5);
    private static final int NO_PAGE = LookGrid.NO_PAGE;

    /** An install every earlier upgrade has brought along. */
    private static LookGrid.MemoryStore store() {
        LookGrid.MemoryStore store = bare();
        store.putBoolean(TERMUX_APP.KEY_SURFACE_INHERITANCE_MIGRATED, true);
        store.putBoolean(TERMUX_APP.KEY_KEYBOARD_GLASS_SLOT_FOLDED, true);
        store.putBoolean(TERMUX_APP.KEY_KEYBOARD_OPACITY_SENTINEL_HEALED, true);
        return store;
    }

    private static LookGrid.MemoryStore bare() {
        LookGrid.MemoryStore store = new LookGrid.MemoryStore();
        store.putString(TERMUX_APP.KEY_LOG_LEVEL, "1");
        return store;
    }

    private static LookGrid migrate(LookGrid.MemoryStore store) {
        LookGridMigration.run(store, PAGES);
        return new LookGrid(store);
    }

    @Test
    public void aFreshInstallStaysFresh() {
        LookGrid.MemoryStore store = bare();
        migrate(store);
        assertEquals(Collections.singleton(TERMUX_APP.KEY_LOG_LEVEL), store.keys());
    }

    @Test
    public void aWallDetachedDockOpacityBecomesTheDocksCell() {
        LookGrid.MemoryStore store = store();
        store.putInt("surface_base_opacity", 40);
        store.putBoolean("surface_inherit_dock_opacity", false);
        store.putInt(TERMUX_APP.KEY_APP_BAR_OPACITY, 70);
        LookGrid grid = migrate(store);
        assertEquals(70, store.getInt("look.opacity.dock", -1));
        assertEquals(40, store.getInt("look.opacity", -1));
        assertEquals(70, grid.value(Property.OPACITY, Element.DOCK));
        assertEquals(40, grid.value(Property.OPACITY, Element.STATUS));
        assertEquals(40, grid.value(Property.OPACITY, Element.KEYBOARD));
    }

    @Test
    public void aDetachedSurfaceWithNoOwnKeyKeepsItsOwnShippedNumber() {
        LookGrid.MemoryStore store = store();
        store.putBoolean("surface_inherit_status_opacity", false);
        LookGrid grid = migrate(store);
        assertEquals(TERMUX_APP.DEFAULT_STATUS_BAR_OPACITY,
            grid.value(Property.OPACITY, Element.STATUS));
    }

    @Test
    public void aPageOverrideIsKeptUnderThePagesKeyAndNotRead() {
        LookGrid.MemoryStore store = store();
        store.putInt("surface_base_opacity", 34);
        store.putInt(TERMUX_APP.KEY_STATUS_BAR_OPACITY + "@5", 12);
        store.putInt(TERMUX_APP.KEY_TERMINAL_BACKGROUND_OPACITY + "@3", 80);
        LookGrid grid = migrate(store);
        // Kept under the page's key for the reset to find; no page reads it (ADR 0031).
        assertEquals(12, store.getInt("look.opacity.status@5", -1));
        assertEquals(34, grid.value(Property.OPACITY, Element.STATUS));
        assertEquals(80, store.getInt("look.opacity.terminal@3", -1));
        assertFalse(store.contains("look.opacity@5"));
    }

    @Test
    public void aGlobalOnlyInstallLandsOnGlobal() {
        LookGrid.MemoryStore store = store();
        store.putInt("surface_base_blur", 12);
        store.putInt("surface_base_grain", 4);
        store.putInt("surface_base_tint", 20);
        LookGrid grid = migrate(store);
        assertEquals(FrostStops.TINT, store.getInt("look.frost", -1));
        // Tint's stop writes grain 4 itself: no explicit grain.
        assertFalse(store.contains("look.grain"));
        for (Element element : new Element[] {Element.DOCK, Element.STATUS, Element.KEYBOARD,
            Element.TERMINAL}) {
            assertEquals(FrostStops.TINT, grid.value(Property.FROST, element));
            assertEquals(4, grid.value(Property.GRAIN, element));
            assertEquals(20, grid.value(Property.TINT, element));
        }
        for (String key : store.keys())
            assertTrue(key, key.startsWith("look") || key.equals(TERMUX_APP.KEY_LOG_LEVEL)
                || key.equals(TERMUX_APP.KEY_SURFACE_INHERITANCE_MIGRATED));
    }

    @Test
    public void aFrostPairOffTheCurveKeepsItsGrain() {
        LookGrid.MemoryStore store = store();
        store.putInt("surface_base_blur", 8);
        store.putInt("surface_base_grain", 18);
        LookGrid grid = migrate(store);
        assertEquals(FrostStops.CLEAR, store.getInt("look.frost", -1));
        assertEquals(18, store.getInt("look.grain", -1));
        assertEquals(18, grid.value(Property.GRAIN, Element.DOCK));
    }

    @Test
    public void aDetachedBlurOverAnInheritedGrainPinsTheGrain() {
        LookGrid.MemoryStore store = store();
        store.putInt("surface_base_blur", 12);
        store.putInt("surface_base_grain", 4);
        store.putBoolean("surface_inherit_dock_blur", false);
        store.putInt(TERMUX_APP.KEY_EXTRAKEYS_BLUR_RADIUS, 25);
        LookGrid grid = migrate(store);
        assertEquals(FrostStops.MIST, grid.value(Property.FROST, Element.DOCK));
        // Mist's own grain is 7; the dock's grain followed Global's 4 and still does.
        assertEquals(4, grid.value(Property.GRAIN, Element.DOCK));
        assertEquals(4, grid.value(Property.GRAIN, Element.DOCK));
        assertEquals(FrostStops.TINT, grid.value(Property.FROST, Element.STATUS));
    }

    @Test
    public void anInstallFromBeforeGlobalTakesTheDocksNumbersAsGlobal() {
        LookGrid.MemoryStore store = bare();
        store.putInt(TERMUX_APP.KEY_APP_BAR_OPACITY, 60);
        store.putInt(TERMUX_APP.KEY_STATUS_BAR_OPACITY, 60);
        store.putInt(TERMUX_APP.KEY_TERMINAL_BACKGROUND_OPACITY, 25);
        LookGrid grid = migrate(store);
        assertEquals(60, grid.value(Property.OPACITY, null));
        assertEquals(60, grid.value(Property.OPACITY, Element.STATUS));
        assertFalse(store.contains("look.opacity.status"));
        assertEquals(25, grid.value(Property.OPACITY, Element.TERMINAL));
    }

    @Test
    public void aKeyboardBlurAtMinusOneFollowsADetachedDock() {
        LookGrid.MemoryStore store = store();
        store.putBoolean(TERMUX_APP.KEY_KEYBOARD_GLASS_SLOT_FOLDED, false);
        store.putInt("surface_base_blur", 12);
        store.putBoolean("surface_inherit_dock_blur", false);
        store.putInt(TERMUX_APP.KEY_EXTRAKEYS_BLUR_RADIUS, 25);
        store.putInt(TERMUX_APP.KEY_IN_APP_KEYBOARD_BLUR_RADIUS, -1);
        LookGrid grid = migrate(store);
        assertEquals(FrostStops.MIST, grid.value(Property.FROST, Element.KEYBOARD));
        assertEquals(FrostStops.TINT, grid.value(Property.FROST, Element.STATUS));
    }

    /**
     * The half upgrade's output for a Workspace that followed its own Global of 12 while Home's
     * dock was detached at 30: the dock's own 30 everywhere; the Terminal page's 12 is kept
     * under its key, unread.
     */
    @Test
    public void aWorkspacesOwnGlobalIsKeptUnderTheTerminalPagesKeyAndNotRead() {
        LookGrid.MemoryStore store = store();
        store.putInt("surface_base_blur", 4);
        store.putBoolean("surface_inherit_dock_blur", false);
        store.putInt(TERMUX_APP.KEY_EXTRAKEYS_BLUR_RADIUS, 30);
        store.putInt(TERMUX_APP.KEY_EXTRAKEYS_BLUR_RADIUS + "@3", 12);
        LookGrid grid = migrate(store);
        assertEquals(FrostStops.nearestStopByBlur(12), store.getInt("look.frost.dock@3", -1));
        // The page's key is no page's look any more (ADR 0031): every page wears the dock's own.
        assertEquals(31, FrostStops.blurDpFor(grid.value(Property.FROST, Element.DOCK)));
    }

    @Test
    public void widgetsOffGlassBecomeSolidWidgets() {
        LookGrid.MemoryStore store = store();
        store.putBoolean(TERMUX_APP.KEY_WIDGETS_ON_GLASS, false);
        LookGrid grid = migrate(store);
        assertEquals(100, grid.value(Property.OPACITY, Element.WIDGETS));
        assertEquals(FrostStops.SOLID, grid.value(Property.FROST, Element.WIDGETS));
        assertFalse(store.contains(TERMUX_APP.KEY_WIDGETS_ON_GLASS));

        LookGrid.MemoryStore on = store();
        on.putBoolean(TERMUX_APP.KEY_WIDGETS_ON_GLASS, true);
        migrate(on);
        assertFalse(on.contains("look.opacity.widgets"));
        assertFalse(on.contains("look.frost.widgets"));
    }

    @Test
    public void theDrawersCornerBecomesTheSheetsCorner() {
        LookGrid.MemoryStore store = store();
        store.putInt(TERMUX_APP.KEY_APP_LAUNCHER_DRAWER_CORNER_RADIUS, 16);
        store.putInt("surface_base_corner_radius", 30);
        LookGrid grid = migrate(store);
        assertEquals(16, grid.value(Property.CORNERS, Element.SHEETS));
        assertEquals(30, grid.value(Property.CORNERS, Element.DOCK));
    }

    @Test
    public void wallWideGlassValuesLandOnGlobal() {
        LookGrid.MemoryStore store = store();
        store.putInt(TERMUX_APP.KEY_SURFACE_GLASS_VIBRANCY, 120);
        store.putString(TERMUX_APP.KEY_SURFACE_GLASS_TINT_HUE, TERMUX_APP.GLASS_TINT_HUE_TERTIARY);
        store.putString(TERMUX_APP.KEY_SURFACE_GLASS_RIM, TERMUX_APP.GLASS_RIM_GRADIENT);
        store.putInt(TERMUX_APP.KEY_FANCIER_GLASS_BEND, 27);
        store.putInt(TERMUX_APP.KEY_FANCIER_GLASS_DISPERSION, 40);
        store.putInt("surface_base_side_gap", 8);
        LookGrid grid = migrate(store);
        assertEquals(120, grid.value(Property.VIBRANCY, null));
        assertEquals(LookGrid.TINT_HUE_TERTIARY, grid.value(Property.TINT_HUE, Element.DOCK));
        assertEquals(LookGrid.RIM_GRADIENT, grid.value(Property.RIM, Element.STATUS));
        assertEquals(27, grid.value(Property.DEPTH, Element.KEYBOARD));
        assertEquals(40, grid.value(Property.EDGE, null));
        assertEquals(8, grid.value(Property.MARGIN, null));
    }

    @Test
    public void everyOldKeyReadIsDeletedAndTheRestKept() {
        LookGrid.MemoryStore store = store();
        String[] old = {
            "surface_base_blur", "surface_base_opacity", "surface_base_grain", "surface_base_tint",
            "surface_base_corner_radius", "surface_base_side_gap",
            TERMUX_APP.KEY_EXTRAKEYS_BLUR_RADIUS, TERMUX_APP.KEY_APP_BAR_OPACITY,
            TERMUX_APP.KEY_DOCK_GLASS_GRAIN, TERMUX_APP.KEY_DOCK_TINT_STRENGTH,
            TERMUX_APP.KEY_IN_APP_KEYBOARD_BLUR_RADIUS,
            TERMUX_APP.KEY_IN_APP_KEYBOARD_BACKGROUND_OPACITY, TERMUX_APP.KEY_IN_APP_KEYBOARD_GRAIN,
            TERMUX_APP.KEY_IN_APP_KEYBOARD_TINT_STRENGTH, TERMUX_APP.KEY_STATUS_BAR_BLUR_RADIUS,
            TERMUX_APP.KEY_STATUS_BAR_OPACITY, TERMUX_APP.KEY_STATUS_BAR_GRAIN,
            TERMUX_APP.KEY_STATUS_BAR_TINT_STRENGTH, TERMUX_APP.KEY_TERMINAL_GLASS_BLUR_RADIUS,
            TERMUX_APP.KEY_TERMINAL_BACKGROUND_OPACITY, TERMUX_APP.KEY_TERMINAL_GLASS_GRAIN,
            TERMUX_APP.KEY_TERMINAL_TINT_STRENGTH, TERMUX_APP.KEY_STATUS_BAR_OPACITY + "@3",
            TERMUX_APP.KEY_APP_BAR_OPACITY + "@0", TERMUX_APP.KEY_APP_LAUNCHER_DOCK_CORNER_RADIUS,
            TERMUX_APP.KEY_STATUS_BAR_CORNER_RADIUS, TERMUX_APP.KEY_DOCK_HORIZONTAL_INSET,
            TERMUX_APP.KEY_IN_APP_KEYBOARD_HORIZONTAL_INSET,
            TERMUX_APP.KEY_STATUS_BAR_HORIZONTAL_INSET, TERMUX_APP.KEY_SURFACE_GLASS_VIBRANCY,
            TERMUX_APP.KEY_FANCIER_GLASS_BEND, TERMUX_APP.KEY_FANCIER_GLASS_DISPERSION,
            TERMUX_APP.KEY_APP_LAUNCHER_DRAWER_CORNER_RADIUS,
            TERMUX_APP.KEY_IN_APP_KEYBOARD_BACKDROP_OPACITY,
            TERMUX_APP.KEY_WALLPAPER_ENABLED_APP_BAR_OPACITY,
            TERMUX_APP.KEY_WALLPAPER_ENABLED_TERMINAL_BACKGROUND_OPACITY,
            TERMUX_APP.KEY_WALLPAPER_ENABLED_EXTRAKEYS_BLUR_RADIUS,
        };
        for (String key : old) store.putInt(key, 10);
        store.putBoolean("surface_inherit_dock_blur", false);
        store.putBoolean("surface_inherit_keyboard_tint", true);
        store.putBoolean(TERMUX_APP.KEY_IN_APP_KEYBOARD_OPACITY_LINKED, true);
        store.putBoolean(TERMUX_APP.KEY_WIDGETS_ON_GLASS, true);
        store.putBoolean(TERMUX_APP.KEY_KEYBOARD_OPACITY_SENTINEL_HEALED, true);
        store.putBoolean(TERMUX_APP.KEY_KEYBOARD_GLASS_SLOT_FOLDED, true);
        store.putString(TERMUX_APP.KEY_SURFACE_GLASS_TINT_HUE, "primary");
        store.putString(TERMUX_APP.KEY_SURFACE_GLASS_RIM, "hairline");
        store.putInt("dock_icon_size", 48);
        store.putInt(TERMUX_APP.KEY_FANCIER_GLASS_EDGE_WIDTH, 18);

        migrate(store);
        Set<String> rest = new HashSet<>();
        for (String key : store.keys()) {
            if (LookGrid.parse(key) == null) rest.add(key);
        }
        // The keyboard's opacity link and backdrop and the wallpaper-mode memory stay its own keys
        // in W1; the pre-grid upgrades' markers stay with them.
        assertEquals(new HashSet<>(Arrays.asList(TERMUX_APP.KEY_LOG_LEVEL,
            LookGridMigration.KEY_MIGRATED, "dock_icon_size",
            TERMUX_APP.KEY_FANCIER_GLASS_EDGE_WIDTH, TERMUX_APP.KEY_SURFACE_INHERITANCE_MIGRATED,
            TERMUX_APP.KEY_IN_APP_KEYBOARD_OPACITY_LINKED,
            TERMUX_APP.KEY_IN_APP_KEYBOARD_BACKDROP_OPACITY,
            TERMUX_APP.KEY_WALLPAPER_ENABLED_APP_BAR_OPACITY,
            TERMUX_APP.KEY_WALLPAPER_ENABLED_TERMINAL_BACKGROUND_OPACITY,
            TERMUX_APP.KEY_WALLPAPER_ENABLED_EXTRAKEYS_BLUR_RADIUS)), rest);
    }

    @Test
    public void itRunsOnceAndNeverOverAGrid() {
        LookGrid.MemoryStore store = store();
        store.putInt("surface_base_opacity", 40);
        LookGrid grid = migrate(store);
        grid.write(Property.OPACITY, null, 60);
        store.putInt("surface_base_opacity", 10);
        migrate(store);
        assertEquals(60, grid.value(Property.OPACITY, null));

        LookGrid.MemoryStore already = store();
        already.putInt("look.opacity", 55);
        already.putInt("surface_base_opacity", 10);
        migrate(already);
        assertEquals(55, already.getInt("look.opacity", -1));
        assertTrue(already.getBoolean(LookGridMigration.KEY_MIGRATED, false));
    }
}
