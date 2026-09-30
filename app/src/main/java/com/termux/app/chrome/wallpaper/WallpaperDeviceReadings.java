package com.termux.app.chrome.wallpaper;

/**
 * Maps the raw numbers the system reports (thermal status, battery level and plug) to the bands
 * {@link WallpaperDirector.Conditions} takes. The values are repeated from {@code PowerManager} and
 * {@code BatteryManager} so a plain JUnit test drives this.
 */
final class WallpaperDeviceReadings {

    /** PowerManager.THERMAL_STATUS_LIGHT. */
    static final int THERMAL_LIGHT = 1;
    /** PowerManager.THERMAL_STATUS_MODERATE; this and worse pause the background. */
    static final int THERMAL_MODERATE = 2;
    /** Battery percent at or under which the rate drops while discharging. */
    static final int LOW_BATTERY_PERCENT = 15;

    private WallpaperDeviceReadings() {}

    static WallpaperDirector.Thermal thermal(int status) {
        if (status >= THERMAL_MODERATE) return WallpaperDirector.Thermal.MODERATE_OR_WORSE;
        if (status == THERMAL_LIGHT) return WallpaperDirector.Thermal.LIGHT;
        return WallpaperDirector.Thermal.NONE;
    }

    /** Percent from a battery level and scale, or -1 when either is unknown. */
    static int percent(int level, int scale) {
        if (level < 0 || scale <= 0) return -1;
        return Math.round(level * 100f / scale);
    }

    /**
     * Low and not on a charger. {@code plugged} is BatteryManager.EXTRA_PLUGGED (0 = on battery);
     * {@code lowBroadcast} is true between ACTION_BATTERY_LOW and ACTION_BATTERY_OKAY, the
     * system's own idea of low.
     */
    static boolean lowWhileDischarging(int percent, int plugged, boolean lowBroadcast) {
        if (plugged != 0) return false;
        return lowBroadcast || (percent >= 0 && percent <= LOW_BATTERY_PERCENT);
    }
}
