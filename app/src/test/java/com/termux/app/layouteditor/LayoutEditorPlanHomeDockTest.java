package com.termux.app.layouteditor;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;

import com.termux.app.chrome.DressResolver;
import com.termux.app.dock.DockStyle;
import com.termux.app.place.KindFixtures;
import com.termux.app.place.PlaceLayoutStore;
import com.termux.app.place.PlaceOrientation;
import com.termux.app.wall.PaneWallPage;
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
 * The editor opened on any Home page reads and writes Home's dock, and every Home page draws it
 * (ADR 0018 section 4, 2026-11-01), whether the page has a layout of its own or wears the shared
 * one the Terminal shares. A Terminal page keeps the shared layout's dock.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
@ConscryptMode(ConscryptMode.Mode.OFF)
public class LayoutEditorPlanHomeDockTest {

    private SharedPreferences prefs;
    private PlaceLayoutStore places;

    @Before
    public void setUp() {
        Application app = RuntimeEnvironment.getApplication();
        prefs = app.getSharedPreferences("layout-editor-home-dock-test", Context.MODE_PRIVATE);
        prefs.edit().clear().commit();
        places = KindFixtures.dockOnTheTerminal(
            routed(new PlaceLayoutStore(new TermuxAppSharedPreferences(app, prefs, null))));
        // Home 8 in front holds no layout of its own; Home 1 may.
        places.setShownPages(KindFixtures.pages(8, 1, 1, 8));
    }

    private LayoutEditorPlan open(PaneWallPage page) {
        return LayoutEditorPlan.enter(places, page, PlaceOrientation.PORTRAIT);
    }

    private DressResolver dresses() {
        return DressResolver.of(places);
    }

    @Test
    public void aHomePageWithNoLayoutEditsHomesDockAndDrawsIt() {
        LayoutEditorPlan plan = open(PaneWallPage.WIDGETS);
        plan.setDockStyle(DockStyle.PEEK_GLASS);
        plan.setSearchField(true);
        plan.setDrawerTile(true);

        assertEquals("peek_glass", prefs.getString("layout.home.dock_design", null));
        assertTrue(prefs.getBoolean("layout.home.search_field", false));
        assertTrue(prefs.getBoolean("layout.home.drawer_tile", false));
        assertNull("the shared layout is the Terminal's", prefs.getString("layout.dock_design", null));
        assertFalse(prefs.contains("layout.search_field"));
        assertEquals(DockStyle.PEEK_GLASS, plan.dockStyle());
        assertEquals(DockStyle.PEEK_GLASS, dresses().dressOf(PaneWallPage.WIDGETS).dockDesign());
        assertTrue(dresses().dressOf(PaneWallPage.WIDGETS).searchField());
        assertTrue(dresses().dressOf(PaneWallPage.WIDGETS).drawerTile());
        assertTrue(dresses().searchFieldOf(PaneWallPage.WIDGETS));
        assertTrue(dresses().drawerTileOf(PaneWallPage.WIDGETS));
        assertFalse("the Terminal does not follow", dresses().searchFieldOf(PaneWallPage.TERMINAL));
        assertEquals("the Terminal does not follow", DockStyle.GLASS_TRAY,
            dresses().dockDesignOf(PaneWallPage.TERMINAL));
    }

    @Test
    public void aHomePageWithItsOwnLayoutEditsTheSameDock() {
        places.setShownPages(KindFixtures.pages(1, 1, 1, 8));
        LayoutEditorPlan plan = open(PaneWallPage.WIDGETS);
        plan.setDockStyle(DockStyle.PEEK_GLASS);
        plan.setSearchField(true);
        plan.setDrawerTile(true);

        assertEquals("peek_glass", prefs.getString("layout.home.dock_design", null));
        assertTrue(prefs.getBoolean("layout.home.search_field", false));
        assertTrue(prefs.getBoolean("layout.home.drawer_tile", false));
        assertFalse(prefs.contains("layout.page1.dock_design"));
        assertEquals(DockStyle.PEEK_GLASS, dresses().dockDesignOf(PaneWallPage.WIDGETS));
        assertTrue(dresses().searchFieldOf(PaneWallPage.WIDGETS));
    }

    @Test
    public void aTerminalPageEditorStillWritesTheSharedLayout() {
        LayoutEditorPlan plan = open(PaneWallPage.TERMINAL);
        plan.setDockStyle(DockStyle.PEEK_GLASS);
        plan.setSearchField(true);

        assertEquals("peek_glass", prefs.getString("layout.dock_design", null));
        assertTrue(prefs.getBoolean("layout.search_field", false));
        assertFalse(prefs.contains("layout.home.dock_design"));
        assertFalse(prefs.contains("layout.home.search_field"));
    }

    @Test
    public void aHomeDockNobodyEditedKeepsTheSharedLooksUntilTheFirstEdit() {
        prefs.edit().putString("layout.dock_design", "peek_glass")
            .putBoolean("layout.search_field", true).commit();
        PlaceLayoutStore fresh = routed(new PlaceLayoutStore(
            new TermuxAppSharedPreferences(RuntimeEnvironment.getApplication(), prefs, null)));
        fresh.setShownPages(KindFixtures.pages(8, 1, 1, 8));
        LayoutEditorPlan plan = LayoutEditorPlan.enter(fresh, PaneWallPage.WIDGETS,
            PlaceOrientation.PORTRAIT);

        assertEquals(DockStyle.PEEK_GLASS, plan.dockStyle());
        assertTrue(plan.searchField());
        assertEquals(DockStyle.PEEK_GLASS,
            DressResolver.of(fresh).dockDesignOf(PaneWallPage.WIDGETS));
        assertTrue(DressResolver.of(fresh).searchFieldOf(PaneWallPage.WIDGETS));

        plan.setDockStyle(DockStyle.GLASS_TRAY);
        assertEquals("glass_tray", prefs.getString("layout.home.dock_design", null));
        assertEquals("the Terminal keeps its own", "peek_glass",
            prefs.getString("layout.dock_design", null));
        assertEquals(DockStyle.GLASS_TRAY,
            DressResolver.of(fresh).dockDesignOf(PaneWallPage.WIDGETS));
    }

    @Test
    public void discardingPutsHomesDockBack() {
        LayoutEditorPlan plan = open(PaneWallPage.WIDGETS);
        plan.setDockStyle(DockStyle.PEEK_GLASS);
        assertTrue(plan.isDirty());
        plan.revert();
        assertEquals(DockStyle.GLASS_TRAY, dresses().dockDesignOf(PaneWallPage.WIDGETS));
    }

    /** Reads and writes of layout keys go where the store keeps them now: the documents (ADR 0020). */
    private PlaceLayoutStore routed(PlaceLayoutStore store) {
        android.content.SharedPreferences view = store.layoutPreferences();
        if (view != null) prefs = view;
        return store;
    }
}
