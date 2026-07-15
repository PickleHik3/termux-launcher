package com.termux.app.fragments.settings.termux;

import android.app.WallpaperInfo;
import android.app.WallpaperManager;
import android.content.Context;
import android.os.Bundle;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import androidx.annotation.ColorInt;
import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.preference.Preference;
import androidx.preference.PreferenceDataStore;
import androidx.preference.PreferenceManager;
import androidx.preference.SeekBarPreference;
import com.termux.R;
import com.termux.app.TermuxActivity;
import com.termux.app.fragments.settings.MaterialPreferenceFragment;
import com.termux.app.fragments.settings.SettingsLayoutUtils;
import com.termux.shared.data.DataUtils;
import com.termux.shared.file.FileUtils;
import com.termux.shared.logger.Logger;
import com.termux.shared.settings.properties.SharedProperties;
import com.termux.shared.theme.ThemeUtils;
import com.termux.shared.termux.theme.TermuxThemeUtils;
import com.termux.shared.termux.TermuxConstants;
import com.termux.shared.termux.settings.properties.TermuxPropertyConstants;
import com.termux.shared.termux.settings.properties.TermuxSharedProperties;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;
import com.termux.launcherctl.LauncherCtlMcpPreferences;
import java.io.File;
import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

@Keep
public class TermuxStylePreferencesFragment extends MaterialPreferenceFragment {

    static final float[] APP_LAUNCHER_ICON_SCALE_PRESETS = {1.40f, 1.54f, 1.74f, 1.92f};
    // Rebalanced so the old "Large" (2.18) is the new "Default"; smallest/small step down and a
    // larger top bucket is added above. Paired with the height/icon formulas in TermuxActivity.
    static final float[] APP_LAUNCHER_BAR_HEIGHT_PRESETS = {1.72f, 1.95f, 2.18f, 2.45f};

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        Context context = getContext();
        if (context == null)
            return;
        PreferenceManager preferenceManager = getPreferenceManager();
        preferenceManager.setPreferenceDataStore(TermuxStylePreferencesDataStore.getInstance(context));
        setPreferencesFromResource(R.xml.termux_style_preferences, rootKey);
        SettingsLayoutUtils.applyScreenLayout(this);
        LauncherIconPackPreferenceController.configure(this, context);
        configureDockPreferencePresentation();
        updateDockBlurAvailability();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (getActivity() != null) {
            getActivity().setTitle(R.string.termux_style_preferences_title);
        }
        Context context = getContext();
        if (context != null) {
            LauncherIconPackPreferenceController.configure(this, context);
        }
        updateDockBlurAvailability();
    }

    private void updateDockBlurAvailability() {
        Context context = getContext();
        if (context == null) return;

        boolean liveWallpaperActive = isLiveWallpaperActive(context);
        SeekBarPreference dockBlurPreference = findPreference("extrakeys_blur_radius");

        if (dockBlurPreference != null) {
            dockBlurPreference.setEnabled(!liveWallpaperActive);
            dockBlurPreference.setSummary(
                liveWallpaperActive
                    ? R.string.termux_extrakeys_blur_live_wallpaper_active_note
                    : R.string.termux_extrakeys_blur_live_wallpaper_note
            );
        }

        if (!liveWallpaperActive) return;

        TermuxAppSharedPreferences preferences = TermuxAppSharedPreferences.build(context, false);
        if (preferences != null && preferences.getExtraKeysBlurRadius() != 0) {
            preferences.setExtraKeysBlurRadius(0);
            TermuxActivity.requestTermuxActivityStylingOnNextResume(context, true);
        }
    }

    private boolean isLiveWallpaperActive(@NonNull Context context) {
        try {
            WallpaperInfo wallpaperInfo = WallpaperManager.getInstance(context).getWallpaperInfo();
            return wallpaperInfo != null;
        } catch (Exception e) {
            Logger.logStackTraceWithMessage("TermuxStylePreferences", "Failed to detect live wallpaper state", e);
            return false;
        }
    }

    private void configureDockPreferencePresentation() {
        SeekBarPreference iconScalePreference = findPreference("app_launcher_icon_scale_percent");
        if (iconScalePreference != null) {
            updateIconScaleSummary(iconScalePreference, iconScalePreference.getValue());
            iconScalePreference.setOnPreferenceChangeListener((preference, newValue) -> {
                if (newValue instanceof Integer) {
                    updateIconScaleSummary(iconScalePreference, (Integer) newValue);
                }
                return true;
            });
        }

        SeekBarPreference barHeightPreference = findPreference("app_launcher_bar_height_percent");
        if (barHeightPreference != null) {
            updateBarHeightSummary(barHeightPreference, barHeightPreference.getValue());
            barHeightPreference.setOnPreferenceChangeListener((preference, newValue) -> {
                if (newValue instanceof Integer) {
                    updateBarHeightSummary(barHeightPreference, (Integer) newValue);
                }
                return true;
            });
        }
    }

    private void updateIconScaleSummary(@NonNull SeekBarPreference preference, int value) {
        preference.setSummary(getDockPresetLabel(value));
    }

    private void updateBarHeightSummary(@NonNull SeekBarPreference preference, int value) {
        preference.setSummary(getDockPresetLabel(value));
    }

    @NonNull
    private String getDockPresetLabel(int value) {
        switch (clampDockPresetIndex(value, APP_LAUNCHER_BAR_HEIGHT_PRESETS)) {
            case 0:
                return getString(R.string.termux_dock_preset_smallest);
            case 1:
                return getString(R.string.termux_dock_preset_small);
            case 2:
                return getString(R.string.termux_dock_preset_default);
            default:
                return getString(R.string.termux_dock_preset_large);
        }
    }

    static int clampDockPresetIndex(int value, @NonNull float[] presets) {
        return DataUtils.clamp(value, 0, Math.max(0, presets.length - 1));
    }

    static int nearestDockPresetIndex(float value, @NonNull float[] presets) {
        int bestIndex = 0;
        float bestDistance = Float.MAX_VALUE;
        for (int i = 0; i < presets.length; i++) {
            float distance = Math.abs(value - presets[i]);
            if (distance < bestDistance) {
                bestDistance = distance;
                bestIndex = i;
            }
        }
        return bestIndex;
    }

    static float iconScaleForPreset(int preset) {
        return APP_LAUNCHER_ICON_SCALE_PRESETS[clampDockPresetIndex(preset, APP_LAUNCHER_ICON_SCALE_PRESETS)];
    }

    static float barHeightForPreset(int preset) {
        return APP_LAUNCHER_BAR_HEIGHT_PRESETS[clampDockPresetIndex(preset, APP_LAUNCHER_BAR_HEIGHT_PRESETS)];
    }
}

class TermuxStylePreferencesDataStore extends PreferenceDataStore {

    private static final long STYLE_SYNC_DEBOUNCE_MS = 140L;
    private static final Handler MAIN_HANDLER = new Handler(Looper.getMainLooper());

    private final Context mContext;

    private final TermuxAppSharedPreferences mPreferences;
    private boolean mPendingRecreateActivity;
    private final Runnable mStyleSyncRunnable;

    private static TermuxStylePreferencesDataStore mInstance;
    private static final String LOG_TAG = "TermuxStylePreferences";

    private TermuxStylePreferencesDataStore(Context context) {
        mContext = context;
        mPreferences = TermuxAppSharedPreferences.build(context, true);
        mStyleSyncRunnable = () -> {
            boolean recreateActivity = mPendingRecreateActivity;
            mPendingRecreateActivity = false;
            TermuxActivity.requestTermuxActivityStylingOnNextResume(mContext, recreateActivity);
        };
    }

    public static synchronized TermuxStylePreferencesDataStore getInstance(Context context) {
        if (mInstance == null) {
            mInstance = new TermuxStylePreferencesDataStore(context);
        }
        return mInstance;
    }

    private void scheduleTermuxActivityStylingSync(boolean recreateActivity) {
        mPendingRecreateActivity = mPendingRecreateActivity || recreateActivity;
        MAIN_HANDLER.removeCallbacks(mStyleSyncRunnable);
        MAIN_HANDLER.postDelayed(mStyleSyncRunnable, STYLE_SYNC_DEBOUNCE_MS);
    }

    @Override
    public void putBoolean(String key, boolean value) {
        if (mPreferences == null)
            return;
        if (key == null)
            return;
        switch(key) {
            case "use_system_wallpaper":
                TermuxActivity.setWallpaperModeEnabled(mContext, value);
                break;
            case "extrakeys_blur_enabled":
                // Legacy compatibility: map old boolean writes to the new radius-driven model.
                mPreferences.setExtraKeysBlurRadius(value ? Math.max(1, mPreferences.getExtraKeysBlurRadius()) : 0);
                break;
            case "sessions_blur_enabled":
                // Sessions blur is no longer user-facing in the hybrid model.
                break;
            case "monet_background_enabled":
                // Legacy compatibility: material overlay is always enabled now.
                break;
            case "monet_overlay_enabled":
                // Legacy compatibility: material overlay is always enabled now.
                break;
            case "terminal_dynamic_colors_enabled":
                mPreferences.setTerminalDynamicColorsEnabled(value);
                scheduleTermuxActivityStylingSync(false);
                break;
            case "app_launcher_bw_icons":
                mPreferences.setAppLauncherBwIconsEnabled(value);
                scheduleTermuxActivityStylingSync(false);
                break;
            case "app_launcher_unify_icons":
                mPreferences.setAppLauncherUnifyIconsEnabled(value);
                scheduleTermuxActivityStylingSync(false);
                break;
            case "app_launcher_icon_shadow":
                mPreferences.setAppLauncherIconShadowEnabled(value);
                scheduleTermuxActivityStylingSync(false);
                break;
            case "app_launcher_apps_row_enabled":
                mPreferences.setAppLauncherAppsRowEnabled(value);
                scheduleTermuxActivityStylingSync(false);
                break;
            case "app_launcher_display_app_names":
                mPreferences.setAppLauncherDisplayAppNamesEnabled(value);
                scheduleTermuxActivityStylingSync(false);
                break;
            case "app_launcher_notification_dots":
                mPreferences.setAppLauncherNotificationDotsEnabled(value);
                scheduleTermuxActivityStylingSync(false);
                break;
            case "app_launcher_most_used_page":
                mPreferences.setAppLauncherMostUsedPageEnabled(value);
                scheduleTermuxActivityStylingSync(false);
                break;
            case "app_launcher_az_row_enabled":
                mPreferences.setAppLauncherAzRowEnabled(value);
                scheduleTermuxActivityStylingSync(false);
                break;
            case "app_launcher_az_double_tap_lock":
                mPreferences.setAppLauncherAzDoubleTapLockEnabled(value);
                break;
            default:
                break;
        }
    }

    @Override
    public boolean getBoolean(String key, boolean defValue) {
        if (mPreferences == null)
            return defValue;
        switch(key) {
            case "use_system_wallpaper":
                return mPreferences.isUseSystemWallpaperEnabled();
            case "extrakeys_blur_enabled":
                return mPreferences.getExtraKeysBlurRadius() > 0;
            case "sessions_blur_enabled":
                return false;
            case "monet_background_enabled":
            case "monet_overlay_enabled":
                return true;
            case "terminal_dynamic_colors_enabled":
                return mPreferences.isTerminalDynamicColorsEnabled();
            case "app_launcher_bw_icons":
                return mPreferences.isAppLauncherBwIconsEnabled();
            case "app_launcher_unify_icons":
                return mPreferences.isAppLauncherUnifyIconsEnabled();
            case "app_launcher_icon_shadow":
                return mPreferences.isAppLauncherIconShadowEnabled();
            case "app_launcher_apps_row_enabled":
                return mPreferences.isAppLauncherAppsRowEnabled();
            case "app_launcher_display_app_names":
                return mPreferences.isAppLauncherDisplayAppNamesEnabled();
            case "app_launcher_notification_dots":
                return mPreferences.isAppLauncherNotificationDotsEnabled();
            case "app_launcher_most_used_page":
                return mPreferences.isAppLauncherMostUsedPageEnabled();
            case "app_launcher_az_row_enabled":
                return mPreferences.isAppLauncherAzRowEnabled();
            case "app_launcher_az_double_tap_lock":
                return mPreferences.isAppLauncherAzDoubleTapLockEnabled();
            default:
                return defValue;
        }
    }

    @Override
    public void putInt(String key, int value) {
        if (mPreferences == null)
            return;
        if (key == null)
            return;
        switch (key) {
            case "terminal_background_opacity":
                mPreferences.setTerminalBackgroundOpacity(value);
                syncBackgroundOverlayColor(value, null);
                scheduleTermuxActivityStylingSync(false);
                break;
            case "sessions_opacity":
                mPreferences.setSessionsOpacity(value);
                scheduleTermuxActivityStylingSync(false);
                break;
            case "extrakeys_blur_radius":
                mPreferences.setExtraKeysBlurRadius(value);
                scheduleTermuxActivityStylingSync(false);
                break;
            case "app_bar_opacity":
                mPreferences.setAppBarOpacity(value);
                scheduleTermuxActivityStylingSync(false);
                break;
            case "dock_glass_grain":
                mPreferences.setDockGlassGrain(value);
                scheduleTermuxActivityStylingSync(false);
                break;
            case "app_launcher_button_count":
                mPreferences.setAppLauncherButtonCount(value);
                scheduleTermuxActivityStylingSync(false);
                break;
            case "app_launcher_icon_scale_percent":
                mPreferences.setAppLauncherBarHeightScale(TermuxStylePreferencesFragment.barHeightForPreset(value));
                mPreferences.setAppLauncherIconScale(TermuxStylePreferencesFragment.iconScaleForPreset(value));
                scheduleTermuxActivityStylingSync(false);
                break;
            case "app_launcher_bar_height_percent":
                mPreferences.setAppLauncherBarHeightScale(TermuxStylePreferencesFragment.barHeightForPreset(value));
                mPreferences.setAppLauncherIconScale(TermuxStylePreferencesFragment.iconScaleForPreset(value));
                scheduleTermuxActivityStylingSync(false);
                break;
            default:
                break;
        }
    }

    @Override
    public int getInt(String key, int defValue) {
        if (mPreferences == null)
            return defValue;
        if (key == null)
            return defValue;
        switch (key) {
            case "terminal_background_opacity":
                return mPreferences.getTerminalBackgroundOpacity();
            case "sessions_opacity":
                return mPreferences.getSessionsOpacity();
            case "extrakeys_blur_radius":
                return mPreferences.getExtraKeysBlurRadius();
            case "app_bar_opacity":
                return mPreferences.getAppBarOpacity();
            case "dock_glass_grain":
                return mPreferences.getDockGlassGrain();
            case "app_launcher_button_count":
                return mPreferences.getAppLauncherButtonCount();
            case "app_launcher_icon_scale_percent":
                return TermuxStylePreferencesFragment.nearestDockPresetIndex(
                    mPreferences.getAppLauncherBarHeightScale(),
                    TermuxStylePreferencesFragment.APP_LAUNCHER_BAR_HEIGHT_PRESETS
                );
            case "app_launcher_bar_height_percent":
                return TermuxStylePreferencesFragment.nearestDockPresetIndex(
                    mPreferences.getAppLauncherBarHeightScale(),
                    TermuxStylePreferencesFragment.APP_LAUNCHER_BAR_HEIGHT_PRESETS
                );
            default:
                return defValue;
        }
    }

    @Override
    public void putString(String key, String value) {
        if (mPreferences == null)
            return;
        if (key == null)
            return;
        switch (key) {
            case "theme_mode":
                writeTermuxPropertyToProperties(TermuxPropertyConstants.KEY_NIGHT_MODE, value);
                TermuxThemeUtils.setAppNightMode(value);
                scheduleTermuxActivityStylingSync(true);
                break;
            case "app_launcher_button_count":
                mPreferences.setAppLauncherButtonCount(DataUtils.getIntFromString(value, mPreferences.getAppLauncherButtonCount()));
                scheduleTermuxActivityStylingSync(false);
                break;
            case "app_launcher_input_char":
                mPreferences.setAppLauncherInputChar(value);
                scheduleTermuxActivityStylingSync(false);
                break;
            case "app_launcher_az_lock_method":
                mPreferences.setAppLauncherAzLockMethod(value);
                break;
            case "app_launcher_dock_style":
                mPreferences.setAppLauncherDockStyle(value);
                scheduleTermuxActivityStylingSync(false);
                break;
            case "app_launcher_default_buttons":
                mPreferences.setAppLauncherDefaultButtons(value);
                scheduleTermuxActivityStylingSync(false);
                break;
            case "app_launcher_icon_pack_package":
                mPreferences.setAppLauncherIconPackPackage(value);
                com.termux.app.launcher.data.LauncherAppDataProvider.getInstance(mContext).invalidate();
                scheduleTermuxActivityStylingSync(false);
                break;
            case "app_launcher_pinned_icon_pack_package":
                mPreferences.setAppLauncherPinnedIconPackPackage(value);
                com.termux.app.launcher.data.LauncherAppDataProvider.getInstance(mContext).invalidate();
                scheduleTermuxActivityStylingSync(false);
                break;
            case LauncherCtlMcpPreferences.KEY_WEB_PROVIDER:
            case LauncherCtlMcpPreferences.KEY_BRAVE_API_KEY:
            case LauncherCtlMcpPreferences.KEY_SEARXNG_URL:
            case LauncherCtlMcpPreferences.KEY_SEARXNG_API_KEY:
                LauncherCtlMcpPreferences.putString(mContext, key, value);
                LauncherCtlMcpPreferences.writePresetConfig(mContext);
                break;
            case "app_launcher_bar_height":
                mPreferences.setAppLauncherBarHeightScale(
                    TermuxStylePreferencesFragment.barHeightForPreset(
                        TermuxStylePreferencesFragment.nearestDockPresetIndex(
                            DataUtils.getFloatFromString(value, mPreferences.getAppLauncherBarHeightScale()),
                            TermuxStylePreferencesFragment.APP_LAUNCHER_BAR_HEIGHT_PRESETS
                        )
                    )
                );
                mPreferences.setAppLauncherIconScale(
                    TermuxStylePreferencesFragment.iconScaleForPreset(
                        TermuxStylePreferencesFragment.nearestDockPresetIndex(
                            DataUtils.getFloatFromString(value, mPreferences.getAppLauncherBarHeightScale()),
                            TermuxStylePreferencesFragment.APP_LAUNCHER_BAR_HEIGHT_PRESETS
                        )
                    )
                );
                scheduleTermuxActivityStylingSync(false);
                break;
            case "app_launcher_icon_scale":
                mPreferences.setAppLauncherBarHeightScale(
                    TermuxStylePreferencesFragment.barHeightForPreset(
                        TermuxStylePreferencesFragment.nearestDockPresetIndex(
                            DataUtils.getFloatFromString(value, mPreferences.getAppLauncherIconScale()),
                            TermuxStylePreferencesFragment.APP_LAUNCHER_ICON_SCALE_PRESETS
                        )
                    )
                );
                mPreferences.setAppLauncherIconScale(
                    TermuxStylePreferencesFragment.iconScaleForPreset(
                        TermuxStylePreferencesFragment.nearestDockPresetIndex(
                            DataUtils.getFloatFromString(value, mPreferences.getAppLauncherIconScale()),
                            TermuxStylePreferencesFragment.APP_LAUNCHER_ICON_SCALE_PRESETS
                        )
                    )
                );
                scheduleTermuxActivityStylingSync(false);
                break;
            default:
                break;
        }
    }

    @Override
    public String getString(String key, String defValue) {
        if (mPreferences == null)
            return defValue;
        if (key == null)
            return defValue;
        switch (key) {
            case "theme_mode":
                return TermuxSharedProperties.getNightMode(mContext);
            case "app_launcher_button_count":
                return Integer.toString(mPreferences.getAppLauncherButtonCount());
            case "app_launcher_input_char":
                return mPreferences.getAppLauncherInputChar();
            case "app_launcher_az_lock_method":
                return mPreferences.getAppLauncherAzLockMethod();
            case "app_launcher_dock_style":
                return mPreferences.getAppLauncherDockStyle();
            case "app_launcher_default_buttons":
                return mPreferences.getAppLauncherDefaultButtons();
            case "app_launcher_icon_pack_package":
                return mPreferences.getAppLauncherIconPackPackage();
            case "app_launcher_pinned_icon_pack_package":
                return mPreferences.getAppLauncherPinnedIconPackPackage();
            case LauncherCtlMcpPreferences.KEY_BRAVE_API_KEY:
            case LauncherCtlMcpPreferences.KEY_SEARXNG_URL:
            case LauncherCtlMcpPreferences.KEY_SEARXNG_API_KEY:
                return LauncherCtlMcpPreferences.getSecret(mContext, key);
            case LauncherCtlMcpPreferences.KEY_WEB_PROVIDER:
                return LauncherCtlMcpPreferences.getWebProvider(mContext);
            case "app_launcher_bar_height":
                return Float.toString(mPreferences.getAppLauncherBarHeightScale());
            case "app_launcher_icon_scale":
                return Float.toString(mPreferences.getAppLauncherBarHeightScale());
            default:
                return defValue;
        }
    }

    private void syncBackgroundOverlayColor(int opacityPercent, Integer baseColorOverride) {
        int alpha = (int) ((DataUtils.clamp(opacityPercent, 0, 100) / 100f) * 255);
        Properties properties = loadTermuxProperties();
        String currentValue = properties.getProperty(TermuxPropertyConstants.KEY_BACKGROUND_OVERLAY_COLOR);
        int baseColor = baseColorOverride != null ? baseColorOverride : TermuxSharedProperties.getBackgroundOverlayInternalPropertyValueFromValue(currentValue);
        baseColor = getMonetSurfaceColor(baseColor);
        int newColor = (baseColor & 0x00FFFFFF) | (alpha << 24);
        writeTermuxPropertyToProperties(TermuxPropertyConstants.KEY_BACKGROUND_OVERLAY_COLOR,
            String.format("#%08X", newColor));
    }

    private Properties loadTermuxProperties() {
        File propertiesFile = SharedProperties.getPropertiesFileFromList(TermuxConstants.TERMUX_PROPERTIES_FILE_PATHS_LIST, LOG_TAG);
        if (propertiesFile == null) {
            propertiesFile = TermuxConstants.TERMUX_PROPERTIES_PRIMARY_FILE;
        }
        Properties properties = SharedProperties.getPropertiesFromFile(mContext, propertiesFile, null);
        return properties == null ? new Properties() : properties;
    }

    private void writeTermuxPropertyToProperties(@NonNull String propertyKey, @NonNull String propertyValue) {
        File propertiesFile = SharedProperties.getPropertiesFileFromList(TermuxConstants.TERMUX_PROPERTIES_FILE_PATHS_LIST, LOG_TAG);
        if (propertiesFile == null) {
            propertiesFile = TermuxConstants.TERMUX_PROPERTIES_PRIMARY_FILE;
        }
        File parentDir = propertiesFile.getParentFile();
        if (parentDir != null && !parentDir.exists()) {
            //noinspection ResultOfMethodCallIgnored
            parentDir.mkdirs();
        }
        List<String> lines = new ArrayList<>();
        boolean updated = false;
        if (propertiesFile.exists()) {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(propertiesFile), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    String trimmed = line.trim();
                    if (!trimmed.startsWith("#") && trimmed.matches("^\\s*" + propertyKey + "\\s*=.*$")) {
                        lines.add(propertyKey + "=" + propertyValue);
                        updated = true;
                    } else {
                        lines.add(line);
                    }
                }
            } catch (Exception e) {
                Logger.logStackTraceWithMessage(LOG_TAG, "Failed to read termux.properties", e);
            }
        }
        if (!updated) {
            lines.add(propertyKey + "=" + propertyValue);
        }
        StringBuilder output = new StringBuilder();
        for (String line : lines) {
            output.append(line).append('\n');
        }
        FileUtils.writeTextToFile("termux.properties", propertiesFile.getAbsolutePath(), StandardCharsets.UTF_8, output.toString(), false);
    }

    @ColorInt
    private int getMonetSurfaceColor(@ColorInt int fallbackColor) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                return ThemeUtils.getSystemAttrColor(mContext, com.termux.shared.R.attr.termuxColorSurfaceBase, fallbackColor);
            }
            return ThemeUtils.getSystemAttrColor(mContext, com.termux.shared.R.attr.termuxColorSurfaceBase, fallbackColor);
        } catch (Exception e) {
            Logger.logStackTraceWithMessage(LOG_TAG, "Failed to resolve Monet surface color", e);
            return fallbackColor;
        }
    }
}
