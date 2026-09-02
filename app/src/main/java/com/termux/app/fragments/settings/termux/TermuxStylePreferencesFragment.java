package com.termux.app.fragments.settings.termux;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.preference.Preference;
import androidx.preference.PreferenceDataStore;
import androidx.preference.PreferenceManager;
import androidx.preference.SwitchPreferenceCompat;
import com.termux.R;
import com.termux.app.TermuxActivity;
import com.termux.launcherctl.LauncherCtlNotificationStore;
import com.termux.app.fragments.settings.MaterialPreferenceFragment;
import com.termux.app.fragments.settings.SettingsLayoutUtils;
import com.termux.app.theme.LauncherSchemeTheme;
import com.termux.shared.termux.theme.TermuxThemeUtils;
import com.termux.shared.termux.TermuxConstants;
import com.termux.shared.termux.settings.properties.TermuxPropertyConstants;
import com.termux.shared.termux.settings.properties.TermuxSharedProperties;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;

@Keep
public class TermuxStylePreferencesFragment extends MaterialPreferenceFragment {

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
                intent.putExtra(TermuxActivity.EXTRA_SURFACE_EDITOR, true);
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                startActivity(intent);
                return true;
            });
        }
        configureTerminalContrastPreference();
        configureDynamicColorsHint();
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
        configureTerminalContrastPreference();
        configureDynamicColorsHint();
    }

    /**
     * What the wallpaper-colours switch is really choosing, said under it.
     *
     * <p>It picks the palette for the terminal and for the launcher chrome together, so the note
     * names both — and the one case where the chrome cannot follow, the palette being loaded into
     * the activity's resources on API 30 and below, is stated rather than hidden.
     */
    private void configureDynamicColorsHint() {
        Preference hint = findPreference("terminal_dynamic_colors_hint");
        if (hint == null) return;
        hint.setSummary(LauncherSchemeTheme.isSupported()
            ? R.string.settings_wallpaper_colors_hint
            : R.string.settings_wallpaper_colors_hint_chrome_unsupported);
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
            case "terminal_dynamic_colors_enabled":
                // This switch is the whole palette decision: on, the terminal and the chrome both
                // take the wallpaper; off, both take the scheme. Either way the chrome palette
                // changes, and it is loaded into the activity's Resources at theme time, so
                // nothing short of a recreate can swap it.
                mPreferences.setTerminalDynamicColorsEnabled(value);
                LauncherSchemeTheme.invalidate();
                scheduleTermuxActivityStylingSync(true);
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
            case "app_launcher_drawer_search_on_open":
                // Read fresh every time the drawer commits open, so nothing has to be re-styled.
                mPreferences.setAppLauncherDrawerSearchOnOpenEnabled(value);
                break;
            case "app_launcher_drawer_search_android_keyboard":
                // Same: decided per open, never mid-search.
                mPreferences.setAppLauncherDrawerSearchAndroidKeyboardEnabled(value);
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
            case "terminal_dynamic_colors_enabled":
                return mPreferences.isTerminalDynamicColorsEnabled();
            case "app_launcher_bw_icons":
                return mPreferences.isAppLauncherBwIconsEnabled();
            case "show_in_recents_when_not_default":
                return mPreferences.isShowInRecentsWhenNotDefaultEnabled();
            case "app_launcher_apps_row_enabled":
                return mPreferences.isAppLauncherAppsRowEnabled();
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
            case "app_launcher_drawer_search_on_open":
                return mPreferences.isAppLauncherDrawerSearchOnOpenEnabled();
            case "app_launcher_drawer_search_android_keyboard":
                return mPreferences.isAppLauncherDrawerSearchAndroidKeyboardEnabled();
            case "app_launcher_widget_pane_enabled":
                return mPreferences.isAppLauncherWidgetPaneEnabled();
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
            case "sessions_opacity":
                mPreferences.setSessionsOpacity(value);
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
            case "sessions_opacity":
                return mPreferences.getSessionsOpacity();
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
            case "theme_mode":
                writeTermuxPropertyToProperties(TermuxPropertyConstants.KEY_NIGHT_MODE, value);
                TermuxThemeUtils.setAppNightMode(value);
                scheduleTermuxActivityStylingSync(true);
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
            case "theme_mode":
                return TermuxSharedProperties.getNightMode(mContext);
            case "app_launcher_input_char":
                return mPreferences.getAppLauncherInputChar();
            case "app_launcher_az_lock_method":
                return mPreferences.getAppLauncherAzLockMethod();
            case "app_launcher_use_case_mode":
                return com.termux.app.launcher.LauncherUseCaseMode.currentMode(mPreferences);
            case "app_launcher_dock_rail_side":
                return mPreferences.getAppLauncherDockRailSide();
            case "app_launcher_drawer_view_type":
                return mPreferences.getAppLauncherDrawerViewType();
            case "app_launcher_default_buttons":
                return mPreferences.getAppLauncherDefaultButtons();
            case "app_launcher_icon_pack_package":
                return mPreferences.getAppLauncherIconPackPackage();
            case "app_launcher_pinned_icon_pack_package":
                return mPreferences.getAppLauncherPinnedIconPackPackage();
            default:
                return defValue;
        }
    }

    private void writeTermuxPropertyToProperties(@NonNull String propertyKey, @NonNull String propertyValue) {
        com.termux.app.settings.TermuxPropertiesFile.write(propertyKey, propertyValue);
    }
}
