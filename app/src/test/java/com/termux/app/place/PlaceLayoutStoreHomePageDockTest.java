package com.termux.app.place;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;

import com.termux.app.dock.DockStyle;
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
 * Every Home page wears Home's dock (ADR 0018 section 4, 2026-11-01): a Home page's own layout
 * never carries a dock, so swiping between Home pages changes nothing in the dock.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
@ConscryptMode(ConscryptMode.Mode.OFF)
public class PlaceLayoutStoreHomePageDockTest {

    private SharedPreferences prefs;
    private PlaceLayoutStore store;

    @Before
    public void setUp() {
        Application app = RuntimeEnvironment.getApplication();
        prefs = app.getSharedPreferences("home-page-dock-test", Context.MODE_PRIVATE);
        prefs.edit().clear().commit();
        store = routed(new PlaceLayoutStore(new TermuxAppSharedPreferences(app, prefs, null)));
        // Home's own dock, in Home's document (ADR 0020 §5): the flat page keys are never read.
        PlaceLayoutStore homeLayout = store.forVariant(LayoutVariant.HOME);
        homeLayout.setDockStyle(DockStyle.PEEK_GLASS);
        homeLayout.setSearchField(false);
        homeLayout.setDrawerTile(false);
    }

    private PlaceLayoutStore home(int id) {
        return store.forVariant(LayoutVariant.page(id, PageKind.HOME));
    }

    @Test
    public void aHomePageWithItsOwnDockKeysReadsHomes() {
        PlaceLayoutStore page = home(1);
        assertEquals(DockStyle.PEEK_GLASS, page.dockStyle());
        assertEquals(DockStyle.PEEK_GLASS,
            store.homePageDockDesign(LayoutVariant.page(1, PageKind.HOME)));
        assertFalse(page.searchField());
        assertFalse(page.drawerTile());
        assertEquals(store.forVariant(LayoutVariant.HOME).dockHeightScale(PlaceOrientation.PORTRAIT),
            page.dockHeightScale(PlaceOrientation.PORTRAIT), 0f);
    }

    @Test
    public void aWriteFromAHomePageLandsOnHomes() {
        PlaceLayoutStore page = home(1);
        page.setDockStyle(DockStyle.GLASS_TRAY);
        page.setSearchField(true);
        page.setDrawerTile(true);
        page.setAzRowShown(PlaceOrientation.PORTRAIT, false);
        PlaceLayoutStore homeLayout = store.forVariant(LayoutVariant.HOME);
        assertEquals(DockStyle.GLASS_TRAY, homeLayout.dockStyle());
        assertTrue(homeLayout.searchField());
        assertTrue(homeLayout.drawerTile());
        assertEquals("every Home page wears it", DockStyle.GLASS_TRAY, home(2).dockStyle());
        assertTrue(home(2).searchField());
        assertFalse(home(2).azRowShown(PlaceOrientation.PORTRAIT));
        assertFalse("the Terminal's layout is untouched",
            store.forVariant(LayoutVariant.NORMAL).searchField());
    }

    @Test
    public void aTerminalPageWearsTheTerminalsDockAndHomeKeepsItsOwn() {
        store.forVariant(LayoutVariant.NORMAL).setDockStyle(DockStyle.LENS_BEADS);
        PlaceLayoutStore terminal = store.forVariant(LayoutVariant.page(3, PageKind.TERMINAL));
        assertEquals(DockStyle.LENS_BEADS, terminal.dockStyle());
        terminal.setDockStyle(DockStyle.GLASS_TRAY);
        assertEquals(DockStyle.GLASS_TRAY, store.forVariant(LayoutVariant.NORMAL).dockStyle());
        assertEquals(DockStyle.PEEK_GLASS, store.forVariant(LayoutVariant.HOME).dockStyle());
    }

    /** Reads and writes of layout keys go where the store keeps them now: the documents (ADR 0020). */
    private PlaceLayoutStore routed(PlaceLayoutStore store) {
        android.content.SharedPreferences view = store.layoutPreferences();
        if (view != null) prefs = view;
        return store;
    }
}
