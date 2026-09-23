package com.termux.app.terminal;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Which session, if any, currently holds the in-app keyboard down through
 * {@code keyboard.hide --hold} — as opposed to a plain {@code keyboard.hide}, which is a one-off
 * the way {@code source=manual}/{@code source=focus} already are and needs no tracking here.
 *
 * <p>A hold exists so a program running in a session (tlstore-ui today) can ask for the keyboard
 * to stay out of the way for as long as it is on screen, without either side having to poll the
 * other. {@code keyboard.show} always clears it, whoever calls it. If nobody ever calls it back —
 * the holding session's shell simply ends, held open or not, while the hold is still in place —
 * {@link #releaseOnSessionFinished} tells the caller to show the keyboard itself, since nothing
 * else will.
 *
 * <p>Keyed by {@link com.termux.terminal.TerminalSession#mHandle}, the same stable per-session id
 * {@link AgentPaneRegistry} already uses, so a session is identified without holding a reference
 * to it (and without needing one for a test).
 */
public final class KeyboardHoldTracker {

    private static final KeyboardHoldTracker INSTANCE = new KeyboardHoldTracker();

    @Nullable private String heldByHandle;

    private KeyboardHoldTracker() {}

    @NonNull
    public static KeyboardHoldTracker getInstance() {
        return INSTANCE;
    }

    /** Records that the session named {@code handle} is holding the keyboard down. */
    public synchronized void hold(@NonNull String handle) {
        heldByHandle = handle;
    }

    /** True while some session is holding the keyboard down. */
    public synchronized boolean isHeld() {
        return heldByHandle != null;
    }

    /** True while {@code handle} is the session holding the keyboard down. */
    public synchronized boolean isHeldBy(@NonNull String handle) {
        return handle.equals(heldByHandle);
    }

    /** Clears any hold, regardless of who asked for it: showing the keyboard always wins. */
    public synchronized void release() {
        heldByHandle = null;
    }

    /**
     * A session named {@code handle} just finished. If it was the one holding the keyboard down,
     * the hold is cleared and this returns {@code true} — the caller must show the keyboard
     * itself, since the only side that could have asked for it is gone. Returns {@code false},
     * and changes nothing, for any other session (including no hold at all).
     */
    public synchronized boolean releaseOnSessionFinished(@NonNull String handle) {
        if (!handle.equals(heldByHandle)) return false;
        heldByHandle = null;
        return true;
    }

    /** Test hook: forget any hold. */
    public synchronized void clear() {
        heldByHandle = null;
    }
}
