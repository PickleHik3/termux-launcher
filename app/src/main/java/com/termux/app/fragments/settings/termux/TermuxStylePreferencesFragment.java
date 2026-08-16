package com.termux.app.fragments.settings.termux;

import android.app.WallpaperInfo;
import android.app.WallpaperManager;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
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
import androidx.preference.SwitchPreferenceCompat;
import com.termux.R;
import com.termux.app.TermuxActivity;
import com.termux.launcherctl.LauncherCtlNotificationStore;
import com.termux.app.fragments.settings.MaterialPreferenceFragment;
import com.termux.app.fragments.settings.SettingsLayoutUtils;
import com.termux.app.theme.LauncherSchemeTheme;
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

    // Dock size is the sole geometry control. Icon size is derived from it by TermuxActivity, so
    // the UI cannot put dock height and icon scale into contradictory states.
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
        Preference surfaceEditor = findPreference("live_surface_editor");
        if (surfaceEditor != null) {
            surfaceEditor.setOnPreferenceClickListener(preference -> {
                Intent intent = new Intent(context, TermuxActivity.class);
                intent.putExtra(TermuxActivity.EXTRA_DOCK_TUNING, true);
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                startActivity(intent);
                return true;
            });
        }
        configureDockPreferencePresentation();
        configureTerminalContrastPreference();
        configureUiColorSourcePreference();
        updateDockBlurAvailability();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (getActivity() != null) {
            getActivity().setTitle(R.string.settings_destination_appearance);
        }
        Context context = getContext();
        if (context != null) {
            LauncherIconPackPreferenceController.configure(this, context);
        }
        updateDockBlurAvailability();
        configureTerminalContrastPreference();
        configureUiColorSourcePreference();
    }

    /**
     * Availability and summary for the interface-colour source.
     *
     * <p>Both ways it can be unavailable are stated in the summary rather than hidden: on API 30
     * and below the palette cannot be loaded into the activity's resources at all, and with no
     * {@code colors.properties} on disk there is nothing to derive a palette from.
     */
    private void configureUiColorSourcePreference() {
        androidx.preference.ListPreference source = findPreference("ui_color_source");
        Preference hint = findPreference("ui_color_source_hint");
        if (source == null) return;

        boolean supported = LauncherSchemeTheme.isSupported();
        boolean hasScheme = TermuxConstants.TERMUX_COLOR_PROPERTIES_FILE.isFile();
        source.setEnabled(supported && hasScheme);
        if (hint != null) hint.setVisible(supported && hasScheme);

        if (!supported) {
            source.setSummary(R.string.settings_ui_color_source_unsupported);
            return;
        }
        if (!hasScheme) {
            source.setSummary(R.string.settings_ui_color_source_no_scheme);
            return;
        }
        source.setSummary(LauncherSchemeTheme.COLOR_SOURCE_SCHEME.equals(source.getValue())
            ? R.string.settings_ui_color_source_summary_scheme
            : R.string.settings_ui_color_source_summary_wallpaper);
        source.setOnPreferenceChangeListener((preference, value) -> {
            source.setValue(String.valueOf(value));
            if (LauncherSchemeTheme.COLOR_SOURCE_SCHEME.equals(String.valueOf(value))) {
                // The data store has already turned wallpaper colours off — the terminal and the
                // chrome share one palette — so the switch has to stop claiming otherwise.
                SwitchPreferenceCompat dynamic = findPreference("terminal_dynamic_colors_enabled");
                if (dynamic != null) dynamic.setChecked(false);
                configureTerminalContrastPreference();
            }
            configureUiColorSourcePreference();
            return true;
        });
    }

    private void configureTerminalContrastPreference() {
        androidx.preference.ListPreference contrast = findPreference("terminal_contrast_level");
        androidx.preference.SwitchPreferenceCompat dynamic =
            findPreference("terminal_dynamic_colors_enabled");
        if (contrast == null || dynamic == null) return;
        boolean enabled = dynamic.isChecked();
        contrast.setEnabled(enabled);
        updateTerminalContrastSummary(contrast, enabled);
        contrast.setOnPreferenceChangeListener((preference, value) -> {
            contrast.setValue(String.valueOf(value));
            updateTerminalContrastSummary(contrast, true);
            return true;
        });
        dynamic.setOnPreferenceChangeListener((preference, value) -> {
            boolean on = Boolean.TRUE.equals(value);
            contrast.setEnabled(on);
            updateTerminalContrastSummary(contrast, on);
            androidx.preference.ListPreference source = findPreference("ui_color_source");
            if (on && source != null
                && LauncherSchemeTheme.COLOR_SOURCE_SCHEME.equals(source.getValue())) {
                // Same coupling from the other side: the data store has already moved the chrome
                // back to the wallpaper.
                source.setValue(LauncherSchemeTheme.COLOR_SOURCE_WALLPAPER);
                configureUiColorSourcePreference();
            }
            return true;
        });
    }

    private void updateTerminalContrastSummary(@NonNull androidx.preference.ListPreference contrast,
                                               boolean enabled) {
        if (!enabled) {
            contrast.setSummary(R.string.settings_terminal_contrast_disabled);
            return;
        }
        String label = contrast.getEntry() == null ? "Default" : contrast.getEntry().toString();
        contrast.setSummary(getString(R.string.settings_terminal_contrast_summary, label));
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

        // A live wallpaper cannot provide a stable bitmap for this blur pipeline. Keep the user's
        // chosen value intact and only disable its effective rendering until a static wallpaper is
        // active again.
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
    private final Runnable mDrawerSyncRunnable;

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
        mDrawerSyncRunnable = () -> TermuxActivity.requestAppDrawerReloadOnNextResume(mContext);
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

    private void scheduleAppDrawerSync() {
        MAIN_HANDLER.removeCallbacks(mDrawerSyncRunnable);
        MAIN_HANDLER.postDelayed(mDrawerSyncRunnable, STYLE_SYNC_DEBOUNCE_MS);
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
                if (value && LauncherSchemeTheme.COLOR_SOURCE_SCHEME.equals(mPreferences.getUiColorSource())) {
                    // The scheme the chrome was following is no longer what the terminal shows, so
                    // the chrome goes back to the wallpaper with it.
                    mPreferences.setUiColorSource(LauncherSchemeTheme.COLOR_SOURCE_WALLPAPER);
                    LauncherSchemeTheme.invalidate();
                    scheduleTermuxActivityStylingSync(true);
                    break;
                }
                scheduleTermuxActivityStylingSync(false);
                break;
            case "app_launcher_bw_icons":
                mPreferences.setAppLauncherBwIconsEnabled(value);
                scheduleTermuxActivityStylingSync(false);
                break;
            case "show_in_recents_when_not_default":
                mPreferences.setShowInRecentsWhenNotDefaultEnabled(value);
                break;
            case "app_launcher_apps_row_enabled":
                mPreferences.setAppLauncherAppsRowEnabled(value);
                scheduleTermuxActivityStylingSync(false);
                break;
            case "app_launcher_extra_keys_row_enabled":
                mPreferences.setAppLauncherExtraKeysRowEnabled(value);
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
            case "app_launcher_notification_history":
                mPreferences.setAppLauncherNotificationHistoryEnabled(value);
                // Turning it off means the captured message bodies go too, not just future ones.
                if (!value) LauncherCtlNotificationStore.getInstance().clearAll();
                break;
            case "app_launcher_most_used_page":
                mPreferences.setAppLauncherMostUsedPageEnabled(value);
                scheduleTermuxActivityStylingSync(false);
                break;
            case "app_launcher_az_row_enabled":
                mPreferences.setAppLauncherAzRowEnabled(value);
                scheduleTermuxActivityStylingSync(false);
                break;
            case "app_launcher_drawer_enabled":
                mPreferences.setAppLauncherDrawerEnabled(value);
                scheduleTermuxActivityStylingSync(false);
                break;
            case "extra_keys_text_all_caps":
                // A property, not a preference: the row reads it from termux.properties, so the
                // switch writes there and the reload picks it up like any hand edit would.
                writeTermuxPropertyToProperties(
                    TermuxPropertyConstants.KEY_EXTRA_KEYS_TEXT_ALL_CAPS,
                    Boolean.toString(value));
                scheduleTermuxActivityStylingSync(false);
                break;
            case "app_launcher_widget_pane_enabled":
                mPreferences.setAppLauncherWidgetPaneEnabled(value);
                // The pane is built once per activity, so it has to come back on a recreate.
                scheduleTermuxActivityStylingSync(true);
                break;
            case "app_launcher_row_haptics":
                mPreferences.setAppLauncherRowHapticsEnabled(value);
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
            case "show_in_recents_when_not_default":
                return mPreferences.isShowInRecentsWhenNotDefaultEnabled();
            case "app_launcher_apps_row_enabled":
                return mPreferences.isAppLauncherAppsRowEnabled();
            case "app_launcher_extra_keys_row_enabled":
                return mPreferences.isAppLauncherExtraKeysRowEnabled();
            case "app_launcher_display_app_names":
                return mPreferences.isAppLauncherDisplayAppNamesEnabled();
            case "app_launcher_notification_dots":
                return mPreferences.isAppLauncherNotificationDotsEnabled();
            case "app_launcher_notification_history":
                return mPreferences.isAppLauncherNotificationHistoryEnabled();
            case "app_launcher_most_used_page":
                return mPreferences.isAppLauncherMostUsedPageEnabled();
            case "app_launcher_az_row_enabled":
                // Raw: the switch shows what the user picked, the apps row dependency greys it.
                return mPreferences.isAppLauncherAzRowChosen();
            case "app_launcher_drawer_enabled":
                return mPreferences.isAppLauncherDrawerEnabled();
            case "app_launcher_widget_pane_enabled":
                return mPreferences.isAppLauncherWidgetPaneEnabled();
            case "extra_keys_text_all_caps": {
                String stored = loadTermuxProperties().getProperty(
                    TermuxPropertyConstants.KEY_EXTRA_KEYS_TEXT_ALL_CAPS);
                // Absent means the documented default-true behaviour of this property.
                return stored == null || !"false".equals(stored.trim().toLowerCase(java.util.Locale.ROOT));
            }
            case "app_launcher_row_haptics":
                return mPreferences.isAppLauncherRowHapticsEnabled();
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
            case "app_launcher_bar_height_percent":
                mPreferences.setAppLauncherBarHeightScale(TermuxStylePreferencesFragment.barHeightForPreset(value));
                scheduleTermuxActivityStylingSync(false);
                break;
            case "app_launcher_dock_corner_radius":
                mPreferences.setAppLauncherDockCornerRadius(value);
                scheduleTermuxActivityStylingSync(false);
                break;
            case "app_launcher_drawer_icon_size_dp":
                mPreferences.setAppLauncherDrawerIconSizeDp(value);
                scheduleAppDrawerSync();
                break;
            case "app_launcher_drawer_grid_columns_vertical":
                mPreferences.setAppLauncherDrawerGridColumnsVertical(value);
                scheduleAppDrawerSync();
                break;
            case "app_launcher_drawer_grid_columns_horizontal":
                mPreferences.setAppLauncherDrawerGridColumnsHorizontal(value);
                scheduleAppDrawerSync();
                break;
            case "app_launcher_drawer_grid_rows_horizontal":
                mPreferences.setAppLauncherDrawerGridRowsHorizontal(value);
                scheduleAppDrawerSync();
                break;
            case "app_launcher_drawer_grid_columns_categories":
                mPreferences.setAppLauncherDrawerGridColumnsCategories(value);
                scheduleAppDrawerSync();
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
            case "app_launcher_bar_height_percent":
                return TermuxStylePreferencesFragment.nearestDockPresetIndex(
                    mPreferences.getAppLauncherBarHeightScale(),
                    TermuxStylePreferencesFragment.APP_LAUNCHER_BAR_HEIGHT_PRESETS
                );
            case "app_launcher_dock_corner_radius":
                int radius = mPreferences.getAppLauncherDockCornerRadius();
                return radius < 0 ? 28 : radius;
            case "app_launcher_drawer_icon_size_dp":
                return mPreferences.getAppLauncherDrawerIconSizeDp();
            case "app_launcher_drawer_grid_columns_vertical":
                return mPreferences.getAppLauncherDrawerGridColumnsVertical();
            case "app_launcher_drawer_grid_columns_horizontal":
                return mPreferences.getAppLauncherDrawerGridColumnsHorizontal();
            case "app_launcher_drawer_grid_rows_horizontal":
                return mPreferences.getAppLauncherDrawerGridRowsHorizontal();
            case "app_launcher_drawer_grid_columns_categories":
                return mPreferences.getAppLauncherDrawerGridColumnsCategories();
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
            case "terminal_contrast_level":
                mPreferences.setTerminalContrastLevel(value);
                scheduleTermuxActivityStylingSync(false);
                break;
            case "ui_color_source":
                mPreferences.setUiColorSource(value);
                if (LauncherSchemeTheme.COLOR_SOURCE_SCHEME.equals(value)) {
                    // Chrome on the scheme and terminal on the wallpaper is not a state anyone asked
                    // for: the two would sit side by side in different palettes.
                    mPreferences.setTerminalDynamicColorsEnabled(false);
                }
                // The palette is loaded into the activity's Resources at theme time, so nothing
                // short of a recreate can swap it.
                LauncherSchemeTheme.invalidate();
                scheduleTermuxActivityStylingSync(true);
                break;
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
            case "app_launcher_use_case_mode":
                com.termux.app.launcher.LauncherUseCaseMode.applyMode(mPreferences, value);
                // Flips the drawer, both dock rows and the widget pane at once: recreate so every
                // surface is rebuilt against the new state instead of restyled in place.
                scheduleTermuxActivityStylingSync(true);
                break;
            case "app_launcher_dock_style":
                mPreferences.setAppLauncherDockStyle(value);
                scheduleTermuxActivityStylingSync(false);
                break;
            case "app_launcher_dock_rail_side":
                mPreferences.setAppLauncherDockRailSide(value);
                // The side moves the rail's layout gravity and the content root's cutout padding,
                // both of which are applied from an insets pass; recreate rather than restyle so
                // the terminal is re-inset from the edge the rail just left.
                scheduleTermuxActivityStylingSync(true);
                break;
            case "app_launcher_drawer_view_type":
                mPreferences.setAppLauncherDrawerViewType(value);
                scheduleAppDrawerSync();
                break;
            case "app_launcher_drawer_icon_size_dp":
                mPreferences.setAppLauncherDrawerIconSizeDp(DataUtils.getIntFromString(value, 0));
                scheduleAppDrawerSync();
                break;
            case "app_launcher_drawer_grid_columns_vertical":
                mPreferences.setAppLauncherDrawerGridColumnsVertical(DataUtils.getIntFromString(value, 0));
                scheduleAppDrawerSync();
                break;
            case "app_launcher_drawer_grid_columns_horizontal":
                mPreferences.setAppLauncherDrawerGridColumnsHorizontal(DataUtils.getIntFromString(value, 0));
                scheduleAppDrawerSync();
                break;
            case "app_launcher_drawer_grid_rows_horizontal":
                mPreferences.setAppLauncherDrawerGridRowsHorizontal(DataUtils.getIntFromString(value, 0));
                scheduleAppDrawerSync();
                break;
            case "app_launcher_drawer_grid_columns_categories":
                mPreferences.setAppLauncherDrawerGridColumnsCategories(DataUtils.getIntFromString(value, 0));
                scheduleAppDrawerSync();
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
            case "app_launcher_bar_height":
                mPreferences.setAppLauncherBarHeightScale(
                    TermuxStylePreferencesFragment.barHeightForPreset(
                        TermuxStylePreferencesFragment.nearestDockPresetIndex(
                            DataUtils.getFloatFromString(value, mPreferences.getAppLauncherBarHeightScale()),
                            TermuxStylePreferencesFragment.APP_LAUNCHER_BAR_HEIGHT_PRESETS
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
            case "terminal_contrast_level":
                return mPreferences.getTerminalContrastLevel().value;
            case "ui_color_source":
                return mPreferences.getUiColorSource();
            case "theme_mode":
                return TermuxSharedProperties.getNightMode(mContext);
            case "app_launcher_button_count":
                return Integer.toString(mPreferences.getAppLauncherButtonCount());
            case "app_launcher_input_char":
                return mPreferences.getAppLauncherInputChar();
            case "app_launcher_az_lock_method":
                return mPreferences.getAppLauncherAzLockMethod();
            case "app_launcher_use_case_mode":
                return com.termux.app.launcher.LauncherUseCaseMode.currentMode(mPreferences);
            case "app_launcher_dock_style":
                return mPreferences.getAppLauncherDockStyle();
            case "app_launcher_dock_rail_side":
                return mPreferences.getAppLauncherDockRailSide();
            case "app_launcher_drawer_view_type":
                return mPreferences.getAppLauncherDrawerViewType();
            case "app_launcher_drawer_icon_size_dp":
                return Integer.toString(mPreferences.getAppLauncherDrawerIconSizeDp());
            case "app_launcher_drawer_grid_columns_vertical":
                return Integer.toString(mPreferences.getAppLauncherDrawerGridColumnsVertical());
            case "app_launcher_drawer_grid_columns_horizontal":
                return Integer.toString(mPreferences.getAppLauncherDrawerGridColumnsHorizontal());
            case "app_launcher_drawer_grid_rows_horizontal":
                return Integer.toString(mPreferences.getAppLauncherDrawerGridRowsHorizontal());
            case "app_launcher_drawer_grid_columns_categories":
                return Integer.toString(mPreferences.getAppLauncherDrawerGridColumnsCategories());
            case "app_launcher_default_buttons":
                return mPreferences.getAppLauncherDefaultButtons();
            case "app_launcher_icon_pack_package":
                return mPreferences.getAppLauncherIconPackPackage();
            case "app_launcher_pinned_icon_pack_package":
                return mPreferences.getAppLauncherPinnedIconPackPackage();
            case "app_launcher_bar_height":
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
        baseColor = getMaterialSurfaceColor(baseColor);
        int newColor = (baseColor & 0x00FFFFFF) | (alpha << 24);
        writeTermuxPropertyToProperties(TermuxPropertyConstants.KEY_BACKGROUND_OVERLAY_COLOR,
            String.format("#%08X", newColor));
    }

    private Properties loadTermuxProperties() {
        return com.termux.app.settings.TermuxPropertiesFile.load(mContext);
    }

    private void writeTermuxPropertyToProperties(@NonNull String propertyKey, @NonNull String propertyValue) {
        com.termux.app.settings.TermuxPropertiesFile.write(propertyKey, propertyValue);
    }

    @ColorInt
    private int getMaterialSurfaceColor(@ColorInt int fallbackColor) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                return ThemeUtils.getSystemAttrColor(mContext, com.termux.shared.R.attr.termuxColorSurfaceBase, fallbackColor);
            }
            return ThemeUtils.getSystemAttrColor(mContext, com.termux.shared.R.attr.termuxColorSurfaceBase, fallbackColor);
        } catch (Exception e) {
            Logger.logStackTraceWithMessage(LOG_TAG, "Failed to resolve Material surface color", e);
            return fallbackColor;
        }
    }
}
