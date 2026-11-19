package com.termux.app.surfaces;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import com.termux.app.place.LayoutReset;
import com.termux.shared.logger.Logger;
import com.termux.shared.termux.settings.preferences.LookGrid;
import com.termux.shared.termux.settings.preferences.LookGridMarginMigration;
import com.termux.shared.termux.settings.preferences.LookGridMigration;
import com.termux.shared.termux.settings.preferences.PageLookKeys;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences.SurfaceProperty;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences.SurfaceSlot;
import com.termux.shared.termux.settings.preferences.TermuxPreferenceConstants.TERMUX_APP;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * The appearance reset on upgrade: on the first start of a build whose {@link #NUMBER} is higher
 * than the one this install has seen, everything the Appearance editor owns goes back to what a
 * new install has, once, and the user is told on Home ({@code AppearanceResetCard}). A new install
 * only stores the number.
 *
 * <p>Runs in the application's start-up chain after the wall migrations and before the look
 * steps, which then run as on a new install ({@link #runAtStartUp} returns true): the steps' own
 * markers are in the scope below, so the shipped look is written and seeded exactly as a new
 * install's is.
 *
 * <p>The number is raised only for a release the developer decides resets everyone's look (the
 * 2.0 pre-release and the 2.0 stable release). Nothing else the user owns is touched: Home pages,
 * widgets, pins, sessions, the wallpaper, {@code termux.properties} (extra keys, Mode, full
 * screen), keyboard layouts, positions and every one-time flag stay.
 */
public final class AppearanceReset {

    private AppearanceReset() {}

    private static final String LOG_TAG = "AppearanceReset";

    /** The reset number this build carries; raise it only on the developer's say. */
    public static final int NUMBER = 1;

    /** The highest reset number this install has seen (0 for none). */
    public static final String KEY_SEEN = "appearance_reset_seen";

    /** The card's state: {@link #CARD_DUE} after a reset, {@link #CARD_SETTLED} once answered. */
    public static final String KEY_CARD = "appearance_reset_card";
    public static final int CARD_NONE = 0;
    public static final int CARD_DUE = 1;
    public static final int CARD_SETTLED = 2;

    /**
     * The flat keys the reset removes by name (prefix families are in {@link #inScope}). Each
     * group's neighbours under the same prefix that stay are named beside it.
     */
    private static final Set<String> NAMED = names(
        // The glass: material, tint, rim, motion, hue, vibrancy, the Glass positions, depth.
        TERMUX_APP.KEY_SURFACE_MATERIAL, TERMUX_APP.KEY_SURFACE_MATERIAL_INTENSITY,
        TERMUX_APP.KEY_SURFACE_GLASS_TINT, TERMUX_APP.KEY_SURFACE_GLASS_TINT_HUE,
        TERMUX_APP.KEY_SURFACE_GLASS_VIBRANCY, TERMUX_APP.KEY_SURFACE_GLASS_RIM,
        TERMUX_APP.KEY_SURFACE_GLASS_MOTION, TERMUX_APP.KEY_SURFACE_GLASS_POSITION,
        TERMUX_APP.KEY_SURFACE_CUSTOM_PRESET, SurfacePresets.RETIRED_SHADE_KEY,
        TERMUX_APP.KEY_FANCIER_GLASS_BEND, TERMUX_APP.KEY_FANCIER_GLASS_EDGE_WIDTH,
        TERMUX_APP.KEY_FANCIER_GLASS_EDGE_LIGHT, TERMUX_APP.KEY_FANCIER_GLASS_SPECULAR,
        TERMUX_APP.KEY_FANCIER_GLASS_DISPERSION, TERMUX_APP.KEY_FANCIER_GLASS_CLARITY,
        TERMUX_APP.KEY_FANCIER_GLASS_FACE_CLARITY, TERMUX_APP.KEY_FANCIER_GLASS_DIM,
        TERMUX_APP.KEY_FANCIER_GLASS_SIZE_LENS,
        // The surfaces' older keys, which the pinned pre-shipped defaults wrote.
        TERMUX_APP.KEY_EXTRAKEYS_BLUR_RADIUS, TERMUX_APP.KEY_APP_BAR_OPACITY,
        TERMUX_APP.KEY_DOCK_GLASS_GRAIN, TERMUX_APP.KEY_APP_LAUNCHER_DOCK_CORNER_RADIUS,
        TERMUX_APP.KEY_DOCK_HORIZONTAL_INSET, TERMUX_APP.KEY_STATUS_BAR_CORNER_RADIUS,
        TERMUX_APP.KEY_STATUS_BAR_HORIZONTAL_INSET, TERMUX_APP.KEY_HOME_LAYOUT_STYLE,
        TERMUX_APP.KEY_HOME_SURFACE_CORNER_RADIUS, TERMUX_APP.KEY_HOME_SURFACE_SIDE_GAP,
        // Wallpaper mode's remembered glass, the wallpaper dim and soft wallpaper (the wallpaper
        // itself and the system-wallpaper switch stay).
        TERMUX_APP.KEY_WALLPAPER_ENABLED_TERMINAL_BACKGROUND_OPACITY,
        TERMUX_APP.KEY_WALLPAPER_ENABLED_APP_BAR_OPACITY,
        TERMUX_APP.KEY_WALLPAPER_ENABLED_EXTRAKEYS_BLUR_RADIUS,
        TERMUX_APP.KEY_WALLPAPER_BACKDROP_DIM, SoftWallpaper.KEY_WALLPAPER_SOFT,
        // The dock: its style and how many app buttons it shows.
        TERMUX_APP.KEY_APP_LAUNCHER_DOCK_STYLE, TERMUX_APP.KEY_APP_LAUNCHER_BUTTON_COUNT,
        // The terminal: border, corners, pane gap and borders, contrast, Dynamic colour, cursor
        // trail, effect, text size, and the pane switches the Terminal card writes.
        TERMUX_APP.KEY_TERMINAL_BORDER_ENABLED, TERMUX_APP.KEY_TERMINAL_CORNER_RADIUS,
        TERMUX_APP.KEY_TERMINAL_PANE_GAP, TERMUX_APP.KEY_TERMINAL_PANE_BORDER_COLOUR,
        TERMUX_APP.KEY_TERMINAL_PANE_BORDER_CUSTOM, TERMUX_APP.KEY_TERMINAL_PANE_FOCUS_COLOUR,
        TERMUX_APP.KEY_TERMINAL_PANE_FOCUS_CUSTOM, TERMUX_APP.KEY_TERMINAL_PANE_GLOW,
        TERMUX_APP.KEY_TERMINAL_PANE_GLOW_COLOUR, TERMUX_APP.KEY_TERMINAL_PANE_GLOW_CUSTOM,
        TERMUX_APP.KEY_TERMINAL_CONTRAST_LEVEL, TERMUX_APP.KEY_TERMINAL_DYNAMIC_COLORS_ENABLED,
        TERMUX_APP.KEY_TERMINAL_CURSOR_TRAIL, TERMUX_APP.KEY_TERMINAL_CURSOR_TRAIL_STYLE,
        TERMUX_APP.KEY_TERMINAL_RETRO_EFFECT, TERMUX_APP.KEY_TEXT_SIZE_PERCENT,
        // The rows #286 moved onto the Terminal and Keyboard cards.
        TERMUX_APP.KEY_TERMINAL_SCREEN_EVENTS, TERMUX_APP.KEY_TERMINAL_RETRO_COMPOSITE,
        TERMUX_APP.KEY_IN_APP_KEYBOARD_FLOATING_WIDTH_SCALE,
        TERMUX_APP.KEY_IN_APP_KEYBOARD_FLOATING_WIDTH_SCALE_LANDSCAPE,
        TERMUX_APP.KEY_IN_APP_KEYBOARD_FLOATING_HEIGHT_SCALE,
        TERMUX_APP.KEY_IN_APP_KEYBOARD_FLOATING_HEIGHT_SCALE_LANDSCAPE,
        TERMUX_APP.KEY_IN_APP_KEYBOARD_SPLIT_GAP_FRACTION,
        TERMUX_APP.KEY_IN_APP_KEYBOARD_SPLIT_GAP_FRACTION_LANDSCAPE,
        TERMUX_APP.KEY_COMPATIBILITY_MODE, TERMUX_APP.KEY_DWINDLE_DEFAULT_LAYOUT,
        TERMUX_APP.KEY_FOCUSED_PANE_GROWS,
        // The keyboard's look: blur, grain, key opacity and its link, key corners and spacing,
        // the backdrop, height, theme and colour scheme. in_app_keyboard_enabled, _layouts,
        // _extra_keys and _font_path stay.
        TERMUX_APP.KEY_IN_APP_KEYBOARD_BLUR_RADIUS, TERMUX_APP.KEY_IN_APP_KEYBOARD_GRAIN,
        TERMUX_APP.KEY_IN_APP_KEYBOARD_KEY_OPACITY, TERMUX_APP.KEY_IN_APP_KEYBOARD_OPACITY_LINKED,
        TERMUX_APP.KEY_IN_APP_KEYBOARD_KEY_CORNER_RADIUS_DP,
        TERMUX_APP.KEY_IN_APP_KEYBOARD_KEY_MARGIN_SCALE,
        TERMUX_APP.KEY_IN_APP_KEYBOARD_BACKDROP_OPACITY,
        TERMUX_APP.KEY_IN_APP_KEYBOARD_HEIGHT_SCALE,
        TERMUX_APP.KEY_IN_APP_KEYBOARD_HEIGHT_SCALE_LANDSCAPE,
        TERMUX_APP.KEY_IN_APP_KEYBOARD_THEME, TERMUX_APP.KEY_IN_APP_KEYBOARD_COLOR_SCHEME,
        // The status bar: the clock and its widgets' switches. status_widget_weather_location
        // and status_widget_weather_location_coords (where the weather is for) stay.
        TERMUX_APP.KEY_TOP_PANE_CLOCK_STYLE, TERMUX_APP.KEY_TOP_PANE_CLOCK_ALIGNMENT,
        TERMUX_APP.KEY_TOP_PANE_CLOCK_AM_PM, TERMUX_APP.KEY_TOP_PANE_CLOCK_COLLAPSED,
        TERMUX_APP.KEY_STATUS_CLOCK_SECONDS, TERMUX_APP.KEY_STATUS_CLOCK_SHOWN,
        TERMUX_APP.KEY_STATUS_WIDGET_CPU, TERMUX_APP.KEY_STATUS_WIDGET_RAM,
        TERMUX_APP.KEY_STATUS_WIDGET_BATTERY, TERMUX_APP.KEY_STATUS_WIDGET_MEDIA,
        TERMUX_APP.KEY_STATUS_WIDGET_WEATHER, TERMUX_APP.KEY_STATUS_WIDGET_WEATHER_FAHRENHEIT,
        TERMUX_APP.KEY_STATUS_WIDGET_NOTIFICATIONS,
        // The look steps' markers, so they write and seed the shipped look as on a new install.
        // Every other migration marker (wall, pages, half looks, places, cut-out) stays.
        TERMUX_APP.KEY_SHIPPED_SURFACE_DEFAULTS_ADOPTED, TERMUX_APP.KEY_SURFACE_INHERITANCE_MIGRATED,
        TERMUX_APP.KEY_KEYBOARD_GLASS_SLOT_FOLDED, TERMUX_APP.KEY_SURFACE_TUNING_NORMALIZED,
        TERMUX_APP.KEY_LAYOUT_STYLE_MIGRATED, TERMUX_APP.KEY_SURFACE_GLASS_CONTROLS_FORMAT,
        SurfacePresets.KEY_LOOKS_GENERATION, LookGridMigration.KEY_MIGRATED,
        LookGridMarginMigration.KEY_MIGRATED,
        // The kind steps' markers and seed list (retired with the kind row, ADR 0031).
        "look_grid_kind_migrated", "look_grid_kind_seeded", "look_grid_kind_seed_marked",
        "look_grid_seeded_cells");

    /**
     * Whether the reset removes {@code key}: a named key, a surface's Base or own value, a look
     * grid cell ({@code look.*}), a surface link ({@code surface_inherit_*}), a page's own value
     * ({@code <key>@<page id>}), the terminal's pinch size ({@code fontsize*}), or a flat copy of
     * a place's layout or dress ({@code layout.*}, {@code place.dress.*}; the voice pill's and a
     * floating keyboard's positions and {@code place.migrated} are not copies, and stay).
     */
    @VisibleForTesting
    static boolean inScope(@NonNull String key) {
        return NAMED.contains(key) || SURFACE_KEYS.contains(key)
            || LookGrid.parse(key) != null
            || key.startsWith(LookGridMigration.INHERIT_PREFIX)
            || AppearanceSnapshot.isPageKey(key, PAGE_ABLE)
            || key.startsWith(TERMUX_APP.KEY_FONTSIZE)
            || LayoutReset.isFlatLayoutKey(key);
    }

    /** Every surface's Base and own value, and the keys a page may hold its own copy of. */
    private static final Set<String> SURFACE_KEYS = surfaceKeys();
    private static final Set<String> PAGE_ABLE = pageAble();

    /**
     * Runs the reset when this install has seen a lower number; a new install only stores the
     * number. Never throws: a failure is logged and leaves the preferences as they were.
     *
     * @return whether it reset, so the look steps after it run as on a new install
     */
    public static boolean runAtStartUp(@NonNull Context context,
                                       @NonNull TermuxAppSharedPreferences prefs,
                                       boolean freshInstall) {
        try {
            SharedPreferences store = prefs.getSharedPreferences();
            if (store == null || store.getInt(KEY_SEEN, 0) >= NUMBER) return false;
            if (freshInstall) {
                store.edit().putInt(KEY_SEEN, NUMBER).commit();
                return false;
            }
            // The layouts are brought up to date before the one write, which then removes their
            // flat copies too.
            com.termux.app.place.layout.Layouts layouts = LayoutReset.prepare(prefs, context);
            if (!resetPreferences(store)) {
                Logger.logError(LOG_TAG, "The appearance reset did not write; it runs next start");
                return false;
            }
            // After the one write: the files, each on its own, so one that fails is logged.
            try {
                LayoutReset.write(context, layouts);
            } catch (Throwable t) {
                Logger.logStackTraceWithMessage(LOG_TAG, "The layouts were not reset", t);
            }
            try {
                new com.termux.app.fonts.FontInstaller().uninstallManagedConfig();
                new com.termux.app.fonts.FontSettings(context).forgetFamily();
            } catch (Throwable t) {
                Logger.logStackTraceWithMessage(LOG_TAG, "The terminal font was not reset", t);
            }
            return true;
        } catch (Throwable t) {
            Logger.logStackTraceWithMessage(LOG_TAG, "The appearance reset failed", t);
            return false;
        }
    }

    /**
     * In one commit: removes every key {@link #inScope}, stores the number, makes the card due and
     * settles the glass reset nudge (a reset leaves it nothing to offer).
     *
     * @return whether the commit was written
     */
    @VisibleForTesting
    static boolean resetPreferences(@NonNull SharedPreferences store) {
        SharedPreferences.Editor editor = store.edit();
        for (Map.Entry<String, ?> entry : store.getAll().entrySet()) {
            if (inScope(entry.getKey())) editor.remove(entry.getKey());
        }
        editor.putInt(KEY_SEEN, NUMBER);
        editor.putInt(KEY_CARD, CARD_DUE);
        editor.putInt(GlassResetNudge.KEY, GlassResetNudge.SETTLED);
        return editor.commit();
    }

    /** Whether the card waits to be shown. */
    public static boolean isCardDue(@Nullable SharedPreferences store) {
        return store != null && store.getInt(KEY_CARD, CARD_NONE) == CARD_DUE;
    }

    /** The card was dismissed or led to the editor: it never shows again. */
    public static void settleCard(@Nullable SharedPreferences store) {
        if (store != null) store.edit().putInt(KEY_CARD, CARD_SETTLED).apply();
    }

    @NonNull
    private static Set<String> names(@NonNull String... keys) {
        return Collections.unmodifiableSet(new HashSet<>(Arrays.asList(keys)));
    }

    @NonNull
    private static Set<String> surfaceKeys() {
        Set<String> keys = new HashSet<>();
        for (SurfaceProperty property : SurfaceProperty.values()) {
            keys.add(property.baseKey);
            for (SurfaceSlot slot : SurfaceSlot.values()) {
                String own = TermuxAppSharedPreferences.surfaceOverrideKey(slot, property);
                if (own != null) keys.add(own);
            }
        }
        keys.addAll(AppearanceSnapshot.halfAbleKeys());
        keys.addAll(PageLookKeys.everHeld());
        return Collections.unmodifiableSet(keys);
    }

    @NonNull
    private static Set<String> pageAble() {
        Set<String> able = new HashSet<>(AppearanceSnapshot.halfAbleKeys());
        able.addAll(PageLookKeys.everHeld());
        return Collections.unmodifiableSet(able);
    }
}
