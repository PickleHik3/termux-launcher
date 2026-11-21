package com.termux.app.page;

import androidx.annotation.NonNull;

/** What a page may ask of its host; never the activity. */
public interface PageHost {

    /** One of the page's text fields took or lost focus: the host brings the right keyboard up for it. */
    void fieldFocusChanged(@NonNull Page page, boolean focused);

    /** Lowers the launcher's keyboard when it is up; false when it was not. A page's Back asks it. */
    default boolean lowerKeyboard() { return false; }

    /** A host that does nothing. */
    PageHost NONE = (page, focused) -> { };
}
