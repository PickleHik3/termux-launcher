package com.termux.app.wayland;

/**
 * When Close on a Linux window turns into an offer of Force close (#389). Close asks the app; if
 * the window is still there {@link #OFFER_AFTER_MS} later and the app shows no dialog of its own
 * (a child toplevel: an unsaved-changes question), the launcher offers to end the app's process.
 * Pure, so the rule is JVM-testable; the controller owns the timer and the kill.
 */
final class LinuxForceClosePolicy {

    /** How long a Close may go unanswered before Force close is offered. */
    static final long OFFER_AFTER_MS = 5000L;

    private LinuxForceClosePolicy() {}

    /**
     * Whether to offer Force close now: the window is still open, the app is not asking the user
     * something itself, and there is a process to end.
     */
    static boolean shouldOffer(boolean windowStillOpen, boolean showsOwnDialog, int pid,
                               int ownPid, int hostPid) {
        return windowStillOpen && !showsOwnDialog && canKill(pid, ownPid, hostPid);
    }

    /**
     * Whether {@code pid} may be ended: known, and neither the launcher nor the compositor, which
     * an X11 window's process id (_NET_WM_PID, the app's own word) could name.
     */
    static boolean canKill(int pid, int ownPid, int hostPid) {
        return pid > 1 && pid != ownPid && pid != hostPid;
    }
}
