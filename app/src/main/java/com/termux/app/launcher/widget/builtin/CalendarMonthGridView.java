package com.termux.app.launcher.widget.builtin;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
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
    /** Start-aligned twin of {@link #dayPaint}, to read the digits' ink bounds from. */
    private final Paint inkPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Rect ink = new Rect();
    /** A two-digit number's ink, so today's disc is one size on every day of the month. */
    private final Rect sizing = new Rect();
    private final float[] disc = new float[3];
    private int[] todayColours = {0, 0};

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
        inkPaint.setTypeface(dayPaint.getTypeface());
        inkPaint.setFontFeatureSettings(dayPaint.getFontFeatureSettings());
        inkPaint.setTextSize(dayPaint.getTextSize());
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
                fitDisc(grid, i, DAY_LABELS[day], rtl, width);
                todayColours = CalendarTodayDiscPolicy.todayDiscColours(style.card, style.primary,
                    style.onPrimary, style.onSurface);
                fillPaint.setColor(todayColours[0]);
                canvas.drawCircle(disc[0], disc[1], disc[2], fillPaint);
            }
            dayPaint.setColor(isToday ? todayColours[1]
                : weekendColumns[col] ? style.onSurfaceVariant : style.onSurface);
            drawText(canvas, DAY_LABELS[day], grid.digits.get(i), dayPaint, rtl, width);
            int mark = dayMarks[day];
            if (mark != 0) {
                set(rect, grid.dots.get(i), rtl, width);
                fillPaint.setColor(isToday ? todayColours[1] : mark);
                canvas.drawCircle(rect.centerX(), rect.centerY(), rect.width() / 2f, fillPaint);
            }
        }
    }

    /** The widest two-digit date in most fonts; the disc is sized to hold it. */
    private static final String DISC_SIZING_SAMPLE = "28";

    /** Fills {@link #disc} for the number in cell {@code index}: its drawn ink, held in the cell. */
    private void fitDisc(@NonNull CalendarLayoutPlanner.Grid grid, int index,
                         @NonNull String text, boolean rtl, float width) {
        set(rect, grid.digits.get(index), rtl, width);
        inkPaint.getFontMetrics(metrics);
        float baseline = rect.top + (rect.height() - (metrics.descent - metrics.ascent)) / 2f
            - metrics.ascent;
        inkPaint.getTextBounds(text, 0, text.length(), ink);
        inkPaint.getTextBounds(DISC_SIZING_SAMPLE, 0, DISC_SIZING_SAMPLE.length(), sizing);
        float advance = inkPaint.measureText(text);
        // Drawn centre-aligned on the box's centre, so the text starts half an advance back.
        float inkCx = rect.centerX() - advance / 2f + (ink.left + ink.right) / 2f;
        float inkCy = baseline + (ink.top + ink.bottom) / 2f;
        Box cell = grid.cells.get(index);
        float pitch = (grid.cells.size() > 7 ? grid.cells.get(7).top - cell.top
            : cell.height + CalendarLayoutPlanner.CELL_GAP_DP) * density;
        set(rect, cell, rtl, width);
        CalendarTodayDisc.fit(inkCx, inkCy, sizing.width(), sizing.height(), rect.left, rect.width(),
            pitch, CalendarLayoutPlanner.CELL_GAP_DP * density, disc);
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
