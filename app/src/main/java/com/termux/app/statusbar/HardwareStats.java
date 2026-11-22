package com.termux.app.statusbar;

import java.util.Collections;
import java.util.List;

/**
 * One reading of the hardware the stats card shows beyond CPU and memory: GPU load and clock,
 * CPU cluster clocks, and temperatures. Every number is "unknown" ({@link #UNKNOWN} or NaN) until a
 * node that supplies it exists and could be read; nothing is guessed. Pure, so plain JUnit tests it
 * together with its parse helpers.
 */
public final class HardwareStats {

    public static final int UNKNOWN = -1;

    /** One CPU frequency cluster (a cpufreq policy): the CPUs it covers and its clock. */
    public static final class Cluster {
        public final int firstCpu;
        public final int lastCpu;
        public final int curMhz;
        public final int maxMhz;

        public Cluster(int firstCpu, int lastCpu, int curMhz, int maxMhz) {
            this.firstCpu = firstCpu;
            this.lastCpu = lastCpu;
            this.curMhz = curMhz;
            this.maxMhz = maxMhz;
        }
    }

    /** GPU busy percent, 0..100, or {@link #UNKNOWN}. */
    public final int gpuBusyPercent;
    public final int gpuCurMhz;
    public final int gpuMaxMhz;
    /** Hottest zone of each class in degrees Celsius, NaN when unknown. */
    public final float cpuTempC;
    public final float gpuTempC;
    public final float socTempC;
    public final List<Cluster> clusters;

    public HardwareStats(int gpuBusyPercent, int gpuCurMhz, int gpuMaxMhz, float cpuTempC,
                         float gpuTempC, float socTempC, List<Cluster> clusters) {
        this.gpuBusyPercent = gpuBusyPercent;
        this.gpuCurMhz = gpuCurMhz;
        this.gpuMaxMhz = gpuMaxMhz;
        this.cpuTempC = cpuTempC;
        this.gpuTempC = gpuTempC;
        this.socTempC = socTempC;
        this.clusters = Collections.unmodifiableList(clusters);
    }

    /** The units a frequency node is written in. */
    public enum Unit { HZ, KHZ, MHZ }

    /**
     * GPU busy as a percent: "34 %", "34", or a devfreq load "34@585000000Hz". {@link #UNKNOWN}
     * when it does not start with a number; values are clamped to 0..100.
     */
    public static int parseBusyPercent(String raw) {
        if (raw == null) return UNKNOWN;
        String s = raw.trim();
        int end = 0;
        while (end < s.length() && Character.isDigit(s.charAt(end))) end++;
        if (end == 0 || end > 9) return UNKNOWN;
        return Math.min(100, Integer.parseInt(s.substring(0, end)));
    }

    /** A frequency in whole MHz, or {@link #UNKNOWN} for anything unreadable or not positive. */
    public static int parseMhz(String raw, Unit unit) {
        if (raw == null) return UNKNOWN;
        double value;
        try {
            value = Double.parseDouble(raw.trim());
        } catch (NumberFormatException e) {
            return UNKNOWN;
        }
        if (!(value > 0)) return UNKNOWN;
        double mhz;
        switch (unit) {
            case HZ: mhz = value / 1_000_000d; break;
            case KHZ: mhz = value / 1_000d; break;
            default: mhz = value; break;
        }
        long rounded = Math.round(mhz);
        return rounded <= 0 || rounded > 100_000 ? UNKNOWN : (int) rounded;
    }

    /**
     * A thermal zone reading in degrees Celsius. Kernels report millidegrees (a value above 1000 is
     * divided by 1000); anything outside 0..150 is a sensor fault and is dropped (NaN).
     */
    public static float parseTempC(String raw) {
        if (raw == null) return Float.NaN;
        float value;
        try {
            value = Float.parseFloat(raw.trim());
        } catch (NumberFormatException e) {
            return Float.NaN;
        }
        if (value > 1000f) value /= 1000f;
        if (Float.isNaN(value) || value < 0f || value > 150f) return Float.NaN;
        return value;
    }

    /**
     * The first and last CPU of a {@code related_cpus} value: "0 1 2 3" gives {0, 3}. Ranges such
     * as "0-3" and comma lists are read too. Null when there is no CPU number in it.
     */
    public static int[] parseRelatedCpus(String raw) {
        if (raw == null) return null;
        int min = Integer.MAX_VALUE;
        int max = -1;
        for (String token : raw.trim().split("[\\s,]+")) {
            if (token.isEmpty()) continue;
            String[] ends = token.split("-", -1);
            if (ends.length > 2) return null;
            try {
                int a = Integer.parseInt(ends[0]);
                int b = ends.length == 2 ? Integer.parseInt(ends[1]) : a;
                if (a < 0 || b < a) return null;
                min = Math.min(min, a);
                max = Math.max(max, b);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return max < 0 ? null : new int[] {min, max};
    }
}
