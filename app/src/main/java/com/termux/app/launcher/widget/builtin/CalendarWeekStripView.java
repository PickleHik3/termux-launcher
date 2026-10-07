package com.termux.app.launcher.widget.builtin;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.TypedValue;
import android.view.View;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.time.LocalDate;
import java.util.Locale;
import java.util.Map;

/**
 * The calendar's week strip: one cell per day, a weekday letter over the day's number, today
 * filled in the accent at half the card's corner radius, weekends in the variant colour. With
 * marks, each cell also carries a 4dp dot in the day's first event colour (on-accent on today).
 */
final class CalendarWeekStripView extends View {
    private final BuiltinWidgetStyle style;
    private final Paint letterPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dayPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint.FontMetrics metrics = new Paint.FontMetrics();
    private final RectF rect = new RectF();
    private final float padPx;
    private final float innerGapPx;
    private final float cellGapPx;
    private final float dotPx;

    @NonNull private String[] letters = new String[0];
    @NonNull private String[] numbers = new String[0];
    @NonNull private boolean[] weekend = new boolean[0];
    @NonNull private int[] marks = new int[0];
    private int todayIndex = -1;
    private final boolean dots;

    CalendarWeekStripView(@NonNull Context context, @NonNull BuiltinWidgetStyle style,
                          float daySp, boolean dots) {
        super(context);
        this.style = style;
        this.dots = dots;
        padPx = style.dp(5);
        innerGapPx = style.dp(2);
        cellGapPx = style.dp(2);
        dotPx = style.dp(4);
        letterPaint.setTypeface(style.sansBold);
        letterPaint.setTextSize(sp(9.5f));
        letterPaint.setTextAlign(Paint.Align.CENTER);
        dayPaint.setTypeface(style.monoMedium);
        dayPaint.setTextSize(sp(daySp));
        dayPaint.setTextAlign(Paint.Align.CENTER);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    private float sp(float value) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, value,
            getResources().getDisplayMetrics());
    }

    /** {@code count} days from {@code start}; {@code dayColors} feeds the dots when shown. */
    void set(@NonNull LocalDate start, int count, @NonNull LocalDate today,
             @Nullable Map<LocalDate, Integer> dayColors, @NonNull Locale locale) {
        letters = new String[count];
        numbers = new String[count];
        weekend = new boolean[count];
        marks = new int[count];
        todayIndex = -1;
        for (int i = 0; i < count; i++) {
            LocalDate day = start.plusDays(i);
            letters[i] = CalendarWidgetFormats.weekdayLetter(day.getDayOfWeek(), locale);
            numbers[i] = Integer.toString(day.getDayOfMonth());
            weekend[i] = CalendarWidgetFormats.isWeekend(day.getDayOfWeek());
            Integer mark = dayColors == null ? null : dayColors.get(day);
            marks[i] = mark == null ? 0 : mark;
            if (day.equals(today)) todayIndex = i;
        }
        requestLayout();
        invalidate();
    }

    private float height(@NonNull Paint paint) {
        paint.getFontMetrics(metrics);
        return metrics.descent - metrics.ascent;
    }

    @Override protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        float wanted = padPx * 2 + height(letterPaint) + innerGapPx + height(dayPaint)
            + (dots ? innerGapPx + dotPx : 0);
        setMeasuredDimension(resolveSize(MeasureSpec.getSize(widthMeasureSpec), widthMeasureSpec),
            resolveSize(Math.round(wanted), heightMeasureSpec));
    }

    @Override protected void onDraw(@NonNull Canvas canvas) {
        int count = letters.length;
        if (count == 0) return;
        float width = getWidth() - getPaddingLeft() - getPaddingRight();
        float cellWidth = (width - (count - 1) * cellGapPx) / count;
        if (cellWidth <= 0) return;
        float letterHeight = height(letterPaint);
        float letterBaseline = padPx - metrics.ascent;
        float dayHeight = height(dayPaint);
        float dayBaseline = padPx + letterHeight + innerGapPx - metrics.ascent;
        float dotCenter = padPx + letterHeight + innerGapPx + dayHeight + innerGapPx + dotPx / 2f;
        float radius = Math.min(style.cornerRadiusPx / 2f, cellWidth / 2f);
        for (int i = 0; i < count; i++) {
            float left = getPaddingLeft() + i * (cellWidth + cellGapPx);
            float cx = left + cellWidth / 2f;
            boolean today = i == todayIndex;
            if (today) {
                rect.set(left, 0, left + cellWidth, getHeight());
                fillPaint.setColor(style.primary);
                canvas.drawRoundRect(rect, radius, radius, fillPaint);
            }
            @ColorInt int ink = today ? style.onPrimary
                : weekend[i] ? style.onSurfaceVariant : style.onSurface;
            letterPaint.setColor(ink);
            letterPaint.setAlpha(Math.round(Color.alpha(ink) * 0.75f));
            canvas.drawText(letters[i], cx, letterBaseline, letterPaint);
            dayPaint.setColor(ink);
            canvas.drawText(numbers[i], cx, dayBaseline, dayPaint);
            if (dots && marks[i] != 0) {
                fillPaint.setColor(today ? style.onPrimary : marks[i]);
                canvas.drawCircle(cx, dotCenter, dotPx / 2f, fillPaint);
            }
        }
    }
}
