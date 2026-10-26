package com.termux.app.terminal;

/**
 * Which session a new row window joins (the bar's {@code +}, the Strip's {@code +}, the Terminal
 * entry's New window): the session the bar's session box shows as current. The box reads the
 * current session's place among the sessions, pulled into range, so with sessions standing it
 * always names one of them, even when the activity has lost track of which is current (none set,
 * or one that is no longer among them). The new window goes there. Only with no session at all
 * does the new window start a session of its own.
 */
public final class NewWindowSessionPolicy {

    /** {@link #sessionFor}'s answer when no session stands: the window starts one. */
    public static final int NEW_SESSION = -1;

    private NewWindowSessionPolicy() {}

    /**
     * The index of the session a new window joins, or {@link #NEW_SESSION}.
     *
     * @param currentIndex the current session's index among the sessions, negative when there is
     *                     no current session or it is not among them
     * @param sessionCount how many sessions stand
     */
    public static int sessionFor(int currentIndex, int sessionCount) {
        if (sessionCount <= 0) return NEW_SESSION;
        // The session box's own reading (SessionsIndicatorView.setSession): the place, clamped.
        return Math.max(0, Math.min(currentIndex, sessionCount - 1));
    }
}
