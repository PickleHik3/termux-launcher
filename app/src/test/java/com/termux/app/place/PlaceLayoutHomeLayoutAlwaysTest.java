package com.termux.app.place;

import static org.junit.Assert.assertEquals;
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
        return new PlaceLayoutStore(launcher);
    }

    /** The store as a build before version 14 left it, with the switch as given, then upgraded. */
    private PlaceLayoutStore upgradedFrom13(boolean homeLayoutOn) {
        SharedPreferences.Editor editor = prefs.edit().putInt(PlaceLayoutStore.KEY_MIGRATED, 13);
        if (homeLayoutOn) editor.putBoolean(PlaceLayoutStore.homeLayoutKey(), true);
        else editor.remove(PlaceLayoutStore.homeLayoutKey());
        editor.commit();
        return new PlaceLayoutStore(launcher);
    }

    @Test
    public void aHomeOnTheSharedLayoutGetsACopyOfItSoNothingMoves() {
        PlaceLayoutStore seed = current();
        PlaceLayoutStore normal = seed.forVariant(LayoutVariant.NORMAL);
        normal.setSlot(PlaceOrientation.PORTRAIT, Element.APPS, Slot.on(Edge.LEFT, 0));
        normal.setSlot(PlaceOrientation.PORTRAIT, Element.STATUS, Slot.hiddenFrom(Edge.TOP, 0));
        normal.setSlot(PlaceOrientation.LANDSCAPE, Element.AZ, Slot.hiddenFrom(Edge.RIGHT, 1));
        normal.setKeyboardChinDp(PlaceOrientation.PORTRAIT, 24);
        normal.setDockStyle(DockStyle.CHOICES.get(1));
        // A Home layout once turned on, edited and turned off again leaves a stale copy behind.
        seed.forVariant(LayoutVariant.HOME).setSlot(PlaceOrientation.PORTRAIT, Element.APPS,
            Slot.on(Edge.RIGHT, 0));

        PlaceLayoutStore upgraded = upgradedFrom13(false);

        assertTrue(upgraded.hasHomeLayout());
        assertEquals(LayoutVariant.HOME,
            DressResolver.of(upgraded).layoutOf(PaneWallPage.WIDGETS));
        PlaceLayoutStore from = upgraded.forVariant(LayoutVariant.NORMAL);
        PlaceLayoutStore home = upgraded.forVariant(LayoutVariant.HOME);
        for (PlaceOrientation orientation : PlaceOrientation.values()) {
            for (Element element : Element.values()) {
                if (!DressResolver.wornOn(LayoutVariant.HOME, element)) continue;
                assertEquals(orientation + " " + element, from.slot(orientation, element),
                    home.slot(orientation, element));
            }
        }
        assertTrue("the hidden stack comes along",
            home.slot(PlaceOrientation.PORTRAIT, Element.STATUS).hidden);
        assertTrue(home.slot(PlaceOrientation.LANDSCAPE, Element.AZ).hidden);
        assertEquals("Bottom padding is one value on both screens", 24,
            home.keyboardChinDp(PlaceOrientation.PORTRAIT));
        assertEquals(DockStyle.CHOICES.get(1), home.dockStyle());
    }

    @Test
    public void aHomeLayoutAlreadyOnIsLeftAsItIs() {
        PlaceLayoutStore seed = current();
        seed.forVariant(LayoutVariant.NORMAL).setSlot(PlaceOrientation.PORTRAIT, Element.APPS,
            Slot.on(Edge.LEFT, 0));
        seed.forVariant(LayoutVariant.HOME).setSlot(PlaceOrientation.PORTRAIT, Element.APPS,
            Slot.on(Edge.BOTTOM, 0));

        PlaceLayoutStore upgraded = upgradedFrom13(true);

        assertTrue(upgraded.hasHomeLayout());
        assertEquals(Edge.BOTTOM, upgraded.forVariant(LayoutVariant.HOME)
            .slot(PlaceOrientation.PORTRAIT, Element.APPS).edge);
    }

    @Test
    public void aNewInstallHoldsAHomeLayoutFromTheStart() {
        PlaceLayoutStore fresh = new PlaceLayoutStore(launcher);
        assertTrue(fresh.hasHomeLayout());
        assertEquals(PlaceLayoutStore.MIGRATION_VERSION,
            prefs.getInt(PlaceLayoutStore.KEY_MIGRATED, 0));
        assertEquals(LayoutVariant.HOME, DressResolver.of(fresh).layoutOf(PaneWallPage.WIDGETS));
    }
}
