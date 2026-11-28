package com.termux.app.tiling.desktop;

/**
 * Where a shell the launcher starts on its own goes (terl-integration-1). Under Terl there is no
 * v1 Terminal page to show one: a launch asking for a session (the New session app shortcut, a
 * failsafe one, cold or warm) opens a terminal window of the stack, which starts its shell once
 * the start has settled; a start that asks for none starts no v1 shell (the stack's windows start
 * their own). With the switch off every answer is v1's.
 *
 * <p>Pure.
 */
public final class TerlShellStart {

    /** What to start. */
    public enum Start {
        /** v1's shell: a new session, or the autosaved arrangement rebuilt. */
        V1,
        /** A terminal window of the stack, gone to. */
        TERL_WINDOW,
        /** Nothing: the stack's windows start their own shells. */
        NOTHING
    }

    private TerlShellStart() {}

    /**
     * The start for a launch: {@code terl} the desktop is running, {@code asksForSession} the
     * launch names a session (the New session shortcut, or a failsafe one).
     */
    public static Start of(boolean terl, boolean asksForSession) {
        if (!terl) return Start.V1;
        return asksForSession ? Start.TERL_WINDOW : Start.NOTHING;
    }
}
