package com.termux.app.fragments.settings.termux;

import androidx.annotation.Keep;

/** Display troubleshooting. Reuses {@link X11DisplayPreferencesFragment}'s controller; only the XML and title differ. */
@Keep
public class X11DisplayTroubleshootingPreferencesFragment extends X11DisplayPreferencesFragment {
    @Override
    protected int preferencesXml() {
        return com.termux.R.xml.x11_display_troubleshooting_preferences;
    }

    @Override
    protected int titleRes() {
        return com.termux.R.string.settings_x11_sub_troubleshooting_title;
    }
}
