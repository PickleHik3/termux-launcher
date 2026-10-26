package com.termux.app.fragments.settings;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.content.Context;
import android.os.Build;

import androidx.fragment.app.Fragment;
import androidx.preference.PreferenceManager;
import androidx.preference.PreferenceScreen;

import com.termux.R;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.ConscryptMode;

/**
 * SettingsTree is the one table behind deep links, saved stacks, search and motion. Every screen it
 * names must exist, every old page name any caller or older build may send must resolve to one of
 * them, and every moved row must be found on the page its old link is sent to.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
@ConscryptMode(ConscryptMode.Mode.OFF)
public class SettingsTreeTest {

    private static final String PAGES = "com.termux.app.fragments.settings.termux.";

    /**
     * Every page an Intent has named (TermuxActivity, the palette, widgets, services, the
     * Appearance editor, the display window) or a pre-restructure Back stack can hold.
     */
    private static final String[] OLD_TARGETS = {
        "TermuxStylePreferencesFragment", "LauncherPreferencesFragment", "LauncherDockPreferencesFragment",
        "LauncherSearchPreferencesFragment", "LauncherLockPreferencesFragment", "AppDrawerPreferencesFragment",
        "StatusBarPreferencesFragment", "NotificationsPreferencesFragment", "TerminalPreferencesFragment",
        "TerminalStatusPreferencesFragment", "KeyboardPreferencesFragment", "KeyboardLayoutPreferencesFragment",
        "KeyboardSizePreferencesFragment", "KeyboardTypingPreferencesFragment", "KeyboardVoicePreferencesFragment",
        "KeyboardHardwarePreferencesFragment", "KeyboardColorSchemeFragment", "ExtraKeysEditorFragment",
        "X11DisplayPreferencesFragment", "X11DisplayInputPreferencesFragment",
        "X11DisplayResolutionPreferencesFragment", "X11DisplayLinuxAppsPreferencesFragment",
        "X11DisplayStartupPreferencesFragment", "X11DisplayTroubleshootingPreferencesFragment",
        "GuiAppsSetupPreferencesFragment", "TaiPreferencesFragment", "TaiModelCentreFragment",
        "AppBehaviorPreferencesFragment", "BackupRestorePreferencesFragment",
        "ServicesPermissionsPreferencesFragment", "PrivilegedAccessPreferencesFragment",
        "AdvancedDiagnosticsPreferencesFragment", "TermuxFontsPreferencesFragment",
        "NotificationHistoryFragment", "SpeechModelPreferencesFragment", "DebuggingPreferencesFragment",
    };

    @Test
    public void everyScreenExistsAndIsASettingsFragment() throws Exception {
        for (SettingsTree.Screen screen : SettingsTree.screens()) {
            Class<?> type = Class.forName(screen.className);
            assertTrue(screen.className, Fragment.class.isAssignableFrom(type));
            assertTrue(screen.className, screen.className.startsWith("com.termux.app.fragments.settings."));
            if (screen.parent != null) assertNotNull(SettingsTree.find(screen.parent));
        }
    }

    @Test
    public void everyOldTargetResolvesToAScreenOfTheTree() throws Exception {
        for (String old : OLD_TARGETS) {
            String now = SettingsTree.redirect(PAGES + old, null);
            assertNotNull(old + " -> " + now, SettingsTree.find(now));
            Class.forName(now);
        }
    }

    @Test
    public void everySectionHasOnePage() {
        for (SettingsTree.Section section : SettingsTree.Section.values()) {
            SettingsTree.Screen page = SettingsTree.sectionPage(section);
            assertTrue(page.isSectionPage());
            assertEquals(1, SettingsTree.path(page.className).size());
        }
    }

    /** Old page, row key, and the row's key must be on the page the link is sent to. */
    private static final String[][] MOVED_ROWS = {
        {"TermuxStylePreferencesFragment", "terminal_fonts"},
        {"TermuxStylePreferencesFragment", "customize_keyboard_surface"},
        {"TermuxStylePreferencesFragment", "in_app_keyboard_color_scheme_editor"},
        {"TermuxStylePreferencesFragment", "wallpaper_parallax"},
        {"LauncherPreferencesFragment", "app_launcher_home_palette_gesture"},
        {"LauncherPreferencesFragment", "app_launcher_widget_pane_enabled"},
        {"LauncherPreferencesFragment", "app_launcher_builtin_widget_style"},
        {"LauncherPreferencesFragment", "app_launcher_set_home"},
        {"LauncherPreferencesFragment", "app_launcher_help"},
        {"GeneralPreferencesFragment", "app_launcher_help"},
        {"TerminalTextPreferencesFragment", "fullscreen"},
        {"LauncherPreferencesFragment", "app_haptics_enabled"},
        {"LauncherPreferencesFragment", "app_launcher_most_used_page"},
        {"LauncherDockPreferencesFragment", "app_launcher_default_buttons"},
        {"LauncherPreferencesFragment", "app_launcher_input_char"},
        {"LauncherPreferencesFragment", "app_launcher_quick_reply_swipe"},
        {"LauncherPreferencesFragment", "app_launcher_sub_lock"},
        {"TerminalWindowsPreferencesFragment", "terminal_body_swipe"},
        {"TerminalPreferencesFragment", "split_pane_controls"},
        {"TerminalPreferencesFragment", "pane_agent_api"},
        {"TerminalPreferencesFragment", "fullscreen"},
        {"TerminalPreferencesFragment", "terminal_retro_effect"},
        {"TerminalPreferencesFragment", "terminal_osc52_clipboard_read_enabled"},
        {"TerminalPreferencesFragment", "terminal_margin_adjustment"},
        {"TerminalStatusPreferencesFragment", "status_widget_cpu"},
        {"TerminalStatusPreferencesFragment", "lazy_mode"},
        {"KeyboardPreferencesFragment", "in_app_keyboard_haptics_enabled"},
        {"KeyboardPreferencesFragment", "keyboard_voice_engine"},
        {"AppBehaviorPreferencesFragment", "low_memory_preset"},
        {"GeneralPreferencesFragment", "lazy_mode"},
        {"GeneralPreferencesFragment", "low_memory_preset"},
        {"TermuxStylePreferencesFragment", "fancier_glass_motion_light"},
        {"X11DisplayPreferencesFragment", "x11_drawer_apps"},
        {"X11DisplayLinuxAppsPreferencesFragment", "x11_window_manager"},
        {"X11DisplayPreferencesFragment", "displayScale"},
        {"NotificationsPreferencesFragment", "app_launcher_notification_access"},
    };

    @Test
    public void everyMovedRowIsOnThePageItsOldLinkOpens() {
        Context context = RuntimeEnvironment.getApplication();
        for (String[] moved : MOVED_ROWS) {
            String page = SettingsTree.redirect(PAGES + moved[0], moved[1]);
            SettingsTree.Screen screen = SettingsTree.find(page);
            assertNotNull(page, screen);
            PreferenceScreen inflated = new PreferenceManager(context)
                .inflateFromResource(context, screen.xmlRes, null);
            assertNotNull(moved[0] + "#" + moved[1] + " -> " + page, inflated.findPreference(moved[1]));
        }
    }

    /** The 10-24 filing (spec §4): the sections in root order, Appearance a door, the lock under Home. */
    @Test
    public void theSectionsFollowTheRefiledRoot() {
        assertEquals(java.util.Arrays.asList(SettingsTree.Section.HOME, SettingsTree.Section.DOCK_DRAWER,
            SettingsTree.Section.TERMINAL, SettingsTree.Section.LINUX_APPS, SettingsTree.Section.APPEARANCE,
            SettingsTree.Section.KEYBOARD, SettingsTree.Section.NOTIFICATIONS, SettingsTree.Section.AI,
            SettingsTree.Section.GENERAL), java.util.Arrays.asList(SettingsTree.Section.values()));
        assertTrue(SettingsTree.sectionPage(SettingsTree.Section.APPEARANCE).door);
        assertEquals(PAGES + "X11DisplayPreferencesFragment",
            SettingsTree.sectionPage(SettingsTree.Section.LINUX_APPS).className);
        assertEquals(PAGES + "HomePreferencesFragment",
            SettingsTree.find(PAGES + "LauncherLockPreferencesFragment").parent);
        assertEquals(PAGES + "AdvancedDiagnosticsPreferencesFragment",
            SettingsTree.redirect("com.termux.app.fragments.settings.termux_api.DebuggingPreferencesFragment", null));
        for (SettingsTree.Screen screen : SettingsTree.screens()) {
            if (screen.door) assertTrue("only a section is a door", screen.isSectionPage());
        }
    }

    @Test
    public void startInStandsBesideUseAsOnGeneral() {
        assertEquals(PAGES + "GeneralPreferencesFragment",
            SettingsTree.redirect(SettingsTree.ROOT, "app_launcher_start_in"));
        assertEquals("a link to Start in on Home finds it on General",
            PAGES + "GeneralPreferencesFragment",
            SettingsTree.redirect(PAGES + "HomePreferencesFragment", "app_launcher_start_in"));
        assertEquals(R.xml.home_preferences, SettingsTree.find(PAGES + "HomePreferencesFragment").xmlRes);
    }
}
