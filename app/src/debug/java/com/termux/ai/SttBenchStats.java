package com.termux.ai;

import java.util.Arrays;
import java.util.Locale;

/**
 * The pure parts of {@link SttBench}: statistics, {@code /proc/<pid>/stat} CPU parsing and the
 * one-line {@code key=value} output format. No Android classes, so a JVM test covers them.
 */
final class SttBenchStats {

    private SttBenchStats() {
    }

    /** Median of {@code values} (mean of the middle two for an even count); NaN for none. */
    static double median(double[] values) {
        if (values.length == 0) return Double.NaN;
        double[] sorted = values.clone();
        Arrays.sort(sorted);
        int mid = sorted.length / 2;
        return sorted.length % 2 == 1 ? sorted[mid] : (sorted[mid - 1] + sorted[mid]) / 2.0;
    }

    /** Smallest of {@code values}; NaN for none. */
    static double min(double[] values) {
        if (values.length == 0) return Double.NaN;
        double best = values[0];
        for (double v : values) best = Math.min(best, v);
        return best;
    }

    /** Mean of the finite entries of {@code values}; NaN when there are none. */
    static double meanOfFinite(double[] values) {
        double sum = 0.0;
        int n = 0;
        for (double v : values) {
            if (Double.isNaN(v) || Double.isInfinite(v)) continue;
            sum += v;
            n++;
        }
        return n == 0 ? Double.NaN : sum / n;
    }

    /**
     * utime + stime, in clock ticks, from the text of {@code /proc/<pid>/stat}: fields 14 and 15,
     * counted after the {@code (comm)} field, which may itself hold spaces and parentheses.
     * -1 when the text does not parse.
     */
    static long parseCpuTicks(String stat) {
        if (stat == null) return -1L;
        int close = stat.lastIndexOf(')');
        if (close < 0 || close + 2 > stat.length()) return -1L;
        String[] fields = stat.substring(close + 2).split(" ");
        // fields[0] is field 3 (state), so utime (14) is fields[11] and stime (15) fields[12].
        if (fields.length < 13) return -1L;
        try {
            return Long.parseLong(fields[11]) + Long.parseLong(fields[12]);
        } catch (NumberFormatException e) {
            return -1L;
        }
    }

    /** Clock ticks as seconds; NaN when {@code ticks} is negative. */
    static double ticksToSeconds(long ticks, long ticksPerSecond) {
        return ticks < 0L || ticksPerSecond <= 0L ? Double.NaN : ticks / (double) ticksPerSecond;
    }

    /** A number with {@code decimals} places, {@code NA} for NaN. Always a dot, whatever the locale. */
    static String num(double value, int decimals) {
        if (Double.isNaN(value) || Double.isInfinite(value)) return "NA";
        return String.format(Locale.ROOT, "%." + decimals + "f", value);
    }

    /** Microamps as milliamps with one decimal, {@code NA} for NaN. */
    static String milliamps(double microamps) {
        return num(microamps / 1000.0, 1);
    }

    /** A transcript on one line, quoted, with quotes, backslashes and line breaks escaped. */
    static String quote(String text) {
        StringBuilder out = new StringBuilder(text.length() + 2).append('"');
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '"' || c == '\\') out.append('\\').append(c);
            else if (c == '\n') out.append("\\n");
            else if (c == '\r') out.append("\\r");
            else if (c == '\t') out.append("\\t");
            else out.append(c);
        }
        return out.append('"').toString();
    }

    /**
     * {@code TAG key=value key=value ...} from alternating keys and values. A value that is empty or
     * holds a space or a quote is quoted, so every line splits on spaces into {@code key=value} words
     * except inside a quoted value.
     */
    static String line(String tag, String... keyValues) {
        StringBuilder out = new StringBuilder(tag);
        for (int i = 0; i + 1 < keyValues.length; i += 2) {
            String value = keyValues[i + 1];
            boolean plain = !value.isEmpty() && value.indexOf(' ') < 0 && value.indexOf('"') < 0
                && value.indexOf('\n') < 0 && value.indexOf('\\') < 0;
            out.append(' ').append(keyValues[i]).append('=').append(plain ? value : quote(value));
        }
        return out.toString();
    }
}
