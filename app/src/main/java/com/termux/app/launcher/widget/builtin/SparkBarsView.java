package com.termux.app.launcher.widget.builtin;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.View;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;

/**
 * A row of thin bars standing on a hairline: the Battery widget's last 24 hours and the System
 * widget's recent CPU. Each bar is a height (0..1, or negative for "no reading") and a colour;
 * tops take the direction's bar radius, bottoms stay square on the line. Drawn, never laid out:
 * new values only invalidate.
 */
public final class SparkBarsView extends View {
    private static final int GAP_DP = 3;
    /** What an empty slot shows, so the timeline still reads as continuous. */
    private static final float STUB_DP = 2f;

    @NonNull private final BuiltinWidgetStyle style;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final RectF rect = new RectF();
    private final float[] radii = new float[8];
    @NonNull private float[] heights = new float[0];
    @NonNull private int[] colors = new int[0];

    public SparkBarsView(@NonNull Context context, @NonNull BuiltinWidgetStyle style) {
        super(context);
        this.style = style;
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    /** {@code heights} 0..1 (negative = empty) with one colour per bar, oldest first. */
    public void set(@NonNull float[] heights, @NonNull int[] colors) {
        this.heights = heights.clone();
        this.colors = colors.clone();
        invalidate();
    }

    @Override protected void onDraw(@NonNull Canvas canvas) {
        float width = getWidth(), height = getHeight();
        float hair = Math.max(1f, style.density);
        float floor = height - hair;
        paint.setColor(style.outlineVariant);
        canvas.drawRect(0, floor, width, height, paint);
        int count = heights.length;
        if (count == 0 || floor <= 0) return;
        float gap = GAP_DP * style.density;
        float barWidth = (width - gap * (count - 1)) / count;
        if (barWidth <= 0) return;
        float stub = STUB_DP * style.density;
        for (int i = 0; i < count; i++) {
            float value = heights[i];
            boolean empty = value < 0 || Float.isNaN(value);
            float barHeight = empty ? stub : Math.max(stub, Math.min(1f, value) * floor);
            float left = i * (barWidth + gap);
            rect.set(left, floor - barHeight, left + barWidth, floor);
            float radius = Math.min(style.barRadiusPx, Math.min(barWidth / 2f, barHeight));
            radii[0] = radii[1] = radii[2] = radii[3] = radius;
            radii[4] = radii[5] = radii[6] = radii[7] = 0f;
            path.reset();
            path.addRoundRect(rect, radii, Path.Direction.CW);
            paint.setColor(empty || i >= colors.length ? style.containerHigh : colors[i]);
            canvas.drawPath(path, paint);
        }
    }

    /** {@code fill} for the last {@code accented} bars, the high container for the rest. */
    @NonNull public static int[] accentTail(int count, int accented, @ColorInt int fill,
                                            @ColorInt int rest) {
        int[] out = new int[count];
        for (int i = 0; i < count; i++) out[i] = i >= count - accented ? fill : rest;
        return out;
    }
}
