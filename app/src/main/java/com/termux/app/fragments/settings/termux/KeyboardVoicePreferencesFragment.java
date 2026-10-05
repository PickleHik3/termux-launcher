package com.termux.app.fragments.settings.termux;

import androidx.annotation.Keep;

/** Voice input. Reuses {@link KeyboardPreferencesFragment}'s controller; only the XML and title differ. */
@Keep
public class KeyboardVoicePreferencesFragment extends KeyboardPreferencesFragment {
    @Override
    protected int preferencesXml() {
        return com.termux.R.xml.termux_keyboard_voice_preferences;
    }

    @Override
    protected int titleRes() {
        return com.termux.R.string.settings_keyboard_sub_voice_title;
    }
}
