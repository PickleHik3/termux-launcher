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
import androidx.preference.SeekBarPreference;
import androidx.preference.SwitchPreferenceCompat;

import com.termux.R;
import com.termux.app.activities.SettingsActivity;
import com.termux.app.fragments.settings.LayoutOverviewPreference;
import com.termux.app.fragments.settings.SegmentedPillPreference;
import com.termux.app.place.PlaceLayout.RowPlacement;
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

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * The Layout page: every row is exposed, the Display tab only appears once the Linux display is
 * on, and the one row phase 5 has not built rendering for yet stays invisible even though it is
 * fully wired to the store.
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

    @Test
    public void everyRowIsExposedAndTheHiddenRowsStayInvisible() {
        LayoutPreferencesFragment fragment = launch();
        PreferenceScreen screen = fragment.getPreferenceScreen();

        Preference overview = screen.findPreference("layout_overview");
        assertTrue(overview instanceof LayoutOverviewPreference);

        assertTrue(screen.findPreference("layout_status_bar") instanceof SegmentedPillPreference);
        assertTrue(screen.findPreference("layout_apps_row") instanceof SegmentedPillPreference);
        assertTrue(screen.findPreference("layout_alphabets_row") instanceof SwitchPreferenceCompat);
        assertTrue(screen.findPreference("layout_alphabets_row_edge")
            instanceof SegmentedPillPreference);
        assertTrue(screen.findPreference("layout_extra_keys") instanceof SegmentedPillPreference);
        assertTrue(screen.findPreference("layout_keyboard_on_enter") instanceof SegmentedPillPreference);
        assertTrue(screen.findPreference("layout_keyboard_mode") instanceof SegmentedPillPreference);
        assertTrue(screen.findPreference("layout_keyboard_form") instanceof SegmentedPillPreference);
        assertTrue(screen.findPreference("layout_grid_columns") instanceof SeekBarPreference);
        assertTrue(screen.findPreference("layout_grid_rows") instanceof SeekBarPreference);
        assertNotNull(screen.findPreference("layout_look"));

        // The bar now stands on any of the four edges, so its row is live.
        assertTrue("status bar row", screen.findPreference("layout_status_bar").isVisible());
        // Phase 5 has not built the overlay keyboard yet, so that row stays invisible even though
        // it is wired all the way through the store.
        assertFalse("keyboard mode row", screen.findPreference("layout_keyboard_mode").isVisible());

        // The keyboard type is a choice on every place, the terminal included.
        assertTrue("keyboard type row", screen.findPreference("layout_keyboard_form").isVisible());

        // The default selection is Terminal, not Home, so the widget grid has nothing to show yet.
        assertFalse("grid columns row", screen.findPreference("layout_grid_columns").isVisible());
        assertFalse("grid rows row", screen.findPreference("layout_grid_rows").isVisible());

        // The apps row is at the bottom by default, so the bar rides under it and its own
        // placement pill has nothing to offer.
        assertFalse("alphabets bar edge pill",
            screen.findPreference("layout_alphabets_row_edge").isVisible());
    }

    @Test
    public void theAlphabetsRowEdgePillShowsOnlyWhileTheBarStandsAlone() {
        Application app = RuntimeEnvironment.getApplication();
        TermuxAppSharedPreferences preferences = TermuxAppSharedPreferences.build(app, true);
        PlaceLayoutStore places = new PlaceLayoutStore(preferences);

        // Apps row at the bottom (the default): the bar rides under it, pill hidden.
        LayoutPreferencesFragment bottomFragment = launch();
        assertFalse("bottom apps row hides the pill", bottomFragment.getPreferenceScreen()
            .findPreference("layout_alphabets_row_edge").isVisible());

        // Apps row hidden entirely: the bar stands alone, pill shown.
        places.setAppsRow(PaneWallPage.TERMINAL, PlaceOrientation.PORTRAIT, RowPlacement.HIDDEN);
        LayoutPreferencesFragment hiddenFragment = launch();
        assertTrue("hidden apps row shows the pill", hiddenFragment.getPreferenceScreen()
            .findPreference("layout_alphabets_row_edge").isVisible());
    }

    @Test
    public void theAlphabetsRowEdgePillShowsWithARailAppsRowInLandscape() {
        // A side apps row has no width to stand in portrait — the store reads it back as bottom —
        // so the rail case that leaves the bar standing alone only exists in landscape.
        Application app = RuntimeEnvironment.getApplication();
        TermuxAppSharedPreferences preferences = TermuxAppSharedPreferences.build(app, true);
        PlaceLayoutStore places = new PlaceLayoutStore(preferences);
        places.setAppsRow(PaneWallPage.TERMINAL, PlaceOrientation.LANDSCAPE, RowPlacement.LEFT);

        RuntimeEnvironment.setQualifiers("+land");
        LayoutPreferencesFragment railFragment = launch();
        assertTrue("rail apps row shows the pill", railFragment.getPreferenceScreen()
            .findPreference("layout_alphabets_row_edge").isVisible());
    }

    @Test
    public void theAlphabetsRowEdgeOffersTwoSegmentsInPortraitAndFourInLandscape() {
        LayoutPreferencesFragment portraitFragment = launch();
        SegmentedPillPreference portraitPill = portraitFragment.getPreferenceScreen()
            .findPreference("layout_alphabets_row_edge");
        assertNotNull(portraitPill);
        assertEquals("portrait has no width for a side column", 2, portraitPill.segmentCount());

        RuntimeEnvironment.setQualifiers("+land");
        LayoutPreferencesFragment landscapeFragment = launch();
        SegmentedPillPreference landscapePill = landscapeFragment.getPreferenceScreen()
            .findPreference("layout_alphabets_row_edge");
        assertNotNull(landscapePill);
        assertEquals("landscape offers every edge", 4, landscapePill.segmentCount());
    }

    @Test
    public void aWriteToTheAlphabetsRowEdgeLandsInTheScopedKey() {
        Application app = RuntimeEnvironment.getApplication();
        TermuxAppSharedPreferences preferences = TermuxAppSharedPreferences.build(app, true);
        LayoutPreferencesFragment.LayoutPreferencesDataStore store =
            new LayoutPreferencesFragment.LayoutPreferencesDataStore(app, preferences);
        store.setSelection(PaneWallPage.TERMINAL, PlaceOrientation.LANDSCAPE);

        store.putString("layout_alphabets_row_edge", "right");
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(200, TimeUnit.MILLISECONDS);

        SharedPreferences prefs = preferences.getSharedPreferences();
        assertEquals("right", prefs.getString("place.terminal.landscape.az_bar", null));
        assertEquals("right", store.getString("layout_alphabets_row_edge", "bottom"));
        // A different orientation on the same place is untouched.
        store.setSelection(PaneWallPage.TERMINAL, PlaceOrientation.PORTRAIT);
        assertEquals("bottom", store.getString("layout_alphabets_row_edge", "bottom"));
    }

    @Test
    public void theAlphabetsRowSwitchIsAlwaysEnabled() {
        Application app = RuntimeEnvironment.getApplication();
        TermuxAppSharedPreferences preferences = TermuxAppSharedPreferences.build(app, true);
        PlaceLayoutStore places = new PlaceLayoutStore(preferences);

        // The default: apps row along the bottom.
        LayoutPreferencesFragment bottomFragment = launch();
        Preference bottomSwitch = bottomFragment.getPreferenceScreen()
            .findPreference("layout_alphabets_row");
        assertNotNull(bottomSwitch);
        assertTrue("enabled with the apps row at the bottom", bottomSwitch.isEnabled());

        // Apps row hidden entirely.
        places.setAppsRow(PaneWallPage.TERMINAL, PlaceOrientation.PORTRAIT, RowPlacement.HIDDEN);
        LayoutPreferencesFragment hiddenFragment = launch();
        Preference hiddenSwitch = hiddenFragment.getPreferenceScreen()
            .findPreference("layout_alphabets_row");
        assertNotNull(hiddenSwitch);
        assertTrue("enabled with the apps row hidden", hiddenSwitch.isEnabled());

        // Apps row on a side rail — the fragment opens on portrait/Terminal by default, and the
        // store does not itself refuse a side placement there, so this exercises the same
        // enablement check without driving the overview's orientation tab.
        places.setAppsRow(PaneWallPage.TERMINAL, PlaceOrientation.PORTRAIT, RowPlacement.LEFT);
        LayoutPreferencesFragment railFragment = launch();
        Preference railSwitch = railFragment.getPreferenceScreen()
            .findPreference("layout_alphabets_row");
        assertNotNull(railSwitch);
        assertTrue("enabled with the apps row on a rail", railSwitch.isEnabled());
    }

    @Test
    public void theKeyboardTypeRowOffersAllThreeTypesInEitherOrientation() {
        LayoutPreferencesFragment portraitFragment = launch();
        SegmentedPillPreference portraitPill = portraitFragment.getPreferenceScreen()
            .findPreference("layout_keyboard_form");
        assertNotNull(portraitPill);
        assertEquals(3, portraitPill.segmentCount());

        RuntimeEnvironment.setQualifiers("+land");
        LayoutPreferencesFragment landscapeFragment = launch();
        SegmentedPillPreference landscapePill = landscapeFragment.getPreferenceScreen()
            .findPreference("layout_keyboard_form");
        assertNotNull(landscapePill);
        assertEquals(3, landscapePill.segmentCount());
    }

    @Test
    public void aWriteToTheKeyboardTypeLandsInTheScopedKey() {
        Application app = RuntimeEnvironment.getApplication();
        TermuxAppSharedPreferences preferences = TermuxAppSharedPreferences.build(app, true);
        LayoutPreferencesFragment.LayoutPreferencesDataStore store =
            new LayoutPreferencesFragment.LayoutPreferencesDataStore(app, preferences);
        store.setSelection(PaneWallPage.TERMINAL, PlaceOrientation.LANDSCAPE);

        store.putString("layout_keyboard_form", "floating");
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(200, TimeUnit.MILLISECONDS);

        SharedPreferences prefs = preferences.getSharedPreferences();
        assertEquals("floating",
            prefs.getString("place.terminal.landscape.keyboard_form", null));
        assertEquals("floating", store.getString("layout_keyboard_form", "docked"));
        // A different orientation on the same place keeps the keyboard it had.
        store.setSelection(PaneWallPage.TERMINAL, PlaceOrientation.PORTRAIT);
        assertEquals("docked", store.getString("layout_keyboard_form", "docked"));
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
    public void aWriteOnTheTerminalLandscapeSelectionLandsScopedAndRestylesWithoutRecreate() {
        Application app = RuntimeEnvironment.getApplication();
        TermuxAppSharedPreferences preferences = TermuxAppSharedPreferences.build(app, true);
        LayoutPreferencesFragment.LayoutPreferencesDataStore store =
            new LayoutPreferencesFragment.LayoutPreferencesDataStore(app, preferences);
        store.setSelection(PaneWallPage.TERMINAL, PlaceOrientation.LANDSCAPE);

        int before = Shadows.shadowOf(app).getBroadcastIntents().size();
        store.putString("layout_apps_row", "right");
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(200, TimeUnit.MILLISECONDS);

        SharedPreferences prefs = preferences.getSharedPreferences();
        assertEquals("right", prefs.getString("place.terminal.landscape.apps_row", null));
        assertEquals("right", store.getString("layout_apps_row", "bottom"));
        // A different orientation on the same place is untouched.
        store.setSelection(PaneWallPage.TERMINAL, PlaceOrientation.PORTRAIT);
        assertEquals("bottom", store.getString("layout_apps_row", "bottom"));

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
    public void writingTheSameValueBackDoesNotQueueASpuriousRestyle() {
        Application app = RuntimeEnvironment.getApplication();
        TermuxAppSharedPreferences preferences = TermuxAppSharedPreferences.build(app, true);
        LayoutPreferencesFragment.LayoutPreferencesDataStore store =
            new LayoutPreferencesFragment.LayoutPreferencesDataStore(app, preferences);
        store.setSelection(PaneWallPage.TERMINAL, PlaceOrientation.PORTRAIT);

        int before = Shadows.shadowOf(app).getBroadcastIntents().size();
        // The shipped default for Terminal/portrait is already "bottom".
        store.putString("layout_apps_row", "bottom");
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(200, TimeUnit.MILLISECONDS);

        assertEquals(before, Shadows.shadowOf(app).getBroadcastIntents().size());
    }

    @Test
    public void aDeepLinkOpensThePageOnTheNamedPlaceAndPointsAtItsRow() {
        Application app = RuntimeEnvironment.getApplication();
        LayoutPreferencesFragment fragment = launch(new Intent(app, SettingsActivity.class)
            .putExtra(SettingsActivity.EXTRA_INITIAL_FRAGMENT,
                LayoutPreferencesFragment.class.getName())
            .putExtra(SettingsActivity.EXTRA_INITIAL_PLACE, PaneWallPage.WIDGETS.toolName())
            .putExtra(SettingsActivity.EXTRA_SCROLL_TO_KEY, "layout_grid_columns"));

        PreferenceScreen screen = fragment.getPreferenceScreen();
        // The grid rows exist only on Home, so their visibility is the page's own answer to
        // which place the deep link selected.
        assertTrue("grid columns row", screen.findPreference("layout_grid_columns").isVisible());
        assertTrue("grid rows row", screen.findPreference("layout_grid_rows").isVisible());
        // The keyboard-mode row belongs to the Display place, so Home must not be showing it.
        assertFalse("keyboard mode row", screen.findPreference("layout_keyboard_mode").isVisible());
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
            PaneWallPage.WIDGETS.toolName(), "layout_grid_columns");
        assertEquals(LayoutPreferencesFragment.class.getName(),
            intent.getStringExtra(SettingsActivity.EXTRA_INITIAL_FRAGMENT));
        assertEquals("widgets", intent.getStringExtra(SettingsActivity.EXTRA_INITIAL_PLACE));
        assertEquals("layout_grid_columns",
            intent.getStringExtra(SettingsActivity.EXTRA_SCROLL_TO_KEY));
    }
}
