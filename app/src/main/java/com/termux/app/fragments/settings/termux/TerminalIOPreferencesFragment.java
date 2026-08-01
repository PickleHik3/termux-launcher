package com.termux.app.fragments.settings.termux;

import android.content.Context;
import android.os.Bundle;
import androidx.annotation.Keep;
import androidx.annotation.Nullable;
import androidx.preference.PreferenceDataStore;
import androidx.preference.PreferenceManager;
import com.termux.R;
import com.termux.app.fragments.settings.MaterialPreferenceFragment;
import com.termux.app.fragments.settings.SettingsLayoutUtils;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;

@Keep
public class TerminalIOPreferencesFragment extends MaterialPreferenceFragment {

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        Context context = getContext();
        if (context == null)
            return;
        PreferenceManager preferenceManager = getPreferenceManager();
        preferenceManager.setPreferenceDataStore(TerminalIOPreferencesDataStore.getInstance(context));
        setPreferencesFromResource(R.xml.termux_terminal_io_preferences, rootKey);
        SettingsLayoutUtils.applyScreenLayout(this);
    }
}

class TerminalIOPreferencesDataStore extends PreferenceDataStore {

    private final Context mContext;

    private final TermuxAppSharedPreferences mPreferences;

    private static TerminalIOPreferencesDataStore mInstance;

    private TerminalIOPreferencesDataStore(Context context) {
        mContext = context;
        mPreferences = TermuxAppSharedPreferences.build(context, true);
    }

    public static synchronized TerminalIOPreferencesDataStore getInstance(Context context) {
        if (mInstance == null) {
            mInstance = new TerminalIOPreferencesDataStore(context);
        }
        return mInstance;
    }

    @Override
    public void putBoolean(String key, boolean value) {
        if (mPreferences == null)
            return;
        if (key == null)
            return;
        switch(key) {
            case "soft_keyboard_enabled":
                mPreferences.setSoftKeyboardEnabled(value);
                break;
            case "soft_keyboard_enabled_only_if_no_hardware":
                mPreferences.setSoftKeyboardEnabledOnlyIfNoHardware(value);
                break;
            case "compatibility_mode":
                mPreferences.setCompatibilityModeEnabled(value);
                break;
            case "top_pane_clock_am_pm":
                mPreferences.setTopPaneClockAmPmEnabled(value);
                break;
            case "status_widget_cpu":
                mPreferences.setStatusWidgetCpuEnabled(value);
                break;
            case "status_widget_ram":
                mPreferences.setStatusWidgetRamEnabled(value);
                break;
            case "status_widget_weather":
                mPreferences.setStatusWidgetWeatherEnabled(value);
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
            case "soft_keyboard_enabled":
                return mPreferences.isSoftKeyboardEnabled();
            case "soft_keyboard_enabled_only_if_no_hardware":
                return mPreferences.isSoftKeyboardEnabledOnlyIfNoHardware();
            case "compatibility_mode":
                return mPreferences.isCompatibilityModeEnabled();
            case "top_pane_clock_am_pm":
                return mPreferences.isTopPaneClockAmPmEnabled();
            case "status_widget_cpu":
                return mPreferences.isStatusWidgetCpuEnabled();
            case "status_widget_ram":
                return mPreferences.isStatusWidgetRamEnabled();
            case "status_widget_weather":
                return mPreferences.isStatusWidgetWeatherEnabled();
            default:
                return defValue;
        }
    }

    @Override
    public void putString(String key, @Nullable String value) {
        if (mPreferences == null || key == null) return;
        if ("top_pane_clock_style".equals(key)) {
            mPreferences.setTopPaneClockStyle(value);
        }
    }

    @Override
    public String getString(String key, @Nullable String defValue) {
        if (mPreferences == null || key == null) return defValue;
        if ("top_pane_clock_style".equals(key)) {
            return mPreferences.getTopPaneClockStyle();
        }
        return defValue;
    }
}
