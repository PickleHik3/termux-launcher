package com.termux.app.fragments.settings.termux;

import androidx.annotation.Keep;

/** Hardware keyboard behaviour. Reuses {@link KeyboardPreferencesFragment}'s controller; only the XML and title differ. */
@Keep
public class KeyboardHardwarePreferencesFragment extends KeyboardPreferencesFragment {
    @Override
    protected int preferencesXml() {
        return com.termux.R.xml.termux_keyboard_hardware_preferences;
    }

    @Override
    protected int titleRes() {
        return com.termux.R.string.settings_keyboard_sub_hardware_title;
    }
}
