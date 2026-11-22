package com.termux.app.fragments.settings.termux;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.content.Intent;
import android.os.Build;

import androidx.fragment.app.Fragment;
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
 * Phase 6: the Status bar page carries the clock, CPU/memory/weather cards and the notification
 * rules — the status half of the old combined "Terminal & status" page. Its surface (blur,
 * opacity, grain, radius) is edited from the Layout page's "Look of this place" now, so this page
 * has no surface-editor entry of its own.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
@ConscryptMode(ConscryptMode.Mode.OFF)
public class StatusBarPreferencesFragmentTest {

    private StatusBarPreferencesFragment launch() {
        Application app = RuntimeEnvironment.getApplication();
        Intent intent = new Intent(app, SettingsActivity.class)
            .putExtra(SettingsActivity.EXTRA_INITIAL_FRAGMENT, StatusBarPreferencesFragment.class.getName());
        ActivityController<SettingsActivity> controller =
            Robolectric.buildActivity(SettingsActivity.class, intent).create().start().resume();
        SettingsActivity activity = controller.get();
        activity.getSupportFragmentManager().executePendingTransactions();
        Fragment fragment = activity.getSupportFragmentManager().findFragmentById(R.id.settings);
        assertTrue(fragment instanceof StatusBarPreferencesFragment);
        return (StatusBarPreferencesFragment) fragment;
    }

    @Test
    public void everyStatusRowIsExposedAndTerminalRowsAreGone() {
        StatusBarPreferencesFragment fragment = launch();
        PreferenceScreen screen = fragment.getPreferenceScreen();

        // The Appearance editor owns the clock face and position.
        assertNull(screen.findPreference("top_pane_clock_style"));
        assertNull(screen.findPreference("top_pane_clock_alignment"));
        // One home per setting: the clock's 12-hour choice, the indicator and weather switches and
        // the unit are the editor's Status bar card; the page keeps one Appearance row.
        for (String moved : new String[] {"top_pane_clock_am_pm", "status_widget_cpu", "status_widget_gpu", "status_widget_font_toggle", "status_widget_ram",
            "status_widget_battery", "status_widget_media", "status_widget_weather",
            "status_widget_weather_fahrenheit"}) {
            assertNull(moved, screen.findPreference(moved));
        }
        assertNotNull(screen.findPreference("status_layout_editor"));
        assertNotNull(screen.findPreference("status_widget_weather_location"));
        assertNotNull(screen.findPreference("status_bar_cutout_mode"));
        // Moved to the Notifications page, which has one home now: no link row here either.
        assertNull(screen.findPreference("top_pane_notification_access"));
        assertNull(screen.findPreference("essential_notification_rules_manage"));
        assertNull(screen.findPreference("status_bar_notifications_link"));

        // Moved to the Terminal page: not reachable here any more.
        assertNull(screen.findPreference("split_pane_controls"));
        assertNull(screen.findPreference("lazy_mode"));
        assertNull(screen.findPreference("fullscreen"));
        // Duplicate of the Layout page's "Look of this place": removed outright.
        assertNull(screen.findPreference("customize_status_surface"));

        assertEquals("Status bar", fragment.getActivity().getTitle().toString());
    }
}
