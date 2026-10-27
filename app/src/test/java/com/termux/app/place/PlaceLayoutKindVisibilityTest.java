package com.termux.app.place;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;

import com.termux.app.place.PlaceLayout.Edge;
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
 * Which elements show is a setting of the kind of page (the page model, #171): the defaults, the
 * pages' resolved layouts, the editor's writers, and the upgrade that keeps every setup looking
 * as it did.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
@ConscryptMode(ConscryptMode.Mode.OFF)
public class PlaceLayoutKindVisibilityTest {

    private static final PlaceOrientation PORTRAIT = PlaceOrientation.PORTRAIT;
    private static final PlaceOrientation LANDSCAPE = PlaceOrientation.LANDSCAPE;

    private SharedPreferences prefs;
    private TermuxAppSharedPreferences launcher;

    @Before
    public void setUp() {
        Application app = RuntimeEnvironment.getApplication();
        prefs = app.getSharedPreferences("place-layout-kind-test", Context.MODE_PRIVATE);
        prefs.edit().clear().commit();
        launcher = new TermuxAppSharedPreferences(app, prefs, null);
    }

    private PlaceLayoutStore store() {
        return new PlaceLayoutStore(launcher);
    }

    private static String dress(PageKind kind, Element element) {
        return PlaceLayoutStore.kindKey(kind, element);
    }

    // ------------------------------------------------------------------ defaults

    @Test
    public void theKeysAreDressKindElement() {
        assertEquals("place.dress.home.apps_row", dress(PageKind.HOME, Element.APPS));
        assertEquals("place.dress.terminal.extra_keys",
            dress(PageKind.TERMINAL, Element.EXTRA_KEYS));
    }

    @Test
    public void aNewInstallShowsTheStatusBarAndDockOnHomeAndTheStatusBarAndKeysOnTheTerminal() {
        PlaceLayoutStore store = store();
        for (PageKind kind : PageKind.values())
            assertTrue(kind.name(), store.kindShows(kind, Element.STATUS));
        assertTrue(store.kindShows(PageKind.HOME, Element.APPS));
        assertTrue(store.kindShows(PageKind.HOME, Element.AZ));
        assertFalse(store.kindShows(PageKind.HOME, Element.EXTRA_KEYS));
        for (PageKind kind : new PageKind[] {PageKind.TERMINAL, PageKind.DISPLAY}) {
            assertTrue(kind.name(), store.kindShows(kind, Element.EXTRA_KEYS));
            assertFalse(kind.name(), store.kindShows(kind, Element.APPS));
            assertFalse(kind.name(), store.kindShows(kind, Element.AZ));
        }
        assertFalse("a new install writes nothing",
            prefs.contains(dress(PageKind.HOME, Element.APPS)));
    }

    @Test
    public void aNewInstallsPagesResolveToTheirKinds() {
        PlaceLayoutStore store = store();
        PlaceLayout terminal = store.resolve(PORTRAIT, PaneWallPage.TERMINAL);
        assertTrue(terminal.slot(Element.APPS).hidden);
        assertTrue(terminal.slot(Element.AZ).hidden);
        assertFalse(terminal.slot(Element.EXTRA_KEYS).hidden);
        assertFalse(terminal.slot(Element.STATUS).hidden);
        PlaceLayout home = store.resolve(PORTRAIT, PaneWallPage.WIDGETS);
        assertFalse(home.slot(Element.APPS).hidden);
        assertFalse(home.slot(Element.AZ).hidden);
        assertTrue(home.slot(Element.EXTRA_KEYS).hidden);
        assertFalse(home.slot(Element.STATUS).hidden);
    }

    @Test
    public void theToolbarSwitchStillPutsTheExtraKeysAwayEverywhere() {
        PlaceLayoutStore store = store();
        store.setKindShows(PageKind.HOME, Element.EXTRA_KEYS, true);
        launcher.setShowTerminalToolbar(false);
        assertTrue(store.resolve(PORTRAIT, PaneWallPage.TERMINAL).slot(Element.EXTRA_KEYS).hidden);
        assertTrue(store.resolve(PORTRAIT, PaneWallPage.WIDGETS).slot(Element.EXTRA_KEYS).hidden);
    }

    @Test
    public void aKindsVisibilityIsOneValueForBothOrientations() {
        PlaceLayoutStore store = store();
        store.setKindShows(PageKind.TERMINAL, Element.APPS, true);
        assertFalse(store.resolve(PORTRAIT, PaneWallPage.TERMINAL).slot(Element.APPS).hidden);
        assertFalse(store.resolve(LANDSCAPE, PaneWallPage.TERMINAL).slot(Element.APPS).hidden);
        assertFalse("Home is its own kind", store.kindShows(PageKind.HOME, Element.EXTRA_KEYS));
    }

    // ------------------------------------------------------------------ the editor's writers

    @Test
    public void theEditorsSlotWritesLandOnTheKindOfTheVariantItEdits() {
        PlaceLayoutStore store = store();
        PlaceLayoutStore normal = store.forVariant(LayoutVariant.NORMAL);
        normal.setSlot(PORTRAIT, Element.APPS, normal.slot(PORTRAIT, Element.APPS).withHidden(false));
        assertTrue(store.kindShows(PageKind.TERMINAL, Element.APPS));
        assertFalse("the Home kind is its own", prefs.contains(dress(PageKind.HOME, Element.APPS)));

        PlaceLayoutStore home = store.forVariant(LayoutVariant.HOME);
        home.setSlot(PORTRAIT, Element.AZ, home.slot(PORTRAIT, Element.AZ).withHidden(true));
        assertFalse(store.kindShows(PageKind.HOME, Element.AZ));
        assertTrue(store.kindShows(PageKind.TERMINAL, Element.EXTRA_KEYS));
        assertEquals("an edge stays an arrangement", Edge.BOTTOM,
            home.slot(PORTRAIT, Element.AZ).edge);
    }

    @Test
    public void aSnapshotRestoreBringsTheKindBack() {
        PlaceLayoutStore store = store();
        PlaceLayoutStore normal = store.forVariant(LayoutVariant.NORMAL);
        Slot before = normal.slot(PORTRAIT, Element.EXTRA_KEYS);
        normal.setSlot(PORTRAIT, Element.EXTRA_KEYS, before.withHidden(true));
        assertFalse(store.kindShows(PageKind.TERMINAL, Element.EXTRA_KEYS));
        normal.setSlot(PORTRAIT, Element.EXTRA_KEYS, before);
        assertTrue(store.kindShows(PageKind.TERMINAL, Element.EXTRA_KEYS));
    }

    @Test
    public void minimalModesLayoutKeepsItsOwnVisibility() {
        PlaceLayoutStore store = store();
        PlaceLayoutStore minimal = store.forVariant(LayoutVariant.MINIMAL);
        minimal.setSlot(PORTRAIT, Element.STATUS,
            minimal.slot(PORTRAIT, Element.STATUS).withHidden(true));
        assertTrue("Terminal pages still show the bar",
            store.kindShows(PageKind.TERMINAL, Element.STATUS));
        assertTrue(minimal.resolve(PORTRAIT).slot(Element.STATUS).hidden);
        assertFalse(store.forVariant(LayoutVariant.NORMAL).resolve(PORTRAIT)
            .slot(Element.STATUS).hidden);
    }

    // ------------------------------------------------------------------ the upgrade

    /** A store at version 14 with a shared layout and a Home layout as given. */
    private PlaceLayoutStore upgrade(boolean homeLayout, boolean sharedDock, boolean homeDock) {
        SharedPreferences.Editor editor = prefs.edit().putInt("place.migrated", 14)
            .putBoolean("place.home_layout", homeLayout);
        for (PlaceOrientation o : PlaceOrientation.values()) {
            editor.putString(PlaceLayoutStore.layoutKey(LayoutVariant.NORMAL, o, "apps_row"),
                sharedDock ? "bottom" : "hidden");
            editor.putBoolean(PlaceLayoutStore.layoutKey(LayoutVariant.NORMAL, o, "az_row"),
                sharedDock);
            editor.putString(PlaceLayoutStore.layoutKey(LayoutVariant.HOME, o, "apps_row"),
                homeDock ? "top" : "hidden");
            editor.putBoolean(PlaceLayoutStore.layoutKey(LayoutVariant.HOME, o, "az_row"), homeDock);
        }
        editor.commit();
        return store();
    }

    @Test
    public void theHomeLayoutsVisibilityBecomesTheHomeKindsAndTheSharedOnesTheTerminals() {
        for (boolean sharedDock : new boolean[] {true, false}) {
            for (boolean homeDock : new boolean[] {true, false}) {
                prefs.edit().clear().commit();
                PlaceLayoutStore store = upgrade(true, sharedDock, homeDock);
                String why = "shared " + sharedDock + ", home " + homeDock;
                assertEquals(why, homeDock, store.kindShows(PageKind.HOME, Element.APPS));
                assertEquals(why, homeDock, store.kindShows(PageKind.HOME, Element.AZ));
                assertFalse(why, store.kindShows(PageKind.HOME, Element.EXTRA_KEYS));
                assertTrue(why, store.kindShows(PageKind.HOME, Element.STATUS));
                for (PageKind kind : new PageKind[] {PageKind.TERMINAL, PageKind.DISPLAY}) {
                    assertEquals(why, sharedDock, store.kindShows(kind, Element.APPS));
                    assertEquals(why, sharedDock, store.kindShows(kind, Element.AZ));
                    assertTrue(why, store.kindShows(kind, Element.EXTRA_KEYS));
                }
                assertEquals(why, PlaceLayoutStore.MIGRATION_VERSION,
                    prefs.getInt("place.migrated", 0));
            }
        }
    }

    @Test
    public void anUpgraderKeepsTheDockOnTheTerminalPage() {
        PlaceLayoutStore store = upgrade(true, true, true);
        PlaceLayout terminal = store.resolve(PORTRAIT, PaneWallPage.TERMINAL);
        assertFalse(terminal.slot(Element.APPS).hidden);
        assertFalse(terminal.slot(Element.AZ).hidden);
        assertFalse(terminal.slot(Element.EXTRA_KEYS).hidden);
        assertFalse(store.resolve(PORTRAIT, PaneWallPage.WIDGETS).slot(Element.APPS).hidden);
    }

    @Test
    public void withTheHomeLayoutOffHomeTakesTheSharedLayoutsVisibility() {
        PlaceLayoutStore store = upgrade(false, false, true);
        assertFalse(store.kindShows(PageKind.HOME, Element.APPS));
        assertFalse(store.kindShows(PageKind.TERMINAL, Element.APPS));
    }

    @Test
    public void anElementShownInEitherOrientationShows() {
        prefs.edit().putInt("place.migrated", 14).putBoolean("place.home_layout", true)
            .putString(PlaceLayoutStore.layoutKey(LayoutVariant.NORMAL, PORTRAIT, "apps_row"),
                "hidden")
            .putString(PlaceLayoutStore.layoutKey(LayoutVariant.NORMAL, LANDSCAPE, "apps_row"),
                "left")
            .commit();
        assertTrue(store().kindShows(PageKind.TERMINAL, Element.APPS));
    }

    @Test
    public void theUpgradeRunsOnceAndKeepsLaterChoices() {
        PlaceLayoutStore first = upgrade(true, true, true);
        first.setKindShows(PageKind.TERMINAL, Element.APPS, false);
        PlaceLayoutStore second = store();
        assertFalse(second.kindShows(PageKind.TERMINAL, Element.APPS));
    }

    @Test
    public void aStoreThatRanBeforeButHoldsNoLayoutKeysIsStillAnUpgrade() {
        prefs.edit().putInt("place.migrated", 6).commit();
        PlaceLayoutStore store = store();
        assertTrue("written, so nothing moves",
            prefs.contains(dress(PageKind.TERMINAL, Element.APPS)));
        assertTrue(store.kindShows(PageKind.TERMINAL, Element.APPS));
    }
}
