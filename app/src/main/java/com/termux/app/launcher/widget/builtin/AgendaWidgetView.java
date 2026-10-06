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

import java.util.ArrayList;
import java.util.List;

/**
 * Today and what comes next: the date, the count of today's events and the next few, from the
 * calendar provider through the shared {@link CalendarEventsSource}. Without calendar access it
 * still shows the date and asks for access in place of the events; a tap then asks for it.
 *
 * <p>The minute tick redraws only when a label the user can see would change ("in 50 min"
 * becoming "in 49 min", an event ending); the source redraws on new events and on a new day.</p>
 */
public class AgendaWidgetView extends BuiltinWidgetView {
    @Nullable private FrameLayout frame;
    @Nullable private BuiltinWidgetUi ui;
    @Nullable private CalendarEventsSource.Snapshot shown;
    @NonNull private String shownSignature = "";

    private final CalendarEventsSource.Listener sourceListener = snapshot -> {
        if (isStarted()) populate();
    };
    private final BuiltinWidgetServices.TickListener tick = () -> {
        if (!isStarted()) return;
        if (!signature(CalendarWidgetSupport.dayFor(this, services)).equals(shownSignature)) {
            populate();
        }
    };

    public AgendaWidgetView(@NonNull Context context, @NonNull BuiltinWidgetServices services,
                 @NonNull BuiltinWidgetStyle style) {
        super(context, BuiltinWidgetKind.AGENDA, services, style);
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
        services.addTickListener(tick);
        if (source.snapshot() != shown) populate();
    }

    @Override protected void onStop() {
        CalendarEventsSource.of(services).unsubscribe(sourceListener);
        services.removeTickListener(tick);
    }

    @Override protected void onTap() {
        if (isPreview()) return;
        if (!CalendarEventsSource.hasPermission(getContext())) {
            services.host().requestCalendarPermission();
            return;
        }
        CalendarWidgetSupport.openCalendarAt(getContext(), System.currentTimeMillis());
    }

    // ----- drawing --------------------------------------------------------------------------

    /** Lays the current span out again with the events as they are now. */
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

    /** Everything on the card that moves with the clock; a tick that leaves it alone draws nothing. */
    @NonNull private static String signature(@NonNull CalendarWidgetSupport.Day day) {
        StringBuilder out = new StringBuilder();
        out.append(day.today).append('|').append(day.permitted).append(day.loaded)
            .append('|').append(day.todays.size());
        int shown = 0;
        for (CalendarEvent event : day.upcoming) {
            if (shown++ == 4) break;
            out.append('|').append(event.id).append('@').append(event.startMs)
                .append(day.slot(event));
        }
        if (day.next != null) out.append('|').append(day.relative(day.next));
        return out.toString();
    }

    @NonNull private String describe(@NonNull CalendarWidgetSupport.Day day) {
        Context context = getContext();
        String date = day.date("EEEEdMMMM");
        if (!day.permitted) {
            return context.getString(R.string.bw_calendar_cd_agenda, date,
                context.getString(R.string.bw_calendar_cd_access));
        }
        if (!day.loaded) return date;
        String state = day.countText(context);
        if (day.next != null) {
            state = context.getString(R.string.bw_calendar_cd_agenda, state,
                context.getString(R.string.bw_calendar_cd_next,
                    CalendarWidgetSupport.title(context, day.next), day.relative(day.next)));
        }
        return context.getString(R.string.bw_calendar_cd_agenda, date, state);
    }

    // ----- spans ----------------------------------------------------------------------------

    @NonNull private View oneByOne(@NonNull BuiltinWidgetUi ui, @NonNull CalendarWidgetSupport.Day day) {
        TextView weekday = weekday(ui, CalendarWidgetFormats.weekdayShortUpper(
            day.today.getDayOfWeek(), day.locale));
        TextView numeral = ui.numeral(Integer.toString(day.today.getDayOfMonth()), 36f);
        TextView line = !day.permitted ? CalendarWidgetSupport.allowLine(ui, 10.5f, true)
            : ui.mono(day.loaded ? day.countText(getContext()) : "", 10.5f);
        LinearLayout root = ui.column(2, weekday, numeral, line);
        root.setGravity(Gravity.CENTER);
        inset(root, 6, 0, 6, 0, ui);
        return root;
    }

    @NonNull private View twoByOne(@NonNull BuiltinWidgetUi ui, @NonNull CalendarWidgetSupport.Day day) {
        TextView heading = ui.text(getContext().getString(R.string.bw_calendar_next_heading,
            day.date("EEEdMMM")), 12f, ui.style.sansBold, ui.style.onSurfaceVariant);
        List<View> children = new ArrayList<>();
        children.add(CalendarWidgetSupport.wide(heading));
        View body = nextBlock(ui, day, false);
        if (body != null) children.add(CalendarWidgetSupport.wide(body));
        LinearLayout root = ui.column(6, children.toArray(new View[0]));
        root.setGravity(Gravity.CENTER_VERTICAL);
        inset(root, 14, 0, 14, 0, ui);
        return root;
    }

    @NonNull private View twoByTwo(@NonNull BuiltinWidgetUi ui, @NonNull CalendarWidgetSupport.Day day) {
        TextView numeral = ui.numeral(Integer.toString(day.today.getDayOfMonth()), 30f);
        TextView label = ui.text(getContext().getString(R.string.bw_calendar_weekday_month,
                CalendarWidgetFormats.weekdayShort(day.today.getDayOfWeek(), day.locale),
                CalendarWidgetFormats.monthShort(day.today.getMonth(), day.locale)),
            13f, ui.style.sansBold, ui.style.onSurfaceVariant);
        LinearLayout heading = CalendarWidgetSupport.baselineRow(ui, 8, numeral, BuiltinWidgetUi.flex(label));
        CalendarFitColumn list = new CalendarFitColumn(getContext(), ui.dp(9));
        if (!day.permitted) {
            list.addView(CalendarWidgetSupport.allowLine(ui, 12f, false));
        } else if (day.loaded) {
            int count = 0;
            for (CalendarEvent event : day.upcoming) {
                if (count++ == 3) break;
                list.addView(CalendarWidgetSupport.compactRow(ui, event, day.slot(event),
                    !isPreview()));
            }
            if (count == 0) list.addView(CalendarWidgetSupport.emptyLine(ui, day.emptyText(getContext()), 12f));
        }
        LinearLayout root = ui.column(10, CalendarWidgetSupport.wide(heading), BuiltinWidgetUi.flexTall(list));
        inset(root, 14, 14, 14, 14, ui);
        return root;
    }

    @NonNull private View fourByOne(@NonNull BuiltinWidgetUi ui, @NonNull CalendarWidgetSupport.Day day) {
        TextView weekday = weekday(ui, CalendarWidgetFormats.weekdayShortUpper(
            day.today.getDayOfWeek(), day.locale));
        TextView numeral = ui.numeral(Integer.toString(day.today.getDayOfMonth()), 34f);
        LinearLayout date = ui.column(2, weekday, numeral);
        date.setGravity(Gravity.CENTER_HORIZONTAL);
        BuiltinWidgetUi.size(date, ui.dp(52), ViewGroup.LayoutParams.WRAP_CONTENT);
        View divider = ui.divider(true);
        LinearLayout.LayoutParams dividerParams = (LinearLayout.LayoutParams) divider.getLayoutParams();
        dividerParams.topMargin = ui.dp(18);
        dividerParams.bottomMargin = ui.dp(18);
        View body = nextBlock(ui, day, true);
        if (body == null) body = new View(getContext());
        LinearLayout root = ui.row(16, date, divider, BuiltinWidgetUi.flex(body));
        inset(root, 16, 0, 16, 0, ui);
        return root;
    }

    @NonNull private View fourByTwo(@NonNull BuiltinWidgetUi ui, @NonNull CalendarWidgetSupport.Day day) {
        Context context = getContext();
        TextView weekday = weekday(ui, CalendarWidgetFormats.weekdayFullUpper(
            day.today.getDayOfWeek(), day.locale));
        TextView numeral = ui.numeral(Integer.toString(day.today.getDayOfMonth()), 52f);
        TextView month = ui.text(CalendarWidgetFormats.monthName(day.today.getMonth(), day.locale),
            12f, ui.style.sansBold, ui.style.onSurfaceVariant);
        TextView count = ui.mono(day.loaded
            ? context.getString(R.string.bw_calendar_today_count, day.todays.size()) : "", 10.5f);
        LinearLayout date = ui.column(2, weekday, numeral, month,
            BuiltinWidgetUi.flexTall(new View(context)), count);
        BuiltinWidgetUi.size(date, ui.dp(84), ViewGroup.LayoutParams.MATCH_PARENT);

        CalendarFitColumn list = new CalendarFitColumn(context, ui.dp(10));
        if (!day.permitted) {
            list.addView(CalendarWidgetSupport.allowLine(ui, 13f, false));
        } else if (day.loaded) {
            List<CalendarEvent> rows = new ArrayList<>();
            for (CalendarEvent event : day.upcoming) {
                if (rows.size() == 4) break;
                rows.add(event);
            }
            // One width for the time column, the widest label's, so the titles line up.
            int timeWidth = ui.dp(40);
            List<String> slots = new ArrayList<>();
            TextView probe = ui.mono("", 11.5f);
            for (CalendarEvent event : rows) {
                String slot = day.slot(event);
                slots.add(slot);
                timeWidth = Math.max(timeWidth,
                    (int) Math.ceil(probe.getPaint().measureText(slot)) + ui.dp(1));
            }
            for (int i = 0; i < rows.size(); i++) {
                list.addView(wideRow(ui, day, rows.get(i), slots.get(i), timeWidth));
            }
            if (rows.isEmpty()) list.addView(CalendarWidgetSupport.emptyLine(ui, day.emptyText(context), 13f));
        }
        list.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));
        LinearLayout root = ui.row(16, date, list);
        inset(root, 16, 16, 16, 16, ui);
        return root;
    }

    // ----- pieces ---------------------------------------------------------------------------

    /**
     * The next event as the 2×1 and 4×1 draw it: its time in the accent and its title, over a
     * line saying how far off it is and where (2×1) or what follows it (4×1). Null when there is
     * nothing yet to draw.
     */
    @Nullable private View nextBlock(@NonNull BuiltinWidgetUi ui,
                                     @NonNull CalendarWidgetSupport.Day day, boolean wide) {
        if (!day.permitted) return CalendarWidgetSupport.allowLine(ui, 13f, false);
        if (!day.loaded) return null;
        CalendarEvent next = day.next;
        if (next == null) return CalendarWidgetSupport.emptyLine(ui, day.emptyText(getContext()), 13f);
        Context context = getContext();
        String slot = day.slot(next);
        TextView time = ui.text(slot, 13f, ui.style.monoMedium, ui.style.primary);
        TextView title = ui.text(CalendarWidgetSupport.title(context, next), 14f, ui.style.sansBold,
            ui.style.onSurface);
        LinearLayout line = CalendarWidgetSupport.baselineRow(ui, 8, time, BuiltinWidgetUi.flex(title));
        String detail = wide ? following(context, day, next) : "";
        if (detail.isEmpty()) {
            detail = CalendarWidgetSupport.joined(context, day.relative(next), next.place());
        }
        TextView under = ui.text(detail, wide ? 11.5f : 11f, ui.style.sansMedium,
            ui.style.onSurfaceVariant);
        LinearLayout block = ui.column(wide ? 5 : 6, CalendarWidgetSupport.wide(line), CalendarWidgetSupport.wide(under));
        if (!isPreview()) CalendarWidgetSupport.makeEventRow(block, next, slot);
        return block;
    }

    /** "16:00 KU library meeting · 19:00 Gym": the two events after {@code next}. */
    @NonNull private static String following(@NonNull Context context,
                                             @NonNull CalendarWidgetSupport.Day day,
                                             @NonNull CalendarEvent next) {
        String out = "";
        int count = 0;
        for (CalendarEvent event : day.upcoming) {
            if (event == next) continue;
            if (count++ == 2) break;
            out = CalendarWidgetSupport.joined(context, out,
                day.slot(event) + " " + CalendarWidgetSupport.title(context, event));
        }
        return out;
    }

    /** A 4×2 row: time, dot, title over where it is (or, for a later day, when). */
    @NonNull private View wideRow(@NonNull BuiltinWidgetUi ui, @NonNull CalendarWidgetSupport.Day day,
                                  @NonNull CalendarEvent event, @NonNull String slot, int timeWidth) {
        Context context = getContext();
        TextView time = ui.mono(slot, 11.5f);
        BuiltinWidgetUi.size(time, timeWidth, ViewGroup.LayoutParams.WRAP_CONTENT);
        TextView title = ui.text(CalendarWidgetSupport.title(context, event), 13f,
            ui.style.sansBold, ui.style.onSurface);
        boolean today = CalendarWidgetFormats.overlaps(event, day.today, day.zone);
        String where = today ? event.place()
            : CalendarWidgetSupport.joined(context, day.relative(event), event.location);
        TextView place = ui.text(where, 11f, ui.style.sans, ui.style.onSurfaceVariant);
        if (where.isEmpty()) place.setVisibility(View.GONE);
        LinearLayout text = ui.column(0, title, place);
        LinearLayout row = ui.row(10, time,
            ui.dot(8, CalendarWidgetSupport.colorOf(event, ui.style.primary)),
            BuiltinWidgetUi.flex(text));
        if (!isPreview()) CalendarWidgetSupport.makeEventRow(row, event, slot);
        return row;
    }

    @NonNull private static TextView weekday(@NonNull BuiltinWidgetUi ui, @NonNull String text) {
        TextView view = ui.text(text, 11f, ui.style.sansBold, ui.style.primary);
        view.setLetterSpacing(0.08f);
        return view;
    }
}
