package com.termux.app.fragments.settings.termux;

import androidx.annotation.Keep;

/** Terminal › Windows &amp; panes. Reuses {@link TerminalPreferencesFragment}'s store; only the XML and title differ. */
@Keep
public class TerminalWindowsPreferencesFragment extends TerminalPreferencesFragment {
    @Override
    protected int preferencesXml() {
        return com.termux.R.xml.terminal_windows_preferences;
    }

    @Override
    protected int titleRes() {
        return com.termux.R.string.settings_terminal_sub_windows_title;
    }
}
