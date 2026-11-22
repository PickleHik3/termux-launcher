package com.termux.app.launcher.widget.builtin;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.launcher.widget.builtin.WidgetLayoutPlan.Box;

import java.time.LocalDate;
import java.util.Map;

/**
 * The Calendar's fortnight: one column per day, its weekday letter (when the plan has room) over
 * its number and the dot of its first event under the number. Today's column is filled in the
 * accent at half the card's corner radius; weekends are in the variant colour. The geometry is the
 * planner's {@link CalendarLayoutPlanner.Grid}.
 */
final class CalendarWeekStripView extends View {
    private final BuiltinWidgetStyle style;
    /** The widget's live corner: today's fill is cut at half of it, as it moves. */
    private final BuiltinWidgetCorner corner;
    private final float density;
    private final Paint letterPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dayPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint.FontMetrics metrics = new Paint.FontMetrics();
    private final RectF rect = new RectF();

    @Nullable private CalendarLayoutPlanner.Grid grid;
    @NonNull private String[] letters = new String[0];
    @NonNull private String[] numbers = new String[0];
    @NonNull private boolean[] weekend = new boolean[0];
    @NonNull private int[] marks = new int[0];
    private int todayIndex = -1;

    CalendarWeekStripView(@NonNull Context context, @NonNull BuiltinWidgetStyle style,
                          @NonNull BuiltinWidgetCorner corner) {
        super(context);
        this.style = style;
        this.corner = corner;
        density = context.getResources().getDisplayMetrics().density;
        letterPaint.setTextAlign(Paint.Align.CENTER);
        dayPaint.setTextAlign(Paint.Align.CENTER);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    /** The grid's days from {@code start}, named by {@code names}; {@code dayColors} feeds the dots. */
    void set(@NonNull CalendarLayoutPlanner.Grid grid, @NonNull LocalDate start,
             @NonNull LocalDate today, @Nullable Map<LocalDate, Integer> dayColors,
             @NonNull String[] names) {
        this.grid = grid;
        int count = grid.columns;
        letters = new String[count];
        numbers = new String[count];
        weekend = new boolean[count];
        marks = new int[count];
        todayIndex = -1;
        for (int i = 0; i < count; i++) {
            LocalDate day = start.plusDays(i);
            letters[i] = i < names.length ? names[i] : "";
            numbers[i] = Integer.toString(day.getDayOfMonth());
            weekend[i] = CalendarWidgetFormats.isWeekend(day.getDayOfWeek());
            Integer mark = dayColors == null ? null : dayColors.get(day);
            marks[i] = mark == null ? 0 : mark;
            if (day.equals(today)) todayIndex = i;
        }
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
        for (int i = 0; i < grid.columns && i < numbers.length; i++) {
            boolean today = i == todayIndex;
            if (today) {
                set(rect, grid.column(i), rtl, width);
                float radius = Math.min(corner.radiusPx() / 2f, rect.width() / 2f);
                fillPaint.setColor(style.primary);
                canvas.drawRoundRect(rect, radius, radius, fillPaint);
            }
            @ColorInt int ink = today ? style.onPrimary
                : weekend[i] ? style.onSurfaceVariant : style.onSurface;
            if (grid.letterRole != null && i < grid.letters.size()) {
                letterPaint.setColor(ink);
                letterPaint.setAlpha(Math.round(Color.alpha(ink) * 0.8f));
                drawText(canvas, letters[i], grid.letters.get(i), letterPaint, rtl, width);
            }
            dayPaint.setColor(ink);
            drawText(canvas, numbers[i], grid.digits.get(i), dayPaint, rtl, width);
            if (marks[i] != 0) {
                set(rect, grid.dots.get(i), rtl, width);
                fillPaint.setColor(today ? style.onPrimary : marks[i]);
                canvas.drawCircle(rect.centerX(), rect.centerY(), rect.width() / 2f, fillPaint);
            }
        }
    }

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
