package com.termux.app;

import androidx.annotation.NonNull;

/**
 * Tells a resume that only ends a pause begun in the same main-loop message.
 *
 * <p>A new intent delivered to the resumed launcher (singleTask: HOME pressed while already home,
 * or a second start racing a cold start) arrives as one client transaction that pauses the
 * activity, hands it the intent and resumes it again, all inside one message. Nothing outside
 * this process can have run in between, so none of what onResume refreshes "because it may have
 * changed while we were away" can have changed. Any pause that lasted past its own message — an
 * activity on top, a dialog, the screen going off — is not transient, and its resume does all of
 * its work as before.
 *
 * <p>The pause marks itself and posts the unmarking at the front of the main queue, so it is gone
 * before any later message — a later transaction included — can run.
 */
final class TransientPauseTracker {

    /** Posts {@code runnable} ahead of everything already queued on the main looper. */
    interface FrontPoster {
        void postAtFrontOfQueue(@NonNull Runnable runnable);
    }

    @NonNull private final FrontPoster mPoster;
    private boolean mPausedThisMessage;
    private final Runnable mMessageEnded = () -> mPausedThisMessage = false;

    TransientPauseTracker(@NonNull FrontPoster poster) {
        mPoster = poster;
    }

    /** Call from onPause. */
    void onPause() {
        if (mPausedThisMessage) return;
        mPausedThisMessage = true;
        mPoster.postAtFrontOfQueue(mMessageEnded);
    }

    /** Whether the resume now running ends a pause begun in this same message. */
    boolean resumesTransientPause() {
        return mPausedThisMessage;
    }
}
