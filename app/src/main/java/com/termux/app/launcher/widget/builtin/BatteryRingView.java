package com.termux.app.launcher.widget.builtin;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;

/**
 * The 1×1 Battery ring: a disc filled clockwise from twelve o'clock to the level, the rest in the
 * high container, with the inner disc punched out in the ring colour so the bolt and the numeral
 * laid over it read cleanly.
 */
public final class BatteryRingView extends View {
    /** The inner disc's diameter as a share of the ring's: 56 of 68 in the design. */
    private static final float INNER = 56f / 68f;

    @NonNull private final BuiltinWidgetStyle style;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF oval = new RectF();
    private float fraction;
    @ColorInt private int fill;

    public BatteryRingView(@NonNull Context context, @NonNull BuiltinWidgetStyle style) {
        super(context);
        this.style = style;
        fill = style.primary;
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    public void set(float fraction, @ColorInt int fill) {
        this.fraction = Math.max(0f, Math.min(1f, fraction));
        this.fill = fill;
        invalidate();
    }

    @Override protected void onDraw(@NonNull Canvas canvas) {
        float size = Math.min(getWidth(), getHeight());
        if (size <= 0) return;
        float cx = getWidth() / 2f, cy = getHeight() / 2f, r = size / 2f;
        oval.set(cx - r, cy - r, cx + r, cy + r);
        paint.setColor(style.containerHigh);
        canvas.drawOval(oval, paint);
        if (fraction > 0f) {
            paint.setColor(fill);
            canvas.drawArc(oval, -90f, 360f * fraction, true, paint);
        }
        paint.setColor(style.ringInner);
        canvas.drawCircle(cx, cy, r * INNER, paint);
    }
}
