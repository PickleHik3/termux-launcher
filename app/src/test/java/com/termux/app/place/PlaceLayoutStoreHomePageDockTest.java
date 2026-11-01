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
        store = new PlaceLayoutStore(new TermuxAppSharedPreferences(app, prefs, null));
        prefs.edit()
            .putString("layout.home.dock_design", "peek_glass")
            .putString("layout.page1.dock_design", "lens_beads")
            .putBoolean("layout.home.search_field", false)
            .putBoolean("layout.page1.search_field", true)
            .putBoolean("layout.page1.drawer_tile", true)
            .putFloat("layout.home.portrait.dock_height", 1.2f)
            .putFloat("layout.page1.portrait.dock_height", 1.7f)
            .commit();
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
        assertEquals("glass_tray", prefs.getString("layout.home.dock_design", null));
        assertTrue(prefs.getBoolean("layout.home.search_field", false));
        assertTrue(prefs.getBoolean("layout.home.drawer_tile", false));
        assertEquals("lens_beads", prefs.getString("layout.page1.dock_design", null));
        assertEquals(DockStyle.GLASS_TRAY, home(2).dockStyle());
        assertEquals(DockStyle.GLASS_TRAY, store.forVariant(LayoutVariant.HOME).dockStyle());
        assertFalse(home(2).azRowShown(PlaceOrientation.PORTRAIT));
    }

    @Test
    public void aTerminalPageKeepsItsOwnDock() {
        prefs.edit().putString("layout.page3.dock_design", "lens_beads").commit();
        PlaceLayoutStore terminal = store.forVariant(LayoutVariant.page(3, PageKind.TERMINAL));
        assertEquals(DockStyle.LENS_BEADS, terminal.dockStyle());
        terminal.setDockStyle(DockStyle.GLASS_TRAY);
        assertEquals("glass_tray", prefs.getString("layout.page3.dock_design", null));
        assertEquals("peek_glass", prefs.getString("layout.home.dock_design", null));
    }
}
