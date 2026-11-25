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
import com.termux.privileged.PrivilegedBackendManager;
import com.termux.privileged.PrivilegedAvailabilitySignal;
import com.termux.privileged.PrivilegedChoice;
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
    /** A bind that has not connected by now is given up and tried again. */
    static final long BIND_TIMEOUT_MS = 10_000L;

    /** What the settings screen shows. */
    public enum State { OFF, NO_SHIZUKU, NO_PERMISSION, NO_ROOT, BINDING, RUNNING, ROOT }

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
    /** The binding in flight or live; a dead one is replaced, never reused. Guarded by {@link #lock}. */
    @Nullable private LaneConnection connection;
    /** The one timer that ends a bind that never calls back; cancelled when it connects or ends. */
    private final Runnable bindTimeout = this::bindTimedOut;
    @Nullable private IPrivilegedLane lane;
    /**
     * The in-process lane used while root runs and Shizuku cannot. When Shizuku binds later, new
     * spawns go to it and this one is only forgotten: its running children end on their own and
     * the server still holds the stub for their waitFor and kill.
     */
    @Nullable private RootLane rootLane;
    /** What the service last reported through {@link IPrivilegedLane#uid()}, for the status row. */
    private int serviceUid = -1;

    /**
     * One binding to the lane service. Each bind gets its own object so a late call from a binding
     * that was given up or died is ignored.
     */
    final class LaneConnection implements ServiceConnection {
        @Override
        public void onServiceConnected(ComponentName name, IBinder binder) {
            if (!isCurrent(this)) return;
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
                if (connection != this) return;
                lane = service;
                rootLane = null;
                serviceUid = uid;
                binding = false;
                lock.notifyAll();
            }
            mainHandler.removeCallbacks(bindTimeout);
            Log.i(TAG, "Lane service bound, uid " + uid);
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            lost(this, "Lane service gone");
        }

        @Override
        public void onBindingDied(ComponentName name) {
            lost(this, "Lane service binding died");
        }

        @Override
        public void onNullBinding(ComponentName name) {
            lost(this, "Lane service gave no binder");
        }
    }

    private boolean isCurrent(@NonNull LaneConnection c) {
        synchronized (lock) {
            return connection == c;
        }
    }

    /** A binding ended or never connected: forget it, let it go and bind afresh shortly. */
    private void lost(@NonNull LaneConnection c, @NonNull String why) {
        synchronized (lock) {
            if (connection != c) return;
            connection = null;
            if (lane != rootLane) {
                lane = null;
                serviceUid = -1;
            }
            binding = false;
            lock.notifyAll();
        }
        mainHandler.removeCallbacks(bindTimeout);
        unbindQuietly(c);
        Log.i(TAG, why + "; rebinding shortly");
        mainHandler.postDelayed(this::bindIfPossible, REBIND_DELAY_MS);
    }

    /** The bind timer fired: the attempt did not connect, so it is given up and tried again. */
    private void bindTimedOut() {
        LaneConnection c;
        synchronized (lock) {
            c = connection;
            if (!binding || c == null) return;
        }
        lost(c, "Lane service did not connect within " + (BIND_TIMEOUT_MS / 1000) + "s");
    }

    private void unbindQuietly(@NonNull LaneConnection c) {
        Shizuku.UserServiceArgs serviceArgs;
        synchronized (lock) {
            serviceArgs = args;
        }
        if (serviceArgs == null) return;
        try {
            // remove=true: Shizuku kills the service process rather than keeping it around.
            Shizuku.unbindUserService(serviceArgs, c, true);
        } catch (Exception e) {
            Log.w(TAG, "unbindUserService failed", e);
        }
    }

    private final Shizuku.OnBinderReceivedListener binderReceived = this::bindIfPossible;
    private final Shizuku.OnBinderDeadListener binderDead = () -> {
        mainHandler.removeCallbacks(bindTimeout);
        synchronized (lock) {
            connection = null;
            if (lane != rootLane) {
                lane = null;
                serviceUid = -1;
            }
            binding = false;
            lock.notifyAll();
        }
        // Shizuku is gone: a rooted phone carries on through root.
        mainHandler.post(this::bindIfPossible);
    };
    /** Root went away: the su clients it ran are useless, so the root lane ends with it. */
    private final PrivilegedAvailabilitySignal.Listener availabilityListener = available -> {
        if (!isRootActiveNow()) dropRootLane();
        else bindIfPossible();
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
        // The availability signal is main-thread only.
        mainHandler.post(() -> PrivilegedBackendManager.getInstance().addAvailabilityListener(availabilityListener));
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
            // The choice may have changed: let go of the old way, then take the one it allows.
            unbind();
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
        State chosen = ctx == null ? State.OFF : LaneChoice.choose(PrivilegedPolicyStore.getChoice(ctx),
            PrivilegedPolicyStore.isLaneEnabled(ctx), shizukuAlive(), permissionGranted(), isRootActiveNow());
        if (chosen != State.BINDING) return chosen;
        if (current != null && current != rootLaneNow() && current.asBinder().isBinderAlive()) return State.RUNNING;
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
        PrivilegedChoice choice = PrivilegedPolicyStore.getChoice(ctx);
        if (!(choice.allowsShizuku() && shizukuAlive() && permissionGranted())) {
            if (choice.allowsRoot() && isRootActiveNow()) return ensureRootLane(ctx);
            if (choice == PrivilegedChoice.ROOT) throw new Unavailable("root is not available");
            if (!shizukuAlive()) throw new Unavailable("Shizuku is not running");
            throw new Unavailable("grant Shizuku permission to " + appLabel(ctx));
        }
        IPrivilegedLane current = liveLane();
        if (current != null && current == rootLaneNow()) current = null;
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
        if (current != null && current == rootLaneNow()) current = null;
        if (current == null) {
            throw new Unavailable("the Shizuku service did not start within " + (timeoutMs / 1000) + "s");
        }
        return current;
    }

    /**
     * The service when it is bound and alive right now, else null; never binds or waits, so a
     * touch handler can ask (the Home pull-down drives the shade through it).
     */
    @Nullable
    public IPrivilegedLane boundLane() {
        IPrivilegedLane live = liveLane();
        // The root lane cannot inject input, so the Home pull-down does not try it.
        return live != null && live == rootLaneNow() ? null : live;
    }

    @Nullable
    private RootLane rootLaneNow() {
        synchronized (lock) {
            return rootLane;
        }
    }

    private static boolean isRootActiveNow() {
        try {
            return PrivilegedBackendManager.getInstance().isRootActive();
        } catch (Exception e) {
            return false;
        }
    }

    /** The root lane, created once and kept while root stays active. */
    @NonNull
    private IPrivilegedLane ensureRootLane(@NonNull Context ctx) {
        synchronized (lock) {
            if (rootLane == null) {
                rootLane = new RootLane(ctx);
                Log.i(TAG, "Lane runs through root as uid " + RootLaneCommands.SHELL_UID);
            }
            lane = rootLane;
            serviceUid = rootLane.uid();
            binding = false;
            return rootLane;
        }
    }

    private void dropRootLane() {
        RootLane dropped;
        synchronized (lock) {
            dropped = rootLane;
            rootLane = null;
            if (dropped != null && lane == dropped) {
                lane = null;
                serviceUid = -1;
            }
            lock.notifyAll();
        }
        if (dropped != null) dropped.destroy();
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
            if (binding || (lane != null && lane != rootLane && lane.asBinder().isBinderAlive())) return;
            if (!PrivilegedPolicyStore.isLaneEnabled(ctx)) return;
        }
        PrivilegedChoice choice = PrivilegedPolicyStore.getChoice(ctx);
        if (!choice.allowsShizuku() || !shizukuAlive() || !permissionGranted()) {
            if (choice.allowsRoot() && isRootActiveNow()) ensureRootLane(ctx);
            return;
        }
        LaneConnection c = beginAttempt();
        if (c == null) return;
        try {
            Shizuku.bindUserService(serviceArgs, c);
            Log.i(TAG, "Binding the lane service");
        } catch (Exception e) {
            Log.w(TAG, "bindUserService failed", e);
            synchronized (lock) {
                if (connection == c) {
                    connection = null;
                    binding = false;
                }
                lock.notifyAll();
            }
            mainHandler.removeCallbacks(bindTimeout);
        }
    }

    /**
     * Starts one bind attempt: a fresh connection and the single timer that gives it up. Null when
     * one is already in flight.
     */
    @Nullable
    LaneConnection beginAttempt() {
        LaneConnection c;
        LaneConnection stale;
        synchronized (lock) {
            if (binding) return null;
            binding = true;
            // A binding whose service already died under it is replaced, not stacked.
            stale = connection;
            c = new LaneConnection();
            connection = c;
        }
        if (stale != null) unbindQuietly(stale);
        mainHandler.removeCallbacks(bindTimeout);
        mainHandler.postDelayed(bindTimeout, BIND_TIMEOUT_MS);
        return c;
    }

    boolean isBinding() {
        synchronized (lock) {
            return binding;
        }
    }

    private void unbind() {
        LaneConnection c;
        synchronized (lock) {
            c = connection;
            connection = null;
            lane = null;
            serviceUid = -1;
            binding = false;
            lock.notifyAll();
        }
        mainHandler.removeCallbacks(bindTimeout);
        dropRootLane();
        if (c == null || !shizukuAlive()) return;
        unbindQuietly(c);
        Log.i(TAG, "Unbound the lane service");
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
