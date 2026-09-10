package com.termux.app.fragments.settings.termux;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.preference.Preference;
import androidx.preference.PreferenceScreen;

import com.termux.R;
import com.termux.app.activities.SettingsActivity;
import com.termux.app.fragments.settings.LayoutChooserModel;
import com.termux.app.fragments.settings.LayoutElement;
import com.termux.app.fragments.settings.LayoutElementRowPreference;
import com.termux.app.fragments.settings.LayoutOverviewPreference;
import com.termux.app.fragments.settings.PlaceMiniatureView;
import com.termux.app.place.PlaceLayoutStore;
import com.termux.app.place.PlaceOrientation;
import com.termux.app.wall.PaneWallPage;
import com.termux.shared.termux.TermuxConstants;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.ConscryptMode;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * The Layout page after the v2 restructure: one compact row per element with its portrait and
 * landscape values, a chooser behind each of them writing the scoped key for the orientation it
 * was picked under, the Widget grid row on Home alone, and the Display place offered only once the
 * Linux display is on.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
@ConscryptMode(ConscryptMode.Mode.OFF)
public class LayoutPreferencesFragmentTest {

    private LayoutPreferencesFragment launch() {
        return launch(new Intent(RuntimeEnvironment.getApplication(), SettingsActivity.class)
            .putExtra(SettingsActivity.EXTRA_INITIAL_FRAGMENT,
                LayoutPreferencesFragment.class.getName()));
    }

    private LayoutPreferencesFragment launch(@NonNull Intent intent) {
        ActivityController<SettingsActivity> controller =
            Robolectric.buildActivity(SettingsActivity.class, intent).create().start().resume();
        SettingsActivity activity = controller.get();
        activity.getSupportFragmentManager().executePendingTransactions();
        Fragment fragment = activity.getSupportFragmentManager().findFragmentById(R.id.settings);
        assertTrue(fragment instanceof LayoutPreferencesFragment);
        return (LayoutPreferencesFragment) fragment;
    }

    private static String summary(LayoutPreferencesFragment fragment, LayoutElement element) {
        Preference row = fragment.getPreferenceScreen().findPreference(element.key());
        assertNotNull("row for " + element, row);
        return String.valueOf(row.getSummary());
    }

    private static List<LayoutChooserModel.Pills> pills(Context context, PlaceLayoutStore places,
                                                        PaneWallPage place,
                                                        LayoutElement element) {
        List<LayoutChooserModel.Pills> found = new ArrayList<>();
        for (LayoutChooserModel.Group group
            : LayoutChooserModel.groups(context, places, place, element)) {
            if (group instanceof LayoutChooserModel.Pills) {
                found.add((LayoutChooserModel.Pills) group);
            }
        }
        return found;
    }

    private static List<LayoutChooserModel.Counter> counters(Context context,
                                                             PlaceLayoutStore places,
                                                             PaneWallPage place,
                                                             LayoutElement element) {
        List<LayoutChooserModel.Counter> found = new ArrayList<>();
        for (LayoutChooserModel.Group group
            : LayoutChooserModel.groups(context, places, place, element)) {
            if (group instanceof LayoutChooserModel.Counter) {
                found.add((LayoutChooserModel.Counter) group);
            }
        }
        return found;
    }

    @Test
    public void thePageIsOneRowPerElementWithTheLookRowLast() {
        LayoutPreferencesFragment fragment = launch();
        PreferenceScreen screen = fragment.getPreferenceScreen();

        assertTrue(screen.findPreference("layout_overview") instanceof LayoutOverviewPreference);
        LayoutElement[] order = LayoutElement.values();
        assertEquals("Status bar, pinned apps, A–Z, extra keys, keyboard, widget grid",
            6, order.length);
        for (int i = 0; i < order.length; i++) {
            Preference row = screen.findPreference(order[i].key());
            assertTrue("row " + order[i], row instanceof LayoutElementRowPreference);
            // The overview stands first, so the rows follow it in the enum's own order.
            assertEquals("row order for " + order[i], i + 1, indexOf(screen, order[i].key()));
        }
        assertEquals("the Look row is last", screen.getPreferenceCount() - 1,
            indexOf(screen, "layout_look"));

        // No captions anywhere: a row says its values, nothing else.
        for (LayoutElement element : order) {
            if (!element.isOn(PaneWallPage.TERMINAL)) continue;
            assertTrue("row " + element + " summarises its values",
                summary(fragment, element).contains("·"));
        }
    }

    private static int indexOf(PreferenceScreen screen, String key) {
        for (int i = 0; i < screen.getPreferenceCount(); i++) {
            if (key.equals(screen.getPreference(i).getKey())) return i;
        }
        return -1;
    }

    @Test
    public void theWidgetGridRowIsOnHomeAndNotOnTheTerminal() {
        LayoutPreferencesFragment fragment = launch();
        Preference grid = fragment.getPreferenceScreen()
            .findPreference(LayoutElement.WIDGET_GRID.key());
        assertNotNull(grid);
        assertFalse("the terminal has no widget grid", grid.isVisible());

        fragment.selectPlace(PaneWallPage.WIDGETS);
        assertTrue("home has one", grid.isVisible());
        // Every other row is on every place.
        for (LayoutElement element : LayoutElement.values()) {
            if (element == LayoutElement.WIDGET_GRID) continue;
            assertTrue("row " + element + " on home",
                fragment.getPreferenceScreen().findPreference(element.key()).isVisible());
        }
    }

    @Test
    public void theDisplayTabOnlyAppearsWhenTheLinuxDisplayIsOn() {
        LayoutPreferencesFragment offFragment = launch();
        LayoutOverviewPreference overview =
            offFragment.getPreferenceScreen().findPreference("layout_overview");
        assertNotNull(overview);
        assertFalse("fresh install: display is off", overview.isDisplayTabVisible());

        Context context = RuntimeEnvironment.getApplication();
        TermuxAppSharedPreferences preferences = TermuxAppSharedPreferences.build(context, true);
        preferences.setX11DisplayEnabled(true);

        LayoutPreferencesFragment onFragment = launch();
        LayoutOverviewPreference onOverview =
            onFragment.getPreferenceScreen().findPreference("layout_overview");
        assertNotNull(onOverview);
        assertEquals("display enabled iff BuildConfig.X11_SERVER too",
            com.termux.BuildConfig.X11_SERVER, onOverview.isDisplayTabVisible());
    }

    @Test
    public void aStatusBarPickLandsOnTheOrientationItWasPickedUnderAndTheRowRereadsIt() {
        Application app = RuntimeEnvironment.getApplication();
        LayoutPreferencesFragment fragment = launch();
        PlaceLayoutStore places = fragment.places();
        assertNotNull(places);

        List<LayoutChooserModel.Pills> groups =
            pills(app, places, PaneWallPage.TERMINAL, LayoutElement.STATUS_BAR);
        assertEquals("one pill row per orientation", 2, groups.size());
        assertEquals("portrait has no width for a side column", 2, groups.get(0).values.length);
        assertEquals("landscape offers every edge", 4, groups.get(1).values.length);

        groups.get(1).writer.write("right");
        fragment.onLayoutWritten();
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(200, TimeUnit.MILLISECONDS);

        SharedPreferences prefs =
            TermuxAppSharedPreferences.build(app, true).getSharedPreferences();
        assertEquals("right", prefs.getString("place.terminal.landscape.status_bar", null));
        assertNull("portrait untouched",
            prefs.getString("place.terminal.portrait.status_bar", null));
        assertEquals("Top · Right", summary(fragment, LayoutElement.STATUS_BAR));
    }

    @Test
    public void aPinnedAppsPickAndAnExtraKeysPickLandOnTheirOwnScopedKeys() {
        Application app = RuntimeEnvironment.getApplication();
        LayoutPreferencesFragment fragment = launch();
        PlaceLayoutStore places = fragment.places();
        assertNotNull(places);

        pills(app, places, PaneWallPage.TERMINAL, LayoutElement.PINNED_APPS).get(0)
            .writer.write("hidden");
        pills(app, places, PaneWallPage.TERMINAL, LayoutElement.EXTRA_KEYS).get(1)
            .writer.write("left");
        fragment.onLayoutWritten();

        SharedPreferences prefs =
            TermuxAppSharedPreferences.build(app, true).getSharedPreferences();
        assertEquals("hidden", prefs.getString("place.terminal.portrait.apps_row", null));
        assertEquals("left", prefs.getString("place.terminal.landscape.extra_keys", null));
        assertEquals("Hidden · Left", summary(fragment, LayoutElement.PINNED_APPS));
        assertEquals("Bottom · Left", summary(fragment, LayoutElement.EXTRA_KEYS));
    }

    @Test
    public void theAzChooserGainsItsOwnEdgeOnlyWhileTheBarStandsAlone() {
        Application app = RuntimeEnvironment.getApplication();
        LayoutPreferencesFragment fragment = launch();
        PlaceLayoutStore places = fragment.places();
        assertNotNull(places);

        // Portrait pins the apps along the bottom and the bar rides under them, so portrait gets
        // one shown/hidden pill; landscape's pinned apps are a rail, so there the bar stands alone
        // and picks an edge of its own.
        assertEquals(3, pills(app, places, PaneWallPage.TERMINAL, LayoutElement.AZ_INDEX).size());
        assertEquals("Shown · Bottom", summary(fragment, LayoutElement.AZ_INDEX));

        places.setAppsRow(PaneWallPage.TERMINAL, PlaceOrientation.PORTRAIT,
            com.termux.app.place.PlaceLayout.RowPlacement.HIDDEN);
        List<LayoutChooserModel.Pills> standingAlone =
            pills(app, places, PaneWallPage.TERMINAL, LayoutElement.AZ_INDEX);
        assertEquals("portrait gains an edge pill too", 4, standingAlone.size());

        standingAlone.get(1).writer.write("top");
        fragment.onLayoutWritten();
        SharedPreferences prefs =
            TermuxAppSharedPreferences.build(app, true).getSharedPreferences();
        assertEquals("top", prefs.getString("place.terminal.portrait.az_bar", null));
        assertEquals("Top · Bottom", summary(fragment, LayoutElement.AZ_INDEX));

        // Hiding it is the same stored switch as before, per orientation.
        standingAlone.get(0).writer.write("hidden");
        fragment.onLayoutWritten();
        assertFalse(prefs.getBoolean("place.terminal.portrait.az_row", true));
        assertEquals("Hidden · Bottom", summary(fragment, LayoutElement.AZ_INDEX));
    }

    @Test
    public void theKeyboardChooserWritesTypePerOrientationOnEnterOncePerPlace() {
        Application app = RuntimeEnvironment.getApplication();
        LayoutPreferencesFragment fragment = launch();
        PlaceLayoutStore places = fragment.places();
        assertNotNull(places);

        List<LayoutChooserModel.Pills> groups =
            pills(app, places, PaneWallPage.TERMINAL, LayoutElement.KEYBOARD);
        assertEquals("a type pill per orientation plus one on enter", 3, groups.size());

        groups.get(1).writer.write("floating");
        groups.get(2).writer.write("open");
        fragment.onLayoutWritten();

        SharedPreferences prefs =
            TermuxAppSharedPreferences.build(app, true).getSharedPreferences();
        assertEquals("floating", prefs.getString("place.terminal.landscape.keyboard_form", null));
        assertNull("portrait keeps the keyboard it had",
            prefs.getString("place.terminal.portrait.keyboard_form", null));
        assertEquals("open", prefs.getString("place.terminal.keyboard_on_enter", null));
        assertEquals("Docked · Floating", summary(fragment, LayoutElement.KEYBOARD));
    }

    @Test
    public void theKeyboardModePillsAreTheDisplaysAlone() {
        Application app = RuntimeEnvironment.getApplication();
        LayoutPreferencesFragment fragment = launch();
        PlaceLayoutStore places = fragment.places();
        assertNotNull(places);

        assertEquals("terminal: type per orientation plus on enter",
            3, pills(app, places, PaneWallPage.TERMINAL, LayoutElement.KEYBOARD).size());
        List<LayoutChooserModel.Pills> display =
            pills(app, places, PaneWallPage.DISPLAY, LayoutElement.KEYBOARD);
        assertEquals("display adds a mode pill per orientation", 5, display.size());

        display.get(3).writer.write("overlay");
        fragment.onLayoutWritten();
        SharedPreferences prefs =
            TermuxAppSharedPreferences.build(app, true).getSharedPreferences();
        assertEquals("overlay", prefs.getString("place.display.portrait.keyboard_mode", null));
    }

    @Test
    public void theWidgetGridChooserWritesColumnsAndRowsPerOrientation() {
        Application app = RuntimeEnvironment.getApplication();
        LayoutPreferencesFragment fragment = launch();
        PlaceLayoutStore places = fragment.places();
        assertNotNull(places);
        fragment.selectPlace(PaneWallPage.WIDGETS);

        List<LayoutChooserModel.Counter> groups =
            counters(app, places, PaneWallPage.WIDGETS, LayoutElement.WIDGET_GRID);
        assertEquals("columns and rows, per orientation", 4, groups.size());

        groups.get(0).writer.write(6);
        groups.get(3).writer.write(3);
        fragment.onLayoutWritten();

        SharedPreferences prefs =
            TermuxAppSharedPreferences.build(app, true).getSharedPreferences();
        assertEquals(6, prefs.getInt("place.home.portrait.widget_columns", -1));
        assertEquals(3, prefs.getInt("place.home.landscape.widget_rows", -1));
        assertEquals("6×5 · 4×3", summary(fragment, LayoutElement.WIDGET_GRID));
    }

    @Test
    public void tappingAMiniatureBandOpensThatBandsChooser() {
        LayoutPreferencesFragment fragment = launch();
        List<LayoutElement> opened = new ArrayList<>();
        fragment.setChooserOpener(opened::add);

        fragment.tapMiniatureBlock(PlaceMiniatureView.Block.STATUS_BAR);
        fragment.tapMiniatureBlock(PlaceMiniatureView.Block.APPS_ROW);
        fragment.tapMiniatureBlock(PlaceMiniatureView.Block.ALPHABETS_ROW);
        fragment.tapMiniatureBlock(PlaceMiniatureView.Block.EXTRA_KEYS);
        // On the terminal the canvas is the terminal itself, and the keyboard is what stands over
        // it; on home it is the widget grid.
        fragment.tapMiniatureBlock(PlaceMiniatureView.Block.CANVAS);
        fragment.selectPlace(PaneWallPage.WIDGETS);
        fragment.tapMiniatureBlock(PlaceMiniatureView.Block.CANVAS);

        assertEquals(Arrays.asList(LayoutElement.STATUS_BAR, LayoutElement.PINNED_APPS,
            LayoutElement.AZ_INDEX, LayoutElement.EXTRA_KEYS, LayoutElement.KEYBOARD,
            LayoutElement.WIDGET_GRID), opened);
    }

    @Test
    public void aChooserWriteRestylesWithoutRecreate() {
        Application app = RuntimeEnvironment.getApplication();
        LayoutPreferencesFragment fragment = launch();
        PlaceLayoutStore places = fragment.places();
        assertNotNull(places);

        int before = Shadows.shadowOf(app).getBroadcastIntents().size();
        pills(app, places, PaneWallPage.TERMINAL, LayoutElement.PINNED_APPS).get(1)
            .writer.write("right");
        fragment.onLayoutWritten();
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(200, TimeUnit.MILLISECONDS);

        List<Intent> broadcasts = Shadows.shadowOf(app).getBroadcastIntents();
        assertTrue("a restyle broadcast was sent", broadcasts.size() > before);
        Intent styling = broadcasts.get(broadcasts.size() - 1);
        assertEquals(TermuxConstants.TERMUX_APP.TERMUX_ACTIVITY.ACTION_RELOAD_STYLE,
            styling.getAction());
        assertFalse("no recreate needed for a Layout page write",
            styling.getBooleanExtra(TermuxConstants.TERMUX_APP.TERMUX_ACTIVITY.EXTRA_RECREATE_ACTIVITY,
                true));
    }

    @Test
    public void aDeepLinkOpensThePageOnTheNamedPlaceAndPointsAtItsRow() {
        Application app = RuntimeEnvironment.getApplication();
        LayoutPreferencesFragment fragment = launch(new Intent(app, SettingsActivity.class)
            .putExtra(SettingsActivity.EXTRA_INITIAL_FRAGMENT,
                LayoutPreferencesFragment.class.getName())
            .putExtra(SettingsActivity.EXTRA_INITIAL_PLACE, PaneWallPage.WIDGETS.toolName())
            .putExtra(SettingsActivity.EXTRA_SCROLL_TO_KEY,
                LayoutPreferencesFragment.KEY_WIDGET_GRID));

        PreferenceScreen screen = fragment.getPreferenceScreen();
        // The Widget grid row exists only on Home, so its visibility is the page's own answer to
        // which place the deep link selected.
        assertTrue("widget grid row",
            screen.findPreference(LayoutPreferencesFragment.KEY_WIDGET_GRID).isVisible());
    }

    @Test
    public void theExtrasReachTheInitialFragmentAsArguments() {
        Application app = RuntimeEnvironment.getApplication();
        LayoutPreferencesFragment fragment = launch(new Intent(app, SettingsActivity.class)
            .putExtra(SettingsActivity.EXTRA_INITIAL_FRAGMENT,
                LayoutPreferencesFragment.class.getName())
            .putExtra(SettingsActivity.EXTRA_INITIAL_PLACE, PaneWallPage.WIDGETS.toolName())
            .putExtra(SettingsActivity.EXTRA_SCROLL_TO_KEY, "layout_grid_rows"));

        Bundle arguments = fragment.getArguments();
        assertNotNull(arguments);
        assertEquals("widgets", arguments.getString(SettingsActivity.EXTRA_INITIAL_PLACE));
        assertEquals("layout_grid_rows", arguments.getString(SettingsActivity.EXTRA_SCROLL_TO_KEY));
    }

    @Test
    public void theDeepLinkExtrasAreParsedTolerantly() {
        assertEquals(PaneWallPage.TERMINAL,
            LayoutPreferencesFragment.placeFromArguments(null, PaneWallPage.TERMINAL));
        assertNull(LayoutPreferencesFragment.scrollKeyFromArguments(null));

        Bundle empty = new Bundle();
        assertEquals(PaneWallPage.TERMINAL,
            LayoutPreferencesFragment.placeFromArguments(empty, PaneWallPage.TERMINAL));
        assertNull(LayoutPreferencesFragment.scrollKeyFromArguments(empty));

        Bundle blank = new Bundle();
        blank.putString(SettingsActivity.EXTRA_INITIAL_PLACE, "");
        blank.putString(SettingsActivity.EXTRA_SCROLL_TO_KEY, "");
        assertEquals(PaneWallPage.DISPLAY,
            LayoutPreferencesFragment.placeFromArguments(blank, PaneWallPage.DISPLAY));
        assertNull(LayoutPreferencesFragment.scrollKeyFromArguments(blank));

        Bundle nonsense = new Bundle();
        nonsense.putString(SettingsActivity.EXTRA_INITIAL_PLACE, "kitchen");
        assertEquals("an unknown place leaves the page where it would have opened",
            PaneWallPage.TERMINAL,
            LayoutPreferencesFragment.placeFromArguments(nonsense, PaneWallPage.TERMINAL));

        Bundle named = new Bundle();
        named.putString(SettingsActivity.EXTRA_INITIAL_PLACE, " Widgets ");
        named.putString(SettingsActivity.EXTRA_SCROLL_TO_KEY, "layout_grid_columns");
        assertEquals(PaneWallPage.WIDGETS,
            LayoutPreferencesFragment.placeFromArguments(named, PaneWallPage.TERMINAL));
        assertEquals("layout_grid_columns",
            LayoutPreferencesFragment.scrollKeyFromArguments(named));
    }

    @Test
    public void aDeepLinkedIntentBuildsFromOneFactory() {
        Application app = RuntimeEnvironment.getApplication();
        Intent intent = SettingsActivity.createFragmentIntent(app,
            LayoutPreferencesFragment.class, R.string.settings_destination_layout,
            PaneWallPage.WIDGETS.toolName(), LayoutPreferencesFragment.KEY_WIDGET_GRID);
        assertEquals(LayoutPreferencesFragment.class.getName(),
            intent.getStringExtra(SettingsActivity.EXTRA_INITIAL_FRAGMENT));
        assertEquals("widgets", intent.getStringExtra(SettingsActivity.EXTRA_INITIAL_PLACE));
        assertEquals(LayoutPreferencesFragment.KEY_WIDGET_GRID,
            intent.getStringExtra(SettingsActivity.EXTRA_SCROLL_TO_KEY));
    }
}
