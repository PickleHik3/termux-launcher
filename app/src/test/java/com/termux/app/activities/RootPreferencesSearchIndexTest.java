package com.termux.app.activities;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
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
import com.termux.app.fragments.settings.SettingsTree;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.ConscryptMode;

/**
 * The settings root by the wall's halves (settings restructure 10-19): the search box, then Your
 * launcher (Home, Dock &amp; drawer, Terminal), Shared (Look, Keyboard, Notifications, On-device AI)
 * and System (General). Each row opens its section's page, as SettingsTree names it.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
@ConscryptMode(ConscryptMode.Mode.OFF)
public class RootPreferencesSearchIndexTest {

    private static final String[] EXPECTED_GROUPS = {"root_your_launcher", "root_shared", "root_system"};
    private static final String[][] EXPECTED_ROWS = {
        {"home", "dock_drawer", "terminal"},
        {"look", "keyboard_input", "notifications", "on_device_ai"},
        {"general"}
    };

    static SettingsActivity.RootPreferencesFragment launch() {
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
    public void theRootIsSearchThenTheThreeGroupsInPlanOrder() {
        SettingsActivity.RootPreferencesFragment root = launch();
        PreferenceScreen screen = root.getPreferenceScreen();
        assertEquals("settings_search", screen.getPreference(0).getKey());
        assertEquals(1 + EXPECTED_GROUPS.length, screen.getPreferenceCount());
        for (int g = 0; g < EXPECTED_GROUPS.length; g++) {
            PreferenceCategory group = (PreferenceCategory) screen.getPreference(1 + g);
            assertEquals(EXPECTED_GROUPS[g], group.getKey());
            assertEquals(EXPECTED_ROWS[g].length, group.getPreferenceCount());
            for (int i = 0; i < EXPECTED_ROWS[g].length; i++) {
                assertEquals("group " + g + " row " + i, EXPECTED_ROWS[g][i], group.getPreference(i).getKey());
            }
        }
        assertNull("Start in is Home's now", root.findPreference("app_launcher_start_in"));
        assertNull("Display is a window of the Terminal now", root.findPreference("display"));
    }

    @Test
    public void everySectionRowOpensTheSectionPageTheTreeNames() {
        SettingsActivity.RootPreferencesFragment root = launch();
        for (SettingsTree.Section section : SettingsTree.Section.values()) {
            Preference row = findAnywhere(root.getPreferenceScreen(), section.rootKey);
            assertEquals(section.name(), SettingsTree.sectionPage(section).className, row.getFragment());
        }
    }

    @Test
    public void typingInTheRootBoxSwapsTheSectionsForResultsAndClearingBringsThemBack() {
        SettingsActivity.RootPreferencesFragment root = launch();
        SettingsSearchPreference search = root.findPreference("settings_search");
        search.getOnQueryChangedListener().onQueryChanged("lazy mode");
        assertFalse(isVisible(root, "root_your_launcher"));
        assertFalse(isVisible(root, "root_system"));
        PreferenceGroup results = root.findPreference("settings_search_results");
        assertTrue(results != null && results.isVisible() && results.getPreferenceCount() > 0);
        assertEquals("Reduce idle activity", String.valueOf(results.getPreference(0).getTitle()));
        assertEquals("found by its old name, on Battery & performance",
            "General › Battery & performance", String.valueOf(results.getPreference(0).getSummary()));

        search.getOnQueryChangedListener().onQueryChanged("");
        assertTrue(isVisible(root, "root_your_launcher"));
        assertTrue(isVisible(root, "root_system"));
        assertNull(root.findPreference("settings_search_results"));
    }

    private static boolean isVisible(SettingsActivity.RootPreferencesFragment root, String key) {
        Preference preference = findAnywhere(root.getPreferenceScreen(), key);
        return preference != null && preference.isVisible();
    }

    static Preference findAnywhere(PreferenceGroup group, String key) {
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
