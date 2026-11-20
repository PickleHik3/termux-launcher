package com.termux.app.chrome;

import android.graphics.Canvas;
import android.graphics.Path;
import android.graphics.Rect;
import android.view.View;
import android.view.ViewParent;

import androidx.annotation.NonNull;

import java.util.HashMap;
import java.util.Map;

/**
 * DIAG(sw-mask): which path clip or path fill draws, at what size, so a pane-size software coverage
 * mask hwui rasterises and uploads mid-swipe can be named. Logged to the motion log once per change
 * of (site, clip size, path size) per site; reverted before merge.
 */
public final class DiagSwMask {

    private static final Map<String, Long> sLast = new HashMap<>();
    private static final Rect sClip = new Rect();
    private static final android.graphics.RectF sBounds = new android.graphics.RectF();

    private DiagSwMask() {}

    /** Main thread, from a draw: notes {@code path} about to be clipped to or filled at {@code site}. */
    public static void note(@NonNull String site, @NonNull Canvas canvas, @NonNull Path path) {
        if (!MotionLog.enabled) return;
        canvas.getClipBounds(sClip);
        path.computeBounds(sBounds, false);
        long key = ((long) sClip.width() << 48) ^ ((long) sClip.height() << 32)
            ^ ((long) Math.round(sBounds.width()) << 16) ^ Math.round(sBounds.height())
            ^ (canvas.isHardwareAccelerated() ? 1L << 62 : 0L);
        Long last = sLast.get(site);
        if (last != null && last == key) return;
        sLast.put(site, key);
        MotionLog.log("sw-mask", "site=" + site + " clip=" + sClip.width() + "x" + sClip.height()
            + " path=" + Math.round(sBounds.width()) + "x" + Math.round(sBounds.height())
            + " convex=" + path.isConvex() + " rect=" + path.isRect(null)
            + " fill=" + path.getFillType() + " hw=" + canvas.isHardwareAccelerated()
            + " layer=" + canvas.getSaveCount());
    }

    /**
     * DIAG(sw-mask): what could stop Ganesh clipping {@code view}'s band round rect analytically.
     * Walks the view up to the root (or the first view drawn into its own layer) and counts the
     * round-rect outline clips stacked above the band (Ganesh fits at most four analytic clip
     * elements; a fifth goes to a software mask), any non-affine matrix (an RRect under
     * perspective stays a path), any rotation, and the band's own rect and radius. Called from each
     * band clip in a draw; logged once per change, the key built without allocating.
     */
    public static void noteChain(@NonNull String site, @NonNull View view, @NonNull Path band,
                                 float radius) {
        if (!MotionLog.enabled) return;
        band.computeBounds(sBounds, false);
        float l = sBounds.left, t = sBounds.top, r = sBounds.right, b = sBounds.bottom;
        int outlines = 0, layers = 0, rotated = 0, nonAffine = 0, scaled = 0, depth = 0;
        int layerDepth = -1;
        View v = view;
        while (v != null) {
            if (v.getClipToOutline()) outlines++;
            if (v.getLayerType() != View.LAYER_TYPE_NONE) {
                layers++;
                if (layerDepth < 0) layerDepth = depth;
            }
            if (v.getRotation() != 0f || v.getRotationX() != 0f || v.getRotationY() != 0f) rotated++;
            if (!v.getMatrix().isAffine()) nonAffine++;
            if (v.getScaleX() != 1f || v.getScaleY() != 1f) scaled++;
            depth++;
            ViewParent p = v.getParent();
            v = p instanceof View ? (View) p : null;
        }
        long key = outlines | ((long) layers << 6) | ((long) rotated << 12) | ((long) nonAffine << 18)
            | ((long) scaled << 24) | ((long) (layerDepth + 1) << 30)
            ^ ((long) Float.floatToIntBits(radius) << 32) ^ Float.floatToIntBits(r - l);
        String chainSite = site + ".chain";
        Long last = sLast.get(chainSite);
        if (last != null && last == key) return;
        sLast.put(chainSite, key);
        StackTraceElement[] st = new Throwable().getStackTrace();
        StringBuilder callers = new StringBuilder();
        for (int i = 1; i < Math.min(st.length, 7); i++)
            callers.append(i == 1 ? "" : "<").append(st[i].getMethodName());
        MotionLog.log("sw-mask", "site=" + chainSite + " band=" + l + "," + t + "," + r + "," + b
            + " r=" + radius + " outlinesAbove=" + outlines + " layers=" + layers
            + " layerDepth=" + layerDepth + " depth=" + depth + " rotated=" + rotated
            + " nonAffine=" + nonAffine + " scaled=" + scaled
            + " clipToOutline=" + view.getClipToOutline() + " callers=" + callers);
    }
}
