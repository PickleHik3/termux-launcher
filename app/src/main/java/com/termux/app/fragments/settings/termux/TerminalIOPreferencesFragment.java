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
        // Same two switches as the Terminal & status page, so they answer the same Shizuku check.
        StatusWidgetPrivilegedGate.attach(context, findPreference("status_widget_cpu"));
        StatusWidgetPrivilegedGate.attach(context, findPreference("status_widget_ram"));
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

    /**
     * The one store the settings screens share.
     *
     * <p>Rebuilt when it is asked for from a different application context: the instance holds a
     * {@link TermuxAppSharedPreferences} bound to the context that first asked for it, and a store
     * pinned to a dead one writes into preferences nobody reads back. In the app that only happens
     * across a process restart, which takes the static with it; under Robolectric every test brings
     * a new Application while the static survives, and the stale store silently dropped writes.
     */
    public static synchronized TerminalIOPreferencesDataStore getInstance(Context context) {
        Context application = context.getApplicationContext();
        if (mInstance == null || mInstance.mContext.getApplicationContext() != application) {
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
            case "show_key_hints":
                mPreferences.setShowKeyHintsEnabled(value);
                break;
            case "pane_dwindle_default":
                mPreferences.setDwindleDefaultLayoutEnabled(value);
                break;
            case "pane_focus_grows":
                mPreferences.setFocusedPaneGrowsEnabled(value);
                break;
            case "pane_agent_api":
                mPreferences.setAgentPanesEnabled(value);
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
            case "status_widget_weather_fahrenheit":
                mPreferences.setStatusWidgetWeatherFahrenheit(value);
                break;
            case "lazy_mode":
                mPreferences.setLazyModeEnabled(value);
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
            case "show_key_hints":
                return mPreferences.isShowKeyHintsEnabled();
            case "pane_dwindle_default":
                return mPreferences.isDwindleDefaultLayoutEnabled();
            case "pane_focus_grows":
                return mPreferences.isFocusedPaneGrowsEnabled();
            case "pane_agent_api":
                return mPreferences.isAgentPanesEnabled();
            case "status_widget_cpu":
                return mPreferences.isStatusWidgetCpuEnabled();
            case "status_widget_ram":
                return mPreferences.isStatusWidgetRamEnabled();
            case "status_widget_weather":
                return mPreferences.isStatusWidgetWeatherEnabled();
            case "status_widget_weather_fahrenheit":
                return mPreferences.isStatusWidgetWeatherFahrenheit();
            case "lazy_mode":
                return mPreferences.isLazyModeEnabled();
            default:
                return defValue;
        }
    }

    @Override
    public void putString(String key, @Nullable String value) {
        if (mPreferences == null || key == null) return;
        if ("top_pane_clock_style".equals(key)) {
            mPreferences.setTopPaneClockStyle(value);
        } else if ("top_pane_clock_alignment".equals(key)) {
            mPreferences.setTopPaneClockAlignment(value);
        }
    }

    @Override
    public String getString(String key, @Nullable String defValue) {
        if (mPreferences == null || key == null) return defValue;
        if ("top_pane_clock_style".equals(key)) {
            return mPreferences.getTopPaneClockStyle();
        }
        if ("top_pane_clock_alignment".equals(key)) {
            return mPreferences.getTopPaneClockAlignment();
        }
        return defValue;
    }
}
