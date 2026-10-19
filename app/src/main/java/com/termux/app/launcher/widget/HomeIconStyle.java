package com.termux.app.launcher.widget;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * How the app icons and folders on Home pages are drawn (Settings › Home › Icons and labels): the
 * icon's size against the dock's 48 dp, the label's size, whether a long name may take a second
 * line, and the soft shadow that keeps a white label legible on a light wallpaper. The dock draws
 * its own icons and is not touched by this.
 *
 * <p>{@link #DEFAULT} is Home as it was: 48 dp icons, 11 sp single-line labels with the shadow.
 */
public final class HomeIconStyle {

    public enum LabelSize {
        SMALL("small", 10f),
        DEFAULT("default", HomeAppIconView.LABEL_SP),
        LARGE("large", 13f);

        @NonNull public final String value;
        public final float sp;

        LabelSize(@NonNull String value, float sp) {
            this.value = value;
            this.sp = sp;
        }

        @NonNull
        public static LabelSize fromValue(@Nullable String value) {
            for (LabelSize size : values()) if (size.value.equals(value)) return size;
            return DEFAULT;
        }
    }

    /** The icon sizes offered, as a percentage of the dock's 48 dp. */
    public static final int[] ICON_SCALES = {80, 90, 100, 110, 120};
    public static final int DEFAULT_ICON_SCALE = 100;

    public static final HomeIconStyle DEFAULT =
        new HomeIconStyle(DEFAULT_ICON_SCALE, LabelSize.DEFAULT, false, true);

    /** The icon's size as a percentage of 48 dp: one of {@link #ICON_SCALES}. */
    public final int iconScalePercent;
    @NonNull public final LabelSize labelSize;
    /** A long name may wrap onto a second line where the cell has the room. */
    public final boolean twoLines;
    /** The label's soft shadow. */
    public final boolean shadow;

    public HomeIconStyle(int iconScalePercent, @NonNull LabelSize labelSize, boolean twoLines,
                         boolean shadow) {
        this.iconScalePercent = nearestScale(iconScalePercent);
        this.labelSize = labelSize;
        this.twoLines = twoLines;
        this.shadow = shadow;
    }

    /** The icon's size as a multiple of 48 dp. */
    public float iconScale() {
        return iconScalePercent / 100f;
    }

    /** The offered size nearest {@code percent}; anything unreadable is 100. */
    public static int nearestScale(int percent) {
        int best = DEFAULT_ICON_SCALE;
        int distance = Integer.MAX_VALUE;
        for (int scale : ICON_SCALES) {
            int d = Math.abs(scale - percent);
            if (d < distance) {
                distance = d;
                best = scale;
            }
        }
        return best;
    }

    /** A stored percentage, read leniently: unset or not a number is the default. */
    public static int parseScale(@Nullable String stored) {
        if (stored == null) return DEFAULT_ICON_SCALE;
        try {
            return nearestScale(Integer.parseInt(stored.trim()));
        } catch (NumberFormatException e) {
            return DEFAULT_ICON_SCALE;
        }
    }

    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof HomeIconStyle)) return false;
        HomeIconStyle that = (HomeIconStyle) other;
        return iconScalePercent == that.iconScalePercent && labelSize == that.labelSize
            && twoLines == that.twoLines && shadow == that.shadow;
    }

    @Override public int hashCode() {
        int result = iconScalePercent;
        result = 31 * result + labelSize.hashCode();
        result = 31 * result + (twoLines ? 1 : 0);
        return 31 * result + (shadow ? 1 : 0);
    }
}
