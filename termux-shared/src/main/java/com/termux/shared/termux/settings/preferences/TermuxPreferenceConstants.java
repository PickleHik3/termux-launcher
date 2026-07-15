package com.termux.shared.termux.settings.preferences;

/*
 * Version: v0.17.0
 *
 * Changelog
 *
 * - 0.1.0 (2021-03-12)
 *      - Initial Release.
 *
 * - 0.2.0 (2021-03-13)
 *      - Added `KEY_LOG_LEVEL` and `KEY_TERMINAL_VIEW_LOGGING_ENABLED`.
 *
 * - 0.3.0 (2021-03-16)
 *      - Changed to per app scoping of variables so that the same file can store all constants of
 *          Termux app and its plugins. This will allow {@link com.termux.app.TermuxSettings} to
 *          manage preferences of plugins as well if they don't have launcher activity themselves
 *          and also allow plugin apps to make changes to preferences from background.
 *      - Added following to `TERMUX_TASKER_APP`:
 *           `KEY_LOG_LEVEL`.
 *
 * - 0.4.0 (2021-03-13)
 *      - Added following to `TERMUX_APP`:
 *          `KEY_PLUGIN_ERROR_NOTIFICATIONS_ENABLED` and `DEFAULT_VALUE_PLUGIN_ERROR_NOTIFICATIONS_ENABLED`.
 *
 * - 0.5.0 (2021-03-24)
 *      - Added following to `TERMUX_APP`:
 *          `KEY_LAST_NOTIFICATION_ID` and `DEFAULT_VALUE_KEY_LAST_NOTIFICATION_ID`.
 *
 * - 0.6.0 (2021-03-24)
 *      - Change `DEFAULT_VALUE_KEEP_SCREEN_ON` value to `false` in `TERMUX_APP`.
 *
 * - 0.7.0 (2021-03-27)
 *      - Added following to `TERMUX_APP`:
 *          `KEY_SOFT_KEYBOARD_ENABLED` and `DEFAULT_VALUE_KEY_SOFT_KEYBOARD_ENABLED`.
 *
 * - 0.8.0 (2021-04-06)
 *      - Added following to `TERMUX_APP`:
 *          `KEY_CRASH_REPORT_NOTIFICATIONS_ENABLED` and `DEFAULT_VALUE_CRASH_REPORT_NOTIFICATIONS_ENABLED`.
 *
 * - 0.9.0 (2021-04-07)
 *      - Updated javadocs.
 *
 * - 0.10.0 (2021-05-12)
 *      - Added following to `TERMUX_APP`:
 *          `KEY_SOFT_KEYBOARD_ENABLED_ONLY_IF_NO_HARDWARE` and `DEFAULT_VALUE_KEY_SOFT_KEYBOARD_ENABLED_ONLY_IF_NO_HARDWARE`.
 *
 * - 0.11.0 (2021-07-08)
 *      - Added following to `TERMUX_APP`:
 *          `KEY_DISABLE_TERMINAL_MARGIN_ADJUSTMENT`.
 *
 * - 0.12.0 (2021-08-27)
 *      - Added `TERMUX_API_APP.KEY_LOG_LEVEL`, `TERMUX_BOOT_APP.KEY_LOG_LEVEL`,
 *          `TERMUX_FLOAT_APP.KEY_LOG_LEVEL`, `TERMUX_STYLING_APP.KEY_LOG_LEVEL`,
 *          `TERMUX_Widget_APP.KEY_LOG_LEVEL`.
 *
 * - 0.13.0 (2021-09-02)
 *      - Added following to `TERMUX_FLOAT_APP`:
 *          `KEY_WINDOW_X`, `KEY_WINDOW_Y`, `KEY_WINDOW_WIDTH`, `KEY_WINDOW_HEIGHT`, `KEY_FONTSIZE`,
 *          `KEY_TERMINAL_VIEW_KEY_LOGGING_ENABLED`.
 *
 * - 0.14.0 (2021-09-04)
 *      - Added `TERMUX_WIDGET_APP.KEY_TOKEN`.
 *
 * - 0.15.0 (2021-09-05)
 *      - Added following to `TERMUX_TASKER_APP`:
 *          `KEY_LAST_PENDING_INTENT_REQUEST_CODE` and `DEFAULT_VALUE_KEY_LAST_PENDING_INTENT_REQUEST_CODE`.
 *
 * - 0.16.0 (2022-06-11)
 *      - Added following to `TERMUX_APP`:
 *          `KEY_APP_SHELL_NUMBER_SINCE_BOOT` and `KEY_TERMINAL_SESSION_NUMBER_SINCE_BOOT`.
 *
 * - 0.16.5 (2022-08-18)
 *      - Add `KEY_ACTIVITY_FINISH_REMOVE_TASK`.
 */
import com.termux.shared.shell.command.ExecutionCommand;

/**
 * A class that defines shared constants of the SharedPreferences used by Termux app and its plugins.
 * This class will be hosted by termux-shared lib and should be imported by other termux plugin
 * apps as is instead of copying constants to random classes. The 3rd party apps can also import
 * it for interacting with termux apps. If changes are made to this file, increment the version number
 * and add an entry in the Changelog section above.
 */
public final class TermuxPreferenceConstants {

    /**
     * Termux app constants.
     */
    public static final class TERMUX_APP {

        /**
         * Defines the key for whether terminal view margin adjustment that is done to prevent soft
         * keyboard from covering bottom part of terminal view on some devices is enabled or not.
         * Margin adjustment may cause screen flickering on some devices and so should be disabled.
         */
        public static final String KEY_TERMINAL_MARGIN_ADJUSTMENT = "terminal_margin_adjustment";

        public static final boolean DEFAULT_TERMINAL_MARGIN_ADJUSTMENT = true;

        /**
         * Defines a one-time migration marker for restoring terminal margin adjustment default after
         * temporary workaround builds forced it off.
         */
        public static final String KEY_TERMINAL_MARGIN_ADJUSTMENT_DEFAULT_MIGRATION_DONE =
            "terminal_margin_adjustment_default_migration_done";

        public static final boolean DEFAULT_TERMINAL_MARGIN_ADJUSTMENT_DEFAULT_MIGRATION_DONE = false;

        /**
         * Defines the key for whether to show terminal toolbar containing extra keys and text input field.
         */
        public static final String KEY_SHOW_TERMINAL_TOOLBAR = "show_extra_keys";

        public static final boolean DEFAULT_VALUE_SHOW_TERMINAL_TOOLBAR = true;

        /**
         * Defines the key for app launcher button count.
         */
        public static final String KEY_APP_LAUNCHER_BUTTON_COUNT = "app_launcher_button_count";

        public static final int DEFAULT_APP_LAUNCHER_BUTTON_COUNT = 7;

        /**
         * Defines the key for app launcher input split character.
         */
        public static final String KEY_APP_LAUNCHER_INPUT_CHAR = "app_launcher_input_char";

        public static final String DEFAULT_APP_LAUNCHER_INPUT_CHAR = "%";

        /**
         * Defines the key for app launcher default buttons.
         */
        public static final String KEY_APP_LAUNCHER_DEFAULT_BUTTONS = "app_launcher_default_buttons";

        public static final String DEFAULT_APP_LAUNCHER_DEFAULT_BUTTONS = "";

        /**
         * Defines the key for enabling the launcher app icons row.
         */
        public static final String KEY_APP_LAUNCHER_APPS_ROW_ENABLED = "app_launcher_apps_row_enabled";

        public static final boolean DEFAULT_APP_LAUNCHER_APPS_ROW_ENABLED = true;

        /**
         * Defines the key for showing active-notification dots on apps bar icons.
         */
        public static final String KEY_APP_LAUNCHER_NOTIFICATION_DOTS = "app_launcher_notification_dots";

        public static final boolean DEFAULT_APP_LAUNCHER_NOTIFICATION_DOTS = false;

        /**
         * Defines the key for the optional dynamic "most used apps" dock page.
         */
        public static final String KEY_APP_LAUNCHER_MOST_USED_PAGE = "app_launcher_most_used_page";

        public static final boolean DEFAULT_APP_LAUNCHER_MOST_USED_PAGE = false;

        /**
         * Defines the key for app launcher bar height scale.
         */
        public static final String KEY_APP_LAUNCHER_BAR_HEIGHT = "app_launcher_bar_height";

        public static final float DEFAULT_APP_LAUNCHER_BAR_HEIGHT = 2.18f;

        /**
         * Defines the visual surface style for the app launcher dock.
         */
        public static final String KEY_APP_LAUNCHER_DOCK_STYLE = "app_launcher_dock_style";

        public static final String APP_LAUNCHER_DOCK_STYLE_DEFAULT = "default";

        public static final String APP_LAUNCHER_DOCK_STYLE_VALARIE_CAPSULE = "valarie_capsule";

        public static final String DEFAULT_APP_LAUNCHER_DOCK_STYLE = APP_LAUNCHER_DOCK_STYLE_DEFAULT;

        /**
         * Defines the key for showing focused app names while scrubbing the dock.
         */
        public static final String KEY_APP_LAUNCHER_DISPLAY_APP_NAMES = "app_launcher_display_app_names";

        public static final boolean DEFAULT_APP_LAUNCHER_DISPLAY_APP_NAMES = true;

        /**
         * Defines the key for app launcher black and white icons.
         */
        public static final String KEY_APP_LAUNCHER_BW_ICONS = "app_launcher_bw_icons";

        public static final boolean DEFAULT_APP_LAUNCHER_BW_ICONS = false;


        /**
         * Defines the key for harmonizing app icons with the dock (consistent footprint, soft
         * shadow, slight saturation match) without requiring a custom icon pack.
         */
        public static final String KEY_APP_LAUNCHER_UNIFY_ICONS = "app_launcher_unify_icons";

        public static final boolean DEFAULT_APP_LAUNCHER_UNIFY_ICONS = true;

        /**
         * Defines the key for the soft drop shadow behind dock app icons.
         */
        public static final String KEY_APP_LAUNCHER_ICON_SHADOW = "app_launcher_icon_shadow";

        public static final boolean DEFAULT_APP_LAUNCHER_ICON_SHADOW = true;

        /**
         * Defines the selected launcher icon-pack package.
         */
        public static final String KEY_APP_LAUNCHER_ICON_PACK_PACKAGE = "app_launcher_icon_pack_package";

        public static final String DEFAULT_APP_LAUNCHER_ICON_PACK_PACKAGE = "";

        /**
         * Defines the selected launcher icon-pack package for pinned apps only.
         */
        public static final String KEY_APP_LAUNCHER_PINNED_ICON_PACK_PACKAGE = "app_launcher_pinned_icon_pack_package";

        public static final String DEFAULT_APP_LAUNCHER_PINNED_ICON_PACK_PACKAGE = "";

        /**
         * Defines the key for app launcher icon scale.
         */
        public static final String KEY_APP_LAUNCHER_ICON_SCALE = "app_launcher_icon_scale";

        public static final float DEFAULT_APP_LAUNCHER_ICON_SCALE = 1.55f;

        /**
         * Defines the key for typed pinned apps/folders launcher configuration.
         */
        public static final String KEY_APP_LAUNCHER_PINNED_ITEMS_V2 = "app_launcher_pinned_items_v2";

        public static final String DEFAULT_APP_LAUNCHER_PINNED_ITEMS_V2 = "";

        /**
         * Defines the key for typed pinned items config schema version.
         */
        public static final String KEY_APP_LAUNCHER_PINNED_ITEMS_SCHEMA_VERSION = "app_launcher_pinned_items_schema_version";

        public static final int DEFAULT_APP_LAUNCHER_PINNED_ITEMS_SCHEMA_VERSION = 0;

        /**
         * Defines the key for enabling A-Z scrub row for launcher.
         */
        public static final String KEY_APP_LAUNCHER_AZ_ROW_ENABLED = "app_launcher_az_row_enabled";

        public static final boolean DEFAULT_APP_LAUNCHER_AZ_ROW_ENABLED = true;

        /**
         * Defines the key for enabling double-tap on A-Z row to lock screen.
         */
        public static final String KEY_APP_LAUNCHER_AZ_DOUBLE_TAP_LOCK = "app_launcher_az_double_tap_lock";

        public static final boolean DEFAULT_APP_LAUNCHER_AZ_DOUBLE_TAP_LOCK = false;

        /**
         * Defines the selected backend for double-tap on A-Z row to lock screen.
         */
        public static final String KEY_APP_LAUNCHER_AZ_LOCK_METHOD = "app_launcher_az_lock_method";

        public static final String APP_LAUNCHER_AZ_LOCK_METHOD_OFF = "off";

        public static final String APP_LAUNCHER_AZ_LOCK_METHOD_SHIZUKU = "shizuku";

        public static final String APP_LAUNCHER_AZ_LOCK_METHOD_ACCESSIBILITY = "accessibility";

        public static final String DEFAULT_APP_LAUNCHER_AZ_LOCK_METHOD = APP_LAUNCHER_AZ_LOCK_METHOD_OFF;

        /**
         * Defines the key for enabling launcher app open/close animations.
         */
        public static final String KEY_APP_LAUNCHER_ANIMATIONS_ENABLED = "app_launcher_animations_enabled";

        public static final boolean DEFAULT_APP_LAUNCHER_ANIMATIONS_ENABLED = true;

        /**
         * Defines the key for launcher animation safe mode auto-fallback.
         */
        public static final String KEY_APP_LAUNCHER_ANIMATION_SAFE_MODE = "app_launcher_animation_safe_mode";

        public static final boolean DEFAULT_APP_LAUNCHER_ANIMATION_SAFE_MODE = false;

        /**
         * Defines the key for whether the soft keyboard will be enabled, for cases where users want
         * to use a hardware keyboard instead.
         */
        public static final String KEY_SOFT_KEYBOARD_ENABLED = "soft_keyboard_enabled";

        public static final boolean DEFAULT_VALUE_KEY_SOFT_KEYBOARD_ENABLED = true;

        /**
         * Defines the key for whether the soft keyboard will be enabled only if no hardware keyboard
         * attached, for cases where users want to use a hardware keyboard instead.
         */
        public static final String KEY_SOFT_KEYBOARD_ENABLED_ONLY_IF_NO_HARDWARE = "soft_keyboard_enabled_only_if_no_hardware";

        public static final boolean DEFAULT_VALUE_KEY_SOFT_KEYBOARD_ENABLED_ONLY_IF_NO_HARDWARE = false;

        /**
         * Defines the key for whether termux will remove itself from the recent apps screen when
         * it closes itself.
         */
        public static final String KEY_ACTIVITY_FINISH_REMOVE_TASK = "activity_finish_remove_task";

        public static final boolean DEFAULT_VALUE_KEY_ACTIVITY_FINISH_REMOVE_TASK = true;

        /**
         * Defines the key for whether to always keep screen on.
         */
        public static final String KEY_KEEP_SCREEN_ON = "screen_always_on";

        public static final boolean DEFAULT_VALUE_KEEP_SCREEN_ON = false;

        /**
         * Defines the key for font size of termux terminal view.
         */
        public static final String KEY_FONTSIZE = "fontsize";

        /**
         * Defines the key for current termux terminal session.
         */
        public static final String KEY_CURRENT_SESSION = "current_session";

        /**
         * Defines the key for current log level.
         */
        public static final String KEY_LOG_LEVEL = "log_level";

        /**
         * Defines the key for last used notification id.
         */
        public static final String KEY_LAST_NOTIFICATION_ID = "last_notification_id";

        public static final int DEFAULT_VALUE_KEY_LAST_NOTIFICATION_ID = 0;

        /**
         * The {@link ExecutionCommand.Runner#APP_SHELL} number after termux app process since boot.
         */
        public static final String KEY_APP_SHELL_NUMBER_SINCE_BOOT = "app_shell_number_since_boot";

        public static final int DEFAULT_VALUE_APP_SHELL_NUMBER_SINCE_BOOT = 0;

        /**
         * The {@link ExecutionCommand.Runner#TERMINAL_SESSION} number after termux app process since boot.
         */
        public static final String KEY_TERMINAL_SESSION_NUMBER_SINCE_BOOT = "terminal_session_number_since_boot";

        public static final int DEFAULT_VALUE_TERMINAL_SESSION_NUMBER_SINCE_BOOT = 0;

        /**
         * Defines the key for whether termux terminal view key logging is enabled or not
         */
        public static final String KEY_TERMINAL_VIEW_KEY_LOGGING_ENABLED = "terminal_view_key_logging_enabled";

        public static final boolean DEFAULT_VALUE_TERMINAL_VIEW_KEY_LOGGING_ENABLED = false;

        /**
         * Defines the key for whether flashes and notifications for plugin errors are enabled or not.
         */
        public static final String KEY_PLUGIN_ERROR_NOTIFICATIONS_ENABLED = "plugin_error_notifications_enabled";

        public static final boolean DEFAULT_VALUE_PLUGIN_ERROR_NOTIFICATIONS_ENABLED = true;

        /**
         * Defines the key for whether notifications for crash reports are enabled or not.
         */
        public static final String KEY_CRASH_REPORT_NOTIFICATIONS_ENABLED = "crash_report_notifications_enabled";

        public static final boolean DEFAULT_VALUE_CRASH_REPORT_NOTIFICATIONS_ENABLED = true;

        /**
         * Defines the key for terminal background opacity (percentage), where 100 is fully opaque.
         */
        public static final String KEY_TERMINAL_BACKGROUND_OPACITY = "terminal_background_opacity";

        public static final int DEFAULT_VALUE_TERMINAL_BACKGROUND_OPACITY = 100;

        /**
         * Defines the key for sessions menu opacity (percentage), where 100 is fully opaque.
         */
        public static final String KEY_SESSIONS_OPACITY = "sessions_opacity";

        public static final int DEFAULT_VALUE_SESSIONS_OPACITY = 50;

        /**
         * Defines the key for extrakeys/app bar blur radius (dp). 0 disables blur.
         */
        public static final String KEY_EXTRAKEYS_BLUR_RADIUS = "extrakeys_blur_radius";

        public static final int DEFAULT_VALUE_EXTRAKEYS_BLUR_RADIUS = 10;

        /**
         * Defines the key for extrakeys/app bar opacity (percentage), where 100 is fully opaque.
         */
        public static final String KEY_APP_BAR_OPACITY = "app_bar_opacity";

        public static final int DEFAULT_VALUE_APP_BAR_OPACITY = 50;

        /**
         * Defines the key for the dock-glass grain/noise amount (percentage, 0 disables). A subtle
         * film grain over the frosted glass that reads as real glass texture rather than flat blur.
         */
        public static final String KEY_DOCK_GLASS_GRAIN = "dock_glass_grain";

        public static final int DEFAULT_VALUE_DOCK_GLASS_GRAIN = 12;

        /**
         * Defines whether the terminal's bottom cell remainder is absorbed by the dock glass.
         */
        public static final String KEY_TERMINAL_FLUSH_DOCK = "terminal_flush_dock";

        public static final boolean DEFAULT_VALUE_TERMINAL_FLUSH_DOCK = false;

        /**
         * Stores the user's preferred terminal opacity while wallpaper mode is enabled so it can
         * be restored after temporarily disabling wallpaper.
         */
        public static final String KEY_WALLPAPER_ENABLED_TERMINAL_BACKGROUND_OPACITY =
            "wallpaper_enabled_terminal_background_opacity";

        public static final int DEFAULT_VALUE_WALLPAPER_ENABLED_TERMINAL_BACKGROUND_OPACITY = 50;

        /**
         * Stores the user's preferred dock opacity while wallpaper mode is enabled so it can be
         * be restored after temporarily disabling wallpaper.
         */
        public static final String KEY_WALLPAPER_ENABLED_APP_BAR_OPACITY =
            "wallpaper_enabled_app_bar_opacity";

        public static final int DEFAULT_VALUE_WALLPAPER_ENABLED_APP_BAR_OPACITY = 50;

        /**
         * Stores the user's preferred dock blur radius while wallpaper mode is enabled so it can
         * be restored after temporarily disabling wallpaper.
         */
        public static final String KEY_WALLPAPER_ENABLED_EXTRAKEYS_BLUR_RADIUS =
            "wallpaper_enabled_extrakeys_blur_radius";

        public static final int DEFAULT_VALUE_WALLPAPER_ENABLED_EXTRAKEYS_BLUR_RADIUS =
            DEFAULT_VALUE_EXTRAKEYS_BLUR_RADIUS;

        /**
         * Stores the system wallpaper id for the last wallpaper set through the in-app picker, so
         * exact dock blur can be used only while that wallpaper is still active.
         */
        public static final String KEY_MANAGED_WALLPAPER_SYSTEM_ID =
            "managed_wallpaper_system_id";

        public static final int DEFAULT_VALUE_MANAGED_WALLPAPER_SYSTEM_ID = -1;
        
        /**
         * Defines the key for whether extrakeys blur is enabled or not.
         */
        public static final String KEY_EXTRAKEYS_BLUR_ENABLED = "extrakeys_blur_enabled";

        public static final boolean DEFAULT_VALUE_EXTRAKEYS_BLUR_ENABLED = false;
        
        /**
         * Defines the key for whether sessions blur is enabled or not.
         */
        public static final String KEY_SESSIONS_BLUR_ENABLED = "sessions_blur_enabled";

        public static final boolean DEFAULT_VALUE_SESSIONS_BLUR_ENABLED = false;
        
        /**
         * Defines the key for whether monet background is enabled or not.
         */
        public static final String KEY_MONET_BACKGROUND_ENABLED = "monet_background_enabled";

        public static final boolean DEFAULT_VALUE_MONET_BACKGROUND_ENABLED = false;

        /**
         * Defines the key for whether monet color should be used for the terminal background overlay.
         */
        public static final String KEY_MONET_OVERLAY_ENABLED = "monet_overlay_enabled";

        public static final boolean DEFAULT_VALUE_MONET_OVERLAY_ENABLED = false;

        /**
         * Defines the key for whether terminal colors should follow Material dynamic colors.
         */
        public static final String KEY_TERMINAL_DYNAMIC_COLORS_ENABLED = "terminal_dynamic_colors_enabled";

        public static final boolean DEFAULT_VALUE_TERMINAL_DYNAMIC_COLORS_ENABLED = true;

        /**
         * Defines the key for whether the system wallpaper should be used.
         */
        public static final String KEY_USE_SYSTEM_WALLPAPER = "use_system_wallpaper";

        public static final boolean DEFAULT_VALUE_USE_SYSTEM_WALLPAPER = true;
    }

    /**
     * Termux:API app constants.
     */
    public static final class TERMUX_API_APP {

        /**
         * Defines the key for current log level.
         */
        public static final String KEY_LOG_LEVEL = "log_level";

        /**
         * Defines the key for last used PendingIntent request code.
         */
        public static final String KEY_LAST_PENDING_INTENT_REQUEST_CODE = "last_pending_intent_request_code";

        public static final int DEFAULT_VALUE_KEY_LAST_PENDING_INTENT_REQUEST_CODE = 0;
    }

    /**
     * Termux:Boot app constants.
     */
    public static final class TERMUX_BOOT_APP {

        /**
         * Defines the key for current log level.
         */
        public static final String KEY_LOG_LEVEL = "log_level";
    }

    /**
     * Termux:Float app constants.
     */
    public static final class TERMUX_FLOAT_APP {

        /**
         * The float window x coordinate.
         */
        public static final String KEY_WINDOW_X = "window_x";

        /**
         * The float window y coordinate.
         */
        public static final String KEY_WINDOW_Y = "window_y";

        /**
         * The float window width.
         */
        public static final String KEY_WINDOW_WIDTH = "window_width";

        /**
         * The float window height.
         */
        public static final String KEY_WINDOW_HEIGHT = "window_height";

        /**
         * Defines the key for font size of termux terminal view.
         */
        public static final String KEY_FONTSIZE = "fontsize";

        /**
         * Defines the key for current log level.
         */
        public static final String KEY_LOG_LEVEL = "log_level";

        /**
         * Defines the key for whether termux terminal view key logging is enabled or not
         */
        public static final String KEY_TERMINAL_VIEW_KEY_LOGGING_ENABLED = "terminal_view_key_logging_enabled";

        public static final boolean DEFAULT_VALUE_TERMINAL_VIEW_KEY_LOGGING_ENABLED = false;
    }

    /**
     * Termux:Styling app constants.
     */
    public static final class TERMUX_STYLING_APP {

        /**
         * Defines the key for current log level.
         */
        public static final String KEY_LOG_LEVEL = "log_level";
    }

    /**
     * Termux:Tasker app constants.
     */
    public static final class TERMUX_TASKER_APP {

        /**
         * Defines the key for current log level.
         */
        public static final String KEY_LOG_LEVEL = "log_level";

        /**
         * Defines the key for last used PendingIntent request code.
         */
        public static final String KEY_LAST_PENDING_INTENT_REQUEST_CODE = "last_pending_intent_request_code";

        public static final int DEFAULT_VALUE_KEY_LAST_PENDING_INTENT_REQUEST_CODE = 0;
    }

    /**
     * Termux:GUI app constants.
     */
    public static final class TERMUX_GUI_APP {

        /**
         * Defines the key for current log level.
         */
        public static final String KEY_LOG_LEVEL = "log_level";
    }

    /**
     * Termux:Widget app constants.
     */
    public static final class TERMUX_WIDGET_APP {

        /**
         * Defines the key for current log level.
         */
        public static final String KEY_LOG_LEVEL = "log_level";

        /**
         * Defines the key for current token for shortcuts.
         */
        public static final String KEY_TOKEN = "token";
    }
}
