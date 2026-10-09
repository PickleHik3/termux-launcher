package com.termux.app.launcher.widget.builtin;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

/**
 * What the Scratchpad widget says about its file: how long ago it was edited ("just now", "2m",
 * "3h", "Mon") and which of its lines a card shows. Pure, so the wording is tested without a
 * device; the words themselves come from resources and are passed in.
 */
public final class NotesWidgetFormats {
    private NotesWidgetFormats() { }

    private static final long MINUTE = 60_000L;
    private static final long HOUR = 60 * MINUTE;
    private static final long DAY = 24 * HOUR;

    /** How an edit time is put into words. */
    public enum Unit { NOW, MINUTES, HOURS, WEEKDAY, DATE }

    /** An edit time relative to now: a unit and, for minutes and hours, how many. */
    public static final class Age {
        @NonNull public final Unit unit;
        public final int amount;
        public final long whenMs;
        Age(@NonNull Unit unit, int amount, long whenMs) {
            this.unit = unit; this.amount = amount; this.whenMs = whenMs;
        }
    }

    /**
     * Under a minute (or in the future: a clock that moved) is now; under an hour counts minutes;
     * under a day counts hours; under a week names the weekday; older names the date.
     */
    @NonNull public static Age age(long modifiedMs, long nowMs) {
        long elapsed = nowMs - modifiedMs;
        if (elapsed < MINUTE) return new Age(Unit.NOW, 0, modifiedMs);
        if (elapsed < HOUR) return new Age(Unit.MINUTES, (int) (elapsed / MINUTE), modifiedMs);
        if (elapsed < DAY) return new Age(Unit.HOURS, (int) (elapsed / HOUR), modifiedMs);
        if (elapsed < 6 * DAY) return new Age(Unit.WEEKDAY, 0, modifiedMs);
        return new Age(Unit.DATE, 0, modifiedMs);
    }

    /**
     * The short form: {@code justNow}, {@code minutesFormat} / {@code hoursFormat} with the count
     * ({@code "%dm"} gives "2m"), the short weekday ("Mon") or the day and month ("6 Oct").
     */
    @NonNull public static String compact(@NonNull Age age, @NonNull String justNow,
                                          @NonNull String minutesFormat, @NonNull String hoursFormat,
                                          @NonNull Locale locale, @NonNull TimeZone zone) {
        switch (age.unit) {
            case NOW: return justNow;
            case MINUTES: return String.format(locale, minutesFormat, age.amount);
            case HOURS: return String.format(locale, hoursFormat, age.amount);
            case WEEKDAY: return format("EEE", age.whenMs, locale, zone);
            default: return format("d MMM", age.whenMs, locale, zone);
        }
    }

    /** The weekday or date in full, for a spoken description ("Monday", "6 October"). */
    @NonNull public static String spokenDay(@NonNull Age age, @NonNull Locale locale, @NonNull TimeZone zone) {
        return format(age.unit == Unit.WEEKDAY ? "EEEE" : "d MMMM", age.whenMs, locale, zone);
    }

    @NonNull private static String format(@NonNull String pattern, long whenMs, @NonNull Locale locale,
                                          @NonNull TimeZone zone) {
        SimpleDateFormat format = new SimpleDateFormat(pattern, locale);
        format.setTimeZone(zone);
        return format.format(new Date(whenMs));
    }

    /** The first {@code max} lines of {@code text}, carriage returns dropped, trailing blanks trimmed off. */
    @NonNull public static List<String> head(@Nullable String text, int max) {
        List<String> out = new ArrayList<>();
        if (text == null || text.isEmpty() || max <= 0) return out;
        int start = 0;
        while (start <= text.length() && out.size() < max) {
            int end = text.indexOf('\n', start);
            if (end < 0) end = text.length();
            out.add(stripCr(text.substring(start, end)));
            if (end >= text.length()) break;
            start = end + 1;
        }
        while (!out.isEmpty() && out.get(out.size() - 1).trim().isEmpty()) out.remove(out.size() - 1);
        return out;
    }

    /**
     * The lines a one-line preview shows: the text the user wrote, without blank lines and
     * headings, up to {@code max} of them.
     */
    @NonNull public static List<String> previewLines(@Nullable String text, int max) {
        List<String> out = new ArrayList<>();
        if (text == null || max <= 0) return out;
        for (String line : head(text, 200)) {
            if (line.trim().isEmpty() || isHeading(line)) continue;
            out.add(line.trim());
            if (out.size() >= max) break;
        }
        return out;
    }

    /** True when nothing but whitespace is in {@code text}. */
    public static boolean isBlank(@Nullable String text) {
        return text == null || text.trim().isEmpty();
    }

    /** A markdown heading: {@code # today}. */
    public static boolean isHeading(@NonNull String line) {
        return line.trim().startsWith("#");
    }

    /** A checked checklist item: {@code - [x] fix pane rim}. */
    public static boolean isDoneItem(@NonNull String line) {
        String trimmed = line.trim();
        return trimmed.length() >= 5 && (trimmed.charAt(0) == '-' || trimmed.charAt(0) == '*'
            || trimmed.charAt(0) == '+')
            && trimmed.substring(1).trim().toLowerCase(Locale.ROOT).startsWith("[x]");
    }

    @NonNull private static String stripCr(@NonNull String line) {
        return line.endsWith("\r") ? line.substring(0, line.length() - 1) : line;
    }
}
