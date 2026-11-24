package com.termux.shared.termux.settings.registry;

/**
 * What a {@link SettingDeclaration} asks the {@link SettingsApplier} to refresh after a save. Each
 * names one of today's mechanisms; the app's {@link SettingsApplier.Host} carries it out.
 */
public enum SettingRefresh {
    /** The launcher's styling reload, in place: the Settings rows' debounced styling sync. */
    RESTYLE,
    /** The app's night mode, then the styling reload with a recreate: the Theme row's refresh. */
    THEME,
    /** The shared Text size re-applied to the chrome labels, the keyboard and the terminal. */
    TEXT_SIZE,
    /**
     * An existing preference listener already refreshes this key; the applier adds nothing. A
     * declaration naming it is checked by a test that the listener exists.
     */
    LISTENER
}
