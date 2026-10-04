package com.termux.app.fragments.settings.termux;

import androidx.annotation.Keep;

/** Touch accuracy and key feedback. Reuses {@link KeyboardPreferencesFragment}'s controller; only the XML and title differ. */
@Keep
public class KeyboardTypingPreferencesFragment extends KeyboardPreferencesFragment {
    @Override
    protected int preferencesXml() {
        return com.termux.R.xml.termux_keyboard_typing_preferences;
    }

    @Override
    protected int titleRes() {
        return com.termux.R.string.settings_keyboard_sub_typing_title;
    }
}
