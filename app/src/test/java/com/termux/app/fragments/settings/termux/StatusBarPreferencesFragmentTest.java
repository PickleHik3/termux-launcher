package com.termux.app.fragments.settings.termux;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.content.Intent;
import android.os.Build;

import androidx.fragment.app.Fragment;
import androidx.preference.ListPreference;
import androidx.preference.PreferenceScreen;
import androidx.preference.SwitchPreferenceCompat;

import com.termux.R;
import com.termux.app.activities.SettingsActivity;
import com.termux.app.fragments.settings.SegmentedPillPreference;

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

        assertTrue(screen.findPreference("top_pane_clock_style") instanceof ListPreference);
        assertTrue(screen.findPreference("top_pane_clock_alignment") instanceof SegmentedPillPreference);
        assertTrue(screen.findPreference("top_pane_clock_am_pm") instanceof SwitchPreferenceCompat);
        assertTrue(screen.findPreference("status_widget_cpu") instanceof SwitchPreferenceCompat);
        assertTrue(screen.findPreference("status_widget_ram") instanceof SwitchPreferenceCompat);
        assertTrue(screen.findPreference("status_widget_weather") instanceof SwitchPreferenceCompat);
        assertTrue(screen.findPreference("status_widget_weather_fahrenheit") instanceof SwitchPreferenceCompat);
        assertTrue(screen.findPreference("top_pane_notification_access") != null);
        assertTrue(screen.findPreference("essential_notification_rules_manage") != null);

        // Moved to the Terminal page: not reachable here any more.
        assertNull(screen.findPreference("split_pane_controls"));
        assertNull(screen.findPreference("lazy_mode"));
        assertNull(screen.findPreference("fullscreen"));
        // Duplicate of the Layout page's "Look of this place": removed outright.
        assertNull(screen.findPreference("customize_status_surface"));

        assertEquals("Status bar", fragment.getActivity().getTitle().toString());
    }

    @Test
    public void clockStyleAndCpuUsagePersistThroughTheSharedTerminalIoStore() {
        StatusBarPreferencesFragment fragment = launch();
        ListPreference clockStyle = fragment.findPreference("top_pane_clock_style");
        SwitchPreferenceCompat cpu = fragment.findPreference("status_widget_cpu");

        clockStyle.setValue("led");
        cpu.setChecked(true);

        Application app = RuntimeEnvironment.getApplication();
        TerminalIOPreferencesDataStore io = TerminalIOPreferencesDataStore.getInstance(app);
        assertEquals("led", io.getString("top_pane_clock_style", "slab"));
        assertTrue(io.getBoolean("status_widget_cpu", false));
    }
}
