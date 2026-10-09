package com.termux.privileged.lane;

import android.content.ComponentName;
import android.content.Context;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.BuildConfig;
import com.termux.privileged.PrivilegedPolicyStore;

import rikka.shizuku.Shizuku;

/**
 * Keeps {@link PrivilegedLaneService} bound for as long as the launcher lives, so a launch is a
 * binder call rather than a process start, and binds it again when it dies or when Shizuku
 * comes back. {@link #acquire} is what the socket server calls per request: it returns a live
 * service or the one-line reason the client should print.
 */
public final class PrivilegedLaneBinding {

    private static final String TAG = "PrivilegedLaneBinding";
    private static final long REBIND_DELAY_MS = 2_000L;

    /** What the settings screen shows. */
    public enum State { OFF, NO_SHIZUKU, NO_PERMISSION, BINDING, RUNNING }

    /** The lane cannot serve right now; the message is for the client. */
    public static final class Unavailable extends Exception {
        Unavailable(@NonNull String message) {
            super(message);
        }
    }

    private static PrivilegedLaneBinding instance;

    private final Object lock = new Object();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private Context appContext;
    private Shizuku.UserServiceArgs args;
    private boolean listening;
    private boolean binding;
    @Nullable private IPrivilegedLane lane;
    /** What the service last reported through {@link IPrivilegedLane#uid()}, for the status row. */
    private int serviceUid = -1;

    private final ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder binder) {
            if (binder == null || !binder.pingBinder()) {
                Log.w(TAG, "Lane service connected with a dead binder");
                onServiceDisconnected(name);
                return;
            }
            IPrivilegedLane service = IPrivilegedLane.Stub.asInterface(binder);
            int uid = -1;
            try {
                uid = service.uid();
            } catch (Exception e) {
                Log.w(TAG, "Lane service does not answer uid()", e);
            }
            synchronized (lock) {
                lane = service;
                serviceUid = uid;
                binding = false;
                lock.notifyAll();
            }
            Log.i(TAG, "Lane service bound, uid " + uid);
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            synchronized (lock) {
                lane = null;
                serviceUid = -1;
                binding = false;
                lock.notifyAll();
            }
            Log.i(TAG, "Lane service gone; rebinding shortly");
            mainHandler.postDelayed(PrivilegedLaneBinding.this::bindIfPossible, REBIND_DELAY_MS);
        }
    };

    private final Shizuku.OnBinderReceivedListener binderReceived = this::bindIfPossible;
    private final Shizuku.OnBinderDeadListener binderDead = () -> {
        synchronized (lock) {
            lane = null;
            serviceUid = -1;
            binding = false;
            lock.notifyAll();
        }
    };
    private final Shizuku.OnRequestPermissionResultListener permissionResult =
        (requestCode, grantResult) -> bindIfPossible();

    private PrivilegedLaneBinding() {
    }

    public static synchronized PrivilegedLaneBinding getInstance() {
        if (instance == null) instance = new PrivilegedLaneBinding();
        return instance;
    }

    /** Registers with Shizuku once and binds as soon as it can; safe to call again. */
    public void start(@NonNull Context context) {
        synchronized (lock) {
            this.appContext = context.getApplicationContext();
            if (args == null) {
                args = new Shizuku.UserServiceArgs(
                    new ComponentName(this.appContext.getPackageName(), PrivilegedLaneService.class.getName()))
                    .daemon(false)
                    .processNameSuffix("priv")
                    .debuggable(BuildConfig.DEBUG)
                    .version(PrivilegedLaneService.VERSION);
            }
            if (listening) return;
            listening = true;
        }
        try {
            // Sticky: fires now if the binder is already here, later if Shizuku starts after us.
            Shizuku.addBinderReceivedListenerSticky(binderReceived);
            Shizuku.addBinderDeadListener(binderDead);
            Shizuku.addRequestPermissionResultListener(permissionResult);
        } catch (Exception e) {
            Log.w(TAG, "Cannot register Shizuku listeners", e);
        }
    }

    /** The settings toggle changed: bind or tear down to match it. */
    public void applyPolicy() {
        Context ctx;
        synchronized (lock) {
            ctx = appContext;
        }
        if (ctx == null) return;
        if (PrivilegedPolicyStore.isLaneEnabled(ctx)) {
            bindIfPossible();
        } else {
            unbind();
        }
    }

    @NonNull
    public State state() {
        Context ctx;
        IPrivilegedLane current;
        synchronized (lock) {
            ctx = appContext;
            current = lane;
        }
        if (ctx == null || !PrivilegedPolicyStore.isLaneEnabled(ctx)) return State.OFF;
        if (!shizukuAlive()) return State.NO_SHIZUKU;
        if (!permissionGranted()) return State.NO_PERMISSION;
        if (current != null && current.asBinder().isBinderAlive()) return State.RUNNING;
        // Bound or not yet: the next request binds, so from the outside it is on its way.
        return State.BINDING;
    }

    /** The uid the bound service reported, or -1 while unbound. */
    public int serviceUid() {
        synchronized (lock) {
            return lane == null ? -1 : serviceUid;
        }
    }

    /**
     * A live service, binding first when needed and waiting up to {@code timeoutMs} for it.
     *
     * @throws Unavailable with the line the client prints when there is no service to be had.
     */
    @NonNull
    public IPrivilegedLane acquire(long timeoutMs) throws Unavailable {
        Context ctx;
        synchronized (lock) {
            ctx = appContext;
        }
        if (ctx == null) throw new Unavailable("the privileged lane has not started");
        if (!PrivilegedPolicyStore.isLaneEnabled(ctx)) {
            throw new Unavailable("the privileged lane is turned off in Settings › Shizuku");
        }
        if (!shizukuAlive()) throw new Unavailable("Shizuku is not running");
        if (!permissionGranted()) {
            throw new Unavailable("grant Shizuku permission to " + appLabel(ctx));
        }
        IPrivilegedLane current = liveLane();
        if (current != null) return current;

        bindIfPossible();
        long deadline = System.currentTimeMillis() + timeoutMs;
        synchronized (lock) {
            while (lane == null && binding) {
                long remaining = deadline - System.currentTimeMillis();
                if (remaining <= 0) break;
                try {
                    lock.wait(remaining);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
        current = liveLane();
        if (current == null) {
            throw new Unavailable("the Shizuku service did not start within " + (timeoutMs / 1000) + "s");
        }
        return current;
    }

    @Nullable
    private IPrivilegedLane liveLane() {
        synchronized (lock) {
            if (lane != null && lane.asBinder().isBinderAlive()) return lane;
            lane = null;
            return null;
        }
    }

    private void bindIfPossible() {
        Context ctx;
        Shizuku.UserServiceArgs serviceArgs;
        synchronized (lock) {
            ctx = appContext;
            serviceArgs = args;
            if (ctx == null || serviceArgs == null) return;
            if (binding || (lane != null && lane.asBinder().isBinderAlive())) return;
            if (!PrivilegedPolicyStore.isLaneEnabled(ctx)) return;
            binding = true;
        }
        if (!shizukuAlive() || !permissionGranted()) {
            synchronized (lock) {
                binding = false;
            }
            return;
        }
        try {
            Shizuku.bindUserService(serviceArgs, connection);
            Log.i(TAG, "Binding the lane service");
        } catch (Exception e) {
            Log.w(TAG, "bindUserService failed", e);
            synchronized (lock) {
                binding = false;
                lock.notifyAll();
            }
        }
    }

    private void unbind() {
        Shizuku.UserServiceArgs serviceArgs;
        synchronized (lock) {
            serviceArgs = args;
            lane = null;
            serviceUid = -1;
            binding = false;
            lock.notifyAll();
        }
        if (serviceArgs == null || !shizukuAlive()) return;
        try {
            // remove=true: Shizuku kills the service process rather than keeping it around.
            Shizuku.unbindUserService(serviceArgs, connection, true);
            Log.i(TAG, "Unbound the lane service");
        } catch (Exception e) {
            Log.w(TAG, "unbindUserService failed", e);
        }
    }

    private static boolean shizukuAlive() {
        try {
            return Shizuku.pingBinder() && !Shizuku.isPreV11();
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean permissionGranted() {
        try {
            return Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED;
        } catch (Exception e) {
            return false;
        }
    }

    @NonNull
    private static String appLabel(@NonNull Context context) {
        CharSequence label = context.getApplicationInfo().loadLabel(context.getPackageManager());
        return label == null ? context.getPackageName() : label.toString();
    }
}
