package com.termux.app.fragments.settings.termux;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;

import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.core.app.NotificationManagerCompat;
import androidx.preference.Preference;
import androidx.preference.PreferenceManager;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.termux.app.notice.AppNotice;
import com.termux.R;
import com.termux.app.TermuxActivity;
import com.termux.app.fragments.settings.MaterialPreferenceFragment;
import com.termux.app.fragments.settings.PillPreference;
import com.termux.app.fragments.settings.SettingsLayoutUtils;
import com.termux.app.launcher.LauncherLockAccessibilityAccess;
import com.termux.app.launcher.PinnedAppsEditor;
import com.termux.app.launcher.data.LauncherUsageStatsStore;
import com.termux.app.launcher.notifications.LauncherNotificationAccess;
import com.termux.shared.android.PermissionUtils;
import com.termux.shared.termux.TermuxConstants;
import com.termux.shared.termux.settings.preferences.TermuxPreferenceConstants;

@Keep
public class LauncherPreferencesFragment extends MaterialPreferenceFragment {

    private static final String KEY_STORAGE = "app_launcher_storage_access";
    private static final String KEY_HELP = "app_launcher_help";
    private static final String KEY_NOTIFICATION_ACCESS = "app_launcher_notification_access";
    private static final String KEY_ACCESSIBILITY_LOCK = "app_launcher_accessibility_lock_access";
    private static final String KEY_NOTIFICATION_SETTINGS = "app_launcher_notification_settings";
    private static final String KEY_APP_PERMISSIONS = "app_launcher_app_permissions";

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        Context context = getContext();
        if (context == null)
            return;
        PreferenceManager preferenceManager = getPreferenceManager();
        preferenceManager.setPreferenceDataStore(TermuxStylePreferencesDataStore.getInstance(context));
        setPreferencesFromResource(R.xml.launcher_preferences, rootKey);
        SettingsLayoutUtils.applyScreenLayout(this);
        configurePermissionActions(context);
        configureHelp(context);
        updatePermissionSummaries(context);
        updateDrawerLayoutSummary();

        Preference lockMethodPreference = findPreference("app_launcher_az_lock_method");
        if (lockMethodPreference != null) {
            lockMethodPreference.setOnPreferenceChangeListener((preference, newValue) -> {
                if ("accessibility".equals(newValue) && !LauncherLockAccessibilityAccess.isEnabled(context)) {
                    showAccessibilityLockPrompt(context);
                }
                return true;
            });
        }

        Preference defaultAppsPreference = findPreference("app_launcher_default_buttons");
        if (defaultAppsPreference != null) {
            defaultAppsPreference.setOnPreferenceClickListener(preference -> {
                Context ctx = getContext();
                if (ctx != null) PinnedAppsEditor.show(ctx, null);
                return true;
            });
        }

        Preference setHomePreference = findPreference("app_launcher_set_home");
        if (setHomePreference != null) {
            setHomePreference.setOnPreferenceClickListener(preference -> {
                openHomeLauncherSettings(context);
                return true;
            });
        }

        Preference resetRankingPreference = findPreference("app_launcher_reset_usage_ranking");
        if (resetRankingPreference != null) {
            resetRankingPreference.setOnPreferenceClickListener(preference -> {
                Context ctx = getContext();
                if (ctx == null) return true;
                new MaterialAlertDialogBuilder(ctx)
                    .setTitle(R.string.termux_app_launcher_reset_usage_ranking_confirm_title)
                    .setMessage(R.string.termux_app_launcher_reset_usage_ranking_confirm_message)
                    .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                        LauncherUsageStatsStore.getInstance(ctx).clear();
                        AppNotice.show(ctx, R.string.termux_app_launcher_reset_usage_ranking_done, false);
                    })
                    .setNegativeButton(android.R.string.cancel, null)
                    .show();
                return true;
            });
        }
    }

    private void updateDrawerLayoutSummary() {
        Preference layout = findPreference("app_launcher_drawer_layout");
        if (layout == null || getContext() == null) return;
        String value = TermuxStylePreferencesDataStore.getInstance(getContext()).getString(
            TermuxPreferenceConstants.TERMUX_APP.KEY_APP_LAUNCHER_DRAWER_VIEW_TYPE,
            TermuxPreferenceConstants.TERMUX_APP.DEFAULT_APP_LAUNCHER_DRAWER_VIEW_TYPE);
        int summary = TermuxPreferenceConstants.TERMUX_APP.APP_LAUNCHER_DRAWER_VIEW_TYPE_HORIZONTAL.equals(value)
            ? R.string.settings_app_drawer_view_type_horizontal
            : TermuxPreferenceConstants.TERMUX_APP.APP_LAUNCHER_DRAWER_VIEW_TYPE_CATEGORIES.equals(value)
                ? R.string.settings_app_drawer_view_type_categories
                : R.string.settings_app_drawer_view_type_vertical;
        layout.setSummary(summary);
    }

    @Override
    public void onResume() {
        super.onResume();
        Context context = getContext();
        if (context == null) return;
        if (getActivity() != null) {
            getActivity().setTitle(R.string.settings_destination_launcher_apps);
        }
        updatePermissionSummaries(context);
        updateDrawerLayoutSummary();
    }

    private void configurePermissionActions(@NonNull Context context) {
        setClickListener(KEY_STORAGE, preference -> {
            Intent intent = new Intent(context, TermuxActivity.class)
                .setAction(TermuxConstants.TERMUX_APP.TERMUX_ACTIVITY.ACTION_REQUEST_PERMISSIONS);
            startActivity(intent);
            return true;
        });
        setClickListener(KEY_NOTIFICATION_ACCESS, preference -> {
            openNotificationAccessSettings(context);
            return true;
        });
        setClickListener(KEY_ACCESSIBILITY_LOCK, preference -> {
            if (!startSettingsIntent(context, new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))) {
                showSettingsUnavailable(context);
            }
            return true;
        });
        setClickListener(KEY_NOTIFICATION_SETTINGS, preference -> {
            openNotificationSettings(context);
            return true;
        });
        setClickListener(KEY_APP_PERMISSIONS, preference -> {
            openAppDetails(context);
            return true;
        });
    }


    /** Help, for a user who has not found the corner tabs yet: its own screen, over Settings. */
    private void configureHelp(@NonNull Context context) {
        setClickListener(KEY_HELP, preference -> {
            // An ordinary screen on top of this one: Back comes back to Settings, so nothing
            // has to bounce through the home screen to read the guide.
            startActivity(com.termux.app.help.HelpActivity.intent(context, null, null, null));
            return true;
        });
    }

    private void setClickListener(String key, Preference.OnPreferenceClickListener listener) {
        Preference preference = findPreference(key);
        if (preference != null) {
            preference.setOnPreferenceClickListener(listener);
        }
    }

    private void updatePermissionSummaries(@NonNull Context context) {
        setStatusPill(
            KEY_STORAGE,
            PermissionUtils.checkAndRequestLegacyOrManageExternalStoragePermission(context, -1, true, false)
        );
        setStatusPill(KEY_NOTIFICATION_ACCESS, LauncherNotificationAccess.isEnabled(context));
        setStatusPill(KEY_ACCESSIBILITY_LOCK, LauncherLockAccessibilityAccess.isEnabled(context));
        setStatusPill(KEY_NOTIFICATION_SETTINGS, NotificationManagerCompat.from(context).areNotificationsEnabled());
    }

    private void setStatusPill(String key, boolean enabled) {
        Preference preference = findPreference(key);
        if (preference instanceof PillPreference) {
            ((PillPreference) preference).setPill(
                getString(enabled
                    ? R.string.termux_app_launcher_access_status_on
                    : R.string.termux_app_launcher_access_status_off),
                enabled ? PillPreference.Tone.POSITIVE : PillPreference.Tone.NEGATIVE);
        } else if (preference != null) {
            preference.setSummary(enabled
                ? R.string.termux_app_launcher_access_status_on
                : R.string.termux_app_launcher_access_status_off);
        }
    }

    private void openNotificationAccessSettings(Context context) {
        if (startSettingsIntent(context, LauncherNotificationAccess.detailSettingsIntent(context))) {
            return;
        }
        if (startSettingsIntent(context, LauncherNotificationAccess.listSettingsIntent())) {
            return;
        }
        AppNotice.show(context, R.string.termux_app_launcher_notification_access_unavailable, false);
    }

    private void openNotificationSettings(@NonNull Context context) {
        Intent intent = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.getPackageName());
        if (!startSettingsIntent(context, intent)) {
            openAppDetails(context);
        }
    }

    private void openAppDetails(@NonNull Context context) {
        Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            .setData(Uri.parse("package:" + context.getPackageName()));
        if (!startSettingsIntent(context, intent)) {
            showSettingsUnavailable(context);
        }
    }

    private void showSettingsUnavailable(@NonNull Context context) {
        AppNotice.show(context, R.string.termux_app_launcher_permission_settings_unavailable, false);
    }

    private void showAccessibilityLockPrompt(Context context) {
        new MaterialAlertDialogBuilder(context)
            .setTitle(R.string.termux_app_launcher_accessibility_lock_prompt_title)
            .setMessage(R.string.termux_app_launcher_accessibility_lock_prompt_message)
            .setPositiveButton(R.string.termux_app_launcher_accessibility_lock_prompt_enable, (dialog, which) -> {
                if (!startSettingsIntent(context, new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))) {
                    AppNotice.show(context, R.string.termux_app_launcher_permission_settings_unavailable, false);
                }
            })
            .setNegativeButton(android.R.string.cancel, null)
            .show();
    }

    /** The same chooser the run's last card offers, so both doors behave the same way. */
    private void openHomeLauncherSettings(Context context) {
        com.termux.app.HomeAppChooser.open(context);
    }

    private boolean startSettingsIntent(Context context, Intent intent) {
        if (intent == null) return false;
        try {
            startActivity(intent);
            return true;
        } catch (ActivityNotFoundException | SecurityException e) {
            return false;
        }
    }
}
