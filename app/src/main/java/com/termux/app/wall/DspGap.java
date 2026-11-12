package com.termux.app.wall;

import android.os.Build;
import android.os.SystemClock;
import android.util.Log;
import android.view.Choreographer;
import android.view.View;
import android.view.ViewParent;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.lang.ref.WeakReference;

/**
 * TEMPORARY diagnostics for the Display page's bare-wallpaper frames (tag DSPGAP). Revert before
 * merge. Every line starts {@code t=<uptime ms> fr=<last watched vsync ms>}.
 */
public final class DspGap {
    public static final String TAG = "DSPGAP";
    private static final int WATCH_FRAMES = 20;

    /** The display frame's state, one line, for the per-frame watch. */
    public interface Probe {
        @NonNull String state();
    }

    private static volatile long sFrameNs;
    private static int sFramesLeft;
    private static int sFrameIndex;
    private static boolean sPosted;
    @NonNull private static String sWhy = "";
    @Nullable private static WeakReference<View> sTarget;
    @Nullable private static Probe sProbe;

    private DspGap() {}

    public static void setTarget(@Nullable View target, @Nullable Probe probe) {
        sTarget = target == null ? null : new WeakReference<>(target);
        sProbe = probe;
    }

    public static void log(@NonNull String msg) {
        Log.i(TAG, "t=" + SystemClock.uptimeMillis() + " fr=" + (sFrameNs / 1_000_000L) + " " + msg);
    }

    /** {@code msg} with the six frames that called the method calling this. */
    public static void logCaller(@NonNull String msg) {
        log(msg + " at " + caller());
    }

    @NonNull
    public static String caller() {
        StackTraceElement[] stack = new Throwable().getStackTrace();
        StringBuilder out = new StringBuilder();
        // 0 caller(), 1 logCaller(), 2 the logging method, 3.. its callers.
        int start = 2;
        for (int i = start; i < stack.length && i < start + 6; i++) {
            StackTraceElement e = stack[i];
            String cls = e.getClassName();
            int dot = cls.lastIndexOf('.');
            if (out.length() > 0) out.append(" < ");
            out.append(dot >= 0 ? cls.substring(dot + 1) : cls).append('.')
                .append(e.getMethodName()).append(':').append(e.getLineNumber());
        }
        return out.toString();
    }

    public static boolean watching() {
        return sFramesLeft > 0;
    }

    /** Log the next {@value #WATCH_FRAMES} vsyncs, each with the frame's probe and its commit. */
    public static void watch(@NonNull String why) {
        sFramesLeft = WATCH_FRAMES;
        sFrameIndex = 0;
        sWhy = why;
        log("watch-start why=" + why + " {" + probe() + "}");
        if (!sPosted) {
            sPosted = true;
            Choreographer.getInstance().postFrameCallback(FRAME);
        }
    }

    @NonNull
    private static String probe() {
        Probe probe = sProbe;
        if (probe == null) return "none";
        try {
            return probe.state();
        } catch (RuntimeException e) {
            return "probe-failed " + e;
        }
    }

    /** The page and every ancestor that is not plain: layer, alpha, visibility, transform. */
    @NonNull
    public static String ancestry(@NonNull View view) {
        StringBuilder out = new StringBuilder();
        View v = view;
        while (v != null) {
            boolean plain = v.getLayerType() == View.LAYER_TYPE_NONE && v.getAlpha() == 1f
                && v.getVisibility() == View.VISIBLE && v.getTranslationX() == 0f
                && v.getTranslationY() == 0f && v.getScaleX() == 1f && v.getRotationY() == 0f;
            if (!plain || v == view) {
                out.append('[').append(v.getClass().getSimpleName())
                    .append(" L").append(v.getLayerType())
                    .append(" a").append(v.getAlpha())
                    .append(" v").append(v.getVisibility())
                    .append(" tx").append(v.getTranslationX())
                    .append(" s").append(v.getScaleX())
                    .append(" ry").append(v.getRotationY()).append(']');
            }
            ViewParent parent = v.getParent();
            v = parent instanceof View ? (View) parent : null;
        }
        return out.toString();
    }

    /**
     * What ViewRootImpl would hand SurfaceFlinger as the window's transparent region hint if a
     * layout pass ran now (it recomputes it only on layout passes and applies it at once, not
     * with the frame's buffer): the window rect less every drawing view, plus SurfaceView holes.
     */
    @NonNull
    public static String hintEstimate(@NonNull View anyInWindow) {
        View root = anyInWindow.getRootView();
        if (root == null || root.getWidth() <= 0) return "hint=none";
        android.graphics.Region region = new android.graphics.Region(0, 0, root.getWidth(),
            root.getHeight());
        root.gatherTransparentRegion(region);
        return "hint~" + (region.isEmpty() ? "empty" : region.getBounds().toShortString()
            + (region.isComplex() ? "+" : ""));
    }

    private static final Choreographer.FrameCallback FRAME = new Choreographer.FrameCallback() {
        @Override public void doFrame(long frameTimeNanos) {
            sFrameNs = frameTimeNanos;
            final int index = sFrameIndex++;
            long lateUs = SystemClock.uptimeMillis() * 1000L - frameTimeNanos / 1000L;
            log("frame #" + index + " why=" + sWhy + " vsync=" + (frameTimeNanos / 1_000_000L)
                + " late=" + (lateUs / 1000L) + "ms {" + probe() + "}");
            WeakReference<View> ref = sTarget;
            View target = ref == null ? null : ref.get();
            if (Build.VERSION.SDK_INT >= 29 && target != null && target.isAttachedToWindow()) {
                target.getViewTreeObserver().registerFrameCommitCallback(
                    () -> log("commit #" + index));
            }
            if (--sFramesLeft > 0) {
                Choreographer.getInstance().postFrameCallback(this);
            } else {
                sPosted = false;
                log("watch-end");
            }
        }
    };
}
