package com.termux.app.chrome.wallpaper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Holds the one lock a double tap on the A-Z index asked for while the background settles, and
 * runs it exactly once: the frame clock's "lock is due", the fallback timer and a second double
 * tap can all arrive, and whichever comes first wins. Main thread only; no Android types.
 */
final class LockOnce {

    @Nullable private Runnable mPending;

    /** Whether a lock is waiting for its moment. */
    boolean isPending() {
        return mPending != null;
    }

    /** Waits with {@code lock}; false (and nothing replaced) when one is already waiting. */
    boolean arm(@NonNull Runnable lock) {
        if (mPending != null) return false;
        mPending = lock;
        return true;
    }

    /** Runs the waiting lock, once. Returns whether this call ran it. */
    boolean fire() {
        Runnable lock = mPending;
        if (lock == null) return false;
        mPending = null;
        lock.run();
        return true;
    }

    /** Forgets the waiting lock without running it. */
    void cancel() {
        mPending = null;
    }
}
