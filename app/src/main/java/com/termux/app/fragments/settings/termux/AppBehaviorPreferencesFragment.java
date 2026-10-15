package com.termux.app.fragments.settings.termux;

import android.content.Context;
import android.os.Bundle;

import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.PreferenceDataStore;
import androidx.preference.SwitchPreferenceCompat;

import com.termux.app.TermuxActivity;
import com.termux.app.settings.LowMemoryPreset;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;

import com.termux.R;
import com.termux.app.fragments.settings.MaterialPreferenceFragment;
import com.termux.app.fragments.settings.SettingsLayoutUtils;

/**
 * App behavior: the launcher-wide switches that used to be scattered across Appearance, Apps,
 * Keyboard and Terminal (idle activity, the three vibration controls, Recents). It adds no
 * preference of its own: every row writes the key it always wrote, through the store that already
 * owned it, so a value set here or on any older page is the same value.
 */
@Keep
public class AppBehaviorPreferencesFragment extends MaterialPreferenceFragment {

    private static final String KEY_LOW_MEMORY_PRESET = "low_memory_preset";

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        Context context = getContext();
        if (context == null) return;
        getPreferenceManager().setPreferenceDataStore(new BehaviorDataStore(context));
        setPreferencesFromResource(R.xml.app_behavior_preferences, rootKey);
        SettingsLayoutUtils.applyScreenLayout(this);
        SwitchPreferenceCompat preset = findPreference(KEY_LOW_MEMORY_PRESET);
        if (preset != null) {
            preset.setOnPreferenceChangeListener((row, newValue) -> {
                TermuxAppSharedPreferences prefs = TermuxAppSharedPreferences.build(context, false);
                if (prefs == null) return false;
                if (Boolean.TRUE.equals(newValue)) LowMemoryPreset.turnOn(prefs);
                else LowMemoryPreset.turnOff(prefs);
                SwitchPreferenceCompat lazy = findPreference("lazy_mode");
                if (lazy != null) lazy.setChecked(prefs.isLazyModeEnabled());
                // The launcher restyles itself when the user returns to it.
                TermuxActivity.requestTermuxActivityStylingOnNextResume(context, false);
                return true;
            });
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (getActivity() != null) getActivity().setTitle(R.string.settings_destination_app_behavior);
    }

    /** Keyboard vibration belongs to the keyboard's store; everything else to the launcher's. */
    private static final class BehaviorDataStore extends PreferenceDataStore {
        private static final String KEY_KEYBOARD_HAPTICS = "in_app_keyboard_haptics_enabled";
        @NonNull private final Context context;
        @NonNull private final TermuxStylePreferencesDataStore style;
        @NonNull private final KeyboardPreferencesDataStore keyboard;

        BehaviorDataStore(@NonNull Context context) {
            this.context = context;
            style = TermuxStylePreferencesDataStore.getInstance(context);
            keyboard = KeyboardPreferencesDataStore.getInstance(context);
        }

        @Override
        public void putBoolean(@Nullable String key, boolean value) {
            if (KEY_KEYBOARD_HAPTICS.equals(key)) keyboard.putBoolean(key, value);
            else if (KEY_LOW_MEMORY_PRESET.equals(key)) {
                // turnOn and turnOff, run by the switch's change listener, have set it already.
                TermuxAppSharedPreferences prefs = TermuxAppSharedPreferences.build(context, false);
                if (prefs != null) prefs.setLowMemoryPresetEnabled(value);
            } else style.putBoolean(key, value);
        }

        @Override
        public boolean getBoolean(@Nullable String key, boolean defValue) {
            if (KEY_LOW_MEMORY_PRESET.equals(key)) {
                TermuxAppSharedPreferences prefs = TermuxAppSharedPreferences.build(context, false);
                return prefs == null ? defValue : prefs.isLowMemoryPresetEnabled();
            }
            return KEY_KEYBOARD_HAPTICS.equals(key) ? keyboard.getBoolean(key, defValue)
                : style.getBoolean(key, defValue);
        }
    }
}
