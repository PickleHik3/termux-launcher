package com.termux.app.fragments.settings.termux;

import androidx.annotation.Keep;

/**
 * Old name of the Linux apps page, kept so an Intent or a saved Back stack that names it still
 * opens. Its rows folded into Terminal › Linux apps ({@link X11DisplayPreferencesFragment}), which
 * this shows; {@code SettingsTree} maps the name there, so a fresh push never instantiates it.
 */
@Keep
public class X11DisplayLinuxAppsPreferencesFragment extends X11DisplayPreferencesFragment {
}
