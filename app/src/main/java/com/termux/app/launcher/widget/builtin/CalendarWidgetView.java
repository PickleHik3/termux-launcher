package com.termux.app.launcher.widget.builtin;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.view.View;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.core.graphics.ColorUtils;

import com.termux.R;
import com.termux.app.launcher.widget.builtin.WidgetLayoutPlan.Part;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * The Calendar (ticket #114, spec #101): one widget for the old Agenda and Calendar month, events
 * first at every span. The weekday, the date and a dot per event of today at 1×1; the next event
 * at 2×1; "Today" with three events in time chips at 2×2, or the month grid when its gear says
 * so; the next event over the fortnight at 4×1; the month beside "Next up" at 4×2. With nothing
 * left today it says "Nothing else today" and shows tomorrow's first event. Without calendar
 * access the card asks for it, and a tap anywhere on it asks. The old {@code agenda} and
 * {@code calendar.month} open as it, on the events and on the month ({@link BuiltinWidgetAliases}).
 *
 * <p>The layout is {@link CalendarLayoutPlanner}'s plan: its text is drawn by the plan view, the
 * dots, disc, chips and grids are views laid out under it in their FACE boxes, and its TOUCH boxes
 * get transparent targets over it. The minute tick redraws only when a label the user can see
 * would change; the source redraws on new events and on a new day.</p>
 */
public class CalendarWidgetView extends BuiltinWidgetView {
    @Nullable private CalendarEventsSource.Snapshot shown;
    @NonNull private String shownSignature = "";
    /** The month the grid was stepped to, or null to follow today's. */
    @Nullable private YearMonth anchor;
    /** The month the grid was last drawn for (null: no grid), and its read (null: the window's). */
    @Nullable private YearMonth drawnMonth;
    @Nullable private CalendarEventsSource.MonthEvents drawnMonthEvents;
    /** Whether the last drawing had calendar access, for the tap. */
    private boolean permitted = true;
    @Nullable private CalendarCard card;

    private final CalendarEventsSource.Listener sourceListener = new CalendarEventsSource.Listener() {
        @Override public void onCalendarEvents(@NonNull CalendarEventsSource.Snapshot snapshot) {
            if (isStarted()) populate();
        }

        @Override public void onCalendarMonth(@NonNull YearMonth month) {
            // The grid draws from the kept read from now on, so this repopulates once per read.
            if (isStarted() && month.equals(drawnMonth)) populate();
        }
    };
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

    // ----- settings -------------------------------------------------------------------------

    /** The gear: what the 2×2 shows, today's events or the month. */
    @NonNull @Override public List<ConfigField> configFields() {
        Context context = getContext();
        List<ConfigField.Option> views = new ArrayList<>();
        views.add(new ConfigField.Option(CalendarWidgetSupport.VIEW_EVENTS,
            context.getString(R.string.bw_calendar_view_events)));
        views.add(new ConfigField.Option(CalendarWidgetSupport.VIEW_MONTH,
            context.getString(R.string.bw_calendar_view_month)));
        return Collections.singletonList(ConfigField.choice(CalendarWidgetSupport.KEY_VIEW,
            context.getString(R.string.bw_calendar_view), monthView()
                ? CalendarWidgetSupport.VIEW_MONTH : CalendarWidgetSupport.VIEW_EVENTS,
            views, null));
    }

    /** Whether the 2×2 shows the month. */
    boolean monthView() { return CalendarWidgetSupport.monthView(config()); }

    // ----- lifecycle ------------------------------------------------------------------------

    /** The plan is laid out at the card's own size, so the card is never zoomed to fill. */
    @Override protected boolean zoomsToFill(@NonNull BuiltinWidgetSpan span) { return false; }

    @Override protected void onBuild(@NonNull FrameLayout frame, @NonNull BuiltinWidgetSpan span,
                                     @NonNull BuiltinWidgetUi ui) {
        populate();
    }

    @Override protected void onStart() {
        CalendarEventsSource source = CalendarEventsSource.of(services);
        source.subscribe(services, sourceListener);
        services.addTickListener(tick);
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

    /** A tap on the card asks for calendar access while it is off, else opens the calendar. */
    @Override protected void onTap() {
        if (isPreview()) return;
        if (!permitted || !CalendarEventsSource.hasPermission(getContext())) {
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

    private void stepMonth(int step) {
        anchor = CalendarWidgetFormats.stepMonth(anchor, LocalDate.now(ZoneId.systemDefault()), step);
        populate();
    }

    // ----- drawing --------------------------------------------------------------------------

    /** Plans the current span again with the events as they are now. */
    private void populate() {
        FrameLayout frame = content();
        CalendarWidgetSupport.Day day = CalendarWidgetSupport.dayFor(this, services);
        BuiltinWidgetSpan span = span();
        permitted = day.permitted;
        drawnMonth = null;
        drawnMonthEvents = null;
        Drawing drawing = drawing(day, span);
        frame.removeAllViews();
        float fontScale = getResources().getConfiguration().fontScale;
        CalendarLayoutPlanner.Words words = words();
        CalendarCard next = new CalendarCard(getContext(), drawing, (widthDp, heightDp, measure) ->
            CalendarLayoutPlanner.plan(span, widthDp, heightDp, fontScale, drawing.content, words,
                measure));
        card = next;
        frame.addView(next, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT));
        shown = isPreview() ? null : CalendarEventsSource.of(services).snapshot();
        shownSignature = signature(day);
        setContentDescription(describe(day, drawing));
    }

    /** What one drawing holds: the planner's content and the events and marks behind it. */
    private static final class Drawing {
        @NonNull final CalendarWidgetSupport.Day day;
        @NonNull final CalendarLayoutPlanner.Content content;
        @NonNull final List<CalendarEvent> today;
        @Nullable final CalendarEvent tomorrow;
        @NonNull final YearMonth month;
        @NonNull final DayOfWeek first;
        @NonNull final Map<LocalDate, Integer> monthMarks;
        @NonNull final LocalDate stripStart;
        @NonNull final Map<LocalDate, Integer> stripMarks;
        @NonNull final String[] names;
        @NonNull final String[] letters;

        Drawing(@NonNull CalendarWidgetSupport.Day day,
                @NonNull CalendarLayoutPlanner.Content content, @NonNull List<CalendarEvent> today,
                @Nullable CalendarEvent tomorrow, @NonNull YearMonth month, @NonNull DayOfWeek first,
                @NonNull Map<LocalDate, Integer> monthMarks, @NonNull LocalDate stripStart,
                @NonNull Map<LocalDate, Integer> stripMarks, @NonNull String[] names,
                @NonNull String[] letters) {
            this.day = day; this.content = content; this.today = today; this.tomorrow = tomorrow;
            this.month = month; this.first = first;
            this.monthMarks = monthMarks; this.stripStart = stripStart;
            this.stripMarks = stripMarks; this.names = names; this.letters = letters;
        }

        @Nullable CalendarEvent lead() {
            return content.lead < 0 ? null : today.get(content.lead);
        }
    }

    @NonNull private Drawing drawing(@NonNull CalendarWidgetSupport.Day day,
                                     @NonNull BuiltinWidgetSpan span) {
        Context context = getContext();
        List<CalendarEvent> remaining = day.remaining;
        List<CalendarLayoutPlanner.Event> today = new ArrayList<>();
        for (CalendarEvent event : remaining) {
            today.add(new CalendarLayoutPlanner.Event(day.slot(event),
                CalendarWidgetSupport.title(context, event), day.relative(event)));
        }
        CalendarEvent next = CalendarWidgetFormats.next(remaining, day.now, day.zone);
        int lead = next == null ? -1 : remaining.indexOf(next);
        CalendarEvent tomorrowFirst = remaining.isEmpty() ? day.tomorrowFirst : null;
        CalendarLayoutPlanner.Event tomorrow = tomorrowFirst == null ? null
            : new CalendarLayoutPlanner.Event(day.clock(tomorrowFirst),
                CalendarWidgetSupport.title(context, tomorrowFirst), "");
        List<CalendarEvent> dots = day.todays;
        int leadDot = next == null ? -1 : dots.indexOf(next);

        DayOfWeek first = firstDayOfWeek();
        YearMonth month = CalendarWidgetFormats.shownMonth(anchor, day.today);
        String[] names = new String[7];
        String[] letters = new String[7];
        for (int i = 0; i < 7; i++) {
            names[i] = CalendarWidgetFormats.weekdayShort(first.plus(i), day.locale)
                .replace(".", "");
            letters[i] = CalendarWidgetFormats.weekdayLetter(first.plus(i), day.locale);
        }
        int blanks = CalendarWidgetFormats.leadingBlanks(month, first);
        int todayCell = YearMonth.from(day.today).equals(month)
            ? blanks + day.today.getDayOfMonth() - 1 : -1;
        CalendarLayoutPlanner.MonthShape shape = new CalendarLayoutPlanner.MonthShape(
            CalendarWidgetSupport.date(month.atDay(1), "yMMMM", day.locale, day.zone),
            CalendarWidgetSupport.date(month.atDay(1), "yMMM", day.locale, day.zone),
            blanks, month.lengthOfMonth(), CalendarWidgetFormats.monthRows(month, first),
            todayCell, anchor != null, names, letters);

        LocalDate stripStart = CalendarWidgetFormats.weekStart(day.today, first);
        String[] stripLetters = new String[14];
        for (int i = 0; i < 14; i++) {
            stripLetters[i] = CalendarWidgetFormats.weekdayLetter(
                stripStart.plusDays(i).getDayOfWeek(), day.locale);
        }
        boolean monthView = monthView();
        boolean drawsMonth = span == BuiltinWidgetSpan.FOUR_BY_TWO
            || (span == BuiltinWidgetSpan.TWO_BY_TWO && monthView);
        Map<LocalDate, Integer> monthMarks = Collections.emptyMap();
        Map<LocalDate, Integer> stripMarks = Collections.emptyMap();
        int accent = style().primary;
        if (drawsMonth) {
            // The whole month's read once it lands; until then the rolling window, so today's
            // month never draws bare while it is read.
            CalendarEventsSource.MonthEvents read = isPreview() || !day.permitted ? null
                : CalendarEventsSource.of(services).month(month);
            drawnMonth = month;
            drawnMonthEvents = read;
            List<CalendarEvent> events = read != null ? read.events : day.events;
            monthMarks = CalendarWidgetFormats.dayColors(events, month.atDay(1),
                month.atEndOfMonth().plusDays(1), day.zone, accent);
        }
        if (span == BuiltinWidgetSpan.FOUR_BY_ONE) {
            stripMarks = CalendarWidgetFormats.dayColors(day.events, stripStart,
                stripStart.plusDays(14), day.zone, accent);
        }
        CalendarLayoutPlanner.Content content = new CalendarLayoutPlanner.Content(day.permitted,
            day.loaded, CalendarWidgetFormats.weekdayShort(day.today.getDayOfWeek(), day.locale),
            Integer.toString(day.today.getDayOfMonth()), today, lead, dots.size(), leadDot,
            tomorrow, monthView, shape, stripLetters);
        return new Drawing(day, content, remaining, tomorrowFirst, month, first, monthMarks,
            stripStart, stripMarks, names, letters);
    }

    /** Everything on the card that moves with the clock; a tick that leaves it alone draws nothing. */
    @NonNull private String signature(@NonNull CalendarWidgetSupport.Day day) {
        boolean relative = span() == BuiltinWidgetSpan.TWO_BY_ONE
            || span() == BuiltinWidgetSpan.FOUR_BY_ONE || span() == BuiltinWidgetSpan.FOUR_BY_TWO;
        StringBuilder out = new StringBuilder();
        out.append(day.today).append('|').append(day.permitted).append(day.loaded)
            .append('|').append(day.todays.size());
        int count = 0;
        for (CalendarEvent event : day.remaining) {
            if (count++ == CalendarLayoutPlanner.ROWS_MAX) break;
            out.append('|').append(event.id).append('@').append(event.startMs)
                .append(day.slot(event));
            if (relative) out.append(day.relative(event));
        }
        if (day.remaining.isEmpty() && day.tomorrowFirst != null) {
            out.append("|t").append(day.tomorrowFirst.id).append('@')
                .append(day.tomorrowFirst.startMs);
        }
        return out.toString();
    }

    @NonNull private String describe(@NonNull CalendarWidgetSupport.Day day,
                                     @NonNull Drawing drawing) {
        Context context = getContext();
        String date = day.date("EEEEdMMMM");
        if (!day.permitted) {
            return context.getString(R.string.bw_calendar_cd_agenda, date,
                context.getString(R.string.bw_calendar_cd_access));
        }
        if (!day.loaded) return date;
        String state = day.countText(context);
        CalendarEvent lead = drawing.lead();
        if (lead != null) {
            state = context.getString(R.string.bw_calendar_cd_agenda, state,
                context.getString(R.string.bw_calendar_cd_next,
                    CalendarWidgetSupport.title(context, lead), day.relative(lead)));
        } else if (drawing.tomorrow != null) {
            state = context.getString(R.string.bw_calendar_cd_agenda,
                context.getString(R.string.bw_calendar_nothing_else_today),
                context.getString(R.string.bw_calendar_cd_next,
                    CalendarWidgetSupport.title(context, drawing.tomorrow),
                    day.relative(drawing.tomorrow)));
        }
        return context.getString(R.string.bw_calendar_cd_agenda, date, state);
    }

    @NonNull private static DayOfWeek firstDayOfWeek() {
        return CalendarWidgetFormats.firstDayOfWeek(java.util.Calendar.getInstance().getFirstDayOfWeek());
    }

    @NonNull private CalendarLayoutPlanner.Words words() {
        Context context = getContext();
        return new CalendarLayoutPlanner.Words() {
            @NonNull @Override public String today() {
                return context.getString(R.string.bw_calendar_today);
            }
            @NonNull @Override public String nextUp() {
                return context.getString(R.string.bw_calendar_next_up);
            }
            @NonNull @Override public String nothingElseToday() {
                return context.getString(R.string.bw_calendar_nothing_else_today);
            }
            @NonNull @Override public String tomorrow() {
                return context.getString(R.string.bw_calendar_tomorrow);
            }
            @NonNull @Override public String allowAccess() {
                return context.getString(R.string.bw_calendar_allow_access);
            }
            @NonNull @Override public String allowShort() {
                return context.getString(R.string.bw_calendar_allow_short);
            }
            @NonNull @Override public String joined(@NonNull String first, @NonNull String second) {
                return CalendarWidgetSupport.joined(context, first, second);
            }
        };
    }

    // ----- the plan's views -----------------------------------------------------------------

    /** The view drawn under the text in a FACE part's box, or null for none. */
    @Nullable private View face(@NonNull Part part, @NonNull Drawing drawing,
                                @NonNull CalendarLayoutPlanner.Result result) {
        BuiltinWidgetStyle style = style();
        String id = part.id;
        if (id.startsWith(CalendarLayoutPlanner.DOT)) {
            int index = index(id, 1);
            boolean lead = index >= 0 && index == drawing.content.leadDot;
            return shape(GradientDrawable.OVAL, lead ? style.primary : style.outline, 0f);
        }
        if (id.equals(CalendarLayoutPlanner.NEXT_DISC)) {
            return shape(GradientDrawable.OVAL, style.primaryContainer, 0f);
        }
        if (id.endsWith(".chip")) {
            return shape(GradientDrawable.RECTANGLE, style.primaryContainer,
                part.box.height * style.density / 2f);
        }
        if (id.equals(CalendarLayoutPlanner.MONTH) && result.month != null) {
            CalendarMonthGridView grid = new CalendarMonthGridView(getContext(), style);
            grid.set(result.month, drawing.month, drawing.day.today, drawing.first,
                drawing.monthMarks, drawing.names, drawing.letters);
            return grid;
        }
        if (id.equals(CalendarLayoutPlanner.STRIP) && result.strip != null) {
            CalendarWeekStripView strip = ui().followCorner(
                new CalendarWeekStripView(getContext(), style, corner()));
            strip.set(result.strip, drawing.stripStart, drawing.day.today,
                drawing.content.permitted ? drawing.stripMarks : null,
                drawing.content.stripLetters);
            return strip;
        }
        return null;
    }

    @NonNull private View shape(int shape, int color, float radiusPx) {
        GradientDrawable fill = new GradientDrawable();
        fill.setShape(shape);
        fill.setColor(color);
        if (radiusPx > 0f) fill.setCornerRadius(radiusPx);
        View view = new View(getContext());
        view.setBackground(fill);
        view.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        return view;
    }

    /** The transparent, rippling, accessible target over a TOUCH part, or null for none. */
    @Nullable private View target(@NonNull Part part, @NonNull Drawing drawing) {
        if (isPreview()) return null;
        Context context = getContext();
        String id = part.id;
        CalendarEvent event = null;
        Runnable action = null;
        String description = null;
        if (id.equals(CalendarLayoutPlanner.ALLOW_TOUCH)) {
            action = () -> services.host().requestCalendarPermission();
            description = context.getString(R.string.bw_calendar_allow_access);
        } else if (id.equals(CalendarLayoutPlanner.STEP_PREV + ".touch")) {
            action = () -> stepMonth(-1);
            description = context.getString(R.string.bw_calendar_previous_month);
        } else if (id.equals(CalendarLayoutPlanner.STEP_NEXT + ".touch")) {
            action = () -> stepMonth(1);
            description = context.getString(R.string.bw_calendar_next_month);
        } else if (id.equals(CalendarLayoutPlanner.MONTH_TITLE_TOUCH)) {
            action = () -> {
                anchor = null;
                populate();
            };
            description = context.getString(R.string.bw_calendar_this_month);
        } else if (id.equals(CalendarLayoutPlanner.NEXT_TOUCH)) {
            event = drawing.lead() != null ? drawing.lead() : drawing.tomorrow;
        } else if (id.equals(CalendarLayoutPlanner.TOMORROW + "touch")) {
            event = drawing.tomorrow;
        } else if (id.startsWith(CalendarLayoutPlanner.ROW) && id.endsWith(".touch")) {
            int index = index(id, 1);
            if (index >= 0 && index < drawing.today.size()) event = drawing.today.get(index);
        }
        View view = new View(context);
        if (event != null) {
            CalendarWidgetSupport.makeEventRow(view, event, drawing.day.slot(event));
        } else if (action != null) {
            Runnable run = action;
            view.setOnClickListener(v -> run.run());
            view.setContentDescription(description);
            view.setClickable(true);
            view.setFocusable(true);
        } else {
            return null;
        }
        view.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
        GradientDrawable mask = ui().rounded(0xFFFFFFFF, style().dp(8));
        view.setForeground(null);
        view.setBackground(new RippleDrawable(ColorStateList.valueOf(
            ColorUtils.setAlphaComponent(style().onSurface, 0x1F)), null, mask));
        return view;
    }

    /** The number at dot-separated position {@code at} of {@code id}, or -1. */
    private static int index(@NonNull String id, int at) {
        String[] pieces = id.split("\\.");
        if (pieces.length <= at) return -1;
        try {
            return Integer.parseInt(pieces[at]);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    // ----- for tests ------------------------------------------------------------------------

    /** The plan on screen, null before the first layout. */
    @VisibleForTesting @Nullable WidgetLayoutPlan plan() {
        CalendarCard card = this.card;
        return card == null || card.latest == null ? null : card.latest.plan;
    }

    /**
     * The card: the plan view over the whole card, the FACE parts' views under it and the TOUCH
     * parts' targets over it, each laid out in its part's box once the plan is made at the card's
     * size.
     */
    private final class CalendarCard extends FrameLayout {
        @NonNull private final WidgetPlanView planView;
        @NonNull private final Drawing drawing;
        @Nullable CalendarLayoutPlanner.Result latest;
        @Nullable private CalendarLayoutPlanner.Result synced;
        private final List<View> placed = new ArrayList<>();
        private final List<Part> placedParts = new ArrayList<>();

        CalendarCard(@NonNull Context context, @NonNull Drawing drawing,
                     @NonNull ResultPlanner planner) {
            super(context);
            this.drawing = drawing;
            planView = new WidgetPlanView(context, style(), (w, h, measure) -> {
                CalendarLayoutPlanner.Result result = planner.plan(w, h, measure);
                latest = result;
                return result.plan;
            });
            addView(planView, new LayoutParams(LayoutParams.MATCH_PARENT,
                LayoutParams.MATCH_PARENT));
        }

        @Override protected void onLayout(boolean changed, int left, int top, int right,
                                          int bottom) {
            int width = right - left, height = bottom - top;
            planView.layout(0, 0, width, height);
            CalendarLayoutPlanner.Result result = latest;
            if (result == null) return;
            if (result != synced) {
                synced = result;
                for (View view : placed) removeViewInLayout(view);
                placed.clear();
                placedParts.clear();
                int under = 0;
                for (Part part : result.plan.parts) {
                    View view;
                    int at;
                    if (part.type == WidgetLayoutPlan.Type.FACE) {
                        view = face(part, drawing, result);
                        at = under;
                    } else if (part.type == WidgetLayoutPlan.Type.TOUCH) {
                        view = target(part, drawing);
                        at = -1;
                    } else {
                        continue;
                    }
                    if (view == null) continue;
                    addViewInLayout(view, at, new LayoutParams(0, 0), true);
                    if (at >= 0) under++;
                    placed.add(view);
                    placedParts.add(part);
                }
            }
            float density = getResources().getDisplayMetrics().density;
            boolean rtl = getLayoutDirection() == LAYOUT_DIRECTION_RTL;
            for (int i = 0; i < placed.size(); i++) {
                WidgetLayoutPlan.Box box = placedParts.get(i).box;
                int l = clamp(Math.round(box.left * density), width);
                int r = clamp(Math.round(box.right() * density), width);
                int t = clamp(Math.round(box.top * density), height);
                int b = clamp(Math.round(box.bottom() * density), height);
                if (rtl) {
                    int mirrored = width - r;
                    r = width - l;
                    l = mirrored;
                }
                View view = placed.get(i);
                view.measure(MeasureSpec.makeMeasureSpec(r - l, MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(b - t, MeasureSpec.EXACTLY));
                view.layout(l, t, r, b);
            }
        }

        private int clamp(int value, int max) { return Math.max(0, Math.min(max, value)); }
    }

    /** Plans the card at a size. */
    private interface ResultPlanner {
        @NonNull CalendarLayoutPlanner.Result plan(float widthDp, float heightDp,
                                                   @NonNull WidgetLayoutPlan.Measure measure);
    }
}
