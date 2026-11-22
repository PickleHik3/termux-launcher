package com.termux.app.wall.pageswitch;

import androidx.annotation.NonNull;

/**
 * Whether the page bar popped up over the extra keys is away, under the finger that opened it, or
 * left open for one use. Pure: the controller reports what happened, this says what the popup is.
 * A release on the key without a slide leaves it open; a slide's release, a tap anywhere, Back, a
 * page change by other means and a cancel all put it away.
 */
final class PageBarPopup {

    enum State {
        /** Not shown, nothing created or held. */
        AWAY,
        /** The finger that touched the key is down. */
        TOUCHING,
        /** Released without a slide: stays up until a glyph is tapped or it is put away. */
        OPEN
    }

    @NonNull private State mState = State.AWAY;

    @NonNull State state() {
        return mState;
    }

    /** Shown at all: the window gives its touch to the views first. */
    boolean shown() {
        return mState != State.AWAY;
    }

    /** The finger's touch on the key still feeds the bar. */
    boolean touching() {
        return mState == State.TOUCHING;
    }

    void keyDown() {
        mState = State.TOUCHING;
    }

    /** The key's finger lifted; {@code slid} when it had begun to slide. */
    void keyReleased(boolean slid) {
        if (mState != State.TOUCHING) return;
        mState = slid ? State.AWAY : State.OPEN;
    }

    /** A tap, a commit or a cancel ended a touch on the bar; a tap outside, Back, a page change, a pause or a detach too. */
    void putAway() {
        mState = State.AWAY;
    }
}
