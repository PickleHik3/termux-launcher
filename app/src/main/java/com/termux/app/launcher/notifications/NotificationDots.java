package com.termux.app.launcher.notifications;

import android.graphics.Canvas;
import android.graphics.Paint;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Set;

/**
 * Terl's notification dot: one rule for whether an app's icon carries it, and one place that draws
 * it on an icon's corner, so Home, the control bar, the drawer and search read alike. A dot never
 * carries a count under Terl, whatever v1's badge style says.
 */
public final class NotificationDots {

    private NotificationDots() {
    }

    /** Whether {@code packageName}'s icon carries a dot: dots on, and the app has a notification. */
    public static boolean shows(boolean dotsOn, @Nullable String packageName,
                                @NonNull Set<String> activePackages) {
        return dotsOn && packageName != null && activePackages.contains(packageName);
    }

    /** The badge style in force: Terl has dots only, v1 keeps the style the user chose. */
    @NonNull
    public static NotificationBadgeStyle styleFor(boolean terl, @NonNull NotificationBadgeStyle chosen) {
        return terl ? NotificationBadgeStyle.DOT : chosen;
    }

    /** Dot radius for an icon of {@code iconSizePx}, never under 3.5dp. */
    public static float radiusPx(float iconSizePx, float density) {
        return Math.max(3.5f * density, iconSizePx * 0.075f);
    }

    /**
     * The dot's centre on the icon's top-right corner, written into {@code out} (x, y): inside the
     * icon's box by a fifth of its size, so it is never clipped by the cell.
     */
    public static void centre(float iconLeft, float iconTop, float iconRight, float iconBottom,
                              @NonNull float[] out) {
        float size = Math.min(iconRight - iconLeft, iconBottom - iconTop);
        out[0] = iconRight - size * 0.2f;
        out[1] = iconTop + size * 0.2f;
    }

    /** Draws the dot (a rim, then the fill) at {@code cx, cy}; the paints are the caller's, reused. */
    public static void draw(@NonNull Canvas canvas, float cx, float cy, float radius, float rimWidth,
                            int fill, int rim, @NonNull Paint fillPaint, @NonNull Paint rimPaint) {
        rimPaint.setStyle(Paint.Style.STROKE);
        rimPaint.setStrokeWidth(rimWidth);
        rimPaint.setColor(rim);
        fillPaint.setStyle(Paint.Style.FILL);
        fillPaint.setColor(fill);
        canvas.drawCircle(cx, cy, radius + rimWidth * 0.7f, rimPaint);
        canvas.drawCircle(cx, cy, radius, fillPaint);
    }
}
