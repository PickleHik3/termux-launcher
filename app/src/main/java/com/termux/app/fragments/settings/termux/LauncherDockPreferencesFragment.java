package com.termux.app.fragments.settings.termux;

import androidx.annotation.Keep;

/**
 * Old name of the Dock page, kept so an Intent or a saved Back stack that names it still opens.
 * The dock's rows folded into Dock ({@link LauncherPreferencesFragment}), which this
 * shows; {@code SettingsTree} maps the name there, so a fresh push never instantiates it.
 */
@Keep
public class LauncherDockPreferencesFragment extends LauncherPreferencesFragment {
}
