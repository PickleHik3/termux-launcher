package com.termux.app.fragments.settings.termux;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.preference.Preference;
import androidx.preference.PreferenceScreen;

import com.termux.R;
import com.termux.app.TermuxActivity;
import com.termux.app.activities.SettingsActivity;
import com.termux.app.wall.PaneWallPage;
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

/**
 * The Layout page as the door into the Layout editor, and nothing else: three place rows, each
 * closing Settings and deep-linking the launcher to that place with the editor up. Everything a
 * place's layout holds is picked in the editor now, so the page has no rows of its own.
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
    public void thePageIsThreeDoorsAndNothingElse() {
        LayoutPreferencesFragment fragment = launch();
        PreferenceScreen screen = fragment.getPreferenceScreen();

        assertEquals("Home, Terminal, Display", 3, LayoutPreferencesFragment.KEY_PLACE_ROWS.length);
        String[] titles = {"Home", "Terminal", "Display"};
        for (int i = 0; i < LayoutPreferencesFragment.KEY_PLACE_ROWS.length; i++) {
            String key = LayoutPreferencesFragment.KEY_PLACE_ROWS[i];
            Preference row = screen.findPreference(key);
            assertNotNull("place row " + key, row);
            assertEquals("place row " + key + " stands where it was put", i, indexOf(screen, key));
            assertEquals(titles[i], String.valueOf(row.getTitle()));
        }
        assertEquals("the three doors are the whole page", 3, screen.getPreferenceCount());
    }

    @Test
    public void theElementRowsHaveLeftForTheEditor() {
        PreferenceScreen screen = launch().getPreferenceScreen();

        for (String key : new String[] {"layout_row_status_bar", "layout_row_pinned_apps",
            "layout_row_az_index", "layout_row_extra_keys", "layout_row_keyboard",
            "layout_row_widget_grid", "layout_overview"}) {
            assertFalse("the page still carries " + key,
                indexOf(screen, key) >= 0);
        }
    }

    @Test
    public void eachPlaceRowClosesSettingsAndDeepLinksTheLauncherToThatPlacesLayoutEditor() {
        Application app = RuntimeEnvironment.getApplication();
        PaneWallPage[] places =
            {PaneWallPage.WIDGETS, PaneWallPage.TERMINAL, PaneWallPage.DISPLAY};
        for (int i = 0; i < LayoutPreferencesFragment.KEY_PLACE_ROWS.length; i++) {
            ActivityController<SettingsActivity> controller = Robolectric.buildActivity(
                SettingsActivity.class,
                new Intent(app, SettingsActivity.class).putExtra(
                    SettingsActivity.EXTRA_INITIAL_FRAGMENT,
                    LayoutPreferencesFragment.class.getName()))
                .create().start().resume();
            SettingsActivity activity = controller.get();
            activity.getSupportFragmentManager().executePendingTransactions();
            LayoutPreferencesFragment fragment = (LayoutPreferencesFragment)
                activity.getSupportFragmentManager().findFragmentById(R.id.settings);
            assertNotNull(fragment);

            Preference row = fragment.getPreferenceScreen()
                .findPreference(LayoutPreferencesFragment.KEY_PLACE_ROWS[i]);
            assertNotNull(row);
            row.performClick();

            Intent started = Shadows.shadowOf(activity).getNextStartedActivity();
            assertNotNull("the launcher was asked for", started);
            assertEquals(TermuxActivity.class.getName(),
                started.getComponent() == null ? null : started.getComponent().getClassName());
            assertTrue("with the Layout editor",
                started.getBooleanExtra(TermuxActivity.EXTRA_LAYOUT_EDITOR, false));
            assertEquals("on this place", places[i].toolName(),
                started.getStringExtra(TermuxActivity.EXTRA_LAYOUT_EDITOR_PLACE));
            assertTrue("Settings closes behind it", activity.isFinishing());
        }
    }

    @Test
    public void theDisplayRowOnlyStandsWhenTheLinuxDisplayIsOn() {
        LayoutPreferencesFragment offFragment = launch();
        Preference off = offFragment.getPreferenceScreen()
            .findPreference(LayoutPreferencesFragment.KEY_PLACE_ROWS[2]);
        assertNotNull(off);
        assertFalse("fresh install: display is off", off.isVisible());

        Context context = RuntimeEnvironment.getApplication();
        TermuxAppSharedPreferences preferences = TermuxAppSharedPreferences.build(context, true);
        preferences.setX11DisplayEnabled(true);

        LayoutPreferencesFragment onFragment = launch();
        Preference on = onFragment.getPreferenceScreen()
            .findPreference(LayoutPreferencesFragment.KEY_PLACE_ROWS[2]);
        assertNotNull(on);
        assertEquals("display enabled iff BuildConfig.X11_SERVER too",
            com.termux.BuildConfig.X11_SERVER, on.isVisible());
    }

    @Test
    public void theDeepLinkedIntentBuildsFromOneFactory() {
        Application app = RuntimeEnvironment.getApplication();
        Intent intent =
            LayoutPreferencesFragment.layoutEditorIntent(app, PaneWallPage.WIDGETS);

        assertEquals(TermuxActivity.class.getName(),
            intent.getComponent() == null ? null : intent.getComponent().getClassName());
        assertTrue(intent.getBooleanExtra(TermuxActivity.EXTRA_LAYOUT_EDITOR, false));
        assertEquals("widgets", intent.getStringExtra(TermuxActivity.EXTRA_LAYOUT_EDITOR_PLACE));
        assertTrue("the launcher comes forward rather than stacking",
            (intent.getFlags() & Intent.FLAG_ACTIVITY_SINGLE_TOP) != 0);
    }

    private static int indexOf(PreferenceScreen screen, String key) {
        for (int i = 0; i < screen.getPreferenceCount(); i++) {
            if (key.equals(screen.getPreference(i).getKey())) return i;
        }
        return -1;
    }
}
