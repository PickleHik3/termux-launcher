package com.termux.app.page;

import androidx.annotation.NonNull;

/** What a page may ask of its host; never the activity. */
public interface PageHost {

    /** One of the page's text fields took or lost focus: the host brings the right keyboard up for it. */
    void fieldFocusChanged(@NonNull Page page, boolean focused);

    /** A host that does nothing. */
    PageHost NONE = (page, focused) -> { };
}
