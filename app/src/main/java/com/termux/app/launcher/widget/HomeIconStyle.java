package com.termux.app.launcher.widget;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * How the app icons and folders on Home pages are drawn (Settings › Home › Icons and labels): the
 * icon's size against the dock's 48 dp, the label's size, whether a long name may take a second
 * line, and the soft shadow that keeps a white label legible on a light wallpaper. The dock draws
 * its own icons and is not touched by this.
 *
 * <p>{@link #DEFAULT}: the Default icon size (54 dp), 11 sp single-line labels with the shadow.
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

    /** The style of a Home nobody styled: an unset icon size reads as {@link HomeIconSize#UNSET}. */
    public static final HomeIconStyle DEFAULT =
        new HomeIconStyle(HomeIconSize.UNSET, LabelSize.DEFAULT, false, true);

    /** The icon's size: one of the three presets ({@link HomeIconSize}). */
    @NonNull public final HomeIconSize iconSize;
    @NonNull public final LabelSize labelSize;
    /** A long name may wrap onto a second line where the cell has the room. */
    public final boolean twoLines;
    /** The label's soft shadow. */
    public final boolean shadow;

    public HomeIconStyle(@NonNull HomeIconSize iconSize, @NonNull LabelSize labelSize,
                         boolean twoLines, boolean shadow) {
        this.iconSize = iconSize;
        this.labelSize = labelSize;
        this.twoLines = twoLines;
        this.shadow = shadow;
    }

    /** The icon's size as a multiple of 48 dp. */
    public float iconScale() {
        return iconSize.scale();
    }

    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof HomeIconStyle)) return false;
        HomeIconStyle that = (HomeIconStyle) other;
        return iconSize == that.iconSize && labelSize == that.labelSize
            && twoLines == that.twoLines && shadow == that.shadow;
    }

    @Override public int hashCode() {
        int result = iconSize.hashCode();
        result = 31 * result + labelSize.hashCode();
        result = 31 * result + (twoLines ? 1 : 0);
        return 31 * result + (shadow ? 1 : 0);
    }
}
