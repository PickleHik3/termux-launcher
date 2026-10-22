package com.termux.app.launcher.widget.builtin;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.launcher.widget.builtin.WidgetLayoutPlan.Box;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Map;

/**
 * The Calendar's month: the column names, then the weeks the plan's {@link
 * CalendarLayoutPlanner.Grid} has room for, each day's digit with its event dot under it in the
 * day's first event colour. Today is a pill in the accent over its digit and dot. The geometry is
 * the planner's, so what is tested is what draws. Nothing is allocated while drawing.
 */
final class CalendarMonthGridView extends View {
    private static final String[] DAY_LABELS = new String[32];
    static {
        for (int i = 1; i < DAY_LABELS.length; i++) DAY_LABELS[i] = Integer.toString(i);
    }

    private final BuiltinWidgetStyle style;
    private final float density;
    private final Paint letterPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dayPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint.FontMetrics metrics = new Paint.FontMetrics();
    private final RectF rect = new RectF();

    @Nullable private CalendarLayoutPlanner.Grid grid;
    @NonNull private final String[] names = new String[7];
    /** Per day of the month, its dot's colour, or 0 for none. */
    @NonNull private final int[] dayMarks = new int[32];
    private final boolean[] weekendColumns = new boolean[7];
    private int days = 30;
    private int todayDay = -1;
    private int lead;

    CalendarMonthGridView(@NonNull Context context, @NonNull BuiltinWidgetStyle style) {
        super(context);
        this.style = style;
        density = context.getResources().getDisplayMetrics().density;
        letterPaint.setTextAlign(Paint.Align.CENTER);
        dayPaint.setTextAlign(Paint.Align.CENTER);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    /**
     * Shows {@code month} laid out by {@code grid}, {@code today} as the pill, a dot under every
     * day in {@code marks}; {@code shortNames} and {@code narrowNames} name the columns from
     * {@code first}.
     */
    void set(@NonNull CalendarLayoutPlanner.Grid grid, @NonNull YearMonth month,
             @NonNull LocalDate today, @NonNull DayOfWeek first,
             @NonNull Map<LocalDate, Integer> marks, @NonNull String[] shortNames,
             @NonNull String[] narrowNames) {
        this.grid = grid;
        for (int i = 0; i < 7; i++) {
            names[i] = grid.shortNames ? shortNames[i] : narrowNames[i];
            weekendColumns[i] = CalendarWidgetFormats.isWeekend(first.plus(i));
        }
        days = month.lengthOfMonth();
        todayDay = YearMonth.from(today).equals(month) ? today.getDayOfMonth() : -1;
        for (int day = 1; day < dayMarks.length; day++) {
            Integer mark = day <= days ? marks.get(month.atDay(day)) : null;
            dayMarks[day] = mark == null ? 0 : mark;
        }
        lead = CalendarWidgetFormats.leadingBlanks(month, first);
        CalendarWidgetSupport.dressNumerals(dayPaint, style, grid.digitRole,
            grid.digitSizeDp * density);
        if (grid.letterRole != null) {
            letterPaint.setTypeface(style.sansMedium);
            letterPaint.setTextSize(grid.letterSizeDp * density);
        }
        invalidate();
    }

    @Override protected void onDraw(@NonNull Canvas canvas) {
        CalendarLayoutPlanner.Grid grid = this.grid;
        if (grid == null) return;
        boolean rtl = getLayoutDirection() == LAYOUT_DIRECTION_RTL;
        float width = getWidth();
        if (grid.letterRole != null) {
            letterPaint.setColor(style.onSurfaceVariant);
            for (int col = 0; col < 7 && col < grid.letters.size(); col++) {
                drawText(canvas, names[col], grid.letters.get(col), letterPaint, rtl, width);
            }
        }
        for (int i = 0; i < grid.cells.size(); i++) {
            int row = i / 7 + grid.firstRow;
            int col = i % 7;
            int day = row * 7 + col - lead + 1;
            if (day < 1 || day > days) continue;
            boolean isToday = day == todayDay;
            if (isToday) {
                set(rect, grid.selectedDisc(i), rtl, width);
                float radius = Math.min(rect.width(), rect.height()) / 2f;
                fillPaint.setColor(style.primary);
                canvas.drawRoundRect(rect, radius, radius, fillPaint);
            }
            dayPaint.setColor(isToday ? style.onPrimary
                : weekendColumns[col] ? style.onSurfaceVariant : style.onSurface);
            drawText(canvas, DAY_LABELS[day], grid.digits.get(i), dayPaint, rtl, width);
            int mark = dayMarks[day];
            if (mark != 0) {
                set(rect, grid.dots.get(i), rtl, width);
                fillPaint.setColor(isToday ? style.onPrimary : mark);
                canvas.drawCircle(rect.centerX(), rect.centerY(), rect.width() / 2f, fillPaint);
            }
        }
    }

    /** {@code text} centred in {@code box}, the box mirrored for a right-to-left layout. */
    private void drawText(@NonNull Canvas canvas, @NonNull String text, @NonNull Box box,
                          @NonNull Paint paint, boolean rtl, float width) {
        set(rect, box, rtl, width);
        paint.getFontMetrics(metrics);
        float baseline = rect.top + (rect.height() - (metrics.descent - metrics.ascent)) / 2f
            - metrics.ascent;
        canvas.drawText(text, rect.centerX(), baseline, paint);
    }

    private void set(@NonNull RectF out, @NonNull Box box, boolean rtl, float width) {
        float left = box.left * density;
        float right = left + box.width * density;
        if (rtl) {
            float mirrored = width - right;
            right = width - left;
            left = mirrored;
        }
        out.set(left, box.top * density, right, (box.top + box.height) * density);
    }
}
