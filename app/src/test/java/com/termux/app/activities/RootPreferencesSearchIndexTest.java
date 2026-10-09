package com.termux.app.activities;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.content.Intent;
import android.os.Build;

import androidx.fragment.app.Fragment;
import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceGroup;
import androidx.preference.PreferenceScreen;

import com.termux.R;
import com.termux.app.fragments.settings.SettingsSearchPreference;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.ConscryptMode;

/**
 * Phase 6 restructured the settings root into one destination per question and split the old
 * combined "Terminal & status" page in two. The root row order and keys must match that map, and
 * the lazily-built child search index (keyed by destination row, see
 * {@code SettingsActivity.RootPreferencesFragment.CHILD_XML_RESOURCES}) must cover the new
 * "terminal" and "status_bar" destinations and no longer carry the retired "terminal_status" one.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
@ConscryptMode(ConscryptMode.Mode.OFF)
public class RootPreferencesSearchIndexTest {

    private static final String[] EXPECTED_GROUPS = {
        "root_personalization", "root_workspace", "root_app"
    };
    private static final String[][] EXPECTED_ROWS = {
        {"wallpaper_style", "launcher_apps", "status_bar", "notifications"},
        {"terminal", "keyboard_input", "display", "on_device_ai"},
        {"app_behavior", "services_permissions", "advanced_diagnostics", "about_support"}
    };

    private SettingsActivity.RootPreferencesFragment launch() {
        Application app = RuntimeEnvironment.getApplication();
        Intent intent = new Intent(app, SettingsActivity.class);
        ActivityController<SettingsActivity> controller =
            Robolectric.buildActivity(SettingsActivity.class, intent).create().start().resume();
        SettingsActivity activity = controller.get();
        activity.getSupportFragmentManager().executePendingTransactions();
        Fragment fragment = activity.getSupportFragmentManager().findFragmentById(R.id.settings);
        assertTrue(fragment instanceof SettingsActivity.RootPreferencesFragment);
        return (SettingsActivity.RootPreferencesFragment) fragment;
    }

    /** The search box, then the usage mode row on its own, then the first task group. */
    @Test
    public void theUsageModeRowStandsAboveTheLauncherHeader() {
        SettingsActivity.RootPreferencesFragment root = launch();
        PreferenceScreen screen = root.getPreferenceScreen();
        assertEquals("settings_search", screen.getPreference(0).getKey());
        Preference useAs = screen.getPreference(1);
        assertEquals("app_launcher_use_case_mode", useAs.getKey());
        assertTrue(useAs instanceof androidx.preference.ListPreference);
        assertEquals("a fresh install is the terminal with the home screen",
            useAs.getContext().getString(R.string.settings_use_as_home),
            String.valueOf(useAs.getSummary()));
        assertTrue(screen.getPreference(2) instanceof PreferenceCategory);
    }

    @Test
    public void searchingForModeFindsTheUsageModeRow() {
        SettingsActivity.RootPreferencesFragment root = launch();
        SettingsSearchPreference search = root.findPreference("settings_search");
        search.getOnQueryChangedListener().onQueryChanged("mode");

        assertTrue(isVisible(root, "app_launcher_use_case_mode"));
        assertFalse("no page below carries the words", isVisible(root, "status_bar"));

        search.getOnQueryChangedListener().onQueryChanged("");
        Preference useAs = root.findPreference("app_launcher_use_case_mode");
        assertEquals("the summary survives a cleared search",
            useAs.getContext().getString(R.string.settings_use_as_home),
            String.valueOf(useAs.getSummary()));
    }

    @Test
    public void taskGroupRowsAreInTheSpecOrder() {
        SettingsActivity.RootPreferencesFragment root = launch();
        PreferenceScreen screen = root.getPreferenceScreen();
        for (int g = 0; g < EXPECTED_GROUPS.length; g++) {
            PreferenceCategory group = (PreferenceCategory) screen.getPreference(2 + g);
            assertEquals(EXPECTED_GROUPS[g], group.getKey());
            assertEquals(EXPECTED_ROWS[g].length, group.getPreferenceCount());
            for (int i = 0; i < EXPECTED_ROWS[g].length; i++) {
                assertEquals("group " + g + " row " + i, EXPECTED_ROWS[g][i],
                    group.getPreference(i).getKey());
            }
        }
    }

    @Test
    public void searchingASubpageSettingFindsItsDestination() {
        SettingsActivity.RootPreferencesFragment root = launch();
        SettingsSearchPreference search = root.findPreference("settings_search");
        search.getOnQueryChangedListener().onQueryChanged("vibration");
        assertTrue("App behavior holds the vibration switches", isVisible(root, "app_behavior"));
        assertTrue("so does the keyboard's typing page", isVisible(root, "keyboard_input"));
        search.getOnQueryChangedListener().onQueryChanged("dpi");
        assertTrue("the Display resolution page is indexed", isVisible(root, "display"));
        search.getOnQueryChangedListener().onQueryChanged("");
        assertEquals("a cleared search restores the row's own page",
            "com.termux.app.fragments.settings.termux.KeyboardPreferencesFragment",
            root.findPreference("keyboard_input").getFragment());
        // "LAN" alone also matches the remote model's "free plan" note, a second page.
        search.getOnQueryChangedListener().onQueryChanged("LAN access");
        assertTrue("the Local API page is indexed under On-device AI", isVisible(root, "on_device_ai"));
        assertEquals("a hit on one subpage opens that page",
            "com.termux.app.fragments.settings.termux.TaiApiPreferencesFragment",
            root.findPreference("on_device_ai").getFragment());
        search.getOnQueryChangedListener().onQueryChanged("");
        assertEquals("a cleared search restores the overview",
            "com.termux.app.fragments.settings.termux.TaiPreferencesFragment",
            root.findPreference("on_device_ai").getFragment());
    }

    @Test
    public void searchingALazyModeTermFindsAppBehaviorNotStatusBar() {
        SettingsActivity.RootPreferencesFragment root = launch();
        SettingsSearchPreference search = root.findPreference("settings_search");
        assertTrue(search.getOnQueryChangedListener() != null);
        search.getOnQueryChangedListener().onQueryChanged("lazy mode");

        assertTrue("Lazy mode lives on App behavior now", isVisible(root, "app_behavior"));
        assertFalse("status bar page has no lazy mode row", isVisible(root, "status_bar"));
    }

    @Test
    public void searchingAClockTermFindsTheStatusBarDestinationOnly() {
        SettingsActivity.RootPreferencesFragment root = launch();
        SettingsSearchPreference search = root.findPreference("settings_search");
        search.getOnQueryChangedListener().onQueryChanged("12-hour");

        assertTrue("status bar page contains the 12-hour clock", isVisible(root, "status_bar"));
        assertFalse("terminal page has no clock row", isVisible(root, "terminal"));
    }

    @Test
    public void searchingATypefaceTermFindsTheLookDestination() {
        SettingsActivity.RootPreferencesFragment root = launch();
        SettingsSearchPreference search = root.findPreference("settings_search");
        search.getOnQueryChangedListener().onQueryChanged("typeface");

        assertTrue("keyboard look moved onto the Look page, indexed under Appearance",
            isVisible(root, "wallpaper_style"));
    }

    private static boolean isVisible(SettingsActivity.RootPreferencesFragment root, String key) {
        Preference preference = findAnywhere(root.getPreferenceScreen(), key);
        return preference != null && preference.isVisible();
    }

    private static Preference findAnywhere(PreferenceGroup group, String key) {
        for (int i = 0; i < group.getPreferenceCount(); i++) {
            Preference child = group.getPreference(i);
            if (key.equals(child.getKey())) return child;
            if (child instanceof PreferenceGroup) {
                Preference found = findAnywhere((PreferenceGroup) child, key);
                if (found != null) return found;
            }
        }
        return null;
    }
}
