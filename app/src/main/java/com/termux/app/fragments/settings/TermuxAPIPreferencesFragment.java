package com.termux.app.fragments.settings;

import android.content.Context;
import android.os.Bundle;
import androidx.annotation.Keep;
import androidx.preference.PreferenceDataStore;
import androidx.preference.PreferenceManager;
import com.termux.R;
import com.termux.shared.termux.settings.preferences.TermuxAPIAppSharedPreferences;

@Keep
public class TermuxAPIPreferencesFragment extends MaterialPreferenceFragment {

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        Context context = getContext();
        if (context == null)
            return;
        PreferenceManager preferenceManager = getPreferenceManager();
        preferenceManager.setPreferenceDataStore(TermuxAPIPreferencesDataStore.getInstance(context));
        setPreferencesFromResource(R.xml.termux_api_preferences, rootKey);
        androidx.preference.Preference logs = findPreference("termux_api_logs");
        if (logs != null) {
            // Diagnostics, landed on Termux:API's log level.
            logs.setFragment("com.termux.app.fragments.settings.termux.AdvancedDiagnosticsPreferencesFragment");
            logs.getExtras().putString(com.termux.app.activities.SettingsActivity.EXTRA_SCROLL_TO_KEY,
                "termux_api_log_level");
        }
    }
}

class TermuxAPIPreferencesDataStore extends PreferenceDataStore {

    private final Context mContext;

    private final TermuxAPIAppSharedPreferences mPreferences;

    private static TermuxAPIPreferencesDataStore mInstance;

    private TermuxAPIPreferencesDataStore(Context context) {
        mContext = context;
        mPreferences = TermuxAPIAppSharedPreferences.build(context, true);
    }

    public static synchronized TermuxAPIPreferencesDataStore getInstance(Context context) {
        if (mInstance == null) {
            mInstance = new TermuxAPIPreferencesDataStore(context);
        }
        return mInstance;
    }
}
