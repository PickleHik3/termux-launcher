package com.termux.app.fragments.settings;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.annotation.XmlRes;

import com.termux.R;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The settings tree as plain data (project-docs/active/settings-restructure-2026-10-19.md): every
 * screen Settings can show, the section of the root it belongs to, the screen that opens it, its
 * title and the XML its rows come from. One table answers what the host would otherwise guess
 * several times over: the path to rebuild for a saved Back stack, the page an old deep link names
 * now, what the search index walks, whether a move is deeper, sideways or into an editor, and
 * which section a two-pane list should show as open.
 *
 * <p>Screens are named by class name, so a saved stack or an Intent from an older build is
 * compared without loading anything; a name this table does not know is simply passed through.
 */
public final class SettingsTree {

    private SettingsTree() {}

    /** The root list itself. Never on the Back stack: it is the page under (or beside) everything. */
    public static final String ROOT = "com.termux.app.activities.SettingsActivity$RootPreferencesFragment";

    private static final String PAGES = "com.termux.app.fragments.settings.termux.";
    private static final String SETTINGS = "com.termux.app.fragments.settings.";

    /** The root's sections, in root order, by the wall's halves. */
    public enum Section {
        HOME(R.string.settings_section_home, "home"),
        DOCK_DRAWER(R.string.settings_section_dock_drawer, "dock_drawer"),
        TERMINAL(R.string.settings_destination_terminal, "terminal"),
        LINUX_APPS(R.string.settings_terminal_sub_linux_apps_title, "linux_apps"),
        /** A door, not a page: the root's row opens the Appearance editor. */
        APPEARANCE(R.string.settings_section_look, "look"),
        KEYBOARD(R.string.settings_destination_keyboard_input, "keyboard_input"),
        NOTIFICATIONS(R.string.settings_destination_notifications, "notifications"),
        AI(R.string.settings_tai_service_title, "on_device_ai"),
        GENERAL(R.string.settings_section_general, "general");

        @StringRes public final int titleRes;
        /** The key of this section's row on the root page. */
        @NonNull public final String rootKey;

        Section(@StringRes int titleRes, @NonNull String rootKey) {
            this.titleRes = titleRes;
            this.rootKey = rootKey;
        }
    }

    /** One screen of the tree. */
    public static final class Screen {
        @NonNull public final String className;
        /** The screen that opens this one, or null for a section's own page. */
        @Nullable public final String parent;
        @StringRes public final int titleRes;
        /** The rows' XML, or 0 for a screen built in code (an editor). */
        @XmlRes public final int xmlRes;
        @NonNull public final Section section;
        /**
         * A dedicated editor rather than a page of rows: entered with a container transform from
         * the row that opens it instead of a slide.
         */
        public final boolean editor;
        /**
         * Not shown as a page: a door into the Appearance editor over the live launcher. Its XML
         * lists the rows the editor holds, so search still finds them; a hit opens the editor.
         */
        public final boolean door;

        Screen(@NonNull String className, @Nullable String parent, @StringRes int titleRes,
               @XmlRes int xmlRes, @NonNull Section section, boolean editor, boolean door) {
            this.className = className;
            this.parent = parent;
            this.titleRes = titleRes;
            this.xmlRes = xmlRes;
            this.section = section;
            this.editor = editor;
            this.door = door;
        }

        public boolean isSectionPage() {
            return parent == null;
        }
    }

    private static final Map<String, Screen> SCREENS = new LinkedHashMap<>();
    /** Old class names that now show another screen: the old name keeps resolving, to the new one. */
    private static final Map<String, String> ALIASES = new HashMap<>();

    private static void section(Section section, String className, int titleRes, int xmlRes) {
        SCREENS.put(className, new Screen(className, null, titleRes, xmlRes, section, false, false));
    }

    /** A section whose root row opens the Appearance editor; {@code xmlRes} is for search only. */
    private static void door(Section section, String className, int titleRes, int xmlRes) {
        SCREENS.put(className, new Screen(className, null, titleRes, xmlRes, section, false, true));
    }

    private static void page(String parent, String className, int titleRes, int xmlRes) {
        Screen up = SCREENS.get(parent);
        if (up == null) throw new IllegalStateException("parent first: " + parent);
        SCREENS.put(className, new Screen(className, parent, titleRes, xmlRes, up.section, false, false));
    }

    private static void editor(String parent, String className, int titleRes) {
        Screen up = SCREENS.get(parent);
        if (up == null) throw new IllegalStateException("parent first: " + parent);
        SCREENS.put(className, new Screen(className, parent, titleRes, 0, up.section, true, false));
    }

    static {
        // Your launcher
        section(Section.HOME, PAGES + "HomePreferencesFragment", R.string.settings_section_home,
            R.xml.home_preferences);

        String dock = PAGES + "LauncherPreferencesFragment";
        section(Section.DOCK_DRAWER, dock, R.string.settings_section_dock_drawer, R.xml.launcher_preferences);
        page(dock, PAGES + "AppDrawerPreferencesFragment", R.string.settings_app_drawer_view_type_title,
            R.xml.app_drawer_preferences);
        page(dock, PAGES + "LauncherSearchPreferencesFragment", R.string.settings_apps_sub_search_title,
            R.xml.launcher_search_preferences);
        page(dock, PAGES + "LauncherLockPreferencesFragment", R.string.settings_apps_sub_lock_title,
            R.xml.launcher_lock_preferences);

        String terminal = PAGES + "TerminalPreferencesFragment";
        section(Section.TERMINAL, terminal, R.string.settings_destination_terminal, R.xml.terminal_preferences);
        page(terminal, PAGES + "TerminalWindowsPreferencesFragment",
            R.string.settings_terminal_sub_windows_title, R.xml.terminal_windows_preferences);
        page(terminal, PAGES + "StatusBarPreferencesFragment", R.string.settings_destination_status_bar,
            R.xml.status_bar_preferences);
        String text = PAGES + "TerminalTextPreferencesFragment";
        page(terminal, text, R.string.settings_terminal_sub_text_title, R.xml.terminal_text_preferences);
        page(text, PAGES + "TermuxFontsPreferencesFragment", R.string.termux_fonts_preferences_title,
            R.xml.termux_fonts_preferences);
        page(terminal, PAGES + "TerminalClipboardPreferencesFragment",
            R.string.settings_terminal_sub_clipboard_title, R.xml.terminal_clipboard_preferences);

        // Linux apps sits beside Terminal again (spec §4; the word Display stays retired).
        String linuxApps = PAGES + "X11DisplayPreferencesFragment";
        section(Section.LINUX_APPS, linuxApps, R.string.settings_terminal_sub_linux_apps_title,
            R.xml.x11_display_preferences);
        page(linuxApps, PAGES + "GuiAppsSetupPreferencesFragment", R.string.settings_gui_apps_title,
            R.xml.gui_apps_setup_preferences);
        page(PAGES + "GuiAppsSetupPreferencesFragment", PAGES + "GraphicsAccelerationFragment",
            R.string.settings_x11_gpu_title, 0);
        page(linuxApps, PAGES + "HiddenAppsPreferencesFragment", R.string.settings_x11_hidden_apps_title, 0);
        page(linuxApps, PAGES + "X11DisplayInputPreferencesFragment", R.string.settings_x11_sub_input_title,
            R.xml.x11_display_input_preferences);
        page(linuxApps, PAGES + "X11DisplayResolutionPreferencesFragment",
            R.string.settings_x11_sub_resolution_title, R.xml.x11_display_resolution_preferences);
        page(linuxApps, PAGES + "X11DisplayStartupPreferencesFragment", R.string.settings_x11_sub_startup_title,
            R.xml.x11_display_startup_preferences);
        page(linuxApps, PAGES + "X11DisplayTroubleshootingPreferencesFragment",
            R.string.settings_x11_sub_troubleshooting_title, R.xml.x11_display_troubleshooting_preferences);

        // Everywhere
        // Appearance: the old Look page's name, now the door into the editor. Its rows moved into
        // the editor's pill card and stay searchable through appearance_search_index.
        door(Section.APPEARANCE, PAGES + "TermuxStylePreferencesFragment", R.string.settings_section_look,
            R.xml.appearance_search_index);

        String keyboard = PAGES + "KeyboardPreferencesFragment";
        section(Section.KEYBOARD, keyboard, R.string.settings_destination_keyboard_input,
            R.xml.termux_keyboard_preferences);
        String layouts = PAGES + "KeyboardLayoutPreferencesFragment";
        page(keyboard, layouts, R.string.settings_keyboard_sub_layout_title,
            R.xml.termux_keyboard_layout_preferences);
        editor(layouts, PAGES + "KeyboardLayoutsFragment", R.string.settings_keyboard_layouts_title);
        editor(PAGES + "KeyboardLayoutsFragment", PAGES + "KeyboardLayoutEditorFragment",
            R.string.settings_keyboard_layout_editor_title);
        page(keyboard, PAGES + "KeyboardSizePreferencesFragment", R.string.settings_keyboard_sub_size_title,
            R.xml.termux_keyboard_size_preferences);
        page(keyboard, PAGES + "KeyboardTypingPreferencesFragment", R.string.settings_keyboard_sub_typing_title,
            R.xml.termux_keyboard_typing_preferences);
        String voice = PAGES + "KeyboardVoicePreferencesFragment";
        page(keyboard, voice, R.string.settings_keyboard_sub_voice_title, R.xml.termux_keyboard_voice_preferences);
        page(voice, PAGES + "KeyboardVoiceDetailsPreferencesFragment", R.string.settings_voice_details_title,
            R.xml.termux_keyboard_voice_details_preferences);
        page(voice, PAGES + "SpeechModelPreferencesFragment", R.string.settings_keyboard_voice_model_title,
            R.xml.speech_model_preferences);
        page(keyboard, PAGES + "KeyboardHardwarePreferencesFragment",
            R.string.settings_keyboard_sub_hardware_title, R.xml.termux_keyboard_hardware_preferences);
        editor(keyboard, PAGES + "KeyboardColorSchemeFragment", R.string.keyboard_theme_title);
        editor(keyboard, PAGES + "ExtraKeysEditorFragment", R.string.settings_edit_extra_keys_title);

        String notifications = PAGES + "NotificationsPreferencesFragment";
        section(Section.NOTIFICATIONS, notifications, R.string.settings_destination_notifications,
            R.xml.notifications_preferences);
        page(notifications, PAGES + "EssentialNotificationRulesFragment",
            R.string.settings_essential_notifications_title, 0);
        page(notifications, PAGES + "NotificationHistoryFragment", R.string.notif_history_title, 0);

        String ai = PAGES + "TaiPreferencesFragment";
        section(Section.AI, ai, R.string.termux_ai_preferences_title, R.xml.termux_ai_preferences);
        page(ai, PAGES + "TaiRuntimePreferencesFragment", R.string.termux_ai_runtime_screen_title,
            R.xml.tai_runtime_preferences);
        page(ai, PAGES + "TaiApiPreferencesFragment", R.string.termux_ai_local_api_title, R.xml.tai_api_preferences);
        page(ai, PAGES + "TaiAdvancedPreferencesFragment", R.string.termux_ai_zone_advanced_title,
            R.xml.tai_advanced_preferences);
        page(ai, PAGES + "TaiRemotePreferencesFragment", R.string.tai_remote_screen_title,
            R.xml.tai_remote_preferences);
        page(ai, PAGES + "TaiModelCentreFragment", R.string.tai_model_centre_title, 0);

        // System
        String general = PAGES + "GeneralPreferencesFragment";
        section(Section.GENERAL, general, R.string.settings_section_general, R.xml.general_preferences);
        // Page transition turns every page, not only Home's: it sits under Motion.
        editor(general, PAGES + "HomePageTransitionFragment", R.string.settings_home_page_transition_title);
        // The presets and the motion they trim: General's Battery row opens it.
        page(general, PAGES + "PerformancePreferencesFragment", R.string.settings_destination_performance,
            R.xml.performance_preferences);
        page(general, PAGES + "BackupRestorePreferencesFragment", R.string.settings_destination_backup_restore,
            R.xml.backup_restore_preferences);
        String permissions = PAGES + "ServicesPermissionsPreferencesFragment";
        page(general, permissions, R.string.settings_destination_services_permissions,
            R.xml.services_permissions_preferences);
        page(permissions, PAGES + "PrivilegedAccessPreferencesFragment",
            R.string.termux_privileged_access_preferences_title, R.xml.termux_privileged_access_preferences);
        page(permissions, SETTINGS + "TermuxAPIPreferencesFragment", R.string.settings_termux_api_service_title,
            R.xml.termux_api_preferences);
        page(general, PAGES + "AdvancedDiagnosticsPreferencesFragment",
            R.string.settings_destination_advanced_diagnostics, R.xml.advanced_diagnostics_preferences);
        page(general, SETTINGS + "AboutSupportPreferencesFragment", R.string.settings_destination_about_support,
            R.xml.about_support_preferences);

        ALIASES.put(PAGES + "AppBehaviorPreferencesFragment", general);
        ALIASES.put(PAGES + "LauncherDockPreferencesFragment", dock);
        ALIASES.put(PAGES + "X11DisplayLinuxAppsPreferencesFragment", linuxApps);
        ALIASES.put(PAGES + "TerminalStatusPreferencesFragment", terminal);
    }

    /** Every screen, sections first within each section, in root order. */
    @NonNull
    public static List<Screen> screens() {
        return Collections.unmodifiableList(new ArrayList<>(SCREENS.values()));
    }

    /** The name a screen goes by now: an old name that folded into another screen gives that one. */
    @NonNull
    public static String canonical(@NonNull String className) {
        String alias = ALIASES.get(className);
        return alias == null ? className : alias;
    }

    @Nullable
    public static Screen find(@Nullable String className) {
        return className == null ? null : SCREENS.get(canonical(className));
    }

    @Nullable
    public static Section sectionOf(@Nullable String className) {
        Screen screen = find(className);
        return screen == null ? null : screen.section;
    }

    /** The section's own page. */
    @NonNull
    public static Screen sectionPage(@NonNull Section section) {
        for (Screen screen : SCREENS.values()) {
            if (screen.section == section && screen.isSectionPage()) return screen;
        }
        throw new IllegalStateException("no page for " + section);
    }

    /**
     * The screens from the section's page down to {@code className}, both included; empty for a
     * name the tree does not know (or the root).
     */
    @NonNull
    public static List<Screen> path(@NonNull String className) {
        List<Screen> path = new ArrayList<>();
        Screen screen = find(className);
        while (screen != null) {
            path.add(0, screen);
            screen = screen.parent == null ? null : SCREENS.get(screen.parent);
        }
        return path;
    }

    /**
     * Where a link that names {@code className}, scrolled to {@code scrollToKey}, should land now.
     * An old name that folded into another screen gives that screen; a row that moved to another
     * page (an external Intent, a saved Back stack, a search hit, a link from an older build) is
     * sent to the page that holds it, with the same key, so the row is still found. A link without a
     * key keeps opening the page it named (or that page's new name).
     */
    @NonNull
    public static String redirect(@NonNull String className, @Nullable String scrollToKey) {
        String page = scrollToKey == null ? null : pageForMovedRow(className, scrollToKey);
        return page != null ? PAGES + page : canonical(className);
    }

    @Nullable
    private static String pageForMovedRow(@NonNull String className, @NonNull String key) {
        String name = className.startsWith(PAGES) ? className.substring(PAGES.length()) : className;
        switch (name) {
            case "KeyboardPreferencesFragment":
                if (key.startsWith("keyboard_voice") || key.equals("keyboard_docs_voice"))
                    return "KeyboardVoicePreferencesFragment";
                if (key.equals("in_app_keyboard_hide_on_hardware") || key.equals("pass_ctrl_space_to_android"))
                    return "KeyboardHardwarePreferencesFragment";
                if (key.equals("keyboard_layout") || key.equals("in_app_keyboard_extra_keys")
                    || key.equals("in_app_keyboard_layouts")
                    || key.startsWith("keyboard_docs_") || key.startsWith("keyboard_credits"))
                    return "KeyboardLayoutPreferencesFragment";
                if (key.equals("keyboard_shapes") || key.startsWith("in_app_keyboard_floating_")
                    || key.equals("in_app_keyboard_split_gap"))
                    return "KeyboardSizePreferencesFragment";
                if (key.equals("keyboard_typing") || key.equals("keyboard_feedback")
                    || key.startsWith("in_app_keyboard_tap_correction")
                    || key.equals("in_app_keyboard_key_sound_enabled")
                    || key.equals("in_app_keyboard_key_popup"))
                    return "KeyboardTypingPreferencesFragment";
                if (key.equals("in_app_keyboard_haptics_enabled")) return "GeneralPreferencesFragment";
                return null;
            case "GeneralPreferencesFragment":
            case "AppBehaviorPreferencesFragment":
                return key.equals("lazy_mode") || key.equals("low_memory_preset")
                    ? "PerformancePreferencesFragment" : null;
            case "LauncherPreferencesFragment":
            case "LauncherDockPreferencesFragment":
                if (key.equals("app_launcher_input_char") || key.equals("app_launcher_reset_usage_ranking"))
                    return "LauncherSearchPreferencesFragment";
                if (key.equals("app_launcher_az_lock_method") || key.equals("app_launcher_az_double_tap_lock"))
                    return "LauncherLockPreferencesFragment";
                if (key.equals("app_launcher_home_palette_gesture") || key.equals("app_launcher_widget_pane_enabled")
                    || key.equals("app_launcher_builtin_widget_style"))
                    return "HomePreferencesFragment";
                if (key.equals("app_haptics_enabled") || key.equals("app_launcher_row_haptics")
                    || key.equals("show_in_recents_when_not_default") || key.equals("app_launcher_set_home")
                    || key.equals("app_launcher_help"))
                    return "GeneralPreferencesFragment";
                if (key.equals("app_launcher_notifications_link")) return "NotificationsPreferencesFragment";
                return null;
            case "HomePreferencesFragment":
                return key.equals("app_launcher_home_page_transition")
                    ? "GeneralPreferencesFragment" : null;
            case "TermuxStylePreferencesFragment":
                if (key.equals("terminal_fonts")) return "TerminalTextPreferencesFragment";
                if (key.equals("keyboard_appearance") || key.equals("customize_keyboard_surface")
                    || key.equals("in_app_keyboard_color_scheme_editor"))
                    return "KeyboardPreferencesFragment";
                if (key.equals("wallpaper_parallax")) return "HomePreferencesFragment";
                if (key.equals("fancier_glass_motion_light")) return "PerformancePreferencesFragment";
                return null;
            case "TerminalPreferencesFragment":
            case "TerminalStatusPreferencesFragment":
                switch (key) {
                    case "split_pane_controls": case "show_key_hints": case "pane_dwindle_default":
                    case "pane_focus_grows": case "pane_agent_api":
                        return "TerminalWindowsPreferencesFragment";
                    case "fullscreen": case "terminal_padding_fill": case "terminal_cursor_trail_style":
                    case "terminal_retro_effect": case "terminal_fonts":
                        return "TerminalTextPreferencesFragment";
                    case "terminal_osc52_clipboard_read_enabled": case "terminal_clipboard_cleanup":
                    case "terminal_margin_adjustment":
                        return "TerminalClipboardPreferencesFragment";
                    case "top_pane_clock_am_pm": case "status_widget_cpu": case "status_widget_ram":
                    case "status_widget_media": case "status_widget_weather":
                    case "status_widget_weather_fahrenheit": case "status_widget_weather_location":
                        return "StatusBarPreferencesFragment";
                    case "show_in_recents_when_not_default":
                        return "GeneralPreferencesFragment";
                    case "lazy_mode":
                        return "PerformancePreferencesFragment";
                    default:
                        return null;
                }
            case "StatusBarPreferencesFragment":
                return key.equals("status_bar_notifications_link") ? "NotificationsPreferencesFragment" : null;
            case "X11DisplayPreferencesFragment":
                switch (key) {
                    case "touchMode": case "x11_android_keyboard": case "x11_keyboard_follows_text":
                    case "clipboardEnable":
                        return "X11DisplayInputPreferencesFragment";
                    case "displayResolutionMode": case "displayScale": case "displayResolutionExact":
                    case "displayResolutionCustom": case "displayFilteringMode": case "x11_display_dpi":
                        return "X11DisplayResolutionPreferencesFragment";
                    case "x11_display_autostart": case "x11_display_command": case "x11_set_display_env":
                        return "X11DisplayStartupPreferencesFragment";
                    case "x11_legacy_drawing": case "x11_force_bgra":
                        return "X11DisplayTroubleshootingPreferencesFragment";
                    default:
                        // The Linux apps rows sit on this page itself now.
                        return null;
                }
            case "TaiPreferencesFragment":
                switch (key) {
                    case "tai_runtime_actions": case "tai_runtime_stop": case "tai_runtime_unload":
                    case "tai_runtime_logs":
                        return "TaiRuntimePreferencesFragment";
                    case "tai_server_category": case "tai_openai_auto_load": case "tai_endpoint_copy":
                    case "tai_api_auth_required": case "tai_lan_enabled":
                        return "TaiApiPreferencesFragment";
                    case "tai_advanced_category": case "tai_parameters_defaults": case "tai_runtime_overrides":
                    case "tai_memory_limits": case "tai_share_diagnostics":
                        return "TaiAdvancedPreferencesFragment";
                    default:
                        return null; // tai_remote_category and tai_remote_provider stay on the overview.
                }
            default:
                if (ROOT.equals(className) && key.equals("app_launcher_start_in")) return "HomePreferencesFragment";
                return null;
        }
    }
}
