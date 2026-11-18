package com.termux.app.x11;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.R;
import com.termux.app.TermuxService;
import com.termux.shared.logger.Logger;
import com.termux.shared.termux.TermuxConstants;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * The embedded X server's lifecycle for one activity: asking for it, starting it, stopping it
 * with its apps given time to close, killing it, tearing its window down, and following its
 * controller and the list of apps open on it. What the activity does about a display coming or
 * going (chrome, page, keyboard) goes through {@link Host}.
 *
 * <p>The page contract reads {@link #isRunning}, {@link #isStarting} and {@link #sendBack}.
 */
public final class DisplayServer {
    private static final String LOG_TAG = "DisplayServer";

    /** What the activity does about the display. At most fifteen methods. */
    public interface Host {
        void refreshPageGlyphs();
        /** Hand the Display window the reason the last Start died, or clear it with null. */
        void setStartFailure(@Nullable String reason);
        boolean hasDisplayWindow();
        boolean isDisplayWindowInFront();
        /** Everything after the server's own teardown: the window, the row and the chrome. */
        void onDisplayTornDown(boolean wasInFront);
        void onLinuxAppsRunningChanged(boolean running);
        /** The display window, wall, still, bar, touchpad and environment told it runs or not. */
        void syncRunningChrome(boolean running);
        /** The keyboard, its route and the row after a running change that kept the window. */
        void afterRunningChanged(boolean running);
        /** Start in = Display: the cold start's landing finishes on the display window. */
        void bringInFrontIfStartOnDisplayPending();
        /** A stopped and not asked for display: the wall hears it and the bar is brought up. */
        void onDisplayGone();
        void onWindowsChanged(@NonNull List<X11WindowList.Window> windows, int active);
        void syncPlaceBar();
        @Nullable TermuxService service();
        @Nullable TermuxAppSharedPreferences preferences();
        boolean isActivityGone();
    }

    @NonNull private final Context context;
    @NonNull private final Host host;
    @Nullable private X11DisplayHostController controller;
    private boolean requested;
    /** The apps open on the display: the drawer's Displays group, and its chip's front icon. */
    @Nullable private X11WindowList windowList;
    @NonNull private List<X11WindowList.Window> windows = Collections.emptyList();
    private int activeWindow = -1;
    /** Closes the display's apps before the server is killed; built the first time Stop is used. */
    @Nullable private DisplayStopSequence stop;
    /** Which Start the exit callback belongs to; bumped by every Start. */
    private int startId;
    /** Whether a display came up since the last Start. */
    private boolean connectedSinceStart;
    /**
     * Whether a display has run since the launcher started. Once it has, landing on a stopped
     * Display page shows Start instead of starting it again: the autostart setting starts the
     * display with the launcher, and a stop the user made stays stopped.
     */
    private boolean ranThisLaunch;

    public DisplayServer(@NonNull Context context, @NonNull Host host) {
        this.context = context;
        this.host = host;
    }

    // ---- The narrow read ------------------------------------------------------------------

    /** Whether a display server is up and connected to the display window. */
    public boolean isRunning() {
        return controller != null && controller.isRunning();
    }

    /** A display was asked for and has not come up yet. */
    public boolean isStarting() {
        return requested && !isRunning();
    }

    /** Send Back to the app in front on the display; false when there is no display. */
    public boolean sendBack() {
        return controller != null && controller.sendBackKey();
    }

    // ---- State the activity reads and sets ------------------------------------------------

    /** The display controller while one is built, else null. */
    @Nullable
    public X11DisplayHostController controller() {
        return controller;
    }

    /**
     * A display was asked for (the Display entry, a Linux app, {@code wall.display}) and has not
     * come up yet: its window is on the row while it starts. Cleared when it runs — the running
     * display holds the window from then on — and when the row leaves the window before it does.
     */
    public boolean isRequested() {
        return requested;
    }

    public void setRequested(boolean requested) {
        this.requested = requested;
    }

    public boolean ranThisLaunch() {
        return ranThisLaunch;
    }

    @NonNull
    public List<X11WindowList.Window> windows() {
        return windows;
    }

    public int activeWindow() {
        return activeWindow;
    }

    /** Whether a display's apps are being followed. */
    public boolean hasWindowList() {
        return windowList != null;
    }

    /** Raise the app window with this id; false when no display is followed. */
    public boolean activateWindow(int id) {
        if (windowList == null) return false;
        windowList.activate(id);
        return true;
    }

    /** Ask the app window with this id to close; false when no display is followed. */
    public boolean closeWindowById(int id) {
        if (windowList == null) return false;
        windowList.close(id);
        return true;
    }

    // ---- The controller -------------------------------------------------------------------

    /** Hold the controller the activity just built. */
    public void setController(@NonNull X11DisplayHostController built) {
        controller = built;
    }

    /** Listen to the held controller once the activity has wired it. */
    public void listen(@NonNull View postTarget) {
        final X11DisplayHostController adopted = controller;
        if (adopted == null) return;
        adopted.setListener(this::onRunningChanged);
        // A kept announcement can have reported the display running while the controller was
        // built, before anything listened: told once the build has finished.
        if (adopted.isRunning()) {
            postTarget.post(() -> {
                if (controller == adopted && adopted.isRunning()) {
                    onRunningChanged(true);
                }
            });
        }
    }

    /**
     * Let go of the controller on activity destroy. The display controller is this activity's;
     * the server it talks to is not. Letting go hands the server's announcement on to the next
     * activity's controller. The listener is cleared first: letting go reads as "not running",
     * which is only true when the display stopped.
     */
    public void release() {
        if (controller != null) {
            controller.setListener(null);
            controller.destroy();
            controller = null;
        }
        if (windowList != null) {
            windowList.stop();
            windowList = null;
        }
    }

    /** Forget a pending stop on activity destroy. */
    public void abandonStop() {
        if (stop != null) {
            stop.abandon();
            stop = null;
        }
    }

    /**
     * A display came up or went down. Up, it holds its window in the row from now on; down, and
     * not asked for again, its window goes (ADR 0008) — the row, if it was in front, lands on the
     * window on its left, or the last Home page when there is none.
     */
    private void onRunningChanged(boolean running) {
        host.refreshPageGlyphs();
        if (running) {
            requested = false;
            connectedSinceStart = true;
            ranThisLaunch = true;
            host.setStartFailure(null);
        }
        if (!running && !requested) {
            tearDown();
            host.onDisplayGone();
            return;
        }
        host.syncRunningChrome(running);
        host.onLinuxAppsRunningChanged(running);
        syncWindowList(running);
        host.afterRunningChanged(running);
        if (running) host.bringInFrontIfStartOnDisplayPending();
    }

    // ---- Window list ----------------------------------------------------------------------

    /** Follow the display's apps while a display runs; forget them when it stops. */
    public void syncWindowList(boolean running) {
        if (windowList != null) {
            windowList.stop();
            windowList = null;
        }
        if (running) {
            windowList = new X11WindowList(
                X11DisplayHostController.displayName(), this::onWindowsChanged);
            windowList.start();
        } else {
            onWindowsChanged(Collections.emptyList(), -1);
        }
    }

    private void onWindowsChanged(@NonNull List<X11WindowList.Window> list, int active) {
        windows = list;
        activeWindow = active;
        if (controller != null) {
            int front = DisplayWindowName.frontIndex(list.size(), active);
            controller.setFrontWindowClass(front < 0 ? null : list.get(front).wmClass);
        }
        // A stop in progress is waiting for exactly this: the last app closing itself.
        if (stop != null) stop.onWindowsChanged(list.size());
        host.onWindowsChanged(list, active);
    }

    // ---- Start ----------------------------------------------------------------------------

    private boolean isEnabled() {
        return X11ServerSupport.isAvailable(context) && host.preferences() != null;
    }

    /** Run the configured start command as a background task, as if it had been typed. */
    public void start() {
        if (host.service() == null || host.preferences() == null || !isEnabled()) return;
        // The prefix commands are written at start-up, which on a first launch is before the
        // bootstrap exists. Put them in now if they are missing, then start.
        if (!X11CliInstaller.isInstalled(context)) {
            X11CliInstaller.installAsync(context, result -> {
                if (host.isActivityGone()) return;
                if (X11CliInstaller.isInstalled(context)) startNow();
                else Logger.logWarn(LOG_TAG, "Display commands not installed: " + result);
            });
            return;
        }
        startNow();
    }

    private void startNow() {
        final TermuxService service = host.service();
        final TermuxAppSharedPreferences preferences = host.preferences();
        if (service == null || preferences == null || !isEnabled()) return;
        String[] argv = X11StartCommand.argv(preferences.getX11DisplayCommand(),
            preferences.getX11DisplayDpi(), preferences.isX11LegacyDrawingEnabled(),
            preferences.isX11ForceBgraEnabled(),
            X11WindowManager.xstartup(preferences.getX11WindowManager()));
        if (argv.length == 0) return;
        // A new Start clears what the last one said, and counts from here whether a display
        // connects.
        final int id = ++startId;
        connectedSinceStart = false;
        host.setStartFailure(null);
        final com.termux.shared.shell.command.runner.app.AppShell task =
            service.createTermuxTask(argv[0],
                Arrays.copyOfRange(argv, 1, argv.length), null,
                TermuxConstants.TERMUX_HOME_DIR_PATH);
        if (task == null) return;
        // The exit callback runs on the main thread after the task's output is complete, so
        // stderr is safe to read here.
        service.notifyOnAppShellExit(task, exitCode -> {
            if (host.isActivityGone()) return;
            // Superseded by a later Start, or a display did connect: the server ended normally.
            if (id != startId || connectedSinceStart) return;
            host.setStartFailure(X11StartFailure.reason(
                task.getExecutionCommand().resultData.stderr,
                context.getString(R.string.termux_x11_start_failed_generic)));
        });
    }

    // ---- Stop -----------------------------------------------------------------------------

    /**
     * Stop the display: every app on it is asked to close itself first, and the server goes once
     * they have — or after {@link DisplayStopSequence#DRAIN_TIMEOUT_MS} ms, whichever comes
     * first. Killing the server with apps still on it is what leaves Firefox and friends
     * believing they crashed.
     */
    public void stop() {
        stopSequence().start(windows.size());
    }

    @NonNull
    private DisplayStopSequence stopSequence() {
        if (stop == null) {
            final Handler handler = new Handler(Looper.getMainLooper());
            stop = new DisplayStopSequence(
                new DisplayStopSequence.Actions() {
                    @Override public void closeAllWindows() { closeAllAppWindows(); }
                    @Override public void killDisplayServer() { kill(); }
                },
                new DisplayStopSequence.Scheduler() {
                    @Nullable private Runnable pending;

                    @Override public void schedule(long delayMs, @NonNull Runnable action) {
                        pending = action;
                        handler.postDelayed(action, delayMs);
                    }

                    @Override public void cancel() {
                        if (pending != null) handler.removeCallbacks(pending);
                        pending = null;
                    }
                });
        }
        return stop;
    }

    /** Ask every app listed on the display to close itself, as its own close button would. */
    private void closeAllAppWindows() {
        if (windowList == null) return;
        for (X11WindowList.Window window : new ArrayList<>(windows)) {
            windowList.close(window.id);
        }
    }

    /**
     * Seam for a Display window's close: ask the app at {@code index} of the chip row to close.
     * False when there is no such window.
     */
    public boolean closeWindow(int index) {
        if (windowList == null || index < 0 || index >= windows.size()) return false;
        windowList.close(windows.get(index).id);
        return true;
    }

    /**
     * Stop the server the way {@code pkill termux-x11} does, whoever started it. It is a separate
     * process on purpose — that is what keeps an X server crash off the home screen — so its own
     * name is the only handle the launcher has on it. Signalled from here, not through the
     * prefix's {@code pkill}, which a prefix without procps lacks; that is only the fallback when
     * no server could be found here. The window leaves when the server's Binder dies.
     */
    private void kill() {
        final String packageName = context.getPackageName();
        final Handler main = new Handler(Looper.getMainLooper());
        new Thread(() -> {
            int stopped = X11ServerProcesses.stopAll(packageName);
            // A SIGTERMed server leaves its socket; looking at the sockets drops the dead ones.
            X11Sockets.liveDisplays(X11Sockets.socketDir(), X11Sockets.LOCAL_SOCKET);
            if (stopped > 0) return;
            main.post(() -> {
                TermuxService service = host.service();
                if (service == null) return;
                service.createTermuxTask(
                    TermuxConstants.TERMUX_BIN_PREFIX_DIR_PATH + "/pkill",
                    new String[]{"-f", "termux-x11 " + packageName}, null,
                    TermuxConstants.TERMUX_HOME_DIR_PATH);
            });
        }, "x11-stop").start();
        host.syncPlaceBar();
    }

    // ---- Teardown -------------------------------------------------------------------------

    /**
     * Takes the display window off the wall: the server connection is let go of, and the
     * activity takes the window's frame and chrome down ({@link Host#onDisplayTornDown}). The
     * server itself, if one runs, is left alone. Runs when the display stops, and when a display
     * that never came up is left or closed.
     */
    public void tearDown() {
        if (!host.hasDisplayWindow()) return;
        boolean wasInFront = host.isDisplayWindowInFront();
        requested = false;
        if (stop != null) {
            stop.abandon();
            stop = null;
        }
        syncWindowList(false);
        host.onLinuxAppsRunningChanged(false);
        if (controller != null) {
            // Destroying the controller reports it stopped; nothing is to hear that now.
            controller.setListener(null);
            controller.detachView();
            controller.onDisplayPlaceLeft();
            controller.destroy();
            controller = null;
        }
        host.onDisplayTornDown(wasInFront);
    }
}
