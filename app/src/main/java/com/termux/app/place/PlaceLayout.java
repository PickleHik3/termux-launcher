package com.termux.app.place;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Locale;

/**
 * One place's arrangement, already resolved for one orientation: what stands on which edge, whether
 * the alphabets row rides along, how the keyboard behaves and how big the widget grid is.
 *
 * <p>Immutable and value-equal, so a caller can hold the last one it applied and compare rather
 * than re-deriving the chrome on every pass. Everything that decides <em>what is on screen and
 * where</em> for a place lives here; {@link PlaceLayoutStore} is the only thing that builds one.
 */
public final class PlaceLayout {

    /** A screen edge. The status bar always stands on one of them — it is never hidden. */
    public enum Edge {
        TOP, BOTTOM, LEFT, RIGHT;

        @NonNull
        public String storageValue() {
            return name().toLowerCase(Locale.ROOT);
        }

        @NonNull
        public static Edge parse(@Nullable String value, @NonNull Edge fallback) {
            if (value != null) {
                for (Edge edge : values()) {
                    if (edge.storageValue().equals(value)) return edge;
                }
            }
            return fallback;
        }
    }

    /** Where a chrome row stands: along the bottom, as a column on one edge, or nowhere at all. */
    public enum RowPlacement {
        BOTTOM, LEFT, RIGHT, HIDDEN;

        /** A column on a screen edge rather than a row along the bottom. */
        public boolean isOnSide() {
            return this == LEFT || this == RIGHT;
        }

        public boolean isOnRight() {
            return this == RIGHT;
        }

        @NonNull
        public String storageValue() {
            return name().toLowerCase(Locale.ROOT);
        }

        @NonNull
        public static RowPlacement parse(@Nullable String value, @NonNull RowPlacement fallback) {
            if (value != null) {
                for (RowPlacement placement : values()) {
                    if (placement.storageValue().equals(value)) return placement;
                }
            }
            return fallback;
        }
    }

    /** What an open keyboard does to the place under it. */
    public enum KeyboardMode {
        /** The place shrinks to whatever the keyboard leaves. */
        RESIZE,
        /** The keyboard floats over the place, which keeps its size. */
        OVERLAY;

        @NonNull
        public String storageValue() {
            return name().toLowerCase(Locale.ROOT);
        }

        @NonNull
        public static KeyboardMode parse(@Nullable String value, @NonNull KeyboardMode fallback) {
            if (value != null) {
                for (KeyboardMode mode : values()) {
                    if (mode.storageValue().equals(value)) return mode;
                }
            }
            return fallback;
        }
    }

    @NonNull public final Edge statusBarEdge;
    @NonNull public final RowPlacement appsRow;
    public final boolean azRowShown;
    @NonNull public final RowPlacement extraKeys;
    @NonNull public final KeyboardMode keyboardMode;
    public final int widgetColumns;
    public final int widgetRows;

    public PlaceLayout(@NonNull Edge statusBarEdge, @NonNull RowPlacement appsRow,
                       boolean azRowShown, @NonNull RowPlacement extraKeys,
                       @NonNull KeyboardMode keyboardMode, int widgetColumns, int widgetRows) {
        this.statusBarEdge = statusBarEdge;
        this.appsRow = appsRow;
        this.azRowShown = azRowShown;
        this.extraKeys = extraKeys;
        this.keyboardMode = keyboardMode;
        this.widgetColumns = widgetColumns;
        this.widgetRows = widgetRows;
    }

    @Override
    public boolean equals(@Nullable Object other) {
        if (this == other) return true;
        if (!(other instanceof PlaceLayout)) return false;
        PlaceLayout that = (PlaceLayout) other;
        return azRowShown == that.azRowShown
            && widgetColumns == that.widgetColumns
            && widgetRows == that.widgetRows
            && statusBarEdge == that.statusBarEdge
            && appsRow == that.appsRow
            && extraKeys == that.extraKeys
            && keyboardMode == that.keyboardMode;
    }

    @Override
    public int hashCode() {
        int result = statusBarEdge.hashCode();
        result = 31 * result + appsRow.hashCode();
        result = 31 * result + (azRowShown ? 1 : 0);
        result = 31 * result + extraKeys.hashCode();
        result = 31 * result + keyboardMode.hashCode();
        result = 31 * result + widgetColumns;
        result = 31 * result + widgetRows;
        return result;
    }

    @NonNull
    @Override
    public String toString() {
        return "PlaceLayout{status=" + statusBarEdge
            + ", apps=" + appsRow
            + ", az=" + azRowShown
            + ", keys=" + extraKeys
            + ", keyboard=" + keyboardMode
            + ", grid=" + widgetColumns + "x" + widgetRows
            + "}";
    }
}
