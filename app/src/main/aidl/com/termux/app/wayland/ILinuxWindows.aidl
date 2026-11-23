package com.termux.app.wayland;

import android.view.Surface;
import com.termux.app.wayland.ILinuxWindowsCallback;

/**
 * The Linux windows host (spike), served by LinuxWindowsService in the :linuxwindows process.
 *
 * Every call is oneway: the caller is the launcher's UI thread, and a hung or dead compositor
 * must never block it. Results come back through ILinuxWindowsCallback. Calls from one client
 * arrive in the order they were made.
 */
oneway interface ILinuxWindows {
    /** Registers a listener and replays the host state and every live toplevel to it. */
    void addCallback(ILinuxWindowsCallback callback);
    void removeCallback(ILinuxWindowsCallback callback);

    /** Starts the compositor unless it runs; answered by onHostState. */
    void start(String socketDir, String xkbConfigRoot, String xwaylandBinary, int outputWidth, int outputHeight);
    /** Stops the compositor (clients and Xwayland end); answered by onHostState. */
    void stop();

    /** The Surface the toplevel is shown in; null takes it away. */
    void setSurface(long id, in Surface surface);
    /** The view's size in pixels: the window geometry size the client is asked for. */
    void configure(long id, int width, int height);
    /** One MotionEvent: count points sharing action (0 down, 1 up, 2 motion, 3 cancel). */
    void touch(long id, int action, int count, in int[] slots, in float[] xs, in float[] ys);
    /** 0 motion, 1 button down, 2 button up (Linux BTN_*), 3 axis (x, y scroll px), 4 leave. */
    void pointer(long id, int action, int button, float x, float y);
    /** A hardware key, as an Android key code; the toplevel takes keyboard focus. */
    void key(long id, int keyCode, boolean down);
    /** Asks the toplevel to close. */
    void close(long id);

    /** Debug: abort() the host process. */
    void crash();
}
