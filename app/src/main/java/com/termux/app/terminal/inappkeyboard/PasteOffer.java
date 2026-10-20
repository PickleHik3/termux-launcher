package com.termux.app.terminal.inappkeyboard;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * What the in-app keyboard's paste key puts in: the clipboard, or the newest screenshot while it
 * is newer than the clipboard and fresh. Android has no "copy" for a screenshot, so this is how one
 * reaches a shell. Plain paste never asks; it always pastes the clipboard.
 */
public enum PasteOffer {
    /** The clipboard, which pastes as text (or nothing). */
    CLIPBOARD,
    /** The clipboard, which holds a picture and pastes it as a file. */
    CLIPBOARD_PICTURE,
    /** The newest screenshot, pasted as a file the way a clipboard picture is. */
    SCREENSHOT;

    /** A screenshot this old is never offered: by then it is not what the user means. */
    public static final long FRESH_MILLIS = 30L * 60 * 1000;

    /** The consumed id while no paste has taken a screenshot. MediaStore ids are positive. */
    public static final long NONE_CONSUMED = -1;

    /** Whether the paste key shows a photo for this offer. */
    public boolean showsPicture() {
        return this != CLIPBOARD;
    }

    /**
     * Newest wins: the screenshot when it was taken after the clip was set, is younger than
     * {@link #FRESH_MILLIS} and has not been taken by a paste already; otherwise the clipboard.
     *
     * @param clipMillis       when the clip was set, wall clock; 0 for no clip or no description
     * @param clipHoldsPicture whether the clipboard pastes as a picture
     * @param screenshot       the newest screenshot, or null while there is none (or the keys are down)
     * @param consumedId       the screenshot a paste already took, or {@link #NONE_CONSUMED}
     * @param nowMillis        wall clock
     */
    @NonNull
    public static PasteOffer decide(long clipMillis, boolean clipHoldsPicture,
                                    @Nullable RecentScreenshot.Shot screenshot, long consumedId,
                                    long nowMillis) {
        if (screenshot != null && screenshot.id != consumedId
            && screenshot.takenMillis > clipMillis
            && nowMillis - screenshot.takenMillis < FRESH_MILLIS)
            return SCREENSHOT;
        return clipHoldsPicture ? CLIPBOARD_PICTURE : CLIPBOARD;
    }

    /**
     * How long until {@code screenshot}, offered now, stops being fresh; the key goes back to the
     * clipboard then without anything else happening.
     */
    public static long millisUntilStale(@NonNull RecentScreenshot.Shot screenshot, long nowMillis) {
        return Math.max(0, screenshot.takenMillis + FRESH_MILLIS - nowMillis);
    }
}
