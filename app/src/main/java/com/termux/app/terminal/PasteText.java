package com.termux.app.terminal;

import androidx.annotation.NonNull;

/** Turns clipboard text into what a single-line intake accepts. */
public final class PasteText {

    private PasteText() {}

    /**
     * Strips {@code \r} outright and turns every {@code \n} into a space: every launcher intake
     * PASTE reaches — the palette's query, the drawer search, a folder or inline rename, find —
     * is one line, so a multi-line clipboard is folded onto it rather than truncated or rejected.
     */
    @NonNull
    public static String sanitizeSingleLine(@NonNull String text) {
        return text.replace("\r", "").replace('\n', ' ');
    }
}
