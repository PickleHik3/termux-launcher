package com.termux.app.page;

import android.view.View;

import androidx.annotation.NonNull;

/** What a page may ask of its host; never the activity. */
public interface PageHost {

    /** One of the page's text fields took or lost focus: the host brings the right keyboard up for it. */
    void fieldFocusChanged(@NonNull Page page, boolean focused);

    /** Lowers the launcher's keyboard when it is up; false when it was not. A page's Back asks it. */
    default boolean lowerKeyboard() { return false; }

    /**
     * Whether the user's keyboard is up: the launcher's, or the phone's. A page asks before
     * raising it, to know whether it did.
     */
    default boolean keyboardUp() { return false; }

    /** Whether the launcher's own keyboard is the user's keyboard, rather than the phone's. */
    default boolean launcherKeyboard() { return false; }

    /**
     * Shows the phone's keyboard for {@code view}, which has taken focus and serves its input
     * connection. The window's system-IME Hold is taken first, so the keyboard's insets move the
     * layout.
     */
    default void showPhoneKeyboard(@NonNull View view) { }

    /** Hides the phone's keyboard a page showed for {@code view}. */
    default void hidePhoneKeyboard(@NonNull View view) { }

    /** A host that does nothing. */
    PageHost NONE = (page, focused) -> { };
}
