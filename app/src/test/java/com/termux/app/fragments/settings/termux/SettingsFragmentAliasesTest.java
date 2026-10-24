package com.termux.app.fragments.settings.termux;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.content.Intent;
import android.os.Build;

import androidx.fragment.app.Fragment;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceScreen;

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
 * Every fragment class name that used to be a valid {@code EXTRA_INITIAL_FRAGMENT} value (a deep
 * link, a shortcut, a rebroadcast Intent, a saved Back stack from before the 10-19 restructure)
 * must keep resolving and inflating rather than crash or silently fall back to the root. Old names
 * whose page folded into another open that page (App behavior is General, the Dock page is Dock
 * &amp; drawer, Linux apps is the Linux apps section, Terminal &amp; status is Terminal); every other
 * old name still names the same page, with the rows the restructure gave it.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
@ConscryptMode(ConscryptMode.Mode.OFF)
public class SettingsFragmentAliasesTest {

    private Fragment launch(Class<? extends Fragment> fragmentClass) {
        Application app = RuntimeEnvironment.getApplication();
        Intent intent = new Intent(app, SettingsActivity.class)
            .putExtra(SettingsActivity.EXTRA_INITIAL_FRAGMENT, fragmentClass.getName());
        ActivityController<SettingsActivity> controller =
            Robolectric.buildActivity(SettingsActivity.class, intent).create().start().resume();
        SettingsActivity activity = controller.get();
        activity.getSupportFragmentManager().executePendingTransactions();
        return activity.getSupportFragmentManager().findFragmentById(R.id.settings);
    }

    private static PreferenceScreen screen(Fragment fragment) {
        return ((PreferenceFragmentCompat) fragment).getPreferenceScreen();
    }

    @Test
    public void foldedPageNamesOpenThePageTheirRowsLiveOnNow() {
        assertEquals(GeneralPreferencesFragment.class, launch(AppBehaviorPreferencesFragment.class).getClass());
        assertEquals(LauncherPreferencesFragment.class, launch(LauncherDockPreferencesFragment.class).getClass());
        assertEquals(X11DisplayPreferencesFragment.class,
            launch(X11DisplayLinuxAppsPreferencesFragment.class).getClass());
        assertEquals(TerminalPreferencesFragment.class,
            launch(TerminalStatusPreferencesFragment.class).getClass());
    }

    @Test
    public void lookKeepsTheThemeAndLosesWhatMovedOut() {
        PreferenceScreen look = screen(launch(TermuxStylePreferencesFragment.class));
        assertNotNull("the Appearance editor is Look's top row", look.findPreference("appearance_surface"));
        assertNotNull(look.findPreference("theme_mode"));
        assertNull("the terminal font is Terminal's", look.findPreference("terminal_fonts"));
        assertNull("the keyboard's look is Keyboard's", look.findPreference("customize_keyboard_surface"));
        assertNull("the wallpaper's scroll is Home's", look.findPreference("wallpaper_parallax"));
        assertNull("Lazy mode is General's", look.findPreference("lazy_mode"));
    }

    @Test
    public void keyboardGainsItsLook() {
        PreferenceScreen keyboard = screen(launch(KeyboardPreferencesFragment.class));
        // The hardware keyboard has its own group; the extra keys group holds extra keys only.
        androidx.preference.PreferenceGroup hardware = keyboard.findPreference("keyboard_hardware");
        assertNotNull(hardware.findPreference("keyboard_sub_hardware"));
        androidx.preference.PreferenceGroup extraKeys = keyboard.findPreference("keyboard_terminal_hardware");
        assertEquals(2, extraKeys.getPreferenceCount());
        assertNull(extraKeys.findPreference("keyboard_sub_hardware"));
        assertNotNull(keyboard.findPreference("keyboard_input_method"));
        assertEquals(KeyboardLayoutPreferencesFragment.class.getName(),
            keyboard.findPreference("keyboard_sub_layout").getFragment());
        assertNotNull(keyboard.findPreference("keyboard_appearance"));
        assertNotNull(keyboard.findPreference("customize_keyboard_surface"));
        assertEquals(KeyboardColorSchemeFragment.class.getName(),
            keyboard.findPreference("in_app_keyboard_color_scheme_editor").getFragment());
        assertNull("vibration is General's alone", keyboard.findPreference("keyboard_vibration_link"));
        assertNull("vibration is General's alone", screen(launch(KeyboardTypingPreferencesFragment.class))
            .findPreference("keyboard_typing_vibration_link"));
    }

    @Test
    public void oneHomePerSetting() {
        assertNull("access is Permissions' alone", screen(launch(NotificationsPreferencesFragment.class))
            .findPreference("app_launcher_notification_access"));
        assertNotNull(screen(launch(ServicesPermissionsPreferencesFragment.class))
            .findPreference("app_launcher_notification_access"));
        PreferenceScreen diagnostics = screen(launch(AdvancedDiagnosticsPreferencesFragment.class));
        for (String key : new String[] {"log_level", "termux_api_log_level", "terminal_view_key_logging_enabled",
            "plugin_error_notifications_enabled", "crash_report_notifications_enabled",
            "privileged_backend_smoke_test"}) {
            assertNotNull(key, diagnostics.findPreference(key));
        }
    }

    @Test
    public void dockAndDrawerHoldsPinnedAppsOnceAndNoCopies() {
        PreferenceScreen dock = screen(launch(LauncherPreferencesFragment.class));
        assertNotNull(dock.findPreference("app_launcher_default_buttons"));
        assertNotNull(dock.findPreference("app_launcher_most_used_page"));
        assertNotNull(dock.findPreference("app_launcher_drawer_layout"));
        assertNull("copy of Notifications", dock.findPreference("app_launcher_notifications_link"));
        assertNull("copy of General", dock.findPreference("app_launcher_behavior_link"));
        assertNull("General's now", dock.findPreference("app_launcher_set_home"));
        assertNull("Home's now", dock.findPreference("app_launcher_widget_pane_enabled"));
        assertNull("the Dock page folded in", dock.findPreference("app_launcher_sub_dock"));
    }

    @Test
    public void homeHoldsItsRowsWithTheirKeys() {
        PreferenceScreen home = screen(launch(HomePreferencesFragment.class));
        assertEquals("home_layout_editor", home.getPreference(0).getKey());
        for (String key : new String[] {"app_launcher_home_palette_gesture", "app_launcher_widget_pane_enabled",
            "app_launcher_builtin_widget_style", "wallpaper_parallax", "app_launcher_start_in"}) {
            assertNotNull(key, home.findPreference(key));
        }
        // Gestures gathers every gesture, the lock's page linked from its row.
        androidx.preference.PreferenceGroup gestures = home.findPreference("home_gestures");
        for (String key : new String[] {"app_launcher_sub_lock", "app_launcher_quick_reply_swipe",
            "terminal_body_swipe"}) {
            assertNotNull(key, gestures.findPreference(key));
        }
        assertEquals(LauncherLockPreferencesFragment.class.getName(),
            gestures.findPreference("app_launcher_sub_lock").getFragment());
    }

    @Test
    public void generalHoldsBehaviorTheAppsPagesAndTheHomeApp() {
        PreferenceScreen general = screen(launch(GeneralPreferencesFragment.class));
        for (String key : new String[] {"app_haptics_enabled", "app_launcher_row_haptics",
            "in_app_keyboard_haptics_enabled", "performance_link", "backup_restore",
            "services_permissions", "advanced_diagnostics", "about_support",
            "app_launcher_set_home", "show_in_recents_when_not_default"}) {
            assertNotNull(key, general.findPreference(key));
        }
        assertNull("Help folded into About & help", general.findPreference("app_launcher_help"));
        assertNotNull(screen(launch(com.termux.app.fragments.settings.AboutSupportPreferencesFragment.class))
            .findPreference("app_launcher_help"));
    }

    @Test
    public void linuxAppsIsTheOldDisplaySectionWithItsAppsOnTop() {
        PreferenceScreen linux = screen(launch(X11DisplayPreferencesFragment.class));
        assertNotNull(linux.findPreference("x11_drawer_apps"));
        assertNotNull(linux.findPreference("x11_window_manager"));
        assertNotNull(linux.findPreference("x11_sub_input"));
        assertNull("its rows are on this page now", linux.findPreference("x11_sub_linux_apps"));
        assertNotNull("the apps come first",
            ((androidx.preference.PreferenceGroup) linux.getPreference(0)).findPreference("x11_drawer_apps"));
    }
}
