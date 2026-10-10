package com.termux.app.launcher.widget.builtin;

import android.content.Context;
import android.graphics.Rect;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.TouchDelegate;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.R;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.Month;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
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
    /** The month steppers' disc, beside the 12sp month name. */
    private static final int STEPPER_DP = 22;
    /** The square a stepper answers touches in, so a near miss does not open the calendar app. */
    private static final int STEPPER_HIT_DP = 36;
    /** The steppers' gap, and the least it closes to before the month name would be cut. */
    private static final int STEPPER_GAP_DP = 8;
    private static final int STEPPER_GAP_MIN_DP = 4;
    /** The month heading's side padding, and the gap between its name, year and steppers. */
    private static final int HEADING_SIDE_DP = 4;
    private static final int HEADING_GAP_DP = 8;
    /** Extra air under the month heading, taken from the grid's rows, so the steppers' touch areas
     *  end before the weekday letters. */
    private static final int HEADING_GRID_GAP_DP = 4;

    @Nullable private FrameLayout frame;
    @Nullable private BuiltinWidgetUi ui;
    @Nullable private CalendarEventsSource.Snapshot shown;
    @NonNull private String shownSignature = "";
    /** The month the grid was stepped to, or null to follow today's. */
    @Nullable private YearMonth anchor;
    /** The month the grid was last drawn for (null: no grid), and its read (null: the window's). */
    @Nullable private YearMonth drawnMonth;
    @Nullable private CalendarEventsSource.MonthEvents drawnMonthEvents;

    private final CalendarEventsSource.Listener sourceListener = new CalendarEventsSource.Listener() {
        @Override public void onCalendarEvents(@NonNull CalendarEventsSource.Snapshot snapshot) {
            if (isStarted()) populate();
        }

        @Override public void onCalendarMonth(@NonNull YearMonth month) {
            // The grid draws from the kept read from now on, so this repopulates once per read.
            if (isStarted() && month.equals(drawnMonth)) populate();
        }
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
        if (source.snapshot() != shown || monthStale(source)) populate();
    }

    @Override protected void onStop() {
        CalendarEventsSource.of(services).unsubscribe(sourceListener);
        services.removeTickListener(tick);
        // Back on today's month next time; the grid is redrawn when the widget starts again.
        anchor = null;
    }

    /**
     * Whether the grid shows another month than it should (stepped off, or the month turned
     * while stopped), or drew without a month read that is there now. Asks for the read.
     */
    private boolean monthStale(@NonNull CalendarEventsSource source) {
        YearMonth drawn = drawnMonth;
        if (drawn == null) return false;
        YearMonth due = CalendarWidgetFormats.shownMonth(anchor, LocalDate.now(ZoneId.systemDefault()));
        return !drawn.equals(due) || source.month(drawn) != drawnMonthEvents;
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
        YearMonth month = anchor;
        if (month == null) return System.currentTimeMillis();
        ZoneId zone = ZoneId.systemDefault();
        return CalendarWidgetFormats.startOfDay(month.atDay(15), zone) + 12 * 60 * 60_000L;
    }

    // ----- drawing --------------------------------------------------------------------------

    private void populate() {
        FrameLayout frame = this.frame;
        BuiltinWidgetUi ui = this.ui;
        if (frame == null || ui == null) return;
        CalendarWidgetSupport.Day day = CalendarWidgetSupport.dayFor(this, services);
        frame.removeAllViews();
        frame.setTouchDelegate(null);
        drawnMonth = null;
        drawnMonthEvents = null;
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
        String heading = context.getString(R.string.bw_calendar_cd_agenda,
            CalendarWidgetSupport.date(shownMonth(day).atDay(1), "yMMMM", day.locale, day.zone),
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
        LinearLayout root = ui.column(HEADING_GRID_GAP_DP + 6,
            CalendarWidgetSupport.wide(monthHeading(ui, day)),
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
        MonthHeading monthHeading = monthHeading(ui, day);
        LinearLayout month = ui.column(HEADING_GRID_GAP_DP + 5, CalendarWidgetSupport.wide(monthHeading),
            BuiltinWidgetUi.flexTall(monthGrid(ui, day, 5)));
        // The month goes no narrower than 16dp a day column, nor than keeps the longest month's
        // name whole beside the steppers, measured in the face and font scale it is drawn in.
        int monthMin = Math.max(ui.dp(MONTH_MIN_DP), monthHeading.narrowest(day.locale));
        int monthWidth = Math.max(ui.dp(184), monthMin);
        BuiltinWidgetUi.size(month, monthWidth, ViewGroup.LayoutParams.MATCH_PARENT);

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
            .addShrink(month, FitStack.ESSENTIAL, 0, monthWidth, monthMin)
            .add(divider, 5, gap)
            .addFlex(events, 5, gap, ui.dp(EVENTS_MIN_DP));
        inset(root, 10, 12, 14, 12, ui);
        return root;
    }

    // ----- pieces ---------------------------------------------------------------------------

    /**
     * "October" on the left, "2026" and the month steppers on the right. The steppers walk the
     * grid by a month; once stepped off, the month's name brings it to today's again.
     */
    @NonNull private MonthHeading monthHeading(@NonNull BuiltinWidgetUi ui,
                                               @NonNull CalendarWidgetSupport.Day day) {
        YearMonth shown = shownMonth(day);
        TextView month = ui.text(CalendarWidgetFormats.monthName(shown.getMonth(), day.locale),
            12f, ui.style.sansBold, ui.style.onSurface);
        TextView year = ui.mono(Integer.toString(shown.getYear()), 11f);
        MonthHeading heading = new MonthHeading(ui, month,
            CalendarWidgetFormats.monthShort(shown.getMonth(), day.locale), year,
            monthStepper(ui, GLYPH_PREV_MONTH, R.string.bw_calendar_previous_month, -1),
            monthStepper(ui, GLYPH_NEXT_MONTH, R.string.bw_calendar_next_month, 1));
        if (isPreview()) return heading;
        if (anchor != null) {
            OnClickListener toToday = v -> {
                anchor = null;
                populate();
            };
            month.setOnClickListener(toToday);
            month.setContentDescription(getContext().getString(R.string.bw_calendar_this_month));
            CalendarWidgetSupport.pressable(month);
            year.setOnClickListener(toToday);
            year.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        }
        FrameLayout frame = this.frame;
        if (frame != null) frame.setTouchDelegate(new StepperTouch(frame, heading));
        return heading;
    }

    /** A round chevron that steps the shown month by {@code step}; inert on the picker's card. */
    @NonNull private TextView monthStepper(@NonNull BuiltinWidgetUi ui, @NonNull String glyph,
                                           int label, int step) {
        TextView button = ui.roundButton(glyph, STEPPER_DP, 10.5f, ui.style.primaryContainer,
            ui.style.onPrimaryContainer, getContext().getString(label));
        if (isPreview()) {
            button.setClickable(false);
            button.setFocusable(false);
        } else {
            button.setOnClickListener(v -> stepMonth(step));
        }
        return button;
    }

    private void stepMonth(int step) {
        anchor = CalendarWidgetFormats.stepMonth(anchor, LocalDate.now(ZoneId.systemDefault()), step);
        populate();
    }

    @NonNull private YearMonth shownMonth(@NonNull CalendarWidgetSupport.Day day) {
        return CalendarWidgetFormats.shownMonth(anchor, day.today);
    }

    @NonNull private View monthGrid(@NonNull BuiltinWidgetUi ui, @NonNull CalendarWidgetSupport.Day day,
                                    int lettersGapDp) {
        YearMonth month = shownMonth(day);
        // The whole month's read once it lands; until then the rolling window, so today's month
        // never draws bare while it is read.
        CalendarEventsSource.MonthEvents read = isPreview() || !day.permitted ? null
            : CalendarEventsSource.of(services).month(month);
        List<CalendarEvent> events = read != null ? read.events : day.events;
        drawnMonth = month;
        drawnMonthEvents = read;
        Map<LocalDate, Integer> marks = CalendarWidgetFormats.dayColors(events, month.atDay(1),
            month.atEndOfMonth().plusDays(1), day.zone, ui.style.primary);
        CalendarMonthGridView grid = new CalendarMonthGridView(getContext(), ui.style, lettersGapDp);
        grid.set(month, day.today, firstDayOfWeek(), marks, day.locale);
        return grid;
    }

    // ----- the month heading's row ----------------------------------------------------------

    /**
     * The month heading: the name at the start, the year on its baseline just before the
     * steppers, the two discs at the end. It fits by {@link CalendarWidgetFormats#fitHeading}:
     * the year goes first, then the discs close up, then the name shortens ("Sep"). A year left
     * out is laid out empty rather than hidden, so fitting never asks for another layout.
     */
    private static final class MonthHeading extends ViewGroup {
        @NonNull private final TextView name;
        @NonNull private final String fullName;
        @NonNull private final String shortName;
        @NonNull private final TextView year;
        @NonNull private final View prev;
        @NonNull private final View next;
        private final int side;
        private final int textGap;
        private final int disc;
        private final int gap;
        private final int minGap;
        /** How far a stepper's touch area reaches past its disc on every side. */
        private final int hitGrow;
        @NonNull private CalendarWidgetFormats.HeadingFit fit;

        MonthHeading(@NonNull BuiltinWidgetUi ui, @NonNull TextView name, @NonNull String shortName,
                     @NonNull TextView year, @NonNull View prev, @NonNull View next) {
            super(ui.context);
            this.name = name; this.year = year; this.prev = prev; this.next = next;
            this.fullName = name.getText().toString();
            this.shortName = shortName;
            side = ui.dp(HEADING_SIDE_DP);
            textGap = ui.dp(HEADING_GAP_DP);
            disc = ui.dp(STEPPER_DP);
            gap = ui.dp(STEPPER_GAP_DP);
            minGap = ui.dp(STEPPER_GAP_MIN_DP);
            hitGrow = Math.max(0, (ui.dp(STEPPER_HIT_DP) - disc) / 2);
            fit = new CalendarWidgetFormats.HeadingFit(true, gap, 0);
            addView(name);
            addView(year);
            addView(prev);
            addView(next);
        }

        /** The narrowest this row keeps the longest of the twelve month names whole in. */
        int narrowest(@NonNull Locale locale) {
            float longest = 0f;
            for (Month month : Month.values()) {
                longest = Math.max(longest,
                    name.getPaint().measureText(CalendarWidgetFormats.monthName(month, locale)));
            }
            return 2 * side + CalendarWidgetFormats.headingNeed((int) Math.ceil(longest), textGap,
                disc, minGap);
        }

        @Override protected void onMeasure(int widthSpec, int heightSpec) {
            // A row too narrow for the whole name beside the steppers shows the short one.
            if (MeasureSpec.getMode(widthSpec) != MeasureSpec.UNSPECIFIED) {
                int room = MeasureSpec.getSize(widthSpec) - 2 * side
                    - CalendarWidgetFormats.headingNeed(0, textGap, disc, minGap);
                String label = name.getPaint().measureText(fullName) <= room ? fullName : shortName;
                if (!label.contentEquals(name.getText())) name.setText(label);
            }
            int any = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED);
            name.measure(any, any);
            year.measure(any, any);
            int discSpec = MeasureSpec.makeMeasureSpec(disc, MeasureSpec.EXACTLY);
            prev.measure(discSpec, discSpec);
            next.measure(discSpec, discSpec);
            int nameWidth = name.getMeasuredWidth();
            int yearWidth = year.getMeasuredWidth();
            int want = 2 * side + nameWidth + textGap + yearWidth + textGap + 2 * disc + gap;
            int width = resolveSize(want, widthSpec);
            fit = CalendarWidgetFormats.fitHeading(width - 2 * side, nameWidth, yearWidth, textGap,
                textGap, disc, gap, minGap);
            if (fit.nameWidth < nameWidth) {
                name.measure(MeasureSpec.makeMeasureSpec(fit.nameWidth, MeasureSpec.EXACTLY), any);
            }
            int height = Math.max(disc, Math.max(name.getMeasuredHeight(), year.getMeasuredHeight()));
            setMeasuredDimension(width, resolveSize(height, heightSpec));
        }

        @Override protected void onLayout(boolean changed, int l, int t, int r, int b) {
            int width = r - l;
            int height = b - t;
            int nextStart = width - side - disc;
            int prevStart = nextStart - fit.stepperGap - disc;
            int discTop = (height - disc) / 2;
            place(next, nextStart, discTop, width);
            place(prev, prevStart, discTop, width);
            int nameTop = (height - name.getMeasuredHeight()) / 2;
            place(name, side, nameTop, width);
            if (fit.showYear) {
                int baseline = nameTop + name.getBaseline();
                place(year, prevStart - textGap - year.getMeasuredWidth(),
                    baseline - year.getBaseline(), width);
            } else {
                year.layout(0, 0, 0, 0);
            }
        }

        /** Lays {@code child} out at its measured size {@code start} in from the leading edge. */
        private void place(@NonNull View child, int start, int top, int width) {
            int childWidth = child.getMeasuredWidth();
            int left = isRtl() ? width - start - childWidth : start;
            child.layout(left, top, left + childWidth, top + child.getMeasuredHeight());
        }

        private boolean isRtl() { return getLayoutDirection() == LAYOUT_DIRECTION_RTL; }

        /**
         * The stepper whose touch area in {@code host} holds ({@code x}, {@code y}), with that
         * area in {@code out}; null for neither. The areas are the discs grown by {@link #hitGrow},
         * met halfway between the discs and kept off the text, within {@code host}.
         */
        @Nullable View stepperAt(@NonNull ViewGroup host, int x, int y, @NonNull Rect out) {
            if (getParent() == null || prev.getWidth() == 0) return null;
            boolean rtl = isRtl();
            View first = rtl ? next : prev;
            View second = rtl ? prev : next;
            View text = fit.showYear ? year : name;
            int[] spans = CalendarWidgetFormats.stepperSpans(first.getLeft(), second.getLeft(), disc,
                hitGrow, rtl ? text.getLeft() : text.getRight(), !rtl);
            for (int i = 0; i < 2; i++) {
                View stepper = i == 0 ? first : second;
                out.set(spans[2 * i], stepper.getTop() - hitGrow, spans[2 * i + 1],
                    stepper.getBottom() + hitGrow);
                try {
                    host.offsetDescendantRectToMyCoords(this, out);
                } catch (IllegalArgumentException notInHost) {
                    return null;
                }
                if (out.intersect(0, 0, host.getWidth(), host.getHeight()) && out.contains(x, y)) {
                    return stepper;
                }
            }
            return null;
        }
    }

    /**
     * Hands a touch near a month stepper to the stepper, so a near miss steps the month instead
     * of opening the calendar app. The areas are read from the laid-out heading at each touch,
     * so they follow it without being kept up to date.
     */
    private static final class StepperTouch extends TouchDelegate {
        @NonNull private final ViewGroup host;
        @NonNull private final MonthHeading heading;
        private final Rect area = new Rect();
        private final Rect slack = new Rect();
        private final int slop;
        @Nullable private View target;

        StepperTouch(@NonNull ViewGroup host, @NonNull MonthHeading heading) {
            super(new Rect(), heading);
            this.host = host;
            this.heading = heading;
            slop = ViewConfiguration.get(host.getContext()).getScaledTouchSlop();
        }

        @Override public boolean onTouchEvent(@NonNull MotionEvent event) {
            int x = (int) event.getX();
            int y = (int) event.getY();
            int action = event.getActionMasked();
            if (action == MotionEvent.ACTION_DOWN) target = heading.stepperAt(host, x, y, area);
            View stepper = target;
            if (stepper == null) return false;
            if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) target = null;
            // As the platform's delegate does: inside the area the stepper is touched at its
            // centre; a finger that slides off is moved outside it, so the press lets go.
            slack.set(area);
            slack.inset(-slop, -slop);
            if (slack.contains(x, y)) event.setLocation(stepper.getWidth() / 2f, stepper.getHeight() / 2f);
            else event.setLocation(-2f * slop, -2f * slop);
            return stepper.dispatchTouchEvent(event);
        }
    }
}
