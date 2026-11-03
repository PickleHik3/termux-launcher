package com.termux.app.fragments.settings.termux;

import androidx.annotation.Keep;

/** Terminal › Clipboard &amp; compatibility. Reuses {@link TerminalPreferencesFragment}'s store; only the XML and title differ. */
@Keep
public class TerminalClipboardPreferencesFragment extends TerminalPreferencesFragment {
    @Override
    protected int preferencesXml() {
        return com.termux.R.xml.terminal_clipboard_preferences;
    }

    @Override
    protected int titleRes() {
        return com.termux.R.string.settings_terminal_sub_clipboard_title;
    }
}
