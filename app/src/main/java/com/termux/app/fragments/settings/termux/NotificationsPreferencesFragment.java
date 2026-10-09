package com.termux.app.fragments.settings.termux;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.Preference;
import androidx.preference.PreferenceManager;
import androidx.preference.SwitchPreferenceCompat;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.termux.R;
import com.termux.app.fragments.settings.MaterialPreferenceFragment;
import com.termux.app.fragments.settings.SettingsLayoutUtils;
import com.termux.app.fragments.settings.StatusActionPreference;
import com.termux.app.launcher.notifications.LauncherNotificationAccess;
import com.termux.app.notice.AppNotice;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;

import java.util.Set;

/**
 * Everything about Android notifications in one place: the access grant the launcher's
 * notification features share, the dots on apps, the essential rules that pin notifications to the
 * status bar, and the per-app history the shell can read. The rows keep the keys they had on the
 * Launcher and Status bar pages, so saved values carry over.
 */
@Keep
public final class NotificationsPreferencesFragment extends MaterialPreferenceFragment {
    private static final String KEY_ACCESS = "app_launcher_notification_access";
    private static final String KEY_DOTS = "app_launcher_notification_dots";
    private static final String KEY_HISTORY = "notification_history_shell";

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        Context context = getContext();
        if (context == null) return;
        PreferenceManager manager = getPreferenceManager();
        manager.setPreferenceDataStore(TermuxStylePreferencesDataStore.getInstance(context));
        setPreferencesFromResource(R.xml.notifications_preferences, rootKey);
        SettingsLayoutUtils.applyScreenLayout(this);

        Preference access = findPreference(KEY_ACCESS);
        if (access != null) access.setOnPreferenceClickListener(preference -> {
            openAccessSettings(context);
            return true;
        });
        SwitchPreferenceCompat dots = findPreference(KEY_DOTS);
        if (dots != null) {
            dots.setOnPreferenceChangeListener((preference, newValue) -> {
                if (Boolean.TRUE.equals(newValue) && !LauncherNotificationAccess.isEnabled(context)) {
                    showAccessPrompt(context);
                }
                return true;
            });
        }
        refresh(context);
    }

    @Override
    public void onResume() {
        super.onResume();
        if (getActivity() != null) getActivity().setTitle(R.string.settings_destination_notifications);
        Context context = getContext();
        if (context != null) refresh(context);
    }

    private void refresh(@NonNull Context context) {
        boolean allowed = LauncherNotificationAccess.isEnabled(context);
        Preference access = findPreference(KEY_ACCESS);
        if (access instanceof StatusActionPreference) {
            ((StatusActionPreference) access).setState(
                getString(allowed ? R.string.settings_status_allowed : R.string.settings_status_action_needed),
                getString(allowed ? R.string.settings_manage_action : R.string.settings_fix_action),
                allowed ? StatusActionPreference.Tone.POSITIVE : StatusActionPreference.Tone.WARNING);
            access.setSummary(R.string.settings_notifications_access_summary);
        }
        SwitchPreferenceCompat dots = findPreference(KEY_DOTS);
        if (dots != null) {
            dots.setSummary(allowed ? R.string.termux_app_launcher_notification_dots_summary
                : R.string.termux_app_launcher_notification_dots_summary_needs_access);
        }
        Preference history = findPreference(KEY_HISTORY);
        if (history != null) history.setSummary(historySummary(context));
    }

    @NonNull
    private String historySummary(@NonNull Context context) {
        TermuxAppSharedPreferences preferences = TermuxAppSharedPreferences.build(context);
        Set<String> packages = preferences == null ? null : preferences.getNotificationHistoryPackages();
        int count = packages == null ? 0 : packages.size();
        if (count == 0) return getString(R.string.notif_history_link_summary);
        return getResources().getQuantityString(R.plurals.notif_history_apps_recorded, count, count);
    }

    private void showAccessPrompt(@NonNull Context context) {
        new MaterialAlertDialogBuilder(context)
            .setTitle(R.string.termux_app_launcher_notification_access_title)
            .setMessage(R.string.termux_app_launcher_notification_access_message)
            .setPositiveButton(R.string.termux_app_launcher_notification_access_enable,
                (dialog, which) -> openAccessSettings(context))
            .setNegativeButton(android.R.string.cancel, null)
            .show();
    }

    /** The per-app detail screen where the system has one, the full list otherwise. */
    static void openAccessSettings(@NonNull Context context) {
        if (start(context, LauncherNotificationAccess.detailSettingsIntent(context))) return;
        if (start(context, LauncherNotificationAccess.listSettingsIntent())) return;
        AppNotice.show(context, R.string.termux_app_launcher_notification_access_unavailable, false);
    }

    private static boolean start(@NonNull Context context, @Nullable Intent intent) {
        if (intent == null) return false;
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            context.startActivity(intent);
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }
}
