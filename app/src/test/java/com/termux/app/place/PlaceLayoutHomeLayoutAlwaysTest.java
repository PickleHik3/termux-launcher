package com.termux.app.place;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;

import com.termux.app.chrome.DressResolver;
import com.termux.app.dock.DockStyle;
import com.termux.app.place.PlaceLayout.Edge;
import com.termux.app.wall.PaneWallPage;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.ConscryptMode;

/**
 * Version 14 of the migration (editor-redesign §9): the Own layout switch is gone, so every
 * install holds a Home layout. A Home that wore the shared layout gets a copy of the Workspace's,
 * hidden stack and Dock design included, and reads the keyboard's chin (Bottom padding) linked,
 * so nothing on screen moves.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
@ConscryptMode(ConscryptMode.Mode.OFF)
public class PlaceLayoutHomeLayoutAlwaysTest {

    private SharedPreferences prefs;
    private TermuxAppSharedPreferences launcher;

    @Before
    public void setUp() {
        Application app = RuntimeEnvironment.getApplication();
        prefs = app.getSharedPreferences("place-layout-home-always-test", Context.MODE_PRIVATE);
        prefs.edit().clear().commit();
        launcher = new TermuxAppSharedPreferences(app, prefs, null);
    }

    /** A store already at this version, to arrange through the accessors before rewinding. */
    private PlaceLayoutStore current() {
        prefs.edit().putInt(PlaceLayoutStore.KEY_MIGRATED, PlaceLayoutStore.MIGRATION_VERSION)
            .commit();
        return routed(new PlaceLayoutStore(launcher));
    }

    /** The store as a build before version 14 left it, with the switch as given, then upgraded. */
    private PlaceLayoutStore upgradedFrom13(boolean homeLayoutOn) {
        SharedPreferences.Editor editor = prefs.edit().putInt(PlaceLayoutStore.KEY_MIGRATED, 13);
        if (homeLayoutOn) editor.putBoolean(PlaceLayoutStore.homeLayoutKey(), true);
        else editor.remove(PlaceLayoutStore.homeLayoutKey());
        editor.commit();
        return routed(new PlaceLayoutStore(launcher));
    }

    @Test
    public void aHomeLayoutAlreadyOnIsLeftAsItIs() {
        PlaceLayoutStore seed = current();
        seed.forVariant(LayoutVariant.NORMAL).setSlot(PlaceOrientation.PORTRAIT, Element.APPS,
            Slot.on(Edge.LEFT, 0));
        seed.forVariant(LayoutVariant.HOME).setSlot(PlaceOrientation.PORTRAIT, Element.APPS,
            Slot.on(Edge.BOTTOM, 0));

        PlaceLayoutStore upgraded = KindFixtures.onHomePage(upgradedFrom13(true));

        // A Home layout that differs lives on as the Home page's own (version 16).
        assertFalse(upgraded.hasHomeLayout());
        assertEquals(KindFixtures.HOME_PAGE,
            DressResolver.of(upgraded).layoutOf(PaneWallPage.WIDGETS));
        assertEquals(Edge.BOTTOM, upgraded.forVariant(KindFixtures.HOME_PAGE)
            .slot(PlaceOrientation.PORTRAIT, Element.APPS).edge);
    }

    @Test
    public void aNewInstallStartsWithHomeOnTheSharedLayout() {
        PlaceLayoutStore fresh = KindFixtures.onHomePage(routed(new PlaceLayoutStore(launcher)));
        assertFalse(fresh.hasHomeLayout());
        assertEquals(PlaceLayoutStore.MIGRATION_VERSION,
            prefs.getInt(PlaceLayoutStore.KEY_MIGRATED, 0));
        assertEquals(LayoutVariant.HOME, DressResolver.of(fresh).layoutOf(PaneWallPage.WIDGETS));
    }

    /** Reads and writes of layout keys go where the store keeps them now: the documents (ADR 0020). */
    private PlaceLayoutStore routed(PlaceLayoutStore store) {
        android.content.SharedPreferences view = store.layoutPreferences();
        if (view != null) prefs = view;
        return store;
    }
}
