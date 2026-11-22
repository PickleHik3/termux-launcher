package com.termux.app.assistant;

import androidx.annotation.NonNull;

import java.util.Locale;

/** The speed line under an answer: whole tokens per second and the time it took. */
final class AssistantSpeed {
    /** A decode window shorter than this says nothing about speed. */
    static final long MIN_DECODE_MS = 200L;
    static final int MIN_TOKENS = 2;

    private AssistantSpeed() {
    }

    /** Tokens over the time from the first token to the end, rounded; 0 when too little to tell. */
    static int tokensPerSecond(long durationMs, long ttftMs, int tokens) {
        long window = durationMs - ttftMs;
        if (tokens < MIN_TOKENS || window < MIN_DECODE_MS) return 0;
        return (int) Math.round(tokens * 1000.0 / window);
    }

    /** 4.1 under 10 s, 12 from 10 s, 1:05 from 60 s. */
    @NonNull
    static String time(long durationMs) {
        if (durationMs < 9950L) return String.format(Locale.getDefault(), "%.1f", durationMs / 1000.0);
        long s = Math.round(durationMs / 1000.0);
        if (s < 60L) return String.format(Locale.getDefault(), "%d", s);
        return String.format(Locale.getDefault(), "%d:%02d", s / 60L, s % 60L);
    }

    /** The time is written as minutes and seconds, so it carries no "s" unit. */
    static boolean isClock(long durationMs) {
        return durationMs >= 9950L && Math.round(durationMs / 1000.0) >= 60L;
    }

    /** The time with its unit: {@code secondsTemplate} (like "%1$s s") for seconds, bare 1:05 for a clock. */
    @NonNull
    static String timeWithUnit(long durationMs, @NonNull String secondsTemplate) {
        String t = time(durationMs);
        return isClock(durationMs) ? t : String.format(Locale.getDefault(), secondsTemplate, t);
    }

    /** The seconds as a number for speech: 4.1, 12, 65. */
    @NonNull
    static String spokenSeconds(long durationMs) {
        return isClock(durationMs) ? String.format(Locale.getDefault(), "%d", Math.round(durationMs / 1000.0))
            : time(durationMs);
    }
}
