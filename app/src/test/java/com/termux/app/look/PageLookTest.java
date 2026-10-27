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
        look = new PageLook(store);
        events = new ArrayList<>();
        look.addListener(events::add);
    }

    private static Set<Integer> ids(Integer... ids) {
        return new HashSet<>(Arrays.asList(ids));
    }

    private static String dockGrain() { return TERMUX_APP.KEY_DOCK_GLASS_GRAIN; }

    private void detach(SurfaceSlot slot, SurfaceProperty property) {
        store.putBoolean(LookKeys.inheritKey(slot, property), false);
    }

    // ---- resolution order

    @Test
    public void emptyStoreAnswersShippedValues() {
        PageLook.Snapshot s = look.of(HOME);
        // Every surface follows Base until detached, so a fresh install reads Base's defaults.
        assertEquals(TERMUX_APP.DEFAULT_SURFACE_BASE_GRAIN, s.dockGlassGrain());
        assertEquals(TERMUX_APP.DEFAULT_SURFACE_BASE_OPACITY, s.appBarOpacity());
        assertEquals(TERMUX_APP.DEFAULT_SURFACE_BASE_BLUR, s.extraKeysBlurRadius());
        assertEquals(TERMUX_APP.DEFAULT_SURFACE_BASE_OPACITY, s.statusBarOpacity());
        assertEquals(TERMUX_APP.DEFAULT_SURFACE_MATERIAL, s.surfaceMaterial());
        assertEquals(TERMUX_APP.DEFAULT_SURFACE_MATERIAL_INTENSITY, s.surfaceMaterialIntensity());
        assertEquals(TERMUX_APP.DEFAULT_WALLPAPER_BACKDROP_DIM, s.wallpaperDim());
        assertEquals(100, s.inAppKeyboardBackdropOpacity());
    }

    @Test
    public void surfaceFollowsBaseByDefault() {
        store.putInt(TERMUX_APP.KEY_SURFACE_BASE_GRAIN, 40);
        store.putInt(dockGrain(), 77); // a stale own value, the flag still says follow Base
        assertEquals(40, look.of(HOME).dockGlassGrain());
        assertEquals(40, look.of(TERMINAL).dockGlassGrain());
    }

    @Test
    public void detachedSurfaceHoldsItsWallValue() {
        store.putInt(TERMUX_APP.KEY_SURFACE_BASE_GRAIN, 40);
        store.putInt(dockGrain(), 77);
        detach(SurfaceSlot.DOCK, SurfaceProperty.GRAIN);
        assertEquals(77, look.of(HOME).dockGlassGrain());
    }

    @Test
    public void pageOverrideBeatsWallValueAndBase() {
        store.putInt(TERMUX_APP.KEY_SURFACE_BASE_GRAIN, 40);
        store.putInt(dockGrain(), 77);
        detach(SurfaceSlot.DOCK, SurfaceProperty.GRAIN);
        store.putInt(LookKeys.pageKey(dockGrain(), 2), 5);
        assertEquals(5, look.of(TERMINAL).dockGlassGrain());
        assertEquals(77, look.of(HOME).dockGlassGrain());
    }

    @Test
    public void pageOverrideBeatsBaseWhenSurfaceFollowsBase() {
        store.putInt(TERMUX_APP.KEY_SURFACE_BASE_GRAIN, 40);
        store.putInt(LookKeys.pageKey(dockGrain(), 3), 9);
        assertEquals(9, look.of(HOME_B).dockGlassGrain());
        assertEquals(40, look.of(HOME).dockGlassGrain());
    }

    @Test
    public void valuesAreClampedAsTheGettersClamp() {
        store.putInt(TERMUX_APP.KEY_SURFACE_BASE_BLUR, 90);
        store.putInt(TERMUX_APP.KEY_SURFACE_BASE_OPACITY, -4);
        PageLook.Snapshot s = look.of(HOME);
        assertEquals(48, s.extraKeysBlurRadius());
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
                assertEquals(slot + " " + property, 11, look.of(HOME_B).value(slot, property));
                assertTrue(slot + " " + property,
                    look.of(HOME).value(slot, property) != 11);
            }
        }
    }

    @Test
    public void canvasIsDetachableOnTheTerminalPageOnly() {
        String tint = TERMUX_APP.KEY_TERMINAL_TINT_STRENGTH;
        assertTrue(PageLook.isDetachable(tint));
        store.putInt(LookKeys.pageKey(tint, 2), 33);
        store.putInt(LookKeys.pageKey(tint, 3), 44);
        assertEquals(33, look.of(TERMINAL).canvasTint());
        assertEquals(0, look.of(HOME_B).canvasTint());
        assertEquals(TERMUX_APP.DEFAULT_SURFACE_BASE_TINT,
            look.of(HOME_B).value(SurfaceSlot.CANVAS, SurfaceProperty.TINT));
    }

    @Test
    public void shapesAreWallWideAndIgnorePageOverrides() {
        store.putInt(TERMUX_APP.KEY_SURFACE_BASE_CORNER_RADIUS, 30);
        store.putInt(TERMUX_APP.KEY_SURFACE_BASE_SIDE_GAP, 7);
        String corner = LookKeys.surfaceKey(SurfaceSlot.DOCK, SurfaceProperty.CORNER_RADIUS);
        assertFalse(PageLook.isDetachable(corner));
        assertFalse(look.write(PageLook.Scope.page(3), corner, 2));
        store.putInt(LookKeys.pageKey(corner, 3), 2); // a stray key written around the API
        PageLook.Snapshot s = look.of(HOME_B);
        assertEquals(30, s.value(SurfaceSlot.DOCK, SurfaceProperty.CORNER_RADIUS));
        assertEquals(30, s.value(SurfaceSlot.STATUS, SurfaceProperty.CORNER_RADIUS));
        assertEquals(7, s.value(SurfaceSlot.DOCK, SurfaceProperty.SIDE_GAP));
    }

    @Test
    public void materialAndIntensityAreWallWideOnly() {
        String material = TERMUX_APP.KEY_SURFACE_MATERIAL;
        assertFalse(PageLook.isDetachable(material));
        assertFalse(PageLook.isDetachable(TERMUX_APP.KEY_SURFACE_MATERIAL_INTENSITY));
        store.putString(material, "stone");
        store.putInt(TERMUX_APP.KEY_SURFACE_MATERIAL_INTENSITY, 70);
        store.putString(LookKeys.pageKey(material, 3), "other");
        store.putInt(LookKeys.pageKey(TERMUX_APP.KEY_SURFACE_MATERIAL_INTENSITY, 3), 1);
        assertEquals("stone", look.of(HOME_B).surfaceMaterial());
        assertEquals(70, look.of(HOME_B).surfaceMaterialIntensity());
    }

    @Test
    public void baseAndInheritFlagsAreWallWide() {
        assertFalse(PageLook.isDetachable(TERMUX_APP.KEY_SURFACE_BASE_GRAIN));
        assertFalse(PageLook.isDetachable(
            LookKeys.inheritKey(SurfaceSlot.DOCK, SurfaceProperty.GRAIN)));
        assertFalse(look.write(PageLook.Scope.page(3), TERMUX_APP.KEY_SURFACE_BASE_GRAIN, 1));
        assertFalse(store.has(LookKeys.pageKey(TERMUX_APP.KEY_SURFACE_BASE_GRAIN, 3)));
    }

    @Test
    public void groundInputsAreDetachable() {
        store.putInt(TERMUX_APP.KEY_WALLPAPER_BACKDROP_DIM, 20);
        assertTrue(look.write(PageLook.Scope.page(3), TERMUX_APP.KEY_WALLPAPER_BACKDROP_DIM, 55));
        assertEquals(55, look.of(HOME_B).wallpaperDim());
        assertEquals(20, look.of(HOME).wallpaperDim());
        look.write(PageLook.Scope.page(3), TERMUX_APP.KEY_IN_APP_KEYBOARD_BACKDROP_OPACITY, 60);
        assertEquals(60, look.of(HOME_B).inAppKeyboardBackdropOpacity());
        assertEquals(100, look.of(HOME).inAppKeyboardBackdropOpacity());
    }

    @Test
    public void canvasTintOnTheTerminalPageFollowsBaseUnlessDetached() {
        store.putInt(TERMUX_APP.KEY_SURFACE_BASE_TINT, 25);
        assertEquals(25, look.of(TERMINAL).canvasTint());
        detach(SurfaceSlot.CANVAS, SurfaceProperty.TINT);
        store.putInt(TERMUX_APP.KEY_TERMINAL_TINT_STRENGTH, 60);
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
    public void pageKeyInvalidatesOnlyThatPage() {
        PageLook.Snapshot home = look.of(HOME);
        PageLook.Snapshot other = look.of(HOME_B);
        assertTrue(look.write(PageLook.Scope.page(3), dockGrain(), 12));
        assertSame(home, look.of(HOME));
        assertNotSame(other, look.of(HOME_B));
        assertEquals(12, look.of(HOME_B).dockGlassGrain());
        assertEquals(1, events.size());
        assertEquals(ids(3), events.get(0));
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
        store.putInt("font_size", 14);
        store.putInt("font_size@1", 14);
        assertTrue(events.isEmpty());
    }

    @Test
    public void noEventWhenNothingIsCached() {
        look.write(PageLook.Scope.GLOBAL, TERMUX_APP.KEY_SURFACE_BASE_GRAIN, 50);
        assertTrue(events.isEmpty());
    }

    // ---- reset per scope

    @Test
    public void resetAtPageClearsOnlyThePageOverride() {
        detach(SurfaceSlot.DOCK, SurfaceProperty.GRAIN);
        look.write(PageLook.Scope.GLOBAL, dockGrain(), 70);
        look.write(PageLook.Scope.page(3), dockGrain(), 10);
        assertEquals(10, look.of(HOME_B).dockGlassGrain());
        look.reset(PageLook.Scope.page(3), dockGrain());
        assertEquals(70, look.of(HOME_B).dockGlassGrain());
        assertTrue(store.has(dockGrain()));
    }

    @Test
    public void resetAtGlobalClearsTheSurfacesOwnValueAndKeepsOverrides() {
        detach(SurfaceSlot.DOCK, SurfaceProperty.GRAIN);
        look.write(PageLook.Scope.GLOBAL, dockGrain(), 70);
        look.write(PageLook.Scope.page(3), dockGrain(), 10);
        look.reset(PageLook.Scope.GLOBAL, dockGrain());
        assertFalse(store.has(dockGrain()));
        assertEquals(TERMUX_APP.DEFAULT_VALUE_DOCK_GLASS_GRAIN, look.of(HOME).dockGlassGrain());
        assertEquals(10, look.of(HOME_B).dockGlassGrain());
    }

    // ---- prune and drop

    @Test
    public void pruneRemovesOverridesOfUnknownPagesOnly() {
        look.write(PageLook.Scope.page(1), dockGrain(), 1);
        look.write(PageLook.Scope.page(8), dockGrain(), 2);
        look.write(PageLook.Scope.page(9), TERMUX_APP.KEY_WALLPAPER_BACKDROP_DIM, 3);
        store.putInt("font_size@9", 3);
        store.putInt(dockGrain(), 4);
        look.of(HOME);
        events.clear();
        int removed = look.pruneUnknown(ids(1, 2));
        assertEquals(2, removed);
        assertTrue(store.has(LookKeys.pageKey(dockGrain(), 1)));
        assertFalse(store.has(LookKeys.pageKey(dockGrain(), 8)));
        assertFalse(store.has(LookKeys.pageKey(TERMUX_APP.KEY_WALLPAPER_BACKDROP_DIM, 9)));
        assertTrue(store.has("font_size@9"));
        assertTrue(store.has(dockGrain()));
        assertTrue(events.isEmpty());
    }

    @Test
    public void dropPageRemovesItsOverridesAndSnapshot() {
        look.write(PageLook.Scope.page(3), dockGrain(), 12);
        look.write(PageLook.Scope.page(3), TERMUX_APP.KEY_WALLPAPER_BACKDROP_DIM, 12);
        look.write(PageLook.Scope.page(1), dockGrain(), 5);
        PageLook.Snapshot before = look.of(HOME_B);
        assertEquals(2, look.dropPage(3));
        assertTrue(store.has(LookKeys.pageKey(dockGrain(), 1)));
        assertFalse(store.has(LookKeys.pageKey(dockGrain(), 3)));
        assertNotSame(before, look.of(HOME_B));
        assertEquals(TERMUX_APP.DEFAULT_VALUE_DOCK_GLASS_GRAIN, look.of(HOME_B).dockGlassGrain());
    }
}
