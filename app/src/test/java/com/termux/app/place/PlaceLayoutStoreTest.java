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
 * The layout store: what the shared layout resolves to before anything is written, what the old
 * global keys become, that a write is what every place reads back, and what the one thing a place
 * still remembers of its own — its keyboard — answers. The fold from per-place layouts and looks
 * into the shared ones has its own test, {@link PlaceLayoutSharedMigrationTest}.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
@ConscryptMode(ConscryptMode.Mode.OFF)
public class PlaceLayoutStoreTest {

    /** What a migrated store says it has folded. Bumped with {@code MIGRATION_VERSION}. */
    private static final int MIGRATED = 6;

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
    public void freshInstallResolvesTheArrangementTheLauncherAlreadyHad() {
        PlaceLayoutStore store = store();
        PlaceLayout portrait = store.resolve(PlaceOrientation.PORTRAIT);
        assertEquals(Edge.TOP, portrait.slot(Element.STATUS).edge);
        assertEquals(RowPlacement.BOTTOM, rowOf(portrait, Element.APPS));
        assertFalse(portrait.slot(Element.AZ).hidden);
        assertEquals(RowPlacement.BOTTOM, rowOf(portrait, Element.EXTRA_KEYS));
        assertEquals(KeyboardMode.RESIZE, portrait.keyboardMode);
        assertEquals(4, portrait.widgetColumns);
        assertEquals(5, portrait.widgetRows);

        // Landscape stands the pinned apps on the left edge: today's rail.
        PlaceLayout landscape = store.resolve(PlaceOrientation.LANDSCAPE);
        assertEquals(RowPlacement.LEFT, rowOf(landscape, Element.APPS));
        assertEquals(RowPlacement.BOTTOM, rowOf(landscape, Element.EXTRA_KEYS));
    }

    @Test
    public void theKeyboardFloatsOverTheDisplayInLandscapeUntilAskedOtherwise() {
        // Only the display reads the mode (KeyboardOverlayPolicy), and it floated there in
        // landscape before the layout was shared, so that is still the default.
        PlaceLayoutStore store = store();
        assertEquals(KeyboardMode.OVERLAY, store.resolve(PlaceOrientation.LANDSCAPE).keyboardMode);
        assertEquals(KeyboardMode.RESIZE, store.resolve(PlaceOrientation.PORTRAIT).keyboardMode);
    }

    // ------------------------------------------------------------------ the global fallback

    @Test
    public void aMissingKeyFallsBackToTheGlobalValue() {
        launcher.setAppLauncherWidgetGridColumns(6);
        launcher.setAppLauncherWidgetGridRows(7);
        launcher.setAppLauncherAzRowEnabled(false);
        PlaceLayout layout = store().resolve(PlaceOrientation.PORTRAIT);
        assertEquals(6, layout.widgetColumns);
        assertEquals(7, layout.widgetRows);
        assertTrue(layout.slot(Element.AZ).hidden);
    }

    @Test
    public void aMasterSwitchedOffBeforeTheMigrationIsHiddenThenStopsGating() {
        launcher.setAppLauncherAppsRowEnabled(false);
        launcher.setAppLauncherExtraKeysRowEnabled(false);
        PlaceLayoutStore store = store();
        for (PlaceOrientation orientation : PlaceOrientation.values()) {
            PlaceLayout layout = store.resolve(orientation);
            assertEquals(orientation.toString(), RowPlacement.HIDDEN, rowOf(layout, Element.APPS));
            assertEquals(orientation.toString(), RowPlacement.HIDDEN,
                rowOf(layout, Element.EXTRA_KEYS));
        }
        // The migration folded the master into Hidden once; a write afterwards is a real
        // placement, not a value the master can still veto.
        store.setAppsRow(PlaceOrientation.LANDSCAPE, RowPlacement.RIGHT);
        assertEquals(RowPlacement.RIGHT,
            rowOf(store.resolve(PlaceOrientation.LANDSCAPE), Element.APPS));
    }

    @Test
    public void placingTheExtraKeysLiftsTheToolbarToggleThatHidThem() {
        launcher.setShowTerminalToolbar(false);
        PlaceLayoutStore store = store();
        assertEquals(RowPlacement.HIDDEN,
            rowOf(store.resolve(PlaceOrientation.PORTRAIT), Element.EXTRA_KEYS));

        store.setExtraKeys(PlaceOrientation.PORTRAIT, RowPlacement.BOTTOM);
        assertTrue("a placement is a request to see them", launcher.shouldShowTerminalToolbar());
        assertEquals(RowPlacement.BOTTOM,
            rowOf(store.resolve(PlaceOrientation.PORTRAIT), Element.EXTRA_KEYS));

        // Hiding them again is a placement of its own and leaves the toggle alone.
        store.setExtraKeys(PlaceOrientation.PORTRAIT, RowPlacement.HIDDEN);
        assertTrue(launcher.shouldShowTerminalToolbar());
        assertEquals(RowPlacement.HIDDEN,
            rowOf(store.resolve(PlaceOrientation.PORTRAIT), Element.EXTRA_KEYS));
    }

    // ------------------------------------------------------------------ writes

    @Test
    public void aWriteWinsAndReachesNoOtherOrientation() {
        PlaceLayoutStore store = store();
        store.setExtraKeys(PlaceOrientation.LANDSCAPE, RowPlacement.RIGHT);
        assertEquals(RowPlacement.RIGHT,
            rowOf(store.resolve(PlaceOrientation.LANDSCAPE), Element.EXTRA_KEYS));
        assertEquals(RowPlacement.BOTTOM,
            rowOf(store.resolve(PlaceOrientation.PORTRAIT), Element.EXTRA_KEYS));
    }

    @Test
    public void everyFieldRoundTripsThroughItsOwnKey() {
        PlaceLayoutStore store = store();
        PlaceOrientation orientation = PlaceOrientation.LANDSCAPE;
        store.setStatusBarEdge(orientation, Edge.LEFT);
        store.setAppsRow(orientation, RowPlacement.HIDDEN);
        store.setAzRowShown(orientation, false);
        store.setAzBarEdge(orientation, Edge.RIGHT);
        store.setExtraKeys(orientation, RowPlacement.LEFT);
        store.setKeyboardMode(orientation, KeyboardMode.OVERLAY);
        store.setWidgetColumns(orientation, 6);
        store.setWidgetRows(orientation, 3);

        PlaceLayout layout = store.resolve(orientation);
        assertEquals(new PlaceLayout(Edge.LEFT, RowPlacement.HIDDEN, false, Edge.RIGHT,
            RowPlacement.LEFT, KeyboardMode.OVERLAY, KeyboardForm.DOCKED, 6, 3), layout);
        assertTrue(layout.toString().contains("grid=6x3"));
        assertNotEquals(layout, store.resolve(PlaceOrientation.PORTRAIT));
    }

    @Test
    public void aGridBeyondWhatTheGridCanLayOutIsClamped() {
        PlaceLayoutStore store = store();
        store.setWidgetColumns(PlaceOrientation.PORTRAIT, 99);
        store.setWidgetRows(PlaceOrientation.PORTRAIT, 0);
        PlaceLayout layout = store.resolve(PlaceOrientation.PORTRAIT);
        assertEquals(8, layout.widgetColumns);
        assertEquals(2, layout.widgetRows);
    }

    @Test
    public void clearingPutsOneOrientationBackAndLeavesTheOtherAlone() {
        PlaceLayoutStore store = store();
        store.setAppsRow(PlaceOrientation.PORTRAIT, RowPlacement.RIGHT);
        store.setAppsRow(PlaceOrientation.LANDSCAPE, RowPlacement.RIGHT);
        store.setStatusBarEdge(PlaceOrientation.PORTRAIT, Edge.BOTTOM);
        store.setAzBarEdge(PlaceOrientation.LANDSCAPE, Edge.LEFT);

        store.clear(PlaceOrientation.PORTRAIT);
        PlaceLayout portrait = store.resolve(PlaceOrientation.PORTRAIT);
        assertEquals(RowPlacement.BOTTOM, rowOf(portrait, Element.APPS));
        assertEquals(Edge.TOP, portrait.slot(Element.STATUS).edge);
        assertEquals(RowPlacement.RIGHT,
            rowOf(store.resolve(PlaceOrientation.LANDSCAPE), Element.APPS));
        // az_bar is in ARRANGEMENT_KEYS but for the untouched orientation, so it survives the clear.
        assertEquals(Edge.LEFT, store.resolve(PlaceOrientation.LANDSCAPE).slot(Element.AZ).edge);
    }

    @Test
    public void azBarEdgeDefaultsToBottomAndStandsOnEveryEdge() {
        PlaceLayoutStore store = store();
        assertEquals(Edge.BOTTOM, store.resolve(PlaceOrientation.PORTRAIT).slot(Element.AZ).edge);
        assertEquals(Edge.BOTTOM, store.resolve(PlaceOrientation.LANDSCAPE).slot(Element.AZ).edge);

        store.setAzBarEdge(PlaceOrientation.PORTRAIT, Edge.LEFT);
        store.setAzBarEdge(PlaceOrientation.LANDSCAPE, Edge.LEFT);
        assertEquals("portrait stands a column of its own now", Edge.LEFT,
            store.resolve(PlaceOrientation.PORTRAIT).slot(Element.AZ).edge);
        assertEquals(Edge.LEFT, store.resolve(PlaceOrientation.LANDSCAPE).slot(Element.AZ).edge);

        store.setAzBarEdge(PlaceOrientation.PORTRAIT, Edge.TOP);
        assertEquals(Edge.TOP, store.resolve(PlaceOrientation.PORTRAIT).slot(Element.AZ).edge);
    }

    @Test
    public void clearingRemovesTheAzBarEdge() {
        PlaceLayoutStore store = store();
        store.setAzBarEdge(PlaceOrientation.LANDSCAPE, Edge.RIGHT);
        store.clear(PlaceOrientation.LANDSCAPE);
        assertEquals(Edge.BOTTOM, store.resolve(PlaceOrientation.LANDSCAPE).slot(Element.AZ).edge);
    }

    @Test
    public void everyWriteMovesTheRevisionSoACachedLayoutIsRetired() {
        PlaceLayoutStore store = store();
        int before = store.revision();
        store.setAppsRow(PlaceOrientation.PORTRAIT, RowPlacement.LEFT);
        assertNotEquals(before, store.revision());
    }

    // ------------------------------------------------------------------ memory

    @Test
    public void theTerminalAndTheDisplayEachRememberTheirKeyboardAndHomeNeverDoes() {
        PlaceLayoutStore store = store();
        for (PaneWallPage place : PaneWallPage.values())
            assertFalse(place.toString(), store.wasKeyboardOpen(place));

        store.setKeyboardOpen(PaneWallPage.DISPLAY, true);
        assertTrue(store.wasKeyboardOpen(PaneWallPage.DISPLAY));
        assertFalse("the terminal keeps its own", store.wasKeyboardOpen(PaneWallPage.TERMINAL));

        store.setKeyboardOpen(PaneWallPage.TERMINAL, true);
        store.setKeyboardOpen(PaneWallPage.DISPLAY, false);
        assertTrue(store.wasKeyboardOpen(PaneWallPage.TERMINAL));
        assertFalse(store.wasKeyboardOpen(PaneWallPage.DISPLAY));

        // Home has nothing to type into: it comes back closed whatever it was left with, and
        // nothing is written for it.
        store.setKeyboardOpen(PaneWallPage.WIDGETS, true);
        assertFalse(store.wasKeyboardOpen(PaneWallPage.WIDGETS));
        assertFalse(prefs.contains("place.home.keyboard_open"));
    }

    @Test
    public void minimalModeIsRememberedPerPlaceBesideTheKeyboardAndNeverForHome() {
        PlaceLayoutStore store = store();
        for (PaneWallPage place : PaneWallPage.values())
            assertFalse(place.toString(), store.isMinimal(place));

        store.setKeyboardOpen(PaneWallPage.TERMINAL, true);
        store.setMinimal(PaneWallPage.TERMINAL, true);
        assertTrue(store.isMinimal(PaneWallPage.TERMINAL));
        assertFalse("the display keeps its own", store.isMinimal(PaneWallPage.DISPLAY));
        assertTrue("kept beside the keyboard memory", prefs.contains("place.terminal.minimal"));
        // The mode does not overwrite the keyboard the place remembers; it only overrides it.
        assertTrue(store.wasKeyboardOpen(PaneWallPage.TERMINAL));
        assertFalse(MinimalMode.keyboardOnEnter(store.wasKeyboardOpen(PaneWallPage.TERMINAL),
            store.isMinimal(PaneWallPage.TERMINAL)));

        store.setMinimal(PaneWallPage.DISPLAY, true);
        store.setMinimal(PaneWallPage.TERMINAL, false);
        assertFalse(store.isMinimal(PaneWallPage.TERMINAL));
        assertTrue(store.isMinimal(PaneWallPage.DISPLAY));
        assertTrue("off again, the keyboard comes back as it was",
            MinimalMode.keyboardOnEnter(store.wasKeyboardOpen(PaneWallPage.TERMINAL),
                store.isMinimal(PaneWallPage.TERMINAL)));

        // Remembered until it is turned off, across a new store over the same preferences.
        assertTrue(store().isMinimal(PaneWallPage.DISPLAY));

        // Home has no pane to give the screen to: nothing is written, and it never answers yes.
        store.setMinimal(PaneWallPage.WIDGETS, true);
        assertFalse(store.isMinimal(PaneWallPage.WIDGETS));
        assertFalse(prefs.contains("place.home.minimal"));
    }

    @Test
    public void theBarRestsOncePerOrientationForEveryPlace() {
        PlaceLayoutStore store = store();
        // Portrait starts compact, the way the launcher's one status bar always has.
        assertTrue(store.isStatusCompact(PlaceOrientation.PORTRAIT));
        store.setStatusCompact(PlaceOrientation.PORTRAIT, false);
        // Portrait opened the bar; the landscape screen it never opened it on is untouched.
        assertFalse(store.isStatusCompact(PlaceOrientation.PORTRAIT));
        assertTrue(store.isStatusCompact(PlaceOrientation.LANDSCAPE));
        // And the other way about: a landscape bar left open stays open there and nowhere else.
        store.setStatusCompact(PlaceOrientation.LANDSCAPE, false);
        store.setStatusCompact(PlaceOrientation.PORTRAIT, true);
        assertFalse(store.isStatusCompact(PlaceOrientation.LANDSCAPE));
        assertTrue(store.isStatusCompact(PlaceOrientation.PORTRAIT));
    }

    @Test
    public void anExplicitlyOpenedBarBeatsEveryDefaultAndSurvivesAReopen() {
        PlaceLayoutStore store = store();
        // The landscape default is compact. A user who says otherwise is not asked again — not by
        // the default, and not by the next store built over the same preferences.
        store.setStatusCompact(PlaceOrientation.LANDSCAPE, false);
        assertFalse(store.isStatusCompact(PlaceOrientation.LANDSCAPE));
        assertFalse(store().isStatusCompact(PlaceOrientation.LANDSCAPE));
        assertEquals(MIGRATED, prefs.getInt("place.migrated", 0));
    }

    @Test
    public void portraitKeepsTheStateTheLaunchersOneBarAlwaysRead() {
        // Portrait's default is the old global, unchanged: nothing about portrait moves here.
        prefs.edit().putBoolean("top_pane_clock_collapsed", false).commit();
        PlaceLayoutStore store = store();
        assertFalse(store.isStatusCompact(PlaceOrientation.PORTRAIT));
        // The same global with the migration already run seeds landscape too, so the expanded bar
        // the user chose is what landscape reads as well.
        assertFalse(store.isStatusCompact(PlaceOrientation.LANDSCAPE));
    }

    @Test
    public void aRestingStateNobodyChoseIsNeverWrittenDown() {
        PlaceLayoutStore store = store();
        assertTrue(store.isStatusCompact(PlaceOrientation.LANDSCAPE));
        assertTrue(store.isStatusCompact(PlaceOrientation.PORTRAIT));
        // Reading a default must not pin it: the store cannot tell a pinned default from a choice
        // afterwards, so one written here is a choice the user can never be given back.
        assertFalse(prefs.contains("layout.landscape.status_compact"));
        assertFalse(prefs.contains("layout.portrait.status_compact"));
    }

    @Test
    public void theRestingStateIsSeededFromTheTerminalsOneValue() {
        // An install that migrated before the key moved to the orientation: one value per place.
        prefs.edit()
            .putInt("place.migrated", 4)
            .putBoolean("place.terminal.status_compact", false)
            .putBoolean("place.home.status_compact", true)
            .commit();

        PlaceLayoutStore store = store();
        // The terminal's open bar is still open — in both orientations, so the choice is kept
        // wherever the user had it, and the landscape default does not get to overrule it.
        assertFalse(store.isStatusCompact(PlaceOrientation.PORTRAIT));
        assertFalse(store.isStatusCompact(PlaceOrientation.LANDSCAPE));
        // The keys it came from are gone, and the fold does not run again.
        assertFalse(prefs.contains("place.terminal.status_compact"));
        assertFalse(prefs.contains("place.home.status_compact"));
        assertFalse(prefs.contains("place.terminal.landscape.status_compact"));
        assertEquals(MIGRATED, prefs.getInt("place.migrated", 0));

        store.setStatusCompact(PlaceOrientation.LANDSCAPE, true);
        PlaceLayoutStore reopened = store();
        assertTrue(reopened.isStatusCompact(PlaceOrientation.LANDSCAPE));
        assertFalse(reopened.isStatusCompact(PlaceOrientation.PORTRAIT));
    }

    // ------------------------------------------------------------------ migration

    @Test
    public void theOldGlobalKeysAreFoldedExactlyOnce() {
        prefs.edit()
            .putString("app_launcher_dock_rail_side", "right")
            .putString("x11_extra_keys_side", "left")
            .putBoolean("x11_hide_status_bar", true)
            .putBoolean("x11_keyboard_shown", true)
            // Expanded, which is not the shipped default: only the migration can produce it.
            .putBoolean("top_pane_clock_collapsed", false)
            .commit();

        PlaceLayoutStore store = store();
        assertEquals(RowPlacement.RIGHT,
            rowOf(store.resolve(PlaceOrientation.LANDSCAPE), Element.APPS));
        for (PlaceOrientation orientation : PlaceOrientation.values())
            assertFalse("status " + orientation, store.isStatusCompact(orientation));
        // The display's old extra-keys column was the display's alone; the shared layout is the
        // terminal's, which never had one.
        assertEquals(RowPlacement.BOTTOM,
            rowOf(store.resolve(PlaceOrientation.LANDSCAPE), Element.EXTRA_KEYS));
        assertTrue(store.wasKeyboardOpen(PaneWallPage.DISPLAY));
        // There is no hidden status bar any more, and the display's keyboard memory has moved.
        assertFalse(prefs.contains("x11_hide_status_bar"));
        assertFalse(prefs.contains("x11_keyboard_shown"));
        assertEquals(MIGRATED, prefs.getInt("place.migrated", 0));

        // A second store over the same preferences must not fold anything again: the user's own
        // choices since the migration stand.
        store.setAppsRow(PlaceOrientation.LANDSCAPE, RowPlacement.LEFT);
        store.setStatusCompact(PlaceOrientation.LANDSCAPE, true);
        PlaceLayoutStore reopened = store();
        assertEquals(RowPlacement.LEFT,
            rowOf(reopened.resolve(PlaceOrientation.LANDSCAPE), Element.APPS));
        assertTrue(reopened.isStatusCompact(PlaceOrientation.LANDSCAPE));
        assertFalse(reopened.isStatusCompact(PlaceOrientation.PORTRAIT));
    }

    @Test
    public void reachingVersionTwoFromVersionOneOnlyRunsTheStepAddedSince() {
        // An install already migrated to version 1 keeps its own choice — re-running version 1's
        // fold would stomp it with the legacy global it was migrated away from.
        prefs.edit()
            .putInt("place.migrated", 1)
            .putString("app_launcher_dock_rail_side", "right")
            .putString("place.terminal.landscape.apps_row", "left")
            .commit();
        launcher.setAppLauncherExtraKeysRowEnabled(false);

        PlaceLayoutStore store = store();
        assertEquals(RowPlacement.LEFT,
            rowOf(store.resolve(PlaceOrientation.LANDSCAPE), Element.APPS));
        // Version 2 still runs: the extra-keys master was off, so it folds to Hidden.
        assertEquals(RowPlacement.HIDDEN,
            rowOf(store.resolve(PlaceOrientation.PORTRAIT), Element.EXTRA_KEYS));
        assertEquals(MIGRATED, prefs.getInt("place.migrated", 0));
    }

    @Test
    public void aFreshInstallHasNothingToFoldAndSaysSo() {
        PlaceLayoutStore store = store();
        assertEquals(MIGRATED, prefs.getInt("place.migrated", 0));
        assertFalse(prefs.contains("layout.landscape.apps_row"));
        assertEquals(RowPlacement.LEFT,
            rowOf(store.resolve(PlaceOrientation.LANDSCAPE), Element.APPS));
    }

    @Test
    public void theKeysAreNamedTheWayTheSpecNamesThem() {
        assertEquals("layout.portrait.apps_row",
            PlaceLayoutStore.layoutKey(PlaceOrientation.PORTRAIT, "apps_row"));
        assertEquals("layout.landscape.extra_keys",
            PlaceLayoutStore.layoutKey(PlaceOrientation.LANDSCAPE, "extra_keys"));
        assertEquals("layout.landscape.status_compact",
            PlaceLayoutStore.layoutKey(PlaceOrientation.LANDSCAPE, "status_compact"));
        assertEquals("place.display.keyboard_open",
            PlaceLayoutStore.memoryKey(PaneWallPage.DISPLAY, "keyboard_open"));
        // The spellings the fold reads from.
        assertEquals("place.home.portrait.apps_row",
            PlaceLayoutStore.legacyArrangementKey(PaneWallPage.WIDGETS, PlaceOrientation.PORTRAIT,
                "apps_row"));
        assertEquals("place.terminal.look.terminal_corner_radius",
            PlaceLayoutStore.legacyLookKey(PaneWallPage.TERMINAL, "terminal_corner_radius"));
    }

    @Test
    public void portraitKeepsASideStoredForIt() {
        // Portrait used to refuse a column and read a side back as the top. Every edge stands in
        // both orientations now; a canvas too narrow for a column is the Layout editor's to warn
        // about rather than the store's to overrule.
        PlaceLayoutStore store = store();
        store.setStatusBarEdge(PlaceOrientation.PORTRAIT, Edge.RIGHT);
        store.setStatusBarEdge(PlaceOrientation.LANDSCAPE, Edge.RIGHT);

        assertEquals(Edge.RIGHT, store.resolve(PlaceOrientation.PORTRAIT).slot(Element.STATUS).edge);
        assertEquals(Edge.RIGHT,
            store.resolve(PlaceOrientation.LANDSCAPE).slot(Element.STATUS).edge);
        store.setStatusBarEdge(PlaceOrientation.PORTRAIT, Edge.BOTTOM);
        assertEquals(Edge.BOTTOM,
            store.resolve(PlaceOrientation.PORTRAIT).slot(Element.STATUS).edge);
    }

    @Test
    public void portraitKeepsASideRowStoredForIt() {
        // The apps row and the extra keys stand in a column in either orientation now, and hidden
        // still stays hidden.
        PlaceLayoutStore store = store();
        store.setAppsRow(PlaceOrientation.PORTRAIT, RowPlacement.LEFT);
        store.setExtraKeys(PlaceOrientation.PORTRAIT, RowPlacement.RIGHT);
        store.setAppsRow(PlaceOrientation.LANDSCAPE, RowPlacement.RIGHT);
        store.setExtraKeys(PlaceOrientation.LANDSCAPE, RowPlacement.HIDDEN);

        PlaceLayout portrait = store.resolve(PlaceOrientation.PORTRAIT);
        assertEquals(RowPlacement.LEFT, rowOf(portrait, Element.APPS));
        assertEquals(RowPlacement.RIGHT, rowOf(portrait, Element.EXTRA_KEYS));
        assertEquals(RowPlacement.RIGHT,
            rowOf(store.resolve(PlaceOrientation.LANDSCAPE), Element.APPS));
        assertEquals(RowPlacement.HIDDEN,
            rowOf(store.resolve(PlaceOrientation.LANDSCAPE), Element.EXTRA_KEYS));
    }

    // ------------------------------------------------------------------ slots and stack order

    @Test
    public void everySlotReadsBackTheKeyItHasAlwaysBeenStoredUnder() {
        PlaceLayoutStore store = store();
        assertEquals("layout.landscape.status_bar",
            PlaceLayoutStore.layoutKey(PlaceOrientation.LANDSCAPE, Element.STATUS.storageKey()));
        assertEquals("layout.landscape.apps_row",
            PlaceLayoutStore.layoutKey(PlaceOrientation.LANDSCAPE, Element.APPS.storageKey()));
        assertEquals("layout.landscape.az_bar",
            PlaceLayoutStore.layoutKey(PlaceOrientation.LANDSCAPE, Element.AZ.storageKey()));
        assertEquals("layout.landscape.extra_keys",
            PlaceLayoutStore.layoutKey(PlaceOrientation.LANDSCAPE,
                Element.EXTRA_KEYS.storageKey()));
        assertEquals("layout.portrait.apps_row_order",
            PlaceLayoutStore.layoutKey(PlaceOrientation.PORTRAIT,
                PlaceLayoutStore.orderKeyName(Element.APPS)));

        // Written the old way, read back as a slot.
        store.setAppsRow(PlaceOrientation.PORTRAIT, RowPlacement.RIGHT);
        assertEquals(new Slot(false, Edge.RIGHT, Element.APPS.defaultOrder(Edge.RIGHT)),
            store.slot(PlaceOrientation.PORTRAIT, Element.APPS));
        // Written as a slot, read back the old way.
        store.setSlot(PlaceOrientation.PORTRAIT, Element.EXTRA_KEYS, new Slot(false, Edge.LEFT, 1));
        assertEquals(RowPlacement.LEFT, store.extraKeys(PlaceOrientation.PORTRAIT));
        assertEquals("left", prefs.getString("layout.portrait.extra_keys", null));
        assertEquals(1, prefs.getInt("layout.portrait.extra_keys_order", -1));
    }

    @Test
    public void aStoredPlacementWidensToEveryEdgeAndToHidden() {
        // The four spellings the rows have always been stored as still read, in both orientations,
        // and "top" — which the old three-way placement had no room for — reads as the top edge.
        store();
        for (String value : new String[] {"bottom", "left", "right", "top", "hidden"}) {
            for (PlaceOrientation orientation : PlaceOrientation.values()) {
                prefs.edit().putString("layout." + orientation.storageValue() + ".apps_row",
                    value).commit();
                Slot slot = store().slot(orientation, Element.APPS);
                if ("hidden".equals(value)) {
                    assertTrue(value, slot.hidden);
                    assertEquals(value, Edge.BOTTOM, slot.edge);
                } else {
                    assertFalse(value, slot.hidden);
                    assertEquals(value, Edge.parse(value, Edge.TOP), slot.edge);
                }
            }
        }
        // A value nobody recognises still falls back to what the launcher has always shown.
        prefs.edit().putString("layout.landscape.apps_row", "sideways").commit();
        assertEquals(Edge.LEFT, store().slot(PlaceOrientation.LANDSCAPE, Element.APPS).edge);
    }

    @Test
    public void anAbsentOrderKeyIsTheStackTheLauncherAlreadyDraws() {
        PlaceLayoutStore store = store();
        PlaceLayout portrait = store.resolve(PlaceOrientation.PORTRAIT);
        // Portrait ships the status bar on the top and the three rows on the bottom.
        assertEquals(new Slot(false, Edge.TOP, 0), portrait.slot(Element.STATUS));
        assertEquals(new Slot(false, Edge.BOTTOM, 0), portrait.slot(Element.EXTRA_KEYS));
        assertEquals(new Slot(false, Edge.BOTTOM, 1), portrait.slot(Element.AZ));
        assertEquals(new Slot(false, Edge.BOTTOM, 2), portrait.slot(Element.APPS));
        assertEquals(java.util.Arrays.asList(Element.EXTRA_KEYS, Element.AZ, Element.APPS),
            EdgeStackPolicy.stack(portrait, Edge.BOTTOM));

        // Landscape ships the pinned apps as the left rail, so they take the rail's band.
        PlaceLayout landscape = store.resolve(PlaceOrientation.LANDSCAPE);
        assertEquals(new Slot(false, Edge.LEFT, 1), landscape.slot(Element.APPS));
        assertEquals(java.util.Arrays.asList(Element.APPS),
            EdgeStackPolicy.stack(landscape, Edge.LEFT));
        assertFalse("nothing is written until something is re-ordered",
            prefs.contains("layout.portrait.apps_row_order"));
    }

    @Test
    public void anOrderFollowsTheEdgeTheElementIsMovedTo() {
        PlaceLayoutStore store = store();
        // No order of its own: the default is read against whichever edge it is standing on.
        assertEquals(Element.AZ.defaultOrder(Edge.BOTTOM),
            store.slotOrder(PlaceOrientation.PORTRAIT, Element.AZ));
        store.setAzBarEdge(PlaceOrientation.PORTRAIT, Edge.RIGHT);
        assertEquals(Element.AZ.defaultOrder(Edge.RIGHT),
            store.slotOrder(PlaceOrientation.PORTRAIT, Element.AZ));

        store.setSlotOrder(PlaceOrientation.PORTRAIT, Element.AZ, 0);
        assertEquals(0, store.slotOrder(PlaceOrientation.PORTRAIT, Element.AZ));
        assertEquals("the other orientation is untouched", Element.AZ.defaultOrder(Edge.BOTTOM),
            store.slotOrder(PlaceOrientation.LANDSCAPE, Element.AZ));
    }

    @Test
    public void clearingPutsAReorderBackToo() {
        PlaceLayoutStore store = store();
        store.setSlot(PlaceOrientation.LANDSCAPE, Element.EXTRA_KEYS,
            new Slot(false, Edge.RIGHT, 0));
        assertTrue(prefs.contains("layout.landscape.extra_keys_order"));
        store.clear(PlaceOrientation.LANDSCAPE);
        assertFalse(prefs.contains("layout.landscape.extra_keys_order"));
        assertEquals(Element.EXTRA_KEYS.defaultOrder(Edge.BOTTOM),
            store.slotOrder(PlaceOrientation.LANDSCAPE, Element.EXTRA_KEYS));
    }

    // ------------------------------------------------------------------ the three sizes

    @Test
    public void theSizeKeysAreNamedTheWayTheSpecNamesThem() {
        assertEquals("layout.portrait.dock_height",
            PlaceLayoutStore.layoutKey(PlaceOrientation.PORTRAIT, "dock_height"));
        assertEquals("layout.landscape.keyboard_height",
            PlaceLayoutStore.layoutKey(PlaceOrientation.LANDSCAPE, "keyboard_height"));
        assertEquals("layout.portrait.keyboard_chin",
            PlaceLayoutStore.layoutKey(PlaceOrientation.PORTRAIT, "keyboard_chin"));
    }

    @Test
    public void aFreshInstallResolvesTheSizesItShippedWith() {
        PlaceLayoutStore store = store();
        for (PlaceOrientation orientation : PlaceOrientation.values()) {
            assertEquals(orientation + " dock", TERMUX_APP.DEFAULT_APP_LAUNCHER_BAR_HEIGHT,
                store.dockHeightScale(orientation), 0.0001f);
            assertEquals(orientation + " keyboard",
                TERMUX_APP.DEFAULT_IN_APP_KEYBOARD_HEIGHT_SCALE,
                store.keyboardHeightScale(orientation), 0.0001f);
            assertEquals(orientation + " chin", TERMUX_APP.DEFAULT_IN_APP_KEYBOARD_BOTTOM_PADDING,
                store.keyboardChinDp(orientation));
        }
    }

    @Test
    public void aSizeWrittenForOneOrientationLeavesTheOtherAlone() {
        PlaceLayoutStore store = store();
        store.setKeyboardHeightScale(PlaceOrientation.PORTRAIT, 1.4f);
        store.setDockHeightScale(PlaceOrientation.PORTRAIT, 1.2f);
        store.setKeyboardChinDp(PlaceOrientation.PORTRAIT, 18);

        assertEquals(1.4f, store.keyboardHeightScale(PlaceOrientation.PORTRAIT), 0.0001f);
        assertEquals(1.2f, store.dockHeightScale(PlaceOrientation.PORTRAIT), 0.0001f);
        assertEquals(18, store.keyboardChinDp(PlaceOrientation.PORTRAIT));
        assertEquals("landscape is untouched", TERMUX_APP.DEFAULT_IN_APP_KEYBOARD_HEIGHT_SCALE,
            store.keyboardHeightScale(PlaceOrientation.LANDSCAPE), 0.0001f);
        assertEquals(TERMUX_APP.DEFAULT_APP_LAUNCHER_BAR_HEIGHT,
            store.dockHeightScale(PlaceOrientation.LANDSCAPE), 0.0001f);
        assertEquals(TERMUX_APP.DEFAULT_IN_APP_KEYBOARD_BOTTOM_PADDING,
            store.keyboardChinDp(PlaceOrientation.LANDSCAPE));
    }

    @Test
    public void aSizeIsClampedOnTheWayInAndOnTheWayOut() {
        PlaceLayoutStore store = store();
        store.setKeyboardHeightScale(PlaceOrientation.PORTRAIT, 9f);
        store.setDockHeightScale(PlaceOrientation.PORTRAIT, -3f);
        store.setKeyboardChinDp(PlaceOrientation.PORTRAIT, 400);
        assertEquals(TERMUX_APP.MAX_IN_APP_KEYBOARD_HEIGHT_SCALE,
            store.keyboardHeightScale(PlaceOrientation.PORTRAIT), 0.0001f);
        assertEquals(TERMUX_APP.MIN_APP_LAUNCHER_BAR_HEIGHT,
            store.dockHeightScale(PlaceOrientation.PORTRAIT), 0.0001f);
        assertEquals(TERMUX_APP.MAX_IN_APP_KEYBOARD_BOTTOM_PADDING,
            store.keyboardChinDp(PlaceOrientation.PORTRAIT));

        // And a number written into the file by hand is clamped as it is read back.
        prefs.edit()
            .putFloat("layout.landscape.keyboard_height", 0.01f)
            .putFloat("layout.landscape.dock_height", 99f)
            .putInt("layout.landscape.keyboard_chin", -5)
            .commit();
        assertEquals(TERMUX_APP.MIN_IN_APP_KEYBOARD_HEIGHT_SCALE,
            store.keyboardHeightScale(PlaceOrientation.LANDSCAPE), 0.0001f);
        assertEquals(TERMUX_APP.MAX_APP_LAUNCHER_BAR_HEIGHT,
            store.dockHeightScale(PlaceOrientation.LANDSCAPE), 0.0001f);
        assertEquals(TERMUX_APP.MIN_IN_APP_KEYBOARD_BOTTOM_PADDING,
            store.keyboardChinDp(PlaceOrientation.LANDSCAPE));
    }

    @Test
    public void theFirstRunSeedsEachOrientationFromTheValuesTheTerminalResolvedToBefore() {
        // What an install upgrading into the Layout editor is carrying: a keyboard height per
        // orientation, one chin, a shared dock height, and one place that took a dock height of
        // its own while the look keys could still be scoped.
        prefs.edit()
            .putFloat(TERMUX_APP.KEY_IN_APP_KEYBOARD_HEIGHT_SCALE, 1.2f)
            .putFloat(TERMUX_APP.KEY_IN_APP_KEYBOARD_HEIGHT_SCALE_LANDSCAPE, 0.9f)
            .putInt(TERMUX_APP.KEY_IN_APP_KEYBOARD_BOTTOM_PADDING, 14)
            .putFloat(TERMUX_APP.KEY_APP_LAUNCHER_BAR_HEIGHT, 2.0f)
            .putFloat(PlaceLayoutStore.legacyLookKey(PaneWallPage.DISPLAY,
                TERMUX_APP.KEY_APP_LAUNCHER_BAR_HEIGHT), 1.1f)
            .commit();

        PlaceLayoutStore store = store();

        assertEquals(MIGRATED, prefs.getInt("place.migrated", 0));
        assertEquals(1.2f, store.keyboardHeightScale(PlaceOrientation.PORTRAIT), 0.0001f);
        assertEquals(0.9f, store.keyboardHeightScale(PlaceOrientation.LANDSCAPE), 0.0001f);
        for (PlaceOrientation orientation : PlaceOrientation.values()) {
            assertEquals(orientation + " chin", 14, store.keyboardChinDp(orientation));
            // The display kept a dock height of its own; the shared layout is the terminal's,
            // which took the shared one.
            assertEquals(orientation + " dock", 2.0f, store.dockHeightScale(orientation), 0.0001f);
        }
        // Dock height left the scopable look keys, so the old override goes with them.
        assertFalse(prefs.contains(PlaceLayoutStore.legacyLookKey(PaneWallPage.DISPLAY,
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
        store.setKeyboardHeightScale(PlaceOrientation.PORTRAIT, 1.5f);
        store.setKeyboardChinDp(PlaceOrientation.PORTRAIT, 4);
        store.setDockHeightScale(PlaceOrientation.PORTRAIT, 0.8f);

        PlaceLayoutStore reopened = store();

        assertEquals(1.5f, reopened.keyboardHeightScale(PlaceOrientation.PORTRAIT), 0.0001f);
        assertEquals(4, reopened.keyboardChinDp(PlaceOrientation.PORTRAIT));
        assertEquals(0.8f, reopened.dockHeightScale(PlaceOrientation.PORTRAIT), 0.0001f);
    }

    @Test
    public void anInstallAlreadyOnVersionTwoStillGetsTheSizes() {
        prefs.edit()
            .putInt("place.migrated", 2)
            .putFloat(TERMUX_APP.KEY_IN_APP_KEYBOARD_HEIGHT_SCALE, 1.3f)
            .commit();

        PlaceLayoutStore store = store();

        assertEquals(MIGRATED, prefs.getInt("place.migrated", 0));
        assertEquals(1.3f, store.keyboardHeightScale(PlaceOrientation.LANDSCAPE), 0.0001f);
    }

    @Test
    public void landscapeTakesPortraitsKeyboardHeightWhenItNeverHadOneOfItsOwn() {
        // The landscape global fell back to the portrait one, so the seed has to as well.
        prefs.edit().putFloat(TERMUX_APP.KEY_IN_APP_KEYBOARD_HEIGHT_SCALE, 1.35f).commit();

        PlaceLayoutStore store = store();

        assertEquals(1.35f, store.keyboardHeightScale(PlaceOrientation.LANDSCAPE), 0.0001f);
    }

    @Test
    public void clearingAnOrientationGivesTheSizesBackToo() {
        PlaceLayoutStore store = store();
        store.setKeyboardHeightScale(PlaceOrientation.PORTRAIT, 1.5f);
        store.setDockHeightScale(PlaceOrientation.PORTRAIT, 0.8f);
        store.setKeyboardChinDp(PlaceOrientation.PORTRAIT, 12);

        store.clear(PlaceOrientation.PORTRAIT);

        assertEquals(TERMUX_APP.DEFAULT_IN_APP_KEYBOARD_HEIGHT_SCALE,
            store.keyboardHeightScale(PlaceOrientation.PORTRAIT), 0.0001f);
        assertEquals(TERMUX_APP.DEFAULT_APP_LAUNCHER_BAR_HEIGHT,
            store.dockHeightScale(PlaceOrientation.PORTRAIT), 0.0001f);
        assertEquals(TERMUX_APP.DEFAULT_IN_APP_KEYBOARD_BOTTOM_PADDING,
            store.keyboardChinDp(PlaceOrientation.PORTRAIT));
    }

    /**
     * One element's slot as the terse row placement the old model spelled. Only the tests speak
     * it now: the model itself keeps the slot, so a bar on the top edge is a top edge rather than
     * being folded into the bottom, and this helper says so by refusing to name one.
     */
    static PlaceLayout.RowPlacement rowOf(PlaceLayout layout, Element element) {
        Slot slot = layout.slot(element);
        if (slot.hidden) return PlaceLayout.RowPlacement.HIDDEN;
        switch (slot.edge) {
            case LEFT: return PlaceLayout.RowPlacement.LEFT;
            case RIGHT: return PlaceLayout.RowPlacement.RIGHT;
            case BOTTOM: return PlaceLayout.RowPlacement.BOTTOM;
            default: throw new AssertionError("no row placement for " + slot);
        }
    }
}
