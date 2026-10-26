package com.termux.app.launcher.widget;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Home's icon size (editor-redesign §6, §8): three presets on the key Settings › Home has always
 * used ({@code app_launcher_home_icon_scale}), the sizes of AOSP Launcher3's phone and tablet grid
 * profiles. The Home card and Settings › Home offer the same three; the dock sizes its own icons.
 *
 * <p>The key once held a percentage of the dock's 48 dp (80 to 120). Such a value is read as the
 * preset nearest the size it drew, so an upgrade keeps each icon as close to its old size as the
 * presets allow. A key never written reads as {@link #SMALL}, the 48 dp the old default of
 * 100% drew, so no one's icons change size on upgrade; {@link #DEFAULT} is only the middle
 * preset's name.</p>
 */
public enum HomeIconSize {
    SMALL("small", 48),
    DEFAULT("default", 54),
    LARGE("large", 60);

    /** The size the dock's icons are drawn at, which the old percentages were taken of. */
    public static final int DOCK_ICON_DP = 48;

    /**
     * The preset a key never written reads as: the size the old default percentage, 100, drew
     * (48 dp), so an icon nobody sized keeps its size.
     */
    @NonNull public static final HomeIconSize UNSET = SMALL;

    /** What the key stores. */
    @NonNull public final String value;
    /** The icon's size in dp. */
    public final int dp;

    HomeIconSize(@NonNull String value, int dp) {
        this.value = value;
        this.dp = dp;
    }

    /** The icon's size as a multiple of the dock's 48 dp, as the Home icon views take it. */
    public float scale() {
        return dp / (float) DOCK_ICON_DP;
    }

    /**
     * The preset {@code stored} names: a preset's value, or an old percentage of 48 dp read as
     * the preset nearest the size it drew. Unset or unreadable is {@link #UNSET}, as the old
     * default of 100% reads.
     */
    @NonNull
    public static HomeIconSize fromStored(@Nullable String stored) {
        if (stored == null)
            return UNSET;
        String text = stored.trim();
        for (HomeIconSize size : values()) {
            if (size.value.equals(text))
                return size;
        }
        try {
            return nearestDp(Integer.parseInt(text) * DOCK_ICON_DP / 100f);
        } catch (NumberFormatException e) {
            return UNSET;
        }
    }

    /** The preset whose size is nearest {@code dp}; a tie goes to the smaller. */
    @NonNull
    public static HomeIconSize nearestDp(float dp) {
        HomeIconSize best = DEFAULT;
        float distance = Float.MAX_VALUE;
        for (HomeIconSize size : values()) {
            float d = Math.abs(size.dp - dp);
            if (d < distance) {
                distance = d;
                best = size;
            }
        }
        return best;
    }
}
