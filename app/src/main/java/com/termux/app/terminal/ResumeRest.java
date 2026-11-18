package com.termux.app.terminal;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.page.Rest;

/**
 * Whether the panes hear a new size when the launcher comes back to the screen. The stop records
 * the front page's rest and the room its panes were laid out in; once the layout it returns to
 * has come, the same rest in the same room means no pane is resized, and anything else means one
 * re-measure. Pure.
 */
public final class ResumeRest {

    /** What the return does to the panes' sizes. */
    public enum Decision { KEEP, RESIZE }

    private boolean mRecorded;
    @Nullable private Rest mRest;
    private long mRoomKey;

    /** The stop: the front page's rest (null when the front is not a contract page) and the room. */
    public void record(@Nullable Rest rest, long roomKey) {
        mRecorded = true;
        mRest = rest;
        mRoomKey = roomKey;
    }

    /** Forget the record (the window goes, or the return was decided). */
    public void clear() {
        mRecorded = false;
        mRest = null;
    }

    /** Compare the layout it came back to with the recorded one. */
    @NonNull
    public Decision decide(@Nullable Rest rest, long roomKey) {
        if (!mRecorded) return Decision.RESIZE;
        boolean sameRest = mRest == null ? rest == null : mRest.equals(rest);
        return sameRest && roomKey == mRoomKey ? Decision.KEEP : Decision.RESIZE;
    }

    /** Why {@link #decide} answered as it did, for the log. */
    @NonNull
    public String reason(@Nullable Rest rest, long roomKey) {
        if (!mRecorded) return "nothing recorded";
        boolean sameRest = mRest == null ? rest == null : mRest.equals(rest);
        if (!sameRest) return "rest " + mRest + " -> " + rest;
        if (roomKey != mRoomKey) return "room";
        return "same rest and room";
    }
}
