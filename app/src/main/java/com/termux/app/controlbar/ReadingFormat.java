package com.termux.app.controlbar;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/** How each source's number becomes a {@link ReadingValue}. Pure. */
public final class ReadingFormat {

    private ReadingFormat() {}

    /** A load or charge in percent; null when unknown (below 0) so the reading is not offered. */
    @Nullable
    public static ReadingValue percent(@NonNull ReadingKind kind, int percent) {
        if (percent < 0) return null;
        return new ReadingValue(kind, String.valueOf(Math.min(100, percent)), "%");
    }

    /**
     * The battery's charge: the chip's own text ({@code "87%"}, or {@code "--"} before the first
     * reading) read back as a number; null while unknown.
     */
    @Nullable
    public static ReadingValue battery(@NonNull String chipText) {
        String digits = chipText.endsWith("%") ? chipText.substring(0, chipText.length() - 1) : chipText;
        try {
            return percent(ReadingKind.BATTERY, Integer.parseInt(digits.trim()));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** The temperature in whole degrees, in Fahrenheit when asked; null when the weather is not known. */
    @Nullable
    public static ReadingValue weather(boolean valid, double celsius, boolean fahrenheit) {
        if (!valid || Double.isNaN(celsius)) return null;
        double degrees = fahrenheit ? celsius * 9d / 5d + 32d : celsius;
        return new ReadingValue(ReadingKind.WEATHER, String.valueOf(Math.round(degrees)), "°");
    }

    /** Media has a session: the icon alone, no number. */
    @NonNull
    public static ReadingValue media() {
        return new ReadingValue(ReadingKind.MEDIA, "", "");
    }
}
