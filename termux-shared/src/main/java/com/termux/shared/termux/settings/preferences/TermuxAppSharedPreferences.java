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

    public String getSurfaceTuningLastSection() {
        return SharedPreferenceUtils.getString(
            mSharedPreferences,
            TERMUX_APP.KEY_SURFACE_TUNING_LAST_SECTION,
            TERMUX_APP.DEFAULT_SURFACE_TUNING_LAST_SECTION,
            true
        );
    }

    public void setSurfaceTuningLastSection(String value) {
        SharedPreferenceUtils.setString(
            mSharedPreferences,
            TERMUX_APP.KEY_SURFACE_TUNING_LAST_SECTION,
            value,
            false
        );
    }

    public int getAppLauncherDockCornerRadius() {
        int value = resolveSurfaceValue(SurfaceSlot.DOCK, SurfaceProperty.CORNER_RADIUS,
            TERMUX_APP.KEY_APP_LAUNCHER_DOCK_CORNER_RADIUS, TERMUX_APP.DEFAULT_APP_LAUNCHER_DOCK_CORNER_RADIUS);
        if (value < 0) return TERMUX_APP.DEFAULT_APP_LAUNCHER_DOCK_CORNER_RADIUS;
        return Math.min(TERMUX_APP.MAX_APP_LAUNCHER_DOCK_CORNER_RADIUS, value);
    }

    public void setAppLauncherDockCornerRadius(int value) {
        writeSurfaceValue(SurfaceSlot.DOCK, SurfaceProperty.CORNER_RADIUS,
            TERMUX_APP.KEY_APP_LAUNCHER_DOCK_CORNER_RADIUS, value < 0 ? TERMUX_APP.DEFAULT_APP_LAUNCHER_DOCK_CORNER_RADIUS
                : Math.min(TERMUX_APP.MAX_APP_LAUNCHER_DOCK_CORNER_RADIUS, value));
    }

    public boolean isAppLauncherDrawerEnabled() {
        return SharedPreferenceUtils.getBoolean(mSharedPreferences, TERMUX_APP.KEY_APP_LAUNCHER_DRAWER_ENABLED,
            TERMUX_APP.DEFAULT_APP_LAUNCHER_DRAWER_ENABLED);
    }

    public void setAppLauncherDrawerEnabled(boolean value) {
        SharedPreferenceUtils.setBoolean(mSharedPreferences, TERMUX_APP.KEY_APP_LAUNCHER_DRAWER_ENABLED, value, false);
    }

    public String getAppLauncherDrawerViewType() {
        return normalizeAppLauncherDrawerViewType(SharedPreferenceUtils.getString(mSharedPreferences,
            TERMUX_APP.KEY_APP_LAUNCHER_DRAWER_VIEW_TYPE,
            TERMUX_APP.DEFAULT_APP_LAUNCHER_DRAWER_VIEW_TYPE, true));
    }

    public void setAppLauncherDrawerViewType(String value) {
        SharedPreferenceUtils.setString(mSharedPreferences, TERMUX_APP.KEY_APP_LAUNCHER_DRAWER_VIEW_TYPE,
            normalizeAppLauncherDrawerViewType(value), false);
    }

    private static String normalizeAppLauncherDrawerViewType(String value) {
        if (TERMUX_APP.APP_LAUNCHER_DRAWER_VIEW_TYPE_HORIZONTAL.equals(value))
            return TERMUX_APP.APP_LAUNCHER_DRAWER_VIEW_TYPE_HORIZONTAL;
        if (TERMUX_APP.APP_LAUNCHER_DRAWER_VIEW_TYPE_CATEGORIES.equals(value))
            return TERMUX_APP.APP_LAUNCHER_DRAWER_VIEW_TYPE_CATEGORIES;
        return TERMUX_APP.APP_LAUNCHER_DRAWER_VIEW_TYPE_VERTICAL;
    }

    public int getAppLauncherDrawerCornerRadius() {
        int value = SharedPreferenceUtils.getInt(mSharedPreferences,
            TERMUX_APP.KEY_APP_LAUNCHER_DRAWER_CORNER_RADIUS,
            TERMUX_APP.DEFAULT_APP_LAUNCHER_DRAWER_CORNER_RADIUS);
        if (value < 0) return TERMUX_APP.DEFAULT_APP_LAUNCHER_DRAWER_CORNER_RADIUS;
        return Math.min(TERMUX_APP.MAX_APP_LAUNCHER_DRAWER_CORNER_RADIUS, value);
    }

    public void setAppLauncherDrawerCornerRadius(int value) {
        SharedPreferenceUtils.setInt(mSharedPreferences,
            TERMUX_APP.KEY_APP_LAUNCHER_DRAWER_CORNER_RADIUS,
            value < 0 ? TERMUX_APP.DEFAULT_APP_LAUNCHER_DRAWER_CORNER_RADIUS
                : Math.min(TERMUX_APP.MAX_APP_LAUNCHER_DRAWER_CORNER_RADIUS, value),
            false);
    }

    public int getStatusBarBlurRadius() {
        return DataUtils.clamp(resolveSurfaceValue(SurfaceSlot.STATUS, SurfaceProperty.BLUR,
            TERMUX_APP.KEY_STATUS_BAR_BLUR_RADIUS, TERMUX_APP.DEFAULT_STATUS_BAR_BLUR_RADIUS), 0, 30);
    }

    public void setStatusBarBlurRadius(int value) {
        writeSurfaceValue(SurfaceSlot.STATUS, SurfaceProperty.BLUR,
            TERMUX_APP.KEY_STATUS_BAR_BLUR_RADIUS, DataUtils.clamp(value, 0, 30));
    }

    public int getStatusBarOpacity() {
        return DataUtils.clamp(resolveSurfaceValue(SurfaceSlot.STATUS, SurfaceProperty.OPACITY,
            TERMUX_APP.KEY_STATUS_BAR_OPACITY, TERMUX_APP.DEFAULT_STATUS_BAR_OPACITY), 0, 100);
    }

    public void setStatusBarOpacity(int value) {
        writeSurfaceValue(SurfaceSlot.STATUS, SurfaceProperty.OPACITY,
            TERMUX_APP.KEY_STATUS_BAR_OPACITY, DataUtils.clamp(value, 0, 100));
    }

    public int getStatusBarGrain() {
        return DataUtils.clamp(resolveSurfaceValue(SurfaceSlot.STATUS, SurfaceProperty.GRAIN,
            TERMUX_APP.KEY_STATUS_BAR_GRAIN, TERMUX_APP.DEFAULT_STATUS_BAR_GRAIN), 0, 100);
    }

    public void setStatusBarGrain(int value) {
        writeSurfaceValue(SurfaceSlot.STATUS, SurfaceProperty.GRAIN,
            TERMUX_APP.KEY_STATUS_BAR_GRAIN, DataUtils.clamp(value, 0, 100));
    }

    public int getStatusBarCornerRadius() {
        int value = resolveSurfaceValue(SurfaceSlot.STATUS, SurfaceProperty.CORNER_RADIUS,
            TERMUX_APP.KEY_STATUS_BAR_CORNER_RADIUS, TERMUX_APP.DEFAULT_STATUS_BAR_CORNER_RADIUS);
        return value < 0 ? TERMUX_APP.DEFAULT_STATUS_BAR_CORNER_RADIUS
            : Math.min(TERMUX_APP.MAX_STATUS_BAR_CORNER_RADIUS, value);
    }

    public void setStatusBarCornerRadius(int value) {
        writeSurfaceValue(SurfaceSlot.STATUS, SurfaceProperty.CORNER_RADIUS,
            TERMUX_APP.KEY_STATUS_BAR_CORNER_RADIUS, value < 0 ? TERMUX_APP.DEFAULT_STATUS_BAR_CORNER_RADIUS
                : Math.min(TERMUX_APP.MAX_STATUS_BAR_CORNER_RADIUS, value));
    }

    /** Wallpaper blur radius (dp) of the terminal's bordered glass pane; 0 disables. */
    /**
     * Docked terminal frame corner radius (dp). Deliberately outside the surface cascade — see
     * {@link TERMUX_APP#KEY_TERMINAL_CORNER_RADIUS} — so it neither detaches from nor follows Base.
     */
    public int getTerminalCornerRadius() {
        return DataUtils.clamp(SharedPreferenceUtils.getInt(mSharedPreferences,
                TERMUX_APP.KEY_TERMINAL_CORNER_RADIUS, TERMUX_APP.DEFAULT_TERMINAL_CORNER_RADIUS),
            0, TERMUX_APP.MAX_TERMINAL_CORNER_RADIUS);
    }

    public void setTerminalCornerRadius(int value) {
        SharedPreferenceUtils.setInt(mSharedPreferences, TERMUX_APP.KEY_TERMINAL_CORNER_RADIUS,
            DataUtils.clamp(value, 0, TERMUX_APP.MAX_TERMINAL_CORNER_RADIUS), false);
    }

    public int getTerminalGlassBlurRadius() {
        // The 30dp ceiling is the terminal's own; a larger inherited Base narrows here rather than
        // leaking a value the pane cannot render.
        return DataUtils.clamp(resolveSurfaceValue(SurfaceSlot.CANVAS, SurfaceProperty.BLUR,
            TERMUX_APP.KEY_TERMINAL_GLASS_BLUR_RADIUS, TERMUX_APP.DEFAULT_TERMINAL_GLASS_BLUR_RADIUS), 0, 30);
    }

    public void setTerminalGlassBlurRadius(int value) {
        writeSurfaceValue(SurfaceSlot.CANVAS, SurfaceProperty.BLUR,
            TERMUX_APP.KEY_TERMINAL_GLASS_BLUR_RADIUS, DataUtils.clamp(value, 0, 30));
    }

    /** Film-grain strength (percent) of the terminal's bordered glass pane; 0 disables. */
    public int getTerminalGlassGrain() {
        return DataUtils.clamp(resolveSurfaceValue(SurfaceSlot.CANVAS, SurfaceProperty.GRAIN,
            TERMUX_APP.KEY_TERMINAL_GLASS_GRAIN, TERMUX_APP.DEFAULT_TERMINAL_GLASS_GRAIN), 0, 100);
    }

    public void setTerminalGlassGrain(int value) {
        writeSurfaceValue(SurfaceSlot.CANVAS, SurfaceProperty.GRAIN,
            TERMUX_APP.KEY_TERMINAL_GLASS_GRAIN, DataUtils.clamp(value, 0, 100));
    }

    /**
     * Opacity of the black backdrop over the wallpaper, behind every surface (percent); 0 leaves
     * the wallpaper undimmed.
     *
     * <p>Supersedes the old {@code wallpaper_backdrop_opacity}, which stored the complement — the
     * wallpaper's own visibility — and so ran backwards under a slider labelled Opacity. A value
     * left over from that key is complemented on read rather than rewritten, so the migration is
     * idempotent and a downgrade still finds its own key intact.
     */
    public int getWallpaperBackdropDim() {
        int stored = SharedPreferenceUtils.getInt(mSharedPreferences,
            TERMUX_APP.KEY_WALLPAPER_BACKDROP_DIM, -1);
        if (stored < 0) {
            int legacy = SharedPreferenceUtils.getInt(mSharedPreferences,
                TERMUX_APP.KEY_WALLPAPER_BACKDROP_OPACITY, -1);
            stored = legacy < 0
                ? TERMUX_APP.DEFAULT_WALLPAPER_BACKDROP_DIM
                : 100 - DataUtils.clamp(legacy, 0, 100);
        }
        return DataUtils.clamp(stored, 0, 100);
    }

    public void setWallpaperBackdropDim(int value) {
        SharedPreferenceUtils.setInt(mSharedPreferences, TERMUX_APP.KEY_WALLPAPER_BACKDROP_DIM,
            DataUtils.clamp(value, 0, 100), false);
    }

    /** Gap between tiled terminal panes, in dp. */
    public int getTerminalPaneGap() {
        return DataUtils.clamp(SharedPreferenceUtils.getInt(mSharedPreferences,
            TERMUX_APP.KEY_TERMINAL_PANE_GAP, TERMUX_APP.DEFAULT_TERMINAL_PANE_GAP),
            0, TERMUX_APP.MAX_TERMINAL_PANE_GAP);
    }

    public void setTerminalPaneGap(int value) {
        SharedPreferenceUtils.setInt(mSharedPreferences, TERMUX_APP.KEY_TERMINAL_PANE_GAP,
            DataUtils.clamp(value, 0, TERMUX_APP.MAX_TERMINAL_PANE_GAP), false);
    }

    public static int clampSurfaceHorizontalInset(int value) {
        return DataUtils.clamp(value, 0, TERMUX_APP.MAX_SURFACE_HORIZONTAL_INSET);
    }

    public int getDockHorizontalInset() {
        return clampSurfaceHorizontalInset(resolveSurfaceValue(SurfaceSlot.DOCK, SurfaceProperty.SIDE_GAP,
            TERMUX_APP.KEY_DOCK_HORIZONTAL_INSET, TERMUX_APP.DEFAULT_SURFACE_HORIZONTAL_INSET));
    }

    public void setDockHorizontalInset(int value) {
        writeSurfaceValue(SurfaceSlot.DOCK, SurfaceProperty.SIDE_GAP,
            TERMUX_APP.KEY_DOCK_HORIZONTAL_INSET, clampSurfaceHorizontalInset(value));
    }

    public int getInAppKeyboardHorizontalInset() {
        return clampSurfaceHorizontalInset(resolveSurfaceValue(SurfaceSlot.KEYBOARD, SurfaceProperty.SIDE_GAP,
            TERMUX_APP.KEY_IN_APP_KEYBOARD_HORIZONTAL_INSET, TERMUX_APP.DEFAULT_IN_APP_KEYBOARD_HORIZONTAL_INSET));
    }

    public void setInAppKeyboardHorizontalInset(int value) {
        writeSurfaceValue(SurfaceSlot.KEYBOARD, SurfaceProperty.SIDE_GAP,
            TERMUX_APP.KEY_IN_APP_KEYBOARD_HORIZONTAL_INSET, clampSurfaceHorizontalInset(value));
    }

    public int getStatusBarHorizontalInset() {
        return clampSurfaceHorizontalInset(resolveSurfaceValue(SurfaceSlot.STATUS, SurfaceProperty.SIDE_GAP,
            TERMUX_APP.KEY_STATUS_BAR_HORIZONTAL_INSET, TERMUX_APP.DEFAULT_SURFACE_HORIZONTAL_INSET));
    }

    public void setStatusBarHorizontalInset(int value) {
        writeSurfaceValue(SurfaceSlot.STATUS, SurfaceProperty.SIDE_GAP,
            TERMUX_APP.KEY_STATUS_BAR_HORIZONTAL_INSET, clampSurfaceHorizontalInset(value));
    }

    /** See {@link TermuxPreferenceConstants.TERMUX_APP#KEY_LAZY_MODE}. */
    public boolean isLazyModeEnabled() {
        return SharedPreferenceUtils.getBoolean(mSharedPreferences,
            TermuxPreferenceConstants.TERMUX_APP.KEY_LAZY_MODE,
            TermuxPreferenceConstants.TERMUX_APP.DEFAULT_VALUE_LAZY_MODE);
    }

    /** See {@link TermuxPreferenceConstants.TERMUX_APP#KEY_LAZY_MODE}. */
    public void setLazyModeEnabled(boolean value) {
        SharedPreferenceUtils.setBoolean(mSharedPreferences,
            TermuxPreferenceConstants.TERMUX_APP.KEY_LAZY_MODE, value, false);
    }

    /** See {@link TermuxPreferenceConstants.TERMUX_APP#KEY_SHOW_KEY_HINTS}. */
    public boolean isShowKeyHintsEnabled() {
        return SharedPreferenceUtils.getBoolean(mSharedPreferences,
            TERMUX_APP.KEY_SHOW_KEY_HINTS, TERMUX_APP.DEFAULT_SHOW_KEY_HINTS);
    }

    public void setShowKeyHintsEnabled(boolean value) {
        SharedPreferenceUtils.setBoolean(mSharedPreferences, TERMUX_APP.KEY_SHOW_KEY_HINTS, value, false);
    }

    public boolean isStatusWidgetCpuEnabled() {
        return SharedPreferenceUtils.getBoolean(mSharedPreferences,
            TERMUX_APP.KEY_STATUS_WIDGET_CPU, TERMUX_APP.DEFAULT_STATUS_WIDGET_CPU);
    }

    public void setStatusWidgetCpuEnabled(boolean value) {
        SharedPreferenceUtils.setBoolean(mSharedPreferences, TERMUX_APP.KEY_STATUS_WIDGET_CPU, value, false);
    }

    public boolean isStatusWidgetRamEnabled() {
        return SharedPreferenceUtils.getBoolean(mSharedPreferences,
            TERMUX_APP.KEY_STATUS_WIDGET_RAM, TERMUX_APP.DEFAULT_STATUS_WIDGET_RAM);
    }

    public void setStatusWidgetRamEnabled(boolean value) {
        SharedPreferenceUtils.setBoolean(mSharedPreferences, TERMUX_APP.KEY_STATUS_WIDGET_RAM, value, false);
    }

    public boolean isStatusWidgetWeatherEnabled() {
        return SharedPreferenceUtils.getBoolean(mSharedPreferences,
            TERMUX_APP.KEY_STATUS_WIDGET_WEATHER, TERMUX_APP.DEFAULT_STATUS_WIDGET_WEATHER);
    }

    public void setStatusWidgetWeatherEnabled(boolean value) {
        SharedPreferenceUtils.setBoolean(mSharedPreferences, TERMUX_APP.KEY_STATUS_WIDGET_WEATHER, value, false);
    }

    public boolean isStatusWidgetWeatherFahrenheit() {
        return SharedPreferenceUtils.getBoolean(mSharedPreferences,
            TERMUX_APP.KEY_STATUS_WIDGET_WEATHER_FAHRENHEIT, TERMUX_APP.DEFAULT_STATUS_WIDGET_WEATHER_FAHRENHEIT);
    }

    public void setStatusWidgetWeatherFahrenheit(boolean value) {
        SharedPreferenceUtils.setBoolean(mSharedPreferences, TERMUX_APP.KEY_STATUS_WIDGET_WEATHER_FAHRENHEIT, value, false);
    }

    public boolean isTerminalCursorTrailEnabled() {
        return SharedPreferenceUtils.getBoolean(mSharedPreferences,
            TERMUX_APP.KEY_TERMINAL_CURSOR_TRAIL, TERMUX_APP.DEFAULT_TERMINAL_CURSOR_TRAIL);
    }

    public void setTerminalCursorTrailEnabled(boolean value) {
        SharedPreferenceUtils.setBoolean(mSharedPreferences, TERMUX_APP.KEY_TERMINAL_CURSOR_TRAIL, value, false);
    }

    public boolean isAppLauncherDisplayAppNamesEnabled() {
        // App names are always shown; no longer user-configurable.
        return true;
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

    /** Commits the normalized launcher payload and its schema marker as one durable transaction. */
    public boolean commitAppLauncherPinnedItems(String value, int version) {
        return mSharedPreferences.edit()
            .putString(TERMUX_APP.KEY_APP_LAUNCHER_PINNED_ITEMS_V2, value)
            .putInt(TERMUX_APP.KEY_APP_LAUNCHER_PINNED_ITEMS_SCHEMA_VERSION, version)
            .commit();
    }

    public boolean isAppLauncherAppsRowEnabled() {
        return SharedPreferenceUtils.getBoolean(mSharedPreferences, TERMUX_APP.KEY_APP_LAUNCHER_APPS_ROW_ENABLED,
            TERMUX_APP.DEFAULT_APP_LAUNCHER_APPS_ROW_ENABLED);
    }

    public void setAppLauncherAppsRowEnabled(boolean value) {
        SharedPreferenceUtils.setBoolean(mSharedPreferences, TERMUX_APP.KEY_APP_LAUNCHER_APPS_ROW_ENABLED, value, false);
    }

    /** @return {@code "left"} or {@code "right"}; anything else stored reads back as the default. */
    public String getAppLauncherDockRailSide() {
        String value = SharedPreferenceUtils.getString(mSharedPreferences,
            TERMUX_APP.KEY_APP_LAUNCHER_DOCK_RAIL_SIDE,
            TERMUX_APP.DEFAULT_APP_LAUNCHER_DOCK_RAIL_SIDE, true);
        return TERMUX_APP.APP_LAUNCHER_DOCK_RAIL_SIDE_RIGHT.equals(value)
            ? TERMUX_APP.APP_LAUNCHER_DOCK_RAIL_SIDE_RIGHT
            : TERMUX_APP.APP_LAUNCHER_DOCK_RAIL_SIDE_LEFT;
    }

    public void setAppLauncherDockRailSide(String value) {
        SharedPreferenceUtils.setString(mSharedPreferences,
            TERMUX_APP.KEY_APP_LAUNCHER_DOCK_RAIL_SIDE,
            TERMUX_APP.APP_LAUNCHER_DOCK_RAIL_SIDE_RIGHT.equals(value)
                ? TERMUX_APP.APP_LAUNCHER_DOCK_RAIL_SIDE_RIGHT
                : TERMUX_APP.APP_LAUNCHER_DOCK_RAIL_SIDE_LEFT, false);
    }

    /** @return true when the landscape rail sits on the right, i.e. the drawer pull runs left. */
    public boolean isAppLauncherDockRailOnRight() {
        return TERMUX_APP.APP_LAUNCHER_DOCK_RAIL_SIDE_RIGHT.equals(getAppLauncherDockRailSide());
    }

    public boolean isAppLauncherExtraKeysRowEnabled() {
        return SharedPreferenceUtils.getBoolean(mSharedPreferences,
            TERMUX_APP.KEY_APP_LAUNCHER_EXTRA_KEYS_ROW_ENABLED,
            TERMUX_APP.DEFAULT_APP_LAUNCHER_EXTRA_KEYS_ROW_ENABLED);
    }

    public void setAppLauncherExtraKeysRowEnabled(boolean value) {
        SharedPreferenceUtils.setBoolean(mSharedPreferences,
            TERMUX_APP.KEY_APP_LAUNCHER_EXTRA_KEYS_ROW_ENABLED, value, false);
    }

    public boolean isAppLauncherWidgetPaneEnabled() {
        return SharedPreferenceUtils.getBoolean(mSharedPreferences,
            TERMUX_APP.KEY_APP_LAUNCHER_WIDGET_PANE_ENABLED,
            TERMUX_APP.DEFAULT_APP_LAUNCHER_WIDGET_PANE_ENABLED);
    }

    public void setAppLauncherWidgetPaneEnabled(boolean value) {
        SharedPreferenceUtils.setBoolean(mSharedPreferences,
            TERMUX_APP.KEY_APP_LAUNCHER_WIDGET_PANE_ENABLED, value, false);
    }

    public String getAppLauncherUseCaseMode() {
        String value = SharedPreferenceUtils.getString(mSharedPreferences,
            TERMUX_APP.KEY_APP_LAUNCHER_USE_CASE_MODE,
            TERMUX_APP.DEFAULT_APP_LAUNCHER_USE_CASE_MODE, true);
        return TERMUX_APP.APP_LAUNCHER_USE_CASE_MODE_TERMINAL.equals(value)
            ? TERMUX_APP.APP_LAUNCHER_USE_CASE_MODE_TERMINAL
            : TERMUX_APP.APP_LAUNCHER_USE_CASE_MODE_LAUNCHER;
    }

    public void setAppLauncherUseCaseMode(String value) {
        SharedPreferenceUtils.setString(mSharedPreferences,
            TERMUX_APP.KEY_APP_LAUNCHER_USE_CASE_MODE,
            TERMUX_APP.APP_LAUNCHER_USE_CASE_MODE_TERMINAL.equals(value)
                ? TERMUX_APP.APP_LAUNCHER_USE_CASE_MODE_TERMINAL
                : TERMUX_APP.APP_LAUNCHER_USE_CASE_MODE_LAUNCHER, false);
    }

    public boolean isTerminalOnlyUseCase() {
        return TERMUX_APP.APP_LAUNCHER_USE_CASE_MODE_TERMINAL.equals(getAppLauncherUseCaseMode());
    }

    public String getAppLauncherUseCaseSnapshot() {
        return SharedPreferenceUtils.getString(mSharedPreferences,
            TERMUX_APP.KEY_APP_LAUNCHER_USE_CASE_SNAPSHOT,
            TERMUX_APP.DEFAULT_APP_LAUNCHER_USE_CASE_SNAPSHOT, true);
    }

    public void setAppLauncherUseCaseSnapshot(String value) {
        SharedPreferenceUtils.setString(mSharedPreferences,
            TERMUX_APP.KEY_APP_LAUNCHER_USE_CASE_SNAPSHOT,
            value == null ? TERMUX_APP.DEFAULT_APP_LAUNCHER_USE_CASE_SNAPSHOT : value, false);
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

    /** See {@link TermuxPreferenceConstants.TERMUX_APP#KEY_APP_LAUNCHER_NOTIFICATION_HISTORY}. */
    public boolean isAppLauncherNotificationHistoryEnabled() {
        return SharedPreferenceUtils.getBoolean(
            mSharedPreferences,
            TERMUX_APP.KEY_APP_LAUNCHER_NOTIFICATION_HISTORY,
            TERMUX_APP.DEFAULT_APP_LAUNCHER_NOTIFICATION_HISTORY
        );
    }

    public void setAppLauncherNotificationHistoryEnabled(boolean value) {
        SharedPreferenceUtils.setBoolean(mSharedPreferences,
            TERMUX_APP.KEY_APP_LAUNCHER_NOTIFICATION_HISTORY, value, false);
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

    /**
     * The A-Z index scrolls the apps row, so it is meaningless on its own: with the apps row off
     * it is a strip of letters that scrubs nothing. Coupled the same way the notification dots and
     * most-used page are, so the stored choice survives the apps row being toggled off and back.
     */
    public boolean isAppLauncherAzRowEnabled() {
        return isAppLauncherAppsRowEnabled() && isAppLauncherAzRowChosen();
    }

    /** The stored A-Z choice, ignoring the apps row coupling — for settings and for snapshots. */
    public boolean isAppLauncherAzRowChosen() {
        return SharedPreferenceUtils.getBoolean(mSharedPreferences,
            TERMUX_APP.KEY_APP_LAUNCHER_AZ_ROW_ENABLED,
            TERMUX_APP.DEFAULT_APP_LAUNCHER_AZ_ROW_ENABLED);
    }

    public void setAppLauncherAzRowEnabled(boolean value) {
        SharedPreferenceUtils.setBoolean(mSharedPreferences, TERMUX_APP.KEY_APP_LAUNCHER_AZ_ROW_ENABLED, value, false);
    }

    public boolean isAppLauncherRowHapticsEnabled() {
        return SharedPreferenceUtils.getBoolean(mSharedPreferences,
            TERMUX_APP.KEY_APP_LAUNCHER_ROW_HAPTICS,
            TERMUX_APP.DEFAULT_APP_LAUNCHER_ROW_HAPTICS);
    }

    public void setAppLauncherRowHapticsEnabled(boolean value) {
        SharedPreferenceUtils.setBoolean(mSharedPreferences,
            TERMUX_APP.KEY_APP_LAUNCHER_ROW_HAPTICS, value, false);
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
            case TERMUX_APP.APP_LAUNCHER_DOCK_STYLE_LEGACY_VALARIE_CAPSULE:
            case TERMUX_APP.APP_LAUNCHER_DOCK_STYLE_ROUNDED:
                return TERMUX_APP.APP_LAUNCHER_DOCK_STYLE_ROUNDED;
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
            case "custom":
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
        float defaultValue = SharedPreferenceUtils.getFloat(mSharedPreferences,
            TERMUX_APP.KEY_IN_APP_KEYBOARD_HEIGHT_SCALE,
            getDefaultInAppKeyboardHeightScale());
        if (Float.isNaN(defaultValue) || Float.isInfinite(defaultValue))
            defaultValue = getDefaultInAppKeyboardHeightScale();
        if (!isLandscapeOrientation())
            return clampInAppKeyboardHeightScale(defaultValue);
        float value = SharedPreferenceUtils.getFloat(mSharedPreferences,
            TERMUX_APP.KEY_IN_APP_KEYBOARD_HEIGHT_SCALE_LANDSCAPE,
            defaultValue);
        if (Float.isNaN(value) || Float.isInfinite(value)) return clampInAppKeyboardHeightScale(defaultValue);
        return clampInAppKeyboardHeightScale(value);
    }

    public void setInAppKeyboardHeightScale(float value) {
        SharedPreferenceUtils.setFloat(mSharedPreferences,
            isLandscapeOrientation()
                ? TERMUX_APP.KEY_IN_APP_KEYBOARD_HEIGHT_SCALE_LANDSCAPE
                : TERMUX_APP.KEY_IN_APP_KEYBOARD_HEIGHT_SCALE,
            clampInAppKeyboardHeightScale(value), false);
    }

    private boolean isLandscapeOrientation() {
        return getContext().getResources().getConfiguration().orientation
            == android.content.res.Configuration.ORIENTATION_LANDSCAPE;
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

    private boolean usesRoundedInAppKeyboardDefaults() {
        return TERMUX_APP.APP_LAUNCHER_DOCK_STYLE_ROUNDED.equals(
            getAppLauncherDockStyle());
    }

    private float getDefaultInAppKeyboardHeightScale() {
        return usesRoundedInAppKeyboardDefaults()
            ? TERMUX_APP.DEFAULT_ROUNDED_IN_APP_KEYBOARD_HEIGHT_SCALE
            : TERMUX_APP.DEFAULT_IN_APP_KEYBOARD_HEIGHT_SCALE;
    }

    private float getDefaultInAppKeyboardKeyMarginScale() {
        return usesRoundedInAppKeyboardDefaults()
            ? TERMUX_APP.DEFAULT_ROUNDED_IN_APP_KEYBOARD_KEY_MARGIN_SCALE
            : TERMUX_APP.DEFAULT_IN_APP_KEYBOARD_KEY_MARGIN_SCALE;
    }

    private float getDefaultInAppKeyboardKeyCornerRadiusDp() {
        return usesRoundedInAppKeyboardDefaults()
            ? TERMUX_APP.DEFAULT_ROUNDED_IN_APP_KEYBOARD_KEY_CORNER_RADIUS_DP
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

    public int getInAppKeyboardKeyOpacity() {
        return clampInAppKeyboardKeyOpacity(SharedPreferenceUtils.getInt(mSharedPreferences,
            TERMUX_APP.KEY_IN_APP_KEYBOARD_KEY_OPACITY,
            TERMUX_APP.DEFAULT_IN_APP_KEYBOARD_KEY_OPACITY));
    }

    public void setInAppKeyboardKeyOpacity(int value) {
        SharedPreferenceUtils.setInt(mSharedPreferences,
            TERMUX_APP.KEY_IN_APP_KEYBOARD_KEY_OPACITY,
            clampInAppKeyboardKeyOpacity(value), false);
    }

    /** Negative values collapse to the -1 "theme-defined" sentinel. */
    public static int clampInAppKeyboardKeyOpacity(int value) {
        if (value < 0) return TERMUX_APP.DEFAULT_IN_APP_KEYBOARD_KEY_OPACITY;
        return Math.min(TERMUX_APP.MAX_IN_APP_KEYBOARD_KEY_OPACITY, value);
    }

    public int getInAppKeyboardBackgroundOpacity() {
        return clampInAppKeyboardBackgroundOpacity(resolveSurfaceValue(SurfaceSlot.KEYBOARD, SurfaceProperty.OPACITY,
            TERMUX_APP.KEY_IN_APP_KEYBOARD_BACKGROUND_OPACITY, TERMUX_APP.DEFAULT_IN_APP_KEYBOARD_BACKGROUND_OPACITY));
    }

    public void setInAppKeyboardBackgroundOpacity(int value) {
        writeSurfaceValue(SurfaceSlot.KEYBOARD, SurfaceProperty.OPACITY,
            TERMUX_APP.KEY_IN_APP_KEYBOARD_BACKGROUND_OPACITY, clampInAppKeyboardBackgroundOpacity(value));
    }

    public static int clampInAppKeyboardBackgroundOpacity(int value) {
        return Math.max(TERMUX_APP.MIN_IN_APP_KEYBOARD_BACKGROUND_OPACITY,
            Math.min(TERMUX_APP.MAX_IN_APP_KEYBOARD_BACKGROUND_OPACITY, value));
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

    public boolean isShowInRecentsWhenNotDefaultEnabled() {
        return SharedPreferenceUtils.getBoolean(mSharedPreferences, TERMUX_APP.KEY_SHOW_IN_RECENTS_WHEN_NOT_DEFAULT, TERMUX_APP.DEFAULT_VALUE_KEY_SHOW_IN_RECENTS_WHEN_NOT_DEFAULT);
    }

    public void setShowInRecentsWhenNotDefaultEnabled(boolean value) {
        SharedPreferenceUtils.setBoolean(mSharedPreferences, TERMUX_APP.KEY_SHOW_IN_RECENTS_WHEN_NOT_DEFAULT, value, false);
    }

    public boolean shouldKeepScreenOn() {
        return SharedPreferenceUtils.getBoolean(mSharedPreferences, TERMUX_APP.KEY_KEEP_SCREEN_ON, TERMUX_APP.DEFAULT_VALUE_KEEP_SCREEN_ON);
    }

    public void setKeepScreenOn(boolean value) {
        SharedPreferenceUtils.setBoolean(mSharedPreferences, TERMUX_APP.KEY_KEEP_SCREEN_ON, value, false);
    }

    public boolean isCompatibilityModeEnabled() {
        return SharedPreferenceUtils.getBoolean(mSharedPreferences, TERMUX_APP.KEY_COMPATIBILITY_MODE, TERMUX_APP.DEFAULT_VALUE_COMPATIBILITY_MODE);
    }

    public void setCompatibilityModeEnabled(boolean value) {
        SharedPreferenceUtils.setBoolean(mSharedPreferences, TERMUX_APP.KEY_COMPATIBILITY_MODE, value, false);
    }

    public String getTopPaneClockStyle() {
        String value = SharedPreferenceUtils.getString(mSharedPreferences,
            TERMUX_APP.KEY_TOP_PANE_CLOCK_STYLE, TERMUX_APP.DEFAULT_TOP_PANE_CLOCK_STYLE, true);
        return normalizeTopPaneClockStyle(value);
    }

    public void setTopPaneClockStyle(String value) {
        SharedPreferenceUtils.setString(mSharedPreferences, TERMUX_APP.KEY_TOP_PANE_CLOCK_STYLE,
            normalizeTopPaneClockStyle(value), false);
    }

    private static String normalizeTopPaneClockStyle(String value) {
        if (TERMUX_APP.TOP_PANE_CLOCK_STYLE_LCD.equals(value)
            || TERMUX_APP.TOP_PANE_CLOCK_STYLE_MINIMAL.equals(value)
            || TERMUX_APP.TOP_PANE_CLOCK_STYLE_LED.equals(value)
            || TERMUX_APP.TOP_PANE_CLOCK_STYLE_TAPE.equals(value)
            || TERMUX_APP.TOP_PANE_CLOCK_STYLE_SLAB.equals(value)) {
            return value;
        }
        return TERMUX_APP.TOP_PANE_CLOCK_STYLE_FLIP;
    }

    public String getTopPaneClockAlignment() {
        String value = SharedPreferenceUtils.getString(mSharedPreferences,
            TERMUX_APP.KEY_TOP_PANE_CLOCK_ALIGNMENT,
            TERMUX_APP.DEFAULT_TOP_PANE_CLOCK_ALIGNMENT, true);
        return normalizeTopPaneClockAlignment(value);
    }

    public void setTopPaneClockAlignment(String value) {
        SharedPreferenceUtils.setString(mSharedPreferences,
            TERMUX_APP.KEY_TOP_PANE_CLOCK_ALIGNMENT,
            normalizeTopPaneClockAlignment(value), false);
    }

    private static String normalizeTopPaneClockAlignment(String value) {
        if (TERMUX_APP.TOP_PANE_CLOCK_ALIGNMENT_CENTER.equals(value)
            || TERMUX_APP.TOP_PANE_CLOCK_ALIGNMENT_RIGHT.equals(value)) {
            return value;
        }
        return TERMUX_APP.TOP_PANE_CLOCK_ALIGNMENT_LEFT;
    }

    public boolean isTopPaneClockAmPmEnabled() {
        return SharedPreferenceUtils.getBoolean(mSharedPreferences,
            TERMUX_APP.KEY_TOP_PANE_CLOCK_AM_PM, TERMUX_APP.DEFAULT_TOP_PANE_CLOCK_AM_PM);
    }

    public void setTopPaneClockAmPmEnabled(boolean value) {
        SharedPreferenceUtils.setBoolean(mSharedPreferences,
            TERMUX_APP.KEY_TOP_PANE_CLOCK_AM_PM, value, false);
    }

    public boolean isTopPaneClockCollapsed() {
        return SharedPreferenceUtils.getBoolean(mSharedPreferences,
            TERMUX_APP.KEY_TOP_PANE_CLOCK_COLLAPSED,
            TERMUX_APP.DEFAULT_TOP_PANE_CLOCK_COLLAPSED);
    }

    public void setTopPaneClockCollapsed(boolean value) {
        SharedPreferenceUtils.setBoolean(mSharedPreferences,
            TERMUX_APP.KEY_TOP_PANE_CLOCK_COLLAPSED, value, false);
    }

    public String getEssentialNotificationRules() {
        return SharedPreferenceUtils.getString(mSharedPreferences,
            TERMUX_APP.KEY_ESSENTIAL_NOTIFICATION_RULES,
            TERMUX_APP.DEFAULT_ESSENTIAL_NOTIFICATION_RULES, true);
    }

    public void setEssentialNotificationRules(String value) {
        SharedPreferenceUtils.setString(mSharedPreferences,
            TERMUX_APP.KEY_ESSENTIAL_NOTIFICATION_RULES,
            value == null || value.isEmpty()
                ? TERMUX_APP.DEFAULT_ESSENTIAL_NOTIFICATION_RULES : value, true);
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
        setFontSize(stepFontSize(getFontSize(), increase));
    }

    /** {@code current} stepped one zoom increment, clamped to this display's font size limits. */
    public int stepFontSize(int current, boolean increase) {
        int fontSize = current + (increase ? 1 : -1) * 2;
        return Math.max(MIN_FONTSIZE, Math.min(fontSize, MAX_FONTSIZE));
    }

    /**
     * Scratchpad text size is display-local just like the main terminal size. The first read copies
     * the current main size, making this a migration-free, one-time initialization.
     */
    public int getScratchpadFontSize() {
        String key = TERMUX_APP.KEY_FONTSIZE + "_scratchpad" + getDisplayIdAsString();
        if (!mSharedPreferences.contains(key)) {
            SharedPreferenceUtils.setIntStoredAsString(mSharedPreferences, key, getFontSize(), false);
        }
        return DataUtils.clamp(SharedPreferenceUtils.getIntStoredAsString(
            mSharedPreferences, key, getFontSize()), MIN_FONTSIZE, MAX_FONTSIZE);
    }

    public void setScratchpadFontSize(int value) {
        String key = TERMUX_APP.KEY_FONTSIZE + "_scratchpad" + getDisplayIdAsString();
        SharedPreferenceUtils.setIntStoredAsString(mSharedPreferences, key,
            DataUtils.clamp(value, MIN_FONTSIZE, MAX_FONTSIZE), false);
    }

    public void changeScratchpadFontSize(boolean increase) {
        setScratchpadFontSize(getScratchpadFontSize() + (increase ? 2 : -2));
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

    public boolean isWallpaperReadPermissionPrompted() {
        return SharedPreferenceUtils.getBoolean(mSharedPreferences, TERMUX_APP.KEY_WALLPAPER_READ_PERMISSION_PROMPTED, TERMUX_APP.DEFAULT_VALUE_WALLPAPER_READ_PERMISSION_PROMPTED);
    }

    public void setWallpaperReadPermissionPrompted(boolean value) {
        SharedPreferenceUtils.setBoolean(mSharedPreferences, TERMUX_APP.KEY_WALLPAPER_READ_PERMISSION_PROMPTED, value, false);
    }

    public int getTerminalBackgroundOpacity() {
        return DataUtils.clamp(resolveSurfaceValue(SurfaceSlot.CANVAS, SurfaceProperty.OPACITY,
            TERMUX_APP.KEY_TERMINAL_BACKGROUND_OPACITY, TERMUX_APP.DEFAULT_VALUE_TERMINAL_BACKGROUND_OPACITY), 0, 100);
    }

    public void setTerminalBackgroundOpacity(int value) {
        int clamped = DataUtils.clamp(value, 0, 100);
        writeSurfaceValue(SurfaceSlot.CANVAS, SurfaceProperty.OPACITY,
            TERMUX_APP.KEY_TERMINAL_BACKGROUND_OPACITY, clamped);
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

    /**
     * One-time fold of the old per-surface values into Base plus overrides. Runs before anything
     * reads a surface value, and is written so that every install keeps exactly the look it had:
     * Base takes the dock's numbers, and any surface whose stored value already differed starts
     * detached. Installs that had the old all-or-nothing "match all surfaces" switch on are fully
     * linked, which is what that switch meant.
     *
     * <p>Deliberately reads the raw keys rather than the getters - the getters resolve through
     * inheritance, which is exactly what is not established yet.
     */
    public synchronized void migrateSurfaceInheritance() {
        adoptShippedSurfaceDefaults();
        if (SharedPreferenceUtils.getBoolean(mSharedPreferences,
                TERMUX_APP.KEY_SURFACE_INHERITANCE_MIGRATED, false))
            return;

        boolean wasNormalized = SharedPreferenceUtils.getBoolean(mSharedPreferences,
            TERMUX_APP.KEY_SURFACE_TUNING_NORMALIZED,
            TERMUX_APP.DEFAULT_VALUE_SURFACE_TUNING_NORMALIZED);

        for (SurfaceProperty property : SurfaceProperty.values()) {
            // The dock is the reference surface: it is the one every other surface's legacy
            // "fall back to the dock" branch already pointed at.
            int base = getSurfaceOverrideValue(SurfaceSlot.DOCK, property);
            setSurfaceBaseValue(property, base);

            for (SurfaceSlot slot : SurfaceSlot.values()) {
                if (!hasSurfaceProperty(slot, property))
                    continue;
                // Only a value the user actually stored can justify starting detached. A surface
                // that was never touched has no opinion to preserve, so it joins Base - which is
                // also what keeps a fresh install from opening with override badges already lit.
                String key = surfaceOverrideKey(slot, property);
                boolean stored = key != null && mSharedPreferences.contains(key);
                boolean matches = wasNormalized || !stored
                    || getSurfaceOverrideValue(slot, property) == base;
                setSurfaceInheriting(slot, property, matches);
            }
        }

        SharedPreferenceUtils.setBoolean(mSharedPreferences,
            TERMUX_APP.KEY_SURFACE_INHERITANCE_MIGRATED, true, true);
    }

    /**
     * Decides, once, whether this install gets the shipped Docked look or keeps the one it already
     * had. A new install simply reads the current defaults, so the only thing it needs written is
     * the one asymmetry a default cannot express. An existing install has the pre-shipped numbers
     * pinned into every key it never set, which is what keeps changing what ships from reaching
     * anyone who has already been using the app.
     *
     * <p>Runs before {@link #migrateSurfaceInheritance()}'s fold and carries its own marker, since
     * an install can have folded under an earlier build and still need this.
     */
    private void adoptShippedSurfaceDefaults() {
        if (SharedPreferenceUtils.getBoolean(mSharedPreferences,
                TERMUX_APP.KEY_SHIPPED_SURFACE_DEFAULTS_ADOPTED, false))
            return;

        if (isFreshInstall()) {
            // The shipped look sits the dock a few points denser than the surfaces behind it, and
            // "denser than Base" is a detached row by definition — so this is the one thing about
            // it that has to be written rather than defaulted. A fresh install opens with exactly
            // one override badge lit, which is the truth about the look it is wearing.
            setSurfaceInheriting(SurfaceSlot.DOCK, SurfaceProperty.OPACITY, false);
            setSurfaceRawValue(SurfaceSlot.DOCK, SurfaceProperty.OPACITY,
                TERMUX_APP.DEFAULT_VALUE_APP_BAR_OPACITY);
            // Nothing to fold either, and folding would do harm: the fold seeds Base from the
            // dock's numbers, which here means the dock's own denser opacity rather than the
            // shared one the other surfaces are meant to open at.
            SharedPreferenceUtils.setBoolean(mSharedPreferences,
                TERMUX_APP.KEY_SURFACE_INHERITANCE_MIGRATED, true, false);
        } else {
            // The dock's numbers come first because the fold reads them as its reference for Base:
            // an install that never touched a dock control would otherwise be handed the new
            // shared layer through the back door.
            pinPreShippedDefaults(PRE_SHIPPED_DOCK_DEFAULTS);
            pinPreShippedDefaults(PRE_SHIPPED_DEFAULTS);
        }

        SharedPreferenceUtils.setBoolean(mSharedPreferences,
            TERMUX_APP.KEY_SHIPPED_SURFACE_DEFAULTS_ADOPTED, true, true);
    }

    /**
     * Whether this install has never run. The logger writes the log level before anything else on
     * every launch, including the very first, so it is the one key a genuinely fresh install can
     * already have; any other key means the app has been used and its look is the user's, not the
     * shipped one's.
     */
    private boolean isFreshInstall() {
        for (String key : mSharedPreferences.getAll().keySet()) {
            if (!TERMUX_APP.KEY_LOG_LEVEL.equals(key))
                return false;
        }
        return true;
    }

    /**
     * The surface values as they stood before the shipped Docked look was captured from a tuned
     * device. Written into an install that predates it, for every key it never set, so that
     * changing what ships changes only what new installs see. Values the editor writes on detach
     * are deliberately absent: those keys are filled from the resolved number at the moment a row
     * is detached, so a stale default can never surface through them.
     */
    private static final Object[][] PRE_SHIPPED_DOCK_DEFAULTS = {
        {TERMUX_APP.KEY_EXTRAKEYS_BLUR_RADIUS, 10},
        {TERMUX_APP.KEY_APP_BAR_OPACITY, 46},
        {TERMUX_APP.KEY_DOCK_GLASS_GRAIN, 39},
        {TERMUX_APP.KEY_APP_LAUNCHER_DOCK_CORNER_RADIUS, -1},
        {TERMUX_APP.KEY_DOCK_HORIZONTAL_INSET, 10},
    };

    private static final Object[][] PRE_SHIPPED_DEFAULTS = {
        {TERMUX_APP.KEY_TERMINAL_BORDER_ENABLED, Boolean.FALSE},
        {TERMUX_APP.KEY_TERMINAL_CORNER_RADIUS, 0},
        {TERMUX_APP.KEY_TERMINAL_PANE_GAP, 1},
        {TERMUX_APP.KEY_IN_APP_KEYBOARD_HEIGHT_SCALE, 1.0830541f},
        {TERMUX_APP.KEY_IN_APP_KEYBOARD_KEY_CORNER_RADIUS_DP, 2.7f},
        {TERMUX_APP.KEY_TOP_PANE_CLOCK_STYLE, TERMUX_APP.TOP_PANE_CLOCK_STYLE_FLIP},
        {TERMUX_APP.KEY_TOP_PANE_CLOCK_AM_PM, Boolean.FALSE},
        {TERMUX_APP.KEY_TOP_PANE_CLOCK_COLLAPSED, Boolean.FALSE},
        {TERMUX_APP.KEY_WALLPAPER_ENABLED_TERMINAL_BACKGROUND_OPACITY, 47},
        {TERMUX_APP.KEY_WALLPAPER_ENABLED_APP_BAR_OPACITY, 46},
        {TERMUX_APP.KEY_WALLPAPER_ENABLED_EXTRAKEYS_BLUR_RADIUS, 10},
    };

    /** Writes each pre-shipped value that this install has no opinion of its own about. */
    private void pinPreShippedDefaults(@NonNull Object[][] defaults) {
        for (Object[] entry : defaults) {
            String key = (String) entry[0];
            if (mSharedPreferences.contains(key))
                continue;
            Object value = entry[1];
            if (value instanceof Boolean)
                SharedPreferenceUtils.setBoolean(mSharedPreferences, key, (Boolean) value, false);
            else if (value instanceof Float)
                SharedPreferenceUtils.setFloat(mSharedPreferences, key, (Float) value, false);
            else if (value instanceof String)
                SharedPreferenceUtils.setString(mSharedPreferences, key, (String) value, false);
            else
                SharedPreferenceUtils.setInt(mSharedPreferences, key, (Integer) value, false);
        }
    }

    /**
     * Writes a surface's value through whatever link it currently has: while the surface follows
     * Base the number lands on Base and every other follower moves with it, and once detached it
     * lands on the surface's own key. This is what keeps callers that legitimately mean "make this
     * surface look like X" - the Settings sliders, the wallpaper-mode policy - from silently
     * detaching a surface as a side effect, and it is why every existing setter can stay as-is.
     */
    private void writeSurfaceValue(SurfaceSlot slot, SurfaceProperty property, String overrideKey,
                                   int value) {
        if (hasSurfaceProperty(slot, property) && isSurfaceInheriting(slot, property))
            setSurfaceBaseValue(property, value);
        else
            SharedPreferenceUtils.setInt(mSharedPreferences, overrideKey, value, false);
    }

    /**
     * Makes a surface show exactly {@code value} while disturbing the others as little as it can:
     * it stays on (or returns to) Base when Base already gives that number, and detaches only when
     * it genuinely cannot. For callers restoring a remembered per-surface picture - wallpaper mode
     * - where some surfaces agreed and some did not.
     */
    public void setSurfaceValueExact(SurfaceSlot slot, SurfaceProperty property, int value) {
        if (!hasSurfaceProperty(slot, property))
            return;
        writeSurfaceRaw(slot, property, value);
        setSurfaceInheriting(slot, property, getSurfaceBaseValue(property) == value);
    }

    /** Writes the surface's own key regardless of the link, without changing it. Migration/undo. */
    public void setSurfaceRawValue(SurfaceSlot slot, SurfaceProperty property, int value) {
        writeSurfaceRaw(slot, property, value);
    }

    /** Detaches this one property from Base and gives it {@code value}. The editor's drag path. */
    public void detachSurfaceValue(SurfaceSlot slot, SurfaceProperty property, int value) {
        if (!hasSurfaceProperty(slot, property))
            return;
        setSurfaceInheriting(slot, property, false);
        writeSurfaceRaw(slot, property, value);
    }

    private void writeSurfaceRaw(SurfaceSlot slot, SurfaceProperty property, int value) {
        String key = surfaceOverrideKey(slot, property);
        if (key != null)
            SharedPreferenceUtils.setInt(mSharedPreferences, key, value, false);
    }

    private static String surfaceOverrideKey(SurfaceSlot slot, SurfaceProperty property) {
        if (!hasSurfaceProperty(slot, property))
            return null;
        switch (slot) {
            case KEYBOARD:
                return property == SurfaceProperty.OPACITY
                    ? TERMUX_APP.KEY_IN_APP_KEYBOARD_BACKGROUND_OPACITY
                    : TERMUX_APP.KEY_IN_APP_KEYBOARD_HORIZONTAL_INSET;
            case STATUS:
                switch (property) {
                    case BLUR: return TERMUX_APP.KEY_STATUS_BAR_BLUR_RADIUS;
                    case OPACITY: return TERMUX_APP.KEY_STATUS_BAR_OPACITY;
                    case GRAIN: return TERMUX_APP.KEY_STATUS_BAR_GRAIN;
                    case CORNER_RADIUS: return TERMUX_APP.KEY_STATUS_BAR_CORNER_RADIUS;
                    default: return TERMUX_APP.KEY_STATUS_BAR_HORIZONTAL_INSET;
                }
            case CANVAS:
                switch (property) {
                    case BLUR: return TERMUX_APP.KEY_TERMINAL_GLASS_BLUR_RADIUS;
                    case GRAIN: return TERMUX_APP.KEY_TERMINAL_GLASS_GRAIN;
                    default: return TERMUX_APP.KEY_TERMINAL_BACKGROUND_OPACITY;
                }
            default:
                switch (property) {
                    case BLUR: return TERMUX_APP.KEY_EXTRAKEYS_BLUR_RADIUS;
                    case OPACITY: return TERMUX_APP.KEY_APP_BAR_OPACITY;
                    case GRAIN: return TERMUX_APP.KEY_DOCK_GLASS_GRAIN;
                    case CORNER_RADIUS: return TERMUX_APP.KEY_APP_LAUNCHER_DOCK_CORNER_RADIUS;
                    default: return TERMUX_APP.KEY_DOCK_HORIZONTAL_INSET;
                }
        }
    }

    // ------------------------------------------------------------------ surface inheritance
    //
    // Five properties are shared across the surfaces. Each (surface, property) pair either follows
    // the Base value or holds its own override, and the resolution runs here rather than at the
    // call sites: every existing getter below already returns the resolved number, so the whole
    // render pipeline sees the right value without knowing inheritance exists. Only the editor
    // reaches for the raw halves.

    /** The surfaces that can carry an override. Sessions is deliberately not one: it is not glass. */
    public enum SurfaceSlot {
        DOCK("dock"), KEYBOARD("keyboard"), STATUS("status"), CANVAS("canvas");

        public final String key;
        SurfaceSlot(String key) { this.key = key; }
    }

    /** The shared properties. Not every slot has every one - see {@link #hasSurfaceProperty}. */
    public enum SurfaceProperty {
        BLUR("blur"), OPACITY("opacity"), GRAIN("grain"),
        CORNER_RADIUS("corner_radius"), SIDE_GAP("side_gap");

        public final String key;
        SurfaceProperty(String key) { this.key = key; }
    }

    /**
     * Whether a property is real for a slot. The keyboard renders on the dock's material, so it has
     * no blur, grain or radius of its own; the terminal canvas has no capsule radius and no screen
     * edge gap. Showing an inherited row for one of these would display a number controlling
     * nothing.
     */
    public static boolean hasSurfaceProperty(SurfaceSlot slot, SurfaceProperty property) {
        switch (slot) {
            case KEYBOARD:
                return property == SurfaceProperty.OPACITY || property == SurfaceProperty.SIDE_GAP;
            case CANVAS:
                return property == SurfaceProperty.BLUR || property == SurfaceProperty.OPACITY
                    || property == SurfaceProperty.GRAIN;
            default:
                return true;
        }
    }

    private static String surfaceInheritKey(SurfaceSlot slot, SurfaceProperty property) {
        return TERMUX_APP.KEY_SURFACE_INHERIT_PREFIX + slot.key + "_" + property.key;
    }

    /** True while this surface still follows Base for this property. */
    public boolean isSurfaceInheriting(SurfaceSlot slot, SurfaceProperty property) {
        if (!hasSurfaceProperty(slot, property))
            return true;
        return SharedPreferenceUtils.getBoolean(mSharedPreferences,
            surfaceInheritKey(slot, property), TERMUX_APP.DEFAULT_VALUE_SURFACE_INHERITS_BASE);
    }

    public void setSurfaceInheriting(SurfaceSlot slot, SurfaceProperty property, boolean inherit) {
        if (!hasSurfaceProperty(slot, property))
            return;
        SharedPreferenceUtils.setBoolean(mSharedPreferences,
            surfaceInheritKey(slot, property), inherit, false);
    }

    /** How many properties this surface has detached from Base - the editor's tab badge. */
    public int surfaceOverrideCount(SurfaceSlot slot) {
        int count = 0;
        for (SurfaceProperty property : SurfaceProperty.values()) {
            if (hasSurfaceProperty(slot, property) && !isSurfaceInheriting(slot, property))
                count++;
        }
        return count;
    }

    /** Puts every property of a surface back on Base. */
    public void reattachSurface(SurfaceSlot slot) {
        for (SurfaceProperty property : SurfaceProperty.values())
            setSurfaceInheriting(slot, property, true);
    }

    private static String surfaceBaseKey(SurfaceProperty property) {
        switch (property) {
            case BLUR: return TERMUX_APP.KEY_SURFACE_BASE_BLUR;
            case OPACITY: return TERMUX_APP.KEY_SURFACE_BASE_OPACITY;
            case GRAIN: return TERMUX_APP.KEY_SURFACE_BASE_GRAIN;
            case CORNER_RADIUS: return TERMUX_APP.KEY_SURFACE_BASE_CORNER_RADIUS;
            default: return TERMUX_APP.KEY_SURFACE_BASE_SIDE_GAP;
        }
    }

    private static int surfaceBaseDefault(SurfaceProperty property) {
        switch (property) {
            case BLUR: return TERMUX_APP.DEFAULT_SURFACE_BASE_BLUR;
            case OPACITY: return TERMUX_APP.DEFAULT_SURFACE_BASE_OPACITY;
            case GRAIN: return TERMUX_APP.DEFAULT_SURFACE_BASE_GRAIN;
            case CORNER_RADIUS: return TERMUX_APP.DEFAULT_SURFACE_BASE_CORNER_RADIUS;
            default: return TERMUX_APP.DEFAULT_SURFACE_BASE_SIDE_GAP;
        }
    }

    /** The shared value for a property, before any surface's clamp is applied to it. */
    public int getSurfaceBaseValue(SurfaceProperty property) {
        return SharedPreferenceUtils.getInt(mSharedPreferences, surfaceBaseKey(property),
            surfaceBaseDefault(property));
    }

    public void setSurfaceBaseValue(SurfaceProperty property, int value) {
        SharedPreferenceUtils.setInt(mSharedPreferences, surfaceBaseKey(property), value, false);
    }

    /**
     * The number a surface should actually use: its own override when detached, Base otherwise.
     * Callers still apply their own clamp, so a Base value outside one surface's range (the
     * terminal's 30dp blur ceiling, say) narrows there instead of leaking.
     */
    private int resolveSurfaceValue(SurfaceSlot slot, SurfaceProperty property,
                                    String overrideKey, int overrideDefault) {
        if (isSurfaceInheriting(slot, property))
            return getSurfaceBaseValue(property);
        return SharedPreferenceUtils.getInt(mSharedPreferences, overrideKey, overrideDefault);
    }

    /**
     * The dp a stored -1 corner radius ("follow the style") stands for. This is the ONE place the
     * sentinel is resolved for display; the render paths keep their own px caps (half the surface
     * height) on top of it. Docked resolves to a straight edge whatever the slot - Docked has
     * always been square, and an upgrade must not quietly round it. Floating resolves to the
     * shared rounded-surface token, except the status surface, which shipped with its own larger
     * adaptive radius and keeps it. A null slot is any non-slot rounded surface (the drawer).
     */
    public static int resolveAutoCornerRadiusDp(@Nullable SurfaceSlot slot, boolean floating) {
        if (!floating)
            return 0;
        return slot == SurfaceSlot.STATUS
            ? TERMUX_APP.STATUS_AUTO_CORNER_RADIUS_MAX_DP
            : TERMUX_APP.DEFAULT_ROUNDED_SURFACE_CORNER_RADIUS_DP;
    }

    /** The surface's own stored number, ignoring inheritance. For the editor's detached rows. */
    public int getSurfaceOverrideValue(SurfaceSlot slot, SurfaceProperty property) {
        switch (slot) {
            case KEYBOARD:
                return property == SurfaceProperty.OPACITY
                    ? SharedPreferenceUtils.getInt(mSharedPreferences,
                        TERMUX_APP.KEY_IN_APP_KEYBOARD_BACKGROUND_OPACITY,
                        TERMUX_APP.DEFAULT_IN_APP_KEYBOARD_BACKGROUND_OPACITY)
                    : SharedPreferenceUtils.getInt(mSharedPreferences,
                        TERMUX_APP.KEY_IN_APP_KEYBOARD_HORIZONTAL_INSET,
                        TERMUX_APP.DEFAULT_IN_APP_KEYBOARD_HORIZONTAL_INSET);
            case STATUS:
                switch (property) {
                    case BLUR: return SharedPreferenceUtils.getInt(mSharedPreferences,
                        TERMUX_APP.KEY_STATUS_BAR_BLUR_RADIUS, TERMUX_APP.DEFAULT_STATUS_BAR_BLUR_RADIUS);
                    case OPACITY: return SharedPreferenceUtils.getInt(mSharedPreferences,
                        TERMUX_APP.KEY_STATUS_BAR_OPACITY, TERMUX_APP.DEFAULT_STATUS_BAR_OPACITY);
                    case GRAIN: return SharedPreferenceUtils.getInt(mSharedPreferences,
                        TERMUX_APP.KEY_STATUS_BAR_GRAIN, TERMUX_APP.DEFAULT_STATUS_BAR_GRAIN);
                    case CORNER_RADIUS: return SharedPreferenceUtils.getInt(mSharedPreferences,
                        TERMUX_APP.KEY_STATUS_BAR_CORNER_RADIUS, TERMUX_APP.DEFAULT_STATUS_BAR_CORNER_RADIUS);
                    default: return SharedPreferenceUtils.getInt(mSharedPreferences,
                        TERMUX_APP.KEY_STATUS_BAR_HORIZONTAL_INSET, TERMUX_APP.DEFAULT_SURFACE_HORIZONTAL_INSET);
                }
            case CANVAS:
                switch (property) {
                    case BLUR: return SharedPreferenceUtils.getInt(mSharedPreferences,
                        TERMUX_APP.KEY_TERMINAL_GLASS_BLUR_RADIUS, TERMUX_APP.DEFAULT_TERMINAL_GLASS_BLUR_RADIUS);
                    case GRAIN: return SharedPreferenceUtils.getInt(mSharedPreferences,
                        TERMUX_APP.KEY_TERMINAL_GLASS_GRAIN, TERMUX_APP.DEFAULT_TERMINAL_GLASS_GRAIN);
                    default: return SharedPreferenceUtils.getInt(mSharedPreferences,
                        TERMUX_APP.KEY_TERMINAL_BACKGROUND_OPACITY,
                        TERMUX_APP.DEFAULT_VALUE_TERMINAL_BACKGROUND_OPACITY);
                }
            default:
                switch (property) {
                    case BLUR: return SharedPreferenceUtils.getInt(mSharedPreferences,
                        TERMUX_APP.KEY_EXTRAKEYS_BLUR_RADIUS, TERMUX_APP.DEFAULT_VALUE_EXTRAKEYS_BLUR_RADIUS);
                    case OPACITY: return SharedPreferenceUtils.getInt(mSharedPreferences,
                        TERMUX_APP.KEY_APP_BAR_OPACITY, TERMUX_APP.DEFAULT_VALUE_APP_BAR_OPACITY);
                    case GRAIN: return SharedPreferenceUtils.getInt(mSharedPreferences,
                        TERMUX_APP.KEY_DOCK_GLASS_GRAIN, TERMUX_APP.DEFAULT_VALUE_DOCK_GLASS_GRAIN);
                    case CORNER_RADIUS: return SharedPreferenceUtils.getInt(mSharedPreferences,
                        TERMUX_APP.KEY_APP_LAUNCHER_DOCK_CORNER_RADIUS,
                        TERMUX_APP.DEFAULT_APP_LAUNCHER_DOCK_CORNER_RADIUS);
                    default: return SharedPreferenceUtils.getInt(mSharedPreferences,
                        TERMUX_APP.KEY_DOCK_HORIZONTAL_INSET, TERMUX_APP.DEFAULT_SURFACE_HORIZONTAL_INSET);
                }
        }
    }

    public int getExtraKeysBlurRadius() {
        return DataUtils.clamp(resolveSurfaceValue(SurfaceSlot.DOCK, SurfaceProperty.BLUR,
            TERMUX_APP.KEY_EXTRAKEYS_BLUR_RADIUS, TERMUX_APP.DEFAULT_VALUE_EXTRAKEYS_BLUR_RADIUS), 0, 30);
    }

    public void setExtraKeysBlurRadius(int value) {
        writeSurfaceValue(SurfaceSlot.DOCK, SurfaceProperty.BLUR,
            TERMUX_APP.KEY_EXTRAKEYS_BLUR_RADIUS, DataUtils.clamp(value, 0, 30));
    }

    public int getDockGlassGrain() {
        return DataUtils.clamp(resolveSurfaceValue(SurfaceSlot.DOCK, SurfaceProperty.GRAIN,
            TERMUX_APP.KEY_DOCK_GLASS_GRAIN, TERMUX_APP.DEFAULT_VALUE_DOCK_GLASS_GRAIN), 0, 100);
    }

    public void setDockGlassGrain(int value) {
        writeSurfaceValue(SurfaceSlot.DOCK, SurfaceProperty.GRAIN,
            TERMUX_APP.KEY_DOCK_GLASS_GRAIN, DataUtils.clamp(value, 0, 100));
    }

    public boolean isTerminalFlushDockEnabled() {
        return SharedPreferenceUtils.getBoolean(mSharedPreferences, TERMUX_APP.KEY_TERMINAL_FLUSH_DOCK,
            TERMUX_APP.DEFAULT_VALUE_TERMINAL_FLUSH_DOCK);
    }

    public void setTerminalFlushDockEnabled(boolean value) {
        SharedPreferenceUtils.setBoolean(mSharedPreferences, TERMUX_APP.KEY_TERMINAL_FLUSH_DOCK, value, false);
    }

    public boolean isTerminalBorderEnabled() {
        return SharedPreferenceUtils.getBoolean(mSharedPreferences, TERMUX_APP.KEY_TERMINAL_BORDER_ENABLED,
            TERMUX_APP.DEFAULT_VALUE_TERMINAL_BORDER_ENABLED);
    }

    public void setTerminalBorderEnabled(boolean value) {
        SharedPreferenceUtils.setBoolean(mSharedPreferences, TERMUX_APP.KEY_TERMINAL_BORDER_ENABLED, value, false);
    }

    public int getAppBarOpacity() {
        return DataUtils.clamp(resolveSurfaceValue(SurfaceSlot.DOCK, SurfaceProperty.OPACITY,
            TERMUX_APP.KEY_APP_BAR_OPACITY, TERMUX_APP.DEFAULT_VALUE_APP_BAR_OPACITY), 0, 100);
    }

    public void setAppBarOpacity(int value) {
        int clamped = DataUtils.clamp(value, 0, 100);
        writeSurfaceValue(SurfaceSlot.DOCK, SurfaceProperty.OPACITY,
            TERMUX_APP.KEY_APP_BAR_OPACITY, clamped);
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
    
    public boolean isTerminalDynamicColorsEnabled() {
        return SharedPreferenceUtils.getBoolean(mSharedPreferences, TERMUX_APP.KEY_TERMINAL_DYNAMIC_COLORS_ENABLED, TERMUX_APP.DEFAULT_VALUE_TERMINAL_DYNAMIC_COLORS_ENABLED);
    }

    public void setTerminalDynamicColorsEnabled(boolean value) {
        SharedPreferenceUtils.setBoolean(mSharedPreferences, TERMUX_APP.KEY_TERMINAL_DYNAMIC_COLORS_ENABLED, value, false);
    }

    @NonNull
    public TerminalContrastLevel getTerminalContrastLevel() {
        return TerminalContrastLevel.from(SharedPreferenceUtils.getString(mSharedPreferences,
            TERMUX_APP.KEY_TERMINAL_CONTRAST_LEVEL,
            TERMUX_APP.DEFAULT_VALUE_TERMINAL_CONTRAST_LEVEL, true));
    }

    public void setTerminalContrastLevel(@Nullable String value) {
        TerminalContrastLevel level = TerminalContrastLevel.from(value == null ? "" : value);
        SharedPreferenceUtils.setString(mSharedPreferences, TERMUX_APP.KEY_TERMINAL_CONTRAST_LEVEL,
            level.value, false);
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
