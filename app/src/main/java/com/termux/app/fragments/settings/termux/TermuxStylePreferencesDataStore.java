package com.termux.app.fragments.settings.termux;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import androidx.annotation.NonNull;
import androidx.preference.PreferenceDataStore;
import com.termux.app.TermuxActivity;
import com.termux.app.theme.LauncherSchemeTheme;
import com.termux.app.theme.templates.ThemeTemplates;
import com.termux.shared.termux.theme.TermuxThemeUtils;
import com.termux.shared.termux.settings.properties.TermuxPropertyConstants;
import com.termux.shared.termux.settings.properties.TermuxSharedProperties;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;

import java.util.Set;

/**
 * The launcher's preference store behind the Settings pages that write the style keys (Home,
 * Dock, General, Notifications, Performance and the Appearance editor's own rows).
 */
class TermuxStylePreferencesDataStore extends PreferenceDataStore {

    private static final long STYLE_SYNC_DEBOUNCE_MS = 140L;
    private static final Handler MAIN_HANDLER = new Handler(Looper.getMainLooper());

    private final Context mContext;

    private final TermuxAppSharedPreferences mPreferences;
    private boolean mPendingRecreateActivity;
    private final Runnable mStyleSyncRunnable;
    private final Runnable mDrawerSyncRunnable;

    private static TermuxStylePreferencesDataStore mInstance;

    private final KeyboardPreferencesDataStore mKeyboardLook;

    private TermuxStylePreferencesDataStore(Context context) {
        mContext = context;
        mPreferences = TermuxAppSharedPreferences.build(context, true);
        mKeyboardLook = KeyboardPreferencesDataStore.getInstance(context);
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

    /**
     * Lazy mode, Fancier Glass and the terminal swipe (on Home › Gestures) are stored by the
     * terminal I/O store, whoever asks this one.
     */
    private static boolean isTerminalIoToggle(String key) {
        return "lazy_mode".equals(key) || "fancier_glass".equals(key) || "terminal_body_swipe".equals(key);
    }

    @Override
    public void putBoolean(String key, boolean value) {
        if (mPreferences == null)
            return;
        if (key == null)
            return;
        if (isTerminalIoToggle(key)) {
            TerminalIOPreferencesDataStore.getInstance(mContext).putBoolean(key, value);
            return;
        }
        if (com.termux.app.launcher.home.HomeSettings.owns(key)) {
            com.termux.app.launcher.home.HomeSettings.putBoolean(mContext, key, value);
            return;
        }
        switch(key) {
            case "use_system_wallpaper":
                TermuxActivity.setWallpaperModeEnabled(mContext, value);
                break;
            case "wallpaper_parallax":
                // The captured frame changes width with this, so the styling reload re-captures
                // and every surface re-cuts; no recreate needed.
                mPreferences.setWallpaperParallaxEnabled(value);
                scheduleTermuxActivityStylingSync(false);
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
            case "widgets_on_glass":
                // Each widget page re-dresses its widgets on the styling pass.
                mPreferences.setWidgetsOnGlassEnabled(value);
                scheduleTermuxActivityStylingSync(false);
                break;
            case "app_launcher_bw_icons":
                mPreferences.setAppLauncherBwIconsEnabled(value);
                scheduleTermuxActivityStylingSync(false);
                break;
            case "show_in_recents_when_not_default":
                mPreferences.setShowInRecentsWhenNotDefaultEnabled(value);
                break;
            case "app_launcher_display_app_names":
                mPreferences.setAppLauncherDisplayAppNamesEnabled(value);
                scheduleTermuxActivityStylingSync(false);
                break;
            case "app_launcher_notification_dots":
                mPreferences.setAppLauncherNotificationDotsEnabled(value);
                scheduleTermuxActivityStylingSync(false);
                break;
            case "app_launcher_quick_reply_swipe":
                mPreferences.setAppLauncherQuickReplySwipeEnabled(value);
                scheduleTermuxActivityStylingSync(false);
                break;
            case "app_launcher_most_used_page":
                mPreferences.setAppLauncherMostUsedPageEnabled(value);
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
            case "app_haptics_enabled":
                mPreferences.setAppHapticsEnabled(value);
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
        if (isTerminalIoToggle(key))
            return TerminalIOPreferencesDataStore.getInstance(mContext).getBoolean(key, defValue);
        if (com.termux.app.launcher.home.HomeSettings.owns(key))
            return com.termux.app.launcher.home.HomeSettings.getBoolean(mContext, key, defValue);
        switch(key) {
            case "use_system_wallpaper":
                return mPreferences.isUseSystemWallpaperEnabled();
            case "wallpaper_parallax":
                return mPreferences.isWallpaperParallaxEnabled();
            case "terminal_dynamic_colors_enabled":
                return mPreferences.isTerminalDynamicColorsEnabled();
            case "widgets_on_glass":
                return mPreferences.isWidgetsOnGlassEnabled();
            case "app_launcher_bw_icons":
                return mPreferences.isAppLauncherBwIconsEnabled();
            case "show_in_recents_when_not_default":
                return mPreferences.isShowInRecentsWhenNotDefaultEnabled();
            case "app_launcher_display_app_names":
                return mPreferences.isAppLauncherDisplayAppNamesEnabled();
            case "app_launcher_notification_dots":
                return mPreferences.isAppLauncherNotificationDotsEnabled();
            case "app_launcher_quick_reply_swipe":
                return mPreferences.isAppLauncherQuickReplySwipeEnabled();
            case "app_launcher_most_used_page":
                return mPreferences.isAppLauncherMostUsedPageEnabled();
            case "app_launcher_drawer_search_on_open":
                return mPreferences.isAppLauncherDrawerSearchOnOpenEnabled();
            case "app_launcher_drawer_search_android_keyboard":
                return mPreferences.isAppLauncherDrawerSearchAndroidKeyboardEnabled();
            case "app_launcher_widget_pane_enabled":
                return mPreferences.isAppLauncherWidgetPaneEnabled();
            case "app_haptics_enabled":
                return mPreferences.isAppHapticsEnabled();
            case "app_launcher_row_haptics":
                return mPreferences.isAppLauncherRowHapticsEnabled();
            case "app_launcher_az_double_tap_lock":
                return mPreferences.isAppLauncherAzDoubleTapLockEnabled();
            default:
                return defValue;
        }
    }

    @Override
    public Set<String> getStringSet(String key, Set<String> defValues) {
        if (mPreferences == null || key == null) return defValues;
        if ("theme_templates_enabled".equals(key)) return mPreferences.getThemeTemplatesEnabled();
        return defValues;
    }

    @Override
    public void putStringSet(String key, Set<String> values) {
        if (mPreferences == null || key == null) return;
        if ("theme_templates_enabled".equals(key)) {
            mPreferences.setThemeTemplatesEnabled(values);
            // A tool the user just switched on should be wearing the colours by the time they leave
            // this screen, so the pass runs now rather than at the next palette refresh.
            ThemeTemplates.applyFromDiskAsync(mContext);
        }
    }

    @Override
    public void putInt(String key, int value) {
        if (mPreferences == null)
            return;
        if (key == null)
            return;
        switch (key) {
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
        // Settings › Home's newer rows keep their own store; the launcher reads them on return.
        if (com.termux.app.launcher.home.HomeSettings.owns(key)) {
            com.termux.app.launcher.home.HomeSettings.putString(mContext, key, value);
            return;
        }
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
            case "use_as":
                // The launcher re-reads its wall as this lands (its Start in listener).
                mPreferences.setUseAs(value);
                break;
            case "app_launcher_start_in": {
                // Read at the next cold start only; nothing on screen changes. Display also turns
                // on the display's start with the launcher, without which it would not hold.
                com.termux.app.wall.StartInPolicy.Choice choice =
                    com.termux.app.wall.StartInPolicy.choose(value);
                if (choice.startsDisplay) mPreferences.setX11DisplayAutostartEnabled(true);
                // Home lives in the wall's pages; the other holders of the store see it written.
                final String startIn = choice.startIn;
                com.termux.app.wall.WallPagesStore.open(mContext)
                    .edit(pages -> pages.chooseStartIn(startIn));
                break;
            }
            case "app_launcher_home_palette_gesture":
                // Read at each swipe; nothing on screen changes.
                mPreferences.setHomePaletteGesture(value);
                break;
            case "app_launcher_drawer_view_type":
                mPreferences.setAppLauncherDrawerViewType(value);
                scheduleAppDrawerSync();
                break;
            case "app_launcher_drawer_icon_size":
                mPreferences.setAppLauncherDrawerIconSize(value);
                scheduleAppDrawerSync();
                break;
            case "app_launcher_drawer_columns":
                mPreferences.setAppLauncherDrawerColumns(value);
                scheduleAppDrawerSync();
                break;
            case "app_launcher_drawer_rows":
                mPreferences.setAppLauncherDrawerRows(value);
                scheduleAppDrawerSync();
                break;
            case "app_launcher_builtin_widget_style":
                mPreferences.setAppLauncherBuiltinWidgetStyle(value);
                scheduleTermuxActivityStylingSync(false);
                break;
            case "app_launcher_default_buttons":
                mPreferences.setAppLauncherDefaultButtons(value);
                scheduleTermuxActivityStylingSync(false);
                break;
            case "app_launcher_icon_pack_package":
                mPreferences.setAppLauncherIconPackPackage(value);
                // Not invalidate(): that resets catalogue state only, and leaves every layer of
                // held artwork on the previous pack.
                com.termux.app.launcher.data.LauncherAppDataProvider.getInstance(mContext)
                    .invalidateIconArtwork();
                scheduleTermuxActivityStylingSync(false);
                break;
            case "app_launcher_pinned_icon_pack_package":
                mPreferences.setAppLauncherPinnedIconPackPackage(value);
                com.termux.app.launcher.data.LauncherAppDataProvider.getInstance(mContext)
                    .invalidateIconArtwork();
                scheduleTermuxActivityStylingSync(false);
                break;
            case "in_app_keyboard_theme":
                mKeyboardLook.putString(key, value);
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
        if (com.termux.app.launcher.home.HomeSettings.owns(key))
            return com.termux.app.launcher.home.HomeSettings.getString(mContext, key, defValue);
        switch (key) {
            case "terminal_contrast_level":
                return mPreferences.getTerminalContrastLevel().value;
            case "theme_mode":
                return TermuxSharedProperties.getNightMode(mContext);
            case "app_launcher_input_char":
                return mPreferences.getAppLauncherInputChar();
            case "app_launcher_az_lock_method":
                return mPreferences.getAppLauncherAzLockMethod();
            case "in_app_keyboard_theme":
                return mKeyboardLook.getString(key, defValue);
            case "use_as":
                return mPreferences.getUseAs();
            case "app_launcher_start_in":
                // As the launcher reads it: Home is the Terminal under Terminal only.
                return com.termux.app.wall.UseAs.read(mPreferences.getUseAs())
                    .startIn(com.termux.app.wall.WallPagesStore.open(mContext)
                        .current().startIn());
            case "app_launcher_home_palette_gesture":
                return mPreferences.getHomePaletteGesture();
            case "app_launcher_drawer_view_type":
                return mPreferences.getAppLauncherDrawerViewType();
            case "app_launcher_drawer_icon_size":
                return mPreferences.getAppLauncherDrawerIconSize();
            case "app_launcher_drawer_columns":
                return mPreferences.getAppLauncherDrawerColumns();
            case "app_launcher_drawer_rows":
                return mPreferences.getAppLauncherDrawerRows();
            case "app_launcher_builtin_widget_style":
                return mPreferences.getAppLauncherBuiltinWidgetStyle();
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
