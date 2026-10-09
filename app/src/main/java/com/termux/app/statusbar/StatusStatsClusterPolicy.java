package com.termux.app.statusbar;

import androidx.annotation.NonNull;

import com.termux.app.wall.PaneWallPage;

/**
 * The CPU/RAM/weather cluster's order and centring: everywhere else it keeps the row's end in its
 * usual CPU · RAM · Weather order; on the Widgets place it reads Weather · RAM · CPU and centres
 * in the row instead. Both are place-driven, not width-driven, so a stat toggling on or off in
 * Settings or the weather text changing length never has to re-derive anything.
 */
public final class StatusStatsClusterPolicy {

    private StatusStatsClusterPolicy() {}

    /** Whether the cluster reads Weather · RAM · CPU and centres in the row, instead of the
     *  default CPU · RAM · Weather at the row's end. */
    public static boolean centeredReversed(@NonNull PaneWallPage page) {
        return page == PaneWallPage.WIDGETS;
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

    /** The dot between the CPU and RAM widgets, wherever that pair sits in the row: on only when
     *  CPU shows and something follows it (RAM, or weather standing in for a hidden RAM). */
    public static boolean cpuRamDotVisible(boolean cpuOn, boolean ramOn, boolean weatherOn) {
        return cpuOn && (ramOn || weatherOn);
    }

    /** The dot between the RAM and weather widgets, wherever that pair sits in the row: on only
     *  when both show. */
    public static boolean ramWeatherDotVisible(boolean ramOn, boolean weatherOn) {
        return ramOn && weatherOn;
    }
}
