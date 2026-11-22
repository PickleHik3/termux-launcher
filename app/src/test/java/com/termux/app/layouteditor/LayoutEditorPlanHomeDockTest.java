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
import com.termux.app.page.PageKind;
import com.termux.app.place.KindFixtures;
import com.termux.app.place.PlaceLayoutStore;
import com.termux.app.place.PlaceOrientation;
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

    private LayoutEditorPlan open(PageKind page) {
        return DraftedSession.enter(places, page, PlaceOrientation.PORTRAIT);
    }

    private DressResolver dresses() {
        return DressResolver.of(places);
    }

    @Test
    public void aHomePageWithNoLayoutEditsHomesDockAndDrawsIt() {
        LayoutEditorPlan plan = open(PageKind.HOME);
        plan.setDockStyle(DockStyle.PEEK_GLASS);
        plan.setSearchField(true);
        plan.setDrawerTile(true);

        assertEquals("peek_glass", prefs.getString("layout.home.dock_design", null));
        assertTrue(prefs.getBoolean("layout.home.search_field", false));
        assertTrue(prefs.getBoolean("layout.home.drawer_tile", false));
        assertFalse("no page layout of its own", prefs.contains("layout.page1.dock_design"));
        assertEquals(DockStyle.PEEK_GLASS, plan.dockStyle());
        assertEquals(DockStyle.PEEK_GLASS, dresses().dressOf(PageKind.HOME).dockDesign());
        assertTrue(dresses().dressOf(PageKind.HOME).searchField());
        assertTrue(dresses().dressOf(PageKind.HOME).drawerTile());
        assertTrue(dresses().searchFieldOf(PageKind.HOME));
        assertTrue(dresses().drawerTileOf(PageKind.HOME));
        assertTrue("the Terminal wears the same dock (ADR 0023 §1)",
            dresses().searchFieldOf(PageKind.TERMINAL));
        assertEquals("the Terminal wears the same dock (ADR 0023 §1)", DockStyle.PEEK_GLASS,
            dresses().dockDesignOf(PageKind.TERMINAL));
    }

    @Test
    public void aHomePageWithItsOwnLayoutEditsTheSameDock() {
        places.setShownPages(KindFixtures.pages(1, 1, 1, 8));
        LayoutEditorPlan plan = open(PageKind.HOME);
        plan.setDockStyle(DockStyle.PEEK_GLASS);
        plan.setSearchField(true);
        plan.setDrawerTile(true);

        assertEquals("peek_glass", prefs.getString("layout.home.dock_design", null));
        assertTrue(prefs.getBoolean("layout.home.search_field", false));
        assertTrue(prefs.getBoolean("layout.home.drawer_tile", false));
        assertFalse(prefs.contains("layout.page1.dock_design"));
        assertEquals(DockStyle.PEEK_GLASS, dresses().dockDesignOf(PageKind.HOME));
        assertTrue(dresses().searchFieldOf(PageKind.HOME));
    }

    @Test
    public void aTerminalPageEditorWritesTheOneDockHomeHolds() {
        LayoutEditorPlan plan = open(PageKind.TERMINAL);
        plan.setDockStyle(DockStyle.PEEK_GLASS);
        plan.setSearchField(true);

        // Whatever page the editor opened on, the dock is Home's one (ADR 0023 §1).
        assertEquals("peek_glass", prefs.getString("layout.home.dock_design", null));
        assertTrue(prefs.getBoolean("layout.home.search_field", false));
    }

    @Test
    public void discardingPutsHomesDockBack() {
        LayoutEditorPlan plan = open(PageKind.HOME);
        plan.setDockStyle(DockStyle.PEEK_GLASS);
        assertTrue(plan.isDirty());
        plan.revert();
        assertEquals(DockStyle.GLASS_TRAY, dresses().dockDesignOf(PageKind.HOME));
    }

    /** Reads and writes of layout keys go where the store keeps them now: the documents (ADR 0020). */
    private PlaceLayoutStore routed(PlaceLayoutStore store) {
        android.content.SharedPreferences view = store.layoutPreferences();
        if (view != null) prefs = view;
        return store;
    }
}
