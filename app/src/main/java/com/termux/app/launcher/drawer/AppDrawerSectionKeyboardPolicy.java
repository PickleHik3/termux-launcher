package com.termux.app.launcher.drawer;

import androidx.annotation.NonNull;

/**
 * Whether a section change of Default sends the search keyboard away, pure. Categories has no
 * search field, so a keyboard still up when the switch settles on it goes, the built-in keyboard
 * and the system one alike. Moving to Apps never raises it again: the search capsule does.
 */
final class AppDrawerSectionKeyboardPolicy {

    private AppDrawerSectionKeyboardPolicy() {}

    /** {@code settledOn} is the section the switch committed to; {@code keyboardShown} is the search keyboard being up. */
    static boolean hidesKeyboard(@NonNull AppDrawerSection settledOn, boolean keyboardShown) {
        return keyboardShown && settledOn == AppDrawerSection.CATEGORIES;
    }
}
