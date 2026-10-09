package com.termux.app.launcher.widget.builtin;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.time.DateTimeException;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.Locale;

/**
 * The clock widgets' arithmetic and wording that needs no view: a zone's city, its offset from the
 * device, how far through the day and the year we are, and the time in the user's 12- or 24-hour
 * habit. Kept pure so the edge cases — half-hour zones, a zone already in tomorrow, daylight
 * saving days — are pinned by tests rather than found on a phone.
 */
final class ClockWidgetFormats {
    /** The design's minus sign: a true minus, not a hyphen, so "−2 h" lines up with "+2:30". */
    static final char MINUS = '−';

    private ClockWidgetFormats() { }

    // ----- zones ----------------------------------------------------------------------------

    /** {@code Europe/London} → "London", {@code America/Argentina/Buenos_Aires} → "Buenos Aires". */
    @NonNull static String cityName(@Nullable String zoneId) {
        if (zoneId == null) return "";
        int slash = zoneId.lastIndexOf('/');
        return zoneId.substring(slash + 1).replace('_', ' ');
    }

    /** The Pane caption form of a city: lower case, words joined like a path segment. */
    @NonNull static String paneCity(@Nullable String zoneId) {
        return cityName(zoneId).toLowerCase(Locale.ROOT).replace(' ', '-');
    }

    /**
     * The zone the user meant by {@code input}: an IANA id ("Asia/Kolkata"), an offset id
     * ("UTC+3"), or just the city ("london", "New York") matched against the last segment of
     * {@code availableIds}. Null when nothing matches.
     */
    @Nullable static ZoneId resolveZone(@Nullable String input, @NonNull Collection<String> availableIds) {
        if (input == null) return null;
        String trimmed = input.trim();
        if (trimmed.isEmpty()) return null;
        if (availableIds.contains(trimmed)) return zoneOrNull(trimmed);
        ZoneId direct = zoneOrNull(trimmed);
        if (direct != null) return direct;
        String wanted = trimmed.replace(' ', '_');
        for (String id : availableIds) {
            if (id.equalsIgnoreCase(wanted)) return zoneOrNull(id);
        }
        String best = null;
        for (String id : availableIds) {
            String last = id.substring(id.lastIndexOf('/') + 1);
            if (!last.equalsIgnoreCase(wanted)) continue;
            // Prefer the shortest id: "Europe/London" over a legacy alias with a longer path.
            if (best == null || id.length() < best.length()) best = id;
        }
        return best == null ? null : zoneOrNull(best);
    }

    @Nullable static ZoneId resolveZone(@Nullable String input) {
        return resolveZone(input, ZoneId.getAvailableZoneIds());
    }

    @Nullable private static ZoneId zoneOrNull(@NonNull String id) {
        try {
            return ZoneId.of(id);
        } catch (DateTimeException e) {
            return null;
        }
    }

    /** Minutes {@code zone} is ahead of the device's zone at {@code deviceNow} (negative behind). */
    static int offsetMinutes(@NonNull ZonedDateTime deviceNow, @NonNull ZoneId zone) {
        int device = deviceNow.getOffset().getTotalSeconds();
        int other = deviceNow.withZoneSameInstant(zone).getOffset().getTotalSeconds();
        return (other - device) / 60;
    }

    /** Whether it is already tomorrow (+1) or still yesterday (−1) in {@code zone}. */
    static int dayDelta(@NonNull ZonedDateTime deviceNow, @NonNull ZoneId zone) {
        LocalDate here = deviceNow.toLocalDate();
        LocalDate there = deviceNow.withZoneSameInstant(zone).toLocalDate();
        long days = ChronoUnit.DAYS.between(here, there);
        return (int) Math.max(-1, Math.min(1, days));
    }

    /** "−2 h", "+2:30", "+5:45"; empty for no difference. */
    @NonNull static String offsetText(int minutes) {
        if (minutes == 0) return "";
        char sign = minutes < 0 ? MINUS : '+';
        int abs = Math.abs(minutes);
        int hours = abs / 60, rest = abs % 60;
        if (rest == 0) return sign + Integer.toString(hours) + " h";
        return sign + Integer.toString(hours) + ":" + (rest < 10 ? "0" : "") + rest;
    }

    /**
     * The design's offset line: {@code today} where the zone keeps the device's time, the offset
     * where the date is the same, and the offset with the day word where it is not.
     * {@code tomorrowFormat}/{@code yesterdayFormat} take the offset as {@code %1$s}.
     */
    @NonNull static String offsetLabel(int minutes, int dayDelta, @NonNull String today,
                                       @NonNull String tomorrowFormat,
                                       @NonNull String yesterdayFormat) {
        String offset = offsetText(minutes);
        if (dayDelta > 0) return String.format(tomorrowFormat, offset).trim();
        if (dayDelta < 0) return String.format(yesterdayFormat, offset).trim();
        return offset.isEmpty() ? today : offset;
    }

    /** "GMT+3", "GMT+5:30", "GMT−4", "GMT". */
    @NonNull static String zoneLabel(int offsetSeconds) {
        int minutes = offsetSeconds / 60;
        if (minutes == 0) return "GMT";
        char sign = minutes < 0 ? MINUS : '+';
        int abs = Math.abs(minutes);
        int hours = abs / 60, rest = abs % 60;
        return "GMT" + sign + hours + (rest == 0 ? "" : ":" + (rest < 10 ? "0" : "") + rest);
    }

    // ----- progress -------------------------------------------------------------------------

    /** How much of today has passed, 0..1, with a 23- or 25-hour day counted as it is. */
    static float dayFraction(@NonNull ZonedDateTime now) {
        LocalDate date = now.toLocalDate();
        ZonedDateTime start = date.atStartOfDay(now.getZone());
        ZonedDateTime end = date.plusDays(1).atStartOfDay(now.getZone());
        return fraction(start, now, end);
    }

    /** How much of this year has passed, 0..1. */
    static float yearFraction(@NonNull ZonedDateTime now) {
        LocalDate first = now.toLocalDate().withDayOfYear(1);
        ZonedDateTime start = first.atStartOfDay(now.getZone());
        ZonedDateTime end = first.plusYears(1).atStartOfDay(now.getZone());
        return fraction(start, now, end);
    }

    private static float fraction(@NonNull ZonedDateTime start, @NonNull ZonedDateTime now,
                                  @NonNull ZonedDateTime end) {
        long total = Duration.between(start, end).toMillis();
        if (total <= 0) return 0f;
        long done = Duration.between(start, now).toMillis();
        return Math.max(0f, Math.min(1f, done / (float) total));
    }

    /** A rounded percentage that only reads 100 once the span is really over. */
    static int percent(float fraction) {
        int value = Math.round(Math.max(0f, Math.min(1f, fraction)) * 100f);
        return fraction < 1f ? Math.min(99, value) : 100;
    }

    // ----- time of day ----------------------------------------------------------------------

    /** "13:40" or "1:40": the time without its AM/PM marker. */
    @NonNull static String hoursMinutes(@NonNull LocalTime time, boolean is24Hour) {
        return DateTimeFormatter.ofPattern(is24Hour ? "HH:mm" : "h:mm", Locale.getDefault()).format(time);
    }

    /** "13" or "1": the hour alone, for the stacked 1×1 clock. */
    @NonNull static String hours(@NonNull LocalTime time, boolean is24Hour) {
        return DateTimeFormatter.ofPattern(is24Hour ? "HH" : "h", Locale.getDefault()).format(time);
    }

    /** "40". */
    @NonNull static String minutes(@NonNull LocalTime time) {
        return DateTimeFormatter.ofPattern("mm", Locale.getDefault()).format(time);
    }

    /** "12". */
    @NonNull static String seconds(@NonNull LocalTime time) {
        return DateTimeFormatter.ofPattern("ss", Locale.getDefault()).format(time);
    }

    /** "PM" in the user's language; empty for a 24-hour clock. */
    @NonNull static String amPm(@NonNull LocalTime time, boolean is24Hour) {
        if (is24Hour) return "";
        return DateTimeFormatter.ofPattern("a", Locale.getDefault()).format(time);
    }

    /**
     * A weather "HH:mm" (sunrise, sunset) in the user's habit: "05:38" or "5:38". Empty when the
     * value is missing or unreadable, which hides the row.
     */
    @NonNull static String clockText(@Nullable String hhmm, boolean is24Hour) {
        if (hhmm == null || hhmm.isEmpty()) return "";
        try {
            return hoursMinutes(LocalTime.parse(hhmm), is24Hour);
        } catch (DateTimeException e) {
            return "";
        }
    }

    // ----- hands ----------------------------------------------------------------------------

    /** The hour hand's angle in degrees clockwise from 12, moving with the minutes. */
    static float hourAngle(int hour, int minute, int second) {
        return ((hour % 12) + minute / 60f + second / 3600f) * 30f;
    }

    /** The minute hand's angle, creeping with the seconds. */
    static float minuteAngle(int minute, int second) {
        return (minute + second / 60f) * 6f;
    }

    static float secondAngle(int second) {
        return second * 6f;
    }
}
