package com.termux.app.launcher.widget.builtin;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.statusbar.WeatherController;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * What the weather widget shows, already formatted: the current reading, the next hours and the
 * coming days. Built from the shared {@link WeatherController.Weather} or from the design's sample
 * values for the picker, so the view only lays it out. The static helpers are the pure parts —
 * which hours are "next", where a day's range sits on the week's scale, which glyph and tone a
 * sky gets — and are tested on their own.
 */
final class WeatherWidgetModel {
    /** How a sky glyph is tinted: the sun warm, the moon primary, everything else muted. */
    enum Tone { SUN, MOON, CLOUD }

    /** Weather Icons night cuts: a clear night, and a partly cloudy one. */
    static final String GLYPH_NIGHT_CLEAR = "\ue32b";
    static final String GLYPH_NIGHT_PARTLY_CLOUDY = "\ue37e";

    static final int HOURLY_MAX = 5;
    static final int FORECAST_DAYS = 5;

    static final class Hour {
        @NonNull final String label;
        @NonNull final String glyph;
        @NonNull final Tone tone;
        @NonNull final String temp;
        Hour(@NonNull String label, @NonNull String glyph, @NonNull Tone tone, @NonNull String temp) {
            this.label = label; this.glyph = glyph; this.tone = tone; this.temp = temp;
        }
    }

    static final class Day {
        @NonNull final String name;
        @NonNull final String glyph;
        @NonNull final Tone tone;
        @NonNull final String low;
        @NonNull final String high;
        /** Where the day's low and high sit on the shown days' common scale, 0..1. */
        final float start;
        final float end;
        Day(@NonNull String name, @NonNull String glyph, @NonNull Tone tone, @NonNull String low,
            @NonNull String high, float start, float end) {
            this.name = name; this.glyph = glyph; this.tone = tone; this.low = low; this.high = high;
            this.start = start; this.end = end;
        }
    }

    /** Short place name, empty when unknown. */
    @NonNull final String place;
    /** "34°". */
    @NonNull final String temp;
    /** "34", for the spoken description. */
    @NonNull final String tempBare;
    /** "Clear"; empty for a code the table does not know. */
    @NonNull final String condition;
    @NonNull final String glyph;
    @NonNull final Tone tone;
    /** Today's high and low without the degree sign; empty when there is no daily entry. */
    @NonNull final String high;
    @NonNull final String low;
    /** Relative humidity in percent, -1 when unknown. */
    final int humidityPct;
    /** Wind speed in km/h, -1 when unknown. */
    final int windKmh;
    @NonNull final List<Hour> hours;
    @NonNull final List<Day> days;

    WeatherWidgetModel(@NonNull String place, @NonNull String temp, @NonNull String tempBare,
                       @NonNull String condition, @NonNull String glyph, @NonNull Tone tone,
                       @NonNull String high, @NonNull String low, int humidityPct, int windKmh,
                       @NonNull List<Hour> hours, @NonNull List<Day> days) {
        this.place = place; this.temp = temp; this.tempBare = tempBare;
        this.condition = condition; this.glyph = glyph; this.tone = tone;
        this.high = high; this.low = low;
        this.humidityPct = humidityPct; this.windKmh = windKmh;
        this.hours = hours; this.days = days;
    }

    boolean hasRange() { return !high.isEmpty() && !low.isEmpty(); }

    // ----- building -------------------------------------------------------------------------

    /** The widget's view of a valid forecast, read at {@code now}. */
    @NonNull static WeatherWidgetModel from(@NonNull WeatherController.Weather weather,
                                            boolean fahrenheit, @NonNull Calendar now) {
        String high = "", low = "";
        if (!weather.daily.isEmpty()) {
            WeatherController.Daily today = weather.daily.get(0);
            high = WeatherController.formatTempBare(today.maxC, fahrenheit);
            low = WeatherController.formatTempBare(today.minC, fahrenheit);
        }

        List<Hour> hours = new ArrayList<>();
        List<String> isos = new ArrayList<>(weather.hourly.size());
        for (WeatherController.Hourly h : weather.hourly) isos.add(h.iso);
        int first = firstUpcoming(isos, currentHourIso(now));
        for (int i = Math.max(0, first); i >= 0 && i < weather.hourly.size()
            && hours.size() < HOURLY_MAX; i++) {
            WeatherController.Hourly h = weather.hourly.get(i);
            hours.add(new Hour(hourLabel(h.iso), glyph(h.code, h.isDay), tone(h.code, h.isDay),
                WeatherController.formatTemp(h.tempC, fahrenheit)));
        }

        // The coming days from tomorrow, on one scale so a bar means the same thing on every row.
        List<Day> days = new ArrayList<>();
        int from = Math.min(1, weather.daily.size());
        int to = Math.min(weather.daily.size(), from + FORECAST_DAYS);
        double min = Double.MAX_VALUE, max = -Double.MAX_VALUE;
        for (int i = from; i < to; i++) {
            WeatherController.Daily d = weather.daily.get(i);
            min = Math.min(min, d.minC);
            max = Math.max(max, d.maxC);
        }
        for (int i = from; i < to; i++) {
            WeatherController.Daily d = weather.daily.get(i);
            float[] range = range(d.minC, d.maxC, min, max);
            days.add(new Day(weekday(d.date), glyph(d.code, true), tone(d.code, true),
                WeatherController.formatTemp(d.minC, fahrenheit),
                WeatherController.formatTemp(d.maxC, fahrenheit), range[0], range[1]));
        }

        String condition = WeatherController.describe(weather.currentCode);
        if ("—".equals(condition)) condition = "";
        return new WeatherWidgetModel(shortPlace(weather.locationName),
            WeatherController.formatTemp(weather.currentC, fahrenheit),
            WeatherController.formatTempBare(weather.currentC, fahrenheit),
            condition, glyph(weather.currentCode, weather.currentIsDay),
            tone(weather.currentCode, weather.currentIsDay), high, low,
            Double.isNaN(weather.humidityPct) ? -1 : (int) Math.round(weather.humidityPct),
            Double.isNaN(weather.windKmh) ? -1 : (int) Math.round(weather.windKmh),
            Collections.unmodifiableList(hours), Collections.unmodifiableList(days));
    }

    /** The design's sample: Kuwait City at 13:40 on a sunny Tuesday. */
    @NonNull static WeatherWidgetModel sample() {
        List<Hour> hours = new ArrayList<>();
        // hour, temperature, WMO code, is-day
        int[][] hourly = { {14, 35, 0, 1}, {15, 35, 0, 1}, {16, 34, 0, 1}, {17, 32, 3, 1},
            {18, 30, 0, 0} };
        for (int[] h : hourly) {
            boolean day = h[3] == 1;
            hours.add(new Hour(String.valueOf(h[0]), glyph(h[2], day), tone(h[2], day), h[1] + "°"));
        }
        List<Day> days = new ArrayList<>();
        String[] names = { "Wed", "Thu", "Fri", "Sat", "Sun" };
        // low, high, WMO code
        int[][] forecast = { {24, 36, 0}, {23, 35, 0}, {22, 33, 3}, {23, 34, 0}, {24, 35, 0} };
        for (int i = 0; i < forecast.length; i++) {
            int[] f = forecast[i];
            // The design's own scale for the sample: 20° to 40°.
            float[] range = range(f[0], f[1], 20, 40);
            days.add(new Day(names[i], glyph(f[2], true), tone(f[2], true), f[0] + "°", f[1] + "°",
                range[0], range[1]));
        }
        return new WeatherWidgetModel("Kuwait City", "34°", "34", WeatherController.describe(0),
            glyph(0, true), Tone.SUN,
            "36", "24", 18, 14, hours, days);
    }

    // ----- pure helpers ---------------------------------------------------------------------

    /** {@code 2026-10-06T13:00} for 13:40 on that day: the provider's key for the current hour. */
    @NonNull static String currentHourIso(@NonNull Calendar now) {
        return new SimpleDateFormat("yyyy-MM-dd'T'HH':00'", Locale.ROOT).format(now.getTime());
    }

    /**
     * Index of the first hourly entry after the current hour, the design's "next hours" (13:40
     * shows 14, 15, 16...). A stale list wholly in the past answers 0, so the strip still shows
     * its head rather than nothing; an empty list answers -1. ISO local times sort as strings.
     */
    static int firstUpcoming(@NonNull List<String> isos, @NonNull String currentHourIso) {
        if (isos.isEmpty()) return -1;
        for (int i = 0; i < isos.size(); i++) {
            if (isos.get(i).compareTo(currentHourIso) > 0) return i;
        }
        return 0;
    }

    /** "14" out of {@code 2026-10-06T14:00}; the input itself when it has no time part. */
    @NonNull static String hourLabel(@NonNull String iso) {
        int t = iso.indexOf('T');
        return t >= 0 && iso.length() >= t + 3 ? iso.substring(t + 1, t + 3) : iso;
    }

    /**
     * Where a day running {@code low}..{@code high} sits on a scale of {@code min}..{@code max}:
     * the start and end of its range bar, each 0..1. A flat scale draws the bar whole.
     */
    @NonNull static float[] range(double low, double high, double min, double max) {
        double span = max - min;
        if (!(span > 0.01)) return new float[] { 0f, 1f };
        float start = (float) ((low - min) / span);
        float end = (float) ((high - min) / span);
        return new float[] { clamp(start), clamp(Math.max(start, end)) };
    }

    private static float clamp(float value) { return Math.max(0f, Math.min(1f, value)); }

    /** "Kuwait City" out of "Kuwait City, Al Asimah": a caption has room for the city only. */
    @NonNull static String shortPlace(@Nullable String name) {
        if (name == null) return "";
        int comma = name.indexOf(',');
        return (comma > 0 ? name.substring(0, comma) : name).trim();
    }

    /** True for the skies the design draws as a sun by day and a moon by night. */
    private static boolean isClearish(int code) { return code == 0 || code == 1 || code == 2; }

    @NonNull static Tone tone(int code, boolean isDay) {
        if (!isClearish(code)) return Tone.CLOUD;
        return isDay ? Tone.SUN : Tone.MOON;
    }

    /**
     * The sky's glyph: the shared table's day cut, with a night cut for the clear and partly
     * cloudy skies the table only draws with a sun.
     */
    @NonNull static String glyph(int code, boolean isDay) {
        if (!isDay && (code == 0 || code == 1)) return GLYPH_NIGHT_CLEAR;
        if (!isDay && code == 2) return GLYPH_NIGHT_PARTLY_CLOUDY;
        return WeatherController.glyphFor(code);
    }

    /** "Wed" for {@code 2026-10-07}, in the user's language; the input when it does not parse. */
    @NonNull static String weekday(@NonNull String date) {
        try {
            Date d = new SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).parse(date);
            if (d != null) return new SimpleDateFormat("EEE", Locale.getDefault()).format(d);
        } catch (Exception ignored) { }
        return date;
    }
}
