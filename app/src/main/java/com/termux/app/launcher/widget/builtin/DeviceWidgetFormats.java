package com.termux.app.launcher.widget.builtin;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Locale;
import java.util.TimeZone;

/**
 * The short readings the Battery and System widgets print: "5.1/7.7G", "3d 4h", "31.4°C",
 * "18 W", the hour axis under the 24-hour chart. Android-free so every rule here is a unit test.
 * Numbers use {@link Locale#ROOT}: these are stat readouts in the mono face, set the way a
 * terminal tool prints them.
 */
public final class DeviceWidgetFormats {
    private DeviceWidgetFormats() { }

    /** Shown wherever a reading is not known. */
    public static final String UNKNOWN = "—";

    private static final double KIB_PER_GIB = 1024d * 1024d;
    private static final double BYTES_PER_GB = 1_000_000_000d;
    private static final long HOUR_MS = 3_600_000L;

    // ----- sizes ----------------------------------------------------------------------------

    /** Memory as "used/totalG" from kilobytes (binary gigabytes, as /proc/meminfo counts). */
    @NonNull public static String memoryPair(long usedKb, long totalKb) {
        if (totalKb <= 0) return UNKNOWN;
        return pair(usedKb / KIB_PER_GIB, totalKb / KIB_PER_GIB);
    }

    /** Storage as "used/totalG" from bytes (decimal gigabytes, as the system's storage screen counts). */
    @NonNull public static String storagePair(long usedBytes, long totalBytes) {
        if (totalBytes <= 0) return UNKNOWN;
        return pair(usedBytes / BYTES_PER_GB, totalBytes / BYTES_PER_GB);
    }

    @NonNull private static String pair(double used, double total) {
        return gigabytes(Math.max(0d, used)) + "/" + gigabytes(total) + "G";
    }

    /** One decimal below ten, whole numbers from ten up: "5.1", "7.7", "156", "256". */
    @NonNull static String gigabytes(double value) {
        if (value < 9.95d) return String.format(Locale.ROOT, "%.1f", value);
        return String.format(Locale.ROOT, "%d", Math.round(value));
    }

    /** {@code part} of {@code whole} as a whole percent, 0..100; -1 when the whole is unknown. */
    public static int percent(long part, long whole) {
        if (whole <= 0) return -1;
        long value = Math.round(100d * Math.max(0L, part) / whole);
        return (int) Math.max(0L, Math.min(100L, value));
    }

    /** "24%", or the unknown dash for a negative percent. */
    @NonNull public static String percentText(int percent) {
        return percent < 0 ? UNKNOWN : percent + "%";
    }

    // ----- time -----------------------------------------------------------------------------

    /** Uptime the way a status line says it: "3d 4h", "4h 12m", "12m". */
    @NonNull public static String uptime(long millis) {
        long minutes = Math.max(0L, millis) / 60_000L;
        long days = minutes / (24 * 60);
        long hours = (minutes / 60) % 24;
        long mins = minutes % 60;
        if (days > 0) return days + "d " + hours + "h";
        if (hours > 0) return hours + "h " + mins + "m";
        return mins + "m";
    }

    /** A remaining charge time: "42 min", "1 h 5 min", "2 h"; rounded up to the minute. */
    @NonNull public static String duration(long millis) {
        long minutes = Math.max(1L, (Math.max(0L, millis) + 59_999L) / 60_000L);
        if (minutes < 60) return minutes + " min";
        long hours = minutes / 60, mins = minutes % 60;
        return mins == 0 ? hours + " h" : hours + " h " + mins + " min";
    }

    /** Whole minutes in {@code millis}, rounded up, for a spoken description. */
    public static int minutesRoundedUp(long millis) {
        return (int) Math.max(1L, (Math.max(0L, millis) + 59_999L) / 60_000L);
    }

    /**
     * The local hour {@code millis} falls in, counted from the epoch. Slots keyed by this agree
     * with the wall clock in zones with half-hour offsets, which plain UTC hours would not.
     */
    public static long localHour(long millis, @NonNull TimeZone zone) {
        return Math.floorDiv(millis + zone.getOffset(millis), HOUR_MS);
    }

    /** The hour of day, 0..23, of a {@link #localHour} index. */
    public static int hourOfDay(long localHour) {
        return (int) Math.floorMod(localHour, 24L);
    }

    /** "14:00" for hour 14. */
    @NonNull public static String hourLabel(int hourOfDay) {
        return String.format(Locale.ROOT, "%02d:00", Math.floorMod(hourOfDay, 24));
    }

    /**
     * The four dated labels under a chart of {@code slots} hourly bars ending at {@code nowHour}
     * (a {@link #localHour}): the hours of bars 0, 6, 12 and 18 for 24 slots — the caller adds
     * "now" for the last. For 13:40 that is 14:00, 20:00, 02:00, 08:00.
     */
    @NonNull public static String[] hourAxis(long nowHour, int slots) {
        String[] labels = new String[4];
        long first = nowHour - (slots - 1);
        int step = Math.max(1, slots / 4);
        for (int i = 0; i < labels.length; i++) {
            labels[i] = hourLabel(hourOfDay(first + (long) i * step));
        }
        return labels;
    }

    // ----- battery --------------------------------------------------------------------------

    /** "31.4°C" from tenths of a degree. */
    @NonNull public static String celsius(int tenths) {
        if (tenths == Integer.MIN_VALUE) return UNKNOWN;
        return String.format(Locale.ROOT, "%.1f°C", tenths / 10f);
    }

    /** "31.4°" from tenths of a degree. */
    @NonNull public static String degreesTenths(int tenths) {
        if (tenths == Integer.MIN_VALUE) return UNKNOWN;
        return String.format(Locale.ROOT, "%.1f°", tenths / 10f);
    }

    /** "31°" from tenths of a degree, rounded. */
    @NonNull public static String degrees(int tenths) {
        if (tenths == Integer.MIN_VALUE) return UNKNOWN;
        return Math.round(tenths / 10f) + "°";
    }

    /** "18 W", "4.5 W"; the unknown dash for NaN. */
    @NonNull public static String watts(double watts) {
        if (Double.isNaN(watts)) return UNKNOWN;
        if (watts < 9.95d) return String.format(Locale.ROOT, "%.1f W", watts);
        return Math.round(watts) + " W";
    }

    /**
     * Battery power in watts from {@code BATTERY_PROPERTY_CURRENT_NOW} and the voltage. The current
     * is microamps whose sign differs by maker, so its magnitude is used; a property the device
     * does not support ({@code Integer.MIN_VALUE}, zero) or a reading too small to be real
     * microamps (makers that report milliamps land here) is unknown rather than a wrong number.
     */
    public static double powerWatts(long currentMicroAmps, int voltageMv) {
        if (currentMicroAmps == Integer.MIN_VALUE || currentMicroAmps == Long.MIN_VALUE
            || voltageMv <= 0) {
            return Double.NaN;
        }
        double watts = Math.abs((double) currentMicroAmps) * voltageMv / 1e9;
        return watts < 0.05d ? Double.NaN : watts;
    }

    /** The battery voltage in millivolts; a few devices report microvolts. */
    public static int voltageMillivolts(int raw) {
        if (raw <= 0) return -1;
        return raw > 100_000 ? raw / 1000 : raw;
    }

    // ----- cpu ------------------------------------------------------------------------------

    /** "1.8 GHz" from a {@code cpuinfo_max_freq} reading in kHz; null when there is none. */
    @Nullable public static String gigahertz(long kHz) {
        if (kHz <= 0) return null;
        return String.format(Locale.ROOT, "%.1f GHz", kHz / 1_000_000d);
    }
}
