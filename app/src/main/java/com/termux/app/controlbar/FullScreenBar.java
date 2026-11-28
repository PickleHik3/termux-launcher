package com.termux.app.controlbar;

import androidx.annotation.NonNull;

/**
 * The control bar's full-screen states (#426), pure: the bar in its place, hidden down to the
 * handle while a window is full screen, or raised over the window by a slide up on the handle.
 * Every move answers whether the mode changed.
 */
public final class FullScreenBar {

    /** Where the bar stands. */
    public enum Mode { NORMAL, HIDDEN_HANDLE, RAISED }

    /** How long a raised bar stays up with no touch on it, in ms. */
    public static final long IDLE_MS = 4000L;

    @NonNull private Mode mMode = Mode.NORMAL;

    @NonNull
    public Mode mode() {
        return mMode;
    }

    /** Whether the bar itself is in sight: in its place, or raised. */
    public boolean barShown() {
        return mMode != Mode.HIDDEN_HANDLE;
    }

    /** Whether the thin handle stands where the bar was. */
    public boolean handleShown() {
        return mMode == Mode.HIDDEN_HANDLE;
    }

    /** Whether a window is full screen. */
    public boolean fullScreen() {
        return mMode != Mode.NORMAL;
    }

    /** A window went full screen: the bar goes down to the handle (a raised bar stays raised). */
    public boolean enter() {
        if (mMode != Mode.NORMAL) return false;
        mMode = Mode.HIDDEN_HANDLE;
        return true;
    }

    /** Full screen ended: the bar is back in its place. */
    public boolean leave() {
        if (mMode == Mode.NORMAL) return false;
        mMode = Mode.NORMAL;
        return true;
    }

    /** A slide up on the handle: the bar rises over the window. */
    public boolean slideUp() {
        if (mMode != Mode.HIDDEN_HANDLE) return false;
        mMode = Mode.RAISED;
        return true;
    }

    /** A tool, mark or app of the raised bar was used: it drops back to the handle. */
    public boolean used() {
        return drop();
    }

    /** A touch outside the raised bar: it drops back to the handle. */
    public boolean tapOutside() {
        return drop();
    }

    /** The idle time ran out on the raised bar: it drops back to the handle. */
    public boolean idle() {
        return drop();
    }

    private boolean drop() {
        if (mMode != Mode.RAISED) return false;
        mMode = Mode.HIDDEN_HANDLE;
        return true;
    }
}
