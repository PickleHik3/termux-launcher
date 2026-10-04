package com.termux.app.fragments.settings.termux;

import androidx.annotation.Keep;

/** Display resolution and scaling. Reuses {@link X11DisplayPreferencesFragment}'s controller; only the XML and title differ. */
@Keep
public class X11DisplayResolutionPreferencesFragment extends X11DisplayPreferencesFragment {
    @Override
    protected int preferencesXml() {
        return com.termux.R.xml.x11_display_resolution_preferences;
    }

    @Override
    protected int titleRes() {
        return com.termux.R.string.settings_x11_sub_resolution_title;
    }
}
