package com.termux.app.wayland;

import android.hardware.HardwareBuffer;
import android.os.Build;
import android.os.RemoteException;
import android.os.SystemClock;
import android.util.Log;

import com.termux.shared.termux.TermuxConstants;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Feasibility probe (debug only), host side, in the {@code :linuxwindows} process: the Java half
 * of the AHB experiments that {@link ILinuxWindowsDebug} starts. Each runs on a thread of its own,
 * one at a time, and logs under {@code TLWaylandAHB}.
 */
final class AhbProbe {

    static final String LOG_TAG = "TLWaylandAHB";
    /** Where E3 offers its buffer; tlwayland-ahbclient takes the same path. */
    static final String SOCKET_PATH = TermuxConstants.TERMUX_TMP_PREFIX_DIR_PATH + "/tl-ahb.sock";

    private static final int E1_SIZE = 512;
    private static final long E1_STATIC_MS = 2000;
    private static final int E1_FRAMES = 300;
    private static final long E1_PERIOD_NANOS = 1_000_000_000L / 60;

    private static final AtomicBoolean sBusy = new AtomicBoolean();

    private AhbProbe() {}

    private static boolean begin(String what) {
        if (sBusy.compareAndSet(false, true)) return true;
        Log.i(LOG_TAG, what + ": another AHB experiment is running");
        return false;
    }

    /** E1: a CPU-written buffer for the launcher to wrap, then 5 s of rewrites at 60 Hz. */
    static void viewTest(IAhbTestCallback cb) {
        if (cb == null || !begin("E1")) return;
        new Thread(() -> {
            try {
                runViewTest(cb);
            } catch (RemoteException e) {
                Log.i(LOG_TAG, "E1 host: the launcher went away: " + e);
            } catch (RuntimeException e) {
                Log.w(LOG_TAG, "E1 host failed", e);
            } finally {
                sBusy.set(false);
            }
        }, "tl-ahb-e1").start();
    }

    private static void runViewTest(IAhbTestCallback cb) throws RemoteException {
        Log.i(LOG_TAG, "E1 host: API " + Build.VERSION.SDK_INT + ", pid " + android.os.Process.myPid());
        HardwareBuffer buffer;
        try {
            buffer = HardwareBuffer.create(E1_SIZE, E1_SIZE, HardwareBuffer.RGBA_8888, 1,
                HardwareBuffer.USAGE_GPU_SAMPLED_IMAGE | HardwareBuffer.USAGE_CPU_WRITE_OFTEN);
        } catch (RuntimeException e) {
            Log.i(LOG_TAG, "E1 host: HardwareBuffer.create failed: " + e);
            cb.onDone("e1", "allocate failed: " + e);
            return;
        }
        try {
            if (!WaylandHost.nativeAhbFill(buffer, 0)) {
                cb.onDone("e1", "the first fill failed");
                return;
            }
            Log.i(LOG_TAG, "E1 host: buffer " + buffer.getWidth() + "x" + buffer.getHeight() + " usage 0x"
                + Long.toHexString(buffer.getUsage()) + " filled (frame 0), sending");
            cb.onBuffer(buffer, "e1");
            SystemClock.sleep(E1_STATIC_MS);
            long start = SystemClock.elapsedRealtimeNanos();
            long fillNanos = 0;
            int filled = 0;
            for (int frame = 1; frame <= E1_FRAMES; frame++) {
                long due = start + frame * E1_PERIOD_NANOS;
                long wait = due - SystemClock.elapsedRealtimeNanos();
                if (wait > 0) SystemClock.sleep(Math.max(1, wait / 1_000_000));
                long t = SystemClock.elapsedRealtimeNanos();
                boolean ok = WaylandHost.nativeAhbFill(buffer, frame);
                fillNanos += SystemClock.elapsedRealtimeNanos() - t;
                if (!ok) break;
                filled++;
                cb.onFrame(frame);
            }
            long elapsedMs = (SystemClock.elapsedRealtimeNanos() - start) / 1_000_000;
            String summary = "host rewrote " + filled + " of " + E1_FRAMES + " frames in " + elapsedMs + " ms, mean fill "
                + (filled == 0 ? 0 : fillNanos / filled / 1000) + " us";
            Log.i(LOG_TAG, "E1 host: " + summary);
            cb.onDone("e1", summary);
        } finally {
            // The launcher holds its own reference through the parcel.
            buffer.close();
        }
    }

    /** E2: the native probe, which runs on a thread of its own. */
    static void layoutProbe() {
        WaylandHost.nativeAhbLayoutProbe();
    }

    /** E3: serve one buffer to tlwayland-ahbclient, then hand it to the launcher. */
    static void clientTest(int variant, int modifier, IAhbTestCallback cb) {
        if (!begin("E3")) return;
        new Thread(() -> {
            try {
                Log.i(LOG_TAG, "E3 host: variant " + variant + " modifier " + modifier + " socket " + SOCKET_PATH);
                HardwareBuffer buffer = WaylandHost.nativeAhbServeClient(variant, modifier, SOCKET_PATH);
                if (cb == null) {
                    if (buffer != null) buffer.close();
                    return;
                }
                if (buffer == null) {
                    cb.onDone("e3", "no buffer to show (see TLWaylandAHB E3 lines)");
                    return;
                }
                try {
                    cb.onBuffer(buffer, "e3");
                    cb.onDone("e3", "buffer handed over");
                } finally {
                    buffer.close();
                }
            } catch (RemoteException e) {
                Log.i(LOG_TAG, "E3 host: the launcher went away: " + e);
            } catch (RuntimeException e) {
                Log.w(LOG_TAG, "E3 host failed", e);
            } finally {
                sBusy.set(false);
            }
        }, "tl-ahb-e3").start();
    }
}
