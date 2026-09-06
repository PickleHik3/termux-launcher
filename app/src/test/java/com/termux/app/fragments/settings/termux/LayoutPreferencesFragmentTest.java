package com.termux.app.fragments.settings.termux;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Looper;

import androidx.fragment.app.Fragment;
import androidx.preference.Preference;
import androidx.preference.PreferenceScreen;
import androidx.preference.SeekBarPreference;
import androidx.preference.SwitchPreferenceCompat;

import com.termux.R;
import com.termux.app.activities.SettingsActivity;
import com.termux.app.fragments.settings.LayoutOverviewPreference;
import com.termux.app.fragments.settings.SegmentedPillPreference;
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
        Application app = RuntimeEnvironment.getApplication();
        Intent intent = new Intent(app, SettingsActivity.class)
            .putExtra(SettingsActivity.EXTRA_INITIAL_FRAGMENT, LayoutPreferencesFragment.class.getName());
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
        assertTrue(screen.findPreference("layout_extra_keys") instanceof SegmentedPillPreference);
        assertTrue(screen.findPreference("layout_keyboard_on_enter") instanceof SegmentedPillPreference);
        assertTrue(screen.findPreference("layout_keyboard_mode") instanceof SegmentedPillPreference);
        assertTrue(screen.findPreference("layout_grid_columns") instanceof SeekBarPreference);
        assertTrue(screen.findPreference("layout_grid_rows") instanceof SeekBarPreference);
        assertNotNull(screen.findPreference("layout_look"));

        // The bar now stands on any of the four edges, so its row is live.
        assertTrue("status bar row", screen.findPreference("layout_status_bar").isVisible());
        // Phase 5 has not built the overlay keyboard yet, so that row stays invisible even though
        // it is wired all the way through the store.
        assertFalse("keyboard mode row", screen.findPreference("layout_keyboard_mode").isVisible());

        // The default selection is Terminal, not Home, so the widget grid has nothing to show yet.
        assertFalse("grid columns row", screen.findPreference("layout_grid_columns").isVisible());
        assertFalse("grid rows row", screen.findPreference("layout_grid_rows").isVisible());
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
}
