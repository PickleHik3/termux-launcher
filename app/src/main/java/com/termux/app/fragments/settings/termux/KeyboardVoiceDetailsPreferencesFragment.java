package com.termux.app.fragments.settings.termux;

import androidx.annotation.Keep;

/** What dictation cleanup does, when it is off, and how shell commands are formatted. */
@Keep
public class KeyboardVoiceDetailsPreferencesFragment extends KeyboardPreferencesFragment {
    @Override
    protected int preferencesXml() {
        return com.termux.R.xml.termux_keyboard_voice_details_preferences;
    }

    @Override
    protected int titleRes() {
        return com.termux.R.string.settings_voice_details_title;
    }
}
