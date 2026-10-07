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

    /** The clear space kept between the ring and the card's edge when the card is small. */
    private static final int MARGIN_DP = 8;

    @NonNull private final BuiltinWidgetStyle style;
    private final int desiredPx;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF oval = new RectF();
    private float fraction;
    @ColorInt private int fill;

    /** A ring that wants {@code desiredDp} and settles for what its room leaves under the margin. */
    public BatteryRingView(@NonNull Context context, @NonNull BuiltinWidgetStyle style,
                           int desiredDp) {
        super(context);
        this.style = style;
        this.desiredPx = style.dp(desiredDp);
        fill = style.primary;
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    public void set(float fraction, @ColorInt int fill) {
        this.fraction = Math.max(0f, Math.min(1f, fraction));
        this.fill = fill;
        invalidate();
    }

    @Override protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int room = Math.min(
            MeasureSpec.getMode(widthMeasureSpec) == MeasureSpec.UNSPECIFIED ? Integer.MAX_VALUE
                : MeasureSpec.getSize(widthMeasureSpec),
            MeasureSpec.getMode(heightMeasureSpec) == MeasureSpec.UNSPECIFIED ? Integer.MAX_VALUE
                : MeasureSpec.getSize(heightMeasureSpec));
        int side = room == Integer.MAX_VALUE ? desiredPx
            : Math.min(desiredPx, Math.max(0, room - 2 * style.dp(MARGIN_DP)));
        setMeasuredDimension(side, side);
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
