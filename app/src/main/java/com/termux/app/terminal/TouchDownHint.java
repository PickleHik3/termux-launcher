package com.termux.app.terminal;

import android.app.Activity;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.PerformanceHintManager;
import android.os.Process;
import android.os.SystemClock;
import android.os.Trace;
import android.view.FrameMetrics;
import android.view.Window;

import com.termux.terminal.DebugSwitch;
import com.termux.terminal.TouchDownListener;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

/**
 * Debug experiment: tell the system at touch-down that CPU work is coming, so the first frame of a
 * typing burst does not meet cold clocks (see the "Buffer Stuffing analysis" in
 * project-docs/active/input-latency-study.md).
 *
 * <p>{@code adb shell setprop debug.tl.touch_hint 1} turns it on; {@code 0} or unset does nothing.
 * Mode 1 holds one ADPF hint session for the main thread and the RenderThread, sends
 * {@code CPU_LOAD_UP} on every keyboard or terminal touch-down, and reports each frame's measured
 * total duration while frames keep arriving (stops 500 ms after the last). API 33+ only.
 */
public final class TouchDownHint implements TouchDownListener.Listener {

    static final String PROP = "debug.tl.touch_hint";
    private static final long FALLBACK_PERIOD_NS = 11_111_111L;
    private static final long ACTIVE_MS = 500L;
    /** {@code Session.CPU_LOAD_UP}; sendHint and its constants are @hide in the SDK. */
    private static final Integer CPU_LOAD_UP = 0;

    private static final DebugSwitch ON =
        new DebugSwitch(PROP, false, DebugSwitch.SYSTEM_PROPERTIES);

    private static TouchDownHint sInstalled;

    private final Activity mActivity;
    private PerformanceHintManager.Session mSession;
    private boolean mSessionFailed;
    private Window.OnFrameMetricsAvailableListener mMetrics;
    private volatile long mActiveUntilMs;
    private java.lang.reflect.Method mSendHint;
    private boolean mSendHintFailed;
    private final Object[] mLoadUpArgs = {CPU_LOAD_UP};

    private TouchDownHint(Activity activity) {
        mActivity = activity;
    }

    /** Install from onStart; a no-op below API 33. */
    public static void attach(Activity activity) {
        if (Build.VERSION.SDK_INT < 33) return;
        detach(activity);
        sInstalled = new TouchDownHint(activity);
        TouchDownListener.set(sInstalled);
    }

    /** Remove from onStop: drops the slot, the metrics listener and closes the session. */
    public static void detach(Activity activity) {
        TouchDownHint h = sInstalled;
        if (h == null) return;
        sInstalled = null;
        TouchDownListener.clear(h);
        h.release();
    }

    @Override
    public void onTouchDown() {
        long now = SystemClock.uptimeMillis();
        if (!ON.isOn(now)) return;
        Trace.beginSection("TouchHint.loadUp");
        try {
            PerformanceHintManager.Session s = session();
            if (s == null) return;
            mActiveUntilMs = now + ACTIVE_MS;
            sendLoadUp(s);
            ensureMetrics();
        } catch (Throwable t) {
            mSessionFailed = true;
        } finally {
            Trace.endSection();
        }
    }

    /** Reflection, because {@code Session#sendHint} is hidden; the argument array is reused. */
    private void sendLoadUp(PerformanceHintManager.Session s) throws Exception {
        if (mSendHintFailed) return;
        try {
            if (mSendHint == null) {
                mSendHint = PerformanceHintManager.Session.class.getMethod("sendHint", int.class);
            }
            mSendHint.invoke(s, mLoadUpArgs);
        } catch (ReflectiveOperationException | RuntimeException e) {
            mSendHintFailed = true;
        }
    }

    private PerformanceHintManager.Session session() {
        if (mSession != null) return mSession;
        if (mSessionFailed) return null;
        PerformanceHintManager mgr = mActivity.getSystemService(PerformanceHintManager.class);
        if (mgr == null) {
            mSessionFailed = true;
            return null;
        }
        int render = findRenderThreadTid();
        int[] tids = render > 0
            ? new int[] {Process.myTid(), render} : new int[] {Process.myTid()};
        mSession = mgr.createHintSession(tids, framePeriodNs());
        if (mSession == null) mSessionFailed = true;
        return mSession;
    }

    @SuppressWarnings("deprecation")
    private long framePeriodNs() {
        try {
            float hz = mActivity.getWindowManager().getDefaultDisplay().getRefreshRate();
            if (hz >= 30f) return (long) (1_000_000_000d / hz);
        } catch (Throwable ignored) {
            // fall through to the 90 Hz period
        }
        return FALLBACK_PERIOD_NS;
    }

    /** Reports each frame's total duration while a touch was seen in the last {@link #ACTIVE_MS}. */
    private void ensureMetrics() {
        if (mMetrics != null) return;
        mMetrics = (window, metrics, dropped) -> {
            long now = SystemClock.uptimeMillis();
            PerformanceHintManager.Session s = mSession;
            if (s == null || now > mActiveUntilMs) return;
            long total = metrics.getMetric(FrameMetrics.TOTAL_DURATION);
            if (total > 0L) s.reportActualWorkDuration(total);
            mActiveUntilMs = now + ACTIVE_MS;
        };
        mActivity.getWindow().addOnFrameMetricsAvailableListener(mMetrics,
            new Handler(Looper.getMainLooper()));
    }

    private void release() {
        if (mMetrics != null) {
            try {
                mActivity.getWindow().removeOnFrameMetricsAvailableListener(mMetrics);
            } catch (Throwable ignored) {
                // window already gone
            }
            mMetrics = null;
        }
        if (mSession != null) {
            try {
                mSession.close();
            } catch (Throwable ignored) {
                // nothing left to release
            }
            mSession = null;
        }
        mSessionFailed = false;
    }

    private static int findRenderThreadTid() {
        File[] tasks = new File("/proc/self/task").listFiles();
        if (tasks == null) return -1;
        String[] dirs = new String[tasks.length];
        String[] names = new String[tasks.length];
        for (int i = 0; i < tasks.length; i++) {
            dirs[i] = tasks[i].getName();
            try {
                names[i] = new String(Files.readAllBytes(new File(tasks[i], "comm").toPath()));
            } catch (IOException e) {
                names[i] = null;
            }
        }
        return pickTid(dirs, names, "RenderThread");
    }

    /** The tid whose comm (trailing newline ignored) equals {@code wanted}, or -1. */
    static int pickTid(String[] taskDirs, String[] comms, String wanted) {
        for (int i = 0; i < taskDirs.length; i++) {
            if (comms[i] == null || !comms[i].trim().equals(wanted)) continue;
            try {
                return Integer.parseInt(taskDirs[i]);
            } catch (NumberFormatException e) {
                // not a tid directory; keep looking
            }
        }
        return -1;
    }
}
