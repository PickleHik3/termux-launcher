package com.termux.app.look;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import com.termux.app.wall.WallPages.Page;
import com.termux.app.wall.WallPages.PageKind;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences.SurfaceProperty;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences.SurfaceSlot;
import com.termux.shared.termux.settings.preferences.TermuxPreferenceConstants.TERMUX_APP;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class PageLookTest {

    private static final Page HOME = new Page(1, PageKind.HOME, null, null);
    private static final Page TERMINAL = new Page(2, PageKind.TERMINAL, null, null);
    private static final Page HOME_B = new Page(3, PageKind.HOME, null, null);

    private MemoryLookStore store;
    private PageLook look;
    private List<Set<Integer>> events;

    @Before
    public void setUp() {
        store = new MemoryLookStore();
        // A page's look is its kind's (ADR 0020 §2): Home pages 1 and 3 share the Home kind's
        // cells, the Terminal page 2 has the Terminal kind's.
        com.termux.shared.termux.settings.preferences.LookGrid.install(page -> page == 1 || page == 3 ? com.termux.shared.termux.settings.preferences.LookGrid.Kind.HOME
            : page == 2 ? com.termux.shared.termux.settings.preferences.LookGrid.Kind.TERMINAL : null, null);
        look = new PageLook(store);
        events = new ArrayList<>();
        look.addListener(events::add);
    }

    /** The grid's page kinds are process-wide: none leaks into the next test class. */
    @org.junit.After
    public void resetLookGridPageKinds() {
        com.termux.shared.termux.settings.preferences.LookGrid.install(null, null);
    }

    private static Set<Integer> ids(Integer... ids) {
        return new HashSet<>(Arrays.asList(ids));
    }

    private static String dockGrain() { return TERMUX_APP.KEY_DOCK_GLASS_GRAIN; }

    /** Own numbers written but not yet detached: the grid holds them only once detached. */
    private final java.util.Map<String, Integer> pendingOwn = new java.util.HashMap<>();

    /** Seeds an old look key as the grid holds it. */
    private void put(String key, int value) {
        int page = LookKeys.pageIdOf(key);
        com.termux.shared.termux.settings.preferences.LookGrid.Key cell =
            LookKeys.cellOf(LookKeys.plainOf(key));
        if (cell == null) {
            store.putInt(key, value);
        } else if (cell.element != null && page < 0 && !store.has(
                com.termux.shared.termux.settings.preferences.LookGrid.key(
                    cell.property, cell.element, page))) {
            pendingOwn.put(key, value);
        } else {
            store.putInt(com.termux.shared.termux.settings.preferences.LookGrid.key(
                cell.property, cell.element, page), LookKeys.toCell(cell.property, value));
        }
    }

    /** Whether the store holds an old look key's cell (or the key itself, for any other). */
    private boolean has(String key) {
        int page = LookKeys.pageIdOf(key);
        com.termux.shared.termux.settings.preferences.LookGrid.Key cell =
            LookKeys.cellOf(LookKeys.plainOf(key));
        if (cell == null) return store.has(key);
        if (cell.element != null && page < 0 && pendingOwn.containsKey(key)) return true;
        return store.has(com.termux.shared.termux.settings.preferences.LookGrid.key(
            cell.property, cell.element, page));
    }

    /** Detaching makes the surface's cell appear, holding its own number. */
    private void detach(SurfaceSlot slot, SurfaceProperty property) {
        String own = LookKeys.surfaceKey(slot, property);
        Integer value = pendingOwn.get(own);
        store.putInt(com.termux.shared.termux.settings.preferences.LookGrid.key(
                property.grid, slot.element,
                com.termux.shared.termux.settings.preferences.LookGrid.NO_PAGE),
            LookKeys.toCell(property.grid,
                value != null ? value : LookKeys.surfaceDefault(slot, property)));
    }

    // ---- resolution order

    @Test
    public void emptyStoreAnswersShippedValues() {
        PageLook.Snapshot s = look.of(HOME);
        // Every surface follows Base until detached, so a fresh install reads Base's defaults.
        assertEquals(com.termux.shared.termux.settings.preferences.LookGrid.Property.GRAIN.shipped, s.dockGlassGrain());
        assertEquals(TERMUX_APP.DEFAULT_SURFACE_BASE_OPACITY, s.appBarOpacity());
        assertEquals(com.termux.shared.termux.settings.preferences.FrostStops.blurDpFor(com.termux.shared.termux.settings.preferences.FrostStops.CLEAR), s.extraKeysBlurRadius());
        assertEquals(TERMUX_APP.DEFAULT_SURFACE_BASE_OPACITY, s.statusBarOpacity());
        assertEquals(TERMUX_APP.DEFAULT_SURFACE_MATERIAL, s.surfaceMaterial());
        assertEquals(TERMUX_APP.DEFAULT_SURFACE_MATERIAL_INTENSITY, s.surfaceMaterialIntensity());
        assertEquals(TERMUX_APP.DEFAULT_WALLPAPER_BACKDROP_DIM, s.wallpaperDim());
        assertEquals(100, s.inAppKeyboardBackdropOpacity());
    }

    @Test
    public void surfaceFollowsBaseByDefault() {
        put(TERMUX_APP.KEY_SURFACE_BASE_GRAIN, 40);
        put(dockGrain(), 77); // a stale own value, the flag still says follow Base
        assertEquals(40, look.of(HOME).dockGlassGrain());
        assertEquals(40, look.of(TERMINAL).dockGlassGrain());
    }

    @Test
    public void detachedSurfaceHoldsItsWallValue() {
        put(TERMUX_APP.KEY_SURFACE_BASE_GRAIN, 40);
        put(dockGrain(), 77);
        detach(SurfaceSlot.DOCK, SurfaceProperty.GRAIN);
        assertEquals(77, look.of(HOME).dockGlassGrain());
    }

    @Test
    public void kindCellBeatsWallValueAndBase() {
        put(TERMUX_APP.KEY_SURFACE_BASE_GRAIN, 40);
        put(dockGrain(), 77);
        detach(SurfaceSlot.DOCK, SurfaceProperty.GRAIN);
        assertTrue(look.write(PageLook.Scope.page(2), dockGrain(), 5));
        assertEquals(5, look.of(TERMINAL).dockGlassGrain());
        assertEquals(77, look.of(HOME).dockGlassGrain());
    }

    @Test
    public void kindCellBeatsBaseWhenSurfaceFollowsBase() {
        put(TERMUX_APP.KEY_SURFACE_BASE_GRAIN, 40);
        assertTrue(look.write(PageLook.Scope.page(3), dockGrain(), 9));
        // Both Home pages wear the Home kind's cell; the Terminal page still follows Base.
        assertEquals(9, look.of(HOME_B).dockGlassGrain());
        assertEquals(9, look.of(HOME).dockGlassGrain());
        assertEquals(40, look.of(TERMINAL).dockGlassGrain());
    }

    @Test
    public void valuesAreClampedAsTheGettersClamp() {
        put(TERMUX_APP.KEY_SURFACE_BASE_BLUR, 90);
        put(TERMUX_APP.KEY_SURFACE_BASE_OPACITY, -4);
        PageLook.Snapshot s = look.of(HOME);
        // Past the last Frost stop: its 36 dp.
        assertEquals(36, s.extraKeysBlurRadius());
        assertEquals(0, s.appBarOpacity());
    }

    // ---- detachable vs wall-wide only

    @Test
    public void everyLookPropertyOfDockStatusKeyboardIsDetachable() {
        SurfaceSlot[] slots = {SurfaceSlot.DOCK, SurfaceSlot.STATUS, SurfaceSlot.KEYBOARD};
        SurfaceProperty[] props = {SurfaceProperty.BLUR, SurfaceProperty.OPACITY,
            SurfaceProperty.GRAIN, SurfaceProperty.TINT};
        for (SurfaceSlot slot : slots) {
            for (SurfaceProperty property : props) {
                String key = LookKeys.surfaceKey(slot, property);
                assertTrue(slot + " " + property, PageLook.isDetachable(key));
                assertTrue(look.write(PageLook.Scope.page(3), key, 11));
                // Blur 11 wears the nearest Frost stop's 12.
                assertEquals(slot + " " + property, property == SurfaceProperty.BLUR ? 12 : 11,
                    look.of(HOME_B).value(slot, property));
                assertTrue(slot + " " + property,
                    look.of(TERMINAL).value(slot, property) != 11);
            }
        }
    }

    @Test
    public void canvasIsDetachableOnTheTerminalPageOnly() {
        String tint = TERMUX_APP.KEY_TERMINAL_TINT_STRENGTH;
        assertTrue(PageLook.isDetachable(tint));
        assertTrue(look.write(PageLook.Scope.page(2), tint, 33));
        // A leftover @<page> cell of a Home page is no longer read.
        put(LookKeys.pageKey(tint, 3), 44);
        assertEquals(33, look.of(TERMINAL).canvasTint());
        assertEquals(0, look.of(HOME_B).canvasTint());
        assertEquals(TERMUX_APP.DEFAULT_SURFACE_BASE_TINT,
            look.of(HOME_B).value(SurfaceSlot.CANVAS, SurfaceProperty.TINT));
    }

    @Test
    public void shapesAreWallWideAndIgnorePageOverrides() {
        put(TERMUX_APP.KEY_SURFACE_BASE_CORNER_RADIUS, 30);
        put(TERMUX_APP.KEY_SURFACE_BASE_SIDE_GAP, 7);
        String corner = LookKeys.surfaceKey(SurfaceSlot.DOCK, SurfaceProperty.CORNER_RADIUS);
        assertFalse(PageLook.isDetachable(corner));
        assertFalse(look.write(PageLook.Scope.page(3), corner, 2));
        put(LookKeys.pageKey(corner, 3), 2); // a stray key written around the API
        PageLook.Snapshot s = look.of(HOME_B);
        assertEquals(30, s.value(SurfaceSlot.DOCK, SurfaceProperty.CORNER_RADIUS));
        assertEquals(30, s.value(SurfaceSlot.STATUS, SurfaceProperty.CORNER_RADIUS));
        assertEquals(7, s.value(SurfaceSlot.DOCK, SurfaceProperty.SIDE_GAP));
    }

    @Test
    public void materialAndIntensityAreDetachable() {
        String material = TERMUX_APP.KEY_SURFACE_MATERIAL;
        String intensity = TERMUX_APP.KEY_SURFACE_MATERIAL_INTENSITY;
        assertTrue(PageLook.isDetachable(material));
        assertTrue(PageLook.isDetachable(intensity));
        store.putString(material, "stone");
        put(intensity, 70);
        assertEquals("stone", look.of(HOME_B).surfaceMaterial());
        assertEquals(70, look.of(HOME_B).surfaceMaterialIntensity());

        assertTrue(look.writeString(PageLook.Scope.page(3), material, "frost"));
        assertTrue(look.write(PageLook.Scope.page(3), intensity, 20));
        assertEquals("frost", look.of(HOME_B).surfaceMaterial());
        assertEquals(20, look.of(HOME_B).surfaceMaterialIntensity());
        assertEquals("stone", look.of(HOME).surfaceMaterial());
        assertEquals(70, look.of(HOME).surfaceMaterialIntensity());

        look.reset(PageLook.Scope.page(3), material);
        assertEquals("stone", look.of(HOME_B).surfaceMaterial());
    }

    @Test
    public void baseAndInheritFlagsAreWallWide() {
        assertFalse(PageLook.isDetachable(TERMUX_APP.KEY_SURFACE_BASE_GRAIN));
        assertFalse(PageLook.isDetachable(
            ("surface_inherit_" + SurfaceSlot.DOCK.key + "_" + SurfaceProperty.GRAIN.key)));
        assertFalse(look.write(PageLook.Scope.page(3), TERMUX_APP.KEY_SURFACE_BASE_GRAIN, 1));
        assertFalse(has(LookKeys.pageKey(TERMUX_APP.KEY_SURFACE_BASE_GRAIN, 3)));
    }

    @Test
    public void groundInputsAreDetachable() {
        put(TERMUX_APP.KEY_WALLPAPER_BACKDROP_DIM, 20);
        assertTrue(look.write(PageLook.Scope.page(3), TERMUX_APP.KEY_WALLPAPER_BACKDROP_DIM, 55));
        assertEquals(55, look.of(HOME_B).wallpaperDim());
        assertEquals(20, look.of(HOME).wallpaperDim());
        look.write(PageLook.Scope.page(3), TERMUX_APP.KEY_IN_APP_KEYBOARD_BACKDROP_OPACITY, 60);
        assertEquals(60, look.of(HOME_B).inAppKeyboardBackdropOpacity());
        assertEquals(100, look.of(HOME).inAppKeyboardBackdropOpacity());
    }

    @Test
    public void canvasTintOnTheTerminalPageFollowsBaseUnlessDetached() {
        put(TERMUX_APP.KEY_SURFACE_BASE_TINT, 25);
        assertEquals(25, look.of(TERMINAL).canvasTint());
        detach(SurfaceSlot.CANVAS, SurfaceProperty.TINT);
        put(TERMUX_APP.KEY_TERMINAL_TINT_STRENGTH, 60);
        assertEquals(60, look.of(TERMINAL).canvasTint());
    }

    // ---- caching and invalidation

    @Test
    public void snapshotsAreCachedPerPageId() {
        PageLook.Snapshot a = look.of(HOME);
        assertSame(a, look.of(new Page(1, PageKind.HOME, "renamed", null)));
        assertNotSame(a, look.of(HOME_B));
    }

    @Test
    public void kindWriteInvalidatesOnlyThatKindsPages() {
        PageLook.Snapshot terminal = look.of(TERMINAL);
        PageLook.Snapshot other = look.of(HOME_B);
        assertTrue(look.write(PageLook.Scope.page(3), dockGrain(), 12));
        assertSame(terminal, look.of(TERMINAL));
        assertNotSame(other, look.of(HOME_B));
        assertEquals(12, look.of(HOME_B).dockGlassGrain());
        assertEquals(1, events.size());
        assertTrue(events.get(0).contains(3));
        assertFalse(events.get(0).contains(2));
    }

    @Test
    public void baseWriteTouchesEveryCachedPage() {
        PageLook.Snapshot home = look.of(HOME);
        look.of(TERMINAL);
        look.of(HOME_B);
        assertTrue(look.write(PageLook.Scope.GLOBAL, TERMUX_APP.KEY_SURFACE_BASE_GRAIN, 50));
        assertEquals(1, events.size());
        assertEquals(ids(1, 2, 3), events.get(0));
        assertNotSame(home, look.of(HOME));
        assertEquals(50, look.of(HOME).dockGlassGrain());
    }

    @Test
    public void wallSurfaceWriteTouchesEveryCachedPage() {
        detach(SurfaceSlot.DOCK, SurfaceProperty.GRAIN);
        look.of(HOME);
        look.of(TERMINAL);
        events.clear();
        look.write(PageLook.Scope.GLOBAL, dockGrain(), 31);
        assertEquals(ids(1, 2), events.get(0));
        assertEquals(31, look.of(TERMINAL).dockGlassGrain());
    }

    @Test
    public void unrelatedKeysAreIgnored() {
        look.of(HOME);
        put("font_size", 14);
        put("font_size@1", 14);
        assertTrue(events.isEmpty());
    }

    @Test
    public void noEventWhenNothingIsCached() {
        look.write(PageLook.Scope.GLOBAL, TERMUX_APP.KEY_SURFACE_BASE_GRAIN, 50);
        assertTrue(events.isEmpty());
    }

    // ---- reset per scope

    @Test
    public void resetAtPageClearsOnlyTheKindsCell() {
        detach(SurfaceSlot.DOCK, SurfaceProperty.GRAIN);
        look.write(PageLook.Scope.GLOBAL, dockGrain(), 70);
        look.write(PageLook.Scope.page(3), dockGrain(), 10);
        assertEquals(10, look.of(HOME_B).dockGlassGrain());
        look.reset(PageLook.Scope.page(3), dockGrain());
        assertEquals(70, look.of(HOME_B).dockGlassGrain());
        assertTrue(has(dockGrain()));
    }

    @Test
    public void resetAtGlobalClearsTheSurfacesOwnValueAndKeepsTheKindsCell() {
        detach(SurfaceSlot.DOCK, SurfaceProperty.GRAIN);
        look.write(PageLook.Scope.GLOBAL, dockGrain(), 70);
        look.write(PageLook.Scope.page(3), dockGrain(), 10);
        look.reset(PageLook.Scope.GLOBAL, dockGrain());
        assertFalse(has(dockGrain()));
        assertEquals(com.termux.shared.termux.settings.preferences.LookGrid.Property.GRAIN.shipped, look.of(TERMINAL).dockGlassGrain());
        assertEquals(10, look.of(HOME_B).dockGlassGrain());
    }

    // ---- prune and drop

    @Test
    public void pruneRemovesOverridesOfUnknownPagesOnly() {
        // Leftover @<page> cells (no writer makes them now; step 17 folds the live ones).
        put(LookKeys.pageKey(dockGrain(), 1), 1);
        put(LookKeys.pageKey(dockGrain(), 8), 2);
        put(LookKeys.pageKey(TERMUX_APP.KEY_WALLPAPER_BACKDROP_DIM, 9), 3);
        put("font_size@9", 3);
        put(dockGrain(), 4);
        look.of(HOME);
        events.clear();
        int removed = look.pruneUnknown(ids(1, 2));
        assertEquals(2, removed);
        assertTrue(has(LookKeys.pageKey(dockGrain(), 1)));
        assertFalse(has(LookKeys.pageKey(dockGrain(), 8)));
        assertFalse(has(LookKeys.pageKey(TERMUX_APP.KEY_WALLPAPER_BACKDROP_DIM, 9)));
        assertTrue(has("font_size@9"));
        assertTrue(has(dockGrain()));
        assertTrue(events.isEmpty());
    }

    @Test
    public void dropPageRemovesItsOverridesAndSnapshot() {
        put(LookKeys.pageKey(dockGrain(), 3), 12);
        put(LookKeys.pageKey(TERMUX_APP.KEY_WALLPAPER_BACKDROP_DIM, 3), 12);
        put(LookKeys.pageKey(dockGrain(), 1), 5);
        PageLook.Snapshot before = look.of(HOME_B);
        assertEquals(2, look.dropPage(3));
        assertTrue(has(LookKeys.pageKey(dockGrain(), 1)));
        assertFalse(has(LookKeys.pageKey(dockGrain(), 3)));
        assertNotSame(before, look.of(HOME_B));
        assertEquals(com.termux.shared.termux.settings.preferences.LookGrid.Property.GRAIN.shipped, look.of(HOME_B).dockGlassGrain());
    }

    // ---- the editor's scope (B2)

    @Test
    public void pageScopeWriteAndResetNotifyThatKindsPages() {
        look.of(HOME);
        look.of(HOME_B);
        look.of(TERMINAL);
        events.clear();
        assertTrue(look.write(PageLook.Scope.page(3), dockGrain(), 12));
        assertFalse(events.get(events.size() - 1).contains(2));
        assertTrue(events.get(events.size() - 1).contains(3));
        assertEquals(12, look.of(HOME_B).dockGlassGrain());
        assertEquals(12, look.of(HOME).dockGlassGrain());
        events.clear();
        look.reset(PageLook.Scope.page(3), dockGrain());
        assertFalse(events.get(events.size() - 1).contains(2));
        assertTrue(events.get(events.size() - 1).contains(3));
        assertEquals(com.termux.shared.termux.settings.preferences.LookGrid.Property.GRAIN.shipped, look.of(HOME_B).dockGlassGrain());
    }

    @Test
    public void globalWriteNotifiesEveryCachedPage() {
        look.of(HOME);
        look.of(TERMINAL);
        events.clear();
        assertTrue(look.write(PageLook.Scope.GLOBAL, TERMUX_APP.KEY_SURFACE_BASE_GRAIN, 33));
        assertEquals(ids(1, 2), events.get(events.size() - 1));
        assertEquals(33, look.of(TERMINAL).dockGlassGrain());
    }

    @Test
    public void hasAndReadAnswerTheLevelOfTheScope() {
        PageLook.Scope page = PageLook.Scope.page(3);
        assertFalse(look.has(page, dockGrain()));
        assertEquals(-1, look.read(page, dockGrain(), -1));
        look.write(page, dockGrain(), 21);
        assertTrue(look.has(page, dockGrain()));
        // Page 1 is a Home page too: one kind, one cell. The Terminal page's is its own.
        assertTrue(look.has(PageLook.Scope.page(1), dockGrain()));
        assertFalse(look.has(PageLook.Scope.page(2), dockGrain()));
        assertFalse(look.has(PageLook.Scope.GLOBAL, dockGrain()));
        assertEquals(21, look.read(page, dockGrain(), -1));
        // A wall-wide-only key never holds a page's value.
        assertFalse(look.write(page, TERMUX_APP.KEY_SURFACE_BASE_GRAIN, 5));
        assertFalse(look.has(page, TERMUX_APP.KEY_SURFACE_BASE_GRAIN));
    }
}
