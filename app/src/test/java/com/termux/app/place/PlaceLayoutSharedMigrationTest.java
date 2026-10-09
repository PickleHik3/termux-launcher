package com.termux.app.place;

import static com.termux.app.place.PlaceLayoutStoreTest.rowOf;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
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

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Migration version 6: per-place layouts and per-place looks in, one shared layout per
 * orientation and one shared look out (ADR 0003). The terminal's layout and look are what the user
 * was looking at most, so they are what survives; nothing the terminal showed may move.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
@ConscryptMode(ConscryptMode.Mode.OFF)
public class PlaceLayoutSharedMigrationTest {

    /** Any key a place kept of its own before version 6, arrangement or look. */
    private static final Pattern PER_PLACE_KEY = Pattern.compile(
        "^place\\.(home|terminal|display)\\.(portrait|landscape|look)\\..*");

    private SharedPreferences prefs;
    private TermuxAppSharedPreferences launcher;

    @Before
    public void setUp() {
        Application app = RuntimeEnvironment.getApplication();
        prefs = app.getSharedPreferences("place-layout-shared-migration-test",
            Context.MODE_PRIVATE);
        prefs.edit().clear().commit();
        launcher = new TermuxAppSharedPreferences(app, prefs, null);
    }

    private PlaceLayoutStore store() {
        return new PlaceLayoutStore(launcher);
    }

    /** An install on version 5 whose three places were arranged three different ways. */
    private void seedThreeDifferentPlaces() {
        prefs.edit()
            .putInt("place.migrated", 5)
            // The terminal: what has to come through untouched.
            .putString("place.terminal.portrait.status_bar", "bottom")
            .putString("place.terminal.portrait.apps_row", "right")
            .putInt("place.terminal.portrait.apps_row_order", 0)
            .putBoolean("place.terminal.portrait.az_row", false)
            .putString("place.terminal.landscape.extra_keys", "hidden")
            .putString("place.terminal.landscape.keyboard_form", "split")
            .putFloat("place.terminal.portrait.keyboard_height", 1.3f)
            .putFloat("place.terminal.landscape.dock_height", 0.9f)
            .putInt("place.terminal.portrait.keyboard_chin", 12)
            .putBoolean("place.terminal.portrait.status_compact", false)
            .putFloat("place.terminal.landscape.keyboard_float_x", 0.25f)
            .putFloat("place.terminal.landscape.keyboard_float_y", 0.75f)
            // Home: a different arrangement, and the only grid there ever was.
            .putString("place.home.portrait.status_bar", "left")
            .putString("place.home.portrait.apps_row", "hidden")
            .putInt("place.home.portrait.widget_columns", 6)
            .putInt("place.home.landscape.widget_rows", 3)
            .putFloat("place.home.portrait.keyboard_height", 0.7f)
            .putString("place.home.keyboard_on_enter", "open")
            .putBoolean("place.home.keyboard_open", true)
            // The display: its own arrangement, and the only keyboard mode anything read.
            .putString("place.display.landscape.extra_keys", "left")
            .putString("place.display.landscape.keyboard_mode", "resize")
            .putString("place.display.portrait.keyboard_form", "floating")
            .putString("place.display.keyboard_on_enter", "closed")
            .putBoolean("place.display.keyboard_open", true)
            .commit();
    }

    @Test
    public void eachOrientationIsSeededFromTheTerminalsLayout() {
        seedThreeDifferentPlaces();
        PlaceLayoutStore store = store();

        PlaceLayout portrait = store.resolve(PlaceOrientation.PORTRAIT);
        assertEquals(Edge.BOTTOM, portrait.slot(Element.STATUS).edge);
        assertEquals(RowPlacement.RIGHT, rowOf(portrait, Element.APPS));
        assertEquals(0, portrait.slot(Element.APPS).order);
        assertTrue(portrait.slot(Element.AZ).hidden);
        assertEquals("the display's floating type was the display's alone",
            KeyboardForm.DOCKED, portrait.keyboardForm);
        assertEquals(1.3f, store.keyboardHeightScale(PlaceOrientation.PORTRAIT), 0.0001f);
        assertEquals(12, store.keyboardChinDp(PlaceOrientation.PORTRAIT));
        assertFalse(store.isStatusCompact(PlaceOrientation.PORTRAIT));

        PlaceLayout landscape = store.resolve(PlaceOrientation.LANDSCAPE);
        assertEquals(RowPlacement.HIDDEN, rowOf(landscape, Element.EXTRA_KEYS));
        assertEquals(KeyboardForm.SPLIT, landscape.keyboardForm);
        assertEquals(0.9f, store.dockHeightScale(PlaceOrientation.LANDSCAPE), 0.0001f);
        assertEquals(0.25f, store.floatingKeyboardX(PlaceOrientation.LANDSCAPE), 0.0001f);
        assertEquals(0.75f, store.floatingKeyboardY(PlaceOrientation.LANDSCAPE), 0.0001f);
        // Nothing the terminal never stored is pinned: it still answers with the default.
        assertEquals(Edge.TOP, landscape.slot(Element.STATUS).edge);
        assertFalse(prefs.contains("layout.landscape.status_bar"));
    }

    @Test
    public void theGridComesFromHomeAndTheKeyboardModeFromTheDisplay() {
        // The terminal has no grid and never read a keyboard mode, so its copies of those two are
        // not what anybody saw: Home's grid and the display's mode are.
        seedThreeDifferentPlaces();
        PlaceLayoutStore store = store();
        assertEquals(6, store.widgetColumns(PlaceOrientation.PORTRAIT));
        assertEquals(3, store.widgetRows(PlaceOrientation.LANDSCAPE));
        assertEquals(KeyboardMode.RESIZE, store.keyboardMode(PlaceOrientation.LANDSCAPE));
    }

    @Test
    public void everyPlacesOwnCopyIsDropped() {
        seedThreeDifferentPlaces();
        store();
        for (String key : prefs.getAll().keySet())
            assertFalse(key, PER_PLACE_KEY.matcher(key).matches());
        // The keyboard-on-entry choice is gone, and so is Home's keyboard memory: Home always
        // comes back closed.
        assertFalse(prefs.contains("place.home.keyboard_on_enter"));
        assertFalse(prefs.contains("place.display.keyboard_on_enter"));
        assertFalse(prefs.contains("place.home.keyboard_open"));
        assertEquals(PlaceLayoutStore.MIGRATION_VERSION, prefs.getInt("place.migrated", 0));
    }

    @Test
    public void theTerminalAndTheDisplayKeepTheirKeyboardMemory() {
        seedThreeDifferentPlaces();
        prefs.edit().putBoolean("place.terminal.keyboard_open", false).commit();
        PlaceLayoutStore store = store();
        assertTrue(store.wasKeyboardOpen(PaneWallPage.DISPLAY));
        assertFalse(store.wasKeyboardOpen(PaneWallPage.TERMINAL));
        assertFalse(store.wasKeyboardOpen(PaneWallPage.WIDGETS));
    }

    @Test
    public void theTerminalsLookOverrideBecomesTheSharedLook() {
        prefs.edit()
            .putInt("place.migrated", 5)
            .putInt(TERMUX_APP.KEY_TERMINAL_CORNER_RADIUS, 8)
            .putInt(TERMUX_APP.KEY_TERMINAL_PANE_GAP, 3)
            .putInt(TERMUX_APP.KEY_WALLPAPER_BACKDROP_DIM, 10)
            // The terminal took a corner radius of its own; that is what it was showing.
            .putInt(PlaceLayoutStore.legacyLookKey(PaneWallPage.TERMINAL,
                TERMUX_APP.KEY_TERMINAL_CORNER_RADIUS), 20)
            // Another place's overrides were never on the terminal, so the shared value stands.
            .putInt(PlaceLayoutStore.legacyLookKey(PaneWallPage.DISPLAY,
                TERMUX_APP.KEY_TERMINAL_PANE_GAP), 5)
            .putInt(PlaceLayoutStore.legacyLookKey(PaneWallPage.WIDGETS,
                TERMUX_APP.KEY_WALLPAPER_BACKDROP_DIM), 40)
            // A key the terminal alone had, with no shared value beside it.
            .putString(PlaceLayoutStore.legacyLookKey(PaneWallPage.TERMINAL,
                TERMUX_APP.KEY_APP_LAUNCHER_DOCK_STYLE), "rounded")
            .commit();

        store();

        assertEquals(20, prefs.getInt(TERMUX_APP.KEY_TERMINAL_CORNER_RADIUS, -1));
        assertEquals(3, prefs.getInt(TERMUX_APP.KEY_TERMINAL_PANE_GAP, -1));
        assertEquals(10, prefs.getInt(TERMUX_APP.KEY_WALLPAPER_BACKDROP_DIM, -1));
        assertEquals("rounded", prefs.getString(TERMUX_APP.KEY_APP_LAUNCHER_DOCK_STYLE, null));
        for (String key : prefs.getAll().keySet())
            assertFalse(key, PER_PLACE_KEY.matcher(key).matches());
    }

    @Test
    public void aSecondRunChangesNothing() {
        seedThreeDifferentPlaces();
        prefs.edit()
            .putInt(PlaceLayoutStore.legacyLookKey(PaneWallPage.TERMINAL,
                TERMUX_APP.KEY_TERMINAL_CORNER_RADIUS), 20)
            .commit();
        PlaceLayoutStore first = store();
        Map<String, ?> afterFirst = new HashMap<>(prefs.getAll());

        store();
        assertEquals(afterFirst, prefs.getAll());

        // And a choice made after the migration is not folded over by a later start, even with
        // a stray per-place key left in the file by something older.
        first.setAppsRow(PlaceOrientation.PORTRAIT, RowPlacement.LEFT);
        prefs.edit().putString("place.terminal.portrait.apps_row", "right").commit();
        assertEquals(RowPlacement.LEFT,
            rowOf(store().resolve(PlaceOrientation.PORTRAIT), Element.APPS));
    }

    @Test
    public void aFreshFoldFromTheOldestKeysEndsSharedWithNoPerPlaceKeyLeft() {
        // An install that never migrated at all: steps 1 to 5 write per-place keys and step 6
        // folds them in the same start.
        prefs.edit()
            .putString(TERMUX_APP.KEY_APP_LAUNCHER_DOCK_RAIL_SIDE, "right")
            .putFloat(TERMUX_APP.KEY_IN_APP_KEYBOARD_HEIGHT_SCALE, 1.25f)
            .putBoolean(TERMUX_APP.KEY_TOP_PANE_CLOCK_COLLAPSED, false)
            .commit();

        PlaceLayoutStore store = store();

        assertEquals(RowPlacement.RIGHT,
            rowOf(store.resolve(PlaceOrientation.LANDSCAPE), Element.APPS));
        assertEquals(1.25f, store.keyboardHeightScale(PlaceOrientation.PORTRAIT), 0.0001f);
        assertFalse(store.isStatusCompact(PlaceOrientation.LANDSCAPE));
        for (String key : prefs.getAll().keySet())
            assertFalse(key, PER_PLACE_KEY.matcher(key).matches());
    }
}
