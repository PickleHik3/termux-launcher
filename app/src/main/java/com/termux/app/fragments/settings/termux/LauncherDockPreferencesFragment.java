package com.termux.app.fragments.settings.termux;

import androidx.annotation.Keep;

/** Dock: pinned apps and the usage-ranked page. Reuses {@link LauncherPreferencesFragment}'s controller; only the XML and title differ. */
@Keep
public class LauncherDockPreferencesFragment extends LauncherPreferencesFragment {
    @Override
    protected int preferencesXml() {
        return com.termux.R.xml.launcher_dock_preferences;
    }

    @Override
    protected int titleRes() {
        return com.termux.R.string.settings_apps_sub_dock_title;
    }
}
