package com.termux.app.launcher.widget.builtin;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Comparator;

/**
 * One occurrence of a calendar event, as the agenda and calendar widgets draw it.
 *
 * <p>{@link #startMs}/{@link #endMs} are wall-clock bounds in the zone the event was read in:
 * an all-day event, which the provider stores as UTC midnights, becomes local midnight to local
 * midnight, so it sorts and overlaps days the way the user reads it. {@link #rawBegin}/
 * {@link #rawEnd} keep the provider's own values for opening the event.</p>
 */
public final class CalendarEvent {
    private static final long DAY_MS = 24L * 60 * 60 * 1000;

    /** Earliest first; on the same start an all-day event leads, then by title. */
    public static final Comparator<CalendarEvent> ORDER = (a, b) -> {
        if (a.startMs != b.startMs) return a.startMs < b.startMs ? -1 : 1;
        if (a.allDay != b.allDay) return a.allDay ? -1 : 1;
        return a.title.compareTo(b.title);
    };

    public final long id;
    public final long rawBegin;
    public final long rawEnd;
    public final long startMs;
    public final long endMs;
    public final boolean allDay;
    @NonNull public final String title;
    @NonNull public final String location;
    @NonNull public final String calendarName;
    /** The event's display colour, opaque; 0 when it has none and the widget's accent applies. */
    @ColorInt public final int color;

    private CalendarEvent(long id, long rawBegin, long rawEnd, long startMs, long endMs,
                         boolean allDay, @NonNull String title, @NonNull String location,
                         @NonNull String calendarName, int color) {
        this.id = id;
        this.rawBegin = rawBegin;
        this.rawEnd = rawEnd;
        this.startMs = startMs;
        this.endMs = endMs;
        this.allDay = allDay;
        this.title = title;
        this.location = location;
        this.calendarName = calendarName;
        this.color = color;
    }

    /** An occurrence read from the provider, its bounds normalised for {@code zone}. */
    @NonNull public static CalendarEvent of(long id, long begin, long end, boolean allDay,
                                            @Nullable String title, @Nullable String location,
                                            @Nullable String calendarName, int color,
                                            @NonNull ZoneId zone) {
        long start, finish;
        if (allDay) {
            LocalDate first = LocalDate.ofEpochDay(Math.floorDiv(begin, DAY_MS));
            LocalDate after = LocalDate.ofEpochDay(Math.floorDiv(end, DAY_MS));
            if (!after.isAfter(first)) after = first.plusDays(1);
            start = first.atStartOfDay(zone).toInstant().toEpochMilli();
            finish = after.atStartOfDay(zone).toInstant().toEpochMilli();
        } else {
            start = begin;
            finish = Math.max(begin, end);
        }
        int opaque = color == 0 ? 0 : (color | 0xFF000000);
        return new CalendarEvent(id, begin, end, start, finish, allDay, clean(title),
            clean(location), clean(calendarName), opaque);
    }

    @NonNull private static String clean(@Nullable String value) {
        return value == null ? "" : value.trim();
    }

    /** Where it happens, or failing that, which calendar it is on. */
    @NonNull public String place() { return location.isEmpty() ? calendarName : location; }
}
