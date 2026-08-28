package com.termux.app.theme;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.res.Resources;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import com.google.android.material.color.ColorResourcesOverride;
import com.termux.R;
import com.termux.shared.logger.Logger;
import com.termux.shared.termux.TermuxConstants;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;
import java.util.WeakHashMap;

/**
 * Paints the launcher chrome from the terminal colour scheme.
 *
 * <p>Every surface the launcher draws — dock, status bar, app drawer, in-app keyboard, command
 * palette, glass rims — reads Material roles, so the whole feature is: derive the roles from
 * {@code ~/.termux/colors.properties} (see {@link LauncherThemeTokens}), then make the activity's
 * Material roles resolve to those values. The second half is done with a {@code ResourcesLoader}
 * over Material's own {@code material_personalized_color_*} resources, which is the same machinery
 * {@code DynamicColors} uses for wallpaper colours; borrowing it means no view, layout or drawable
 * in the app has to learn that schemes exist.
 *
 * <p>{@code ResourcesLoader} is API 30+. Below that the chrome keeps the wallpaper/static palette
 * and only the terminal follows the scheme, as before — the alternative was rerouting 130-odd
 * {@code MaterialColors.getColor} calls through a facade, which would still have missed every
 * {@code ?attr/} reference in XML.
 */
public final class LauncherSchemeTheme {

    private static final String LOG_TAG = "LauncherSchemeTheme";

    /** User overrides for the derived tokens; see {@link LauncherThemeOverrides}. */
    public static final String THEME_OVERRIDES_FILE_PATH =
        TermuxConstants.TERMUX_DATA_HOME_DIR_PATH + "/launcher-theme.properties";

    /**
     * Which {@code Resources} already carry which palette.
     *
     * <p>Every added loader stays on the {@code Resources} for its lifetime, and activity recreation
     * — which the launcher does on every style reload, orientation change and font-scale change —
     * hands back the same shared instance. Without this the loaders pile up one per recreate.
     */
    private static final Map<Resources, Integer> APPLIED = new WeakHashMap<>();

    private static LinkedHashMap<String, Integer> sCachedTokens;
    private static long sCachedFingerprint;
    private static Boolean sCachedChromeActive;

    private LauncherSchemeTheme() {}

    /** Whether this device can theme its chrome from a scheme at all. */
    public static boolean isSupported() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.R;
    }

    /**
     * Whether the chrome is on the terminal's own scheme, and there is a scheme to drive it.
     *
     * <p>Derived rather than chosen. There used to be a second control naming the chrome's colour
     * source, which could disagree with the switch above it — chrome on the scheme while the
     * terminal was on the wallpaper, or the reverse — and every combination but "both from the
     * same place" is a launcher wearing two palettes at once. Turning wallpaper colours off is
     * choosing the scheme, for the terminal and for the chrome together.
     */
    public static boolean isEnabled(@Nullable TermuxAppSharedPreferences preferences) {
        return preferences != null
            && !preferences.isTerminalDynamicColorsEnabled()
            && TermuxConstants.TERMUX_COLOR_PROPERTIES_FILE.isFile();
    }

    /**
     * Whether the scheme palette is driving this context's chrome — supported, selected, and with
     * a readable scheme behind it.
     *
     * <p>The launcher chrome has a handful of colours that deliberately bypass the theme in
     * wallpaper mode (the glass base reads the framework's {@code system_neutral1_900} so the dock
     * matches Material You exactly). Those sites must not bypass it when the chrome belongs to the
     * scheme, and this is the predicate they gate on.
     */
    public static synchronized boolean isSchemeChromeActive(@NonNull Context context) {
        if (!isSupported()) return false;
        // Cached because glass colours are resolved on chrome-apply paths: every route that flips
        // the wallpaper-colours switch or rewrites the scheme calls invalidate(), which clears
        // this too.
        if (sCachedChromeActive != null) return sCachedChromeActive;
        TermuxAppSharedPreferences preferences = TermuxAppSharedPreferences.build(context);
        sCachedChromeActive = isEnabled(preferences) && tokens() != null;
        return sCachedChromeActive;
    }

    private static synchronized void setChromeActive(boolean active) {
        sCachedChromeActive = active;
    }

    /**
     * Applies the scheme palette to {@code activity}, or does nothing and returns false.
     *
     * <p>Must be called after {@code setTheme} — both this and Material's own overlay are applied
     * with {@code Theme.applyStyle}, and {@code setTheme} discards anything applied before it.
     */
    @SuppressLint("RestrictedApi")
    public static boolean apply(@NonNull Activity activity,
                                @Nullable TermuxAppSharedPreferences preferences) {
        boolean enabled = isSupported() && isEnabled(preferences);
        LinkedHashMap<String, Integer> tokens = enabled ? tokens() : null;
        // apply() runs on every activity create, so it is the natural refresh point for the
        // chrome-active cache: a scheme written after the first computation (termux-styling
        // installing a theme, then termux-reload-settings recreating) must flip it without
        // waiting for a settings-screen invalidate().
        setChromeActive(tokens != null);
        if (tokens == null) return false;

        ColorResourcesOverride override = ColorResourcesOverride.getInstance();
        if (override == null) return false;

        try {
            Resources resources = activity.getResources();
            Integer applied = APPLIED.get(resources);
            int fingerprint = tokens.hashCode();
            if (applied != null && applied == fingerprint) {
                // The values are already loaded on these Resources; the new theme only needs the
                // overlay that points the Material role attributes at them.
                activity.getTheme().applyStyle(
                    com.google.android.material.R.style.ThemeOverlay_Material3_PersonalizedColors, true);
            } else {
                if (!override.applyIfPossible(activity, resourceOverrides(tokens))) {
                    Logger.logWarn(LOG_TAG, "Scheme colour resources could not be loaded");
                    return false;
                }
                APPLIED.put(resources, fingerprint);
            }
            // Material's overlay only rewires the Material roles. The termuxColor* attributes are
            // the launcher's own indirection and on API 30 they still point at the static palette,
            // so they are pointed back at the roles here. The light/dark spelling of the overlay
            // follows the scheme, not the system night mode.
            boolean dark = SchemeTone.isDark(token(tokens, LauncherThemeTokens.SURFACE));
            activity.getTheme().applyStyle(dark
                ? R.style.ThemeOverlay_Termux_SchemeColors_Dark
                : R.style.ThemeOverlay_Termux_SchemeColors_Light, true);
            return true;
        } catch (Exception e) {
            Logger.logStackTraceWithMessage(LOG_TAG, "Error applying scheme colours", e);
            return false;
        }
    }

    /**
     * The current token set, or null when there is no readable scheme.
     *
     * <p>Cached against the modification stamps of both input files: this runs during
     * {@code onCreate} of an activity that recreates often, and the derivation does a handful of
     * 101-step HCT contrast searches.
     */
    @Nullable
    public static synchronized LinkedHashMap<String, Integer> tokens() {
        long fingerprint = fileFingerprint(TermuxConstants.TERMUX_COLOR_PROPERTIES_FILE)
            * 31 + fileFingerprint(new File(THEME_OVERRIDES_FILE_PATH));
        if (sCachedTokens != null && fingerprint == sCachedFingerprint) return sCachedTokens;

        SchemeColors scheme = SchemeColors.from(read(TermuxConstants.TERMUX_COLOR_PROPERTIES_FILE));
        if (scheme == null) {
            sCachedTokens = null;
            sCachedFingerprint = fingerprint;
            return null;
        }
        Properties overrides = read(new File(THEME_OVERRIDES_FILE_PATH));
        LinkedHashMap<String, Integer> tokens = LauncherThemeTokens.derive(scheme);
        LauncherThemeOverrides.apply(tokens, scheme, overrides);
        // Once per edit of the file, not once per activity: a misspelled token is silent otherwise,
        // and "my override does nothing" is the hardest kind of theme bug to see.
        for (String unknown : LauncherThemeOverrides.unknownKeys(overrides)) {
            Logger.logWarn(LOG_TAG, "Unknown theme token in " + THEME_OVERRIDES_FILE_PATH + ": " + unknown);
        }
        sCachedTokens = tokens;
        sCachedFingerprint = fingerprint;
        return tokens;
    }

    /** Drops the cache; for tests and for callers that just rewrote the scheme themselves. */
    public static synchronized void invalidate() {
        sCachedTokens = null;
        sCachedFingerprint = 0;
        sCachedChromeActive = null;
    }

    /**
     * The token set as the {@code material-colors} export uses it — the same key names the
     * wallpaper path writes, so shell, tmux and Neovim consumers see one palette shape whichever
     * source the colours came from.
     */
    @NonNull
    public static Properties exportProperties(@NonNull LinkedHashMap<String, Integer> tokens) {
        Properties props = new Properties();
        for (Map.Entry<String, Integer> entry : tokens.entrySet()) {
            props.setProperty(entry.getKey(), SchemeColors.hex(entry.getValue()));
        }
        // The wallpaper export names this one surface_variant; keep both spellings so a consumer
        // written against either file keeps working.
        Integer surfaceContainer = tokens.get(LauncherThemeTokens.SURFACE_CONTAINER);
        if (surfaceContainer != null) props.setProperty("surface_variant", SchemeColors.hex(surfaceContainer));
        return props;
    }

    /** Material's overridable colour resources, filled in from {@code tokens}. */
    @VisibleForTesting
    @NonNull
    static Map<Integer, Integer> resourceOverrides(@NonNull LinkedHashMap<String, Integer> tokens) {
        Map<Integer, Integer> map = new HashMap<>();
        int surface = token(tokens, LauncherThemeTokens.SURFACE);
        int onSurface = token(tokens, LauncherThemeTokens.ON_SURFACE);
        int onSurfaceVariant = token(tokens, LauncherThemeTokens.ON_SURFACE_VARIANT);
        int inverseOnSurface = token(tokens, LauncherThemeTokens.INVERSE_ON_SURFACE);

        put(map, com.google.android.material.R.color.material_personalized_color_primary, tokens, LauncherThemeTokens.PRIMARY);
        put(map, com.google.android.material.R.color.material_personalized_color_on_primary, tokens, LauncherThemeTokens.ON_PRIMARY);
        put(map, com.google.android.material.R.color.material_personalized_color_primary_container, tokens, LauncherThemeTokens.PRIMARY_CONTAINER);
        put(map, com.google.android.material.R.color.material_personalized_color_on_primary_container, tokens, LauncherThemeTokens.ON_PRIMARY_CONTAINER);
        put(map, com.google.android.material.R.color.material_personalized_color_primary_inverse, tokens, LauncherThemeTokens.INVERSE_PRIMARY);
        put(map, com.google.android.material.R.color.material_personalized_color_secondary, tokens, LauncherThemeTokens.SECONDARY);
        put(map, com.google.android.material.R.color.material_personalized_color_on_secondary, tokens, LauncherThemeTokens.ON_SECONDARY);
        put(map, com.google.android.material.R.color.material_personalized_color_secondary_container, tokens, LauncherThemeTokens.SECONDARY_CONTAINER);
        put(map, com.google.android.material.R.color.material_personalized_color_on_secondary_container, tokens, LauncherThemeTokens.ON_SECONDARY_CONTAINER);
        put(map, com.google.android.material.R.color.material_personalized_color_tertiary, tokens, LauncherThemeTokens.TERTIARY);
        put(map, com.google.android.material.R.color.material_personalized_color_on_tertiary, tokens, LauncherThemeTokens.ON_TERTIARY);
        put(map, com.google.android.material.R.color.material_personalized_color_tertiary_container, tokens, LauncherThemeTokens.TERTIARY_CONTAINER);
        put(map, com.google.android.material.R.color.material_personalized_color_on_tertiary_container, tokens, LauncherThemeTokens.ON_TERTIARY_CONTAINER);
        put(map, com.google.android.material.R.color.material_personalized_color_error, tokens, LauncherThemeTokens.ERROR);
        put(map, com.google.android.material.R.color.material_personalized_color_on_error, tokens, LauncherThemeTokens.ON_ERROR);
        put(map, com.google.android.material.R.color.material_personalized_color_error_container, tokens, LauncherThemeTokens.ERROR_CONTAINER);
        put(map, com.google.android.material.R.color.material_personalized_color_on_error_container, tokens, LauncherThemeTokens.ON_ERROR_CONTAINER);

        put(map, com.google.android.material.R.color.material_personalized_color_surface, tokens, LauncherThemeTokens.SURFACE);
        put(map, com.google.android.material.R.color.material_personalized_color_on_surface, tokens, LauncherThemeTokens.ON_SURFACE);
        put(map, com.google.android.material.R.color.material_personalized_color_surface_variant, tokens, LauncherThemeTokens.SURFACE_CONTAINER);
        put(map, com.google.android.material.R.color.material_personalized_color_on_surface_variant, tokens, LauncherThemeTokens.ON_SURFACE_VARIANT);
        put(map, com.google.android.material.R.color.material_personalized_color_surface_bright, tokens, LauncherThemeTokens.SURFACE_BRIGHT);
        put(map, com.google.android.material.R.color.material_personalized_color_surface_dim, tokens, LauncherThemeTokens.SURFACE_DIM);
        put(map, com.google.android.material.R.color.material_personalized_color_surface_container_lowest, tokens, LauncherThemeTokens.SURFACE_CONTAINER_LOWEST);
        put(map, com.google.android.material.R.color.material_personalized_color_surface_container_low, tokens, LauncherThemeTokens.SURFACE_CONTAINER_LOW);
        put(map, com.google.android.material.R.color.material_personalized_color_surface_container, tokens, LauncherThemeTokens.SURFACE_CONTAINER);
        put(map, com.google.android.material.R.color.material_personalized_color_surface_container_high, tokens, LauncherThemeTokens.SURFACE_CONTAINER_HIGH);
        put(map, com.google.android.material.R.color.material_personalized_color_surface_container_highest, tokens, LauncherThemeTokens.SURFACE_CONTAINER_HIGHEST);
        put(map, com.google.android.material.R.color.material_personalized_color_surface_inverse, tokens, LauncherThemeTokens.INVERSE_SURFACE);
        put(map, com.google.android.material.R.color.material_personalized_color_on_surface_inverse, tokens, LauncherThemeTokens.INVERSE_ON_SURFACE);
        put(map, com.google.android.material.R.color.material_personalized_color_outline, tokens, LauncherThemeTokens.OUTLINE);
        put(map, com.google.android.material.R.color.material_personalized_color_outline_variant, tokens, LauncherThemeTokens.OUTLINE_VARIANT);

        // The launcher's own overridable colour: the scrollbar is not a Material role.
        put(map, R.color.launcher_scheme_scrollbar, tokens, LauncherThemeTokens.SCROLLBAR);

        map.put(com.google.android.material.R.color.material_personalized_color_background, surface);
        map.put(com.google.android.material.R.color.material_personalized_color_on_background, onSurface);
        map.put(com.google.android.material.R.color.material_personalized_color_control_activated,
            token(tokens, LauncherThemeTokens.PRIMARY));
        map.put(com.google.android.material.R.color.material_personalized_color_control_normal, onSurfaceVariant);
        map.put(com.google.android.material.R.color.material_personalized_color_control_highlight,
            SchemeTone.withAlpha(onSurface, 0.12f));

        // The inverse-side text colours are alpha ramps in Material's own palette, not separate hues.
        map.put(com.google.android.material.R.color.material_personalized_color_text_primary_inverse, inverseOnSurface);
        map.put(com.google.android.material.R.color.material_personalized_color_text_secondary_and_tertiary_inverse,
            SchemeTone.withAlpha(inverseOnSurface, 0.7f));
        map.put(com.google.android.material.R.color.material_personalized_color_text_hint_foreground_inverse,
            SchemeTone.withAlpha(inverseOnSurface, 0.5f));
        map.put(com.google.android.material.R.color.material_personalized_color_text_primary_inverse_disable_only,
            SchemeTone.withAlpha(inverseOnSurface, 0.38f));
        map.put(com.google.android.material.R.color.material_personalized_color_text_secondary_and_tertiary_inverse_disabled,
            SchemeTone.withAlpha(inverseOnSurface, 0.38f));
        return map;
    }

    private static void put(@NonNull Map<Integer, Integer> map, int resourceId,
                            @NonNull Map<String, Integer> tokens, @NonNull String token) {
        map.put(resourceId, token(tokens, token));
    }

    private static int token(@NonNull Map<String, Integer> tokens, @NonNull String name) {
        Integer value = tokens.get(name);
        return value == null ? 0 : value;
    }

    @Nullable
    private static Properties read(@NonNull File file) {
        if (!file.isFile()) return null;
        Properties props = new Properties();
        try (InputStream in = new FileInputStream(file)) {
            props.load(in);
        } catch (Exception e) {
            Logger.logStackTraceWithMessage(LOG_TAG, "Error reading " + file.getAbsolutePath(), e);
            return null;
        }
        return props;
    }

    private static long fileFingerprint(@NonNull File file) {
        if (!file.isFile()) return 0L;
        long fingerprint = file.lastModified() * 31L + file.length();
        // On the Nix edition the scheme is usually a home-manager symlink into /nix/store, where
        // every file carries the same fixed epoch mtime — two generations of equal length would
        // fingerprint identically and serve a stale palette. The store path changes per
        // generation, so mix the resolved target in.
        try {
            fingerprint = fingerprint * 31L + file.getCanonicalPath().hashCode();
        } catch (Exception ignored) {
        }
        return fingerprint;
    }
}
