package com.termux.shared.termux.settings.preferences;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.TypedValue;
import android.os.Build;
import android.view.Display;
import android.view.WindowManager;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.termux.shared.android.PackageUtils;
import com.termux.shared.settings.preferences.AppSharedPreferences;
import com.termux.shared.settings.preferences.SharedPreferenceUtils;
import com.termux.shared.termux.TermuxConstants;
import com.termux.shared.logger.Logger;
import com.termux.shared.data.DataUtils;
import com.termux.shared.termux.TermuxUtils;
import com.termux.shared.termux.settings.preferences.TermuxPreferenceConstants.TERMUX_APP;

public class TermuxAppSharedPreferences extends AppSharedPreferences {

    private int MIN_FONTSIZE;

    private int MAX_FONTSIZE;

    private int DEFAULT_FONTSIZE;

    private static final String LOG_TAG = "TermuxAppSharedPreferences";

    private TermuxAppSharedPreferences(@NonNull Context context) {
        this(
            context,
            SharedPreferenceUtils.getPrivateSharedPreferences(context, TermuxConstants.TERMUX_DEFAULT_PREFERENCES_FILE_BASENAME_WITHOUT_EXTENSION),
            SharedPreferenceUtils.getPrivateAndMultiProcessSharedPreferences(context, TermuxConstants.TERMUX_DEFAULT_PREFERENCES_FILE_BASENAME_WITHOUT_EXTENSION)
        );
    }

    public TermuxAppSharedPreferences(@NonNull Context context, @NonNull SharedPreferences sharedPreferences, @Nullable SharedPreferences multiProcessSharedPreferences) {
        super(context, sharedPreferences, multiProcessSharedPreferences);
        setFontVariables(context);
    }

    /**
     * Get {@link TermuxAppSharedPreferences}.
     *
     * @param context The {@link Context} to use to get the {@link Context} of the
     *                {@link TermuxConstants#TERMUX_PACKAGE_NAME}.
     * @return Returns the {@link TermuxAppSharedPreferences}. This will {@code null} if an exception is raised.
     */
    @Nullable
    public static TermuxAppSharedPreferences build(@NonNull final Context context) {
        Context termuxPackageContext = PackageUtils.getContextForPackage(context, TermuxConstants.TERMUX_PACKAGE_NAME);
        if (termuxPackageContext == null)
            return null;
        else
            return new TermuxAppSharedPreferences(termuxPackageContext);
    }

    /**
     * Get {@link TermuxAppSharedPreferences}.
     *
     * @param context The {@link Context} to use to get the {@link Context} of the
     *                {@link TermuxConstants#TERMUX_PACKAGE_NAME}.
     * @param exitAppOnError If {@code true} and failed to get package context, then a dialog will
     *                       be shown which when dismissed will exit the app.
     * @return Returns the {@link TermuxAppSharedPreferences}. This will {@code null} if an exception is raised.
     */
    public static TermuxAppSharedPreferences build(@NonNull final Context context, final boolean exitAppOnError) {
        Context termuxPackageContext = TermuxUtils.getContextForPackageOrExitApp(context, TermuxConstants.TERMUX_PACKAGE_NAME, exitAppOnError);
        if (termuxPackageContext == null)
            return null;
        else
            return new TermuxAppSharedPreferences(termuxPackageContext);
    }

    public boolean shouldShowTerminalToolbar() {
        return SharedPreferenceUtils.getBoolean(mSharedPreferences, TERMUX_APP.KEY_SHOW_TERMINAL_TOOLBAR, TERMUX_APP.DEFAULT_VALUE_SHOW_TERMINAL_TOOLBAR);
    }

    public void setShowTerminalToolbar(boolean value) {
        SharedPreferenceUtils.setBoolean(mSharedPreferences, TERMUX_APP.KEY_SHOW_TERMINAL_TOOLBAR, value, false);
    }

    public boolean toogleShowTerminalToolbar() {
        boolean currentValue = shouldShowTerminalToolbar();
        setShowTerminalToolbar(!currentValue);
        return !currentValue;
    }

    public int getAppLauncherButtonCount() {
        int buttonCount = SharedPreferenceUtils.getIntStoredAsString(mSharedPreferences, TERMUX_APP.KEY_APP_LAUNCHER_BUTTON_COUNT, TERMUX_APP.DEFAULT_APP_LAUNCHER_BUTTON_COUNT);
        return DataUtils.clamp(buttonCount, 1, 20);
    }

    public void setAppLauncherButtonCount(int value) {
        SharedPreferenceUtils.setIntStoredAsString(mSharedPreferences,
            TERMUX_APP.KEY_APP_LAUNCHER_BUTTON_COUNT, DataUtils.clamp(value, 1, 20), false);
    }

    public String getAppLauncherInputChar() {
        String value = SharedPreferenceUtils.getString(
            mSharedPreferences,
            TERMUX_APP.KEY_APP_LAUNCHER_INPUT_CHAR,
            TERMUX_APP.DEFAULT_APP_LAUNCHER_INPUT_CHAR,
            true
        );
        String normalized = normalizeAppLauncherInputChar(value);
        if (!normalized.equals(value)) {
            value = normalized;
            SharedPreferenceUtils.setString(mSharedPreferences, TERMUX_APP.KEY_APP_LAUNCHER_INPUT_CHAR, value, true);
        }
        return value;
    }

    public void setAppLauncherInputChar(String value) {
        value = normalizeAppLauncherInputChar(value);
        SharedPreferenceUtils.setString(mSharedPreferences, TERMUX_APP.KEY_APP_LAUNCHER_INPUT_CHAR, value, false);
    }

    public String getAppLauncherDefaultButtons() {
        return SharedPreferenceUtils.getString(mSharedPreferences, TERMUX_APP.KEY_APP_LAUNCHER_DEFAULT_BUTTONS, TERMUX_APP.DEFAULT_APP_LAUNCHER_DEFAULT_BUTTONS, true);
    }

    public void setAppLauncherDefaultButtons(String value) {
        SharedPreferenceUtils.setString(mSharedPreferences, TERMUX_APP.KEY_APP_LAUNCHER_DEFAULT_BUTTONS, value, false);
    }

    public float getAppLauncherBarHeightScale() {
        float heightScale = SharedPreferenceUtils.getFloat(mSharedPreferences, TERMUX_APP.KEY_APP_LAUNCHER_BAR_HEIGHT, TERMUX_APP.DEFAULT_APP_LAUNCHER_BAR_HEIGHT);
        return DataUtils.rangedOrDefault(heightScale, TERMUX_APP.DEFAULT_APP_LAUNCHER_BAR_HEIGHT, 0.4f, 3.0f);
    }

    public void setAppLauncherBarHeightScale(float value) {
        SharedPreferenceUtils.setFloat(mSharedPreferences, TERMUX_APP.KEY_APP_LAUNCHER_BAR_HEIGHT,
            Math.max(0.4f, Math.min(3.0f, value)), false);
    }

    public String getAppLauncherDockStyle() {
        String value = SharedPreferenceUtils.getString(
            mSharedPreferences,
            TERMUX_APP.KEY_APP_LAUNCHER_DOCK_STYLE,
            TERMUX_APP.DEFAULT_APP_LAUNCHER_DOCK_STYLE,
            true
        );
        return normalizeAppLauncherDockStyle(value);
    }

    public void setAppLauncherDockStyle(String value) {
        SharedPreferenceUtils.setString(
            mSharedPreferences,
            TERMUX_APP.KEY_APP_LAUNCHER_DOCK_STYLE,
            normalizeAppLauncherDockStyle(value),
            false
        );
    }

    public boolean isAppLauncherDisplayAppNamesEnabled() {
        return SharedPreferenceUtils.getBoolean(mSharedPreferences, TERMUX_APP.KEY_APP_LAUNCHER_DISPLAY_APP_NAMES,
            TERMUX_APP.DEFAULT_APP_LAUNCHER_DISPLAY_APP_NAMES);
    }

    public void setAppLauncherDisplayAppNamesEnabled(boolean value) {
        SharedPreferenceUtils.setBoolean(mSharedPreferences, TERMUX_APP.KEY_APP_LAUNCHER_DISPLAY_APP_NAMES, value, false);
    }

    public boolean isAppLauncherBwIconsEnabled() {
        return SharedPreferenceUtils.getBoolean(mSharedPreferences, TERMUX_APP.KEY_APP_LAUNCHER_BW_ICONS, TERMUX_APP.DEFAULT_APP_LAUNCHER_BW_ICONS);
    }

    public void setAppLauncherBwIconsEnabled(boolean value) {
        SharedPreferenceUtils.setBoolean(mSharedPreferences, TERMUX_APP.KEY_APP_LAUNCHER_BW_ICONS, value, false);
    }

    public boolean isAppLauncherUnifyIconsEnabled() {
        return SharedPreferenceUtils.getBoolean(mSharedPreferences, TERMUX_APP.KEY_APP_LAUNCHER_UNIFY_ICONS, TERMUX_APP.DEFAULT_APP_LAUNCHER_UNIFY_ICONS);
    }

    public void setAppLauncherUnifyIconsEnabled(boolean value) {
        SharedPreferenceUtils.setBoolean(mSharedPreferences, TERMUX_APP.KEY_APP_LAUNCHER_UNIFY_ICONS, value, false);
    }

    public boolean isAppLauncherIconShadowEnabled() {
        return SharedPreferenceUtils.getBoolean(mSharedPreferences, TERMUX_APP.KEY_APP_LAUNCHER_ICON_SHADOW, TERMUX_APP.DEFAULT_APP_LAUNCHER_ICON_SHADOW);
    }

    public void setAppLauncherIconShadowEnabled(boolean value) {
        SharedPreferenceUtils.setBoolean(mSharedPreferences, TERMUX_APP.KEY_APP_LAUNCHER_ICON_SHADOW, value, false);
    }

    public String getAppLauncherIconPackPackage() {
        return SharedPreferenceUtils.getString(mSharedPreferences, TERMUX_APP.KEY_APP_LAUNCHER_ICON_PACK_PACKAGE,
            TERMUX_APP.DEFAULT_APP_LAUNCHER_ICON_PACK_PACKAGE, true);
    }

    public void setAppLauncherIconPackPackage(String value) {
        SharedPreferenceUtils.setString(mSharedPreferences, TERMUX_APP.KEY_APP_LAUNCHER_ICON_PACK_PACKAGE, value == null ? "" : value, true);
    }

    public String getAppLauncherPinnedIconPackPackage() {
        return SharedPreferenceUtils.getString(mSharedPreferences, TERMUX_APP.KEY_APP_LAUNCHER_PINNED_ICON_PACK_PACKAGE,
            TERMUX_APP.DEFAULT_APP_LAUNCHER_PINNED_ICON_PACK_PACKAGE, true);
    }

    public void setAppLauncherPinnedIconPackPackage(String value) {
        SharedPreferenceUtils.setString(mSharedPreferences, TERMUX_APP.KEY_APP_LAUNCHER_PINNED_ICON_PACK_PACKAGE, value == null ? "" : value, true);
    }

    /** @deprecated Dock icon scale is derived from dock size so geometry has one source of truth. */
    @Deprecated
    public float getAppLauncherIconScale() {
        float iconScale = SharedPreferenceUtils.getFloat(mSharedPreferences, TERMUX_APP.KEY_APP_LAUNCHER_ICON_SCALE, TERMUX_APP.DEFAULT_APP_LAUNCHER_ICON_SCALE);
        return DataUtils.rangedOrDefault(iconScale, TERMUX_APP.DEFAULT_APP_LAUNCHER_ICON_SCALE, 1.0f, 1.8f);
    }

    /** @deprecated Retained only for compatibility with older stored preferences. */
    @Deprecated
    public void setAppLauncherIconScale(float value) {
        float clamped = Math.max(1.0f, Math.min(1.8f, value));
        SharedPreferenceUtils.setFloat(mSharedPreferences, TERMUX_APP.KEY_APP_LAUNCHER_ICON_SCALE, clamped, false);
    }

    public String getAppLauncherPinnedItemsV2() {
        return SharedPreferenceUtils.getString(mSharedPreferences, TERMUX_APP.KEY_APP_LAUNCHER_PINNED_ITEMS_V2,
            TERMUX_APP.DEFAULT_APP_LAUNCHER_PINNED_ITEMS_V2, true);
    }

    public void setAppLauncherPinnedItemsV2(String value) {
        SharedPreferenceUtils.setString(mSharedPreferences, TERMUX_APP.KEY_APP_LAUNCHER_PINNED_ITEMS_V2, value, true);
    }

    public int getAppLauncherPinnedItemsSchemaVersion() {
        return SharedPreferenceUtils.getInt(mSharedPreferences, TERMUX_APP.KEY_APP_LAUNCHER_PINNED_ITEMS_SCHEMA_VERSION,
            TERMUX_APP.DEFAULT_APP_LAUNCHER_PINNED_ITEMS_SCHEMA_VERSION);
    }

    public void setAppLauncherPinnedItemsSchemaVersion(int version) {
        SharedPreferenceUtils.setInt(mSharedPreferences, TERMUX_APP.KEY_APP_LAUNCHER_PINNED_ITEMS_SCHEMA_VERSION, version, true);
    }

    public boolean isAppLauncherAppsRowEnabled() {
        return SharedPreferenceUtils.getBoolean(mSharedPreferences, TERMUX_APP.KEY_APP_LAUNCHER_APPS_ROW_ENABLED,
            TERMUX_APP.DEFAULT_APP_LAUNCHER_APPS_ROW_ENABLED);
    }

    public void setAppLauncherAppsRowEnabled(boolean value) {
        SharedPreferenceUtils.setBoolean(mSharedPreferences, TERMUX_APP.KEY_APP_LAUNCHER_APPS_ROW_ENABLED, value, false);
        if (!value) {
            setAppLauncherAzRowEnabled(false);
        }
    }

    public boolean isAppLauncherNotificationDotsEnabled() {
        return isAppLauncherAppsRowEnabled() && SharedPreferenceUtils.getBoolean(
            mSharedPreferences,
            TERMUX_APP.KEY_APP_LAUNCHER_NOTIFICATION_DOTS,
            TERMUX_APP.DEFAULT_APP_LAUNCHER_NOTIFICATION_DOTS
        );
    }

    public void setAppLauncherNotificationDotsEnabled(boolean value) {
        SharedPreferenceUtils.setBoolean(mSharedPreferences, TERMUX_APP.KEY_APP_LAUNCHER_NOTIFICATION_DOTS, value, false);
    }

    public boolean isAppLauncherMostUsedPageEnabled() {
        return isAppLauncherAppsRowEnabled() && SharedPreferenceUtils.getBoolean(
            mSharedPreferences,
            TERMUX_APP.KEY_APP_LAUNCHER_MOST_USED_PAGE,
            TERMUX_APP.DEFAULT_APP_LAUNCHER_MOST_USED_PAGE
        );
    }

    public void setAppLauncherMostUsedPageEnabled(boolean value) {
        SharedPreferenceUtils.setBoolean(mSharedPreferences, TERMUX_APP.KEY_APP_LAUNCHER_MOST_USED_PAGE, value, false);
    }

    public boolean isAppLauncherAzRowEnabled() {
        return isAppLauncherAppsRowEnabled() && SharedPreferenceUtils.getBoolean(mSharedPreferences, TERMUX_APP.KEY_APP_LAUNCHER_AZ_ROW_ENABLED,
            TERMUX_APP.DEFAULT_APP_LAUNCHER_AZ_ROW_ENABLED);
    }

    public void setAppLauncherAzRowEnabled(boolean value) {
        SharedPreferenceUtils.setBoolean(mSharedPreferences, TERMUX_APP.KEY_APP_LAUNCHER_AZ_ROW_ENABLED, value, false);
    }

    public boolean isAppLauncherAzDoubleTapLockEnabled() {
        return TERMUX_APP.APP_LAUNCHER_AZ_LOCK_METHOD_SHIZUKU.equals(getAppLauncherAzLockMethod());
    }

    public void setAppLauncherAzDoubleTapLockEnabled(boolean value) {
        SharedPreferenceUtils.setBoolean(mSharedPreferences, TERMUX_APP.KEY_APP_LAUNCHER_AZ_DOUBLE_TAP_LOCK, value, false);
        setAppLauncherAzLockMethod(value
            ? TERMUX_APP.APP_LAUNCHER_AZ_LOCK_METHOD_SHIZUKU
            : TERMUX_APP.APP_LAUNCHER_AZ_LOCK_METHOD_OFF);
    }

    public String getAppLauncherAzLockMethod() {
        migrateAppLauncherAzLockMethodIfNeeded();
        String value = SharedPreferenceUtils.getString(
            mSharedPreferences,
            TERMUX_APP.KEY_APP_LAUNCHER_AZ_LOCK_METHOD,
            TERMUX_APP.DEFAULT_APP_LAUNCHER_AZ_LOCK_METHOD,
            true
        );
        return normalizeAppLauncherAzLockMethod(value);
    }

    public void setAppLauncherAzLockMethod(String value) {
        SharedPreferenceUtils.setString(
            mSharedPreferences,
            TERMUX_APP.KEY_APP_LAUNCHER_AZ_LOCK_METHOD,
            normalizeAppLauncherAzLockMethod(value),
            false
        );
    }

    private void migrateAppLauncherAzLockMethodIfNeeded() {
        if (mSharedPreferences == null || mSharedPreferences.contains(TERMUX_APP.KEY_APP_LAUNCHER_AZ_LOCK_METHOD)) {
            return;
        }
        boolean legacyEnabled = SharedPreferenceUtils.getBoolean(
            mSharedPreferences,
            TERMUX_APP.KEY_APP_LAUNCHER_AZ_DOUBLE_TAP_LOCK,
            TERMUX_APP.DEFAULT_APP_LAUNCHER_AZ_DOUBLE_TAP_LOCK
        );
        SharedPreferenceUtils.setString(
            mSharedPreferences,
            TERMUX_APP.KEY_APP_LAUNCHER_AZ_LOCK_METHOD,
            legacyEnabled
                ? TERMUX_APP.APP_LAUNCHER_AZ_LOCK_METHOD_SHIZUKU
                : TERMUX_APP.APP_LAUNCHER_AZ_LOCK_METHOD_OFF,
            true
        );
    }

    public static String normalizeAppLauncherAzLockMethod(@Nullable String value) {
        if (value == null) {
            return TERMUX_APP.DEFAULT_APP_LAUNCHER_AZ_LOCK_METHOD;
        }
        switch (value.trim().toLowerCase()) {
            case TERMUX_APP.APP_LAUNCHER_AZ_LOCK_METHOD_SHIZUKU:
                return TERMUX_APP.APP_LAUNCHER_AZ_LOCK_METHOD_SHIZUKU;
            case TERMUX_APP.APP_LAUNCHER_AZ_LOCK_METHOD_ACCESSIBILITY:
                return TERMUX_APP.APP_LAUNCHER_AZ_LOCK_METHOD_ACCESSIBILITY;
            case TERMUX_APP.APP_LAUNCHER_AZ_LOCK_METHOD_OFF:
            default:
                return TERMUX_APP.APP_LAUNCHER_AZ_LOCK_METHOD_OFF;
        }
    }

    public static String normalizeAppLauncherDockStyle(@Nullable String value) {
        if (value == null) {
            return TERMUX_APP.DEFAULT_APP_LAUNCHER_DOCK_STYLE;
        }
        switch (value) {
            case TERMUX_APP.APP_LAUNCHER_DOCK_STYLE_VALARIE_CAPSULE:
                return TERMUX_APP.APP_LAUNCHER_DOCK_STYLE_VALARIE_CAPSULE;
            case TERMUX_APP.APP_LAUNCHER_DOCK_STYLE_DEFAULT:
            default:
                return TERMUX_APP.APP_LAUNCHER_DOCK_STYLE_DEFAULT;
        }
    }

    public boolean isAppLauncherAnimationsEnabled() {
        return SharedPreferenceUtils.getBoolean(mSharedPreferences, TERMUX_APP.KEY_APP_LAUNCHER_ANIMATIONS_ENABLED,
            TERMUX_APP.DEFAULT_APP_LAUNCHER_ANIMATIONS_ENABLED);
    }

    public void setAppLauncherAnimationsEnabled(boolean value) {
        SharedPreferenceUtils.setBoolean(mSharedPreferences, TERMUX_APP.KEY_APP_LAUNCHER_ANIMATIONS_ENABLED, value, false);
    }

    public boolean isAppLauncherAnimationSafeMode() {
        return SharedPreferenceUtils.getBoolean(mSharedPreferences, TERMUX_APP.KEY_APP_LAUNCHER_ANIMATION_SAFE_MODE,
            TERMUX_APP.DEFAULT_APP_LAUNCHER_ANIMATION_SAFE_MODE);
    }

    public void setAppLauncherAnimationSafeMode(boolean value) {
        SharedPreferenceUtils.setBoolean(mSharedPreferences, TERMUX_APP.KEY_APP_LAUNCHER_ANIMATION_SAFE_MODE, value, false);
    }

    public boolean isTerminalMarginAdjustmentEnabled() {
        return SharedPreferenceUtils.getBoolean(mSharedPreferences, TERMUX_APP.KEY_TERMINAL_MARGIN_ADJUSTMENT, TERMUX_APP.DEFAULT_TERMINAL_MARGIN_ADJUSTMENT);
    }

    public void setTerminalMarginAdjustment(boolean value) {
        SharedPreferenceUtils.setBoolean(mSharedPreferences, TERMUX_APP.KEY_TERMINAL_MARGIN_ADJUSTMENT, value, false);
    }

    public void migrateTerminalMarginAdjustmentDefaultIfNeeded() {
        if (mSharedPreferences == null)
            return;

        boolean migrationDone = SharedPreferenceUtils.getBoolean(
            mSharedPreferences,
            TERMUX_APP.KEY_TERMINAL_MARGIN_ADJUSTMENT_DEFAULT_MIGRATION_DONE,
            TERMUX_APP.DEFAULT_TERMINAL_MARGIN_ADJUSTMENT_DEFAULT_MIGRATION_DONE
        );
        if (migrationDone)
            return;

        boolean hasStoredValue = mSharedPreferences.contains(TERMUX_APP.KEY_TERMINAL_MARGIN_ADJUSTMENT);
        boolean currentEnabled = isTerminalMarginAdjustmentEnabled();
        if (shouldEnableTerminalMarginAdjustmentOnMigration(migrationDone, hasStoredValue, currentEnabled)) {
            SharedPreferenceUtils.setBoolean(
                mSharedPreferences,
                TERMUX_APP.KEY_TERMINAL_MARGIN_ADJUSTMENT,
                true,
                true
            );
        }

        SharedPreferenceUtils.setBoolean(
            mSharedPreferences,
            TERMUX_APP.KEY_TERMINAL_MARGIN_ADJUSTMENT_DEFAULT_MIGRATION_DONE,
            true,
            true
        );
    }

    public static String normalizeAppLauncherInputChar(@Nullable String value) {
        if (value == null || value.trim().isEmpty()) {
            return TERMUX_APP.DEFAULT_APP_LAUNCHER_INPUT_CHAR;
        }
        return value;
    }

    public static boolean shouldEnableTerminalMarginAdjustmentOnMigration(boolean migrationDone, boolean hasStoredValue, boolean currentlyEnabled) {
        return !migrationDone && (!hasStoredValue || !currentlyEnabled);
    }

    public boolean isSoftKeyboardEnabled() {
        return SharedPreferenceUtils.getBoolean(mSharedPreferences, TERMUX_APP.KEY_SOFT_KEYBOARD_ENABLED, TERMUX_APP.DEFAULT_VALUE_KEY_SOFT_KEYBOARD_ENABLED);
    }

    public void setSoftKeyboardEnabled(boolean value) {
        SharedPreferenceUtils.setBoolean(mSharedPreferences, TERMUX_APP.KEY_SOFT_KEYBOARD_ENABLED, value, false);
    }

    public boolean isInAppKeyboardEnabled() {
        return SharedPreferenceUtils.getBoolean(mSharedPreferences, TERMUX_APP.KEY_IN_APP_KEYBOARD_ENABLED, TERMUX_APP.DEFAULT_IN_APP_KEYBOARD_ENABLED);
    }

    public void setInAppKeyboardEnabled(boolean value) {
        SharedPreferenceUtils.setBoolean(mSharedPreferences, TERMUX_APP.KEY_IN_APP_KEYBOARD_ENABLED, value, false);
    }

    public String getInAppKeyboardTheme() {
        String value = SharedPreferenceUtils.getString(
            mSharedPreferences,
            TERMUX_APP.KEY_IN_APP_KEYBOARD_THEME,
            TERMUX_APP.DEFAULT_IN_APP_KEYBOARD_THEME,
            true
        );
        if (isValidInAppKeyboardTheme(value))
            return value;
        return TERMUX_APP.DEFAULT_IN_APP_KEYBOARD_THEME;
    }

    public void setInAppKeyboardTheme(String value) {
        if (!isValidInAppKeyboardTheme(value))
            value = TERMUX_APP.DEFAULT_IN_APP_KEYBOARD_THEME;
        SharedPreferenceUtils.setString(mSharedPreferences, TERMUX_APP.KEY_IN_APP_KEYBOARD_THEME, value, false);
    }

    public String getInAppKeyboardColorScheme() {
        String value = SharedPreferenceUtils.getString(mSharedPreferences,
            TERMUX_APP.KEY_IN_APP_KEYBOARD_COLOR_SCHEME,
            TERMUX_APP.DEFAULT_IN_APP_KEYBOARD_COLOR_SCHEME, true);
        return value == null ? TERMUX_APP.DEFAULT_IN_APP_KEYBOARD_COLOR_SCHEME : value;
    }

    public void setInAppKeyboardColorScheme(String value) {
        SharedPreferenceUtils.setString(mSharedPreferences,
            TERMUX_APP.KEY_IN_APP_KEYBOARD_COLOR_SCHEME,
            value == null ? TERMUX_APP.DEFAULT_IN_APP_KEYBOARD_COLOR_SCHEME : value, false);
    }

    private static boolean isValidInAppKeyboardTheme(String value) {
        if (value == null) return false;
        switch (value) {
            case "system":
            case "light":
            case "dark":
            case "black":
            case "steel_teal":
            case "mint_fuji":
            case "neon_nightfall":
            case "sakura_wood":
            case "ink_plum":
                return true;
            default:
                return false;
        }
    }

    /** Dock-match mode: {@code none}, {@code shape}, {@code glass}, or {@code both}. */
    public String getInAppKeyboardDockMatch() {
        String value = SharedPreferenceUtils.getString(
            mSharedPreferences,
            TERMUX_APP.KEY_IN_APP_KEYBOARD_DOCK_MATCH,
            TERMUX_APP.DEFAULT_IN_APP_KEYBOARD_DOCK_MATCH,
            true
        );
        if (isValidInAppKeyboardDockMatch(value))
            return value;
        return TERMUX_APP.DEFAULT_IN_APP_KEYBOARD_DOCK_MATCH;
    }

    public void setInAppKeyboardDockMatch(String value) {
        if (!isValidInAppKeyboardDockMatch(value))
            value = TERMUX_APP.DEFAULT_IN_APP_KEYBOARD_DOCK_MATCH;
        SharedPreferenceUtils.setString(mSharedPreferences,
            TERMUX_APP.KEY_IN_APP_KEYBOARD_DOCK_MATCH, value, false);
    }

    private static boolean isValidInAppKeyboardDockMatch(String value) {
        if (value == null) return false;
        switch (value) {
            case "none":
            case "shape":
            case "glass":
            case "both":
                return true;
            default:
                return false;
        }
    }

    public boolean isInAppKeyboardHapticsEnabled() {
        return SharedPreferenceUtils.getBoolean(mSharedPreferences,
            TERMUX_APP.KEY_IN_APP_KEYBOARD_HAPTICS_ENABLED,
            TERMUX_APP.DEFAULT_IN_APP_KEYBOARD_HAPTICS_ENABLED);
    }

    public void setInAppKeyboardHapticsEnabled(boolean value) {
        SharedPreferenceUtils.setBoolean(mSharedPreferences,
            TERMUX_APP.KEY_IN_APP_KEYBOARD_HAPTICS_ENABLED, value, false);
    }

    public boolean isInAppKeyboardKeySoundEnabled() {
        return SharedPreferenceUtils.getBoolean(mSharedPreferences,
            TERMUX_APP.KEY_IN_APP_KEYBOARD_KEY_SOUND_ENABLED,
            TERMUX_APP.DEFAULT_IN_APP_KEYBOARD_KEY_SOUND_ENABLED);
    }

    public void setInAppKeyboardKeySoundEnabled(boolean value) {
        SharedPreferenceUtils.setBoolean(mSharedPreferences,
            TERMUX_APP.KEY_IN_APP_KEYBOARD_KEY_SOUND_ENABLED, value, false);
    }

    /** Absolute path of the imported label font file, or empty for the default typeface. */
    public String getInAppKeyboardFontPath() {
        String value = SharedPreferenceUtils.getString(
            mSharedPreferences,
            TERMUX_APP.KEY_IN_APP_KEYBOARD_FONT_PATH,
            TERMUX_APP.DEFAULT_IN_APP_KEYBOARD_FONT_PATH,
            true
        );
        return value == null ? TERMUX_APP.DEFAULT_IN_APP_KEYBOARD_FONT_PATH : value;
    }

    public void setInAppKeyboardFontPath(String value) {
        SharedPreferenceUtils.setString(mSharedPreferences,
            TERMUX_APP.KEY_IN_APP_KEYBOARD_FONT_PATH,
            value == null ? TERMUX_APP.DEFAULT_IN_APP_KEYBOARD_FONT_PATH : value, false);
    }

    /** Extra-key names selectable for merging into the in-app keyboard layout, in catalog order. */
    public static final String[] IN_APP_KEYBOARD_EXTRA_KEY_NAMES = {
        "tab", "esc", "capslock", "compose", "home", "end", "page_up", "page_down",
        "copy", "paste", "cut", "selectAll", "undo", "redo",
        "delete_word", "forward_delete_word", "shareText", "pasteAsPlainText",
        "switch_greekmath", "meta", "alt", "superscript", "subscript",
        "f11_placeholder", "f12_placeholder", "menu", "scroll_lock",
        "€", "ß", "£", "§", "†", "ª", "º",
        "accent_aigu", "accent_grave", "accent_circonflexe", "accent_tilde",
        "accent_cedille", "accent_trema", "accent_ring", "accent_caron",
        "accent_macron", "accent_ogonek", "accent_breve", "accent_dot_above",
        "accent_double_aigu", "accent_slash", "accent_bar"
    };

    /**
     * Comma-joined subset of {@link #IN_APP_KEYBOARD_EXTRA_KEY_NAMES} in canonical order (an
     * empty string means "none enabled"), or the
     * {@link TERMUX_APP#DEFAULT_IN_APP_KEYBOARD_EXTRA_KEYS} sentinel when the user never chose
     * a selection and the built-in defaults apply.
     */
    public String getInAppKeyboardExtraKeys() {
        String value = SharedPreferenceUtils.getString(
            mSharedPreferences,
            TERMUX_APP.KEY_IN_APP_KEYBOARD_EXTRA_KEYS,
            TERMUX_APP.DEFAULT_IN_APP_KEYBOARD_EXTRA_KEYS,
            true
        );
        return normalizeInAppKeyboardExtraKeys(value);
    }

    public void setInAppKeyboardExtraKeys(String value) {
        SharedPreferenceUtils.setString(mSharedPreferences,
            TERMUX_APP.KEY_IN_APP_KEYBOARD_EXTRA_KEYS,
            normalizeInAppKeyboardExtraKeys(value), false);
    }

    /**
     * Drops unknown names and rewrites the survivors in canonical catalog order. The
     * never-chose sentinel (and {@code null}) pass through unchanged; an empty string is a
     * valid "none enabled" selection.
     */
    public static String normalizeInAppKeyboardExtraKeys(String value) {
        if (value == null || TERMUX_APP.DEFAULT_IN_APP_KEYBOARD_EXTRA_KEYS.equals(value))
            return TERMUX_APP.DEFAULT_IN_APP_KEYBOARD_EXTRA_KEYS;
        if (value.isEmpty())
            return "";
        java.util.Set<String> selected = new java.util.HashSet<>(
            java.util.Arrays.asList(value.split(",")));
        StringBuilder result = new StringBuilder();
        for (String name : IN_APP_KEYBOARD_EXTRA_KEY_NAMES) {
            if (!selected.contains(name)) continue;
            if (result.length() > 0) result.append(',');
            result.append(name);
        }
        return result.toString();
    }

    public float getInAppKeyboardHeightScale() {
        float defaultValue = getDefaultInAppKeyboardHeightScale();
        float value = SharedPreferenceUtils.getFloat(mSharedPreferences,
            TERMUX_APP.KEY_IN_APP_KEYBOARD_HEIGHT_SCALE,
            defaultValue);
        if (Float.isNaN(value) || Float.isInfinite(value)) return defaultValue;
        return clampInAppKeyboardHeightScale(value);
    }

    public void setInAppKeyboardHeightScale(float value) {
        SharedPreferenceUtils.setFloat(mSharedPreferences,
            TERMUX_APP.KEY_IN_APP_KEYBOARD_HEIGHT_SCALE,
            clampInAppKeyboardHeightScale(value), false);
    }

    public static float clampInAppKeyboardHeightScale(float value) {
        if (Float.isNaN(value) || Float.isInfinite(value))
            return TERMUX_APP.DEFAULT_IN_APP_KEYBOARD_HEIGHT_SCALE;
        return Math.max(TERMUX_APP.MIN_IN_APP_KEYBOARD_HEIGHT_SCALE,
            Math.min(TERMUX_APP.MAX_IN_APP_KEYBOARD_HEIGHT_SCALE, value));
    }

    public float getInAppKeyboardKeyMarginScale() {
        float defaultValue = getDefaultInAppKeyboardKeyMarginScale();
        float value = SharedPreferenceUtils.getFloat(mSharedPreferences,
            TERMUX_APP.KEY_IN_APP_KEYBOARD_KEY_MARGIN_SCALE,
            defaultValue);
        if (Float.isNaN(value) || Float.isInfinite(value)) return defaultValue;
        return clampInAppKeyboardKeyMarginScale(value);
    }

    public void setInAppKeyboardKeyMarginScale(float value) {
        SharedPreferenceUtils.setFloat(mSharedPreferences,
            TERMUX_APP.KEY_IN_APP_KEYBOARD_KEY_MARGIN_SCALE,
            clampInAppKeyboardKeyMarginScale(value), false);
    }

    public static float clampInAppKeyboardKeyMarginScale(float value) {
        if (Float.isNaN(value) || Float.isInfinite(value))
            return TERMUX_APP.DEFAULT_IN_APP_KEYBOARD_KEY_MARGIN_SCALE;
        return Math.max(TERMUX_APP.MIN_IN_APP_KEYBOARD_KEY_MARGIN_SCALE,
            Math.min(TERMUX_APP.MAX_IN_APP_KEYBOARD_KEY_MARGIN_SCALE, value));
    }

    public float getInAppKeyboardKeyCornerRadiusDp() {
        float defaultValue = getDefaultInAppKeyboardKeyCornerRadiusDp();
        float value = SharedPreferenceUtils.getFloat(mSharedPreferences,
            TERMUX_APP.KEY_IN_APP_KEYBOARD_KEY_CORNER_RADIUS_DP,
            defaultValue);
        if (Float.isNaN(value) || Float.isInfinite(value) || value < 0f) return defaultValue;
        return clampInAppKeyboardKeyCornerRadiusDp(value);
    }

    private boolean usesValarieInAppKeyboardDefaults() {
        return TERMUX_APP.APP_LAUNCHER_DOCK_STYLE_VALARIE_CAPSULE.equals(
            getAppLauncherDockStyle());
    }

    private float getDefaultInAppKeyboardHeightScale() {
        return usesValarieInAppKeyboardDefaults()
            ? TERMUX_APP.DEFAULT_VALARIE_IN_APP_KEYBOARD_HEIGHT_SCALE
            : TERMUX_APP.DEFAULT_IN_APP_KEYBOARD_HEIGHT_SCALE;
    }

    private float getDefaultInAppKeyboardKeyMarginScale() {
        return usesValarieInAppKeyboardDefaults()
            ? TERMUX_APP.DEFAULT_VALARIE_IN_APP_KEYBOARD_KEY_MARGIN_SCALE
            : TERMUX_APP.DEFAULT_IN_APP_KEYBOARD_KEY_MARGIN_SCALE;
    }

    private float getDefaultInAppKeyboardKeyCornerRadiusDp() {
        return usesValarieInAppKeyboardDefaults()
            ? TERMUX_APP.DEFAULT_VALARIE_IN_APP_KEYBOARD_KEY_CORNER_RADIUS_DP
            : TERMUX_APP.DEFAULT_IN_APP_KEYBOARD_KEY_CORNER_RADIUS_DP;
    }

    public void setInAppKeyboardKeyCornerRadiusDp(float value) {
        SharedPreferenceUtils.setFloat(mSharedPreferences,
            TERMUX_APP.KEY_IN_APP_KEYBOARD_KEY_CORNER_RADIUS_DP,
            clampInAppKeyboardKeyCornerRadiusDp(value), false);
    }

    public static float clampInAppKeyboardKeyCornerRadiusDp(float value) {
        if (Float.isNaN(value) || Float.isInfinite(value) || value < 0f)
            return TERMUX_APP.DEFAULT_IN_APP_KEYBOARD_KEY_CORNER_RADIUS_DP;
        return Math.min(TERMUX_APP.MAX_IN_APP_KEYBOARD_KEY_CORNER_RADIUS_DP, value);
    }

    public boolean isSoftKeyboardEnabledOnlyIfNoHardware() {
        return SharedPreferenceUtils.getBoolean(mSharedPreferences, TERMUX_APP.KEY_SOFT_KEYBOARD_ENABLED_ONLY_IF_NO_HARDWARE, TERMUX_APP.DEFAULT_VALUE_KEY_SOFT_KEYBOARD_ENABLED_ONLY_IF_NO_HARDWARE);
    }

    public void setSoftKeyboardEnabledOnlyIfNoHardware(boolean value) {
        SharedPreferenceUtils.setBoolean(mSharedPreferences, TERMUX_APP.KEY_SOFT_KEYBOARD_ENABLED_ONLY_IF_NO_HARDWARE, value, false);
    }

    public boolean isRemoveTaskOnActivityFinishEnabled() {
        return SharedPreferenceUtils.getBoolean(mSharedPreferences, TERMUX_APP.KEY_ACTIVITY_FINISH_REMOVE_TASK, TERMUX_APP.DEFAULT_VALUE_KEY_ACTIVITY_FINISH_REMOVE_TASK);
    }

    public void setRemoveTaskOnActivityFinishEnabled(boolean value) {
        SharedPreferenceUtils.setBoolean(mSharedPreferences, TERMUX_APP.KEY_ACTIVITY_FINISH_REMOVE_TASK, value, false);
    }

    public boolean shouldKeepScreenOn() {
        return SharedPreferenceUtils.getBoolean(mSharedPreferences, TERMUX_APP.KEY_KEEP_SCREEN_ON, TERMUX_APP.DEFAULT_VALUE_KEEP_SCREEN_ON);
    }

    public void setKeepScreenOn(boolean value) {
        SharedPreferenceUtils.setBoolean(mSharedPreferences, TERMUX_APP.KEY_KEEP_SCREEN_ON, value, false);
    }

    public static int[] getDefaultFontSizes(Context context) {
        float dipInPixels = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 1, context.getResources().getDisplayMetrics());
        int[] sizes = new int[3];
        // This is a bit arbitrary and sub-optimal. We want to give a sensible default for minimum font size
        // to prevent invisible text due to zoom be mistake:
        // min
        sizes[1] = (int) (4f * dipInPixels);
        // http://www.google.com/design/spec/style/typography.html#typography-line-height
        int defaultFontSize = Math.round(12 * dipInPixels);
        // Make it divisible by 2 since that is the minimal adjustment step:
        if (defaultFontSize % 2 == 1)
            defaultFontSize--;
        // default
        sizes[0] = defaultFontSize;
        // max
        sizes[2] = 256;
        return sizes;
    }

    public void setFontVariables(Context context) {
        int[] sizes = getDefaultFontSizes(context);
        DEFAULT_FONTSIZE = sizes[0];
        MIN_FONTSIZE = sizes[1];
        MAX_FONTSIZE = sizes[2];
    }

    private String getDisplayIdAsString() {
        Context context = getContext();
        Display display;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            display = context.getDisplay();
        } else {
            display = ((WindowManager) context.getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay();
        }
        int d = display.getDisplayId();
        if (d == Display.DEFAULT_DISPLAY)
            return "";
        else
            return Integer.toString(d);
    }

    public int getFontSize() {
        int fontSize = SharedPreferenceUtils.getIntStoredAsString(mSharedPreferences, TERMUX_APP.KEY_FONTSIZE + getDisplayIdAsString(), DEFAULT_FONTSIZE);
        return DataUtils.clamp(fontSize, MIN_FONTSIZE, MAX_FONTSIZE);
    }

    public void setFontSize(int value) {
        SharedPreferenceUtils.setIntStoredAsString(mSharedPreferences, TERMUX_APP.KEY_FONTSIZE + getDisplayIdAsString(), value, false);
    }

    public void changeFontSize(boolean increase) {
        int fontSize = getFontSize();
        fontSize += (increase ? 1 : -1) * 2;
        fontSize = Math.max(MIN_FONTSIZE, Math.min(fontSize, MAX_FONTSIZE));
        setFontSize(fontSize);
    }

    public String getCurrentSession() {
        return SharedPreferenceUtils.getString(mSharedPreferences, TERMUX_APP.KEY_CURRENT_SESSION, null, true);
    }

    public void setCurrentSession(String value) {
        SharedPreferenceUtils.setString(mSharedPreferences, TERMUX_APP.KEY_CURRENT_SESSION, value, false);
    }

    public int getLogLevel() {
        return SharedPreferenceUtils.getInt(mSharedPreferences, TERMUX_APP.KEY_LOG_LEVEL, Logger.DEFAULT_LOG_LEVEL);
    }

    public void setLogLevel(Context context, int logLevel) {
        logLevel = Logger.setLogLevel(context, logLevel);
        SharedPreferenceUtils.setInt(mSharedPreferences, TERMUX_APP.KEY_LOG_LEVEL, logLevel, false);
    }

    public int getLastNotificationId() {
        return SharedPreferenceUtils.getInt(mSharedPreferences, TERMUX_APP.KEY_LAST_NOTIFICATION_ID, TERMUX_APP.DEFAULT_VALUE_KEY_LAST_NOTIFICATION_ID);
    }

    public void setLastNotificationId(int notificationId) {
        SharedPreferenceUtils.setInt(mSharedPreferences, TERMUX_APP.KEY_LAST_NOTIFICATION_ID, notificationId, false);
    }

    public synchronized int getAndIncrementAppShellNumberSinceBoot() {
        // Keep value at MAX_VALUE on integer overflow and not 0, since not first shell
        return SharedPreferenceUtils.getAndIncrementInt(mSharedPreferences, TERMUX_APP.KEY_APP_SHELL_NUMBER_SINCE_BOOT, TERMUX_APP.DEFAULT_VALUE_APP_SHELL_NUMBER_SINCE_BOOT, true, Integer.MAX_VALUE);
    }

    public synchronized void resetAppShellNumberSinceBoot() {
        SharedPreferenceUtils.setInt(mSharedPreferences, TERMUX_APP.KEY_APP_SHELL_NUMBER_SINCE_BOOT, TERMUX_APP.DEFAULT_VALUE_APP_SHELL_NUMBER_SINCE_BOOT, true);
    }

    public synchronized int getAndIncrementTerminalSessionNumberSinceBoot() {
        // Keep value at MAX_VALUE on integer overflow and not 0, since not first shell
        return SharedPreferenceUtils.getAndIncrementInt(mSharedPreferences, TERMUX_APP.KEY_TERMINAL_SESSION_NUMBER_SINCE_BOOT, TERMUX_APP.DEFAULT_VALUE_TERMINAL_SESSION_NUMBER_SINCE_BOOT, true, Integer.MAX_VALUE);
    }

    public synchronized void resetTerminalSessionNumberSinceBoot() {
        SharedPreferenceUtils.setInt(mSharedPreferences, TERMUX_APP.KEY_TERMINAL_SESSION_NUMBER_SINCE_BOOT, TERMUX_APP.DEFAULT_VALUE_TERMINAL_SESSION_NUMBER_SINCE_BOOT, true);
    }

    public boolean isTerminalViewKeyLoggingEnabled() {
        return SharedPreferenceUtils.getBoolean(mSharedPreferences, TERMUX_APP.KEY_TERMINAL_VIEW_KEY_LOGGING_ENABLED, TERMUX_APP.DEFAULT_VALUE_TERMINAL_VIEW_KEY_LOGGING_ENABLED);
    }

    public void setTerminalViewKeyLoggingEnabled(boolean value) {
        SharedPreferenceUtils.setBoolean(mSharedPreferences, TERMUX_APP.KEY_TERMINAL_VIEW_KEY_LOGGING_ENABLED, value, false);
    }

    public boolean isUseSystemWallpaperEnabled() {
        return SharedPreferenceUtils.getBoolean(mSharedPreferences, TERMUX_APP.KEY_USE_SYSTEM_WALLPAPER, TERMUX_APP.DEFAULT_VALUE_USE_SYSTEM_WALLPAPER);
    }

    public void setUseSystemWallpaperEnabled(boolean value) {
        SharedPreferenceUtils.setBoolean(mSharedPreferences, TERMUX_APP.KEY_USE_SYSTEM_WALLPAPER, value, false);
    }

    public int getTerminalBackgroundOpacity() {
        int opacity = SharedPreferenceUtils.getInt(mSharedPreferences, TERMUX_APP.KEY_TERMINAL_BACKGROUND_OPACITY, TERMUX_APP.DEFAULT_VALUE_TERMINAL_BACKGROUND_OPACITY);
        return DataUtils.clamp(opacity, 0, 100);
    }

    public void setTerminalBackgroundOpacity(int value) {
        int clamped = DataUtils.clamp(value, 0, 100);
        SharedPreferenceUtils.setInt(mSharedPreferences, TERMUX_APP.KEY_TERMINAL_BACKGROUND_OPACITY, clamped, false);
        if (isUseSystemWallpaperEnabled()) {
            setWallpaperEnabledTerminalBackgroundOpacity(clamped);
        }
    }

    public int getSessionsOpacity() {
        int opacity = SharedPreferenceUtils.getInt(mSharedPreferences, TERMUX_APP.KEY_SESSIONS_OPACITY, TERMUX_APP.DEFAULT_VALUE_SESSIONS_OPACITY);
        return DataUtils.clamp(opacity, 0, 100);
    }

    public void setSessionsOpacity(int value) {
        SharedPreferenceUtils.setInt(mSharedPreferences, TERMUX_APP.KEY_SESSIONS_OPACITY, DataUtils.clamp(value, 0, 100), false);
    }

    public int getExtraKeysBlurRadius() {
        int radius = SharedPreferenceUtils.getInt(mSharedPreferences, TERMUX_APP.KEY_EXTRAKEYS_BLUR_RADIUS, TERMUX_APP.DEFAULT_VALUE_EXTRAKEYS_BLUR_RADIUS);
        return DataUtils.clamp(radius, 0, 30);
    }

    public void setExtraKeysBlurRadius(int value) {
        SharedPreferenceUtils.setInt(mSharedPreferences, TERMUX_APP.KEY_EXTRAKEYS_BLUR_RADIUS,
            DataUtils.clamp(value, 0, 30), false);
    }

    public int getDockGlassGrain() {
        int grain = SharedPreferenceUtils.getInt(mSharedPreferences, TERMUX_APP.KEY_DOCK_GLASS_GRAIN, TERMUX_APP.DEFAULT_VALUE_DOCK_GLASS_GRAIN);
        return DataUtils.clamp(grain, 0, 100);
    }

    public void setDockGlassGrain(int value) {
        SharedPreferenceUtils.setInt(mSharedPreferences, TERMUX_APP.KEY_DOCK_GLASS_GRAIN, DataUtils.clamp(value, 0, 100), false);
    }

    public boolean isTerminalFlushDockEnabled() {
        return SharedPreferenceUtils.getBoolean(mSharedPreferences, TERMUX_APP.KEY_TERMINAL_FLUSH_DOCK,
            TERMUX_APP.DEFAULT_VALUE_TERMINAL_FLUSH_DOCK);
    }

    public void setTerminalFlushDockEnabled(boolean value) {
        SharedPreferenceUtils.setBoolean(mSharedPreferences, TERMUX_APP.KEY_TERMINAL_FLUSH_DOCK, value, false);
    }

    public int getAppBarOpacity() {
        int opacity = SharedPreferenceUtils.getInt(mSharedPreferences, TERMUX_APP.KEY_APP_BAR_OPACITY, TERMUX_APP.DEFAULT_VALUE_APP_BAR_OPACITY);
        return DataUtils.clamp(opacity, 0, 100);
    }

    public void setAppBarOpacity(int value) {
        int clamped = DataUtils.clamp(value, 0, 100);
        SharedPreferenceUtils.setInt(mSharedPreferences, TERMUX_APP.KEY_APP_BAR_OPACITY, clamped, false);
        if (isUseSystemWallpaperEnabled()) {
            setWallpaperEnabledAppBarOpacity(clamped);
        }
    }

    public int getWallpaperEnabledTerminalBackgroundOpacity() {
        int opacity = SharedPreferenceUtils.getInt(
            mSharedPreferences,
            TERMUX_APP.KEY_WALLPAPER_ENABLED_TERMINAL_BACKGROUND_OPACITY,
            TERMUX_APP.DEFAULT_VALUE_WALLPAPER_ENABLED_TERMINAL_BACKGROUND_OPACITY
        );
        return DataUtils.clamp(opacity, 0, 100);
    }

    public void setWallpaperEnabledTerminalBackgroundOpacity(int value) {
        SharedPreferenceUtils.setInt(
            mSharedPreferences,
            TERMUX_APP.KEY_WALLPAPER_ENABLED_TERMINAL_BACKGROUND_OPACITY,
            DataUtils.clamp(value, 0, 100),
            false
        );
    }

    public int getWallpaperEnabledAppBarOpacity() {
        int opacity = SharedPreferenceUtils.getInt(
            mSharedPreferences,
            TERMUX_APP.KEY_WALLPAPER_ENABLED_APP_BAR_OPACITY,
            TERMUX_APP.DEFAULT_VALUE_WALLPAPER_ENABLED_APP_BAR_OPACITY
        );
        return DataUtils.clamp(opacity, 0, 100);
    }

    public void setWallpaperEnabledAppBarOpacity(int value) {
        SharedPreferenceUtils.setInt(
            mSharedPreferences,
            TERMUX_APP.KEY_WALLPAPER_ENABLED_APP_BAR_OPACITY,
            DataUtils.clamp(value, 0, 100),
            false
        );
    }

    public int getWallpaperEnabledExtraKeysBlurRadius() {
        int radius = SharedPreferenceUtils.getInt(
            mSharedPreferences,
            TERMUX_APP.KEY_WALLPAPER_ENABLED_EXTRAKEYS_BLUR_RADIUS,
            TERMUX_APP.DEFAULT_VALUE_WALLPAPER_ENABLED_EXTRAKEYS_BLUR_RADIUS
        );
        return Math.max(radius, 0);
    }

    public void setWallpaperEnabledExtraKeysBlurRadius(int value) {
        SharedPreferenceUtils.setInt(
            mSharedPreferences,
            TERMUX_APP.KEY_WALLPAPER_ENABLED_EXTRAKEYS_BLUR_RADIUS,
            Math.max(value, 0),
            false
        );
    }

    public int getManagedWallpaperSystemId() {
        return SharedPreferenceUtils.getInt(
            mSharedPreferences,
            TERMUX_APP.KEY_MANAGED_WALLPAPER_SYSTEM_ID,
            TERMUX_APP.DEFAULT_VALUE_MANAGED_WALLPAPER_SYSTEM_ID
        );
    }

    public void setManagedWallpaperSystemId(int value) {
        SharedPreferenceUtils.setInt(
            mSharedPreferences,
            TERMUX_APP.KEY_MANAGED_WALLPAPER_SYSTEM_ID,
            value,
            false
        );
    }
    
    public boolean isExtraKeysBlurEnabled() {
        return getExtraKeysBlurRadius() > 0;
    }
    
    public void setExtraKeysBlurEnabled(boolean value) {
        setExtraKeysBlurRadius(value ? Math.max(1, getExtraKeysBlurRadius()) : 0);
    }
    
    public boolean isSessionsBlurEnabled() {
        return false;
    }
    
    public void setSessionsBlurEnabled(boolean value) {
        SharedPreferenceUtils.setBoolean(mSharedPreferences, TERMUX_APP.KEY_SESSIONS_BLUR_ENABLED, false, false);
    }
    
    public boolean isMonetBackgroundEnabled() {
        return SharedPreferenceUtils.getBoolean(mSharedPreferences, TERMUX_APP.KEY_MONET_BACKGROUND_ENABLED, TERMUX_APP.DEFAULT_VALUE_MONET_BACKGROUND_ENABLED);
    }
    
    public void setMonetBackgroundEnabled(boolean value) {
        SharedPreferenceUtils.setBoolean(mSharedPreferences, TERMUX_APP.KEY_MONET_BACKGROUND_ENABLED, value, false);
    }

    public boolean isMonetOverlayEnabled() {
        return SharedPreferenceUtils.getBoolean(mSharedPreferences, TERMUX_APP.KEY_MONET_OVERLAY_ENABLED, TERMUX_APP.DEFAULT_VALUE_MONET_OVERLAY_ENABLED);
    }

    public void setMonetOverlayEnabled(boolean value) {
        SharedPreferenceUtils.setBoolean(mSharedPreferences, TERMUX_APP.KEY_MONET_OVERLAY_ENABLED, value, false);
    }

    public boolean isTerminalDynamicColorsEnabled() {
        return SharedPreferenceUtils.getBoolean(mSharedPreferences, TERMUX_APP.KEY_TERMINAL_DYNAMIC_COLORS_ENABLED, TERMUX_APP.DEFAULT_VALUE_TERMINAL_DYNAMIC_COLORS_ENABLED);
    }

    public void setTerminalDynamicColorsEnabled(boolean value) {
        SharedPreferenceUtils.setBoolean(mSharedPreferences, TERMUX_APP.KEY_TERMINAL_DYNAMIC_COLORS_ENABLED, value, false);
    }

    public boolean arePluginErrorNotificationsEnabled(boolean readFromFile) {
        if (readFromFile)
            return SharedPreferenceUtils.getBoolean(mMultiProcessSharedPreferences, TERMUX_APP.KEY_PLUGIN_ERROR_NOTIFICATIONS_ENABLED, TERMUX_APP.DEFAULT_VALUE_PLUGIN_ERROR_NOTIFICATIONS_ENABLED);
        else
            return SharedPreferenceUtils.getBoolean(mSharedPreferences, TERMUX_APP.KEY_PLUGIN_ERROR_NOTIFICATIONS_ENABLED, TERMUX_APP.DEFAULT_VALUE_PLUGIN_ERROR_NOTIFICATIONS_ENABLED);
    }

    public void setPluginErrorNotificationsEnabled(boolean value) {
        SharedPreferenceUtils.setBoolean(mSharedPreferences, TERMUX_APP.KEY_PLUGIN_ERROR_NOTIFICATIONS_ENABLED, value, false);
    }

    public boolean areCrashReportNotificationsEnabled(boolean readFromFile) {
        if (readFromFile)
            return SharedPreferenceUtils.getBoolean(mMultiProcessSharedPreferences, TERMUX_APP.KEY_CRASH_REPORT_NOTIFICATIONS_ENABLED, TERMUX_APP.DEFAULT_VALUE_CRASH_REPORT_NOTIFICATIONS_ENABLED);
        else
            return SharedPreferenceUtils.getBoolean(mSharedPreferences, TERMUX_APP.KEY_CRASH_REPORT_NOTIFICATIONS_ENABLED, TERMUX_APP.DEFAULT_VALUE_CRASH_REPORT_NOTIFICATIONS_ENABLED);
    }

    public void setCrashReportNotificationsEnabled(boolean value) {
        SharedPreferenceUtils.setBoolean(mSharedPreferences, TERMUX_APP.KEY_CRASH_REPORT_NOTIFICATIONS_ENABLED, value, false);
    }
}
