package com.termux.app.fragments.settings.termux;

import androidx.annotation.Keep;

/** Keyboard layouts, extra keys and credits. Reuses {@link KeyboardPreferencesFragment}'s controller; only the XML and title differ. */
@Keep
public class KeyboardLayoutPreferencesFragment extends KeyboardPreferencesFragment {
    @Override
    protected int preferencesXml() {
        return com.termux.R.xml.termux_keyboard_layout_preferences;
    }

    @Override
    protected int titleRes() {
        return com.termux.R.string.settings_keyboard_sub_layout_title;
    }
}
