package com.termux.app.wayland;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Surface;

import androidx.annotation.Keep;
import androidx.annotation.MainThread;
import androidx.annotation.Nullable;

/**
 * The Java side of libtlwayland: a Wayland compositor running on its own thread. Used only
 * inside the {@code :linuxwindows} process by {@link LinuxWindowsService}; the launcher reaches it
 * over {@link ILinuxWindows}, whose constants the native calls take. Callbacks arrive on the
 * compositor thread and are posted to the main thread before they reach the {@link Listener};
 * those still queued from a host that has since been stopped are dropped there.
 */
@Keep
public final class WaylandHost {

    private static final String LOG_TAG = "TLWayland";

    /** What the service hears from the compositor, on the main thread. */
    public interface Listener {
        void onWindowCreated(long id, String title, String appId, int clientPid, int flags, long parentId);
        void onWindowChanged(long id, String title, String appId, int width, int height);
        void onWindowDestroyed(long id);
        /** Xwayland's display (":0"), or empty when it is gone. */
        void onXDisplay(String display);
        /** Text a Linux app copied. */
        void onClipboardText(String text);
        /** The window asked to be full screen, or to leave it. */
        void onFullscreenRequest(long id, boolean fullscreen);
        /** The window asked to be moved (a drag on its own title bar). */
        void onMoveRequest(long id);
        /** The window asked for its window menu. */
        void onWindowMenuRequest(long id);
        /** The window's text field took focus ({@code active}) or lost it. */
        void onTextInput(long id, boolean active, int contentHint, int contentPurpose);
        /** An app asked for the window to come in front, with a token the host honours. */
        void onActivationRequest(long id);
        /** The window holds an idle inhibitor, or no longer does. */
        void onIdleInhibit(long id, boolean inhibited);
        void onAttention(long id, boolean on);
        /** The app set the window's icon at run time ({@code edge} 0 and no name: it took it away). */
        void onWindowIcon(long id, String name, int edge, int[] argb);
        /** Where a layer surface stands, right after its onWindowCreated. */
        void onWindowLayer(long id, int anchor, int keyboard, int marginTop, int marginRight, int marginBottom,
                           int marginLeft, int width, int height);
        /** The compositor ended without being asked; {@link #stop()} clears it. */
        void onHostEnded(boolean crashed);
    }

    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static volatile Listener sListener;
    /** Raised by every stop; a callback posted under an older value is dropped. Main thread writes. */
    private static volatile int sGeneration;
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

    /**
     * Stops the compositor (also one that ended on its own) and drops every callback it queued
     * before the stop, so none of them brings a window of the old host back.
     */
    @MainThread
    public static void stop() {
        nativeStop();
        sGeneration = sGeneration + 1;
    }

    public static native boolean nativeStart(String socketDir, @Nullable String xkbConfigRoot,
                                             @Nullable String xwaylandBinary, int outputWidth, int outputHeight,
                                             float scale, long framePeriodNanos, int listenFd);
    /** Use {@link #stop()}. */
    private static native void nativeStop();
    public static native void nativeSetSurface(long id, @Nullable Surface surface);
    public static native void nativeConfigure(long id, int width, int height, float scale);
    /** The page slot a new full-page window gets, and its scale. */
    public static native void nativeSetPageSize(int width, int height, float scale);
    /** The launcher started {@code pid} as a Wayland desktop ({@code on}), or it ended: real pixels from the first configure. */
    public static native void nativeSetPixelClient(int pid, boolean on);
    public static native void nativeSetVisible(long id, boolean visible);
    public static native void nativeSetFocus(long id);
    public static native void nativeSetFullscreen(long id, boolean fullscreen);
    public static native void nativeTouch(long id, int action, int actionIndex, long eventTimeMs, int count,
                                          int[] pointerIds, float[] xs, float[] ys);
    public static native void nativePointer(long id, int action, int button, float x, float y, long eventTimeMs);
    public static native void nativeKey(long id, int androidKeyCode, boolean down, long eventTimeMs);
    public static native void nativeClose(long id);
    /** Android's clipboard text, for Linux apps. */
    public static native void nativeSetClipboardText(String text);
    public static native void nativeCommitText(long id, String text);
    public static native void nativeSetPreeditText(long id, String text, int cursor);
    public static native void nativeDeleteSurroundingText(long id, int beforeLength, int afterLength);
    /** Debug: abort() this process. */
    public static native void nativeAbort();
    /** Debug: log what the GPU drivers offer, on a thread of its own. */
    public static native void nativeProbe();
    /** Debug: the once-a-second copy-time line per window. */
    public static native void nativeSetCopyLog(boolean on);
    /** Debug: write window id's shown pixels as a PNG to path (already checked). */
    public static native void nativeDumpWindow(long id, String path);

    // Called from native code on the compositor thread.

    @Keep
    static void onWindowCreated(long id, String title, String appId, int clientPid, int flags, long parentId) {
        final int generation = sGeneration;
        MAIN.post(() -> {
            Listener l = sListener;
            if (l != null && generation == sGeneration) l.onWindowCreated(id, title, appId, clientPid, flags, parentId);
        });
    }

    @Keep
    static void onWindowChanged(long id, String title, String appId, int width, int height) {
        final int generation = sGeneration;
        MAIN.post(() -> {
            Listener l = sListener;
            if (l != null && generation == sGeneration) l.onWindowChanged(id, title, appId, width, height);
        });
    }

    @Keep
    static void onWindowDestroyed(long id) {
        final int generation = sGeneration;
        MAIN.post(() -> {
            Listener l = sListener;
            if (l != null && generation == sGeneration) l.onWindowDestroyed(id);
        });
    }

    @Keep
    static void onXDisplay(String display) {
        final int generation = sGeneration;
        MAIN.post(() -> {
            Listener l = sListener;
            if (l != null && generation == sGeneration) l.onXDisplay(display);
        });
    }

    @Keep
    static void onClipboardText(String text) {
        final int generation = sGeneration;
        MAIN.post(() -> {
            Listener l = sListener;
            if (l != null && generation == sGeneration) l.onClipboardText(text);
        });
    }

    @Keep
    static void onFullscreenRequest(long id, boolean fullscreen) {
        final int generation = sGeneration;
        MAIN.post(() -> {
            Listener l = sListener;
            if (l != null && generation == sGeneration) l.onFullscreenRequest(id, fullscreen);
        });
    }

    @Keep
    static void onMoveRequest(long id) {
        final int generation = sGeneration;
        MAIN.post(() -> {
            Listener l = sListener;
            if (l != null && generation == sGeneration) l.onMoveRequest(id);
        });
    }

    @Keep
    static void onWindowMenuRequest(long id) {
        final int generation = sGeneration;
        MAIN.post(() -> {
            Listener l = sListener;
            if (l != null && generation == sGeneration) l.onWindowMenuRequest(id);
        });
    }

    @Keep
    static void onTextInput(long id, boolean active, int contentHint, int contentPurpose) {
        final int generation = sGeneration;
        MAIN.post(() -> {
            Listener l = sListener;
            if (l != null && generation == sGeneration) l.onTextInput(id, active, contentHint, contentPurpose);
        });
    }

    @Keep
    static void onActivationRequest(long id) {
        final int generation = sGeneration;
        MAIN.post(() -> {
            Listener l = sListener;
            if (l != null && generation == sGeneration) l.onActivationRequest(id);
        });
    }

    @Keep
    static void onAttention(long id, boolean on) {
        final int generation = sGeneration;
        MAIN.post(() -> {
            Listener l = sListener;
            if (l != null && generation == sGeneration) l.onAttention(id, on);
        });
    }

    @Keep
    static void onIdleInhibit(long id, boolean inhibited) {
        final int generation = sGeneration;
        MAIN.post(() -> {
            Listener l = sListener;
            if (l != null && generation == sGeneration) l.onIdleInhibit(id, inhibited);
        });
    }

    @Keep
    static void onWindowIcon(long id, String name, int edge, int[] argb) {
        final int generation = sGeneration;
        MAIN.post(() -> {
            Listener l = sListener;
            if (l != null && generation == sGeneration) l.onWindowIcon(id, name, edge, argb);
        });
    }

    @Keep
    static void onWindowLayer(long id, int anchor, int keyboard, int marginTop, int marginRight, int marginBottom,
                              int marginLeft, int width, int height) {
        final int generation = sGeneration;
        MAIN.post(() -> {
            Listener l = sListener;
            if (l != null && generation == sGeneration) {
                l.onWindowLayer(id, anchor, keyboard, marginTop, marginRight, marginBottom, marginLeft, width, height);
            }
        });
    }

    @Keep
    static void onHostEnded(boolean crashed) {
        final int generation = sGeneration;
        MAIN.post(() -> {
            Listener l = sListener;
            if (l != null && generation == sGeneration) l.onHostEnded(crashed);
        });
    }
}
