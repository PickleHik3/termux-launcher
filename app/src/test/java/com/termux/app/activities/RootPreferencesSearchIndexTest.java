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

    private static final String[] EXPECTED_LAUNCHER_ROW_ORDER = {
        "layout", "appearance", "terminal", "status_bar", "keyboard_input", "launcher_apps", "display"
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

    @Test
    public void launcherHeaderRowsAreInTheSpecOrder() {
        SettingsActivity.RootPreferencesFragment root = launch();
        PreferenceScreen screen = root.getPreferenceScreen();
        PreferenceCategory launcherHeader = (PreferenceCategory) screen.getPreference(1);
        assertEquals(EXPECTED_LAUNCHER_ROW_ORDER.length, launcherHeader.getPreferenceCount());
        for (int i = 0; i < EXPECTED_LAUNCHER_ROW_ORDER.length; i++) {
            assertEquals("row " + i, EXPECTED_LAUNCHER_ROW_ORDER[i],
                launcherHeader.getPreference(i).getKey());
        }
    }

    @Test
    public void searchingALazyModeTermFindsTheTerminalDestinationOnly() {
        SettingsActivity.RootPreferencesFragment root = launch();
        SettingsSearchPreference search = root.findPreference("settings_search");
        assertTrue(search.getOnQueryChangedListener() != null);
        search.getOnQueryChangedListener().onQueryChanged("lazy mode");

        assertTrue("terminal page contains lazy mode", isVisible(root, "terminal"));
        assertFalse("status bar page has no lazy mode row", isVisible(root, "status_bar"));
    }

    @Test
    public void searchingAClockTermFindsTheStatusBarDestinationOnly() {
        SettingsActivity.RootPreferencesFragment root = launch();
        SettingsSearchPreference search = root.findPreference("settings_search");
        search.getOnQueryChangedListener().onQueryChanged("clock style");

        assertTrue("status bar page contains clock style", isVisible(root, "status_bar"));
        assertFalse("terminal page has no clock row", isVisible(root, "terminal"));
    }

    @Test
    public void searchingATypefaceTermFindsTheLookDestination() {
        SettingsActivity.RootPreferencesFragment root = launch();
        SettingsSearchPreference search = root.findPreference("settings_search");
        search.getOnQueryChangedListener().onQueryChanged("typeface");

        assertTrue("keyboard look moved onto the Look (appearance) page",
            isVisible(root, "appearance"));
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
