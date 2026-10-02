package com.termux.app.chrome.wallpaper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/** Pure choices behind the wallpaper picker page, kept apart from views so they can be tested. */
public final class WallpaperPickerLogic {

    private WallpaperPickerLogic() {}

    /** Whether this tile is the stored background. */
    public static boolean isStored(@NonNull String tileId, @Nullable String storedId) {
        return tileId.equals(storedId);
    }

    /** Thumbnail width for a given height at the screen's portrait aspect, at least 1. */
    public static int thumbWidth(int thumbHeightPx, int screenW, int screenH) {
        int shortSide = Math.max(1, Math.min(screenW, screenH));
        int longSide = Math.max(1, Math.max(screenW, screenH));
        return Math.max(1, Math.round(thumbHeightPx * (shortSide / (float) longSide)));
    }

    /** Whether two slot choices name the same thing. Null equals only null. */
    public static boolean sameChoice(@Nullable WallpaperSlots.Choice a, @Nullable WallpaperSlots.Choice b) {
        if (a == b) return true;
        if (a == null || b == null) return false;
        if (a.sameAsHome != b.sameAsHome || a.photo != b.photo) return false;
        return a.animatedId == null ? b.animatedId == null : a.animatedId.equals(b.animatedId);
    }

    /** Apply is enabled only when the centred slot's pending choice differs from what is stored. */
    public static boolean applyEnabled(@Nullable WallpaperSlots.Choice pending,
                                       @Nullable WallpaperSlots.Choice stored, boolean busy) {
        return !busy && pending != null && !sameChoice(pending, stored);
    }

    /** The Same as Home tile leads the strip only while the Lock preview is centred. */
    public static boolean showsSameAsHome(@NonNull WallpaperSlots.Slot centred) {
        return centred == WallpaperSlots.Slot.LOCK;
    }

    /** The Motion toggle: Lock centred, and the lock live wallpaper exists on this API level. */
    public static boolean showsMotion(@NonNull WallpaperSlots.Slot centred, int sdkInt) {
        return centred == WallpaperSlots.Slot.LOCK && WallpaperSlots.lockLiveSupported(sdkInt);
    }

    /** Whether the Motion row has a place on the page at all (it keeps its height on Home). */
    public static boolean motionRowExists(int sdkInt) {
        return WallpaperSlots.lockLiveSupported(sdkInt);
    }

    /**
     * The lock preview's clock as the glyphs to draw: "9:05" in 12-hour time (no leading zero,
     * 12 for noon and midnight), "09:05" or "21:05" in 24-hour time. Only digits and ':'.
     */
    @NonNull
    public static String composedTime(int hourOfDay, int minute, boolean is24Hour) {
        int h = ((hourOfDay % 24) + 24) % 24;
        int m = ((minute % 60) + 60) % 60;
        String mm = (m < 10 ? "0" : "") + m;
        if (is24Hour) return (h < 10 ? "0" : "") + h + ":" + mm;
        int h12 = h % 12;
        if (h12 == 0) h12 = 12;
        return h12 + ":" + mm;
    }

    /** Milliseconds from {@code nowMs} to the start of the next minute, at least 1. */
    public static long millisToNextMinute(long nowMs) {
        long rem = 60_000L - (((nowMs % 60_000L) + 60_000L) % 60_000L);
        return Math.max(1L, rem);
    }
}
