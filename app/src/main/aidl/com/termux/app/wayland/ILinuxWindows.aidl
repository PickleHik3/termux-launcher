package com.termux.app.wayland;

import android.os.ParcelFileDescriptor;
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
    const int VERSION = 9;

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
    /** Bits 4-7 of the window flags hold the window class: (flags & WINDOW_CLASS_MASK) >> WINDOW_CLASS_SHIFT. */
    const int WINDOW_CLASS_SHIFT = 4;
    const int WINDOW_CLASS_MASK = 0xF0;
    const int WINDOW_CLASS_MAIN = 0;
    const int WINDOW_CLASS_DIALOG = 1;
    const int WINDOW_CLASS_FIXED = 2;
    const int WINDOW_CLASS_TRANSIENT = 3;
    /** Set by the service on a window it re-sends to a newly registered listener (one that opened before it registered); never by the host. */
    const int WINDOW_REPLAYED = 0x100;

    /** Registers a listener: onAttached, then the host state and every live window. */
    void addCallback(ILinuxWindowsCallback callback);
    void removeCallback(ILinuxWindowsCallback callback);

    /**
     * Starts the compositor unless it runs; answered by onHostState. socketDir: the clients'
     * XDG_RUNTIME_DIR (the socket is wayland-0 in it). xkbConfigRoot (nullable): the keymap data.
     * xwaylandBinary (nullable): also start rootless Xwayland. output: the display in pixels and its
     * density. framePeriodNanos: the display's refresh period, the host's frame tick (0: 60 Hz).
     * listenSocket (nullable): a socket the launcher already listens on in socketDir, taken over
     * by the host and left in place when it stops; null makes the host bind the path itself.
     */
    void start(String socketDir, String xkbConfigRoot, String xwaylandBinary, int outputWidth,
            int outputHeight, float scale, long framePeriodNanos, in ParcelFileDescriptor listenSocket);
    /** Stops the compositor (clients and Xwayland end); answered by onHostState. */
    void stop();

    /** The Surface the window is drawn into; null takes it away. */
    void setSurface(long id, in Surface surface);
    /** The view's size in pixels and the display density it is shown at. */
    void configure(long id, int width, int height, float scale);
    /**
     * The page slot a new full-page window gets, in pixels, and the scale it would be shown at:
     * a Main window's first configure uses it while the window has no view yet (since VERSION 6).
     * Sent whenever it changes.
     */
    void setPageSize(int width, int height, float scale);
    /** Whether the window's view is on screen. Windows start visible. */
    void setVisible(long id, boolean visible);
    /**
     * Shows the window full screen (the Fullscreen state, _NET_WM_STATE_FULLSCREEN), or takes it
     * back; the size comes with the next configure. The launcher's answer to onFullscreenRequest.
     */
    void setFullscreen(long id, boolean fullscreen);
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

    /**
     * Android's clipboard text, offered to Linux apps as the plain-text selection. Texts longer
     * than the host takes (see LinuxClipboardText) and null are dropped.
     */
    void setClipboardText(String text);

    /**
     * Typed text for the window's focused text field (zwp_text_input_v3 commit_string), inserted
     * in place of any composing text; dropped unless the window has a field enabled, as
     * onTextInput told (since VERSION 7). Keys still go through key().
     */
    void commitText(long id, String text);
    /** Composing text shown in the field (empty clears it); cursor: a UTF-16 index into it, -1 none. */
    void setPreeditText(long id, String text, int cursor);
    /** Deletes UTF-16 code units before and after the field's cursor. */
    void deleteSurroundingText(long id, int beforeLength, int afterLength);
}
