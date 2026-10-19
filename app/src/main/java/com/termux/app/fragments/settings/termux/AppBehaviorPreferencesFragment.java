package com.termux.app.fragments.settings.termux;

import androidx.annotation.Keep;

/**
 * Old name of the App behavior page, kept so an Intent or a saved Back stack that names it still
 * opens. Its rows are General's ({@link GeneralPreferencesFragment}), which this shows;
 * SettingsTree maps the name there, so a fresh push never instantiates it.
 */
@Keep
public class AppBehaviorPreferencesFragment extends GeneralPreferencesFragment {
}
