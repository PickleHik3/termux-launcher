package com.termux.app.wayland;

import android.app.ActivityManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.graphics.Point;
import android.hardware.display.DisplayManager;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.os.RemoteException;
import android.os.SystemClock;
import android.system.ErrnoException;
import android.system.Os;
import android.text.TextUtils;
import android.util.Log;
import android.view.Display;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import com.termux.app.x11.X11DisplayServerState;
import com.termux.shared.termux.TermuxConstants;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Opens Linux apps under Wayland Display: starts the Linux windows host when it is not running,
 * hands each waiting launch the host's socket once it runs, and brings up the Linux windows
 * screen (the Linux windows in the row, {@link LinuxWindowsController}). The app itself is run by whoever asked
 * ({@link Launch#run}), as a Termux task with the same environment X11 Display gives it.
 *
 * <p>One per process, main thread only. It stays bound to {@link LinuxWindowsService} while the
 * host runs, so it can stop the host once no window has existed for {@link #IDLE_STOP_MS} after
 * the last one closed, and lets go of it when the host stops.
 */
public final class WaylandAppRunner {

    private static final String LOG_TAG = "TLWayland";

    /**
     * The directory the host's socket is in: the prefix's tmp itself, Termux's usual runtime
     * directory, beside the X sockets (see {@link LinuxWindowsEnv}).
     */
    public static final String SOCKET_DIR = TermuxConstants.TERMUX_TMP_PREFIX_DIR_PATH;
    /** The socket the host listens on, in {@link #SOCKET_DIR}. */
    public static final String SOCKET_NAME = "wayland-0";
    /** The service's process, as {@code android:process} names it in the manifest. */
    private static final String HOST_PROCESS_SUFFIX = ":linuxwindows";

    /** How long a tap waits for the host to run before it is given up on. */
    @VisibleForTesting static final long START_TIMEOUT_MS = 15_000L;
    /** How long the host runs with no window after the last one closed. */
    @VisibleForTesting static final long IDLE_STOP_MS = 30_000L;

    /** One app waiting for the host. Main thread. */
    public interface Launch {
        /**
         * The host runs: start the app. {@code waylandDisplay} is the socket's absolute path, for
         * {@code WAYLAND_DISPLAY}; {@code xDisplay} the host's Xwayland display for {@code DISPLAY},
         * or null when it is not known.
         */
        void run(@NonNull String waylandDisplay, @Nullable String xDisplay);

        /** The host did not come up; the app was not started. */
        void failed();

        /**
         * The app's identity, for telling a second tap on an app whose window is open from a first
         * (its catalogue id); null for a launch that is never matched to a window.
         */
        @Nullable
        default String key() {
            return null;
        }
    }

    /** Told, on the main thread, when the launches waiting for the host are no longer waiting. */
    public interface PendingListener {
        /** {@code started}: the host ran and the apps were started; false: the host did not come up. */
        void onPendingEnded(boolean started);
    }

    @Nullable private static WaylandAppRunner sShared;

    /** The process's runner. Main thread only. */
    @NonNull
    public static WaylandAppRunner shared(@NonNull Context context) {
        if (sShared == null) sShared = new WaylandAppRunner(context);
        return sShared;
    }

    /** The absolute socket path apps get as {@code WAYLAND_DISPLAY}. */
    @NonNull
    public static String socketPath() {
        return SOCKET_DIR + "/" + SOCKET_NAME;
    }

    @NonNull private final Context context;
    @NonNull private final Handler main = new Handler(Looper.getMainLooper());
    @NonNull private final List<Launch> pending = new ArrayList<>();
    @Nullable private PendingListener pendingListener;

    @Nullable private ILinuxWindows service;
    @Nullable private IBinder binder;
    private boolean bound;
    /** This runner started the service (not only bound it), so it is the one to stop it. */
    private boolean serviceStarted;
    /** This runner asked the host to start and has not heard it run or fail since. */
    private boolean startAsked;
    /** The host should be stopped as soon as the binding connects (Settings went back to X11). */
    private boolean stopOnConnect;
    private boolean running;
    /** The host has told this binding its state at least once (the replay after addCallback). */
    private boolean stateKnown;
    /**
     * The host said STARTING since this runner asked it to start, so a STOPPED now is the answer
     * to that start and not a state from before it.
     */
    private boolean startingSeen;
    /** Xwayland's display the host last reported (":0"); null when none. */
    @Nullable private String xDisplay;
    /** Gave up waiting for Xwayland's display for the launches in hand: they run without DISPLAY. */
    private boolean xWaitExpired;
    /** Live windows, counted from the host's callbacks; reset on every (re)attach. */
    private int windows;
    /** Which window is which launched app; see {@link LinuxAppWindowMatcher}. Main thread. */
    @NonNull private final LinuxAppWindowMatcher matcher =
        new LinuxAppWindowMatcher(WaylandAppRunner::parentPid);
    /** Wayland Display is the chosen display: the launcher listens on the socket. */
    private boolean waylandChosen;
    /** The socket the launcher listens on while Wayland Display is chosen; null until then. */
    @Nullable private WaylandSocketOwner socketOwner;
    /** A client is waiting on the launcher's socket for the host to start. */
    private boolean connectionWaiting;
    /** Scales chosen on the rail for windows with no app to keep them (started from a shell); by window id. Main thread. */
    @NonNull private final java.util.Map<Long, Float> windowScales = new java.util.HashMap<>();

    private final Runnable startTimeout = () -> {
        if (!pending.isEmpty()) {
            Log.w(LOG_TAG, "the host did not run within " + START_TIMEOUT_MS + " ms");
            failPending();
        }
    };

    private final Runnable xWaitTimeout = () -> {
        Log.w(LOG_TAG, "Xwayland reported no display within " + START_TIMEOUT_MS + " ms; launching without DISPLAY");
        xWaitExpired = true;
        dispatchPending();
    };

    private final Runnable idleStop = this::stopIfIdle;

    private final Runnable connectionTimeout = () -> {
        if (!connectionWaiting) return;
        Log.w(LOG_TAG, "the host did not run within " + START_TIMEOUT_MS + " ms for a waiting client");
        connectionWaiting = false;
        rearmSocket();
    };

    private final IBinder.DeathRecipient death = () -> main.post(this::onHostGone);

    private final ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder b) {
            binder = b;
            service = ILinuxWindows.Stub.asInterface(b);
            windows = 0;
            // The host replays its live windows next.
            matcher.clearWindows();
            windowScales.clear();
            try {
                b.linkToDeath(death, 0);
                service.addCallback(callback);
            } catch (RemoteException e) {
                onHostGone();
                return;
            }
            if (stopOnConnect) {
                stopOnConnect = false;
                stopService();
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            onHostGone();
        }
    };

    private final ILinuxWindowsCallback.Stub callback = new ILinuxWindowsCallback.Stub() {
        @Override
        public void onAttached(int interfaceVersion, int hostPid) {
            main.post(() -> attached(interfaceVersion));
        }

        @Override
        public void onHostState(int state, String message) {
            main.post(() -> hostState(state, message));
        }

        @Override
        public void onXDisplay(String display) {
            main.post(() -> xDisplayReported(display));
        }

        @Override
        public void onClipboardText(String text) {}

        @Override
        public void onFullscreenRequest(long id, boolean fullscreen) {}

        @Override
        public void onWindowCreated(long id, String title, String appId, int clientPid, int flags, long parentId) {
            main.post(() -> {
                windows++;
                main.removeCallbacks(idleStop);
                matcher.windowCreated(id, clientPid, (flags & ILinuxWindows.WINDOW_X11) != 0,
                    SystemClock.uptimeMillis());
                // A new window gets its place in the row from the Linux windows' controller,
                // which hears the host itself (spec #384).
            });
        }

        @Override
        public void onWindowChanged(long id, String title, String appId, int width, int height) {}

        @Override
        public void onWindowDestroyed(long id) {
            main.post(() -> {
                matcher.windowDestroyed(id);
                windowScales.remove(id);
                if (windows > 0) windows--;
                if (windows == 0) armIdleStop();
            });
        }
    };

    private WaylandAppRunner(@NonNull Context context) {
        this.context = context.getApplicationContext();
    }

    /** Whether a launch is waiting for the host to run (the Linux windows screen says "Opening" only then). */
    public boolean launchPending() {
        return !pending.isEmpty();
    }

    /** The one listener for {@link #launchPending} ending; null clears it. */
    public void setPendingListener(@Nullable PendingListener listener) {
        pendingListener = listener;
    }

    /**
     * Opens one app under Wayland Display: at once when the host runs, else once it does. Several
     * may wait together; each runs in the order asked.
     */
    public void launch(@NonNull Launch launch) {
        String key = launch.key();
        if (key != null && running && service != null) {
            long window = matcher.windowFor(key);
            if (window >= 0) {
                // The app's window is still open: go to it, and start nothing.
                if (windowsSurface != null) windowsSurface.showWindow(window);
                return;
            }
        }
        pending.add(launch);
        main.removeCallbacks(idleStop);
        main.removeCallbacks(startTimeout);
        main.postDelayed(startTimeout, START_TIMEOUT_MS);
        if (service != null && running) {
            dispatchPending();
            return;
        }
        if (service != null && stateKnown && !startAsked) {
            // Connected to a stopped host: the state replay has come and gone.
            startHost();
            return;
        }
        // Otherwise the replay, or the start already asked for, brings the host up.
        if (!bound) connect();
    }

    /**
     * Stops the host now, with every app in it: Settings went back to X11 Display. Reaches a host
     * left running by an earlier launcher process too, without starting one that is not there.
     */
    public void stopHost() {
        failPending();
        if (service != null) {
            stopService();
            return;
        }
        if (bound) {
            stopOnConnect = true;
            return;
        }
        if (!hostProcessRunning()) return;
        stopOnConnect = true;
        connect();
    }

    private void connect() {
        Intent intent = new Intent(context, LinuxWindowsService.class);
        try {
            // Started as well as bound, so the host outlives this binding.
            context.startService(intent);
            serviceStarted = true;
            bound = context.bindService(intent, connection, Context.BIND_AUTO_CREATE);
        } catch (RuntimeException e) {
            Log.w(LOG_TAG, "cannot reach the host service: " + e);
            bound = false;
        }
        if (!bound) failPending();
    }

    private void disconnect() {
        if (binder != null) {
            try {
                binder.unlinkToDeath(death, 0);
            } catch (RuntimeException ignored) {
            }
        }
        if (service != null) {
            try {
                service.removeCallback(callback);
            } catch (RemoteException ignored) {
            }
        }
        if (bound) {
            try {
                context.unbindService(connection);
            } catch (IllegalArgumentException ignored) {
            }
        }
        bound = false;
        if (serviceStarted) {
            // Unbound and no longer started: with nobody else bound the service is destroyed and
            // its process ends (LinuxWindowsService.onDestroy). The next launch starts it afresh.
            serviceStarted = false;
            try {
                context.stopService(new Intent(context, LinuxWindowsService.class));
            } catch (RuntimeException e) {
                Log.w(LOG_TAG, "cannot stop the host service: " + e);
            }
        }
        binder = null;
        service = null;
        running = false;
        startAsked = false;
        stateKnown = false;
        windows = 0;
        matcher.clear();
        windowScales.clear();
        xDisplay = null;
        xWaitExpired = false;
        connectionWaiting = false;
        main.removeCallbacks(connectionTimeout);
        publishToSessions();
        main.removeCallbacks(idleStop);
        main.removeCallbacks(xWaitTimeout);
        rearmSocket();
    }

    /**
     * Wayland Display is chosen or not (Settings, or the launcher coming up). Chosen: the launcher
     * listens on the socket, so a client started anywhere finds a display and the host starts on
     * its first connection. Not chosen: the socket goes. Main thread; never throws.
     */
    public void setWaylandChosen(boolean chosen) {
        waylandChosen = chosen;
        if (chosen) {
            if (socketOwner == null) socketOwner = new WaylandSocketOwner(socketPath(), main, this::onConnectionWaiting);
            if (prepareSocketDir() && socketOwner.open() && !running && !startAsked) socketOwner.arm();
        } else {
            connectionWaiting = false;
            main.removeCallbacks(connectionTimeout);
            if (socketOwner != null) socketOwner.close();
        }
        publishToSessions();
    }

    /** The socket for the host to take over, or null for it to bind the path itself. */
    @Nullable
    ParcelFileDescriptor listenSocketForHost() {
        if (!waylandChosen || socketOwner == null) return null;
        socketOwner.disarm();
        return socketOwner.dupForHost();
    }

    /** A client connected to the launcher's socket while the host is not running: start it. */
    private void onConnectionWaiting() {
        if (!waylandChosen) return;
        if (running || startAsked) return;
        connectionWaiting = true;
        main.removeCallbacks(idleStop);
        main.removeCallbacks(connectionTimeout);
        main.postDelayed(connectionTimeout, START_TIMEOUT_MS);
        if (service != null && stateKnown) {
            startHost();
            return;
        }
        if (!bound) connect();
    }

    /**
     * The host is not running (stopped, died, did not start): clients left waiting on the socket
     * get an error rather than waiting forever, and the next one starts the host again.
     */
    private void rearmSocket() {
        if (!waylandChosen || socketOwner == null || running || startAsked || connectionWaiting) return;
        if (!socketOwner.isListening() && !(prepareSocketDir() && socketOwner.open())) {
            publishToSessions();
            return;
        }
        socketOwner.dropWaiting();
        socketOwner.arm();
        publishToSessions();
    }

    /**
     * Tells new terminal sessions where the display is ({@code X11DisplayServerState}): the socket
     * and Xwayland's display while the host runs, nothing once it stops. A new session starts with
     * no {@code XDG_RUNTIME_DIR} (one its rc file sets later is not known here), so it gets the
     * socket's path.
     */
    private void publishToSessions() {
        X11DisplayServerState.setWaylandDisplays(
            running || (waylandChosen && socketOwner != null && socketOwner.isListening())
                ? LinuxWindowsEnv.waylandDisplay(SOCKET_DIR, SOCKET_NAME, null) : null,
            running ? xDisplay : null);
    }

    private void xDisplayReported(@Nullable String display) {
        if (service == null) return;
        xDisplay = TextUtils.isEmpty(display) ? null : display;
        publishToSessions();
        if (xDisplay != null) {
            main.removeCallbacks(xWaitTimeout);
            if (running) dispatchPending();
        }
    }

    private void attached(int interfaceVersion) {
        if (interfaceVersion == ILinuxWindows.VERSION) return;
        Log.w(LOG_TAG, "host speaks version " + interfaceVersion + ", the launcher " + ILinuxWindows.VERSION);
        failPending();
        disconnect();
    }

    private void hostState(int state, @Nullable String message) {
        if (service == null) return;
        stateKnown = true;
        switch (state) {
            case ILinuxWindows.STATE_RUNNING: {
                // A host this runner started: windows from here on are new.
                running = true;
                startAsked = false;
                boolean forConnection = pending.isEmpty();
                connectionWaiting = false;
                main.removeCallbacks(connectionTimeout);
                if (socketOwner != null) socketOwner.disarm();
                publishToSessions();
                dispatchPending();
                // Started for a client of the socket: stop again if it never opens a window.
                if (forConnection && windows == 0) armIdleStop();
                break;
            }
            case ILinuxWindows.STATE_STARTING:
                startingSeen = true;
                break;
            case ILinuxWindows.STATE_STOPPED:
            case ILinuxWindows.STATE_DIED:
                running = false;
                windows = 0;
                matcher.clear();
                windowScales.clear();
                xDisplay = null;
                xWaitExpired = false;
                publishToSessions();
                main.removeCallbacks(idleStop);
                main.removeCallbacks(xWaitTimeout);
                switch (onHostStopped(startAsked, startingSeen, pending.isEmpty(), connectionWaiting)) {
                    case IGNORE:
                        // A state from before this runner's start; the start's own answer follows.
                        break;
                    case RELEASE:
                        // Nothing to open: let go, so the host's process can end.
                        disconnect();
                        break;
                    case RESTART:
                        startHost();
                        break;
                    default:
                        Log.w(LOG_TAG, "the host did not start: " + message);
                        failPending();
                        connectionWaiting = false;
                        disconnect();
                        break;
                }
                break;
            default:
                // STATE_UNAVAILABLE: this build has no host.
                failPending();
                disconnect();
                break;
        }
    }

    /** What a stopped or died host asks of the runner. */
    @VisibleForTesting
    enum StoppedAction {
        /** A state from before the runner's start; wait for the start's own answer. */
        IGNORE,
        /** Nothing is waiting: unbind and stop the service, so its process ends. */
        RELEASE,
        /** Something is waiting and no start is in flight: bind stays, start again. */
        RESTART,
        /** The start asked for did not come up: fail the launches, then release. */
        FAIL
    }

    @VisibleForTesting
    @NonNull
    static StoppedAction onHostStopped(boolean startAsked, boolean startingSeen, boolean pendingEmpty,
                                       boolean connectionWaiting) {
        if (startAsked && !startingSeen) return StoppedAction.IGNORE;
        if (pendingEmpty && !connectionWaiting) return StoppedAction.RELEASE;
        return startAsked ? StoppedAction.FAIL : StoppedAction.RESTART;
    }

    /** The host process died or the binding broke. */
    private void onHostGone() {
        boolean wasConnected = service != null || bound;
        disconnect();
        if (wasConnected) failPending();
    }

    /** One notice per launcher process when the keyboard layout data is missing. */
    private static final java.util.concurrent.atomic.AtomicBoolean KEYMAP_NOTICED =
        new java.util.concurrent.atomic.AtomicBoolean();

    private void startHost() {
        if (service == null) return;
        if (!prepareSocketDir()) {
            failPending();
            connectionWaiting = false;
            main.removeCallbacks(connectionTimeout);
            if (socketOwner != null) socketOwner.dropWaiting();
            return;
        }
        startAsked = true;
        startingSeen = false;
        HostStart start = hostStart(context);
        if (start.xkbConfigRoot == null && KEYMAP_NOTICED.compareAndSet(false, true)) {
            com.termux.app.notice.AppNotice.show(context,
                com.termux.app.notice.AppNoticeItem.Kind.INFO,
                context.getString(com.termux.R.string.linux_keymap_missing_title),
                context.getString(com.termux.R.string.linux_keymap_missing_sub), true);
        }
        try {
            service.start(SOCKET_DIR, start.xkbConfigRoot, start.xwaylandBinary, start.width,
                start.height, start.scale, start.framePeriodNanos, listenSocketForHost());
        } catch (RemoteException e) {
            onHostGone();
        }
    }

    /** Makes the socket's directory (the prefix's tmp) with the runtime directory's 0700; false when it cannot be made. */
    private static boolean prepareSocketDir() {
        File dir = new File(SOCKET_DIR);
        if (!dir.isDirectory() && !dir.mkdirs()) {
            Log.w(LOG_TAG, "cannot create " + SOCKET_DIR);
            return false;
        }
        try {
            Os.chmod(SOCKET_DIR, 0700);
        } catch (ErrnoException e) {
            Log.w(LOG_TAG, "chmod " + SOCKET_DIR + ": " + e.getMessage());
        }
        return true;
    }

    private void dispatchPending() {
        if (pending.isEmpty()) return;
        main.removeCallbacks(startTimeout);
        if (xDisplay == null && !xWaitExpired && xwaylandBinary() != null) {
            // Xwayland was asked for and has not reported its display yet; the report runs this again.
            main.removeCallbacks(xWaitTimeout);
            main.postDelayed(xWaitTimeout, START_TIMEOUT_MS);
            return;
        }
        main.removeCallbacks(xWaitTimeout);
        List<Launch> launches = new ArrayList<>(pending);
        pending.clear();
        String wayland = socketPath();
        String x = xwaylandDisplay();
        for (Launch launch : launches) {
            String key = launch.key();
            if (key != null) matcher.launched(key, SystemClock.uptimeMillis());
            launch.run(wayland, x);
        }
        if (pendingListener != null) pendingListener.onPendingEnded(true);
    }

    private void failPending() {
        main.removeCallbacks(startTimeout);
        main.removeCallbacks(xWaitTimeout);
        startAsked = false;
        if (pending.isEmpty()) return;
        List<Launch> launches = new ArrayList<>(pending);
        pending.clear();
        for (Launch launch : launches) launch.failed();
        if (pendingListener != null) pendingListener.onPendingEnded(false);
    }

    private void armIdleStop() {
        main.removeCallbacks(idleStop);
        if (running) main.postDelayed(idleStop, IDLE_STOP_MS);
    }

    private void stopIfIdle() {
        if (!running || windows > 0 || !pending.isEmpty()) return;
        Log.i(LOG_TAG, "no Linux window for " + IDLE_STOP_MS + " ms; stopping the host");
        stopService();
    }

    private void stopService() {
        if (service == null) return;
        try {
            service.stop();
        } catch (RemoteException ignored) {
        }
    }

    /**
     * The X display the host's Xwayland serves, for {@code DISPLAY}, as the host reported it
     * ({@link ILinuxWindowsCallback#onXDisplay}); null when none came, and an X11-only app then
     * has no display.
     */
    @Nullable
    private String xwaylandDisplay() {
        return xDisplay;
    }

    /**
     * The app window {@code windowId} was opened for: the launch key, which is the catalogue
     * app's id ({@code LinuxAppCatalog.LinuxApp#id}, the key of its per-app settings); null for a
     * window started from a shell or not yet matched. Main thread.
     */
    @Nullable
    public String appFor(long windowId) {
        return matcher.keyFor(windowId);
    }

    /**
     * The scale chosen on the rail for window {@code windowId} when it has no app to store it
     * for ({@link #appFor} null), kept while the window is open; 0 when none. Main thread.
     */
    public float windowScale(long windowId) {
        Float scale = windowScales.get(windowId);
        return scale == null ? 0f : scale;
    }

    /** Keeps {@code scale} for window {@code windowId} while it is open; main thread. */
    public void setWindowScale(long windowId, float scale) {
        if (scale > 0f) windowScales.put(windowId, scale);
        else windowScales.remove(windowId);
    }

    /** A launch started process {@code pid} for the app {@code key}; main thread. */
    public void processStarted(@NonNull String key, int pid) {
        matcher.started(key, pid);
    }

    /** The parent of {@code pid} from /proc, or 0 when it is gone or unreadable. */
    private static int parentPid(int pid) {
        try {
            byte[] bytes = java.nio.file.Files.readAllBytes(java.nio.file.Paths.get("/proc/" + pid + "/stat"));
            return LinuxAppWindowMatcher.parentFromStat(new String(bytes, java.nio.charset.StandardCharsets.UTF_8));
        } catch (java.io.IOException | RuntimeException e) {
            return 0;
        }
    }

    /**
     * Where Linux windows show (spec #384): the row's controller, set while the launcher's
     * activity lives; null otherwise.
     */
    public interface WindowsSurface {
        /** Goes to window {@code id}; false when it is not open there. */
        boolean showWindow(long id);
    }

    @Nullable private WindowsSurface windowsSurface;

    /** The Linux windows' place on screen; null when the activity is gone. Main thread. */
    public void setWindowsSurface(@Nullable WindowsSurface surface) {
        windowsSurface = surface;
    }

    /** Whether the host's process is alive (one of this app's own, so it is listed). */
    private boolean hostProcessRunning() {
        ActivityManager am = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        if (am == null) return false;
        List<ActivityManager.RunningAppProcessInfo> processes = am.getRunningAppProcesses();
        if (processes == null) return false;
        String name = context.getPackageName() + HOST_PROCESS_SUFFIX;
        for (ActivityManager.RunningAppProcessInfo process : processes) {
            if (name.equals(process.processName)) return true;
        }
        return false;
    }

    /** What the host is started with; the Linux windows screen's developer Start uses it too. */
    static final class HostStart {
        @Nullable final String xkbConfigRoot;
        @Nullable final String xwaylandBinary;
        final int width;
        final int height;
        final float scale;
        final long framePeriodNanos;

        HostStart(@Nullable String xkbConfigRoot, @Nullable String xwaylandBinary, int width,
                  int height, float scale, long framePeriodNanos) {
            this.xkbConfigRoot = xkbConfigRoot;
            this.xwaylandBinary = xwaylandBinary;
            this.width = width;
            this.height = height;
            this.scale = scale;
            this.framePeriodNanos = framePeriodNanos;
        }
    }

    /**
     * The prefix's keymap data and {@code Xwayland} (each null when missing), and the default
     * display's real size and refresh period with the output scale ({@link LinuxDisplayScale#current}:
     * the screen's density, which X apps under Xwayland keep).
     */
    @NonNull
    static HostStart hostStart(@NonNull Context context) {
        File xkb = new File(TermuxConstants.TERMUX_PREFIX_DIR_PATH + "/share/X11/xkb");
        String xkbRoot = xkb.isDirectory() ? xkb.getAbsolutePath() : null;
        String xwayland = xwaylandBinary();
        if (xwayland == null) Log.w(LOG_TAG, "no executable Xwayland; X11 apps have no display");
        float scale = LinuxDisplayScale.current(context);
        Point size = new Point();
        long framePeriodNanos = 0L;
        DisplayManager displays = (DisplayManager) context.getSystemService(Context.DISPLAY_SERVICE);
        Display display = displays == null ? null : displays.getDisplay(Display.DEFAULT_DISPLAY);
        if (display != null) {
            display.getRealSize(size);
            float refresh = display.getRefreshRate();
            framePeriodNanos = framePeriodNanos(refresh);
        }
        return new HostStart(xkbRoot, xwayland, size.x, size.y, scale, framePeriodNanos);
    }

    /** The prefix's Xwayland when it is executable; null otherwise. */
    @Nullable
    public static String xwaylandBinary() {
        File bin = new File(TermuxConstants.TERMUX_BIN_PREFIX_DIR_PATH + "/Xwayland");
        return bin.canExecute() ? bin.getAbsolutePath() : null;
    }

    /** The host's frame tick for a display refreshing {@code refreshHz} times a second; 0 means 60 Hz. */
    static long framePeriodNanos(float refreshHz) {
        return refreshHz > 1f ? (long) (1_000_000_000L / refreshHz) : 0L;
    }
}
