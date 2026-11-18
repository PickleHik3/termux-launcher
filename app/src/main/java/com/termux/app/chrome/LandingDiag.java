package com.termux.app.chrome;

import android.graphics.drawable.Drawable;
import android.os.SystemClock;
import android.view.View;
import android.os.Handler;
import android.os.Looper;
import android.view.Window;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.SuggestionBarView;

/**
 * TEMPORARY diagnostics (tags {@code diag-*}) for the post-landing settle on pong; reverted before
 * merge. A per-frame watcher armed at a landing logs the apps row's and the keyboard glass's state
 * when it changes, and writers log their callers while it is armed.
 */
public final class LandingDiag {
    private static final int MAX_FRAMES = 90;
    private static final long MAX_MS = 1500L;

    private static boolean sArmed;
    private static long sLandedAt;
    private static int sFrame;
    private static String sLast = "";
    private static String sLastKb = "";
    private static Window sWindow;
    private static Window.OnFrameMetricsAvailableListener sListener;

    private LandingDiag() {}

    public static boolean armed() {
        return sArmed && MotionLog.enabled;
    }

    /** A writer's caller trace, only while the watcher is armed. */
    public static void write(@NonNull String what) {
        if (!armed()) return;
        MotionLog.log("diag-write", what + " +" + (SystemClock.uptimeMillis() - sLandedAt)
            + "ms callers=" + MotionLog.callers(6));
    }

    /** A writer's caller trace with the view-dispatch frames skipped, while armed. */
    public static void writeDeep(@NonNull String what) {
        if (!armed()) return;
        MotionLog.log("diag-write", what + " +" + (SystemClock.uptimeMillis() - sLandedAt)
            + "ms callers=" + deepCallers(10));
    }

    /** An attach or detach of {@code v}, armed or not (motion log on): its ancestors and the add/remove site. */
    public static void attach(@NonNull String what, @NonNull View v) {
        if (!MotionLog.enabled) return;
        StringBuilder chain = new StringBuilder();
        android.view.ViewParent p = v.getParent();
        for (int i = 0; p instanceof View && i < 8; i++, p = p.getParent()) {
            View pv = (View) p;
            if (chain.length() > 0) chain.append('<');
            chain.append(pv.getClass().getSimpleName());
            if (pv.getId() != View.NO_ID) {
                try { chain.append('/').append(pv.getResources().getResourceEntryName(pv.getId())); }
                catch (RuntimeException ignored) { chain.append('/').append(pv.getId()); }
            }
        }
        MotionLog.log("diag-attach", what + (sArmed ? " +" + (SystemClock.uptimeMillis() - sLandedAt) + "ms" : "")
            + " parents=" + chain + " callers=" + deepCallers(12));
    }

    private static String deepCallers(int count) {
        StackTraceElement[] stack = new Throwable().getStackTrace();
        StringBuilder out = new StringBuilder();
        int kept = 0;
        for (int i = 2; i < stack.length && kept < count; i++) {
            String cls = stack[i].getClassName();
            String m = stack[i].getMethodName();
            if (m.startsWith("dispatch") || cls.startsWith("com.termux.app.chrome.LandingDiag")) continue;
            if (out.length() > 0) out.append('>');
            out.append(cls.substring(cls.lastIndexOf('.') + 1)).append('.').append(m).append(':')
                .append(stack[i].getLineNumber());
            kept++;
        }
        return out.toString();
    }

    /** One timed step of the settle: {@code step=<name> ms=<dur>}. */
    public static void step(@NonNull String name, long startMs) {
        if (!MotionLog.enabled) return;
        MotionLog.log("diag-settle", "step=" + name + " ms=" + (SystemClock.uptimeMillis() - startMs));
    }

    public static void arm(@NonNull Window window, @Nullable SuggestionBarView bar,
                           @Nullable View appsHost, @Nullable View kbContainer, @Nullable View kbHost) {
        if (!MotionLog.enabled) return;
        disarm();
        sArmed = true;
        sLandedAt = SystemClock.uptimeMillis();
        sFrame = 0;
        sLast = "";
        sLastKb = "";
        sWindow = window;
        final StringBuilder sb = new StringBuilder(256);
        sListener = (w, metrics, dropped) -> {
            if (!sArmed) return;
            long ms = SystemClock.uptimeMillis() - sLandedAt;
            int frame = sFrame++;
            sb.setLength(0);
            if (bar != null) bar.diagState(sb);
            if (appsHost != null) {
                sb.append(" host=").append(appsHost.getVisibility()).append('/')
                    .append(appsHost.getAlpha()).append('/').append(appsHost.getTranslationY());
            }
            String now = sb.toString();
            if (!now.equals(sLast)) {
                sLast = now;
                MotionLog.log("diag-frame", "f=" + frame + " ms=" + ms + " apps " + now);
            }
            sb.setLength(0);
            kb(sb, "cont", kbContainer);
            kb(sb, "host", kbHost);
            String kbNow = sb.toString();
            if (!kbNow.equals(sLastKb)) {
                sLastKb = kbNow;
                MotionLog.log("diag-frame", "f=" + frame + " ms=" + ms + " kb " + kbNow);
            }
            if (frame >= MAX_FRAMES || ms >= MAX_MS) disarm();
        };
        window.addOnFrameMetricsAvailableListener(sListener, new Handler(Looper.getMainLooper()));
    }

    private static void kb(StringBuilder sb, String name, @Nullable View v) {
        sb.append(name).append('=');
        if (v == null) {
            sb.append("null ");
            return;
        }
        Drawable bg = v.getBackground();
        sb.append("vis").append(v.getVisibility()).append(" a").append(v.getAlpha()).append(" bg=");
        if (bg == null) sb.append("null");
        else sb.append(bg.getClass().getSimpleName()).append('#')
            .append(Integer.toHexString(System.identityHashCode(bg))).append(" alpha")
            .append(bg.getAlpha());
        sb.append(' ');
    }

    private static void disarm() {
        sArmed = false;
        if (sWindow != null && sListener != null) sWindow.removeOnFrameMetricsAvailableListener(sListener);
        sWindow = null;
        sListener = null;
    }
}
