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
import com.termux.app.place.PlaceLayout.KeyboardMode;
import com.termux.app.place.PlaceLayout.RowPlacement;
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
            assertEquals(place + " portrait status", Edge.TOP, portrait.statusBarEdge);
            assertEquals(place + " portrait apps", RowPlacement.BOTTOM, portrait.appsRow);
            assertTrue(place + " portrait az", portrait.azRowShown);
            assertEquals(place + " portrait keys", RowPlacement.BOTTOM, portrait.extraKeys);
            assertEquals(place + " portrait keyboard", KeyboardMode.RESIZE, portrait.keyboardMode);
            assertEquals(place + " portrait columns", 4, portrait.widgetColumns);
            assertEquals(place + " portrait rows", 5, portrait.widgetRows);

            // Landscape stands the pinned apps on the left edge: today's rail.
            PlaceLayout landscape = store.resolve(place, PlaceOrientation.LANDSCAPE);
            assertEquals(place + " landscape apps", RowPlacement.LEFT, landscape.appsRow);
            assertEquals(place + " landscape keys", RowPlacement.BOTTOM, landscape.extraKeys);
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
        assertFalse(home.azRowShown);
    }

    @Test
    public void aMasterSwitchedOffBeforeTheMigrationIsHiddenForEveryPlaceThenStopsGating() {
        launcher.setAppLauncherAppsRowEnabled(false);
        launcher.setAppLauncherExtraKeysRowEnabled(false);
        PlaceLayoutStore store = store();
        for (PaneWallPage place : PaneWallPage.values()) {
            for (PlaceOrientation orientation : PlaceOrientation.values()) {
                PlaceLayout layout = store.resolve(place, orientation);
                assertEquals(place + " " + orientation, RowPlacement.HIDDEN, layout.appsRow);
                assertEquals(place + " " + orientation, RowPlacement.HIDDEN, layout.extraKeys);
            }
        }
        // The migration folded the master into Hidden once; a scoped write afterwards is a real
        // placement, not a value the master can still veto.
        store.setAppsRow(PaneWallPage.TERMINAL, PlaceOrientation.PORTRAIT, RowPlacement.RIGHT);
        assertEquals(RowPlacement.RIGHT,
            store.resolve(PaneWallPage.TERMINAL, PlaceOrientation.PORTRAIT).appsRow);
    }

    // ------------------------------------------------------------------ scoped writes

    @Test
    public void aScopedWriteWinsAndReachesNoOtherPlaceOrOrientation() {
        PlaceLayoutStore store = store();
        store.setExtraKeys(PaneWallPage.DISPLAY, PlaceOrientation.LANDSCAPE, RowPlacement.RIGHT);
        assertEquals(RowPlacement.RIGHT,
            store.resolve(PaneWallPage.DISPLAY, PlaceOrientation.LANDSCAPE).extraKeys);
        assertEquals(RowPlacement.BOTTOM,
            store.resolve(PaneWallPage.DISPLAY, PlaceOrientation.PORTRAIT).extraKeys);
        assertEquals(RowPlacement.BOTTOM,
            store.resolve(PaneWallPage.TERMINAL, PlaceOrientation.LANDSCAPE).extraKeys);
    }

    @Test
    public void everyFieldRoundTripsThroughItsOwnKey() {
        PlaceLayoutStore store = store();
        PaneWallPage place = PaneWallPage.WIDGETS;
        PlaceOrientation orientation = PlaceOrientation.LANDSCAPE;
        store.setStatusBarEdge(place, orientation, Edge.LEFT);
        store.setAppsRow(place, orientation, RowPlacement.HIDDEN);
        store.setAzRowShown(place, orientation, false);
        store.setExtraKeys(place, orientation, RowPlacement.LEFT);
        store.setKeyboardMode(place, orientation, KeyboardMode.OVERLAY);
        store.setWidgetColumns(place, orientation, 6);
        store.setWidgetRows(place, orientation, 3);

        PlaceLayout layout = store.resolve(place, orientation);
        assertEquals(new PlaceLayout(Edge.LEFT, RowPlacement.HIDDEN, false, RowPlacement.LEFT,
            KeyboardMode.OVERLAY, 6, 3), layout);
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

        store.clear(PaneWallPage.TERMINAL, PlaceOrientation.PORTRAIT);
        PlaceLayout portrait = store.resolve(PaneWallPage.TERMINAL, PlaceOrientation.PORTRAIT);
        assertEquals(RowPlacement.BOTTOM, portrait.appsRow);
        assertEquals(Edge.TOP, portrait.statusBarEdge);
        assertEquals(RowPlacement.RIGHT,
            store.resolve(PaneWallPage.TERMINAL, PlaceOrientation.LANDSCAPE).appsRow);
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
                store.resolve(place, PlaceOrientation.LANDSCAPE).appsRow);
            assertFalse(place + " status", store.isStatusCompact(place));
        }
        for (PlaceOrientation orientation : PlaceOrientation.values()) {
            assertEquals(RowPlacement.LEFT,
                store.resolve(PaneWallPage.DISPLAY, orientation).extraKeys);
        }
        assertTrue(store.wasKeyboardOpen(PaneWallPage.DISPLAY));
        // There is no hidden status bar any more, and the display's keyboard memory has moved.
        assertFalse(prefs.contains("x11_hide_status_bar"));
        assertFalse(prefs.contains("x11_keyboard_shown"));
        assertEquals(2, prefs.getInt("place.migrated", 0));

        // A second store over the same preferences must not fold anything again: the user's own
        // choices since the migration stand.
        store.setAppsRow(PaneWallPage.TERMINAL, PlaceOrientation.LANDSCAPE, RowPlacement.LEFT);
        store.setStatusCompact(PaneWallPage.TERMINAL, true);
        PlaceLayoutStore reopened = store();
        assertEquals(RowPlacement.LEFT,
            reopened.resolve(PaneWallPage.TERMINAL, PlaceOrientation.LANDSCAPE).appsRow);
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
            store.resolve(PaneWallPage.TERMINAL, PlaceOrientation.LANDSCAPE).appsRow);
        // Version 2 still runs: the extra-keys master was off, so it folds to Hidden everywhere.
        assertEquals(RowPlacement.HIDDEN,
            store.resolve(PaneWallPage.WIDGETS, PlaceOrientation.PORTRAIT).extraKeys);
        assertEquals(2, prefs.getInt("place.migrated", 0));
    }

    @Test
    public void aFreshInstallHasNothingToFoldAndSaysSo() {
        PlaceLayoutStore store = store();
        assertEquals(2, prefs.getInt("place.migrated", 0));
        assertFalse(prefs.contains("place.terminal.landscape.apps_row"));
        assertEquals(RowPlacement.LEFT,
            store.resolve(PaneWallPage.TERMINAL, PlaceOrientation.LANDSCAPE).appsRow);
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
}
