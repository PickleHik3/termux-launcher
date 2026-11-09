package com.termux.app.fragments.settings.termux;

import androidx.annotation.Keep;

/** Terminal › Text &amp; display. Reuses {@link TerminalPreferencesFragment}'s store; only the XML and title differ. */
@Keep
public class TerminalTextPreferencesFragment extends TerminalPreferencesFragment {
    @Override
    protected int preferencesXml() {
        return com.termux.R.xml.terminal_text_preferences;
    }

    @Override
    protected int titleRes() {
        return com.termux.R.string.settings_terminal_sub_text_title;
    }
}
