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
         * Defines the key for which screen edge the landscape dock rail is docked to. The app
         * drawer's pull runs away from that edge, so this also picks the swipe that opens it.
         */
        public static final String KEY_APP_LAUNCHER_DOCK_RAIL_SIDE = "app_launcher_dock_rail_side";

        public static final String APP_LAUNCHER_DOCK_RAIL_SIDE_LEFT = "left";

        public static final String APP_LAUNCHER_DOCK_RAIL_SIDE_RIGHT = "right";

        public static final String DEFAULT_APP_LAUNCHER_DOCK_RAIL_SIDE =
            APP_LAUNCHER_DOCK_RAIL_SIDE_LEFT;

        /**
         * Defines the key for enabling the terminal extra-keys dock row.
         */
        public static final String KEY_APP_LAUNCHER_EXTRA_KEYS_ROW_ENABLED =
            "app_launcher_extra_keys_row_enabled";

        public static final boolean DEFAULT_APP_LAUNCHER_EXTRA_KEYS_ROW_ENABLED = true;

        /**
         * Defines the key for enabling the home widget pane reached by the status bar pull-down.
         */
        public static final String KEY_APP_LAUNCHER_WIDGET_PANE_ENABLED =
            "app_launcher_widget_pane_enabled";

        public static final boolean DEFAULT_APP_LAUNCHER_WIDGET_PANE_ENABLED = true;

        /** The widget pane's grid: how many columns across and rows down a page has. */
        public static final String KEY_APP_LAUNCHER_WIDGET_GRID_COLUMNS = "app_launcher_widget_grid_columns";

        public static final int DEFAULT_APP_LAUNCHER_WIDGET_GRID_COLUMNS = 4;

        public static final int MIN_APP_LAUNCHER_WIDGET_GRID_COLUMNS = 2;

        public static final int MAX_APP_LAUNCHER_WIDGET_GRID_COLUMNS = 8;

        public static final String KEY_APP_LAUNCHER_WIDGET_GRID_ROWS = "app_launcher_widget_grid_rows";

        public static final int DEFAULT_APP_LAUNCHER_WIDGET_GRID_ROWS = 5;

        public static final int MIN_APP_LAUNCHER_WIDGET_GRID_ROWS = 2;

        public static final int MAX_APP_LAUNCHER_WIDGET_GRID_ROWS = 12;

        /**
         * Defines the key for the embedded Linux display: the wall's Display page, the
         * {@code termux-x11} command the launcher writes into the prefix, and the display
         * settings. Off until the user asks for it — a home screen that never wants a display
         * should pay nothing for one.
         */
        public static final String KEY_X11_DISPLAY_ENABLED = "x11_display_enabled";

        public static final boolean DEFAULT_X11_DISPLAY_ENABLED = false;

        /** Command line the "Start display" button and the start-up opt-in run. */
        public static final String KEY_X11_DISPLAY_COMMAND = "x11_display_command";

        public static final String DEFAULT_X11_DISPLAY_COMMAND = "termux-x11 :0";

        /** Run the start command when the launcher's service comes up. Off: a home screen that never asked for a display pays nothing for one. */
        public static final String KEY_X11_DISPLAY_AUTOSTART = "x11_display_autostart";

        public static final boolean DEFAULT_X11_DISPLAY_AUTOSTART = false;

        /** Put {@code DISPLAY} into new shells while a display is running. Off, as Termux:X11 never did it. */
        public static final String KEY_X11_SET_DISPLAY_ENV = "x11_set_display_env";

        public static final boolean DEFAULT_X11_SET_DISPLAY_ENV = false;

        /** The X screen's dots per inch when the launcher starts the server; 0 leaves it to the server. */
        public static final String KEY_X11_DISPLAY_DPI = "x11_display_dpi";

        public static final int DEFAULT_X11_DISPLAY_DPI = 0;

        /** Pass {@code -legacy-drawing} when the launcher starts the server. */
        public static final String KEY_X11_LEGACY_DRAWING = "x11_legacy_drawing";

        public static final boolean DEFAULT_X11_LEGACY_DRAWING = false;

        /** Pass {@code -force-bgra} when the launcher starts the server. */
        public static final String KEY_X11_FORCE_BGRA = "x11_force_bgra";

        public static final boolean DEFAULT_X11_FORCE_BGRA = false;

        /** List the prefix's Linux apps in the app drawer, to be run on the display. */
        public static final String KEY_X11_DRAWER_APPS = "x11_drawer_apps";

        public static final boolean DEFAULT_X11_DRAWER_APPS = true;

        /** The window manager the launcher starts with the server, so windows open full size. Empty for none. */
        public static final String KEY_X11_WINDOW_MANAGER = "x11_window_manager";

        public static final String DEFAULT_X11_WINDOW_MANAGER = "openbox";

        /** Set once the phone-sized defaults (text size, touch mode) have been written on first turn-on. */
        public static final String KEY_X11_DEFAULTS_APPLIED = "x11_defaults_applied";

        /** Whether the keyboard was up the last time the user left the Display place. */
        public static final String KEY_X11_KEYBOARD_SHOWN = "x11_keyboard_shown";

        /**
         * The mark the status bar's Display badge wears: the Termux X11 glyph for native apps, or
         * the glyph of the proot distribution the display is used for.
         */
        public static final String KEY_X11_RUNTIME_BADGE = "x11_runtime_badge";

        /**
         * Where the extra keys sit while the Display place is showing: along the bottom as usual,
         * or as a column on one screen edge so the display keeps its height.
         */
        public static final String KEY_X11_EXTRA_KEYS_SIDE = "x11_extra_keys_side";

        public static final String X11_EXTRA_KEYS_SIDE_BOTTOM = "bottom";

        public static final String X11_EXTRA_KEYS_SIDE_LEFT = "left";

        public static final String X11_EXTRA_KEYS_SIDE_RIGHT = "right";

        public static final String DEFAULT_X11_EXTRA_KEYS_SIDE = X11_EXTRA_KEYS_SIDE_BOTTOM;

        /** Hide the launcher's status bar while the Display place is showing. */
        public static final String KEY_X11_HIDE_STATUS_BAR = "x11_hide_status_bar";

        public static final boolean DEFAULT_X11_HIDE_STATUS_BAR = false;

        public static final String DEFAULT_X11_RUNTIME_BADGE = "termux";

        /** The place the pane wall last rested on; the home screen comes back to it. */
        public static final String KEY_WALL_LAST_PAGE = "wall_last_page";

        /**
         * Defines the key for the launcher / terminal-only use case the user picked. Stored rather
         * than derived from the surface switches: the surfaces stay individually settable after a
         * mode is picked, and a derived mode would jump between the two the moment one is flipped.
         */
        public static final String KEY_APP_LAUNCHER_USE_CASE_MODE = "app_launcher_use_case_mode";

        public static final String APP_LAUNCHER_USE_CASE_MODE_LAUNCHER = "launcher";
        public static final String APP_LAUNCHER_USE_CASE_MODE_TERMINAL = "terminal";
        public static final String DEFAULT_APP_LAUNCHER_USE_CASE_MODE =
            APP_LAUNCHER_USE_CASE_MODE_LAUNCHER;

        /**
         * Defines the key holding the launcher surface states captured when the user switched to
         * terminal-only mode, so switching back restores the layout they had. Encoded by
         * {@code LauncherUseCaseMode}; an empty value means nothing was captured yet.
         */
        public static final String KEY_APP_LAUNCHER_USE_CASE_SNAPSHOT =
            "app_launcher_use_case_snapshot";

        public static final String DEFAULT_APP_LAUNCHER_USE_CASE_SNAPSHOT = "";

        /**
         * Defines the key for showing active-notification dots on apps bar icons.
         */
        public static final String KEY_APP_LAUNCHER_NOTIFICATION_DOTS = "app_launcher_notification_dots";

        public static final boolean DEFAULT_APP_LAUNCHER_NOTIFICATION_DOTS = false;

        /**
         * Defines the key for persisting notification history to {@code ~/.launcherctl}.
         *
         * <p>Separate from notification access itself, and off by default. Notification access is
         * granted for dots, the status bar and the top pane, all of which only need notifications
         * in memory. Writing their contents -- titles, texts, expanded texts, so SMS bodies, email
         * previews and 2FA codes -- into the Termux home puts them inside the shell's trust domain,
         * where every package and script running under the app UID can read them. That is a second,
         * larger decision, so it is asked separately rather than inherited from the grant.
         */
        public static final String KEY_APP_LAUNCHER_NOTIFICATION_HISTORY =
            "app_launcher_notification_history";

        public static final boolean DEFAULT_APP_LAUNCHER_NOTIFICATION_HISTORY = false;

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

        public static final String APP_LAUNCHER_DOCK_STYLE_ROUNDED = "rounded";

        /** Pre-unification persisted value. Read for migration, but never write it again. */
        public static final String APP_LAUNCHER_DOCK_STYLE_LEGACY_VALARIE_CAPSULE = "valarie_capsule";

        public static final String DEFAULT_APP_LAUNCHER_DOCK_STYLE = APP_LAUNCHER_DOCK_STYLE_DEFAULT;

        /** Custom capsule corner radius in dp, or -1 to follow the selected dock style. */
        public static final String KEY_APP_LAUNCHER_DOCK_CORNER_RADIUS =
            "app_launcher_dock_corner_radius";
        public static final int DEFAULT_APP_LAUNCHER_DOCK_CORNER_RADIUS = -1;
        public static final int MAX_APP_LAUNCHER_DOCK_CORNER_RADIUS = 40;

        /**
         * Defines the key for enabling the full-screen app launcher drawer.
         */
        public static final String KEY_APP_LAUNCHER_DRAWER_ENABLED = "app_launcher_drawer_enabled";

        public static final boolean DEFAULT_APP_LAUNCHER_DRAWER_ENABLED = true;

        public static final String KEY_APP_LAUNCHER_DRAWER_VIEW_TYPE =
            "app_launcher_drawer_view_type";
        public static final String APP_LAUNCHER_DRAWER_VIEW_TYPE_VERTICAL = "vertical";
        public static final String APP_LAUNCHER_DRAWER_VIEW_TYPE_HORIZONTAL = "horizontal";
        public static final String APP_LAUNCHER_DRAWER_VIEW_TYPE_CATEGORIES = "categories";
        public static final String DEFAULT_APP_LAUNCHER_DRAWER_VIEW_TYPE =
            APP_LAUNCHER_DRAWER_VIEW_TYPE_VERTICAL;

        /**
         * Defines the key for opening the keyboard as soon as the app drawer opens, so the drawer
         * arrives ready to search instead of waiting for the pill to be tapped.
         */
        public static final String KEY_APP_LAUNCHER_DRAWER_SEARCH_ON_OPEN =
            "app_launcher_drawer_search_on_open";

        public static final boolean DEFAULT_APP_LAUNCHER_DRAWER_SEARCH_ON_OPEN = false;

        /**
         * Defines the key for searching the app drawer with the Android keyboard through a real text
         * field, so its suggestions and swipe typing apply, instead of the built-in keyboard's key
         * stream.
         */
        public static final String KEY_APP_LAUNCHER_DRAWER_SEARCH_ANDROID_KEYBOARD =
            "app_launcher_drawer_search_android_keyboard";

        public static final boolean DEFAULT_APP_LAUNCHER_DRAWER_SEARCH_ANDROID_KEYBOARD = false;

        /** Custom drawer corner radius in dp, or -1 to follow the shared rounded-surface token. */
        public static final String KEY_APP_LAUNCHER_DRAWER_CORNER_RADIUS =
            "app_launcher_drawer_corner_radius";
        public static final int DEFAULT_APP_LAUNCHER_DRAWER_CORNER_RADIUS = -1;
        public static final int MAX_APP_LAUNCHER_DRAWER_CORNER_RADIUS = 40;

        /**
         * Radius a Floating surface takes when its own radius is left on -1 (follow the style):
         * the dock capsule, the drawer and the terminal border all read this. The one resolver for
         * the sentinel is {@code TermuxAppSharedPreferences.resolveAutoCornerRadiusDp}.
         */
        public static final int DEFAULT_ROUNDED_SURFACE_CORNER_RADIUS_DP = 20;

        /**
         * The floating status surface shipped with its own adaptive answer to the -1 sentinel -
         * between these bounds, capped at half the pane height - instead of the shared 20dp above.
         * Kept as shipped: an upgrade must not reshape the pane. At the expanded pane height the
         * cap never bites, so the MAX is the number the surface actually shows and the number the
         * editor displays for it.
         */
        public static final int STATUS_AUTO_CORNER_RADIUS_MIN_DP = 16;
        public static final int STATUS_AUTO_CORNER_RADIUS_MAX_DP = 26;

        /*
         * There is no per-session "last section" key any more: the editor became one page when its
         * five tabs were dissolved, so there is no section to remember. A stored
         * "surface_tuning_last_section" from an older build is ignored.
         */

        /** Independent status-surface glass controls used by the live surface editor. */
        public static final String KEY_STATUS_BAR_BLUR_RADIUS = "status_bar_blur_radius";
        public static final int DEFAULT_STATUS_BAR_BLUR_RADIUS = 8;
        public static final String KEY_STATUS_BAR_OPACITY = "status_bar_opacity";
        public static final int DEFAULT_STATUS_BAR_OPACITY = 34;
        public static final String KEY_STATUS_BAR_GRAIN = "status_bar_grain";
        public static final int DEFAULT_STATUS_BAR_GRAIN = 18;
        public static final String KEY_STATUS_BAR_CORNER_RADIUS = "status_bar_corner_radius";
        public static final int DEFAULT_STATUS_BAR_CORNER_RADIUS = -1;
        public static final int MAX_STATUS_BAR_CORNER_RADIUS = 40;

        /**
         * Corner radius (dp) of the two chips on the status row — the sessions indicator and the
         * window pills — which wear one shape between them.
         *
         * <p>Outside the Base cascade, like the terminal frame's radius: this is the shape of two
         * pieces of content inside the status surface, not the shape of a surface. {@code -1} is
         * the shipped "follow the bar" state, which is square while Docked and the capsule's own
         * radius while Floating; any value the user dials in holds in both styles.
         */
        public static final String KEY_STATUS_INDICATOR_CORNER_RADIUS =
            "status_indicator_corner_radius";
        public static final int DEFAULT_STATUS_INDICATOR_CORNER_RADIUS = -1;
        public static final int MAX_STATUS_INDICATOR_CORNER_RADIUS = 16;

        /**
         * Corner radius (dp) of the Docked terminal's bordered frame. Not part of the Base
         * cascade: Floating derives the frame radius from the capsule, so this only exists where
         * the user can actually see it act — Docked with the border on. Defaults to the flush
         * square frame Docked always drew.
         */
        public static final String KEY_TERMINAL_CORNER_RADIUS = "terminal_corner_radius";
        public static final int DEFAULT_TERMINAL_CORNER_RADIUS = 24;
        public static final int MAX_TERMINAL_CORNER_RADIUS = 40;

        /**
         * Terminal glass pane, available while the terminal border is on: wallpaper blur radius
         * (dp, 0 disables) and film grain (percent) localised to the bordered terminal area.
         * Both default off so the border alone stays the plain outline it always was.
         */
        public static final String KEY_TERMINAL_GLASS_BLUR_RADIUS = "terminal_glass_blur_radius";
        public static final int DEFAULT_TERMINAL_GLASS_BLUR_RADIUS = 8;
        public static final String KEY_TERMINAL_GLASS_GRAIN = "terminal_glass_grain";
        public static final int DEFAULT_TERMINAL_GLASS_GRAIN = 18;

        /**
         * Legacy key for the wallpaper backdrop, counted as the wallpaper's own visibility: 100
         * showed the wallpaper as-is and lower values dimmed it. Read only to migrate; every
         * reader now uses {@link #KEY_WALLPAPER_BACKDROP_DIM}, which counts the way the slider
         * reads — right is more backdrop, not less.
         */
        public static final String KEY_WALLPAPER_BACKDROP_OPACITY = "wallpaper_backdrop_opacity";

        /**
         * Opacity of the black backdrop drawn over the wallpaper, behind every surface (percent):
         * 0 shows the wallpaper as-is, 100 hides it completely, before any surface tint is painted
         * over it. Needed once the terminal tint could be localised to its own glass pane — until
         * then the terminal opacity doubled as the whole-window dim.
         */
        public static final String KEY_WALLPAPER_BACKDROP_DIM = "wallpaper_backdrop_dim";
        public static final int DEFAULT_WALLPAPER_BACKDROP_DIM = 0;

        /**
         * Gap between tiled terminal panes in dp — the surface editor's Inner padding. The old
         * fixed 1dp hairline is the default; with the glass panes on, the gap is what makes each
         * pane read as its own floating terminal rather than a cell of one sheet.
         */
        public static final String KEY_TERMINAL_PANE_GAP = "terminal_pane_gap";
        public static final int DEFAULT_TERMINAL_PANE_GAP = 4;
        public static final int MAX_TERMINAL_PANE_GAP = 24;

        /**
         * Symmetric left/right inset in dp between a surface and the physical screen edges. The
         * default matches the floating capsule's redline outer margin; the edge-to-edge default
         * shape only honours whatever is configured beyond that baseline.
         */
        public static final int DEFAULT_SURFACE_HORIZONTAL_INSET = 12;
        /** The keyboard sits slightly further in than the other surfaces; tuned on Pong. */
        public static final int DEFAULT_IN_APP_KEYBOARD_HORIZONTAL_INSET = 12;
        public static final int MAX_SURFACE_HORIZONTAL_INSET = 48;
        public static final String KEY_DOCK_HORIZONTAL_INSET = "dock_horizontal_inset";
        public static final String KEY_IN_APP_KEYBOARD_HORIZONTAL_INSET =
            "in_app_keyboard_horizontal_inset";
        public static final String KEY_STATUS_BAR_HORIZONTAL_INSET = "status_bar_horizontal_inset";

        /**
         * Optional trailing system widgets on the status row.
         *
         * <p>CPU and RAM default off. Both read best on a device with the privileged backend
         * connected — without it the CPU figure is a best-effort /proc estimate that several ROMs
         * refuse outright — so they are opt-in, and turning either on walks the user past a Shizuku
         * check first. Existing installs keep whatever they already chose; only a fresh install
         * starts with them off.
         */
        public static final String KEY_STATUS_WIDGET_CPU = "status_widget_cpu";
        public static final boolean DEFAULT_STATUS_WIDGET_CPU = false;

        public static final String KEY_STATUS_WIDGET_RAM = "status_widget_ram";
        public static final boolean DEFAULT_STATUS_WIDGET_RAM = false;

        public static final String KEY_STATUS_WIDGET_WEATHER = "status_widget_weather";
        public static final boolean DEFAULT_STATUS_WIDGET_WEATHER = true;

        /** Show weather temperatures in Fahrenheit instead of the default Celsius. */
        public static final String KEY_STATUS_WIDGET_WEATHER_FAHRENHEIT = "status_widget_weather_fahrenheit";
        public static final boolean DEFAULT_STATUS_WIDGET_WEATHER_FAHRENHEIT = false;

        /** Animate the terminal cursor between its old and new cell instead of jumping. */
        public static final String KEY_TERMINAL_CURSOR_TRAIL = "terminal_cursor_trail";
        public static final boolean DEFAULT_TERMINAL_CURSOR_TRAIL = true;

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

        /** Defines whether A-Z and app-row focus changes emit subtle haptic ticks. */
        public static final String KEY_APP_LAUNCHER_ROW_HAPTICS = "app_launcher_row_haptics";

        public static final boolean DEFAULT_APP_LAUNCHER_ROW_HAPTICS = true;

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
         * Defines the key for whether the in-app keyboard will be enabled.
         *
         * <p>Defaults to enabled: with the system IME as the fresh-install default, first launch
         * raced the IME's inset animation against the launcher's own keyboard chrome — an extra
         * band of padding and a flickering terminal until the two settled. The embedded keyboard
         * suppresses the system IME outright, so a fresh install never enters that race.
         */
        public static final String KEY_IN_APP_KEYBOARD_ENABLED = "in_app_keyboard_enabled";

        public static final boolean DEFAULT_IN_APP_KEYBOARD_ENABLED = true;

        /**
         * Defines the key for the in-app keyboard color theme.
         */
        public static final String KEY_IN_APP_KEYBOARD_THEME = "in_app_keyboard_theme";

        public static final String DEFAULT_IN_APP_KEYBOARD_THEME = "system";

        /** JSON containing user-edited swatches and per-key keyboard color assignments. */
        public static final String KEY_IN_APP_KEYBOARD_COLOR_SCHEME =
            "in_app_keyboard_color_scheme";

        public static final String DEFAULT_IN_APP_KEYBOARD_COLOR_SCHEME = "";

        /** Defines the key for whether keypresses trigger haptic feedback. */
        public static final String KEY_IN_APP_KEYBOARD_HAPTICS_ENABLED =
            "in_app_keyboard_haptics_enabled";

        public static final boolean DEFAULT_IN_APP_KEYBOARD_HAPTICS_ENABLED = true;

        /** Defines the key for whether keypresses play the system keypress sound. */
        public static final String KEY_IN_APP_KEYBOARD_KEY_SOUND_ENABLED =
            "in_app_keyboard_key_sound_enabled";

        public static final boolean DEFAULT_IN_APP_KEYBOARD_KEY_SOUND_ENABLED = false;

        /**
         * Defines the key for whether the built-in keyboard learns where the user's taps land
         * and nudges presses near a key boundary onto the key they usually mean. Off by
         * default: it is the home screen's keyboard, and the model is learned from the user's
         * own typing, so it has to be asked for.
         */
        public static final String KEY_IN_APP_KEYBOARD_TAP_CORRECTION =
            "in_app_keyboard_tap_correction";

        public static final boolean DEFAULT_IN_APP_KEYBOARD_TAP_CORRECTION = false;

        /**
         * Defines the key for the absolute path of a user-imported label font file,
         * or an empty string for the system default typeface.
         */
        public static final String KEY_IN_APP_KEYBOARD_FONT_PATH = "in_app_keyboard_font_path";

        public static final String DEFAULT_IN_APP_KEYBOARD_FONT_PATH = "";

        /**
         * Defines the key for the comma-joined list of extra key names merged into the in-app
         * keyboard layout. An empty string means "none enabled"; the
         * {@link #DEFAULT_IN_APP_KEYBOARD_EXTRA_KEYS} sentinel means the user never chose a
         * selection and the built-in defaults apply.
         */
        public static final String KEY_IN_APP_KEYBOARD_EXTRA_KEYS = "in_app_keyboard_extra_keys";

        public static final String DEFAULT_IN_APP_KEYBOARD_EXTRA_KEYS = "__default__";

        /**
         * Defines the key for the comma-joined ids of the text layouts the in-app keyboard
         * hot-swaps between, in cycle order. {@code main} is the launcher's own layout — the
         * user's {@code ~/.termux/keyboard/layout.xml} when it exists, the bundled QWERTY
         * otherwise — and every other id names a bundled layout resource.
         */
        /**
         * Defines the key for the id of the search engine the launcher hands web queries to.
         * {@code custom} reads its URL template from {@link #KEY_WEB_SEARCH_CUSTOM_URL}.
         */
        public static final String KEY_WEB_SEARCH_ENGINE = "web_search_engine";

        public static final String DEFAULT_WEB_SEARCH_ENGINE = "duckduckgo";

        /** A user-supplied search URL with one {@code %s} where the encoded query goes. */
        public static final String KEY_WEB_SEARCH_CUSTOM_URL = "web_search_custom_url";

        public static final String DEFAULT_WEB_SEARCH_CUSTOM_URL = "";

        public static final String KEY_IN_APP_KEYBOARD_LAYOUTS = "in_app_keyboard_layouts";

        public static final String DEFAULT_IN_APP_KEYBOARD_LAYOUTS = "main";

        /**
         * Defines the key for the layout the hot-swap ring is currently on, so a swap survives
         * a process restart the way the rest of the keyboard's state does.
         */
        public static final String KEY_IN_APP_KEYBOARD_ACTIVE_LAYOUT =
            "in_app_keyboard_active_layout";

        public static final String DEFAULT_IN_APP_KEYBOARD_ACTIVE_LAYOUT = "main";

        /** Defines the persisted height scale for the in-app keyboard. */
        public static final String KEY_IN_APP_KEYBOARD_HEIGHT_SCALE = "in_app_keyboard_height_scale";

        /**
         * Defines the persisted height scale for the in-app keyboard while the device is in
         * landscape, so adjusting one orientation never changes the other.
         */
        public static final String KEY_IN_APP_KEYBOARD_HEIGHT_SCALE_LANDSCAPE = "in_app_keyboard_height_scale_landscape";

        // Default-dock baseline tuned on Pong (1080x2412). Height is drag-based, so retain the
        // exact confirmed value; spacing and radius below are discrete slider steps.
        public static final float DEFAULT_IN_APP_KEYBOARD_HEIGHT_SCALE = 1.0423018f;

        /** Default keyboard height for the Rounded dock, tuned on Pong. */
        public static final float DEFAULT_ROUNDED_IN_APP_KEYBOARD_HEIGHT_SCALE = 1.0830541f;

        public static final float MIN_IN_APP_KEYBOARD_HEIGHT_SCALE = 0.5f;

        public static final float MAX_IN_APP_KEYBOARD_HEIGHT_SCALE = 1.6f;

        /** Defines the persisted multiplier for both in-app keyboard key-margin ratios. */
        public static final String KEY_IN_APP_KEYBOARD_KEY_MARGIN_SCALE =
            "in_app_keyboard_key_margin_scale";

        public static final float DEFAULT_IN_APP_KEYBOARD_KEY_MARGIN_SCALE = 2.96f;

        /** Default key spacing for the Rounded dock, tuned on Pong. */
        public static final float DEFAULT_ROUNDED_IN_APP_KEYBOARD_KEY_MARGIN_SCALE = 2.96f;

        public static final float MIN_IN_APP_KEYBOARD_KEY_MARGIN_SCALE = 0.0f;

        // The default margin ratios are ~2-3px on a 1080p-wide keyboard, so a small multiplier
        // ceiling is visually imperceptible; 8x tops out around a clearly visible 18-24px gap.
        public static final float MAX_IN_APP_KEYBOARD_KEY_MARGIN_SCALE = 8.0f;

        /** Defines the persisted key corner radius in dp, or -1 for the palette default. */
        public static final String KEY_IN_APP_KEYBOARD_KEY_CORNER_RADIUS_DP =
            "in_app_keyboard_key_corner_radius_dp";

        public static final float DEFAULT_IN_APP_KEYBOARD_KEY_CORNER_RADIUS_DP = 10.8f;

        /** Default key radius for the Rounded dock, tuned on Pong. */
        public static final float DEFAULT_ROUNDED_IN_APP_KEYBOARD_KEY_CORNER_RADIUS_DP = 12.5f;

        public static final float MIN_IN_APP_KEYBOARD_KEY_CORNER_RADIUS_DP = 0.0f;

        public static final float MAX_IN_APP_KEYBOARD_KEY_CORNER_RADIUS_DP = 24.0f;

        /**
         * Defines the extra air in dp under the last key row, inside the keyboard's own surface.
         * The slab keeps its bounds, so the material still runs to the screen edge; only the keys
         * move up — which is what clears a gesture bar without leaving a bare strip under the
         * glass. Lives in Settings rather than the surface editor: it is a fit against one
         * device's chin, not part of a look.
         */
        public static final String KEY_IN_APP_KEYBOARD_BOTTOM_PADDING =
            "in_app_keyboard_bottom_padding";

        public static final int DEFAULT_IN_APP_KEYBOARD_BOTTOM_PADDING = 0;

        public static final int MIN_IN_APP_KEYBOARD_BOTTOM_PADDING = 0;

        public static final int MAX_IN_APP_KEYBOARD_BOTTOM_PADDING = 48;

        /**
         * Defines the absolute key cap opacity in percent (0 = invisible caps, 100 = fully
         * opaque), or -1 to keep the keyboard theme's own key translucency. Unlike the theme
         * value, this is independent of the surface/glass opacity stack.
         */
        public static final String KEY_IN_APP_KEYBOARD_KEY_OPACITY =
            "in_app_keyboard_key_opacity";

        public static final int DEFAULT_IN_APP_KEYBOARD_KEY_OPACITY = -1;

        public static final int MIN_IN_APP_KEYBOARD_KEY_OPACITY = 0;

        public static final int MAX_IN_APP_KEYBOARD_KEY_OPACITY = 100;

        /**
         * Defines the opacity in percent of the keyboard's own background surface — the glass
         * tint, or the color-scheme background override when one is set. Independent of the key
         * caps ({@link #KEY_IN_APP_KEYBOARD_KEY_OPACITY}); 100 keeps the surface as the theme
         * renders it.
         */
        public static final String KEY_IN_APP_KEYBOARD_BACKGROUND_OPACITY =
            "in_app_keyboard_background_opacity";

        public static final int DEFAULT_IN_APP_KEYBOARD_BACKGROUND_OPACITY = 34;

        public static final int MIN_IN_APP_KEYBOARD_BACKGROUND_OPACITY = 0;

        public static final int MAX_IN_APP_KEYBOARD_BACKGROUND_OPACITY = 100;

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
         * Defines the key for whether the launcher stays visible in the recents screen while it is
         * not the default home app. Distinct from {@link #KEY_ACTIVITY_FINISH_REMOVE_TASK}: that
         * one governs task removal on finish, this one governs recents visibility while running.
         */
        public static final String KEY_SHOW_IN_RECENTS_WHEN_NOT_DEFAULT = "show_in_recents_when_not_default";

        public static final boolean DEFAULT_VALUE_KEY_SHOW_IN_RECENTS_WHEN_NOT_DEFAULT = true;

        /**
         * Defines the key for whether to always keep screen on.
         */
        public static final String KEY_KEEP_SCREEN_ON = "screen_always_on";

        public static final boolean DEFAULT_VALUE_KEEP_SCREEN_ON = false;

        /**
         * Defines the key for "compatibility mode": when enabled, all custom window/pane
         * splitting behaviour and tmux-style keybinds are disabled and native Termux
         * single-pane behaviour is used.
         */
        public static final String KEY_COMPATIBILITY_MODE = "compatibility_mode";

        public static final boolean DEFAULT_VALUE_COMPATIBILITY_MODE = false;

        /**
         * Trades the launcher's idle animation for battery: the clock swaps its digits instead of
         * folding them and the busy-window rim stops breathing, so nothing repaints the window while
         * you are only looking at it. Sampling also backs off. Off by default.
         */
        public static final String KEY_LAZY_MODE = "lazy_mode";
        public static final boolean DEFAULT_VALUE_LAZY_MODE = false;

        /**
         * Whether holding a keybind prefix (Ctrl+Alt) automatically shows the key-hint strip.
         * When off, nothing appears on its own: the {@code ?} cap lights up under the prefix and
         * pressing it opens the full table on demand.
         */
        public static final String KEY_SHOW_KEY_HINTS = "show_key_hints";
        public static final boolean DEFAULT_SHOW_KEY_HINTS = true;

        /** New windows start under the dwindle layout (Hyprland-style automatic tiling). */
        public static final String KEY_DWINDLE_DEFAULT_LAYOUT = "pane_dwindle_default";
        public static final boolean DEFAULT_DWINDLE_DEFAULT_LAYOUT = false;

        /** focus.nvim-style: the focused pane takes most of the room, the rest slide aside. */
        public static final String KEY_FOCUSED_PANE_GROWS = "pane_focus_grows";
        public static final boolean DEFAULT_FOCUSED_PANE_GROWS = false;

        /** Whether the local API (`launcherctl pane`, /v1/panes) may open and drive panes. */
        public static final String KEY_AGENT_PANES_ENABLED = "pane_agent_api";
        public static final boolean DEFAULT_AGENT_PANES_ENABLED = true;

        /** Clock renderer shown in the modular widget slot above the terminal window row. */
        public static final String KEY_TOP_PANE_CLOCK_STYLE = "top_pane_clock_style";

        public static final String TOP_PANE_CLOCK_STYLE_FLIP = "flip";
        public static final String TOP_PANE_CLOCK_STYLE_LCD = "lcd";
        public static final String TOP_PANE_CLOCK_STYLE_MINIMAL = "minimal";
        public static final String TOP_PANE_CLOCK_STYLE_LED = "led";
        public static final String TOP_PANE_CLOCK_STYLE_TAPE = "tape";
        public static final String TOP_PANE_CLOCK_STYLE_SLAB = "slab";

        public static final String DEFAULT_TOP_PANE_CLOCK_STYLE = TOP_PANE_CLOCK_STYLE_SLAB;

        /** Horizontal alignment of the FULL-form top-pane clock inside the pane. */
        public static final String KEY_TOP_PANE_CLOCK_ALIGNMENT = "top_pane_clock_alignment";

        public static final String TOP_PANE_CLOCK_ALIGNMENT_LEFT = "left";
        public static final String TOP_PANE_CLOCK_ALIGNMENT_CENTER = "center";
        public static final String TOP_PANE_CLOCK_ALIGNMENT_RIGHT = "right";

        public static final String DEFAULT_TOP_PANE_CLOCK_ALIGNMENT = TOP_PANE_CLOCK_ALIGNMENT_LEFT;

        /** Use a 12-hour top-pane clock and append the AM/PM period. */
        public static final String KEY_TOP_PANE_CLOCK_AM_PM = "top_pane_clock_am_pm";
        public static final boolean DEFAULT_TOP_PANE_CLOCK_AM_PM = true;

        /** Persist the user-controlled compact single-row status-bar state. */
        public static final String KEY_TOP_PANE_CLOCK_COLLAPSED = "top_pane_clock_collapsed";
        public static final boolean DEFAULT_TOP_PANE_CLOCK_COLLAPSED = true;

        /**
         * Rules that pin a notification into the top-pane widget slot, stored as a JSON array of
         * {@code {id, package, match, clear}}. Empty leaves the feature idle.
         */
        public static final String KEY_ESSENTIAL_NOTIFICATION_RULES = "essential_notification_rules";
        public static final String DEFAULT_ESSENTIAL_NOTIFICATION_RULES = "[]";

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

        public static final int DEFAULT_VALUE_TERMINAL_BACKGROUND_OPACITY = 34;

        /**
         * Defines the key for sessions menu opacity (percentage), where 100 is fully opaque.
         */
        public static final String KEY_SESSIONS_OPACITY = "sessions_opacity";

        public static final int DEFAULT_VALUE_SESSIONS_OPACITY = 50;

        /**
         * Defines the key for extrakeys/app bar blur radius (dp). 0 disables blur.
         */
        public static final String KEY_EXTRAKEYS_BLUR_RADIUS = "extrakeys_blur_radius";

        public static final int DEFAULT_VALUE_EXTRAKEYS_BLUR_RADIUS = 8;

        /**
         * Defines the key for extrakeys/app bar opacity (percentage), where 100 is fully opaque.
         */
        public static final String KEY_APP_BAR_OPACITY = "app_bar_opacity";

        public static final int DEFAULT_VALUE_APP_BAR_OPACITY = 43;

        /**
         * Defines the key for the dock-glass grain/noise amount (percentage, 0 disables). A subtle
         * film grain over the frosted glass that reads as real glass texture rather than flat blur.
         */
        public static final String KEY_DOCK_GLASS_GRAIN = "dock_glass_grain";

        public static final int DEFAULT_VALUE_DOCK_GLASS_GRAIN = 18;

        /**
         * Defines whether the terminal's bottom cell remainder is absorbed by the dock glass.
         */
        public static final String KEY_TERMINAL_FLUSH_DOCK = "terminal_flush_dock";

        public static final boolean DEFAULT_VALUE_TERMINAL_FLUSH_DOCK = false;

        /**
         * Defines whether a thin outline border is drawn around the terminal surface.
         */
        public static final String KEY_TERMINAL_BORDER_ENABLED = "terminal_border_enabled";
        /*
         * Surface inheritance. Five properties are shared across the surfaces; each surface either
         * follows the Base value or holds an override of its own. The per-surface value keys already
         * exist above and keep their meaning - they are simply only consulted once a surface has
         * detached. Replaces the old all-or-nothing normalize flag, which could not
         * express "the same everywhere except this one thing".
         */
        public static final String KEY_SURFACE_BASE_BLUR = "surface_base_blur";
        public static final String KEY_SURFACE_BASE_OPACITY = "surface_base_opacity";
        public static final String KEY_SURFACE_BASE_GRAIN = "surface_base_grain";
        public static final String KEY_SURFACE_BASE_CORNER_RADIUS = "surface_base_corner_radius";
        public static final String KEY_SURFACE_BASE_SIDE_GAP = "surface_base_side_gap";

        /**
         * The shared layer's shipped values. Deliberately its own set rather than an alias of the
         * dock's: the shipped look gives the dock a slightly denser opacity than the surfaces that
         * follow Base, and folding the two together would drag every other surface along with it.
         */
        public static final int DEFAULT_SURFACE_BASE_BLUR = 8;
        public static final int DEFAULT_SURFACE_BASE_OPACITY = 34;
        public static final int DEFAULT_SURFACE_BASE_GRAIN = 18;
        public static final int DEFAULT_SURFACE_BASE_CORNER_RADIUS = 24;
        public static final int DEFAULT_SURFACE_BASE_SIDE_GAP = 12;

        /**
         * The Base glass triple said as one decision: a material family plus how much of it.
         * The editor's macro writes blur/opacity/grain through the Base setters like anything
         * else; these two keys only remember which point on which family's curve the triple came
         * from, so the control can show that point again — or "Custom" once the triple no longer
         * matches any point, which is how hand-tuned installs are left untouched.
         */
        public static final String KEY_SURFACE_MATERIAL = "surface_material";
        public static final String KEY_SURFACE_MATERIAL_INTENSITY = "surface_material_intensity";
        public static final String SURFACE_MATERIAL_SOLID = "solid";
        public static final String SURFACE_MATERIAL_GLASS = "glass";
        public static final String SURFACE_MATERIAL_FROST = "frost";
        /** Glass at 50 reproduces the shipped Base triple exactly (8 / 34 / 18). */
        public static final String DEFAULT_SURFACE_MATERIAL = SURFACE_MATERIAL_GLASS;
        public static final int DEFAULT_SURFACE_MATERIAL_INTENSITY = 50;

        /**
         * The look the user pinned as the editor's Custom preset, as the preset format's JSON.
         * Written only by the editor's save glyph — never by Done, which commits the live
         * preferences and would otherwise replace the pin every time someone left the editor.
         */
        public static final String KEY_SURFACE_CUSTOM_PRESET = "surface_custom_preset";

        /** Prefix for the per-(surface, property) detach flags: {@code surface_inherit_dock_blur}. */
        public static final String KEY_SURFACE_INHERIT_PREFIX = "surface_inherit_";
        /** A surface follows Base until the user moves that one control. */
        public static final boolean DEFAULT_VALUE_SURFACE_INHERITS_BASE = true;

        /** Set once the one-time inheritance migration has folded the old per-surface values in. */
        public static final String KEY_SURFACE_INHERITANCE_MIGRATED = "surface_inheritance_migrated";

        /**
         * Set once the install has been told apart as new or existing, and given the shipped
         * Docked look or had the pre-shipped one pinned accordingly. Separate from the
         * inheritance marker above: an install can already have folded and still need this.
         */
        public static final String KEY_SHIPPED_SURFACE_DEFAULTS_ADOPTED =
            "shipped_surface_defaults_adopted";

        /**
         * Set once the keyboard-opacity rows detached at the old sentinel default have been
         * relinked to Base. Before the tuned Docked look, {@code 100} on
         * {@link #KEY_IN_APP_KEYBOARD_BACKGROUND_OPACITY} meant "never touched — render the shared
         * dock material", and the inheritance fold turned that sentinel into a detached override.
         * When the default then changed, those installs silently lost the unified
         * dock/keyboard/nav glass sheet.
         */
        public static final String KEY_KEYBOARD_OPACITY_SENTINEL_HEALED =
            "keyboard_opacity_sentinel_healed";

        /** The pre-tuned-look keyboard-opacity default, which doubled as the "untouched" sentinel. */
        public static final int LEGACY_IN_APP_KEYBOARD_BACKGROUND_OPACITY_SENTINEL = 100;

        /**
         * The old all-or-nothing "match all surfaces" switch. Read once by
         * {@code migrateSurfaceInheritance} to decide whether an upgrading install starts fully
         * linked, but never written again.
         */
        public static final String KEY_SURFACE_TUNING_NORMALIZED = "surface_tuning_normalized";
        public static final boolean DEFAULT_VALUE_SURFACE_TUNING_NORMALIZED = false;

        public static final boolean DEFAULT_VALUE_TERMINAL_BORDER_ENABLED = true;

        /**
         * Stores the user's preferred terminal opacity while wallpaper mode is enabled so it can
         * be restored after temporarily disabling wallpaper.
         */
        public static final String KEY_WALLPAPER_ENABLED_TERMINAL_BACKGROUND_OPACITY =
            "wallpaper_enabled_terminal_background_opacity";

        public static final int DEFAULT_VALUE_WALLPAPER_ENABLED_TERMINAL_BACKGROUND_OPACITY = 34;

        /**
         * Stores the user's preferred dock opacity while wallpaper mode is enabled so it can be
         * be restored after temporarily disabling wallpaper.
         */
        public static final String KEY_WALLPAPER_ENABLED_APP_BAR_OPACITY =
            "wallpaper_enabled_app_bar_opacity";

        public static final int DEFAULT_VALUE_WALLPAPER_ENABLED_APP_BAR_OPACITY = 43;

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
         * Defines the key for whether terminal colors should follow Material dynamic colors.
         */
        public static final String KEY_TERMINAL_DYNAMIC_COLORS_ENABLED = "terminal_dynamic_colors_enabled";

        public static final boolean DEFAULT_VALUE_TERMINAL_DYNAMIC_COLORS_ENABLED = true;

        /*
         * There is no key for the launcher chrome's colour source any more. It was a second name
         * for a decision the wallpaper-colours switch already makes, and the two could disagree —
         * chrome on the scheme with the terminal on the wallpaper, or the reverse. The chrome now
         * follows {@link #KEY_TERMINAL_DYNAMIC_COLORS_ENABLED}: off means both take the scheme in
         * ~/.termux/colors.properties. A stored "ui_color_source" from an older build is ignored.
         */

        /** Contrast profile for wallpaper-derived terminal colors. */
        public static final String KEY_TERMINAL_CONTRAST_LEVEL = "terminal_contrast_level";

        public static final String DEFAULT_VALUE_TERMINAL_CONTRAST_LEVEL = "default";

        /**
         * Defines the key for whether the system wallpaper should be used.
         */
        public static final String KEY_USE_SYSTEM_WALLPAPER = "use_system_wallpaper";

        public static final boolean DEFAULT_VALUE_USE_SYSTEM_WALLPAPER = true;

        /**
         * Defines the key for whether the wallpaper-read storage permission prompt has been shown.
         * Asked at most once, and only after a wallpaper read actually failed.
         */
        public static final String KEY_WALLPAPER_READ_PERMISSION_PROMPTED =
            "wallpaper_read_permission_prompted";

        public static final boolean DEFAULT_VALUE_WALLPAPER_READ_PERMISSION_PROMPTED = false;
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

}
