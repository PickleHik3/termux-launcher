package com.termux.app.fragments.settings.termux;

import androidx.annotation.Keep;

/** Double-tap to lock. Reuses {@link LauncherPreferencesFragment}'s controller; only the XML and title differ. */
@Keep
public class LauncherLockPreferencesFragment extends LauncherPreferencesFragment {
    @Override
    protected int preferencesXml() {
        return com.termux.R.xml.launcher_lock_preferences;
    }

    @Override
    protected int titleRes() {
        return com.termux.R.string.settings_apps_sub_lock_title;
    }
}
