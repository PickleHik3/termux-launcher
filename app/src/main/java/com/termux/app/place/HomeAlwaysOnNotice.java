package com.termux.app.place;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * The one line always on says about the widget grid, held while an editor is up. Always on is
 * turned on or off from the Layout editor, and the arrangement pass that moves the grid runs
 * under the editor's sheet; a notice shown then would stand over the editor's own controls. So it
 * waits for the editor to close and is shown on the arrangement that follows, and a later turn
 * off before then withdraws it.
 *
 * <p>Pure, so the hand-off is tested without a window.
 */
public final class HomeAlwaysOnNotice {

    @Nullable private String mPending;

    /**
     * Always on turned on, with what moved said in {@code text}.
     *
     * @return the text to show now, or null while it waits for the editor to close
     */
    @Nullable
    public String offer(@NonNull String text, boolean editorUp) {
        if (editorUp) {
            mPending = text;
            return null;
        }
        mPending = null;
        return text;
    }

    /** Always on turned off again: a notice still waiting says nothing true any more. */
    public void withdraw() {
        mPending = null;
    }

    /** The editor closed: the notice it held back, once, or null when there is none. */
    @Nullable
    public String onEditorClosed() {
        String text = mPending;
        mPending = null;
        return text;
    }

    /** Whether a notice is waiting for the editor to close. */
    public boolean isPending() {
        return mPending != null;
    }
}
