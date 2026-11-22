package com.termux.app.statusbar;

import androidx.annotation.NonNull;
import com.termux.app.page.PageKind;


/**
 * The CPU/RAM/battery/weather cluster's order and centring: it keeps the row's end in its usual
 * CPU · RAM · Weather order on every page since the bar is one across the wall (#90); the Widgets
 * place used to read Weather · RAM · CPU and centre in the row instead ({@link
 * #centeredReversed}). Both are place-driven, not width-driven, so a stat toggling on or off in
 * Settings or the weather text changing length never has to re-derive anything.
 */
public final class StatusStatsClusterPolicy {

    private StatusStatsClusterPolicy() {}

    /**
     * Whether the cluster reads Weather · RAM · CPU and centres in the row, instead of the
     * default CPU · RAM · Weather at the row's end: on no page since the bar is one across the
     * wall (#90). Home's bar carries the window chips as every window's does, so its readings keep
     * the row's end and the bar stands still on the swipe.
     */
    public static boolean centeredReversed(@NonNull PageKind page) {
        return false;
    }

    /**
     * Which of the weather's forms, longest first, the centred cluster has room for on the
     * Widgets place: the first whose cluster fits in {@code room}, else the last (the shortest).
     * The cluster needs {@code others} for everything but the weather widget, and the widget
     * needs {@code base} (icon and margin) plus its text, never less than {@code minWidth}.
     */
    public static int weatherFormThatFits(@NonNull float[] formWidths, float others, float base,
                                          float minWidth, float room) {
        for (int i = 0; i < formWidths.length; i++) {
            if (others + Math.max(minWidth, base + formWidths[i]) <= room) return i;
        }
        return Math.max(0, formWidths.length - 1);
    }

    /**
     * The width a cluster centred on {@code center} can take and still stay between {@code low}
     * and {@code high}: twice the distance to the nearer bound.
     */
    public static float centeredRoom(float center, float low, float high) {
        return Math.max(0f, 2f * Math.min(center - low, high - center));
    }

    /**
     * Where a cluster of {@code width} starts: centred on {@code center} where that stays between
     * {@code low} and {@code high}, otherwise pulled in off-centre. When it is wider than the gap,
     * the side the end widgets are on wins ({@code endWidgetsHigh}: they are at {@code high}), so
     * nothing is drawn over them.
     */
    public static float clusterStart(float center, float width, float low, float high,
                                     boolean endWidgetsHigh) {
        float start = center - width / 2f;
        if (endWidgetsHigh) {
            start = Math.max(start, low);
            start = Math.min(start, high - width);
        } else {
            start = Math.min(start, high - width);
            start = Math.max(start, low);
        }
        return start;
    }

    /**
     * Whether the CPU widget shows: its switch is on and a privileged backend can read
     * {@code /proc/stat}. Without one the reading could only ever be a dash, so it hides instead,
     * and the dots below are fed this rather than the switch so none is left orphaned.
     */
    public static boolean cpuShown(boolean cpuEnabled, boolean privilegedAvailable) {
        return cpuEnabled && privilegedAvailable;
    }

    /**
     * Whether the GPU widget shows: its switch is on and the last sample read a GPU load. A phone
     * that reports none (no node, no privilege) keeps it hidden rather than showing a dash.
     */
    public static boolean gpuShown(boolean gpuEnabled, boolean gpuKnown) {
        return gpuEnabled && gpuKnown;
    }

    /** The dot between CPU and GPU: on only when both show. */
    public static boolean cpuGpuDotVisible(boolean cpuOn, boolean gpuOn) {
        return cpuOn && gpuOn;
    }

    /** The dot after the GPU widget: on only when GPU shows and RAM, battery or weather follows. */
    public static boolean gpuRamDotVisible(boolean gpuOn, boolean ramOn, boolean batteryOn,
                                           boolean weatherOn) {
        return gpuOn && (ramOn || batteryOn || weatherOn);
    }

    /** The dot after the CPU widget when the GPU is not showing (the cluster reads CPU, GPU, RAM,
     *  battery, weather): on only when CPU shows, GPU does not, and something else follows it.
     *  With the GPU showing, the CPU-GPU dot carries that pair. */
    public static boolean cpuRamDotVisible(boolean cpuOn, boolean gpuOn, boolean ramOn,
                                           boolean batteryOn, boolean weatherOn) {
        return cpuOn && !gpuOn && (ramOn || batteryOn || weatherOn);
    }

    /** The dot after the RAM widget: on only when RAM shows and the battery or the weather
     *  follows it. */
    public static boolean ramWeatherDotVisible(boolean ramOn, boolean batteryOn,
                                               boolean weatherOn) {
        return ramOn && (batteryOn || weatherOn);
    }

    /** The dot after the battery widget: on only when both it and the weather show. */
    public static boolean batteryWeatherDotVisible(boolean batteryOn, boolean weatherOn) {
        return batteryOn && weatherOn;
    }
}
