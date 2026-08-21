package com.termux.app.fragments.settings.termux;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.Toast;

import androidx.annotation.Keep;
import androidx.annotation.Nullable;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;
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

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

@Keep
public final class AdvancedDiagnosticsPreferencesFragment extends MaterialPreferenceFragment {
    private static final String STORE = "advanced_diagnostics";

    @Override public void onCreatePreferences(Bundle state, String rootKey) {
        Context context = getContext();
        if (context == null) return;
        PreferenceManager manager = getPreferenceManager();
        manager.setPreferenceDataStore(new AdvancedDataStore(context));
        setPreferencesFromResource(R.xml.advanced_diagnostics_preferences, rootKey);
        configureLogLevel(context);
        configureDeveloperVisibility();
        bindActions(context);
        SettingsLayoutUtils.applyScreenLayout(this);
    }

    @Override public void onResume() {
        super.onResume();
        if (getActivity() != null) getActivity().setTitle(R.string.settings_destination_advanced_diagnostics);
    }

    private void configureLogLevel(Context context) {
        ListPreference row = findPreference("log_level");
        if (row == null) return;
        DebuggingPreferencesFragment.setLogLevelListPreferenceData(row, context,
            com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences.build(context, true).getLogLevel());
        row.setEntries(Logger.getLogLevelLabelsArray(context, row.getEntryValues(), false));
        row.setSummaryProvider(preference -> {
            CharSequence entry = ((ListPreference) preference).getEntry();
            return (entry == null ? getString(R.string.settings_log_level_normal) : entry)
                + (String.valueOf(Logger.DEFAULT_LOG_LEVEL).equals(((ListPreference) preference).getValue())
                    ? " · " + getString(R.string.settings_default_value) : "");
        });
    }

    private void configureDeveloperVisibility() {
        androidx.preference.SwitchPreferenceCompat toggle = findPreference("developer_options_enabled");
        PreferenceCategory tools = findPreference("developer_tools");
        if (toggle == null || tools == null) return;
        tools.setVisible(toggle.isChecked());
        toggle.setOnPreferenceChangeListener((preference, value) -> { tools.setVisible(Boolean.TRUE.equals(value)); return true; });
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

    private static final class AdvancedDataStore extends PreferenceDataStore {
        private final DebuggingPreferencesDataStore debugging;
        private final SharedPreferences local;
        AdvancedDataStore(Context context) { debugging = DebuggingPreferencesDataStore.getInstance(context); local = context.getSharedPreferences(STORE, Context.MODE_PRIVATE); }
        @Override public void putBoolean(String key, boolean value) { if ("developer_options_enabled".equals(key)) local.edit().putBoolean(key, value).apply(); else debugging.putBoolean(key, value); }
        @Override public boolean getBoolean(String key, boolean fallback) { return "developer_options_enabled".equals(key) ? local.getBoolean(key, fallback) : debugging.getBoolean(key, fallback); }
        @Override public void putString(String key, @Nullable String value) { debugging.putString(key, value); }
        @Override public String getString(String key, @Nullable String fallback) { return debugging.getString(key, fallback); }
    }
}
