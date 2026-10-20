package com.termux.app.x11;

import android.os.IBinder;

import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;

import com.termux.shared.termux.shell.command.environment.TermuxShellEnvironment;

/**
 * Whether a display server is running, for the whole process, and the {@code DISPLAY} that new
 * shells get from it.
 *
 * <p>The server outlives every activity, so this does too. It follows the server alone: up when a
 * live server announces itself (whoever takes the announcement, or nobody), down only when that
 * server's Binder dies or a controller sees it die. An activity going — a configuration relaunch
 * for new wallpaper colours or a theme switch, or any destroy — never touches it, so the shells a
 * relaunched launcher opens still find the display that kept running. Nothing here starts a
 * server or asks one to announce itself; it only listens.
 *
 * <p>The activity's part is the opt-in ({@link #setDisplayEnvWanted}), read from its settings.
 */
public final class X11DisplayServerState {

    private X11DisplayServerState() { }

    /** The running server's Binder and one death link on it, separate from any controller's. */
    @NonNull private static final X11ServerLink link = new X11ServerLink();
    private static boolean running;
    /** The user's opt-in: {@code DISPLAY} for new shells while a display runs. */
    private static boolean displayEnvWanted;

    /** A server announced itself with {@code binder}. A dead one changes nothing. */
    static synchronized void onServerAnnounced(@NonNull IBinder binder) {
        if (link.holds(binder)) return;
        if (!link.accept(binder, X11DisplayServerState::onServerDied)) return;
        running = true;
        applyDisplayEnv();
    }

    /** The server has gone: its Binder died, or a controller saw it die. */
    @VisibleForTesting
    static synchronized void onServerDied() {
        link.release();
        if (!running) return;
        running = false;
        applyDisplayEnv();
    }

    /** True while a display server is up, whatever any activity holds of it. */
    public static synchronized boolean isRunning() {
        return running;
    }

    /** The user's opt-in, as the activity reads it from its settings. */
    public static synchronized void setDisplayEnvWanted(boolean wanted) {
        displayEnvWanted = wanted;
        applyDisplayEnv();
    }

    private static void applyDisplayEnv() {
        TermuxShellEnvironment.setDisplayForNewSessions(displayEnvWanted && running
            ? X11DisplayHostController.displayName() : null);
    }

    @VisibleForTesting
    static synchronized void resetForTest() {
        link.release();
        running = false;
        displayEnvWanted = false;
        TermuxShellEnvironment.setDisplayForNewSessions(null);
    }
}
