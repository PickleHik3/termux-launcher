package com.termux.app.launcher.widget.builtin;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.View;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.core.graphics.ColorUtils;

/**
 * The Device widget's histories, standing on a hairline: the last 24 hours of the battery as a
 * filled line ({@link #setArea}), and the CPU's recent readings as thin bars ({@link #set}). Each
 * value is a height (0..1, or negative for "no reading"). Bar tops take the direction's bar
 * radius, bottoms stay square on the line; the line breaks over hours nothing was heard in and
 * ends in a dot at the newest reading. Drawn, never laid out: new values only invalidate.
 */
public final class SparkBarsView extends View {
    private static final int GAP_DP = 3;
    /** What an empty slot shows, so the timeline still reads as continuous. */
    private static final float STUB_DP = 2f;
    /** The area chart's line, and its newest reading's dot. */
    private static final float LINE_DP = 2f;
    private static final float DOT_DP = 3f;
    /** How much of the line's colour the area under it takes. */
    private static final float AREA_SHARE = 0.22f;

    @NonNull private final BuiltinWidgetStyle style;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final Path fill = new Path();
    private final RectF rect = new RectF();
    private final float[] radii = new float[8];
    @NonNull private float[] heights = new float[0];
    @NonNull private int[] colors = new int[0];
    private boolean area;
    @ColorInt private int areaColor;

    public SparkBarsView(@NonNull Context context, @NonNull BuiltinWidgetStyle style) {
        super(context);
        this.style = style;
        line.setStyle(Paint.Style.STROKE);
        line.setStrokeJoin(Paint.Join.ROUND);
        line.setStrokeCap(Paint.Cap.ROUND);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    /** Bars: {@code heights} 0..1 (negative = empty) with one colour per bar, oldest first. */
    public void set(@NonNull float[] heights, @NonNull int[] colors) {
        this.heights = heights.clone();
        this.colors = colors.clone();
        area = false;
        invalidate();
    }

    /** A filled line through {@code heights} 0..1 (negative = empty), oldest first. */
    public void setArea(@NonNull float[] heights, @ColorInt int color) {
        this.heights = heights.clone();
        this.colors = new int[0];
        areaColor = color;
        area = true;
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
        if (area) {
            drawArea(canvas, width, floor);
            return;
        }
        float gap = GAP_DP * style.density;
        float barWidth = (width - gap * (count - 1)) / count;
        if (barWidth <= 0) return;
        float stub = STUB_DP * style.density;
        for (int i = 0; i < count; i++) {
            float value = heights[i];
            boolean empty = empty(value);
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

    private void drawArea(@NonNull Canvas canvas, float width, float floor) {
        int count = heights.length;
        float dot = DOT_DP * style.density;
        float stroke = LINE_DP * style.density;
        // The line's half width and the dot stay inside the view.
        float inset = Math.max(stroke / 2f, dot);
        float span = Math.max(0f, width - 2f * inset);
        float top = inset, bottom = floor - stroke / 2f;
        float step = count > 1 ? span / (count - 1) : 0f;
        line.setStrokeWidth(stroke);
        line.setColor(areaColor);
        @ColorInt int under = ColorUtils.setAlphaComponent(areaColor,
            Math.round(Color.alpha(areaColor) * AREA_SHARE));
        int i = 0;
        while (i < count) {
            if (empty(heights[i])) { i++; continue; }
            int start = i;
            while (i < count && !empty(heights[i])) i++;
            int end = i - 1;
            path.reset();
            fill.reset();
            for (int j = start; j <= end; j++) {
                float x = inset + j * step;
                float y = bottom - Math.min(1f, heights[j]) * (bottom - top);
                if (j == start) {
                    path.moveTo(x, y);
                    fill.moveTo(x, floor);
                }
                path.lineTo(x, y);
                fill.lineTo(x, y);
            }
            fill.lineTo(inset + end * step, floor);
            fill.close();
            if (end > start) {
                paint.setColor(under);
                canvas.drawPath(fill, paint);
            }
            canvas.drawPath(path, line);
        }
        int newest = count - 1;
        if (!empty(heights[newest])) {
            paint.setColor(areaColor);
            canvas.drawCircle(inset + newest * step,
                bottom - Math.min(1f, heights[newest]) * (bottom - top), dot, paint);
        }
    }

    private static boolean empty(float value) { return value < 0 || Float.isNaN(value); }

    /** {@code fill} for the last {@code accented} bars, the high container for the rest. */
    @NonNull public static int[] accentTail(int count, int accented, @ColorInt int fill,
                                            @ColorInt int rest) {
        int[] out = new int[count];
        for (int i = 0; i < count; i++) out[i] = i >= count - accented ? fill : rest;
        return out;
    }
}
