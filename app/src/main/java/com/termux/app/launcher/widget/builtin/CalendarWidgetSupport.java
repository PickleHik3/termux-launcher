package com.termux.app.launcher.widget.builtin;

import android.content.ActivityNotFoundException;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.provider.CalendarContract;
import android.text.format.DateFormat;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.R;

import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** What the agenda and calendar widgets share beyond the events source: words, intents, rows. */
final class CalendarWidgetSupport {
    private CalendarWidgetSupport() { }

    /**
     * One moment of the calendar as a widget draws it: the day, the events that touch it, the
     * ones still to come and the one to lead with, and the labels that go beside them.
     */
    static final class Day {
        final long now;
        @NonNull final ZoneId zone;
        @NonNull final Locale locale;
        @NonNull final LocalDate today;
        final boolean permitted;
        final boolean loaded;
        @NonNull final List<CalendarEvent> events;
        @NonNull final List<CalendarEvent> todays;
        @NonNull final List<CalendarEvent> upcoming;
        @Nullable final CalendarEvent next;
        private final boolean twentyFourHour;
        @NonNull private final CalendarWidgetFormats.Words words;

        Day(@NonNull Context context, @NonNull CalendarEventsSource.Snapshot snapshot, long now,
            boolean permitted) {
            this.now = now;
            zone = ZoneId.systemDefault();
            locale = Locale.getDefault();
            today = CalendarWidgetFormats.day(now, zone);
            this.permitted = permitted;
            loaded = snapshot.loaded && permitted;
            events = permitted ? snapshot.events : Collections.emptyList();
            todays = CalendarWidgetFormats.onDay(events, today, zone);
            upcoming = CalendarWidgetFormats.upcoming(events, now);
            next = CalendarWidgetFormats.next(events, now, zone);
            twentyFourHour = is24Hour(context);
            words = words(context);
        }

        @NonNull String slot(@NonNull CalendarEvent event) {
            return CalendarWidgetFormats.slot(event, now, zone, twentyFourHour, locale, words);
        }

        @NonNull String relative(@NonNull CalendarEvent event) {
            return CalendarWidgetFormats.relative(event, now, zone, twentyFourHour, locale, words);
        }

        @NonNull String date(@NonNull String skeleton) {
            return CalendarWidgetSupport.date(today, skeleton, locale, zone);
        }

        /** "No events today", or "Nothing else today" once today's are over. */
        @NonNull String emptyText(@NonNull Context context) {
            return context.getString(todays.isEmpty() ? R.string.bw_calendar_no_events_today
                : R.string.bw_calendar_nothing_else_today);
        }

        /** "3 events" / "No events" for today. */
        @NonNull String countText(@NonNull Context context) {
            if (todays.isEmpty()) return context.getString(R.string.bw_calendar_no_events);
            return context.getResources().getQuantityString(R.plurals.bw_calendar_events_count,
                todays.size(), todays.size());
        }
    }

    /** The calendar as {@code view} should draw it now: live, or the picker's sample day. */
    @NonNull static Day dayFor(@NonNull BuiltinWidgetView view,
                               @NonNull BuiltinWidgetServices services) {
        Context context = view.getContext();
        if (view.isPreview()) {
            ZoneId zone = ZoneId.systemDefault();
            return new Day(context, preview(context, view.style(), zone), previewNow(zone), true);
        }
        CalendarEventsSource.Snapshot snapshot = CalendarEventsSource.of(services).snapshot();
        boolean permitted = snapshot.loaded ? snapshot.permitted
            : CalendarEventsSource.hasPermission(context);
        return new Day(context, snapshot, System.currentTimeMillis(), permitted);
    }

    @NonNull static CalendarWidgetFormats.Words words(@NonNull Context context) {
        return new CalendarWidgetFormats.Words(
            context.getString(R.string.bw_calendar_now),
            context.getString(R.string.bw_calendar_in_minutes),
            context.getString(R.string.bw_calendar_in_hours),
            context.getString(R.string.bw_calendar_tomorrow),
            context.getString(R.string.bw_calendar_all_day));
    }

    static boolean is24Hour(@NonNull Context context) { return DateFormat.is24HourFormat(context); }

    /** {@code day} in the locale's own order for the ICU {@code skeleton}, e.g. "EEEdMMM". */
    @NonNull static String date(@NonNull LocalDate day, @NonNull String skeleton,
                                @NonNull Locale locale, @NonNull ZoneId zone) {
        String pattern = DateFormat.getBestDateTimePattern(locale, skeleton);
        long millis = CalendarWidgetFormats.startOfDay(day, zone);
        return new SimpleDateFormat(pattern, locale).format(new Date(millis));
    }

    /** {@code event}'s title, or the calendar's placeholder for an untitled one. */
    @NonNull static String title(@NonNull Context context, @NonNull CalendarEvent event) {
        return event.title.isEmpty() ? context.getString(R.string.bw_calendar_untitled) : event.title;
    }

    /** {@code first · second}, or whichever of the two is not empty. */
    @NonNull static String joined(@NonNull Context context, @NonNull String first,
                                  @NonNull String second) {
        if (first.isEmpty()) return second;
        if (second.isEmpty()) return first;
        return context.getString(R.string.bw_calendar_joined, first, second);
    }

    /** {@code event}'s colour, or {@code fallback} when the calendar gives it none. */
    @ColorInt static int colorOf(@NonNull CalendarEvent event, @ColorInt int fallback) {
        return event.color != 0 ? event.color : fallback;
    }

    // ----- preview --------------------------------------------------------------------------

    /** The picker's clock: today at 13:40, the moment the design draws. */
    static long previewNow(@NonNull ZoneId zone) {
        return LocalDate.now(zone).atTime(LocalTime.of(13, 40)).atZone(zone).toInstant()
            .toEpochMilli();
    }

    /** The design's sample day, set on today: three events this afternoon, more this fortnight. */
    @NonNull static CalendarEventsSource.Snapshot preview(@NonNull Context context,
                                                         @NonNull BuiltinWidgetStyle style,
                                                         @NonNull ZoneId zone) {
        LocalDate today = LocalDate.now(zone);
        List<CalendarEvent> events = new ArrayList<>();
        events.add(sample(context, -1, today, 14, 30, R.string.bw_calendar_sample_standup,
            R.string.bw_calendar_sample_standup_place, style.primary, zone));
        events.add(sample(context, -2, today, 16, 0, R.string.bw_calendar_sample_library,
            R.string.bw_calendar_sample_library_place, style.warm, zone));
        events.add(sample(context, -3, today, 19, 0, R.string.bw_calendar_sample_gym,
            R.string.bw_calendar_sample_gym_place, style.done, zone));
        events.add(sample(context, -4, today.plusDays(1), 10, 0, R.string.bw_calendar_sample_dentist,
            0, style.onTertiaryContainer, zone));
        events.add(sample(context, -5, today.plusDays(3), 16, 0, R.string.bw_calendar_sample_library,
            R.string.bw_calendar_sample_library_place, style.warm, zone));
        events.add(sample(context, -6, today.plusDays(7), 19, 0, R.string.bw_calendar_sample_gym,
            R.string.bw_calendar_sample_gym_place, style.done, zone));
        Collections.sort(events, CalendarEvent.ORDER);
        return new CalendarEventsSource.Snapshot(true, true, events);
    }

    @NonNull private static CalendarEvent sample(@NonNull Context context, long id,
                                                 @NonNull LocalDate day, int hour, int minute,
                                                 int title, int place, @ColorInt int color,
                                                 @NonNull ZoneId zone) {
        long begin = day.atTime(hour, minute).atZone(zone).toInstant().toEpochMilli();
        return CalendarEvent.of(id, begin, begin + 60 * 60_000L, false, context.getString(title),
            place == 0 ? "" : context.getString(place), "", color, zone);
    }

    // ----- intents --------------------------------------------------------------------------

    /** The calendar app, opened on {@code millis}. */
    static void openCalendarAt(@NonNull Context context, long millis) {
        Uri.Builder uri = CalendarContract.CONTENT_URI.buildUpon().appendPath("time");
        ContentUris.appendId(uri, millis);
        start(context, new Intent(Intent.ACTION_VIEW).setData(uri.build()));
    }

    /** The calendar app, opened on {@code event}; the picker's samples open the day instead. */
    static void openEvent(@NonNull Context context, @NonNull CalendarEvent event) {
        if (event.id < 0) {
            openCalendarAt(context, event.startMs);
            return;
        }
        Intent intent = new Intent(Intent.ACTION_VIEW)
            .setData(ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, event.id))
            .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, event.rawBegin)
            .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, event.rawEnd)
            .putExtra(CalendarContract.EXTRA_EVENT_ALL_DAY, event.allDay);
        start(context, intent);
    }

    private static void start(@NonNull Context context, @NonNull Intent intent) {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            context.startActivity(intent);
        } catch (ActivityNotFoundException | SecurityException ignored) {
            // No calendar app: the widget stays as it is.
        }
    }

    // ----- views ----------------------------------------------------------------------------

    /** Gives {@code view} the platform's pressed ripple, when the theme has one. */
    static void pressable(@NonNull View view) {
        TypedValue value = new TypedValue();
        try {
            if (view.getContext().getTheme().resolveAttribute(
                    android.R.attr.selectableItemBackground, value, true) && value.resourceId != 0) {
                Drawable ripple = androidx.appcompat.content.res.AppCompatResources.getDrawable(
                    view.getContext(), value.resourceId);
                view.setForeground(ripple);
            }
        } catch (RuntimeException ignored) {
            // A theme without the attribute: the row still opens, just without the ripple.
        }
    }

    /** The "Allow calendar access" line, in the accent so it reads as the thing to tap. */
    @NonNull static TextView allowLine(@NonNull BuiltinWidgetUi ui, float sp, boolean shortForm) {
        return ui.text(ui.context.getString(shortForm ? R.string.bw_calendar_allow_short
            : R.string.bw_calendar_allow_access), sp, ui.style.sansBold, ui.style.primary);
    }

    /** A quiet line saying there is nothing to show. */
    @NonNull static TextView emptyLine(@NonNull BuiltinWidgetUi ui, @NonNull CharSequence text,
                                       float sp) {
        return ui.text(text, sp, ui.style.sansMedium, ui.style.onSurfaceVariant);
    }

    /**
     * The compact event row both widgets list: an 8dp dot in the event's colour, the title in
     * 12sp bold over its time in 10.5sp mono. Tapping it opens the event.
     */
    @NonNull static LinearLayout compactRow(@NonNull BuiltinWidgetUi ui, @NonNull CalendarEvent event,
                                            @NonNull String time, boolean interactive) {
        TextView title = ui.text(title(ui.context, event), 12f, ui.style.sansBold, ui.style.onSurface);
        TextView when = ui.mono(time, 10.5f);
        LinearLayout text = ui.column(0, title, when);
        LinearLayout row = ui.row(8, ui.dot(8, colorOf(event, ui.style.primary)),
            BuiltinWidgetUi.flex(text));
        if (interactive) makeEventRow(row, event, time);
        return row;
    }

    /** Makes {@code row} open {@code event} on tap and say so to a screen reader. */
    static void makeEventRow(@NonNull View row, @NonNull CalendarEvent event, @NonNull String time) {
        Context context = row.getContext();
        row.setContentDescription(context.getString(R.string.bw_calendar_cd_open_event,
            title(context, event), time));
        row.setClickable(true);
        row.setFocusable(true);
        row.setOnClickListener(v -> openEvent(v.getContext(), event));
        pressable(row);
    }

    /** A row whose children share a text baseline, the design's {@code align-items:baseline}. */
    @NonNull static LinearLayout baselineRow(@NonNull BuiltinWidgetUi ui, int gapDp,
                                             @NonNull View... children) {
        LinearLayout row = ui.row(gapDp, children);
        row.setGravity(Gravity.START);
        row.setBaselineAligned(true);
        return row;
    }

    /** Stretches {@code view} across its column. */
    @NonNull static <V extends View> V wide(@NonNull V view) {
        ViewGroup.LayoutParams existing = view.getLayoutParams();
        int height = existing == null ? ViewGroup.LayoutParams.WRAP_CONTENT : existing.height;
        return BuiltinWidgetUi.size(view, ViewGroup.LayoutParams.MATCH_PARENT, height);
    }
}
