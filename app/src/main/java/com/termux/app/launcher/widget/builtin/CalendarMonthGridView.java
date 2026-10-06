package com.termux.app.launcher.widget.builtin;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.TypedValue;
import android.view.View;

import androidx.annotation.NonNull;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Locale;
import java.util.Map;

/**
 * The calendar's month: a row of weekday letters, then the days in a 7×5 (or 7×6) grid. Today
 * is a filled circle in the accent; a day with events carries a 2dp sliver along the bottom of
 * its circle in the first event's colour; weekend columns are in the variant colour. One view,
 * one draw pass, nothing allocated while drawing.
 */
final class CalendarMonthGridView extends View {
    private final BuiltinWidgetStyle style;
    private final Paint letterPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dayPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path circle = new Path();
    private final float lettersGapPx;
    private final float cellGapPx;
    private final float markPx;

    private static final String[] DAY_LABELS = new String[32];
    static {
        for (int i = 1; i < DAY_LABELS.length; i++) DAY_LABELS[i] = Integer.toString(i);
    }

    private final Paint.FontMetrics metrics = new Paint.FontMetrics();
    @NonNull private final String[] letters = new String[7];
    /** Per day of the month, the colour of its sliver, or 0 for none. */
    @NonNull private final int[] dayMarks = new int[32];
    private final boolean[] weekendColumns = new boolean[7];
    private int days = 30;
    private int todayDay = -1;
    private int lead;
    private int rows = 5;

    CalendarMonthGridView(@NonNull Context context, @NonNull BuiltinWidgetStyle style,
                          int lettersGapDp) {
        super(context);
        this.style = style;
        lettersGapPx = style.dp(lettersGapDp);
        cellGapPx = Math.max(1, style.dp(1));
        markPx = style.dp(2);
        letterPaint.setTypeface(style.sansBold);
        letterPaint.setTextSize(sp(9.5f));
        letterPaint.setColor(style.onSurfaceVariant);
        letterPaint.setTextAlign(Paint.Align.CENTER);
        dayPaint.setTypeface(style.monoMedium);
        dayPaint.setTextSize(sp(10.5f));
        dayPaint.setTextAlign(Paint.Align.CENTER);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    private float sp(float value) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, value,
            getResources().getDisplayMetrics());
    }

    /** Shows {@code month} with {@code today} filled and a sliver under every day in {@code marks}. */
    void set(@NonNull YearMonth month, @NonNull LocalDate today, @NonNull DayOfWeek first,
             @NonNull Map<LocalDate, Integer> marks, @NonNull Locale locale) {
        for (int i = 0; i < 7; i++) {
            letters[i] = CalendarWidgetFormats.weekdayLetter(first.plus(i), locale);
            weekendColumns[i] = CalendarWidgetFormats.isWeekend(first.plus(i));
        }
        days = month.lengthOfMonth();
        todayDay = YearMonth.from(today).equals(month) ? today.getDayOfMonth() : -1;
        for (int day = 1; day < dayMarks.length; day++) {
            Integer mark = day <= days ? marks.get(month.atDay(day)) : null;
            dayMarks[day] = mark == null ? 0 : mark;
        }
        lead = CalendarWidgetFormats.leadingBlanks(month, first);
        int nextRows = CalendarWidgetFormats.monthRows(month, first);
        if (nextRows != rows) {
            rows = nextRows;
            requestLayout();
        }
        invalidate();
    }

    private float lettersHeight() {
        letterPaint.getFontMetrics(metrics);
        return metrics.descent - metrics.ascent;
    }

    @Override protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        dayPaint.getFontMetrics(metrics);
        float minRow = Math.max(style.dp(18), (metrics.descent - metrics.ascent) + style.dp(4));
        int wanted = Math.round(lettersHeight() + lettersGapPx + rows * minRow
            + (rows - 1) * cellGapPx);
        int width = MeasureSpec.getSize(widthMeasureSpec);
        setMeasuredDimension(resolveSize(width, widthMeasureSpec),
            resolveSize(wanted, heightMeasureSpec));
    }

    @Override protected void onDraw(@NonNull Canvas canvas) {
        float width = getWidth() - getPaddingLeft() - getPaddingRight();
        float left = getPaddingLeft();
        float top = getPaddingTop();
        float colWidth = (width - 6 * cellGapPx) / 7f;
        if (colWidth <= 0) return;

        letterPaint.getFontMetrics(metrics);
        float letterBaseline = top - metrics.ascent;
        for (int col = 0; col < 7; col++) {
            float cx = left + col * (colWidth + cellGapPx) + colWidth / 2f;
            canvas.drawText(letters[col], cx, letterBaseline, letterPaint);
        }

        float gridTop = top + lettersHeight() + lettersGapPx;
        float gridHeight = getHeight() - getPaddingBottom() - gridTop;
        float rowHeight = (gridHeight - (rows - 1) * cellGapPx) / rows;
        if (rowHeight <= 0) return;
        float radius = Math.min(colWidth, rowHeight) / 2f;
        dayPaint.getFontMetrics(metrics);
        float baselineOffset = -(metrics.ascent + metrics.descent) / 2f;
        for (int cell = 0; cell < rows * 7; cell++) {
            int day = cell - lead + 1;
            if (day < 1 || day > days) continue;
            int col = cell % 7, row = cell / 7;
            float cx = left + col * (colWidth + cellGapPx) + colWidth / 2f;
            float cy = gridTop + row * (rowHeight + cellGapPx) + rowHeight / 2f;
            boolean isToday = day == todayDay;
            if (isToday) {
                fillPaint.setColor(style.primary);
                canvas.drawCircle(cx, cy, radius, fillPaint);
            } else {
                int mark = dayMarks[day];
                if (mark != 0) {
                    circle.rewind();
                    circle.addCircle(cx, cy, radius, Path.Direction.CW);
                    canvas.save();
                    canvas.clipPath(circle);
                    fillPaint.setColor(mark);
                    canvas.drawRect(cx - radius, cy + radius - markPx, cx + radius, cy + radius,
                        fillPaint);
                    canvas.restore();
                }
            }
            dayPaint.setColor(isToday ? style.onPrimary
                : weekendColumns[col] ? style.onSurfaceVariant : style.onSurface);
            canvas.drawText(DAY_LABELS[day], cx, cy + baselineOffset, dayPaint);
        }
    }
}
