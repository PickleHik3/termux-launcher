package com.termux.app.fragments.settings.termux;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.content.Intent;
import android.os.Build;

import androidx.fragment.app.Fragment;
import androidx.preference.Preference;
import androidx.preference.PreferenceScreen;
import androidx.preference.SwitchPreferenceCompat;

import com.termux.R;
import com.termux.app.activities.SettingsActivity;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.ConscryptMode;

/**
 * Phase 6: the Terminal page carries panes, lazy mode, full screen, system keyboard
 * compatibility, and Recents visibility — the terminal half of the old combined
 * "Terminal & status" page. The clock/status-widget half is {@link StatusBarPreferencesFragment}.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
@ConscryptMode(ConscryptMode.Mode.OFF)
public class TerminalPreferencesFragmentTest {

    private TerminalPreferencesFragment launch() {
        Application app = RuntimeEnvironment.getApplication();
        Intent intent = new Intent(app, SettingsActivity.class)
            .putExtra(SettingsActivity.EXTRA_INITIAL_FRAGMENT, TerminalPreferencesFragment.class.getName());
        ActivityController<SettingsActivity> controller =
            Robolectric.buildActivity(SettingsActivity.class, intent).create().start().resume();
        SettingsActivity activity = controller.get();
        activity.getSupportFragmentManager().executePendingTransactions();
        Fragment fragment = activity.getSupportFragmentManager().findFragmentById(R.id.settings);
        assertTrue(fragment instanceof TerminalPreferencesFragment);
        return (TerminalPreferencesFragment) fragment;
    }

    @Test
    public void everyTerminalRowIsExposedAndStatusRowsAreGone() {
        TerminalPreferencesFragment fragment = launch();
        PreferenceScreen screen = fragment.getPreferenceScreen();

        assertTrue(screen.findPreference("fullscreen") instanceof SwitchPreferenceCompat);
        assertTrue(screen.findPreference("terminal_margin_adjustment") instanceof SwitchPreferenceCompat);
        assertTrue(screen.findPreference("show_in_recents_when_not_default") instanceof SwitchPreferenceCompat);
        assertTrue(screen.findPreference("split_pane_controls") instanceof SwitchPreferenceCompat);
        assertTrue(screen.findPreference("show_key_hints") instanceof SwitchPreferenceCompat);
        assertTrue(screen.findPreference("pane_dwindle_default") instanceof SwitchPreferenceCompat);
        assertTrue(screen.findPreference("pane_focus_grows") instanceof SwitchPreferenceCompat);
        assertTrue(screen.findPreference("pane_agent_api") instanceof SwitchPreferenceCompat);
        assertTrue(screen.findPreference("lazy_mode") instanceof SwitchPreferenceCompat);

        // Moved to the Status bar page: not reachable here any more.
        org.junit.Assert.assertNull(screen.findPreference("top_pane_clock_style"));
        org.junit.Assert.assertNull(screen.findPreference("status_widget_cpu"));
        // Duplicate of the Layout page's "Look of this place": removed outright.
        org.junit.Assert.assertNull(screen.findPreference("customize_status_surface"));

        assertEquals("Terminal", activityTitle(fragment));
    }

    @Test
    public void splitPaneControlsInvertsIntoTheCompatibilityModeKey() {
        Application app = RuntimeEnvironment.getApplication();
        TerminalIOPreferencesDataStore io = TerminalIOPreferencesDataStore.getInstance(app);
        io.putBoolean("compatibility_mode", false);

        TerminalPreferencesFragment fragment = launch();
        SwitchPreferenceCompat splitPanes = fragment.findPreference("split_pane_controls");
        assertTrue(splitPanes.isChecked());

        splitPanes.setChecked(false);
        assertTrue(io.getBoolean("compatibility_mode", false));
    }

    private static String activityTitle(Fragment fragment) {
        return fragment.getActivity() == null ? null : fragment.getActivity().getTitle().toString();
    }
}
