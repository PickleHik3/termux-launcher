package com.termux.app.fragments.settings;

/**
 * A settings screen that can hold edits not yet saved, guarded by its own Back handler (the
 * extra-keys editor, the keyboard layout editor). Settings never navigates away from such a screen
 * behind that handler's back: a jump (a section picked in the two-pane list, a deep link) goes
 * through Back first, so the screen's own discard question is asked.
 */
public interface SettingsUnsavedChanges {

    /** True while leaving would lose edits; the screen's Back handler is armed exactly then. */
    boolean hasUnsavedChanges();
}
