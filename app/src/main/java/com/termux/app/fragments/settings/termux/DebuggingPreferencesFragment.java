package com.termux.app.fragments.settings.termux;

import android.content.Context;
import android.os.Bundle;
import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceDataStore;
import androidx.preference.PreferenceManager;
import com.termux.app.notice.AppNotice;
import com.termux.R;
import com.termux.app.fragments.settings.MaterialPreferenceFragment;
import com.termux.app.fragments.settings.SettingsLayoutUtils;
import com.termux.privileged.PrivilegedBackend;
import com.termux.privileged.PrivilegedBackendManager;
import com.termux.privileged.ShizukuBackend;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;
import com.termux.shared.logger.Logger;

@Keep
public class DebuggingPreferencesFragment extends MaterialPreferenceFragment {
    private static final String LOG_TAG = "DebuggingPrefs";

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        Context context = getContext();
        if (context == null)
            return;
        PreferenceManager preferenceManager = getPreferenceManager();
        preferenceManager.setPreferenceDataStore(DebuggingPreferencesDataStore.getInstance(context));
        setPreferencesFromResource(R.xml.termux_debugging_preferences, rootKey);
        configureLoggingPreferences(context);
        configurePrivilegedBackendSmokeTestPreference(context);
        SettingsLayoutUtils.applyScreenLayout(this);
    }

    private void configureLoggingPreferences(@NonNull Context context) {
        PreferenceCategory loggingCategory = findPreference("logging");
        if (loggingCategory == null)
            return;
        ListPreference logLevelListPreference = findPreference("log_level");
        if (logLevelListPreference != null) {
            TermuxAppSharedPreferences preferences = TermuxAppSharedPreferences.build(context, true);
            if (preferences == null)
                return;
            setLogLevelListPreferenceData(logLevelListPreference, context, preferences.getLogLevel());
            loggingCategory.addPreference(logLevelListPreference);
        }
    }

    public static ListPreference setLogLevelListPreferenceData(ListPreference logLevelListPreference, Context context, int logLevel) {
        if (logLevelListPreference == null)
            logLevelListPreference = new ListPreference(context);
        CharSequence[] logLevels = Logger.getLogLevelsArray();
        CharSequence[] logLevelLabels = Logger.getLogLevelLabelsArray(context, logLevels, true);
        logLevelListPreference.setEntryValues(logLevels);
        logLevelListPreference.setEntries(logLevelLabels);
        logLevelListPreference.setValue(String.valueOf(logLevel));
        logLevelListPreference.setDefaultValue(Logger.DEFAULT_LOG_LEVEL);
        return logLevelListPreference;
    }

    private void configurePrivilegedBackendSmokeTestPreference(@NonNull Context context) {
        Preference smokeTestPreference = findPreference("privileged_backend_smoke_test");
        if (smokeTestPreference == null)
            return;

        smokeTestPreference.setOnPreferenceClickListener(preference -> {
            runPrivilegedBackendSmokeTest(context);
            return true;
        });
    }

    private void runPrivilegedBackendSmokeTest(@NonNull Context context) {
        PrivilegedBackendManager manager = PrivilegedBackendManager.getInstance();
        Logger.logInfo(LOG_TAG, "[SmokeTest] Triggered from settings");
        Logger.logInfo(LOG_TAG, "[SmokeTest] BackendType=" + manager.getBackendType()
            + ", BackendState=" + manager.getBackendState()
            + ", StatusReason=" + manager.getStatusReason()
            + ", StatusMessage=" + manager.getStatusMessage()
            + ", Description=" + manager.getStatusDescription());

        if (manager.getBackendType() == PrivilegedBackend.Type.SHIZUKU && !manager.isPrivilegedAvailable()) {
            boolean requested = manager.requestPrivilegedPermission(ShizukuBackend.PERMISSION_REQUEST_CODE);
            Logger.logInfo(LOG_TAG, "[SmokeTest] Requested Shizuku permission: " + requested);
            AppNotice.show(context,
                requested ? "Requested Shizuku permission. Re-run smoke test after granting." : "Shizuku permission not available/requested.", true);
            if (requested) return;
        }

        manager.executeCommand("id").thenAccept(output -> {
            Logger.logInfo(LOG_TAG, "[SmokeTest] Command=id output=" + output);
            if (isAdded() && getActivity() != null) {
                getActivity().runOnUiThread(() ->
                    AppNotice.show(context, "Smoke test complete. Check logcat for details.", false)
                );
            }
        }).exceptionally(throwable -> {
            Logger.logErrorExtended(LOG_TAG, "[SmokeTest] Command failed: " + throwable.getMessage());
            if (isAdded() && getActivity() != null) {
                getActivity().runOnUiThread(() ->
                    AppNotice.show(context, "Smoke test failed. Check logcat.", false)
                );
            }
            return null;
        });
    }
}

class DebuggingPreferencesDataStore extends PreferenceDataStore {

    private final Context mContext;

    private final TermuxAppSharedPreferences mPreferences;

    private static DebuggingPreferencesDataStore mInstance;

    private DebuggingPreferencesDataStore(Context context) {
        mContext = context;
        mPreferences = TermuxAppSharedPreferences.build(context, true);
    }

    public static synchronized DebuggingPreferencesDataStore getInstance(Context context) {
        if (mInstance == null) {
            mInstance = new DebuggingPreferencesDataStore(context);
        }
        return mInstance;
    }

    @Override
    @Nullable
    public String getString(String key, @Nullable String defValue) {
        if (mPreferences == null)
            return defValue;
        if (key == null)
            return defValue;
        switch(key) {
            case "log_level":
                return String.valueOf(mPreferences.getLogLevel());
            default:
                return defValue;
        }
    }

    @Override
    public void putString(String key, @Nullable String value) {
        if (mPreferences == null)
            return;
        if (key == null)
            return;
        switch(key) {
            case "log_level":
                if (value != null) {
                    mPreferences.setLogLevel(mContext, Integer.parseInt(value));
                }
                break;
            default:
                break;
        }
    }

    @Override
    public void putBoolean(String key, boolean value) {
        if (mPreferences == null)
            return;
        if (key == null)
            return;
        switch(key) {
            case "terminal_view_key_logging_enabled":
                mPreferences.setTerminalViewKeyLoggingEnabled(value);
                break;
            case "plugin_error_notifications_enabled":
                mPreferences.setPluginErrorNotificationsEnabled(value);
                break;
            case "crash_report_notifications_enabled":
                mPreferences.setCrashReportNotificationsEnabled(value);
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
            case "terminal_view_key_logging_enabled":
                return mPreferences.isTerminalViewKeyLoggingEnabled();
            case "plugin_error_notifications_enabled":
                return mPreferences.arePluginErrorNotificationsEnabled(false);
            case "crash_report_notifications_enabled":
                return mPreferences.areCrashReportNotificationsEnabled(false);
            default:
                return defValue;
        }
    }
}
