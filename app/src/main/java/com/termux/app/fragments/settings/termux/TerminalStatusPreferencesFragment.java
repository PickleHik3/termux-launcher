package com.termux.app.fragments.settings.termux;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.Keep;
import androidx.annotation.Nullable;
import androidx.preference.PreferenceDataStore;
import androidx.preference.PreferenceManager;
import androidx.preference.Preference;

import com.termux.R;
import com.termux.app.TermuxActivity;
import com.termux.app.fragments.settings.MaterialPreferenceFragment;
import com.termux.app.fragments.settings.SettingsLayoutUtils;
import com.termux.app.launcher.notifications.LauncherNotificationAccess;

/** User-facing terminal page that combines the old Terminal I/O and Terminal view screens. */
@Keep
public final class TerminalStatusPreferencesFragment extends MaterialPreferenceFragment {

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        Context context = getContext();
        if (context == null) return;
        PreferenceManager manager = getPreferenceManager();
        manager.setPreferenceDataStore(new TerminalStatusDataStore(context));
        setPreferencesFromResource(R.xml.terminal_status_preferences, rootKey);
        SettingsLayoutUtils.applyScreenLayout(this);
        Preference customize = findPreference("customize_status_surface");
        if (customize != null) customize.setOnPreferenceClickListener(preference -> {
            openSurfaceEditor(context, "status");
            return true;
        });
        Preference access = findPreference("top_pane_notification_access");
        if (access != null) access.setOnPreferenceClickListener(preference -> {
            openNotificationAccessSettings(context);
            return true;
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        if (getActivity() != null) {
            getActivity().setTitle(R.string.settings_destination_terminal_status);
        }
        Context context = getContext();
        Preference access = findPreference("top_pane_notification_access");
        if (context != null && access != null) {
            access.setSummary(LauncherNotificationAccess.isEnabled(context)
                ? R.string.termux_app_launcher_access_status_on
                : R.string.termux_top_pane_notification_access_summary);
        }
    }

    /** The media widget and pinned notifications both need listener access to have any data. */
    private void openNotificationAccessSettings(Context context) {
        Intent detail = LauncherNotificationAccess.detailSettingsIntent(context);
        if (detail != null && startSettingsIntent(context, detail)) return;
        startSettingsIntent(context, LauncherNotificationAccess.listSettingsIntent());
    }

    private boolean startSettingsIntent(Context context, @Nullable Intent intent) {
        if (intent == null) return false;
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            context.startActivity(intent);
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    private void openSurfaceEditor(Context context, String section) {
        Intent intent = new Intent(context, TermuxActivity.class);
        intent.putExtra(TermuxActivity.EXTRA_DOCK_TUNING, true);
        intent.putExtra(TermuxActivity.EXTRA_DOCK_TUNING_SECTION, section);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);
    }

    private static final class TerminalStatusDataStore extends PreferenceDataStore {
        private final TerminalIOPreferencesDataStore io;
        private final TerminalViewPreferencesDataStore view;

        TerminalStatusDataStore(Context context) {
            io = TerminalIOPreferencesDataStore.getInstance(context);
            view = TerminalViewPreferencesDataStore.getInstance(context);
        }

        @Override public void putBoolean(String key, boolean value) {
            if ("split_pane_controls".equals(key)) io.putBoolean("compatibility_mode", !value);
            else if ("show_in_recents_when_not_default".equals(key)) view.putBoolean("activity_finish_remove_task", !value);
            else if ("fullscreen".equals(key) || "terminal_margin_adjustment".equals(key)) view.putBoolean(key, value);
            else io.putBoolean(key, value);
        }

        @Override public boolean getBoolean(String key, boolean fallback) {
            if ("split_pane_controls".equals(key)) return !io.getBoolean("compatibility_mode", !fallback);
            if ("show_in_recents_when_not_default".equals(key)) return !view.getBoolean("activity_finish_remove_task", !fallback);
            if ("fullscreen".equals(key) || "terminal_margin_adjustment".equals(key)) return view.getBoolean(key, fallback);
            return io.getBoolean(key, fallback);
        }

        @Override public void putString(String key, @Nullable String value) { io.putString(key, value); }
        @Override public String getString(String key, @Nullable String fallback) { return io.getString(key, fallback); }
    }
}
