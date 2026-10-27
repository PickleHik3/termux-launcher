package com.termux.app.terminal;

/**
 * What the Workspace does with a pane whose shell was killed by a signal the launcher did not
 * send. Android 12 and later stop an app's child processes past a limit (the phantom-process
 * killer, 32 by default) oldest first, and the oldest is the login shell: a Linux desktop on the
 * display was enough to take every terminal window with it, without a word (reported 2026-10-27).
 */
public final class KilledShellNotice {

    /** A burst of kills (the phantom trim takes many at once) raises one notice. */
    public static final long QUIET_MS = 10_000L;

    private static final int SIGKILL = 9;
    private static final int ANDROID_12 = 31;

    private KilledShellNotice() {}

    /**
     * Whether a finished pane stays on screen with its "[Process completed (signal N)]" line
     * until Enter. {@code exitStatus} is the session's: negative for the signal that ended it.
     * A shell the launcher itself finished (a pane or window closed, Kill process) goes at once.
     */
    public static boolean keepsPane(int exitStatus, boolean finishRequested) {
        return exitStatus < 0 && !finishRequested;
    }

    /** Whether the notice points at Android's child-process limit as the likely cause. */
    public static boolean mayBePhantomLimit(int signal, int sdkInt) {
        return signal == SIGKILL && sdkInt >= ANDROID_12;
    }
}
