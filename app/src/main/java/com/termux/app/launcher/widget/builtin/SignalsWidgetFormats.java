package com.termux.app.launcher.widget.builtin;

import androidx.annotation.NonNull;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * The short mono labels the Notifications and Command widgets print: a notification's time
 * ("13:32" today, "Mon" before), how long ago a command ran ("12m ago"), how often it runs
 * ("every 15m") and how long it took ("0.18 s"). Pure, so the rules are tested rather than eyeballed.
 */
public final class SignalsWidgetFormats {
    private SignalsWidgetFormats() { }

    /** {@code timeMs} as a clock time when it falls on {@code nowMs}'s day, else as a short weekday. */
    @NonNull
    public static String clockOrDay(long timeMs, long nowMs, @NonNull ZoneId zone, boolean twentyFourHour,
                                    @NonNull Locale locale) {
        ZonedDateTime time = Instant.ofEpochMilli(timeMs).atZone(zone);
        LocalDate today = Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate();
        if (time.toLocalDate().equals(today)) {
            String pattern = twentyFourHour ? "HH:mm" : "h:mm a";
            return DateTimeFormatter.ofPattern(pattern, locale).format(time);
        }
        return DateTimeFormatter.ofPattern("EEE", locale).format(time);
    }

    /** How long ago {@code thenMs} was: "just now", "30s ago", "12m ago", "2h ago", "3d ago". */
    @NonNull
    public static String ago(long thenMs, long nowMs) {
        long seconds = Math.max(0L, (nowMs - thenMs) / 1000L);
        if (seconds < 5) return "just now";
        if (seconds < 60) return seconds + "s ago";
        long minutes = seconds / 60;
        if (minutes < 60) return minutes + "m ago";
        long hours = minutes / 60;
        if (hours < 24) return hours + "h ago";
        return (hours / 24) + "d ago";
    }

    /** A run interval in minutes: "every 15m", "every 1h", "every 1h 30m", "every 1d". */
    @NonNull
    public static String every(int minutes) {
        return "every " + span(minutes);
    }

    /** {@code minutes} as the compact span {@link #every} prints. */
    @NonNull
    public static String span(int minutes) {
        int value = Math.max(1, minutes);
        if (value < 60) return value + "m";
        if (value % (24 * 60) == 0) return (value / (24 * 60)) + "d";
        int hours = value / 60, rest = value % 60;
        return rest == 0 ? hours + "h" : hours + "h " + rest + "m";
    }

    /** A run's duration: "0.18 s" under ten seconds, "12.4 s" under a minute, "1m 05s" after. */
    @NonNull
    public static String duration(long millis) {
        long ms = Math.max(0L, millis);
        if (ms < 10_000L) return String.format(Locale.ROOT, "%.2f s", ms / 1000.0);
        if (ms < 60_000L) return String.format(Locale.ROOT, "%.1f s", ms / 1000.0);
        long seconds = ms / 1000L;
        return String.format(Locale.ROOT, "%dm %02ds", seconds / 60, seconds % 60);
    }

    /** The "+6" beside the stacked app icons; empty when there is nothing more to count. */
    @NonNull
    public static String overflow(int more) {
        return more <= 0 ? "" : "+" + Math.min(more, 99);
    }

    /** A count for the red badge: the number, or "99+" past it. */
    @NonNull
    public static String badge(int count) {
        return count > 99 ? "99+" : String.valueOf(Math.max(0, count));
    }
}
