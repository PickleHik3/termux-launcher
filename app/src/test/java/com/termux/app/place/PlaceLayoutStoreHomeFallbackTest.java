package com.termux.app.place;

import static org.junit.Assert.assertEquals;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;

import com.termux.app.dock.DockStyle;
import com.termux.app.place.PlaceLayout.Edge;
import com.termux.app.wall.WallPages.PageKind;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.ConscryptMode;

/**
 * A Home page that holds no layout key of its own wears the Home kind's (ADR 0018 section 4):
 * pong's wall had Home pages 1 and 8, only page 1 edited, and page 8 drew the shipped tray.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
@ConscryptMode(ConscryptMode.Mode.OFF)
public class PlaceLayoutStoreHomeFallbackTest {

    private SharedPreferences prefs;
    private PlaceLayoutStore store;

    @Before
    public void setUp() {
        Application app = RuntimeEnvironment.getApplication();
        prefs = app.getSharedPreferences("home-fallback-test", Context.MODE_PRIVATE);
        prefs.edit().clear().commit();
        store = new PlaceLayoutStore(new TermuxAppSharedPreferences(app, prefs, null));
        prefs.edit()
            .putString("layout.page1.dock_design", "peek_glass")
            .putString("layout.home.dock_design", "peek_glass")
            .commit();
    }

    private PlaceLayoutStore page(int id, PageKind kind) {
        return store.forVariant(LayoutVariant.page(id, kind));
    }

    private PlaceLayoutStore fresh() {
        Application app = RuntimeEnvironment.getApplication();
        return new PlaceLayoutStore(new TermuxAppSharedPreferences(app, prefs, null));
    }

    private static DockStyle drawn(PlaceLayoutStore s, LayoutVariant variant,
                                   com.termux.app.wall.PaneWallPage page) {
        return s.resolve(com.termux.app.place.PlaceOrientation.PORTRAIT, variant, page).dockStyle;
    }

    @Test
    public void aHomePageOnTheSharedLayoutDrawsHomesDockDesign() {
        prefs.edit().putString("layout.dock_design", "glass_tray").commit();
        PlaceLayoutStore s = fresh();
        assertEquals(DockStyle.PEEK_GLASS,
            drawn(s, LayoutVariant.NORMAL, com.termux.app.wall.PaneWallPage.WIDGETS));
        assertEquals("the dress agrees", DockStyle.PEEK_GLASS,
            com.termux.app.chrome.DressResolver.of(s)
                .dockDesignOf(com.termux.app.wall.PaneWallPage.WIDGETS));
        assertEquals("a window is unchanged", DockStyle.GLASS_TRAY,
            drawn(s, LayoutVariant.NORMAL, com.termux.app.wall.PaneWallPage.TERMINAL));
        assertEquals("the page's own keys are unchanged", DockStyle.PEEK_GLASS,
            drawn(s, LayoutVariant.page(1, PageKind.HOME),
                com.termux.app.wall.PaneWallPage.WIDGETS));
    }

    @Test
    public void aHomePageDrawsTheSharedDockDesignWhenHomeHoldsNone() {
        prefs.edit().remove("layout.home.dock_design")
            .putString("layout.dock_design", "glass_tray").commit();
        PlaceLayoutStore s = fresh();
        assertEquals(DockStyle.GLASS_TRAY,
            drawn(s, LayoutVariant.NORMAL, com.termux.app.wall.PaneWallPage.WIDGETS));
        assertEquals(DockStyle.GLASS_TRAY, com.termux.app.chrome.DressResolver.of(s)
            .dockDesignOf(com.termux.app.wall.PaneWallPage.WIDGETS));
    }

    @Test
    public void aHomePageWithNoKeysWearsHomes() {
        assertEquals(DockStyle.PEEK_GLASS, page(8, PageKind.HOME).dockStyle());
    }

    @Test
    public void editingThePageDetachesOnlyThatPage() {
        assertEquals(DockStyle.PEEK_GLASS, page(8, PageKind.HOME).dockStyle());
        page(8, PageKind.HOME).setDockStyle(DockStyle.GLASS_TRAY);
        assertEquals(DockStyle.GLASS_TRAY, page(8, PageKind.HOME).dockStyle());
        assertEquals(DockStyle.PEEK_GLASS, page(1, PageKind.HOME).dockStyle());
        assertEquals("peek_glass", prefs.getString("layout.home.dock_design", null));
    }

    @Test
    public void clearingThePagesKeysReturnsHomes() {
        page(8, PageKind.HOME).setDockStyle(DockStyle.GLASS_TRAY);
        assertEquals(DockStyle.GLASS_TRAY, page(8, PageKind.HOME).dockStyle());
        store.dropPageLayout(8, PageKind.HOME);
        assertEquals(DockStyle.PEEK_GLASS, page(8, PageKind.HOME).dockStyle());
    }

    @Test
    public void otherKindsDoNotReadHomes() {
        assertEquals(DockStyle.GLASS_TRAY, page(8, PageKind.TERMINAL).dockStyle());
        assertEquals(DockStyle.GLASS_TRAY, page(9, PageKind.DISPLAY).dockStyle());
    }

    @Test
    public void aPerOrientationKeyFallsBackTheSame() {
        String key = Element.STATUS.storageKey();
        prefs.edit().putString("layout.home.portrait." + key, "bottom").commit();
        assertEquals(Edge.BOTTOM,
            page(8, PageKind.HOME).slot(PlaceOrientation.PORTRAIT, Element.STATUS).edge);
        assertEquals(Edge.TOP,
            page(8, PageKind.TERMINAL).slot(PlaceOrientation.PORTRAIT, Element.STATUS).edge);
        prefs.edit().putString("layout.page8.portrait." + key, "top").commit();
        assertEquals(Edge.TOP,
            page(8, PageKind.HOME).slot(PlaceOrientation.PORTRAIT, Element.STATUS).edge);
    }

    /**
     * The arrangement the dock is drawn from agrees with the dress: on the shared layout a Home
     * page draws Home's design, a window the shared one (emulator, pong's prefs, 2026-11-01: the
     * dress said Peek while Home 2's dock still drew the Tray).
     */
    @Test
    public void theDrawnArrangementOfAHomePageOnTheSharedLayoutIsHomes() {
        prefs.edit().putString("layout.dock_design", "glass_tray").commit();
        com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences.LayoutStyle style =
            store.shape().style;
        assertEquals(DockStyle.PEEK_GLASS, ResolvedArrangement.resolve(store,
            PlaceOrientation.PORTRAIT, LayoutVariant.NORMAL,
            com.termux.app.wall.PageRef.of(com.termux.app.wall.PaneWallPage.WIDGETS, 8), style)
            .design());
        assertEquals(DockStyle.GLASS_TRAY, ResolvedArrangement.resolve(store,
            PlaceOrientation.PORTRAIT, LayoutVariant.NORMAL,
            com.termux.app.wall.PageRef.of(com.termux.app.wall.PaneWallPage.TERMINAL, 4), style)
            .design());
    }
}
