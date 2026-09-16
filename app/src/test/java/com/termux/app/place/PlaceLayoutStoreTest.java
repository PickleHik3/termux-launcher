package com.termux.app.place;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;

import com.termux.app.place.PlaceLayout.Edge;
import com.termux.app.place.PlaceLayout.KeyboardForm;
import com.termux.app.place.PlaceLayout.KeyboardMode;
import com.termux.app.place.PlaceLayout.RowPlacement;
import com.termux.app.wall.PaneWallPage;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;
import com.termux.shared.termux.settings.preferences.TermuxPreferenceConstants.TERMUX_APP;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.ConscryptMode;

/**
 * The per-place layout store: what each place resolves to before anything is written, what the old
 * global keys become, and that a scoped write is what the place reads back.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
@ConscryptMode(ConscryptMode.Mode.OFF)
public class PlaceLayoutStoreTest {

    private Application app;
    private SharedPreferences prefs;
    private TermuxAppSharedPreferences launcher;

    @Before
    public void setUp() {
        app = RuntimeEnvironment.getApplication();
        prefs = app.getSharedPreferences("place-layout-store-test", Context.MODE_PRIVATE);
        prefs.edit().clear().commit();
        launcher = new TermuxAppSharedPreferences(app, prefs, null);
    }

    private PlaceLayoutStore store() {
        return new PlaceLayoutStore(launcher);
    }

    // ------------------------------------------------------------------ defaults

    @Test
    public void freshInstallResolvesTheArrangementEveryPlaceAlreadyHad() {
        PlaceLayoutStore store = store();
        for (PaneWallPage place : PaneWallPage.values()) {
            PlaceLayout portrait = store.resolve(place, PlaceOrientation.PORTRAIT);
            assertEquals(place + " portrait status", Edge.TOP, portrait.slot(Element.STATUS).edge);
            assertEquals(place + " portrait apps", RowPlacement.BOTTOM, rowOf(portrait, com.termux.app.place.Element.APPS));
            assertTrue(place + " portrait az", (!portrait.slot(Element.AZ).hidden));
            assertEquals(place + " portrait keys", RowPlacement.BOTTOM, rowOf(portrait, com.termux.app.place.Element.EXTRA_KEYS));
            assertEquals(place + " portrait keyboard", KeyboardMode.RESIZE, portrait.keyboardMode);
            assertEquals(place + " portrait columns", 4, portrait.widgetColumns);
            assertEquals(place + " portrait rows", 5, portrait.widgetRows);

            // Landscape stands the pinned apps on the left edge: today's rail.
            PlaceLayout landscape = store.resolve(place, PlaceOrientation.LANDSCAPE);
            assertEquals(place + " landscape apps", RowPlacement.LEFT, rowOf(landscape, com.termux.app.place.Element.APPS));
            assertEquals(place + " landscape keys", RowPlacement.BOTTOM, rowOf(landscape, com.termux.app.place.Element.EXTRA_KEYS));
        }
    }

    @Test
    public void onlyTheDisplayFloatsTheKeyboardAndOnlyInLandscape() {
        PlaceLayoutStore store = store();
        assertEquals(KeyboardMode.OVERLAY,
            store.resolve(PaneWallPage.DISPLAY, PlaceOrientation.LANDSCAPE).keyboardMode);
        assertEquals(KeyboardMode.RESIZE,
            store.resolve(PaneWallPage.DISPLAY, PlaceOrientation.PORTRAIT).keyboardMode);
        assertEquals(KeyboardMode.RESIZE,
            store.resolve(PaneWallPage.TERMINAL, PlaceOrientation.LANDSCAPE).keyboardMode);
    }

    // ------------------------------------------------------------------ the shared layer

    @Test
    public void aMissingScopedKeyFallsBackToTheSharedValue() {
        launcher.setAppLauncherWidgetGridColumns(6);
        launcher.setAppLauncherWidgetGridRows(7);
        launcher.setAppLauncherAzRowEnabled(false);
        PlaceLayoutStore store = store();
        PlaceLayout home = store.resolve(PaneWallPage.WIDGETS, PlaceOrientation.PORTRAIT);
        assertEquals(6, home.widgetColumns);
        assertEquals(7, home.widgetRows);
        assertFalse((!home.slot(Element.AZ).hidden));
    }

    @Test
    public void aMasterSwitchedOffBeforeTheMigrationIsHiddenForEveryPlaceThenStopsGating() {
        launcher.setAppLauncherAppsRowEnabled(false);
        launcher.setAppLauncherExtraKeysRowEnabled(false);
        PlaceLayoutStore store = store();
        for (PaneWallPage place : PaneWallPage.values()) {
            for (PlaceOrientation orientation : PlaceOrientation.values()) {
                PlaceLayout layout = store.resolve(place, orientation);
                assertEquals(place + " " + orientation, RowPlacement.HIDDEN, rowOf(layout, com.termux.app.place.Element.APPS));
                assertEquals(place + " " + orientation, RowPlacement.HIDDEN, rowOf(layout, com.termux.app.place.Element.EXTRA_KEYS));
            }
        }
        // The migration folded the master into Hidden once; a scoped write afterwards is a real
        // placement, not a value the master can still veto.
        store.setAppsRow(PaneWallPage.TERMINAL, PlaceOrientation.LANDSCAPE, RowPlacement.RIGHT);
        assertEquals(RowPlacement.RIGHT,
            rowOf(store.resolve(PaneWallPage.TERMINAL, PlaceOrientation.LANDSCAPE), com.termux.app.place.Element.APPS));
    }

    @Test
    public void placingTheExtraKeysLiftsTheToolbarToggleThatHidThem() {
        launcher.setShowTerminalToolbar(false);
        PlaceLayoutStore store = store();
        assertEquals(RowPlacement.HIDDEN,
            rowOf(store.resolve(PaneWallPage.TERMINAL, PlaceOrientation.PORTRAIT), com.termux.app.place.Element.EXTRA_KEYS));

        store.setExtraKeys(PaneWallPage.TERMINAL, PlaceOrientation.PORTRAIT, RowPlacement.BOTTOM);
        assertTrue("a placement is a request to see them", launcher.shouldShowTerminalToolbar());
        assertEquals(RowPlacement.BOTTOM,
            rowOf(store.resolve(PaneWallPage.TERMINAL, PlaceOrientation.PORTRAIT), com.termux.app.place.Element.EXTRA_KEYS));

        // Hiding them again is a placement of its own and leaves the toggle alone.
        store.setExtraKeys(PaneWallPage.TERMINAL, PlaceOrientation.PORTRAIT, RowPlacement.HIDDEN);
        assertTrue(launcher.shouldShowTerminalToolbar());
        assertEquals(RowPlacement.HIDDEN,
            rowOf(store.resolve(PaneWallPage.TERMINAL, PlaceOrientation.PORTRAIT), com.termux.app.place.Element.EXTRA_KEYS));
    }

    // ------------------------------------------------------------------ scoped writes

    @Test
    public void aScopedWriteWinsAndReachesNoOtherPlaceOrOrientation() {
        PlaceLayoutStore store = store();
        store.setExtraKeys(PaneWallPage.DISPLAY, PlaceOrientation.LANDSCAPE, RowPlacement.RIGHT);
        assertEquals(RowPlacement.RIGHT,
            rowOf(store.resolve(PaneWallPage.DISPLAY, PlaceOrientation.LANDSCAPE), com.termux.app.place.Element.EXTRA_KEYS));
        assertEquals(RowPlacement.BOTTOM,
            rowOf(store.resolve(PaneWallPage.DISPLAY, PlaceOrientation.PORTRAIT), com.termux.app.place.Element.EXTRA_KEYS));
        assertEquals(RowPlacement.BOTTOM,
            rowOf(store.resolve(PaneWallPage.TERMINAL, PlaceOrientation.LANDSCAPE), com.termux.app.place.Element.EXTRA_KEYS));
    }

    @Test
    public void everyFieldRoundTripsThroughItsOwnKey() {
        PlaceLayoutStore store = store();
        PaneWallPage place = PaneWallPage.WIDGETS;
        PlaceOrientation orientation = PlaceOrientation.LANDSCAPE;
        store.setStatusBarEdge(place, orientation, Edge.LEFT);
        store.setAppsRow(place, orientation, RowPlacement.HIDDEN);
        store.setAzRowShown(place, orientation, false);
        store.setAzBarEdge(place, orientation, Edge.RIGHT);
        store.setExtraKeys(place, orientation, RowPlacement.LEFT);
        store.setKeyboardMode(place, orientation, KeyboardMode.OVERLAY);
        store.setWidgetColumns(place, orientation, 6);
        store.setWidgetRows(place, orientation, 3);

        PlaceLayout layout = store.resolve(place, orientation);
        assertEquals(new PlaceLayout(Edge.LEFT, RowPlacement.HIDDEN, false, Edge.RIGHT,
            RowPlacement.LEFT, KeyboardMode.OVERLAY, KeyboardForm.DOCKED, 6, 3), layout);
        assertTrue(layout.toString().contains("grid=6x3"));
        assertNotEquals(layout, store.resolve(place, PlaceOrientation.PORTRAIT));
    }

    @Test
    public void aGridBeyondWhatTheGridCanLayOutIsClamped() {
        PlaceLayoutStore store = store();
        store.setWidgetColumns(PaneWallPage.WIDGETS, PlaceOrientation.PORTRAIT, 99);
        store.setWidgetRows(PaneWallPage.WIDGETS, PlaceOrientation.PORTRAIT, 0);
        PlaceLayout home = store.resolve(PaneWallPage.WIDGETS, PlaceOrientation.PORTRAIT);
        assertEquals(8, home.widgetColumns);
        assertEquals(2, home.widgetRows);
    }

    @Test
    public void clearingPutsOneOrientationBackAndLeavesTheOtherAlone() {
        PlaceLayoutStore store = store();
        store.setAppsRow(PaneWallPage.TERMINAL, PlaceOrientation.PORTRAIT, RowPlacement.RIGHT);
        store.setAppsRow(PaneWallPage.TERMINAL, PlaceOrientation.LANDSCAPE, RowPlacement.RIGHT);
        store.setStatusBarEdge(PaneWallPage.TERMINAL, PlaceOrientation.PORTRAIT, Edge.BOTTOM);
        store.setAzBarEdge(PaneWallPage.TERMINAL, PlaceOrientation.LANDSCAPE, Edge.LEFT);

        store.clear(PaneWallPage.TERMINAL, PlaceOrientation.PORTRAIT);
        PlaceLayout portrait = store.resolve(PaneWallPage.TERMINAL, PlaceOrientation.PORTRAIT);
        assertEquals(RowPlacement.BOTTOM, rowOf(portrait, com.termux.app.place.Element.APPS));
        assertEquals(Edge.TOP, portrait.slot(Element.STATUS).edge);
        assertEquals(RowPlacement.RIGHT,
            rowOf(store.resolve(PaneWallPage.TERMINAL, PlaceOrientation.LANDSCAPE), com.termux.app.place.Element.APPS));
        // az_bar is in ARRANGEMENT_KEYS but for the untouched orientation, so it survives the clear.
        assertEquals(Edge.LEFT,
            store.resolve(PaneWallPage.TERMINAL, PlaceOrientation.LANDSCAPE).slot(Element.AZ).edge);
    }

    @Test
    public void azBarEdgeDefaultsToBottomAndStandsOnEveryEdge() {
        PlaceLayoutStore store = store();
        assertEquals(Edge.BOTTOM,
            store.resolve(PaneWallPage.TERMINAL, PlaceOrientation.PORTRAIT).slot(Element.AZ).edge);
        assertEquals(Edge.BOTTOM,
            store.resolve(PaneWallPage.TERMINAL, PlaceOrientation.LANDSCAPE).slot(Element.AZ).edge);

        store.setAzBarEdge(PaneWallPage.TERMINAL, PlaceOrientation.PORTRAIT, Edge.LEFT);
        store.setAzBarEdge(PaneWallPage.TERMINAL, PlaceOrientation.LANDSCAPE, Edge.LEFT);
        assertEquals("portrait stands a column of its own now", Edge.LEFT,
            store.resolve(PaneWallPage.TERMINAL, PlaceOrientation.PORTRAIT).slot(Element.AZ).edge);
        assertEquals(Edge.LEFT,
            store.resolve(PaneWallPage.TERMINAL, PlaceOrientation.LANDSCAPE).slot(Element.AZ).edge);

        store.setAzBarEdge(PaneWallPage.TERMINAL, PlaceOrientation.PORTRAIT, Edge.TOP);
        assertEquals(Edge.TOP,
            store.resolve(PaneWallPage.TERMINAL, PlaceOrientation.PORTRAIT).slot(Element.AZ).edge);
    }

    @Test
    public void clearingRemovesTheAzBarEdge() {
        PlaceLayoutStore store = store();
        store.setAzBarEdge(PaneWallPage.TERMINAL, PlaceOrientation.LANDSCAPE, Edge.RIGHT);
        store.clear(PaneWallPage.TERMINAL, PlaceOrientation.LANDSCAPE);
        assertEquals(Edge.BOTTOM,
            store.resolve(PaneWallPage.TERMINAL, PlaceOrientation.LANDSCAPE).slot(Element.AZ).edge);
    }

    @Test
    public void everyWriteMovesTheRevisionSoACachedLayoutIsRetired() {
        PlaceLayoutStore store = store();
        int before = store.revision();
        store.setAppsRow(PaneWallPage.TERMINAL, PlaceOrientation.PORTRAIT, RowPlacement.LEFT);
        assertNotEquals(before, store.revision());
    }

    // ------------------------------------------------------------------ memory

    @Test
    public void eachPlaceRemembersItsOwnStatusBarAndKeyboard() {
        PlaceLayoutStore store = store();
        // Every place starts compact, the way the launcher's one status bar always has.
        assertTrue(store.isStatusCompact(PaneWallPage.TERMINAL));
        assertTrue(store.isStatusCompact(PaneWallPage.WIDGETS));

        store.setStatusCompact(PaneWallPage.TERMINAL, false);
        store.setKeyboardOpen(PaneWallPage.DISPLAY, true);

        assertFalse(store.isStatusCompact(PaneWallPage.TERMINAL));
        assertTrue(store.isStatusCompact(PaneWallPage.WIDGETS));
        assertTrue(store.wasKeyboardOpen(PaneWallPage.DISPLAY));
        assertFalse(store.wasKeyboardOpen(PaneWallPage.TERMINAL));
    }

    @Test
    public void theWidgetGridComesBackClosedAndEverywhereElseAsItWasLeft() {
        PlaceLayoutStore store = store();
        assertEquals(KeyboardOnEnter.CLOSED, store.keyboardOnEnter(PaneWallPage.WIDGETS));
        assertEquals(KeyboardOnEnter.AS_LEFT, store.keyboardOnEnter(PaneWallPage.TERMINAL));
        assertEquals(KeyboardOnEnter.AS_LEFT, store.keyboardOnEnter(PaneWallPage.DISPLAY));

        store.setKeyboardOnEnter(PaneWallPage.DISPLAY, KeyboardOnEnter.OPEN);
        assertEquals(KeyboardOnEnter.OPEN, store.keyboardOnEnter(PaneWallPage.DISPLAY));
        assertEquals(KeyboardOnEnter.AS_LEFT, store.keyboardOnEnter(PaneWallPage.TERMINAL));
    }

    // ------------------------------------------------------------------ migration

    @Test
    public void theOldGlobalKeysAreFoldedIntoTheScopedOnesExactlyOnce() {
        prefs.edit()
            .putString("app_launcher_dock_rail_side", "right")
            .putString("x11_extra_keys_side", "left")
            .putBoolean("x11_hide_status_bar", true)
            .putBoolean("x11_keyboard_shown", true)
            // Expanded, which is not the shipped default: only the migration can produce it.
            .putBoolean("top_pane_clock_collapsed", false)
            .commit();

        PlaceLayoutStore store = store();
        for (PaneWallPage place : PaneWallPage.values()) {
            assertEquals(place + " landscape apps", RowPlacement.RIGHT,
                rowOf(store.resolve(place, PlaceOrientation.LANDSCAPE), com.termux.app.place.Element.APPS));
            assertFalse(place + " status", store.isStatusCompact(place));
        }
        // The old side is folded into both orientations, and portrait stands a column now too.
        assertEquals(RowPlacement.LEFT,
            rowOf(store.resolve(PaneWallPage.DISPLAY, PlaceOrientation.LANDSCAPE), com.termux.app.place.Element.EXTRA_KEYS));
        assertEquals(RowPlacement.LEFT,
            rowOf(store.resolve(PaneWallPage.DISPLAY, PlaceOrientation.PORTRAIT), com.termux.app.place.Element.EXTRA_KEYS));
        assertTrue(store.wasKeyboardOpen(PaneWallPage.DISPLAY));
        // There is no hidden status bar any more, and the display's keyboard memory has moved.
        assertFalse(prefs.contains("x11_hide_status_bar"));
        assertFalse(prefs.contains("x11_keyboard_shown"));
        assertEquals(4, prefs.getInt("place.migrated", 0));

        // A second store over the same preferences must not fold anything again: the user's own
        // choices since the migration stand.
        store.setAppsRow(PaneWallPage.TERMINAL, PlaceOrientation.LANDSCAPE, RowPlacement.LEFT);
        store.setStatusCompact(PaneWallPage.TERMINAL, true);
        PlaceLayoutStore reopened = store();
        assertEquals(RowPlacement.LEFT,
            rowOf(reopened.resolve(PaneWallPage.TERMINAL, PlaceOrientation.LANDSCAPE), com.termux.app.place.Element.APPS));
        assertTrue(reopened.isStatusCompact(PaneWallPage.TERMINAL));
        assertFalse(reopened.isStatusCompact(PaneWallPage.WIDGETS));
    }

    @Test
    public void reachingVersionTwoFromVersionOneOnlyRunsTheStepAddedSince() {
        // An install already migrated to version 1 keeps its own scoped choice — re-running
        // version 1's fold would stomp it with the legacy global it was migrated away from.
        prefs.edit()
            .putInt("place.migrated", 1)
            .putString("app_launcher_dock_rail_side", "right")
            .putString("place.terminal.landscape.apps_row", "left")
            .commit();
        launcher.setAppLauncherExtraKeysRowEnabled(false);

        PlaceLayoutStore store = store();
        assertEquals(RowPlacement.LEFT,
            rowOf(store.resolve(PaneWallPage.TERMINAL, PlaceOrientation.LANDSCAPE), com.termux.app.place.Element.APPS));
        // Version 2 still runs: the extra-keys master was off, so it folds to Hidden everywhere.
        assertEquals(RowPlacement.HIDDEN,
            rowOf(store.resolve(PaneWallPage.WIDGETS, PlaceOrientation.PORTRAIT), com.termux.app.place.Element.EXTRA_KEYS));
        assertEquals(4, prefs.getInt("place.migrated", 0));
    }

    @Test
    public void aFreshInstallHasNothingToFoldAndSaysSo() {
        PlaceLayoutStore store = store();
        assertEquals(4, prefs.getInt("place.migrated", 0));
        assertFalse(prefs.contains("place.terminal.landscape.apps_row"));
        assertEquals(RowPlacement.LEFT,
            rowOf(store.resolve(PaneWallPage.TERMINAL, PlaceOrientation.LANDSCAPE), com.termux.app.place.Element.APPS));
    }

    @Test
    public void theKeysAreScopedTheWayTheSpecNamesThem() {
        assertEquals("place.home.portrait.apps_row",
            PlaceLayoutStore.arrangementKey(PaneWallPage.WIDGETS, PlaceOrientation.PORTRAIT,
                "apps_row"));
        assertEquals("place.display.landscape.extra_keys",
            PlaceLayoutStore.arrangementKey(PaneWallPage.DISPLAY, PlaceOrientation.LANDSCAPE,
                "extra_keys"));
        assertEquals("place.terminal.status_compact",
            PlaceLayoutStore.memoryKey(PaneWallPage.TERMINAL, "status_compact"));
    }


    @Test
    public void portraitKeepsASideStoredForIt() {
        // Portrait used to refuse a column and read a side back as the top. Every edge stands in
        // both orientations now; a canvas too narrow for a column is the Layout editor's to warn
        // about rather than the store's to overrule.
        PlaceLayoutStore store = store();
        store.setStatusBarEdge(PaneWallPage.TERMINAL, PlaceOrientation.PORTRAIT, Edge.RIGHT);
        store.setStatusBarEdge(PaneWallPage.TERMINAL, PlaceOrientation.LANDSCAPE, Edge.RIGHT);

        assertEquals(Edge.RIGHT,
            store.resolve(PaneWallPage.TERMINAL, PlaceOrientation.PORTRAIT).slot(Element.STATUS).edge);
        assertEquals(Edge.RIGHT,
            store.resolve(PaneWallPage.TERMINAL, PlaceOrientation.LANDSCAPE).slot(Element.STATUS).edge);
        store.setStatusBarEdge(PaneWallPage.TERMINAL, PlaceOrientation.PORTRAIT, Edge.BOTTOM);
        assertEquals(Edge.BOTTOM,
            store.resolve(PaneWallPage.TERMINAL, PlaceOrientation.PORTRAIT).slot(Element.STATUS).edge);
    }

    @Test
    public void portraitKeepsASideRowStoredForIt() {
        // The apps row and the extra keys stand in a column in either orientation now, and hidden
        // still stays hidden.
        PlaceLayoutStore store = store();
        store.setAppsRow(PaneWallPage.TERMINAL, PlaceOrientation.PORTRAIT, RowPlacement.LEFT);
        store.setExtraKeys(PaneWallPage.TERMINAL, PlaceOrientation.PORTRAIT, RowPlacement.RIGHT);
        store.setAppsRow(PaneWallPage.TERMINAL, PlaceOrientation.LANDSCAPE, RowPlacement.RIGHT);
        store.setExtraKeys(PaneWallPage.DISPLAY, PlaceOrientation.PORTRAIT, RowPlacement.HIDDEN);

        PlaceLayout portrait = store.resolve(PaneWallPage.TERMINAL, PlaceOrientation.PORTRAIT);
        assertEquals(RowPlacement.LEFT, rowOf(portrait, com.termux.app.place.Element.APPS));
        assertEquals(RowPlacement.RIGHT, rowOf(portrait, com.termux.app.place.Element.EXTRA_KEYS));
        assertEquals(RowPlacement.RIGHT,
            rowOf(store.resolve(PaneWallPage.TERMINAL, PlaceOrientation.LANDSCAPE), com.termux.app.place.Element.APPS));
        assertEquals(RowPlacement.HIDDEN,
            rowOf(store.resolve(PaneWallPage.DISPLAY, PlaceOrientation.PORTRAIT), com.termux.app.place.Element.EXTRA_KEYS));
    }

    // ------------------------------------------------------------------ slots and stack order

    @Test
    public void everySlotReadsBackTheKeyItHasAlwaysBeenStoredUnder() {
        PlaceLayoutStore store = store();
        assertEquals("place.terminal.landscape.status_bar", PlaceLayoutStore.arrangementKey(
            PaneWallPage.TERMINAL, PlaceOrientation.LANDSCAPE, Element.STATUS.storageKey()));
        assertEquals("place.terminal.landscape.apps_row", PlaceLayoutStore.arrangementKey(
            PaneWallPage.TERMINAL, PlaceOrientation.LANDSCAPE, Element.APPS.storageKey()));
        assertEquals("place.terminal.landscape.az_bar", PlaceLayoutStore.arrangementKey(
            PaneWallPage.TERMINAL, PlaceOrientation.LANDSCAPE, Element.AZ.storageKey()));
        assertEquals("place.terminal.landscape.extra_keys", PlaceLayoutStore.arrangementKey(
            PaneWallPage.TERMINAL, PlaceOrientation.LANDSCAPE, Element.EXTRA_KEYS.storageKey()));
        assertEquals("place.home.portrait.apps_row_order", PlaceLayoutStore.arrangementKey(
            PaneWallPage.WIDGETS, PlaceOrientation.PORTRAIT,
            PlaceLayoutStore.orderKeyName(Element.APPS)));

        // Written the old way, read back as a slot.
        store.setAppsRow(PaneWallPage.TERMINAL, PlaceOrientation.PORTRAIT, RowPlacement.RIGHT);
        assertEquals(new Slot(false, Edge.RIGHT, Element.APPS.defaultOrder(Edge.RIGHT)),
            store.slot(PaneWallPage.TERMINAL, PlaceOrientation.PORTRAIT, Element.APPS));
        // Written as a slot, read back the old way.
        store.setSlot(PaneWallPage.TERMINAL, PlaceOrientation.PORTRAIT, Element.EXTRA_KEYS,
            new Slot(false, Edge.LEFT, 1));
        assertEquals(RowPlacement.LEFT,
            store.extraKeys(PaneWallPage.TERMINAL, PlaceOrientation.PORTRAIT));
        assertEquals("left", prefs.getString("place.terminal.portrait.extra_keys", null));
        assertEquals(1, prefs.getInt("place.terminal.portrait.extra_keys_order", -1));
    }

    @Test
    public void aStoredPlacementWidensToEveryEdgeAndToHidden() {
        // The four spellings the rows have always been stored as still read, in both orientations,
        // and "top" — which the old three-way placement had no room for — reads as the top edge.
        for (String value : new String[] {"bottom", "left", "right", "top", "hidden"}) {
            for (PlaceOrientation orientation : PlaceOrientation.values()) {
                prefs.edit().putString("place.terminal." + orientation.storageValue() + ".apps_row",
                    value).commit();
                Slot slot = store().slot(PaneWallPage.TERMINAL, orientation, Element.APPS);
                if ("hidden".equals(value)) {
                    assertTrue(value, slot.hidden);
                    assertEquals(value, Edge.BOTTOM, slot.edge);
                } else {
                    assertFalse(value, slot.hidden);
                    assertEquals(value, Edge.parse(value, Edge.TOP), slot.edge);
                }
            }
        }
        // A value nobody recognises still falls back to what the place has always shown.
        prefs.edit().putString("place.terminal.landscape.apps_row", "sideways").commit();
        assertEquals(Edge.LEFT,
            store().slot(PaneWallPage.TERMINAL, PlaceOrientation.LANDSCAPE, Element.APPS).edge);
    }

    @Test
    public void anAbsentOrderKeyIsTheStackTheLauncherAlreadyDraws() {
        PlaceLayoutStore store = store();
        for (PaneWallPage place : PaneWallPage.values()) {
            PlaceLayout portrait = store.resolve(place, PlaceOrientation.PORTRAIT);
            // Portrait ships the status bar on the top and the three rows on the bottom.
            assertEquals(place + " status", new Slot(false, Edge.TOP, 0),
                portrait.slot(Element.STATUS));
            assertEquals(place + " keys", new Slot(false, Edge.BOTTOM, 0),
                portrait.slot(Element.EXTRA_KEYS));
            assertEquals(place + " az", new Slot(false, Edge.BOTTOM, 1), portrait.slot(Element.AZ));
            assertEquals(place + " apps", new Slot(false, Edge.BOTTOM, 2),
                portrait.slot(Element.APPS));
            assertEquals(place + " bottom stack",
                java.util.Arrays.asList(Element.EXTRA_KEYS, Element.AZ, Element.APPS),
                EdgeStackPolicy.stack(portrait, Edge.BOTTOM));

            // Landscape ships the pinned apps as the left rail, so they take the rail's band.
            PlaceLayout landscape = store.resolve(place, PlaceOrientation.LANDSCAPE);
            assertEquals(place + " rail", new Slot(false, Edge.LEFT, 1),
                landscape.slot(Element.APPS));
            assertEquals(place + " left stack", java.util.Arrays.asList(Element.APPS),
                EdgeStackPolicy.stack(landscape, Edge.LEFT));
        }
        assertFalse("nothing is written until something is re-ordered",
            prefs.contains("place.terminal.portrait.apps_row_order"));
    }

    @Test
    public void anOrderFollowsTheEdgeTheElementIsMovedTo() {
        PlaceLayoutStore store = store();
        // No order of its own: the default is read against whichever edge it is standing on.
        assertEquals(Element.AZ.defaultOrder(Edge.BOTTOM),
            store.slotOrder(PaneWallPage.TERMINAL, PlaceOrientation.PORTRAIT, Element.AZ));
        store.setAzBarEdge(PaneWallPage.TERMINAL, PlaceOrientation.PORTRAIT, Edge.RIGHT);
        assertEquals(Element.AZ.defaultOrder(Edge.RIGHT),
            store.slotOrder(PaneWallPage.TERMINAL, PlaceOrientation.PORTRAIT, Element.AZ));

        store.setSlotOrder(PaneWallPage.TERMINAL, PlaceOrientation.PORTRAIT, Element.AZ, 0);
        assertEquals(0,
            store.slotOrder(PaneWallPage.TERMINAL, PlaceOrientation.PORTRAIT, Element.AZ));
        assertEquals("the other orientation is untouched", Element.AZ.defaultOrder(Edge.BOTTOM),
            store.slotOrder(PaneWallPage.TERMINAL, PlaceOrientation.LANDSCAPE, Element.AZ));
    }

    @Test
    public void clearingPutsAReorderBackToo() {
        PlaceLayoutStore store = store();
        store.setSlot(PaneWallPage.TERMINAL, PlaceOrientation.LANDSCAPE, Element.EXTRA_KEYS,
            new Slot(false, Edge.RIGHT, 0));
        assertTrue(prefs.contains("place.terminal.landscape.extra_keys_order"));
        store.clear(PaneWallPage.TERMINAL, PlaceOrientation.LANDSCAPE);
        assertFalse(prefs.contains("place.terminal.landscape.extra_keys_order"));
        assertEquals(Element.EXTRA_KEYS.defaultOrder(Edge.BOTTOM),
            store.slotOrder(PaneWallPage.TERMINAL, PlaceOrientation.LANDSCAPE,
                Element.EXTRA_KEYS));
    }

    @Test
    public void reachingVersionFourWritesNothingButTheVersion() {
        // Every placement key keeps its value and no order key appears: an install that was on
        // version three renders exactly as it did.
        prefs.edit()
            .putInt("place.migrated", 3)
            .putString("place.terminal.landscape.apps_row", "right")
            .putString("place.home.portrait.status_bar", "bottom")
            .commit();
        java.util.Map<String, ?> before = new java.util.HashMap<>(prefs.getAll());

        store();

        java.util.Map<String, ?> after = prefs.getAll();
        assertEquals(before.size() + 0, after.size());
        for (java.util.Map.Entry<String, ?> entry : before.entrySet()) {
            if ("place.migrated".equals(entry.getKey())) continue;
            assertEquals(entry.getKey(), entry.getValue(), after.get(entry.getKey()));
        }
        assertEquals(4, prefs.getInt("place.migrated", 0));
        for (Element element : Element.values()) {
            assertFalse(element.toString(),
                prefs.contains("place.terminal.landscape." + element.storageKey() + "_order"));
        }
    }

    // ------------------------------------------------------------------ the three sizes

    @Test
    public void theSizeKeysAreScopedTheWayTheSpecNamesThem() {
        assertEquals("place.home.portrait.dock_height",
            PlaceLayoutStore.arrangementKey(PaneWallPage.WIDGETS, PlaceOrientation.PORTRAIT,
                "dock_height"));
        assertEquals("place.terminal.landscape.keyboard_height",
            PlaceLayoutStore.arrangementKey(PaneWallPage.TERMINAL, PlaceOrientation.LANDSCAPE,
                "keyboard_height"));
        assertEquals("place.display.portrait.keyboard_chin",
            PlaceLayoutStore.arrangementKey(PaneWallPage.DISPLAY, PlaceOrientation.PORTRAIT,
                "keyboard_chin"));
    }

    @Test
    public void aFreshInstallResolvesTheSizesItShippedWith() {
        PlaceLayoutStore store = store();
        for (PaneWallPage place : PaneWallPage.values()) {
            for (PlaceOrientation orientation : PlaceOrientation.values()) {
                assertEquals(place + " " + orientation + " dock",
                    TERMUX_APP.DEFAULT_APP_LAUNCHER_BAR_HEIGHT,
                    store.dockHeightScale(place, orientation), 0.0001f);
                assertEquals(place + " " + orientation + " keyboard",
                    TERMUX_APP.DEFAULT_IN_APP_KEYBOARD_HEIGHT_SCALE,
                    store.keyboardHeightScale(place, orientation), 0.0001f);
                assertEquals(place + " " + orientation + " chin",
                    TERMUX_APP.DEFAULT_IN_APP_KEYBOARD_BOTTOM_PADDING,
                    store.keyboardChinDp(place, orientation));
            }
        }
    }

    @Test
    public void aSizeWrittenForOnePlaceAndOrientationLeavesEveryOtherAlone() {
        PlaceLayoutStore store = store();
        store.setKeyboardHeightScale(PaneWallPage.WIDGETS, PlaceOrientation.PORTRAIT, 1.4f);
        store.setDockHeightScale(PaneWallPage.WIDGETS, PlaceOrientation.PORTRAIT, 1.2f);
        store.setKeyboardChinDp(PaneWallPage.WIDGETS, PlaceOrientation.PORTRAIT, 18);

        assertEquals(1.4f,
            store.keyboardHeightScale(PaneWallPage.WIDGETS, PlaceOrientation.PORTRAIT), 0.0001f);
        assertEquals("home landscape is untouched",
            TERMUX_APP.DEFAULT_IN_APP_KEYBOARD_HEIGHT_SCALE,
            store.keyboardHeightScale(PaneWallPage.WIDGETS, PlaceOrientation.LANDSCAPE), 0.0001f);
        assertEquals("the terminal is untouched",
            TERMUX_APP.DEFAULT_IN_APP_KEYBOARD_HEIGHT_SCALE,
            store.keyboardHeightScale(PaneWallPage.TERMINAL, PlaceOrientation.PORTRAIT), 0.0001f);
        assertEquals(TERMUX_APP.DEFAULT_APP_LAUNCHER_BAR_HEIGHT,
            store.dockHeightScale(PaneWallPage.TERMINAL, PlaceOrientation.PORTRAIT), 0.0001f);
        assertEquals(TERMUX_APP.DEFAULT_IN_APP_KEYBOARD_BOTTOM_PADDING,
            store.keyboardChinDp(PaneWallPage.TERMINAL, PlaceOrientation.PORTRAIT));
    }

    @Test
    public void aSizeIsClampedOnTheWayInAndOnTheWayOut() {
        PlaceLayoutStore store = store();
        store.setKeyboardHeightScale(PaneWallPage.TERMINAL, PlaceOrientation.PORTRAIT, 9f);
        store.setDockHeightScale(PaneWallPage.TERMINAL, PlaceOrientation.PORTRAIT, -3f);
        store.setKeyboardChinDp(PaneWallPage.TERMINAL, PlaceOrientation.PORTRAIT, 400);
        assertEquals(TERMUX_APP.MAX_IN_APP_KEYBOARD_HEIGHT_SCALE,
            store.keyboardHeightScale(PaneWallPage.TERMINAL, PlaceOrientation.PORTRAIT), 0.0001f);
        assertEquals(TERMUX_APP.MIN_APP_LAUNCHER_BAR_HEIGHT,
            store.dockHeightScale(PaneWallPage.TERMINAL, PlaceOrientation.PORTRAIT), 0.0001f);
        assertEquals(TERMUX_APP.MAX_IN_APP_KEYBOARD_BOTTOM_PADDING,
            store.keyboardChinDp(PaneWallPage.TERMINAL, PlaceOrientation.PORTRAIT));

        // And a number written into the file by hand is clamped as it is read back.
        prefs.edit()
            .putFloat("place.display.landscape.keyboard_height", 0.01f)
            .putFloat("place.display.landscape.dock_height", 99f)
            .putInt("place.display.landscape.keyboard_chin", -5)
            .commit();
        assertEquals(TERMUX_APP.MIN_IN_APP_KEYBOARD_HEIGHT_SCALE,
            store.keyboardHeightScale(PaneWallPage.DISPLAY, PlaceOrientation.LANDSCAPE), 0.0001f);
        assertEquals(TERMUX_APP.MAX_APP_LAUNCHER_BAR_HEIGHT,
            store.dockHeightScale(PaneWallPage.DISPLAY, PlaceOrientation.LANDSCAPE), 0.0001f);
        assertEquals(TERMUX_APP.MIN_IN_APP_KEYBOARD_BOTTOM_PADDING,
            store.keyboardChinDp(PaneWallPage.DISPLAY, PlaceOrientation.LANDSCAPE));
    }

    @Test
    public void theFirstRunSeedsEveryPlaceAndOrientationFromTheValuesTheyResolvedToBefore() {
        // What an install upgrading into the Layout editor is carrying: a keyboard height per
        // orientation, one chin, a shared dock height, and one place that took a dock height of
        // its own while the look keys could still be scoped.
        prefs.edit()
            .putFloat(TERMUX_APP.KEY_IN_APP_KEYBOARD_HEIGHT_SCALE, 1.2f)
            .putFloat(TERMUX_APP.KEY_IN_APP_KEYBOARD_HEIGHT_SCALE_LANDSCAPE, 0.9f)
            .putInt(TERMUX_APP.KEY_IN_APP_KEYBOARD_BOTTOM_PADDING, 14)
            .putFloat(TERMUX_APP.KEY_APP_LAUNCHER_BAR_HEIGHT, 2.0f)
            .putFloat(PlaceLookPreferences.lookKey(PaneWallPage.DISPLAY,
                TERMUX_APP.KEY_APP_LAUNCHER_BAR_HEIGHT), 1.1f)
            .commit();

        PlaceLayoutStore store = store();

        assertEquals(4, prefs.getInt("place.migrated", 0));
        for (PaneWallPage place : PaneWallPage.values()) {
            assertEquals(place + " portrait keyboard", 1.2f,
                store.keyboardHeightScale(place, PlaceOrientation.PORTRAIT), 0.0001f);
            assertEquals(place + " landscape keyboard", 0.9f,
                store.keyboardHeightScale(place, PlaceOrientation.LANDSCAPE), 0.0001f);
            for (PlaceOrientation orientation : PlaceOrientation.values()) {
                assertEquals(place + " " + orientation + " chin", 14,
                    store.keyboardChinDp(place, orientation));
                // The display kept a dock height of its own in both orientations; every other
                // place took the shared one.
                assertEquals(place + " " + orientation + " dock",
                    place == PaneWallPage.DISPLAY ? 1.1f : 2.0f,
                    store.dockHeightScale(place, orientation), 0.0001f);
            }
        }
        // Dock height has left the scopable look keys, so the old override goes with them.
        assertFalse(prefs.contains(PlaceLookPreferences.lookKey(PaneWallPage.DISPLAY,
            TERMUX_APP.KEY_APP_LAUNCHER_BAR_HEIGHT)));
    }

    @Test
    public void theSizesAreSeededOnceAndNeverAgain() {
        prefs.edit()
            .putFloat(TERMUX_APP.KEY_IN_APP_KEYBOARD_HEIGHT_SCALE, 1.2f)
            .putInt(TERMUX_APP.KEY_IN_APP_KEYBOARD_BOTTOM_PADDING, 14)
            .putFloat(TERMUX_APP.KEY_APP_LAUNCHER_BAR_HEIGHT, 2.0f)
            .commit();
        PlaceLayoutStore store = store();
        store.setKeyboardHeightScale(PaneWallPage.TERMINAL, PlaceOrientation.PORTRAIT, 1.5f);
        store.setKeyboardChinDp(PaneWallPage.TERMINAL, PlaceOrientation.PORTRAIT, 4);
        store.setDockHeightScale(PaneWallPage.TERMINAL, PlaceOrientation.PORTRAIT, 0.8f);

        PlaceLayoutStore reopened = store();

        assertEquals(1.5f,
            reopened.keyboardHeightScale(PaneWallPage.TERMINAL, PlaceOrientation.PORTRAIT),
            0.0001f);
        assertEquals(4, reopened.keyboardChinDp(PaneWallPage.TERMINAL, PlaceOrientation.PORTRAIT));
        assertEquals(0.8f,
            reopened.dockHeightScale(PaneWallPage.TERMINAL, PlaceOrientation.PORTRAIT), 0.0001f);
    }

    @Test
    public void anInstallAlreadyOnVersionTwoStillGetsTheSizes() {
        prefs.edit()
            .putInt("place.migrated", 2)
            .putFloat(TERMUX_APP.KEY_IN_APP_KEYBOARD_HEIGHT_SCALE, 1.3f)
            .commit();

        PlaceLayoutStore store = store();

        assertEquals(4, prefs.getInt("place.migrated", 0));
        assertEquals(1.3f,
            store.keyboardHeightScale(PaneWallPage.WIDGETS, PlaceOrientation.LANDSCAPE), 0.0001f);
    }

    @Test
    public void landscapeTakesPortraitsKeyboardHeightWhenItNeverHadOneOfItsOwn() {
        // The landscape global fell back to the portrait one, so the seed has to as well.
        prefs.edit().putFloat(TERMUX_APP.KEY_IN_APP_KEYBOARD_HEIGHT_SCALE, 1.35f).commit();

        PlaceLayoutStore store = store();

        assertEquals(1.35f,
            store.keyboardHeightScale(PaneWallPage.TERMINAL, PlaceOrientation.LANDSCAPE), 0.0001f);
    }

    @Test
    public void clearingAPlacesOrientationGivesTheSizesBackToo() {
        PlaceLayoutStore store = store();
        store.setKeyboardHeightScale(PaneWallPage.TERMINAL, PlaceOrientation.PORTRAIT, 1.5f);
        store.setDockHeightScale(PaneWallPage.TERMINAL, PlaceOrientation.PORTRAIT, 0.8f);
        store.setKeyboardChinDp(PaneWallPage.TERMINAL, PlaceOrientation.PORTRAIT, 12);

        store.clear(PaneWallPage.TERMINAL, PlaceOrientation.PORTRAIT);

        assertEquals(TERMUX_APP.DEFAULT_IN_APP_KEYBOARD_HEIGHT_SCALE,
            store.keyboardHeightScale(PaneWallPage.TERMINAL, PlaceOrientation.PORTRAIT), 0.0001f);
        assertEquals(TERMUX_APP.DEFAULT_APP_LAUNCHER_BAR_HEIGHT,
            store.dockHeightScale(PaneWallPage.TERMINAL, PlaceOrientation.PORTRAIT), 0.0001f);
        assertEquals(TERMUX_APP.DEFAULT_IN_APP_KEYBOARD_BOTTOM_PADDING,
            store.keyboardChinDp(PaneWallPage.TERMINAL, PlaceOrientation.PORTRAIT));
    }

    /**
     * One element's slot as the terse row placement the old model spelled. Only the tests speak
     * it now: the model itself keeps the slot, so a bar on the top edge is a top edge rather than
     * being folded into the bottom, and this helper says so by refusing to name one.
     */
    private static PlaceLayout.RowPlacement rowOf(PlaceLayout layout,
                                                  com.termux.app.place.Element element) {
        com.termux.app.place.Slot slot = layout.slot(element);
        if (slot.hidden) return PlaceLayout.RowPlacement.HIDDEN;
        switch (slot.edge) {
            case LEFT: return PlaceLayout.RowPlacement.LEFT;
            case RIGHT: return PlaceLayout.RowPlacement.RIGHT;
            case BOTTOM: return PlaceLayout.RowPlacement.BOTTOM;
            default: throw new AssertionError("no row placement for " + slot);
        }
    }
}
