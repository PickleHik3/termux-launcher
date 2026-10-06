package com.termux.app;

import androidx.annotation.NonNull;

/**
 * A mark that lasts until the end of the main-loop message it was set in.
 *
 * <p>Android runs a whole client transaction in one message: a launch's onCreate, onStart and
 * onResume, or the pause, onNewIntent and resume that deliver a new intent to the resumed
 * launcher (singleTask: HOME pressed while already home, or a second start racing a cold start).
 * Nothing outside this process can run inside that message, so a resume that finds a mark set
 * by the onCreate or onPause of its own message knows that none of what onResume refreshes
 * "because it may have changed while we were away" can have changed. Any pause that lasted past
 * its own message — an activity or dialog on top, the screen going off — leaves no mark, and its
 * resume does all of its work as before.
 *
 * <p>The mark posts its own removal at the front of the main queue, so it is gone before any
 * later message — a later transaction included — can run.
 */
final class SameMessageMark {

    /** Posts {@code runnable} ahead of everything already queued on the main looper. */
    interface FrontPoster {
        void postAtFrontOfQueue(@NonNull Runnable runnable);
    }

    @NonNull private final FrontPoster mPoster;
    private boolean mMarked;
    private final Runnable mMessageEnded = () -> mMarked = false;

    SameMessageMark(@NonNull FrontPoster poster) {
        mPoster = poster;
    }

    /** Sets the mark for the rest of the current message. */
    void mark() {
        if (mMarked) return;
        mMarked = true;
        mPoster.postAtFrontOfQueue(mMessageEnded);
    }

    /** Whether {@link #mark()} was called earlier in the message now running. */
    boolean isMarked() {
        return mMarked;
    }
}
