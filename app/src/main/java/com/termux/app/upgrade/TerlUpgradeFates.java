package com.termux.app.upgrade;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.leader.LeaderChordSettingAdapter;
import com.termux.app.look.StoredTerlLookStore;
import com.termux.app.settings.LayoutSettingsAdapter;
import com.termux.app.settings.LowMemoryPresetAdapter;
import com.termux.app.settings.UseAsAdapter;
import com.termux.app.surfaces.AppearanceReset;
import com.termux.shared.termux.settings.preferences.TermuxPreferenceConstants.TERMUX_APP;
import com.termux.shared.termux.settings.registry.FontSizeSettingsAdapter;
import com.termux.shared.termux.settings.registry.SettingDeclaration;
import com.termux.shared.termux.settings.registry.SettingDeclarations;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * What the upgrade from v1 to Terl does with each stored setting ({@link TerlUpgradePlan}): every
 * key and every registry id v1 knows has a fate here, and {@code TerlUpgradeFatesTest} fails on
 * one that has none, so a ticket that adds a key decides its fate.
 *
 * <ul>
 *   <li>{@link Fate#CARRY}: a behaviour setting that still has a row in Terl (keyboard, terminal,
 *   Linux apps, the Assistant, notification history, permissions, Home's icons and pinned apps),
 *   the wallpaper, and every one-time marker. Its v1 value stays.</li>
 *   <li>{@link Fate#DROP}: a setting whose row retires in Terl. Removed.</li>
 *   <li>{@link Fate#FRESH}: the look and the layout (what the Appearance editor owns, the dock,
 *   the status bar, panes, sessions) and Terl's own look ({@code terl.look.*}). Removed, so
 *   Terl's defaults show.</li>
 * </ul>
 *
 * <p>A key named below wins over its family: the status bar's chips and the dock style are in
 * the Appearance editor's set, but their rows retire, so they are {@link Fate#DROP}.
 */
public final class TerlUpgradeFates {

    private TerlUpgradeFates() {}

    public enum Fate { CARRY, DROP, FRESH }

    // SETTINGS first: the keys' table reads it.
    private static final Map<String, Fate> SETTINGS = settings();
    private static final Map<String, Fate> KEYS = keys();

    /**
     * The fate of a stored preference key: the key's own entry, else its family (Terl's look,
     * then the Appearance editor's set), else {@code null} for a key v1 does not name, which the
     * upgrade leaves alone.
     */
    @Nullable
    public static Fate ofKey(@NonNull String key) {
        Fate named = KEYS.get(key);
        if (named != null) return named;
        if (key.startsWith(StoredTerlLookStore.PREFIX)) return Fate.FRESH;
        if (AppearanceReset.isAppearanceKey(key)) return Fate.FRESH;
        if (key.startsWith(com.termux.app.x11.X11AppSettingsStore.PREFS_KEY_OPACITY_PREFIX)) return Fate.FRESH;
        return null;
    }

    /**
     * The fate of a registry id ({@code launcherctl config}): its own entry, else its adapter's
     * family (the look grid's {@code look.*}, the layouts' {@code layout.*}), else {@code null}.
     */
    @Nullable
    public static Fate ofSetting(@NonNull String id) {
        Fate named = SETTINGS.get(id);
        if (named != null) return named;
        if (id.equals("look") || id.startsWith("look.") || id.startsWith(LayoutSettingsAdapter.PREFIX)) return Fate.FRESH;
        return null;
    }

    /** Whether the upgrade removes {@code key}. */
    public static boolean removes(@NonNull String key) {
        Fate fate = ofKey(key);
        return fate == Fate.DROP || fate == Fate.FRESH;
    }

    @NonNull
    private static Map<String, Fate> settings() {
        Map<String, Fate> m = new HashMap<>();
        // termux.properties lives in $HOME, which the upgrade never touches.
        m.put(SettingDeclarations.THEME_MODE.id, Fate.CARRY);
        m.put(SettingDeclarations.TERMINAL_SCROLLBACK.id, Fate.CARRY);
        m.put(SettingDeclarations.TERMINAL_BELL.id, Fate.CARRY);
        // The Appearance editor's values, owned by edit mode in Terl: start fresh.
        m.put(SettingDeclarations.TERMINAL_CONTRAST.id, Fate.FRESH);
        m.put(SettingDeclarations.TEXT_SIZE.id, Fate.FRESH);
        m.put(SettingDeclarations.CURSOR_TRAIL.id, Fate.FRESH);
        m.put(SettingDeclarations.TERMINAL_EFFECT.id, Fate.FRESH);
        m.put(SettingDeclarations.TERMINAL_SCREEN_EVENTS.id, Fate.FRESH);
        m.put(SettingDeclarations.TERMINAL_EFFECT_COMPOSITE.id, Fate.FRESH);
        m.put(FontSizeSettingsAdapter.FONT_SIZE, Fate.FRESH);
        m.put(FontSizeSettingsAdapter.COMPACT_FONT_SIZE, Fate.FRESH);
        m.put(FontSizeSettingsAdapter.SCRATCHPAD_FONT_SIZE, Fate.FRESH);
        // Motion, battery and glass behaviour.
        m.put(SettingDeclarations.ANIMATION_SPEED.id, Fate.CARRY);
        m.put(SettingDeclarations.IDLE_ANIMATIONS.id, Fate.CARRY);
        m.put(SettingDeclarations.IDLE_TEAR_RATE.id, Fate.CARRY);
        m.put(SettingDeclarations.WALLPAPER_PARALLAX.id, Fate.CARRY);
        m.put(SettingDeclarations.LIVE_WINDOWS.id, Fate.CARRY);
        m.put(SettingDeclarations.REDUCE_IDLE.id, Fate.CARRY);
        m.put(SettingDeclarations.GLASS_REFRACTION.id, Fate.CARRY);
        m.put(SettingDeclarations.LIVE_BLUR.id, Fate.CARRY);
        m.put(LowMemoryPresetAdapter.ID, Fate.CARRY);
        // Home, the drawer and notifications.
        m.put(SettingDeclarations.NOTIFICATION_DOTS.id, Fate.CARRY);
        m.put(SettingDeclarations.DRAWER_SEARCH_ON_OPEN.id, Fate.CARRY);
        m.put(SettingDeclarations.MOST_USED_PAGE.id, Fate.CARRY);
        m.put(UseAsAdapter.ID, Fate.CARRY);
        // Terminals, Linux apps, the camera cutout, the keyboard, keys.
        m.put(SettingDeclarations.TERMINAL_CORE.id, Fate.CARRY);
        m.put(SettingDeclarations.LINUX_DISPLAY_SERVER.id, Fate.CARRY);
        m.put(SettingDeclarations.CUTOUT_MODE.id, Fate.CARRY);
        m.put(SettingDeclarations.CUTOUT_INSET.id, Fate.CARRY);
        m.put(SettingDeclarations.KEYBOARD_KEY_SOUND.id, Fate.CARRY);
        m.put(SettingDeclarations.KEYBOARD_KEY_POPUP.id, Fate.CARRY);
        m.put(SettingDeclarations.KEYBOARD_HAPTICS.id, Fate.CARRY);
        m.put(SettingDeclarations.TERL_DESKTOP.id, Fate.CARRY);
        m.put(SettingDeclarations.TERL_EXTRA_KEYS.id, Fate.CARRY);
        m.put(SettingDeclarations.TERL_DRAWER_TOOL.id, Fate.CARRY);
        m.put(SettingDeclarations.BAR_RAIL_SIDE.id, Fate.CARRY);
        m.put(SettingDeclarations.BAR_READING.id, Fate.CARRY);
        m.put(SettingDeclarations.BAR_READING_KIND.id, Fate.CARRY);
        m.put(SettingDeclarations.BAR_MARGIN.id, Fate.CARRY);
        // Home's A–Z under Terl starts at Terl's default (off), never v1's alphabets.
        m.put(SettingDeclarations.HOME_AZ_EDGE.id, Fate.FRESH);
        // Window looks (#423) are look values: fresh with the Terl look.
        m.put(SettingDeclarations.WINDOWS_GAP.id, Fate.FRESH);
        m.put(SettingDeclarations.WINDOWS_RIM.id, Fate.FRESH);
        m.put(SettingDeclarations.WINDOWS_RIM_COLOUR.id, Fate.FRESH);
        m.put(SettingDeclarations.WINDOWS_UNFOCUSED_DIM.id, Fate.FRESH);
        m.put(SettingDeclarations.LINUX_OPACITY.id, Fate.FRESH);
        m.put(SettingDeclarations.KEYBOARD_LAYOUT.id, Fate.CARRY);
        m.put(SettingDeclarations.KEYS_LEADER.id, Fate.CARRY);
        m.put(LeaderChordSettingAdapter.ID, Fate.CARRY);
        // Rows that retire in Terl (#453's list).
        m.put(SettingDeclarations.PAGE_TRANSITION.id, Fate.DROP);
        m.put(SettingDeclarations.DOCK_STYLE.id, Fate.DROP);
        m.put(SettingDeclarations.STATUS_BATTERY.id, Fate.DROP);
        m.put(SettingDeclarations.STATUS_MEDIA.id, Fate.DROP);
        m.put(SettingDeclarations.STATUS_WEATHER.id, Fate.DROP);
        m.put(SettingDeclarations.STATUS_CLOCK_AM_PM.id, Fate.DROP);
        m.put(SettingDeclarations.NOTIFICATION_BADGE_STYLE.id, Fate.DROP);
        m.put(SettingDeclarations.QUICK_REPLY_SWIPE.id, Fate.DROP);
        return Collections.unmodifiableMap(m);
    }

    @NonNull
    private static Map<String, Fate> keys() {
        Map<String, Fate> m = new HashMap<>();
        // A declaration's key has its declaration's fate.
        for (SettingDeclaration each : SettingDeclarations.ALL) {
            Fate fate = SETTINGS.get(each.id);
            if (fate != null && each.store == SettingDeclaration.Store.APP_PREFERENCES)
                m.put(each.key, fate);
        }
        m.put(LeaderChordSettingAdapter.KEY, Fate.CARRY);
        // The control bar's tools per place (#452): Terl's own, kept through the upgrade.
        carry(m, TERMUX_APP.KEY_TERL_BAR_TOOLS_HOME, TERMUX_APP.KEY_TERL_BAR_TOOLS_TERMINAL,
            TERMUX_APP.KEY_TERL_BAR_TOOLS_LINUX);
        // The first-run hints already seen stay seen.
        carry(m, TERMUX_APP.KEY_TERL_TIPS_DONE);

        carry(m,
            // The keyboard: on or off, layouts, extra keys, font, typing, voice input.
            TERMUX_APP.KEY_IN_APP_KEYBOARD_ENABLED, TERMUX_APP.KEY_IN_APP_KEYBOARD_ACTIVE_LAYOUT,
            TERMUX_APP.KEY_IN_APP_KEYBOARD_LAYOUTS, TERMUX_APP.KEY_IN_APP_KEYBOARD_EXTRA_KEYS,
            TERMUX_APP.KEY_IN_APP_KEYBOARD_FONT_PATH, TERMUX_APP.KEY_IN_APP_KEYBOARD_TAP_CORRECTION,
            TERMUX_APP.KEY_IN_APP_KEYBOARD_BOTTOM_PADDING, TERMUX_APP.KEY_KEYBOARD_TURNED_OFF,
            TERMUX_APP.KEY_IN_APP_KEYBOARD_VOICE_ENGINE, TERMUX_APP.KEY_IN_APP_KEYBOARD_VOICE_LANGUAGE,
            TERMUX_APP.KEY_IN_APP_KEYBOARD_VOICE_MIC_SENSITIVITY,
            TERMUX_APP.KEY_IN_APP_KEYBOARD_VOICE_PAUSE_MS, TERMUX_APP.KEY_IN_APP_KEYBOARD_VOICE_POLISH,
            TERMUX_APP.KEY_IN_APP_KEYBOARD_VOICE_POLISH_LEVEL,
            TERMUX_APP.KEY_IN_APP_KEYBOARD_VOICE_POLISH_MODEL,
            TERMUX_APP.KEY_IN_APP_KEYBOARD_VOICE_SILENCE_TIMEOUT_MS,
            TERMUX_APP.KEY_IN_APP_KEYBOARD_VOICE_SOUNDS, TERMUX_APP.KEY_SOFT_KEYBOARD_ENABLED,
            TERMUX_APP.KEY_SOFT_KEYBOARD_ENABLED_ONLY_IF_NO_HARDWARE,
            TERMUX_APP.KEY_PASS_CTRL_SPACE_TO_ANDROID, TERMUX_APP.KEY_SHOW_TERMINAL_TOOLBAR,
            // The terminal: clipboard, margins, logging, the session counters.
            TERMUX_APP.KEY_TERMINAL_CLIPBOARD_CLEANUP, TERMUX_APP.KEY_TERMINAL_CLIPBOARD_READ_ALWAYS,
            TERMUX_APP.KEY_TERMINAL_OSC52_CLIPBOARD_READ_ENABLED,
            TERMUX_APP.KEY_TERMINAL_MARGIN_ADJUSTMENT, TERMUX_APP.KEY_TERMINAL_VIEW_KEY_LOGGING_ENABLED,
            TERMUX_APP.KEY_TERMINAL_SESSION_NUMBER_SINCE_BOOT, TERMUX_APP.KEY_APP_SHELL_NUMBER_SINCE_BOOT,
            TERMUX_APP.KEY_THEME_TEMPLATES_ENABLED,
            // Linux apps.
            TERMUX_APP.KEY_X11_ANDROID_KEYBOARD, TERMUX_APP.KEY_X11_DISPLAY_AUTOSTART,
            TERMUX_APP.KEY_X11_DISPLAY_COMMAND, TERMUX_APP.KEY_X11_DISPLAY_DPI,
            TERMUX_APP.KEY_X11_DISPLAY_ENABLED, TERMUX_APP.KEY_X11_DRAWER_APPS,
            TERMUX_APP.KEY_X11_EXTRA_KEYS_SIDE, TERMUX_APP.KEY_X11_FORCE_BGRA,
            TERMUX_APP.KEY_X11_KEYBOARD_FOLLOWS_TEXT, TERMUX_APP.KEY_X11_KEYBOARD_SHOWN,
            TERMUX_APP.KEY_X11_LEGACY_DRAWING, TERMUX_APP.KEY_X11_RUNTIME_BADGE,
            TERMUX_APP.KEY_X11_SET_DISPLAY_ENV, TERMUX_APP.KEY_X11_START_WITH,
            TERMUX_APP.KEY_X11_START_WITH_COMMAND, TERMUX_APP.KEY_X11_WINDOW_MANAGER,
            TERMUX_APP.KEY_LAUNCHER_SOUND, TERMUX_APP.KEY_LAUNCHER_MEDIA_CONTROLS,
            // Notifications and their history.
            TERMUX_APP.KEY_APP_NOTIFICATION_HISTORY_MASK_CODES,
            TERMUX_APP.KEY_APP_NOTIFICATION_HISTORY_PACKAGES,
            TERMUX_APP.KEY_APP_NOTIFICATION_HISTORY_RETENTION_DAYS,
            TERMUX_APP.KEY_ESSENTIAL_NOTIFICATION_RULES, TERMUX_APP.KEY_LAST_NOTIFICATION_ID,
            TERMUX_APP.KEY_CRASH_REPORT_NOTIFICATIONS_ENABLED,
            TERMUX_APP.KEY_PLUGIN_ERROR_NOTIFICATIONS_ENABLED,
            // Permissions and the system.
            TERMUX_APP.KEY_LAUNCHERCTL_ROOT_REBOOT, TERMUX_APP.KEY_ACTIVITY_FINISH_REMOVE_TASK,
            TERMUX_APP.KEY_SHOW_IN_RECENTS_WHEN_NOT_DEFAULT, TERMUX_APP.KEY_KEEP_SCREEN_ON,
            TERMUX_APP.KEY_LOG_LEVEL, TERMUX_APP.KEY_MOTION_LOG_ENABLED,
            TERMUX_APP.KEY_APP_HAPTICS_ENABLED, TERMUX_APP.KEY_LOW_MEMORY_PRESET,
            TERMUX_APP.KEY_LOW_MEMORY_PRESET_SNAPSHOT, TERMUX_APP.KEY_APP_LAUNCHER_ANIMATIONS_ENABLED,
            TERMUX_APP.KEY_APP_LAUNCHER_ANIMATION_SAFE_MODE, TERMUX_APP.KEY_USE_AS,
            // Home's icons, pinned apps, widgets' grid, the drawer and search.
            TERMUX_APP.KEY_APP_LAUNCHER_PINNED_ITEMS_V2,
            TERMUX_APP.KEY_APP_LAUNCHER_PINNED_ITEMS_SCHEMA_VERSION,
            TERMUX_APP.KEY_APP_LAUNCHER_DEFAULT_BUTTONS, TERMUX_APP.KEY_APP_LAUNCHER_ICON_PACK_PACKAGE,
            TERMUX_APP.KEY_APP_LAUNCHER_PINNED_ICON_PACK_PACKAGE,
            TERMUX_APP.KEY_APP_LAUNCHER_ICON_PACK_PINNED_ONLY, TERMUX_APP.KEY_APP_LAUNCHER_BW_ICONS,
            TERMUX_APP.KEY_APP_LAUNCHER_DISPLAY_APP_NAMES,
            TERMUX_APP.KEY_APP_LAUNCHER_WIDGET_GRID_COLUMNS,
            TERMUX_APP.KEY_APP_LAUNCHER_WIDGET_GRID_ROWS, TERMUX_APP.KEY_HOME_PALETTE_GESTURE,
            TERMUX_APP.KEY_APP_LAUNCHER_DRAWER_COLUMNS, TERMUX_APP.KEY_APP_LAUNCHER_DRAWER_ROWS,
            TERMUX_APP.KEY_APP_LAUNCHER_DRAWER_ICON_SIZE, TERMUX_APP.KEY_APP_LAUNCHER_DRAWER_SECTION,
            TERMUX_APP.KEY_APP_LAUNCHER_DRAWER_VIEW_TYPE,
            TERMUX_APP.KEY_APP_LAUNCHER_DRAWER_VIEW_TYPE_PINNED,
            TERMUX_APP.KEY_APP_LAUNCHER_DRAWER_SEARCH_ANDROID_KEYBOARD,
            TERMUX_APP.KEY_APP_LAUNCHER_INPUT_CHAR, TERMUX_APP.KEY_WEB_SEARCH_ENGINE,
            TERMUX_APP.KEY_WEB_SEARCH_CUSTOM_URL, TERMUX_APP.KEY_WALL_LAST_PAGE,
            // Where the weather is for (the weather moves to Home's widget).
            TERMUX_APP.KEY_STATUS_WIDGET_WEATHER_LOCATION,
            TERMUX_APP.KEY_STATUS_WIDGET_WEATHER_LOCATION_COORDS,
            // The wallpaper itself (the Appearance reset keeps it too).
            TERMUX_APP.KEY_USE_SYSTEM_WALLPAPER, TERMUX_APP.KEY_WALLPAPER_ALSO_SET_PHONE,
            TERMUX_APP.KEY_WALLPAPER_APP_ONLY, TERMUX_APP.KEY_WALLPAPER_APP_ONLY_SEED,
            TERMUX_APP.KEY_WALLPAPER_APP_ONLY_SEED_STAMP, TERMUX_APP.KEY_WALLPAPER_LOCK_CHOICE,
            TERMUX_APP.KEY_WALLPAPER_MOTION, TERMUX_APP.KEY_MANAGED_WALLPAPER_LOCK_ID,
            TERMUX_APP.KEY_MANAGED_WALLPAPER_SYSTEM_ID, TERMUX_APP.KEY_LIVE_WALLPAPER_DENSITY,
            TERMUX_APP.KEY_LIVE_WALLPAPER_PALETTE, TERMUX_APP.KEY_LIVE_WALLPAPER_RATE_HZ,
            TERMUX_APP.KEY_LIVE_WALLPAPER_SCENE, TERMUX_APP.KEY_LIVE_WALLPAPER_SPEED,
            // One-time markers and first-run flags: what they guard has already happened.
            TERMUX_APP.KEY_TERMINAL_MARGIN_ADJUSTMENT_DEFAULT_MIGRATION_DONE,
            TERMUX_APP.KEY_STATUS_BAR_CUTOUT_MIGRATED, TERMUX_APP.KEY_WALL_MIGRATION_VERSION,
            TERMUX_APP.KEY_KEYBOARD_OPACITY_SENTINEL_HEALED, TERMUX_APP.KEY_X11_DEFAULTS_APPLIED,
            TERMUX_APP.KEY_DISPLAY_ENABLE_PROMPTED, TERMUX_APP.KEY_WALLPAPER_READ_PERMISSION_PROMPTED,
            TERMUX_APP.KEY_EXTRA_KEYS_ASSISTANT_ROW_WRITTEN, TERMUX_APP.KEY_EXTRA_KEYS_DEFAULT_OFFERED,
            TERMUX_APP.KEY_FIRST_BOOT_TOUR_COMPLETED_VERSION,
            TERMUX_APP.KEY_APPEARANCE_THEME_HINT_SEEN, TERMUX_APP.KEY_FIRST_RUN_CHAIN_DONE,
            TERMUX_APP.KEY_FIRST_RUN_PERMISSIONS_CARD_SEEN, TERMUX_APP.KEY_SETUP_WIZARD_VERSION,
            AppearanceReset.KEY_SEEN, AppearanceReset.KEY_CARD);

        put(m, Fate.FRESH,
            // The look outside the Appearance editor's set.
            TERMUX_APP.KEY_SESSIONS_OPACITY, TERMUX_APP.KEY_SURFACE_GLASS_SHINE,
            TERMUX_APP.KEY_PAGE_BAR_GLASS_STYLE, TERMUX_APP.KEY_STATUS_INDICATOR_CORNER_RADIUS,
            TERMUX_APP.KEY_WIDGETS_ON_GLASS, TERMUX_APP.KEY_WALLPAPER_BACKDROP_OPACITY,
            TERMUX_APP.KEY_APP_LAUNCHER_BUILTIN_WIDGET_STYLE,
            TERMUX_APP.KEY_APP_LAUNCHER_DRAWER_CORNER_RADIUS, TERMUX_APP.KEY_COMPACT_FONTSIZE,
            // The layout: the dock's rows, height and rail, the Home pill, the session in front.
            TERMUX_APP.KEY_APP_LAUNCHER_APPS_ROW_ENABLED, TERMUX_APP.KEY_APP_LAUNCHER_AZ_ROW_ENABLED,
            TERMUX_APP.KEY_APP_LAUNCHER_EXTRA_KEYS_ROW_ENABLED, TERMUX_APP.KEY_APP_LAUNCHER_BAR_HEIGHT,
            TERMUX_APP.KEY_APP_LAUNCHER_DOCK_RAIL_SIDE, TERMUX_APP.KEY_HOME_PILL_X,
            TERMUX_APP.KEY_HOME_PILL_Y, TERMUX_APP.KEY_CURRENT_SESSION);

        put(m, Fate.DROP,
            // The dock's A-Z strip and app row, and their ticks.
            TERMUX_APP.KEY_APP_LAUNCHER_AZ_LOCK_METHOD, TERMUX_APP.KEY_APP_LAUNCHER_AZ_DOUBLE_TAP_LOCK,
            TERMUX_APP.KEY_APP_LAUNCHER_ROW_HAPTICS,
            // The status bar's widget pane and its camera Move and Size (Automatic or Custom
            // with an inset stay).
            TERMUX_APP.KEY_APP_LAUNCHER_WIDGET_PANE_ENABLED, TERMUX_APP.KEY_STATUS_BAR_CUTOUT_CENTER,
            TERMUX_APP.KEY_STATUS_BAR_CUTOUT_OFFSET_X, TERMUX_APP.KEY_STATUS_BAR_CUTOUT_OFFSET_Y,
            TERMUX_APP.KEY_STATUS_BAR_CUTOUT_SCALE, TERMUX_APP.KEY_STATUS_BAR_CUTOUT_WIDTH,
            // Panes, body swipes, the flush dock, the Ctrl+Alt hint strip, Start in (Terl opens
            // on Home), and the usage mode that retired before Terl.
            TERMUX_APP.KEY_AGENT_PANES_ENABLED, TERMUX_APP.KEY_TERMINAL_BODY_SWIPE,
            TERMUX_APP.KEY_LEGACY_TERMINAL_FLUSH_DOCK, TERMUX_APP.KEY_SHOW_KEY_HINTS,
            TERMUX_APP.KEY_LAUNCHER_START_IN, TERMUX_APP.KEY_APP_LAUNCHER_USE_CASE_MODE,
            TERMUX_APP.KEY_APP_LAUNCHER_USE_CASE_SNAPSHOT);
        return Collections.unmodifiableMap(m);
    }

    private static void carry(@NonNull Map<String, Fate> m, @NonNull String... keys) {
        put(m, Fate.CARRY, keys);
    }

    private static void put(@NonNull Map<String, Fate> m, @NonNull Fate fate,
                            @NonNull String... keys) {
        for (String key : keys) {
            Fate before = m.put(key, fate);
            if (before != null && before != fate)
                throw new IllegalStateException(key + " is both " + before + " and " + fate);
        }
    }
}
