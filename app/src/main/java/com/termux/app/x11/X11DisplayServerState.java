package com.termux.app.x11;

import android.os.IBinder;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import com.termux.app.wayland.LinuxWindowsEnv;
import com.termux.shared.termux.shell.command.environment.TermuxShellEnvironment;

import java.util.Map;

/**
 * Whether a display server is running, for the whole process, and the {@code DISPLAY} that new
 * shells get from it, or {@code WAYLAND_DISPLAY} and {@code DISPLAY} while the Wayland host runs
 * ({@link #setWaylandDisplays}, {@link LinuxWindowsEnv#forSession}).
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
    /** The Wayland host's {@code WAYLAND_DISPLAY} while it runs; null otherwise. */
    @Nullable private static String waylandDisplay;
    /** The Wayland host's Xwayland display while it runs and has said it; null otherwise. */
    @Nullable private static String xwaylandDisplay;

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

    /**
     * The Wayland host's display while it runs, for new shells: {@code waylandDisplay} as their
     * {@code WAYLAND_DISPLAY} and its Xwayland's {@code xwaylandDisplay} (null until known) as
     * their {@code DISPLAY}; both null once it stops. {@code WaylandAppRunner} says it.
     */
    public static synchronized void setWaylandDisplays(@Nullable String waylandDisplay,
                                                       @Nullable String xwaylandDisplay) {
        X11DisplayServerState.waylandDisplay = waylandDisplay;
        X11DisplayServerState.xwaylandDisplay = waylandDisplay == null ? null : xwaylandDisplay;
        applyDisplayEnv();
    }

    private static void applyDisplayEnv() {
        Map<String, String> env = LinuxWindowsEnv.forSession(displayEnvWanted,
            running ? X11DisplayHostController.displayName() : null, waylandDisplay, xwaylandDisplay);
        TermuxShellEnvironment.setDisplayForNewSessions(env.get("DISPLAY"));
        TermuxShellEnvironment.setWaylandDisplayForNewSessions(env.get("WAYLAND_DISPLAY"));
    }

    @VisibleForTesting
    static synchronized void resetForTest() {
        link.release();
        running = false;
        displayEnvWanted = false;
        waylandDisplay = null;
        xwaylandDisplay = null;
        TermuxShellEnvironment.setDisplayForNewSessions(null);
        TermuxShellEnvironment.setWaylandDisplayForNewSessions(null);
    }
}
