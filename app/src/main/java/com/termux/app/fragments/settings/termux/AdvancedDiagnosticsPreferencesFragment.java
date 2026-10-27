package com.termux.app.fragments.settings.termux;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Bundle;
import android.widget.Toast;

import androidx.annotation.Keep;
import androidx.annotation.Nullable;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceDataStore;
import androidx.preference.PreferenceManager;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.termux.app.notice.AppNotice;
import com.termux.R;
import com.termux.app.fragments.settings.MaterialPreferenceFragment;
import com.termux.app.fragments.settings.SettingsLayoutUtils;
import com.termux.privileged.PrivilegedBackendManager;
import com.termux.shared.activities.ReportActivity;
import com.termux.shared.android.AndroidUtils;
import com.termux.shared.logger.Logger;
import com.termux.shared.models.ReportInfo;
import com.termux.shared.termux.TermuxConstants;
import com.termux.shared.termux.TermuxUtils;
import com.termux.shared.termux.settings.preferences.TermuxAPIAppSharedPreferences;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * Diagnostics: the one logging page (spec §4). The app's own Debugging page and Termux:API's folded
 * in here, keys unchanged: {@code log_level} is the launcher's, and {@code termux_api_log_level}
 * stands for Termux:API's own {@code log_level}, shown only while Termux:API is installed.
 */
@Keep
public final class AdvancedDiagnosticsPreferencesFragment extends MaterialPreferenceFragment {

    /** The row for Termux:API's log level, which Termux:API stores as {@code log_level}. */
    static final String KEY_TERMUX_API_LOG_LEVEL = "termux_api_log_level";

    @Override public void onCreatePreferences(Bundle state, String rootKey) {
        Context context = getContext();
        if (context == null) return;
        PreferenceManager manager = getPreferenceManager();
        manager.setPreferenceDataStore(new AdvancedDataStore(context));
        setPreferencesFromResource(R.xml.advanced_diagnostics_preferences, rootKey);
        configureLogLevel(context);
        bindActions(context);
        SettingsLayoutUtils.applyScreenLayout(this);
    }

    @Override public void onResume() {
        super.onResume();
        if (getActivity() != null) getActivity().setTitle(R.string.settings_destination_advanced_diagnostics);
    }

    private void configureLogLevel(Context context) {
        ListPreference row = findPreference("log_level");
        TermuxAppSharedPreferences app = TermuxAppSharedPreferences.build(context, true);
        if (row != null && app != null) configureLogLevelRow(context, row, app.getLogLevel());
        ListPreference api = findPreference(KEY_TERMUX_API_LOG_LEVEL);
        if (api == null) return;
        // Never the exiting builder: Termux:API being absent is ordinary here.
        TermuxAPIAppSharedPreferences apiPreferences = TermuxAPIAppSharedPreferences.build(context);
        api.setVisible(apiPreferences != null);
        if (apiPreferences != null) configureLogLevelRow(context, api, apiPreferences.getLogLevel(true));
    }

    private void configureLogLevelRow(Context context, ListPreference row, int logLevel) {
        CharSequence[] levels = Logger.getLogLevelsArray();
        row.setEntryValues(levels);
        row.setEntries(Logger.getLogLevelLabelsArray(context, levels, false));
        row.setValue(String.valueOf(logLevel));
        row.setDefaultValue(Logger.DEFAULT_LOG_LEVEL);
        row.setSummaryProvider(preference -> {
            CharSequence entry = ((ListPreference) preference).getEntry();
            return (entry == null ? getString(R.string.settings_log_level_normal) : entry)
                + (String.valueOf(Logger.DEFAULT_LOG_LEVEL).equals(((ListPreference) preference).getValue())
                    ? " · " + getString(R.string.settings_default_value) : "");
        });
    }

    private void bindActions(Context context) {
        click("privileged_backend_smoke_test", preference -> { runBackendTest(context); return true; });
        click("copy_diagnostics", preference -> {
            ClipboardManager clipboard = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
            if (clipboard != null) clipboard.setPrimaryClip(ClipData.newPlainText("Termux Launcher diagnostics", buildDiagnostics(context)));
            AppNotice.show(context, R.string.settings_diagnostics_copied, false);
            return true;
        });
        click("export_logs", preference -> { exportLogs(context); return true; });
        click("clear_logs", preference -> {
            new MaterialAlertDialogBuilder(context).setTitle(R.string.settings_clear_logs_title)
                .setMessage(R.string.settings_clear_logs_confirm).setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.settings_clear_logs_action, (dialog, which) -> clearLogs(context)).show();
            return true;
        });
    }

    private void click(String key, Preference.OnPreferenceClickListener listener) { Preference row = findPreference(key); if (row != null) row.setOnPreferenceClickListener(listener); }

    private void runBackendTest(Context context) {
        PrivilegedBackendManager.getInstance().executeCommand("id").thenAccept(output -> uiToast(context, R.string.settings_backend_test_passed))
            .exceptionally(error -> { uiToast(context, R.string.settings_backend_test_failed); return null; });
    }

    private void exportLogs(Context context) {
        new Thread(() -> {
            StringBuilder report = new StringBuilder(buildDiagnostics(context)).append("\n\n## Logcat\n\n```text\n");
            try {
                Process process = new ProcessBuilder("logcat", "-d", "-v", "threadtime").start();
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                    String line; while ((line = reader.readLine()) != null) report.append(line).append('\n');
                }
            } catch (Exception error) { report.append("Unable to collect logcat: ").append(error.getMessage()).append('\n'); }
            report.append("```\n");
            ReportInfo info = new ReportInfo("export logs", TermuxConstants.TERMUX_APP.TERMUX_SETTINGS_ACTIVITY_NAME,
                getString(R.string.settings_export_logs_title));
            info.setReportString(report.toString());
            if (isAdded() && getActivity() != null) getActivity().runOnUiThread(() -> ReportActivity.startReportActivity(context, info));
        }).start();
    }

    private void clearLogs(Context context) {
        new Thread(() -> {
            try { new ProcessBuilder("logcat", "-c").start().waitFor(); uiToast(context, R.string.settings_logs_cleared); }
            catch (Exception error) { uiToast(context, R.string.settings_clear_logs_failed); }
        }).start();
    }

    private String buildDiagnostics(Context context) {
        return "## Termux Launcher diagnostics\n\n" + TermuxUtils.getAppInfoMarkdownString(context,
            TermuxUtils.AppInfoMode.TERMUX_AND_PLUGIN_PACKAGES) + "\n\n" + AndroidUtils.getDeviceInfoMarkdownString(context, true)
            + "\n\nPrivileged backend: " + PrivilegedBackendManager.getInstance().getStatusDescription();
    }

    private void uiToast(Context context, int message) {
        if (isAdded() && getActivity() != null) getActivity().runOnUiThread(() -> AppNotice.show(context, message, false));
    }

    /**
     * The launcher's logging switches and level, and Termux:API's level, each written where its
     * owner reads it.
     */
    private static final class AdvancedDataStore extends PreferenceDataStore {
        private final Context context;
        @Nullable private final TermuxAppSharedPreferences app;
        @Nullable private final TermuxAPIAppSharedPreferences api;

        AdvancedDataStore(Context context) {
            this.context = context;
            app = TermuxAppSharedPreferences.build(context, true);
            api = TermuxAPIAppSharedPreferences.build(context);
        }

        @Override public void putBoolean(String key, boolean value) {
            if (app == null || key == null) return;
            switch (key) {
                case "terminal_view_key_logging_enabled": app.setTerminalViewKeyLoggingEnabled(value); break;
                case "glass_even_out_enabled": app.setGlassEvenOutEnabled(value); break;
                case "plugin_error_notifications_enabled": app.setPluginErrorNotificationsEnabled(value); break;
                case "crash_report_notifications_enabled": app.setCrashReportNotificationsEnabled(value); break;
                default: break;
            }
        }

        @Override public boolean getBoolean(String key, boolean fallback) {
            if (app == null || key == null) return fallback;
            switch (key) {
                case "terminal_view_key_logging_enabled": return app.isTerminalViewKeyLoggingEnabled();
                case "glass_even_out_enabled": return app.isGlassEvenOutEnabled();
                case "plugin_error_notifications_enabled": return app.arePluginErrorNotificationsEnabled(false);
                case "crash_report_notifications_enabled": return app.areCrashReportNotificationsEnabled(false);
                default: return fallback;
            }
        }

        @Override public void putString(String key, @Nullable String value) {
            if (key == null || value == null) return;
            if ("log_level".equals(key) && app != null) app.setLogLevel(context, Integer.parseInt(value));
            else if (KEY_TERMUX_API_LOG_LEVEL.equals(key) && api != null)
                api.setLogLevel(context, Integer.parseInt(value), true);
        }

        @Override public String getString(String key, @Nullable String fallback) {
            if ("log_level".equals(key) && app != null) return String.valueOf(app.getLogLevel());
            if (KEY_TERMUX_API_LOG_LEVEL.equals(key) && api != null) return String.valueOf(api.getLogLevel(true));
            return fallback;
        }
    }
}
