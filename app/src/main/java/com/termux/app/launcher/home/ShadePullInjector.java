package com.termux.app.launcher.home;

import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.util.Log;
import android.view.InputDevice;
import android.view.MotionEvent;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.privileged.lane.IPrivilegedLane;

/**
 * Pulls the system's notification shade down under the finger: while a swipe down on Home is
 * under way, a drag from the status bar is injected through the privileged lane, one move per
 * finger move ({@link ShadePullPolicy} says when and where).
 *
 * <p>The touch handler calls {@link #begin}, {@link #move}, then {@link #end} or {@link #cancel}
 * on the main thread; nothing there allocates or waits. The binder calls run on a thread of their
 * own, which keeps only the latest finger position when it falls behind. If any injection fails,
 * the drag is cancelled and the let-go swipe opens the shade the old way ({@code fallback}), so the
 * gesture is never dead.
 */
public final class ShadePullInjector {

    private static final String TAG = "ShadePullInjector";
    private static final int OP_DOWN = 1;
    private static final int OP_MOVE = 2;
    private static final int OP_UP = 3;
    private static final int OP_CANCEL = 4;
    /** Far more than a gesture queues (down, one coalesced move, end) while a call is in flight. */
    private static final int CAPACITY = 32;

    /** One queued finger event; the records are reused. */
    private static final class Op {
        int kind;
        float rawX;
        float rawY;
        long time;
        int releaseWay;
        // DOWN only: the session's constants.
        @Nullable IPrivilegedLane lane;
        int displayId;
        float pressY;
    }

    private final Handler main = new Handler(Looper.getMainLooper());
    private final Runnable fallback;
    private final Object lock = new Object();
    private final Op[] ring = new Op[CAPACITY];
    private int head;
    private int count;
    private boolean drainPosted;
    @Nullable private Handler worker;
    /** Main thread: a pull is under way (between begin and end or cancel). */
    private boolean active;

    // Worker thread only: the session being injected.
    private final Op current = new Op();
    @Nullable private IPrivilegedLane lane;
    private int displayId;
    private float pressY;
    private float startRawY;
    private long downTime;
    private boolean injectedDown;
    private boolean failed;
    private final MotionEvent.PointerProperties[] properties = { new MotionEvent.PointerProperties() };
    private final MotionEvent.PointerCoords[] coords = { new MotionEvent.PointerCoords() };

    private final Runnable drain = this::drain;

    /** {@code fallback} opens the shade the old way; it is posted to the main thread. */
    public ShadePullInjector(@NonNull Runnable fallback) {
        this.fallback = fallback;
        for (int i = 0; i < CAPACITY; i++) ring[i] = new Op();
        properties[0].id = 0;
        properties[0].toolType = MotionEvent.TOOL_TYPE_FINGER;
        coords[0].pressure = 1f;
        coords[0].size = 1f;
    }

    /** Whether a pull is under way. Main thread. */
    public boolean isActive() {
        return active;
    }

    /**
     * Starts a pull for the finger claimed at ({@code rawX}, {@code rawY}) at {@code time}
     * (uptime ms) on {@code displayId}; a pull still under way is cancelled first. Main thread.
     */
    public void begin(@NonNull IPrivilegedLane lane, float rawX, float rawY, long time,
                      int displayId, int statusBarPx) {
        if (active) cancel();
        if (worker == null) {
            HandlerThread thread = new HandlerThread(TAG);
            thread.start();
            worker = new Handler(thread.getLooper());
        }
        active = true;
        synchronized (lock) {
            Op op = enqueueLocked(OP_DOWN, rawX, rawY, time);
            if (op != null) {
                op.lane = lane;
                op.displayId = displayId;
                op.pressY = ShadePullPolicy.pressY(statusBarPx);
            }
            postDrainLocked();
        }
    }

    /** The finger moved. Main thread. */
    public void move(float rawX, float rawY, long time) {
        if (!active) return;
        synchronized (lock) {
            if (count > 0) {
                Op tail = ring[(head + count - 1) % CAPACITY];
                if (tail.kind == OP_MOVE) {
                    tail.rawX = rawX;
                    tail.rawY = rawY;
                    tail.time = time;
                    return;
                }
            }
            enqueueLocked(OP_MOVE, rawX, rawY, time);
            postDrainLocked();
        }
    }

    /**
     * The finger let go; {@code releaseWay} is the release's reading (positive down,
     * {@code HomeSwipePolicy.release}), which decides the fallback. Main thread.
     */
    public void end(float rawX, float rawY, long time, int releaseWay) {
        if (!active) return;
        active = false;
        synchronized (lock) {
            Op op = enqueueLocked(OP_UP, rawX, rawY, time);
            if (op != null) op.releaseWay = releaseWay;
            postDrainLocked();
        }
    }

    /** The finger's stream was cancelled: the shade goes back, nothing opens. Main thread. */
    public void cancel() {
        if (!active) return;
        active = false;
        synchronized (lock) {
            enqueueLocked(OP_CANCEL, 0f, 0f, android.os.SystemClock.uptimeMillis());
            postDrainLocked();
        }
    }

    @Nullable
    private Op enqueueLocked(int kind, float rawX, float rawY, long time) {
        if (count == CAPACITY) {
            Log.w(TAG, "Pull queue full; dropping an event");
            return null;
        }
        Op op = ring[(head + count) % CAPACITY];
        count++;
        op.kind = kind;
        op.rawX = rawX;
        op.rawY = rawY;
        op.time = time;
        op.releaseWay = 0;
        op.lane = null;
        return op;
    }

    private void postDrainLocked() {
        if (drainPosted || worker == null) return;
        drainPosted = true;
        worker.post(drain);
    }

    /** Worker thread: injects every queued event in order. */
    private void drain() {
        while (true) {
            synchronized (lock) {
                if (count == 0) {
                    drainPosted = false;
                    return;
                }
                Op op = ring[head];
                current.kind = op.kind;
                current.rawX = op.rawX;
                current.rawY = op.rawY;
                current.time = op.time;
                current.releaseWay = op.releaseWay;
                current.lane = op.lane;
                current.displayId = op.displayId;
                current.pressY = op.pressY;
                op.lane = null;
                head = (head + 1) % CAPACITY;
                count--;
            }
            run(current);
            current.lane = null;
        }
    }

    private void run(@NonNull Op op) {
        switch (op.kind) {
            case OP_DOWN:
                lane = op.lane;
                displayId = op.displayId;
                pressY = op.pressY;
                startRawY = op.rawY;
                downTime = op.time;
                failed = false;
                injectedDown = inject(MotionEvent.ACTION_DOWN, op.rawX, op.rawY, op.time);
                if (!injectedDown) failed = true;
                break;
            case OP_MOVE:
                if (failed) break;
                if (!inject(MotionEvent.ACTION_MOVE, op.rawX, op.rawY, op.time)) failAndCancel(op.time);
                break;
            case OP_UP:
                if (!failed && !inject(MotionEvent.ACTION_UP, op.rawX, op.rawY, op.time)) {
                    failAndCancel(op.time);
                }
                if (ShadePullPolicy.fallsBackOnRelease(failed, op.releaseWay)) main.post(fallback);
                lane = null;
                break;
            case OP_CANCEL:
                if (!failed && injectedDown) inject(MotionEvent.ACTION_CANCEL, 0f, startRawY, op.time);
                lane = null;
                break;
            default:
                break;
        }
    }

    /** A step failed: the shade is let go of (best effort), and the release falls back. */
    private void failAndCancel(long time) {
        failed = true;
        if (injectedDown) inject(MotionEvent.ACTION_CANCEL, 0f, startRawY, time);
    }

    /** One injected event at the finger's mapped place; true when it reached a window. */
    private boolean inject(int action, float rawX, float rawY, long time) {
        IPrivilegedLane target = lane;
        if (target == null) return false;
        coords[0].x = ShadePullPolicy.dragX(rawX);
        coords[0].y = ShadePullPolicy.dragY(pressY, startRawY, rawY);
        MotionEvent event = MotionEvent.obtain(downTime, Math.max(downTime, time), action, 1,
            properties, coords, 0, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_TOUCHSCREEN, 0);
        try {
            return target.injectMotion(event, displayId);
        } catch (Exception e) {
            Log.w(TAG, "Injecting the shade's drag failed", e);
            return false;
        } finally {
            event.recycle();
        }
    }
}
