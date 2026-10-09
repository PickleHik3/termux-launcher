package com.termux.app.launcher.widget.builtin;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Month;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.time.temporal.IsoFields;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The agenda and calendar widgets' date arithmetic and labels, kept free of Android so it is
 * tested on its own: which events are today and which come next, the short labels beside them
 * ("now", "in 50 min", "14:30", "Wed"), the week strip and the month grid's shape.
 */
public final class CalendarWidgetFormats {
    private static final long MINUTE_MS = 60_000L;

    private CalendarWidgetFormats() { }

    /** The words the labels are made of, from resources on a device and literals in tests. */
    public static final class Words {
        @NonNull final String now;
        @NonNull final String inMinutes;
        @NonNull final String inHours;
        @NonNull final String tomorrow;
        @NonNull final String allDay;

        /** {@code inMinutes} and {@code inHours} carry one {@code %1$d}. */
        public Words(@NonNull String now, @NonNull String inMinutes, @NonNull String inHours,
                     @NonNull String tomorrow, @NonNull String allDay) {
            this.now = now; this.inMinutes = inMinutes; this.inHours = inHours;
            this.tomorrow = tomorrow; this.allDay = allDay;
        }
    }

    // ----- days -----------------------------------------------------------------------------

    @NonNull public static LocalDate day(long millis, @NonNull ZoneId zone) {
        return Instant.ofEpochMilli(millis).atZone(zone).toLocalDate();
    }

    public static long startOfDay(@NonNull LocalDate day, @NonNull ZoneId zone) {
        return day.atStartOfDay(zone).toInstant().toEpochMilli();
    }

    public static boolean overlaps(@NonNull CalendarEvent event, @NonNull LocalDate day,
                                   @NonNull ZoneId zone) {
        long from = startOfDay(day, zone), to = startOfDay(day.plusDays(1), zone);
        return event.startMs < to && (event.endMs > from || (event.endMs == event.startMs
            && event.startMs >= from));
    }

    /** Every event that touches {@code day}, in order. */
    @NonNull public static List<CalendarEvent> onDay(@NonNull List<CalendarEvent> events,
                                                     @NonNull LocalDate day, @NonNull ZoneId zone) {
        List<CalendarEvent> out = new ArrayList<>();
        for (CalendarEvent event : events) if (overlaps(event, day, zone)) out.add(event);
        return out;
    }

    /** Events not yet over at {@code now}, in order. */
    @NonNull public static List<CalendarEvent> upcoming(@NonNull List<CalendarEvent> events,
                                                        long now) {
        List<CalendarEvent> out = new ArrayList<>();
        for (CalendarEvent event : events) if (!isOver(event, now)) out.add(event);
        return out;
    }

    /** Today's events not yet over at {@code now}. */
    @NonNull public static List<CalendarEvent> remainingToday(@NonNull List<CalendarEvent> events,
                                                              long now, @NonNull ZoneId zone) {
        LocalDate today = day(now, zone);
        List<CalendarEvent> out = new ArrayList<>();
        for (CalendarEvent event : events) {
            if (!isOver(event, now) && overlaps(event, today, zone)) out.add(event);
        }
        return out;
    }

    private static boolean isOver(@NonNull CalendarEvent event, long now) {
        return event.endMs > event.startMs ? event.endMs <= now : event.startMs < now;
    }

    /**
     * The event the agenda leads with: the first timed event still to come or under way today,
     * else whatever is first among the events not yet over (an all-day event, tomorrow's first).
     */
    @Nullable public static CalendarEvent next(@NonNull List<CalendarEvent> events, long now,
                                               @NonNull ZoneId zone) {
        long endOfToday = startOfDay(day(now, zone).plusDays(1), zone);
        CalendarEvent first = null;
        for (CalendarEvent event : events) {
            if (isOver(event, now)) continue;
            if (first == null) first = event;
            if (!event.allDay && event.startMs < endOfToday) return event;
        }
        return first;
    }

    /** The colour of the first event on each day from {@code from} up to {@code until}. */
    @NonNull public static Map<LocalDate, Integer> dayColors(@NonNull List<CalendarEvent> events,
                                                             @NonNull LocalDate from,
                                                             @NonNull LocalDate until,
                                                             @NonNull ZoneId zone, int fallback) {
        Map<LocalDate, Integer> out = new HashMap<>();
        for (CalendarEvent event : events) {
            LocalDate first = day(event.startMs, zone);
            long lastMs = event.endMs > event.startMs ? event.endMs - 1 : event.startMs;
            LocalDate last = day(lastMs, zone);
            if (first.isBefore(from)) first = from;
            for (LocalDate d = first; !d.isAfter(last) && d.isBefore(until); d = d.plusDays(1)) {
                if (!out.containsKey(d)) out.put(d, event.color != 0 ? event.color : fallback);
            }
        }
        return out;
    }

    // ----- labels ---------------------------------------------------------------------------

    @NonNull public static String clock(long millis, @NonNull ZoneId zone, boolean twentyFourHour,
                                        @NonNull Locale locale) {
        DateTimeFormatter format = DateTimeFormatter.ofPattern(twentyFourHour ? "HH:mm" : "h:mm a",
            locale);
        return format.format(Instant.ofEpochMilli(millis).atZone(zone));
    }

    /**
     * The short label beside an event in a list: its start time today, "All day", "now" for one
     * that began on an earlier day and is still on, or the weekday of a later day.
     */
    @NonNull public static String slot(@NonNull CalendarEvent event, long now, @NonNull ZoneId zone,
                                       boolean twentyFourHour, @NonNull Locale locale,
                                       @NonNull Words words) {
        LocalDate today = day(now, zone);
        if (event.allDay) {
            return overlaps(event, today, zone) ? words.allDay
                : weekdayShort(day(event.startMs, zone).getDayOfWeek(), locale);
        }
        LocalDate start = day(event.startMs, zone);
        if (start.equals(today)) return clock(event.startMs, zone, twentyFourHour, locale);
        if (start.isBefore(today)) return words.now;
        return weekdayShort(start.getDayOfWeek(), locale);
    }

    /**
     * How far off an event is: "now" while it is on, "in 50 min" inside the hour, "in 3 h" later
     * today, then "Tomorrow 10:00" or "Thu 10:00". All-day events say "All day" on their day.
     */
    @NonNull public static String relative(@NonNull CalendarEvent event, long now,
                                           @NonNull ZoneId zone, boolean twentyFourHour,
                                           @NonNull Locale locale, @NonNull Words words) {
        LocalDate today = day(now, zone);
        if (event.allDay) {
            if (overlaps(event, today, zone)) return words.allDay;
            return dayName(day(event.startMs, zone), today, locale, words);
        }
        if (event.startMs <= now) return words.now;
        long minutes = Math.max(1, (event.startMs - now + MINUTE_MS - 1) / MINUTE_MS);
        if (minutes < 60) return String.format(locale, words.inMinutes, minutes);
        LocalDate start = day(event.startMs, zone);
        if (start.equals(today)) {
            return String.format(locale, words.inHours, Math.max(1, Math.round(minutes / 60.0)));
        }
        return dayName(start, today, locale, words) + " "
            + clock(event.startMs, zone, twentyFourHour, locale);
    }

    @NonNull private static String dayName(@NonNull LocalDate day, @NonNull LocalDate today,
                                           @NonNull Locale locale, @NonNull Words words) {
        return day.equals(today.plusDays(1)) ? words.tomorrow
            : weekdayShort(day.getDayOfWeek(), locale);
    }

    // ----- names ----------------------------------------------------------------------------

    @NonNull public static String weekdayShort(@NonNull DayOfWeek day, @NonNull Locale locale) {
        return day.getDisplayName(TextStyle.SHORT, locale);
    }

    @NonNull public static String weekdayShortUpper(@NonNull DayOfWeek day, @NonNull Locale locale) {
        return weekdayShort(day, locale).replace(".", "").toUpperCase(locale);
    }

    @NonNull public static String weekdayFullUpper(@NonNull DayOfWeek day, @NonNull Locale locale) {
        return day.getDisplayName(TextStyle.FULL, locale).toUpperCase(locale);
    }

    /** The one-letter heading of a weekday column. */
    @NonNull public static String weekdayLetter(@NonNull DayOfWeek day, @NonNull Locale locale) {
        return day.getDisplayName(TextStyle.NARROW, locale);
    }

    /** The month on its own, as a heading: "October". */
    @NonNull public static String monthName(@NonNull Month month, @NonNull Locale locale) {
        return month.getDisplayName(TextStyle.FULL_STANDALONE, locale);
    }

    /** The month's short name: "Oct". */
    @NonNull public static String monthShort(@NonNull Month month, @NonNull Locale locale) {
        return month.getDisplayName(TextStyle.SHORT_STANDALONE, locale);
    }

    /** The month's short name in capitals, for the calendar's band: "OCT". */
    @NonNull public static String monthShortUpper(@NonNull Month month, @NonNull Locale locale) {
        return month.getDisplayName(TextStyle.SHORT_STANDALONE, locale).replace(".", "")
            .toUpperCase(locale);
    }

    // ----- weeks and months -----------------------------------------------------------------

    /**
     * The {@link DayOfWeek} for a {@link java.util.Calendar#getFirstDayOfWeek()} value
     * (1 = Sunday … 7 = Saturday); anything else is Monday.
     */
    @NonNull public static DayOfWeek firstDayOfWeek(int calendarDay) {
        if (calendarDay < 1 || calendarDay > 7) return DayOfWeek.MONDAY;
        return DayOfWeek.SUNDAY.plus(calendarDay - 1);
    }

    /** The first day of the week holding {@code day}. */
    @NonNull public static LocalDate weekStart(@NonNull LocalDate day, @NonNull DayOfWeek first) {
        return day.with(TemporalAdjusters.previousOrSame(first));
    }

    /**
     * The ISO week number of the week starting {@code weekStart}, read at its middle so a week
     * that starts on Sunday still names the ISO week most of its days are in.
     */
    public static int isoWeek(@NonNull LocalDate weekStart) {
        return weekStart.plusDays(3).get(IsoFields.WEEK_OF_WEEK_BASED_YEAR);
    }

    /** How many empty cells lead the month's first day in a grid starting on {@code first}. */
    public static int leadingBlanks(@NonNull YearMonth month, @NonNull DayOfWeek first) {
        int lead = month.atDay(1).getDayOfWeek().getValue() - first.getValue();
        return lead < 0 ? lead + 7 : lead;
    }

    /** The grid's rows: five, or six when the month spills into a sixth week. */
    public static int monthRows(@NonNull YearMonth month, @NonNull DayOfWeek first) {
        int cells = leadingBlanks(month, first) + month.lengthOfMonth();
        return Math.max(5, (cells + 6) / 7);
    }

    public static boolean isWeekend(@NonNull DayOfWeek day) {
        return day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY;
    }
}
