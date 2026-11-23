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

/**
 * Spike: hosts the Wayland compositor in its own process ({@code :linuxwindows}), so a crash in
 * it (or, later, in a GPU driver it loads) cannot take the launcher down, and Linux apps outlive
 * the activity that shows them.
 *
 * Started and bound by {@link WaylandSpikeActivity}. The compositor stops only on an explicit
 * {@link ILinuxWindows#stop()}; the service then stops itself once nothing is bound.
 */
public final class LinuxWindowsService extends Service implements WaylandHost.Listener {

    private static final String LOG_TAG = "TLWayland";

    /** What the service remembers of a toplevel, to replay it to a newly bound activity. */
    private static final class Toplevel {
        String title;
        String appId;
        int width;
        int height;
    }

    private interface Call {
        void run(ILinuxWindowsCallback callback) throws RemoteException;
    }

    private final Handler mMain = new Handler(Looper.getMainLooper());
    private final RemoteCallbackList<ILinuxWindowsCallback> mCallbacks = new RemoteCallbackList<>();
    /** Main thread only. */
    private final LongSparseArray<Toplevel> mToplevels = new LongSparseArray<>();
    private boolean mLoaded;
    /** Written on the main thread, read by binder threads to drop calls for a stopped host. */
    private volatile boolean mRunning;
    private String mMessage = "";

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
            if (callback != null) mCallbacks.unregister(callback);
        }

        @Override
        public void start(String socketDir, String xkbConfigRoot, String xwaylandBinary, int outputWidth, int outputHeight) {
            mMain.post(() -> doStart(socketDir, xkbConfigRoot, xwaylandBinary, outputWidth, outputHeight));
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
        public void configure(long id, int width, int height) {
            if (mRunning) WaylandHost.nativeConfigure(id, width, height);
        }

        @Override
        public void touch(long id, int action, int count, int[] slots, float[] xs, float[] ys) {
            if (!mRunning || slots == null || xs == null || ys == null) return;
            int n = Math.min(count, Math.min(slots.length, Math.min(xs.length, ys.length)));
            WaylandHost.nativeTouch(id, action, n, slots, xs, ys);
        }

        @Override
        public void pointer(long id, int action, int button, float x, float y) {
            if (mRunning) WaylandHost.nativePointer(id, action, button, x, y);
        }

        @Override
        public void key(long id, int keyCode, boolean down) {
            if (mRunning) WaylandHost.nativeKey(id, keyCode, down);
        }

        @Override
        public void close(long id) {
            if (mRunning) WaylandHost.nativeClose(id);
        }

        @Override
        public void crash() {
            Log.e(LOG_TAG, "Crash compositor requested in pid " + Process.myPid());
            if (mLoaded) WaylandHost.nativeAbort();
            else Process.killProcess(Process.myPid());
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        mLoaded = WaylandHost.load();
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
        return mBinder;
    }

    @Override
    public void onDestroy() {
        if (mRunning) {
            WaylandHost.nativeStop();
            mRunning = false;
        }
        WaylandHost.setListener(null);
        mCallbacks.kill();
        super.onDestroy();
    }

    private void doStart(String socketDir, String xkbConfigRoot, String xwaylandBinary, int outputWidth, int outputHeight) {
        if (!mRunning) {
            if (!mLoaded) {
                mMessage = "This build has no Linux windows host.";
            } else if (WaylandHost.nativeStart(socketDir, xkbConfigRoot, xwaylandBinary, outputWidth, outputHeight)) {
                mRunning = true;
                mMessage = "WAYLAND_DISPLAY=wayland-0 in " + socketDir
                    + (xwaylandBinary != null ? "; X display in logcat" : "");
            } else {
                mMessage = "The host did not start; see logcat TLWayland.";
            }
        }
        broadcast(cb -> cb.onHostState(mRunning, Process.myPid(), mMessage));
    }

    private void doStop() {
        if (mRunning) {
            WaylandHost.nativeStop();
            mRunning = false;
        }
        for (int i = 0; i < mToplevels.size(); i++) {
            long id = mToplevels.keyAt(i);
            broadcast(cb -> cb.onToplevelDestroyed(id));
        }
        mToplevels.clear();
        mMessage = "Stopped.";
        broadcast(cb -> cb.onHostState(false, Process.myPid(), mMessage));
        // Ends the started state; the service goes once the activity unbinds.
        stopSelf();
    }

    private void replay(ILinuxWindowsCallback callback) {
        try {
            callback.onHostState(mRunning, Process.myPid(), mMessage);
            for (int i = 0; i < mToplevels.size(); i++) {
                long id = mToplevels.keyAt(i);
                Toplevel t = mToplevels.valueAt(i);
                callback.onToplevelCreated(id, t.title, t.appId);
                if (t.width > 0) callback.onToplevelSize(id, t.width, t.height);
            }
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
                }
            }
        } finally {
            mCallbacks.finishBroadcast();
        }
    }

    // WaylandHost.Listener: compositor events, on the main thread.

    @Override
    public void onToplevelCreated(long id, String title, String appId) {
        Toplevel t = new Toplevel();
        t.title = title;
        t.appId = appId;
        mToplevels.put(id, t);
        broadcast(cb -> cb.onToplevelCreated(id, title, appId));
    }

    @Override
    public void onToplevelTitle(long id, String title, String appId) {
        Toplevel t = mToplevels.get(id);
        if (t == null) return;
        t.title = title;
        t.appId = appId;
        broadcast(cb -> cb.onToplevelTitle(id, title, appId));
    }

    @Override
    public void onToplevelBufferSize(long id, int width, int height) {
        Toplevel t = mToplevels.get(id);
        if (t == null) return;
        t.width = width;
        t.height = height;
        broadcast(cb -> cb.onToplevelSize(id, width, height));
    }

    @Override
    public void onToplevelDestroyed(long id) {
        if (mToplevels.get(id) == null) return;
        mToplevels.remove(id);
        broadcast(cb -> cb.onToplevelDestroyed(id));
    }
}
