package com.termux.app.wayland;

import androidx.annotation.Nullable;

/**
 * The pure part of the clipboard bridge between Android and the Linux windows: which texts
 * cross, and the register that stops a text bouncing back to where it came from.
 */
final class LinuxClipboardText {

    /**
     * The longest text that crosses, in chars. A binder text is UTF-16 and the async buffer is
     * about 512 KB, so this stays well under it; the host's own cap (128 KiB of UTF-8) is checked
     * on its side.
     */
    static final int MAX_CHARS = 100_000;

    private String mLast;

    /** Whether a text may cross: not null, not empty, not over {@link #MAX_CHARS}. */
    static boolean acceptable(@Nullable String text) {
        return text != null && !text.isEmpty() && text.length() <= MAX_CHARS;
    }

    /**
     * Records the text as crossing now (either way); false when it is the text that crossed
     * last, which is not sent on.
     */
    boolean crossing(String text) {
        if (text.equals(mLast)) return false;
        mLast = text;
        return true;
    }
}
