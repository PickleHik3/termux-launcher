package com.termux.app.fragments.settings.termux;

import androidx.annotation.Keep;

/** App search: the terminal prefix and usage ranking. Reuses {@link LauncherPreferencesFragment}'s controller; only the XML and title differ. */
@Keep
public class LauncherSearchPreferencesFragment extends LauncherPreferencesFragment {
    @Override
    protected int preferencesXml() {
        return com.termux.R.xml.launcher_search_preferences;
    }

    @Override
    protected int titleRes() {
        return com.termux.R.string.settings_apps_sub_search_title;
    }
}
