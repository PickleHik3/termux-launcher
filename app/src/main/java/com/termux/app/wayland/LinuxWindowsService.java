package com.termux.app.wayland;

import android.app.Service;
import android.content.Intent;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Process;
import android.os.RemoteCallbackList;
import android.os.RemoteException;
import android.util.Log;
import android.util.LongSparseArray;
import android.view.Surface;

import androidx.annotation.Nullable;

import java.io.File;
import java.io.IOException;

/**
 * Hosts the Wayland compositor in its own process ({@code :linuxwindows}), so a crash in it
 * (or, later, in a GPU driver it loads) cannot take the launcher down, and Linux apps outlive
 * the activity that shows them. {@link ILinuxWindows} has the contract.
 *
 * The compositor stops only on {@link ILinuxWindows#stop()} or when it dies; the service then
 * stops itself once nothing is bound, and the process ends with it ({@link #onDestroy}), so an
 * idle host leaves no resident process; the next bind starts a new one. Binding with {@link #ACTION_DEBUG} gives
 * {@link ILinuxWindowsDebug} instead, for the test screen.
 */
public final class LinuxWindowsService extends Service implements WaylandHost.Listener {

    private static final String LOG_TAG = "TLWayland";

    /** Bind with this action for {@link ILinuxWindowsDebug}. */
    public static final String ACTION_DEBUG = LinuxWindowsService.class.getName() + ".DEBUG";

    /** What the service remembers of a window, to replay it to a newly bound listener. */
    private static final class Window {
        String title;
        String appId;
        int clientPid;
        int flags;
        /** The window this one is a dialog of; 0 for none. */
        long parentId;
        int width;
        int height;
        /** The launcher's note for this window (ILinuxWindows#setWindowNote); null for none. */
        String note;
        /**
         * The icon the app set at run time, for replay: a name and/or up to 192 x 192 pixels
         * (147 KB), held only while the window is open. Null for none.
         */
        String iconName;
        int iconEdge;
        int[] iconPixels;
    }

    private interface Call {
        void run(ILinuxWindowsCallback callback) throws RemoteException;
    }

    private final Handler mMain = new Handler(Looper.getMainLooper());
    private final RemoteCallbackList<ILinuxWindowsCallback> mCallbacks = new RemoteCallbackList<ILinuxWindowsCallback>() {
        @Override
        public void onCallbackDied(ILinuxWindowsCallback callback) {
            // The launcher process died without detaching: the Surfaces it handed over are dead.
            mMain.post(LinuxWindowsService.this::detachIfNobodyListens);
        }
    };
    /** Main thread only. */
    private final LongSparseArray<Window> mWindows = new LongSparseArray<>();
    private boolean mLoaded;
    /** Written on the main thread, read by binder threads to drop calls for a stopped host. */
    private volatile boolean mRunning;
    private int mState = ILinuxWindows.STATE_STOPPED;
    private String mMessage = "";
    /** Xwayland's display for replay; empty when there is none. Main thread. */
    private String mXDisplay = "";
    /** The Linux apps this host runs for the launcher. Main thread. */
    /** The session bus the apps run on; it ends with them when the host stops. */
    private final LinuxAppBus mBus = new LinuxAppBus(mMain);
    private final LinuxAppChildren mChildren = new LinuxAppChildren(mMain, new LinuxAppChildren.Listener() {
        @Override
        public void started(long token, String tag, int pid) {
            broadcast(cb -> cb.onAppStarted(token, tag, pid));
        }

        @Override
        public void exited(long token, String tag, int exitCode) {
            broadcast(cb -> cb.onAppExited(token, tag, exitCode));
        }
    });

    private final ILinuxWindows.Stub mBinder = new ILinuxWindows.Stub() {
        @Override
        public void addCallback(ILinuxWindowsCallback callback) {
            if (callback == null) return;
            mMain.post(() -> {
                mCallbacks.register(callback);
                replay(callback);
            });
        }

        @Override
        public void removeCallback(ILinuxWindowsCallback callback) {
            // On main as well, so it cannot overtake the addCallback before it.
            if (callback != null) mMain.post(() -> mCallbacks.unregister(callback));
        }

        @Override
        public void start(String socketDir, String xkbConfigRoot, String xwaylandBinary, int outputWidth,
                          int outputHeight, float scale, long framePeriodNanos,
                          android.os.ParcelFileDescriptor listenSocket) {
            mMain.post(() -> doStart(socketDir, xkbConfigRoot, xwaylandBinary, outputWidth, outputHeight,
                scale, framePeriodNanos, listenSocket));
        }

        @Override
        public void stop() {
            mMain.post(LinuxWindowsService.this::doStop);
        }

        @Override
        public void setSurface(long id, Surface surface) {
            if (mRunning) WaylandHost.nativeSetSurface(id, surface);
            // Native holds its own reference to the window now.
            if (surface != null) surface.release();
        }

        @Override
        public void configure(long id, int width, int height, float scale) {
            if (mRunning) WaylandHost.nativeConfigure(id, width, height, scale);
        }

        @Override
        public void setPageSize(int width, int height, float scale) {
            if (mRunning) WaylandHost.nativeSetPageSize(width, height, scale);
        }

        @Override
        public void setVisible(long id, boolean visible) {
            if (mRunning) WaylandHost.nativeSetVisible(id, visible);
        }

        @Override
        public void setFullscreen(long id, boolean fullscreen) {
            if (mRunning) WaylandHost.nativeSetFullscreen(id, fullscreen);
        }

        @Override
        public void setFocus(long id) {
            if (mRunning) WaylandHost.nativeSetFocus(id);
        }

        @Override
        public void touch(long id, int action, int actionIndex, long eventTimeMs, int pointerCount,
                          int[] pointerIds, float[] xs, float[] ys) {
            if (!mRunning || pointerIds == null || xs == null || ys == null) return;
            int n = Math.min(Math.min(pointerCount, ILinuxWindows.MAX_TOUCH_POINTERS),
                Math.min(pointerIds.length, Math.min(xs.length, ys.length)));
            WaylandHost.nativeTouch(id, action, actionIndex, eventTimeMs, Math.max(n, 0), pointerIds, xs, ys);
        }

        @Override
        public void pointer(long id, int action, int button, float x, float y, long eventTimeMs) {
            if (mRunning) WaylandHost.nativePointer(id, action, button, x, y, eventTimeMs);
        }

        @Override
        public void key(long id, int keyCode, boolean down, long eventTimeMs) {
            if (mRunning) WaylandHost.nativeKey(id, keyCode, down, eventTimeMs);
        }

        @Override
        public void close(long id) {
            if (mRunning) WaylandHost.nativeClose(id);
        }

        @Override
        public void setClipboardText(String text) {
            if (mRunning && LinuxClipboardText.acceptable(text)) WaylandHost.nativeSetClipboardText(text);
        }

        @Override
        public void commitText(long id, String text) {
            if (mRunning && text != null && !text.isEmpty()) WaylandHost.nativeCommitText(id, text);
        }

        @Override
        public void runApp(long token, String tag, String[] argv, String[] env, String cwd, String logPath) {
            mMain.post(() -> {
                // A desktop drops any bus it is given and starts its own; an app is put on the
                // host's, so it does not end with the launcher's (LinuxAppBus).
                LinuxAppTag.Parsed parsed = LinuxAppTag.parse(tag);
                if (parsed != null && parsed.desktop) {
                    mChildren.run(token, tag, argv, env, cwd, logPath);
                } else {
                    mBus.ensure(env, busEnv -> mChildren.run(token, tag, argv, busEnv, cwd, logPath));
                }
            });
        }

        @Override
        public void setWindowNote(long id, String note) {
            mMain.post(() -> {
                Window w = mWindows.get(id);
                if (w != null) w.note = note == null || note.isEmpty() ? null : note;
            });
        }

        @Override
        public void setPreeditText(long id, String text, int cursor) {
            if (mRunning) WaylandHost.nativeSetPreeditText(id, text == null ? "" : text, cursor);
        }

        @Override
        public void deleteSurroundingText(long id, int beforeLength, int afterLength) {
            if (mRunning) WaylandHost.nativeDeleteSurroundingText(id, beforeLength, afterLength);
        }
    };

    private final ILinuxWindowsDebug.Stub mDebugBinder = new ILinuxWindowsDebug.Stub() {
        @Override
        public void crash() {
            Log.e(LOG_TAG, "Crash compositor requested in pid " + Process.myPid());
            if (mLoaded) WaylandHost.nativeAbort();
            else Process.killProcess(Process.myPid());
        }

        @Override
        public void probe() {
            if (mLoaded) WaylandHost.nativeProbe();
        }

        @Override
        public void setCopyLog(boolean on) {
            if (mLoaded) WaylandHost.nativeSetCopyLog(on);
        }

        @Override
        public void dumpWindow(long id, String path) {
            File dataDir = getFilesDir().getParentFile();
            String checked = dataDir == null ? null : dumpPathInside(dataDir.getPath(), path);
            if (checked == null) {
                Log.w(LOG_TAG, "dump: refused path outside the app's data directory: " + path);
                return;
            }
            File parent = new File(checked).getParentFile();
            if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
                Log.w(LOG_TAG, "dump: cannot create " + parent);
                return;
            }
            if (mLoaded && mRunning) WaylandHost.nativeDumpWindow(id, checked);
        }
    };

    /**
     * The canonical form of path when it lies inside dataDir (symlinks and ".." resolved), else
     * null. The debug dump writes only there.
     */
    @Nullable
    static String dumpPathInside(String dataDir, @Nullable String path) {
        if (path == null || path.isEmpty()) return null;
        try {
            String root = new File(dataDir).getCanonicalPath();
            String file = new File(path).getCanonicalPath();
            return file.startsWith(root + File.separator) ? file : null;
        } catch (IOException e) {
            return null;
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        mLoaded = WaylandHost.load();
        if (!mLoaded) {
            mState = ILinuxWindows.STATE_UNAVAILABLE;
            mMessage = "This build has no Linux windows host.";
        }
        WaylandHost.setListener(this);
        Log.i(LOG_TAG, "service up in pid " + Process.myPid());
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // A host that died stays down until the launcher asks again.
        return START_NOT_STICKY;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        if (intent != null && ACTION_DEBUG.equals(intent.getAction())) return mDebugBinder;
        return mBinder;
    }

    @Override
    public void onDestroy() {
        if (mRunning) {
            WaylandHost.stop();
            mRunning = false;
        }
        WaylandHost.setListener(null);
        mCallbacks.kill();
        super.onDestroy();
        // Destroyed means stopped (or never started) and nobody bound. Native teardown is done:
        // WaylandHost.stop() joined the compositor thread, which reaped Xwayland and removed its
        // sockets. The process holds only this service, so it goes rather than idling cached.
        Log.i(LOG_TAG, "service destroyed; ending pid " + Process.myPid());
        Process.killProcess(Process.myPid());
    }

    private void doStart(String socketDir, String xkbConfigRoot, String xwaylandBinary, int outputWidth,
                         int outputHeight, float scale, long framePeriodNanos,
                         @Nullable android.os.ParcelFileDescriptor listenSocket) {
        if (!mLoaded) {
            closeQuietly(listenSocket);
            setState(ILinuxWindows.STATE_UNAVAILABLE, "This build has no Linux windows host.");
            return;
        }
        if (mRunning) {
            closeQuietly(listenSocket);
            setState(mState, mMessage);
            return;
        }
        setState(ILinuxWindows.STATE_STARTING, "");
        // Native owns the fd from here and closes it if the start fails.
        int listenFd = listenSocket == null ? -1 : listenSocket.detachFd();
        if (WaylandHost.nativeStart(socketDir, xkbConfigRoot, xwaylandBinary, outputWidth, outputHeight,
                scale, framePeriodNanos, listenFd)) {
            mRunning = true;
            // The same test as wayland-host/src/xkbroot.rs: without it the host has no keyboard.
            String keymap = keymapMissingAt(xkbConfigRoot);
            setState(ILinuxWindows.STATE_RUNNING, "WAYLAND_DISPLAY=wayland-0 in " + socketDir
                + (xwaylandBinary != null ? "; X display in logcat" : "")
                + (keymap != null ? "; no keyboard: keymap data missing at " + keymap : ""));
        } else {
            setState(ILinuxWindows.STATE_STOPPED, "The host did not start; see logcat TLWayland.");
        }
    }

    private static void closeQuietly(@Nullable android.os.ParcelFileDescriptor fd) {
        if (fd == null) return;
        try {
            fd.close();
        } catch (java.io.IOException ignored) {
        }
    }

    /** The directory looked in when it lacks xkb data (null when it is usable). */
    @Nullable
    private static String keymapMissingAt(@Nullable String root) {
        if (root == null) return "(none given)";
        File dir = new File(root);
        boolean ok = new File(dir, "rules/evdev").isFile();
        for (String d : new String[] {"symbols", "keycodes", "types", "compat"}) {
            ok &= new File(dir, d).isDirectory();
        }
        return ok ? null : root;
    }

    private void doStop() {
        mBus.stop();
        mChildren.endAll();
        if (mRunning) {
            WaylandHost.stop();
            mRunning = false;
        }
        dropWindows();
        setXDisplay("");
        setState(ILinuxWindows.STATE_STOPPED, "Stopped.");
        // Ends the started state; the service goes once nothing is bound.
        stopSelf();
    }

    /** Every window of a host that is gone is destroyed for every listener. */
    private void setXDisplay(String display) {
        if (display.equals(mXDisplay)) return;
        mXDisplay = display;
        broadcast(cb -> cb.onXDisplay(display));
    }

    private void dropWindows() {
        for (int i = 0; i < mWindows.size(); i++) {
            long id = mWindows.keyAt(i);
            broadcast(cb -> cb.onWindowDestroyed(id));
        }
        mWindows.clear();
    }

    /** The last listener died: detach every Surface, so the host stops drawing into dead queues. */
    private void detachIfNobodyListens() {
        if (!mRunning || mCallbacks.getRegisteredCallbackCount() > 0) return;
        for (int i = 0; i < mWindows.size(); i++) WaylandHost.nativeSetSurface(mWindows.keyAt(i), null);
    }

    private void setState(int state, String message) {
        mState = state;
        mMessage = message;
        broadcast(cb -> cb.onHostState(state, message));
    }

    private void replay(ILinuxWindowsCallback callback) {
        try {
            callback.onAttached(ILinuxWindows.VERSION, Process.myPid());
            callback.onHostState(mState, mMessage);
            callback.onXDisplay(mXDisplay);
            for (int i = 0; i < mWindows.size(); i++) {
                long id = mWindows.keyAt(i);
                Window w = mWindows.valueAt(i);
                callback.onWindowCreated(id, w.title, w.appId, w.clientPid, w.flags | ILinuxWindows.WINDOW_REPLAYED, w.parentId);
                callback.onWindowChanged(id, w.title, w.appId, w.width, w.height);
                if (w.iconPixels != null || w.iconName != null) {
                    callback.onWindowIcon(id, w.iconName == null ? "" : w.iconName, w.iconEdge,
                        w.iconPixels == null ? new int[0] : w.iconPixels);
                }
                if (w.note != null) callback.onWindowNote(id, w.note);
            }
            LongSparseArray<LinuxAppChildren.Child> live = mChildren.live();
            for (int i = 0; i < live.size(); i++) {
                LinuxAppChildren.Child child = live.valueAt(i);
                callback.onAppStarted(live.keyAt(i), child.tag, child.pid);
            }
            callback.onReplayEnd();
        } catch (RemoteException e) {
            Log.w(LOG_TAG, "replay to a gone listener: " + e);
        }
    }

    private void broadcast(Call call) {
        int n = mCallbacks.beginBroadcast();
        try {
            for (int i = 0; i < n; i++) {
                try {
                    call.run(mCallbacks.getBroadcastItem(i));
                } catch (RemoteException ignored) {
                    // RemoteCallbackList drops a dead listener on its own.
                } catch (RuntimeException ignored) {
                    // A text too big for the binder buffer (TransactionTooLargeException): dropped.
                }
            }
        } finally {
            mCallbacks.finishBroadcast();
        }
    }

    // WaylandHost.Listener: compositor events, on the main thread.

    @Override
    public void onWindowCreated(long id, String title, String appId, int clientPid, int flags, long parentId) {
        Window w = new Window();
        w.parentId = parentId;
        w.title = title;
        w.appId = appId;
        w.clientPid = clientPid;
        w.flags = flags;
        mWindows.put(id, w);
        broadcast(cb -> cb.onWindowCreated(id, title, appId, clientPid, flags, parentId));
    }

    @Override
    public void onWindowChanged(long id, String title, String appId, int width, int height) {
        Window w = mWindows.get(id);
        if (w == null) return;
        w.title = title;
        w.appId = appId;
        w.width = width;
        w.height = height;
        broadcast(cb -> cb.onWindowChanged(id, title, appId, width, height));
    }

    @Override
    public void onWindowDestroyed(long id) {
        if (mWindows.get(id) == null) return;
        mWindows.remove(id);
        broadcast(cb -> cb.onWindowDestroyed(id));
    }

    @Override
    public void onXDisplay(String display) {
        if (!mRunning) return;
        setXDisplay(display == null ? "" : display);
    }

    @Override
    public void onClipboardText(String text) {
        if (!mRunning || !LinuxClipboardText.acceptable(text)) return;
        broadcast(cb -> cb.onClipboardText(text));
    }

    @Override
    public void onFullscreenRequest(long id, boolean fullscreen) {
        if (!mRunning || mWindows.get(id) == null) return;
        broadcast(cb -> cb.onFullscreenRequest(id, fullscreen));
    }

    @Override
    public void onMoveRequest(long id) {
        if (!mRunning || mWindows.get(id) == null) return;
        broadcast(cb -> cb.onMoveRequest(id));
    }

    @Override
    public void onWindowMenuRequest(long id) {
        if (!mRunning || mWindows.get(id) == null) return;
        broadcast(cb -> cb.onWindowMenuRequest(id));
    }

    @Override
    public void onTextInput(long id, boolean active, int contentHint, int contentPurpose) {
        if (!mRunning || mWindows.get(id) == null) return;
        broadcast(cb -> cb.onTextInput(id, active, contentHint, contentPurpose));
    }

    @Override
    public void onActivationRequest(long id) {
        if (!mRunning || mWindows.get(id) == null) return;
        broadcast(cb -> cb.onActivationRequest(id));
    }

    @Override
    public void onIdleInhibit(long id, boolean inhibited) {
        if (!mRunning || mWindows.get(id) == null) return;
        broadcast(cb -> cb.onIdleInhibit(id, inhibited));
    }

    @Override
    public void onWindowIcon(long id, String name, int edge, int[] argb) {
        Window w = mWindows.get(id);
        if (!mRunning || w == null) return;
        boolean none = (name == null || name.isEmpty()) && (edge <= 0 || argb == null || argb.length == 0);
        w.iconName = none || name == null || name.isEmpty() ? null : name;
        w.iconEdge = none || argb == null ? 0 : edge;
        w.iconPixels = none || argb == null || argb.length == 0 ? null : argb;
        String sentName = w.iconName == null ? "" : w.iconName;
        int[] sentPixels = w.iconPixels == null ? new int[0] : w.iconPixels;
        int sentEdge = w.iconEdge;
        broadcast(cb -> cb.onWindowIcon(id, sentName, sentEdge, sentPixels));
    }

    @Override
    public void onHostEnded(boolean crashed) {
        if (!mRunning) return;
        Log.e(LOG_TAG, "the compositor ended without being asked" + (crashed ? " (panic)" : ""));
        mBus.stop();
        mChildren.endAll();
        // Joins the finished thread and clears it, so a later start works.
        WaylandHost.stop();
        mRunning = false;
        dropWindows();
        setXDisplay("");
        setState(ILinuxWindows.STATE_DIED, "The Linux windows host stopped unexpectedly.");
    }
}
