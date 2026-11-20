package com.termux.app.chrome;

import android.graphics.Canvas;
import android.graphics.Path;
import android.graphics.Rect;

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
}
