package com.termux.app.fragments.settings;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.Preference;

import com.termux.R;

import java.util.HashMap;
import java.util.Map;

/**
 * The leading Material Symbols icon of every settings row that does not declare its own: by the
 * row's key, then by the page the row opens. The drawables are Material Symbols Rounded (24px,
 * weight 400), generated from this file by {@code scripts/material-symbols-vectors.py}; add a
 * {@code R.drawable.ic_ms_<name>} here and run it to fetch the new symbol.
 */
public final class SettingsRowIcons {

    private SettingsRowIcons() {}

    /** The icon for this row, or 0 when it has none. */
    @DrawableRes
    public static int forPreference(@NonNull Preference preference) {
        Integer icon = preference.getKey() == null ? null : BY_KEY.get(preference.getKey());
        if (icon == null) icon = forFragment(preference.getFragment());
        return icon == null ? 0 : icon;
    }

    @Nullable
    private static Integer forFragment(@Nullable String fragment) {
        if (fragment == null) return null;
        String name = fragment.substring(fragment.lastIndexOf('.') + 1);
        return BY_FRAGMENT.get(name);
    }

    private static final Map<String, Integer> BY_KEY = new HashMap<>();
    private static final Map<String, Integer> BY_FRAGMENT = new HashMap<>();

    private static void key(int icon, String... keys) {
        for (String key : keys) BY_KEY.put(key, icon);
    }

    private static void fragment(int icon, String... names) {
        for (String name : names) BY_FRAGMENT.put(name, icon);
    }

    static {
        // Notes: a row that only explains.
        key(R.drawable.ic_ms_info, "gui_apps_hint", "tai_lan_note", "tai_remote_free_plan",
            "tai_remote_consent", "terminal_dynamic_colors_hint", "x11_window_manager_hint",
            "speech_model_empty", "speech_output_missing", "about", "about_support");

        // About & help
        key(R.drawable.ic_ms_menu_book, "documentation", "gui_apps_nix_docs", "keyboard_docs_layouts");
        key(R.drawable.ic_ms_forum, "discord");
        key(R.drawable.ic_ms_tour, "replay_tour");
        key(R.drawable.ic_ms_feedback, "report_issue");
        key(R.drawable.ic_ms_new_releases, "whats_new");
        key(R.drawable.ic_ms_license, "open_source_licenses");
        key(R.drawable.ic_ms_code, "source_code", "keyboard_credits_github");
        key(R.drawable.ic_ms_volunteer_activism, "donate");
        key(R.drawable.ic_ms_help, "app_launcher_help");

        // Diagnostics
        key(R.drawable.ic_ms_receipt_long, "log_level", "tai_runtime_logs");
        key(R.drawable.ic_ms_keyboard, "terminal_view_key_logging_enabled", "app_launcher_drawer_search_on_open",
            "in_app_keyboard_haptics_enabled", "keyboard_input_method", "in_app_keyboard_layouts",
            "x11_android_keyboard");
        key(R.drawable.ic_ms_extension, "plugin_error_notifications_enabled");
        key(R.drawable.ic_ms_bug_report, "crash_report_notifications_enabled");
        key(R.drawable.ic_ms_science, "privileged_backend_smoke_test");
        key(R.drawable.ic_ms_content_copy, "copy_diagnostics", "gui_apps_copy");
        key(R.drawable.ic_ms_ios_share, "export_logs");
        key(R.drawable.ic_ms_delete_sweep, "clear_logs");

        // Drawer
        key(R.drawable.ic_ms_grid_view, "app_launcher_drawer_view_type");
        key(R.drawable.ic_ms_sort_by_alpha, "app_launcher_drawer_az_side");
        key(R.drawable.ic_ms_keyboard_alt, "app_launcher_drawer_search_android_keyboard");
        key(R.drawable.ic_ms_bookmark, "app_launcher_drawer_remember_position");
        key(R.drawable.ic_ms_vertical_align_bottom, "app_launcher_drawer_lower_start");
        key(R.drawable.ic_ms_category, "app_launcher_category_sort");
        key(R.drawable.ic_ms_refresh, "app_launcher_category_refresh");

        // Backup
        key(R.drawable.ic_ms_upload, "settings_backup_export");
        key(R.drawable.ic_ms_download, "settings_backup_import", "speech_model_get_more");
        key(R.drawable.ic_ms_upload_file, "settings_backup_export_models");
        key(R.drawable.ic_ms_file_open, "settings_backup_import_models");

        // General
        key(R.drawable.ic_ms_speed, "animation_speed", "speech_output_speed", "tai_benchmark");
        key(R.drawable.ic_ms_animation, "terminal_cursor_trail", "terminal_cursor_trail_style");
        key(R.drawable.ic_ms_swap_horiz, "app_launcher_home_page_transition");
        key(R.drawable.ic_ms_vibration, "app_haptics_enabled", "keyboard_vibration_link",
            "keyboard_typing_vibration_link");
        key(R.drawable.ic_ms_touch_app, "app_launcher_row_haptics", "keyboard_sub_typing", "touchMode");
        key(R.drawable.ic_ms_battery_saver, "performance_link");
        key(R.drawable.ic_ms_settings_backup_restore, "backup_restore");
        key(R.drawable.ic_ms_admin_panel_settings, "services_permissions", "priv_master_enabled");
        key(R.drawable.ic_ms_troubleshoot, "advanced_diagnostics");
        key(R.drawable.ic_ms_home, "app_launcher_set_home");
        key(R.drawable.ic_ms_restart_alt, "app_launcher_restart", "in_app_keyboard_tap_correction_reset");
        key(R.drawable.ic_ms_history, "show_in_recents_when_not_default", "notification_history_shell");

        // Linux apps
        key(R.drawable.ic_ms_hub, "gui_apps_route");
        key(R.drawable.ic_ms_deployed_code, "gui_apps_distro");
        key(R.drawable.ic_ms_public, "gui_apps_starter_browser");
        key(R.drawable.ic_ms_folder, "gui_apps_starter_files");
        key(R.drawable.ic_ms_developer_board, "x11_gpu", "status_widget_cpu");
        key(R.drawable.ic_ms_apps, "display", "x11_drawer_apps");
        key(R.drawable.ic_ms_volume_up, "launcher_sound");
        key(R.drawable.ic_ms_play_circle, "launcher_media_controls");
        key(R.drawable.ic_ms_install_desktop, "x11_gui_apps_setup");
        key(R.drawable.ic_ms_visibility_off, "x11_hidden_apps");
        key(R.drawable.ic_ms_desktop_windows, "x11_window_manager");
        key(R.drawable.ic_ms_mouse, "x11_sub_input");
        key(R.drawable.ic_ms_fit_screen, "x11_sub_resolution");
        key(R.drawable.ic_ms_power_settings_new, "x11_sub_startup", "x11_display_autostart");
        key(R.drawable.ic_ms_build, "x11_sub_troubleshooting", "tai_advanced");
        key(R.drawable.ic_ms_keyboard_double_arrow_up, "x11_keyboard_follows_text");
        key(R.drawable.ic_ms_layers, "x11_keyboard_over_display");
        key(R.drawable.ic_ms_content_paste, "clipboardEnable", "terminal_osc52_clipboard_read_enabled",
            "terminal_sub_clipboard");
        key(R.drawable.ic_ms_aspect_ratio, "displayResolutionMode", "app_launcher_home_icon_scale");
        key(R.drawable.ic_ms_zoom_in, "displayScale");
        key(R.drawable.ic_ms_crop_free, "displayResolutionExact");
        key(R.drawable.ic_ms_edit, "displayResolutionCustom");
        key(R.drawable.ic_ms_tune, "displayFilteringMode", "tai_parameters_defaults", "keyboard_voice_polish_level");
        key(R.drawable.ic_ms_density_small, "x11_display_dpi");
        key(R.drawable.ic_ms_terminal, "x11_display_command", "priv_allow_shell_fallback",
            "keyboard_voice_details_shell");
        key(R.drawable.ic_ms_link, "x11_set_display_env", "tai_endpoint_copy", "tai_remote_base_url");
        key(R.drawable.ic_ms_brush, "x11_legacy_drawing");
        key(R.drawable.ic_ms_swap_horiz, "x11_force_bgra");

        // Home
        key(R.drawable.ic_ms_dashboard_customize, "home_layout_editor", "terminal_layout_editor");
        key(R.drawable.ic_ms_format_size, "app_launcher_home_label_size", "fonts_size");
        key(R.drawable.ic_ms_wrap_text, "app_launcher_home_label_two_lines");
        key(R.drawable.ic_ms_shadow, "app_launcher_home_label_shadow");
        key(R.drawable.ic_ms_density_medium, "app_launcher_home_folder_density");
        key(R.drawable.ic_ms_swipe_up, "app_launcher_home_palette_gesture");
        key(R.drawable.ic_ms_swipe_down, "app_launcher_home_gesture_swipe_down");
        key(R.drawable.ic_ms_swipe_up_alt, "app_launcher_quick_reply_swipe");
        key(R.drawable.ic_ms_touch_double, "app_launcher_home_gesture_double_tap");
        key(R.drawable.ic_ms_swipe_up_alt, "app_launcher_home_gesture_two_finger_up");
        key(R.drawable.ic_ms_swipe_down_alt, "app_launcher_home_gesture_two_finger_down");
        key(R.drawable.ic_ms_gesture, "home_gestures_helper");
        key(R.drawable.ic_ms_widgets, "app_launcher_widget_pane_enabled", "widgets_on_glass");
        key(R.drawable.ic_ms_schedule, "app_launcher_builtin_widget_style", "top_pane_clock_am_pm");
        key(R.drawable.ic_ms_more_horiz, "app_launcher_home_page_indicator");
        key(R.drawable.ic_ms_view_carousel, "home_edit_pages");
        key(R.drawable.ic_ms_panorama, "wallpaper_parallax");
        key(R.drawable.ic_ms_lock, "app_launcher_home_layout_locked", "app_launcher_az_lock_method",
            "app_launcher_sub_lock");
        key(R.drawable.ic_ms_start, "app_launcher_start_in");

        // Dock & drawer, app search
        key(R.drawable.ic_ms_push_pin, "app_launcher_default_buttons", "essential_notification_rules_manage");
        key(R.drawable.ic_ms_star, "app_launcher_most_used_page");
        key(R.drawable.ic_ms_grid_view, "app_launcher_drawer_layout");
        key(R.drawable.ic_ms_desktop_windows, "linux_apps_in_drawer");
        key(R.drawable.ic_ms_search, "app_launcher_sub_search");
        key(R.drawable.ic_ms_tag, "app_launcher_input_char");
        key(R.drawable.ic_ms_rocket_launch, "app_launcher_drawer_open_only_match");
        key(R.drawable.ic_ms_leaderboard, "app_launcher_reset_usage_ranking");

        // Notifications
        key(R.drawable.ic_ms_circle_notifications, "app_launcher_notification_dots");
        key(R.drawable.ic_ms_app_badging, "app_launcher_notification_badge_style");

        // Battery & performance
        key(R.drawable.ic_ms_bedtime, "lazy_mode");
        key(R.drawable.ic_ms_memory, "low_memory_preset", "status_widget_ram", "tai_memory_limits");
        key(R.drawable.ic_ms_blur_on, "fancier_glass");
        key(R.drawable.ic_ms_auto_awesome, "fancier_glass_motion_light", "tai_welcome_card");
        key(R.drawable.ic_ms_tv, "terminal_retro_effect");

        // Voice output and speech models
        key(R.drawable.ic_ms_record_voice_over, "speech_model_pick", "keyboard_voice_model");
        key(R.drawable.ic_ms_timer, "speech_model_idle_unload", "keyboard_voice_silence_timeout_ms");
        key(R.drawable.ic_ms_graphic_eq, "speech_output_voice", "keyboard_voice_mic_sensitivity");
        key(R.drawable.ic_ms_play_arrow, "speech_output_sample");

        // Status bar
        key(R.drawable.ic_ms_music_note, "status_widget_media");
        key(R.drawable.ic_ms_partly_cloudy_day, "status_widget_weather");
        key(R.drawable.ic_ms_thermostat, "status_widget_weather_fahrenheit");
        key(R.drawable.ic_ms_location_on, "status_widget_weather_location");

        // On-device AI
        key(R.drawable.ic_ms_share, "tai_share_diagnostics");
        key(R.drawable.ic_ms_bolt, "tai_openai_auto_load");
        key(R.drawable.ic_ms_key, "tai_api_auth_required", "tai_remote_api_key", "tai_huggingface_token",
            "keyboard_docs_keys");
        key(R.drawable.ic_ms_wifi, "tai_lan_enabled");
        key(R.drawable.ic_ms_cloud, "tai_remote_provider_row", "tai_remote_provider");
        key(R.drawable.ic_ms_open_in_new, "tai_remote_get_key");
        key(R.drawable.ic_ms_smart_toy, "tai_remote_model", "keyboard_voice_polish_model");
        key(R.drawable.ic_ms_image, "tai_remote_images");
        key(R.drawable.ic_ms_alt_route, "tai_remote_routing_row");
        key(R.drawable.ic_ms_network_check, "tai_remote_test");
        key(R.drawable.ic_ms_delete, "tai_remote_remove");
        key(R.drawable.ic_ms_stop_circle, "tai_runtime_stop");
        key(R.drawable.ic_ms_eject, "tai_runtime_unload");
        key(R.drawable.ic_ms_play_circle, "tai_runtime_controls");
        key(R.drawable.ic_ms_mic, "tai_voice_input", "keyboard_sub_voice");
        key(R.drawable.ic_ms_api, "tai_local_api", "pane_agent_api");

        // Terminal
        key(R.drawable.ic_ms_cleaning_services, "terminal_clipboard_cleanup");
        key(R.drawable.ic_ms_height, "terminal_margin_adjustment", "in_app_keyboard_floating_height");
        key(R.drawable.ic_ms_view_quilt, "terminal_sub_windows");
        key(R.drawable.ic_ms_toolbar, "status_bar");
        key(R.drawable.ic_ms_text_fields, "terminal_sub_text");
        key(R.drawable.ic_ms_font_download, "terminal_fonts", "fonts_use_font_ttf");
        key(R.drawable.ic_ms_fullscreen, "fullscreen");
        key(R.drawable.ic_ms_vertical_split, "split_pane_controls");
        key(R.drawable.ic_ms_keyboard_command_key, "show_key_hints", "in_app_keyboard_extra_keys",
            "edit_extra_keys_row");
        key(R.drawable.ic_ms_auto_awesome_mosaic, "pane_dwindle_default");
        key(R.drawable.ic_ms_center_focus_strong, "pane_focus_grows");
        key(R.drawable.ic_ms_swipe, "terminal_body_swipe");

        // Terminal fonts
        key(R.drawable.ic_ms_emoji_symbols, "nerd_icons");
        key(R.drawable.ic_ms_join, "fonts_ligatures");
        key(R.drawable.ic_ms_format_bold, "fonts_weight");
        key(R.drawable.ic_ms_edit_note, "fonts_power_user_hint");

        // Keyboard
        key(R.drawable.ic_ms_keyboard_hide, "in_app_keyboard_hide_on_hardware");
        key(R.drawable.ic_ms_language, "pass_ctrl_space_to_android", "keyboard_sub_layout", "keyboard_voice_language");
        key(R.drawable.ic_ms_favorite, "keyboard_credits_text");
        key(R.drawable.ic_ms_shop, "keyboard_credits_play");
        key(R.drawable.ic_ms_open_in_full, "keyboard_sub_size");
        key(R.drawable.ic_ms_match_case, "extra_keys_text_all_caps");
        key(R.drawable.ic_ms_keyboard_external_input, "keyboard_sub_hardware");
        key(R.drawable.ic_ms_width, "in_app_keyboard_floating_width");
        key(R.drawable.ic_ms_space_bar, "in_app_keyboard_split_gap");
        key(R.drawable.ic_ms_ads_click, "in_app_keyboard_tap_correction");
        key(R.drawable.ic_ms_model_training, "in_app_keyboard_tap_correction_status");
        key(R.drawable.ic_ms_volume_up, "in_app_keyboard_key_sound_enabled", "keyboard_voice_sounds");
        key(R.drawable.ic_ms_visibility, "in_app_keyboard_key_popup");
        key(R.drawable.ic_ms_auto_fix_high, "keyboard_voice_details_cleanup", "keyboard_voice_polish");
        key(R.drawable.ic_ms_block, "keyboard_voice_details_off");
        key(R.drawable.ic_ms_settings_voice, "keyboard_voice_engine");
        key(R.drawable.ic_ms_description, "keyboard_voice_details");
        key(R.drawable.ic_ms_pause, "keyboard_voice_pause_ms");

        // Privileged access
        key(R.drawable.ic_ms_verified_user, "priv_prefer_shizuku");
        key(R.drawable.ic_ms_lock_open, "priv_request_shizuku_permission");
        key(R.drawable.ic_ms_route, "priv_lane_enabled");

        // Look
        key(R.drawable.ic_ms_dark_mode, "theme_mode");
        key(R.drawable.ic_ms_colorize, "terminal_dynamic_colors_enabled");
        key(R.drawable.ic_ms_contrast, "terminal_contrast_level");
        key(R.drawable.ic_ms_format_paint, "theme_templates_enabled");
        key(R.drawable.ic_ms_wallpaper, "use_system_wallpaper");
        key(R.drawable.ic_ms_filter_b_and_w, "app_launcher_bw_icons");

        // A row with no key of its own, by the page it opens.
        fragment(R.drawable.ic_ms_bug_report, "DebuggingPreferencesFragment");
        fragment(R.drawable.ic_ms_home, "HomePreferencesFragment");
        fragment(R.drawable.ic_ms_dock_to_bottom, "LauncherPreferencesFragment");
        fragment(R.drawable.ic_ms_terminal, "TerminalPreferencesFragment");
        fragment(R.drawable.ic_ms_palette, "TermuxStylePreferencesFragment");
        fragment(R.drawable.ic_ms_keyboard, "KeyboardPreferencesFragment");
        fragment(R.drawable.ic_ms_notifications, "NotificationsPreferencesFragment");
        fragment(R.drawable.ic_ms_smart_toy, "TaiPreferencesFragment");
        fragment(R.drawable.ic_ms_settings, "GeneralPreferencesFragment");
        fragment(R.drawable.ic_ms_apps, "X11DisplayPreferencesFragment", "AppDrawerPreferencesFragment");
        fragment(R.drawable.ic_ms_gesture, "HomeGesturesPreferencesFragment");
    }
}
