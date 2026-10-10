package com.termux.app.launcher.widget.builtin;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.R;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.Map;

/**
 * The month at a glance: today's date on a band (1×1), this week (2×1), the month grid (2×2),
 * a fortnight with event dots (4×1) and the month beside today's events (4×2). Event marks come
 * from the shared {@link CalendarEventsSource}; without calendar access the grids still draw,
 * without marks, and the wide spans ask for access where their event list would be.
 */
public class CalendarWidgetView extends BuiltinWidgetView {
    /** The 4×2 month's narrowest: 16dp a day column. */
    private static final int MONTH_MIN_DP = 112;
    /** The narrowest the 4×2 events column is shown at: a time and a title that still reads. */
    private static final int EVENTS_MIN_DP = 72;
    private static final String GLYPH_PREV_MONTH = "\uf053";
    private static final String GLYPH_NEXT_MONTH = "\uf054";
    /** The month steppers' disc: a touch target beside the 12sp month name. */
    private static final int STEPPER_DP = 22;

    @Nullable private FrameLayout frame;
    @Nullable private BuiltinWidgetUi ui;
    @Nullable private CalendarEventsSource.Snapshot shown;
    @NonNull private String shownSignature = "";
    /** Months the month grid is stepped away from today's, negative for past. */
    private int monthOffset;

    private final CalendarEventsSource.Listener sourceListener = snapshot -> {
        if (isStarted()) populate();
    };
    // Only the 4×2 lists events that end during the day; the other spans move with the day,
    // which the source already reports.
    private final BuiltinWidgetServices.TickListener tick = () -> {
        if (!isStarted()) return;
        if (!signature(CalendarWidgetSupport.dayFor(this, services)).equals(shownSignature)) {
            populate();
        }
    };

    public CalendarWidgetView(@NonNull Context context, @NonNull BuiltinWidgetServices services,
                 @NonNull BuiltinWidgetStyle style) {
        super(context, BuiltinWidgetKind.CALENDAR, services, style);
    }

    @Override protected void onBuild(@NonNull FrameLayout frame, @NonNull BuiltinWidgetSpan span,
                                     @NonNull BuiltinWidgetUi ui) {
        this.frame = frame;
        this.ui = ui;
        populate();
    }

    @Override protected void onStart() {
        CalendarEventsSource source = CalendarEventsSource.of(services);
        source.subscribe(services, sourceListener);
        if (span() == BuiltinWidgetSpan.FOUR_BY_TWO) services.addTickListener(tick);
        if (source.snapshot() != shown) populate();
    }

    @Override protected void onStop() {
        CalendarEventsSource.of(services).unsubscribe(sourceListener);
        services.removeTickListener(tick);
    }

    @Override protected void onTap() {
        if (isPreview()) return;
        boolean asks = span() == BuiltinWidgetSpan.FOUR_BY_ONE || span() == BuiltinWidgetSpan.FOUR_BY_TWO;
        if (asks && !CalendarEventsSource.hasPermission(getContext())) {
            services.host().requestCalendarPermission();
            return;
        }
        CalendarWidgetSupport.openCalendarAt(getContext(), openAtMillis());
    }

    /** Now, or the month being shown once the grid has been stepped off today's. */
    private long openAtMillis() {
        if (monthOffset == 0) return System.currentTimeMillis();
        ZoneId zone = ZoneId.systemDefault();
        LocalDate mid = LocalDate.now(zone).plusMonths(monthOffset).withDayOfMonth(15);
        return CalendarWidgetFormats.startOfDay(mid, zone) + 12 * 60 * 60_000L;
    }

    // ----- drawing --------------------------------------------------------------------------

    private void populate() {
        FrameLayout frame = this.frame;
        BuiltinWidgetUi ui = this.ui;
        if (frame == null || ui == null) return;
        CalendarWidgetSupport.Day day = CalendarWidgetSupport.dayFor(this, services);
        frame.removeAllViews();
        View root;
        switch (span()) {
            case TWO_BY_ONE: root = twoByOne(ui, day); break;
            case TWO_BY_TWO: root = twoByTwo(ui, day); break;
            case FOUR_BY_ONE: root = fourByOne(ui, day); break;
            case FOUR_BY_TWO: root = fourByTwo(ui, day); break;
            case ONE_BY_ONE:
            default: root = oneByOne(ui, day); break;
        }
        frame.addView(root, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT));
        shown = isPreview() ? null : CalendarEventsSource.of(services).snapshot();
        shownSignature = signature(day);
        setContentDescription(describe(day));
    }

    @NonNull private String signature(@NonNull CalendarWidgetSupport.Day day) {
        StringBuilder out = new StringBuilder();
        out.append(day.today).append('|').append(day.permitted).append(day.loaded);
        if (span() == BuiltinWidgetSpan.FOUR_BY_TWO) {
            int count = 0;
            for (CalendarEvent event : todaysLeft(day)) {
                if (count++ == 3) break;
                out.append('|').append(event.id).append('@').append(event.startMs)
                    .append(day.slot(event));
            }
        }
        return out.toString();
    }

    @NonNull private String describe(@NonNull CalendarWidgetSupport.Day day) {
        Context context = getContext();
        LocalDate shown = shownMonth(day);
        String heading = context.getString(R.string.bw_calendar_cd_agenda,
            CalendarWidgetSupport.date(shown, "yMMMM", day.locale, day.zone),
            day.date("EEEEdMMMM"));
        String state = !day.permitted ? context.getString(R.string.bw_calendar_cd_access)
            : day.loaded ? day.countText(context) : "";
        return state.isEmpty() ? heading
            : context.getString(R.string.bw_calendar_cd_agenda, heading, state);
    }

    @NonNull private static java.util.List<CalendarEvent> todaysLeft(@NonNull CalendarWidgetSupport.Day day) {
        return CalendarWidgetFormats.remainingToday(day.events, day.now, day.zone);
    }

    @NonNull private static DayOfWeek firstDayOfWeek() {
        return CalendarWidgetFormats.firstDayOfWeek(java.util.Calendar.getInstance().getFirstDayOfWeek());
    }

    // ----- spans ----------------------------------------------------------------------------

    @NonNull private View oneByOne(@NonNull BuiltinWidgetUi ui, @NonNull CalendarWidgetSupport.Day day) {
        TextView band = ui.text(CalendarWidgetFormats.monthShortUpper(day.today.getMonth(), day.locale),
            11f, ui.style.sansBold, ui.style.onPrimary);
        band.setLetterSpacing(0.1f);
        band.setGravity(Gravity.CENTER);
        band.setBackgroundColor(ui.style.primary);
        BuiltinWidgetUi.size(band, ViewGroup.LayoutParams.MATCH_PARENT, ui.dp(24));
        TextView numeral = ui.numeral(Integer.toString(day.today.getDayOfMonth()), 34f);
        numeral.setGravity(Gravity.CENTER);
        LinearLayout root = ui.column(0, band, BuiltinWidgetUi.flexTall(numeral));
        // The band sits inside the rim, as the design's border-box card has it.
        int rim = Math.round(ui.style.rimWidthPx);
        root.setPadding(rim, rim, rim, 0);
        return root;
    }

    @NonNull private View twoByOne(@NonNull BuiltinWidgetUi ui, @NonNull CalendarWidgetSupport.Day day) {
        LocalDate start = CalendarWidgetFormats.weekStart(day.today, firstDayOfWeek());
        TextView heading = ui.text(getContext().getString(R.string.bw_calendar_month_week,
                CalendarWidgetFormats.monthName(day.today.getMonth(), day.locale),
                CalendarWidgetFormats.isoWeek(start)),
            11f, ui.style.sansBold, ui.style.onSurfaceVariant);
        heading.setPadding(ui.dp(4), 0, 0, 0);
        CalendarWeekStripView strip = new CalendarWeekStripView(getContext(), ui.style, 12.5f, false);
        strip.set(start, 7, day.today, null, day.locale);
        LinearLayout root = ui.column(8, CalendarWidgetSupport.wide(heading), CalendarWidgetSupport.wide(strip));
        root.setGravity(Gravity.CENTER_VERTICAL);
        inset(root, 10, 0, 10, 0, ui);
        return root;
    }

    @NonNull private View twoByTwo(@NonNull BuiltinWidgetUi ui, @NonNull CalendarWidgetSupport.Day day) {
        LinearLayout root = ui.column(6, CalendarWidgetSupport.wide(monthHeading(ui, day)),
            BuiltinWidgetUi.flexTall(monthGrid(ui, day, 6)));
        inset(root, 10, 12, 10, 12, ui);
        return root;
    }

    @NonNull private View fourByOne(@NonNull BuiltinWidgetUi ui, @NonNull CalendarWidgetSupport.Day day) {
        Context context = getContext();
        LocalDate start = CalendarWidgetFormats.weekStart(day.today, firstDayOfWeek());
        TextView weeks = ui.text(context.getString(R.string.bw_calendar_month_weeks,
                CalendarWidgetFormats.monthName(day.today.getMonth(), day.locale),
                CalendarWidgetFormats.isoWeek(start), CalendarWidgetFormats.isoWeek(start.plusDays(7))),
            11f, ui.style.sansBold, ui.style.onSurfaceVariant);
        TextView count = !day.permitted ? CalendarWidgetSupport.allowLine(ui, 11f, true)
            : ui.text(day.loaded ? day.countText(context) : "", 11f, ui.style.monoMedium,
                ui.style.onSurfaceVariant);
        LinearLayout heading = ui.row(8, BuiltinWidgetUi.flex(weeks), count);
        heading.setPadding(ui.dp(4), 0, ui.dp(4), 0);
        Map<LocalDate, Integer> marks = CalendarWidgetFormats.dayColors(day.events, start,
            start.plusDays(14), day.zone, ui.style.primary);
        CalendarWeekStripView strip = new CalendarWeekStripView(context, ui.style, 12f, true);
        strip.set(start, 14, day.today, marks, day.locale);
        LinearLayout root = ui.column(6, CalendarWidgetSupport.wide(heading), CalendarWidgetSupport.wide(strip));
        root.setGravity(Gravity.CENTER_VERTICAL);
        inset(root, 12, 0, 12, 0, ui);
        return root;
    }

    @NonNull private View fourByTwo(@NonNull BuiltinWidgetUi ui, @NonNull CalendarWidgetSupport.Day day) {
        Context context = getContext();
        LinearLayout month = ui.column(5, CalendarWidgetSupport.wide(monthHeading(ui, day)),
            BuiltinWidgetUi.flexTall(monthGrid(ui, day, 5)));
        BuiltinWidgetUi.size(month, ui.dp(184), ViewGroup.LayoutParams.MATCH_PARENT);

        View divider = ui.divider(true);
        LinearLayout.LayoutParams dividerParams = (LinearLayout.LayoutParams) divider.getLayoutParams();
        dividerParams.topMargin = ui.dp(4);
        dividerParams.bottomMargin = ui.dp(4);

        TextView heading = ui.text(context.getString(R.string.bw_calendar_today), 11f,
            ui.style.sansBold, ui.style.onSurfaceVariant);
        FitStack list = FitStack.column(context);
        if (!day.permitted) {
            list.addRow(CalendarWidgetSupport.allowLine(ui, 12f, false), ui.dp(9));
        } else if (day.loaded) {
            int count = 0;
            for (CalendarEvent event : todaysLeft(day)) {
                if (count++ == 3) break;
                list.addRow(CalendarWidgetSupport.compactRow(ui, event, day.slot(event),
                    !isPreview()), ui.dp(9));
            }
            if (count == 0) {
                list.addRow(CalendarWidgetSupport.emptyLine(ui, day.emptyText(context), 12f),
                    ui.dp(9));
            }
        }
        LinearLayout events = ui.column(9, CalendarWidgetSupport.wide(heading),
            BuiltinWidgetUi.flexTall(list));
        events.setPadding(0, ui.dp(2), 0, 0);
        events.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));

        // At the 245dp minimum the month, shrunk to a 16dp column per day, and the day's events
        // do not both fit: the events and their divider are the ones left out. The events never
        // get less than a column of titles that still reads.
        int gap = ui.dp(14);
        FitStack root = FitStack.row(context)
            .addShrink(month, FitStack.ESSENTIAL, 0, ui.dp(184), ui.dp(MONTH_MIN_DP))
            .add(divider, 5, gap)
            .addFlex(events, 5, gap, ui.dp(EVENTS_MIN_DP));
        inset(root, 10, 12, 14, 12, ui);
        return root;
    }

    // ----- pieces ---------------------------------------------------------------------------

    /**
     * "October" on the left, "2026" and the month steppers on the right. The
     * steppers walk the grid by one month and back; the grid is today's month
     * until one of them is pressed.
     */
    @NonNull private View monthHeading(@NonNull BuiltinWidgetUi ui, @NonNull CalendarWidgetSupport.Day day) {
        LocalDate shown = shownMonth(day);
        TextView month = ui.text(CalendarWidgetFormats.monthName(shown.getMonth(), day.locale),
            12f, ui.style.sansBold, ui.style.onSurface);
        TextView year = ui.mono(Integer.toString(shown.getYear()), 11f);
        LinearLayout text = CalendarWidgetSupport.baselineRow(ui, 8,
            BuiltinWidgetUi.flex(month), year);
        LinearLayout row = ui.row(8, BuiltinWidgetUi.flex(text),
            monthStepper(ui, GLYPH_PREV_MONTH, R.string.bw_calendar_previous_month, -1),
            monthStepper(ui, GLYPH_NEXT_MONTH, R.string.bw_calendar_next_month, 1));
        row.setPadding(ui.dp(4), 0, ui.dp(4), 0);
        return row;
    }

    /** A round chevron that steps the shown month by {@code step}. */
    @NonNull private TextView monthStepper(@NonNull BuiltinWidgetUi ui, @NonNull String glyph,
                                              int label, int step) {
        TextView button = ui.roundButton(glyph, STEPPER_DP, 10.5f, ui.style.primaryContainer,
            ui.style.onPrimaryContainer, getContext().getString(label));
        button.setOnClickListener(v -> stepMonth(step));
        return button;
    }

    private void stepMonth(int step) {
        monthOffset += step;
        populate();
    }

    @NonNull private LocalDate shownMonth(@NonNull CalendarWidgetSupport.Day day) {
        return day.today.plusMonths(monthOffset);
    }

    @NonNull private View monthGrid(@NonNull BuiltinWidgetUi ui, @NonNull CalendarWidgetSupport.Day day,
                                    int lettersGapDp) {
        YearMonth month = YearMonth.from(shownMonth(day));
        Map<LocalDate, Integer> marks = CalendarWidgetFormats.dayColors(day.events, month.atDay(1),
            month.atEndOfMonth().plusDays(1), day.zone, ui.style.primary);
        CalendarMonthGridView grid = new CalendarMonthGridView(getContext(), ui.style, lettersGapDp);
        grid.set(month, day.today, firstDayOfWeek(), marks, day.locale);
        return grid;
    }
}
