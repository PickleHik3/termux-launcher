package com.termux.app.launcher.notifications;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.ColorUtils;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * A dock cell wrapper that draws the unread dot for the package(s) its child stands for.
 *
 * <p>The frame owns only the dot: whether there is one to draw, and its colours, come from the host
 * through {@link Style} so a live restyle or a badge-store update is picked up on the next draw
 * without re-creating the cell.
 */
public final class NotificationBadgeFrame extends FrameLayout {

    /** The host's live badge state and look, read at draw time. */
    public interface Style {
        /** Whether the launcher is showing notification dots at all. */
        boolean badgesEnabled();

        /** Packages with an active badgeable notification right now. */
        @NonNull
        Set<String> activeBadgePackages();

        /** Dot fill colour. */
        int badgeFillColor();

        /** Dot rim colour, drawn against the dock material. */
        int badgeStrokeColor();

        /** Rendered dock icon size in px, which positions the dot on the icon's corner. */
        int iconSizePx();

        /** Display density, for the dot's dp-based geometry. */
        float density();

        /** Dot, or the number of notifications; the dot until the host says otherwise. */
        @NonNull
        default NotificationBadgeStyle badgeStyle() {
            return NotificationBadgeStyle.DOT;
        }

        /** Active notifications across {@code packages}, asked only while the style is a count. */
        default int badgeCount(@NonNull Set<String> packages) {
            return 0;
        }
    }

    private final Paint badgePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint badgeStrokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint badgeTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF badgeRect = new RectF();
    @NonNull private final Style style;
    @NonNull private Set<String> badgePackages = Collections.emptySet();

    public NotificationBadgeFrame(@NonNull Context context, @NonNull Style style) {
        super(context);
        this.style = style;
        setWillNotDraw(false);
        setClipChildren(false);
        setClipToPadding(false);
        badgePaint.setStyle(Paint.Style.FILL);
        badgeStrokePaint.setStyle(Paint.Style.STROKE);
        badgeStrokePaint.setStrokeWidth(dp(1.6f));
        badgeTextPaint.setTextAlign(Paint.Align.CENTER);
        badgeTextPaint.setTypeface(Typeface.DEFAULT_BOLD);
    }

    /** The packages this cell stands for: one app, or every app inside a folder. */
    public void setBadgePackages(@Nullable Set<String> packages) {
        badgePackages = packages == null || packages.isEmpty()
            ? Collections.emptySet()
            : new HashSet<>(packages);
        invalidate();
    }

    /**
     * Whether a cell standing for {@code cellPackages} should show a dot.
     *
     * <p>Pure so the rule survives without a live listener service: badges off, a cell that stands
     * for nothing, or no active notification at all all mean no dot.
     */
    public static boolean hasActiveBadge(boolean badgesEnabled,
                                         @NonNull Set<String> cellPackages,
                                         @NonNull Set<String> activePackages) {
        if (!badgesEnabled || cellPackages.isEmpty() || activePackages.isEmpty()) {
            return false;
        }
        for (String packageName : cellPackages) {
            if (activePackages.contains(packageName)) {
                return true;
            }
        }
        return false;
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        super.dispatchDraw(canvas);
        if (!hasActiveBadge(style.badgesEnabled(), badgePackages, style.activeBadgePackages())) {
            return;
        }
        if (getWidth() <= 0 || getHeight() <= 0) {
            return;
        }

        int iconSizePx = style.iconSizePx();
        float radius = Math.max(dp(3.5f), Math.min(getWidth(), getHeight()) * 0.075f);
        float cx = (getWidth() * 0.5f) + (iconSizePx * 0.30f);
        float cy = (getHeight() * 0.5f) - (iconSizePx * 0.30f);
        cx = clampFloat(cx, radius + dp(1f), getWidth() - radius - dp(1f));
        cy = clampFloat(cy, radius + dp(1f), getHeight() - radius - dp(1f));
        badgePaint.setColor(style.badgeFillColor());
        badgeStrokePaint.setColor(style.badgeStrokeColor());
        if (style.badgeStyle() == NotificationBadgeStyle.COUNT) {
            String label = NotificationBadgeStyle.countLabel(style.badgeCount(badgePackages));
            if (!label.isEmpty()) {
                drawCount(canvas, label, cx, cy, iconSizePx);
                return;
            }
        }
        canvas.drawCircle(cx, cy, radius + dp(1.1f), badgeStrokePaint);
        canvas.drawCircle(cx, cy, radius, badgePaint);
    }

    /**
     * The count as a pill on the icon's corner, grown from the dot's own centre: a circle for one
     * digit, wider for more, its text sized from the icon (not the font scale) so it never outgrows
     * the cell.
     */
    private void drawCount(@NonNull Canvas canvas, @NonNull String label, float cx, float cy,
                           int iconSizePx) {
        float radius = Math.max(dp(7f), iconSizePx * 0.16f);
        badgeTextPaint.setTextSize(radius * 1.15f);
        float width = Math.max(radius * 2f, badgeTextPaint.measureText(label) + radius * 0.9f);
        float left = clampFloat(cx - width * 0.5f, dp(1f), getWidth() - width - dp(1f));
        float top = clampFloat(cy - radius, dp(1f), getHeight() - radius * 2f - dp(1f));
        badgeRect.set(left, top, left + width, top + radius * 2f);
        canvas.drawRoundRect(badgeRect, radius, radius, badgeStrokePaint);
        canvas.drawRoundRect(badgeRect, radius, radius, badgePaint);
        badgeTextPaint.setColor(ColorUtils.calculateLuminance(badgePaint.getColor()) > 0.5
            ? Color.BLACK : Color.WHITE);
        Paint.FontMetrics metrics = badgeTextPaint.getFontMetrics();
        canvas.drawText(label, badgeRect.centerX(),
            badgeRect.centerY() - (metrics.ascent + metrics.descent) * 0.5f, badgeTextPaint);
    }

    private float dp(float value) {
        return value * style.density();
    }

    private static float clampFloat(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}
