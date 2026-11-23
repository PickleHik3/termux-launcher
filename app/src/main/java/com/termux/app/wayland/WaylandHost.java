package com.termux.app.wayland;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Surface;

import androidx.annotation.Keep;
import androidx.annotation.Nullable;

/**
 * The Java side of libtlwayland (spike): a Wayland compositor running on its own thread. Used
 * only inside the {@code :linuxwindows} process by {@link LinuxWindowsService}; the launcher
 * reaches it over {@link ILinuxWindows}. Callbacks arrive on the compositor thread and are
 * posted to the main thread before they reach the {@link Listener}.
 */
@Keep
public final class WaylandHost {

    private static final String LOG_TAG = "TLWayland";

    public static final int TOUCH_DOWN = 0;
    public static final int TOUCH_UP = 1;
    public static final int TOUCH_MOTION = 2;
    public static final int TOUCH_CANCEL = 3;

    public static final int POINTER_MOTION = 0;
    public static final int POINTER_BUTTON_DOWN = 1;
    public static final int POINTER_BUTTON_UP = 2;
    public static final int POINTER_AXIS = 3;
    public static final int POINTER_LEAVE = 4;

    /** Linux input button codes. */
    public static final int BTN_LEFT = 0x110;
    public static final int BTN_RIGHT = 0x111;
    public static final int BTN_MIDDLE = 0x112;

    /** What the activity hears about toplevels, on the main thread. */
    public interface Listener {
        void onToplevelCreated(long id, String title, String appId);
        void onToplevelTitle(long id, String title, String appId);
        void onToplevelBufferSize(long id, int width, int height);
        void onToplevelDestroyed(long id);
    }

    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static volatile Listener sListener;
    private static boolean sLoaded;

    private WaylandHost() {}

    /** Loads libtlwayland.so; false when this APK was built without it. */
    public static synchronized boolean load() {
        if (sLoaded) return true;
        try {
            System.loadLibrary("tlwayland");
            sLoaded = true;
        } catch (UnsatisfiedLinkError e) {
            Log.w(LOG_TAG, "libtlwayland.so is not in this APK: " + e.getMessage());
        }
        return sLoaded;
    }

    public static void setListener(@Nullable Listener listener) {
        sListener = listener;
    }

    public static native boolean nativeStart(String socketDir, @Nullable String xkbConfigRoot,
                                             @Nullable String xwaylandBinary, int outputWidth, int outputHeight);
    public static native void nativeStop();
    public static native void nativeSetSurface(long id, @Nullable Surface surface);
    public static native void nativeConfigure(long id, int width, int height);
    public static native void nativeTouch(long id, int action, int count, int[] slots, float[] xs, float[] ys);
    public static native void nativePointer(long id, int action, int button, float x, float y);
    public static native void nativeKey(long id, int androidKeyCode, boolean down);
    public static native void nativeClose(long id);
    /** Debug: abort() this process. */
    public static native void nativeAbort();

    // Called from native code on the compositor thread.

    @Keep
    static void onToplevelCreated(long id, String title, String appId) {
        MAIN.post(() -> {
            Listener l = sListener;
            if (l != null) l.onToplevelCreated(id, title, appId);
        });
    }

    @Keep
    static void onToplevelTitle(long id, String title, String appId) {
        MAIN.post(() -> {
            Listener l = sListener;
            if (l != null) l.onToplevelTitle(id, title, appId);
        });
    }

    @Keep
    static void onToplevelBufferSize(long id, int width, int height) {
        MAIN.post(() -> {
            Listener l = sListener;
            if (l != null) l.onToplevelBufferSize(id, width, height);
        });
    }

    @Keep
    static void onToplevelDestroyed(long id) {
        MAIN.post(() -> {
            Listener l = sListener;
            if (l != null) l.onToplevelDestroyed(id);
        });
    }
}
