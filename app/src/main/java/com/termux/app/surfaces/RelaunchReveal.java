package com.termux.app.surfaces;

import android.os.SystemClock;
import android.view.View;
import android.view.ViewTreeObserver;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * The first frames of an activity a relaunch built while the Appearance editor was open (the
 * Material You recolour after a wallpaper is applied): they are held back until the editor stands
 * on screen as it was carried, so the first frame drawn is the editor, never the plain home with
 * the editor cut in a few frames later.
 *
 * <p>A held window draws nothing, so the hold has three fail-safes: a time backstop, a count of
 * frames declined, and the reopen reporting that it will not show (closed, or never opened).
 * Without a pending handover nothing is held.</p>
 */
public final class RelaunchReveal {

    /**
     * The longest the first frame waits for the reopened editor, ms: the editor's wallpaper and
     * the picker load in about this long on pong; past it the home draws and the editor follows.
     */
    static final long BACKSTOP_MS = 300L;
    /** The most frames declined while waiting, at any refresh rate. */
    static final int MAX_HELD_FRAMES = 40;

    private RelaunchReveal() {}

    /** Whether the first frames are held at all: only with a carried surface to reopen. */
    static boolean holds(boolean handoverPending) {
        return handoverPending;
    }

    /**
     * Whether the frame about to draw is still held: until the editor is shown as carried (or will
     * not be), the backstop has passed, or enough frames were declined.
     */
    static boolean stillHeld(boolean shown, int heldFrames, long heldMs) {
        return !shown && heldFrames < MAX_HELD_FRAMES && heldMs < BACKSTOP_MS;
    }

    /**
     * Holds {@code view}'s window from drawing while a handover is pending, until {@link
     * Gate#release} or a fail-safe; null when nothing is held.
     */
    @Nullable
    public static Gate holdFirstDraw(@Nullable View view, boolean handoverPending) {
        if (view == null || !holds(handoverPending))
            return null;
        Gate gate = new Gate(view);
        view.getViewTreeObserver().addOnPreDrawListener(gate);
        // The time backstop holds even when no frame comes to ask.
        view.postDelayed(gate::release, BACKSTOP_MS);
        return gate;
    }

    /** A pre-draw hold on one window; {@link #release} lets it draw, once and for good. */
    public static final class Gate implements ViewTreeObserver.OnPreDrawListener {
        @NonNull private final View mView;
        private final long mStartMs = SystemClock.uptimeMillis();
        private int mHeldFrames;
        private boolean mShown;
        private boolean mReleased;

        Gate(@NonNull View view) {
            mView = view;
        }

        /** The editor is on screen as carried, or will not be: the next frame draws. */
        public void release() {
            mShown = true;
            if (mReleased)
                return;
            mReleased = true;
            ViewTreeObserver observer = mView.getViewTreeObserver();
            if (observer.isAlive())
                observer.removeOnPreDrawListener(this);
            mView.invalidate();
        }

        @Override
        public boolean onPreDraw() {
            if (mReleased)
                return true;
            if (stillHeld(mShown, mHeldFrames, SystemClock.uptimeMillis() - mStartMs)) {
                mHeldFrames++;
                return false;
            }
            release();
            return true;
        }
    }
}
