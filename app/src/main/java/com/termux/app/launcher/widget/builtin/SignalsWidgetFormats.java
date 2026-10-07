package com.termux.app.launcher.widget.builtin;

import androidx.annotation.NonNull;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * The short mono labels the Notifications widget prints: a notification's time ("13:32" today,
 * "Mon" before) and how long ago ("12m ago"). Pure, so the rules are tested rather than eyeballed.
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
