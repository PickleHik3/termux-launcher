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
