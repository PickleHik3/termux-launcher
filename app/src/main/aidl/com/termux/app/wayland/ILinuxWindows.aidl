package com.termux.app.wayland;

import android.view.Surface;
import com.termux.app.wayland.ILinuxWindowsCallback;

/**
 * The Linux windows host, served by LinuxWindowsService in the :linuxwindows process. This is the
 * interface the launcher's window pages are built on; it changes only with VERSION.
 *
 * Contract:
 * - Every call is oneway: the caller is the launcher's UI thread, and a hung or dead host must
 *   never block it. Calls from one proxy arrive in order. Results come back through
 *   ILinuxWindowsCallback.
 * - On addCallback the host answers onAttached(VERSION, pid) first; the launcher compares it with
 *   its own ILinuxWindows.VERSION and stops using a host that differs. Then it replays the host
 *   state and every live window (onWindowCreated + onWindowChanged).
 * - Window ids are unique for the life of the host process, across stop and start, so a late call
 *   for a gone window reaches nothing. Calls for unknown ids are dropped.
 * - A window is drawn into the Surface it was given, at most once per display frame, and only
 *   while it is visible. A window that is hidden, or has no Surface, copies nothing, gets frame
 *   callbacks only at a slow keep-alive, and keeps its last frame so showing it repaints at once.
 * - Sizes are pixels of the launcher's view; scale is the display density (1.0 = 160 dpi). The
 *   host tells clients the scale (wl_output scale, rounded up, and wp_fractional_scale_v1) and
 *   asks for logical sizes of width / scale. X windows get pixel sizes.
 * - The numbers the host reads are mirrored in wayland-host/src/protocol.rs;
 *   LinuxWindowsProtocolTest and the crate's protocol test fail when they drift.
 */
oneway interface ILinuxWindows {
    /** Raised with every change to this interface or its callback. */
    const int VERSION = 1;

    // Host states, ILinuxWindowsCallback.onHostState.
    /** Asked to start; running or stopped follows. */
    const int STATE_STARTING = 1;
    /** Clients may connect. */
    const int STATE_RUNNING = 2;
    /** Not running: never started, or stopped on request. start() starts it. */
    const int STATE_STOPPED = 3;
    /** The compositor ended without being asked (a crash); its windows are gone. start() starts it again. */
    const int STATE_DIED = 4;
    /** This build has no host. */
    const int STATE_UNAVAILABLE = 5;

    // Touch actions: MotionEvent.getActionMasked().
    const int TOUCH_DOWN = 0;
    const int TOUCH_UP = 1;
    const int TOUCH_MOVE = 2;
    const int TOUCH_CANCEL = 3;
    const int TOUCH_POINTER_DOWN = 5;
    const int TOUCH_POINTER_UP = 6;
    /** Pointers one touch() call carries; more are dropped. */
    const int MAX_TOUCH_POINTERS = 10;

    // Pointer actions.
    /** x, y: position in view pixels. */
    const int POINTER_MOTION = 0;
    /** button: a BTN_* code. */
    const int POINTER_BUTTON_DOWN = 1;
    const int POINTER_BUTTON_UP = 2;
    /** x, y: scroll distance in view pixels, positive right and down. */
    const int POINTER_SCROLL = 3;
    /** The pointer left the view. */
    const int POINTER_LEAVE = 4;

    // Linux input button codes.
    const int BTN_LEFT = 272;
    const int BTN_RIGHT = 273;
    const int BTN_MIDDLE = 274;

    // Window flags, onWindowCreated.
    /** An X11 window from Xwayland; otherwise a Wayland xdg_toplevel. */
    const int WINDOW_X11 = 1;

    /** Registers a listener: onAttached, then the host state and every live window. */
    void addCallback(ILinuxWindowsCallback callback);
    void removeCallback(ILinuxWindowsCallback callback);

    /**
     * Starts the compositor unless it runs; answered by onHostState. socketDir: the clients'
     * XDG_RUNTIME_DIR (the socket is wayland-0 in it). xkbConfigRoot (nullable): the keymap data.
     * xwaylandBinary (nullable): also start rootless Xwayland. output: the display in pixels and its
     * density. framePeriodNanos: the display's refresh period, the host's frame tick (0: 60 Hz).
     */
    void start(String socketDir, String xkbConfigRoot, String xwaylandBinary, int outputWidth,
            int outputHeight, float scale, long framePeriodNanos);
    /** Stops the compositor (clients and Xwayland end); answered by onHostState. */
    void stop();

    /** The Surface the window is drawn into; null takes it away. */
    void setSurface(long id, in Surface surface);
    /** The view's size in pixels and the display density it is shown at. */
    void configure(long id, int width, int height, float scale);
    /** Whether the window's view is on screen. Windows start visible. */
    void setVisible(long id, boolean visible);
    /** Gives the window keyboard focus; 0 takes focus from every window. Keys held at the change are released. */
    void setFocus(long id);

    /**
     * One MotionEvent: action and actionIndex as getActionMasked() and getActionIndex(),
     * eventTimeMs as getEventTime(), and every pointer's id and position in view pixels.
     */
    void touch(long id, int action, int actionIndex, long eventTimeMs, int pointerCount,
            in int[] pointerIds, in float[] xs, in float[] ys);
    /** A mouse or touchpad event; see POINTER_*. */
    void pointer(long id, int action, int button, float x, float y, long eventTimeMs);
    /** A hardware key, as an Android key code; only the focused window takes keys. */
    void key(long id, int keyCode, boolean down, long eventTimeMs);
    /** Asks the window to close (the app may ask the user first). */
    void close(long id);
}
