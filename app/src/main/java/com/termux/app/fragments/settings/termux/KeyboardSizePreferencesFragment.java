package com.termux.app.fragments.settings.termux;

import androidx.annotation.Keep;

/** Floating and split keyboard size. Reuses {@link KeyboardPreferencesFragment}'s controller; only the XML and title differ. */
@Keep
public class KeyboardSizePreferencesFragment extends KeyboardPreferencesFragment {
    @Override
    protected int preferencesXml() {
        return com.termux.R.xml.termux_keyboard_size_preferences;
    }

    @Override
    protected int titleRes() {
        return com.termux.R.string.settings_keyboard_sub_size_title;
    }
}
