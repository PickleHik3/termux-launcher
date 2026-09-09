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

        /** A column down one side of the screen rather than a row along the top or the bottom. */
        public boolean isOnSide() {
            return this == LEFT || this == RIGHT;
        }

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

    /**
     * The shape the on-screen keyboard takes on this place: the full-width row along the bottom it
     * has always been, a narrower frame floating over the content, or that same bottom row parted
     * in the middle for two thumbs.
     *
     * <p>Not the same question as {@link KeyboardMode}, which says what an open keyboard does to
     * the place under it, and not a keyboard <em>layout</em>, which is how the keys are arranged.
     */
    public enum KeyboardForm {
        /** Full width along the bottom edge. */
        DOCKED,
        /** A narrower frame the user can move, always over the content. */
        FLOATING,
        /** Along the bottom, with every row parted at its midpoint. */
        SPLIT;

        @NonNull
        public String storageValue() {
            return name().toLowerCase(Locale.ROOT);
        }

        /**
         * The type {@code delta} steps along the cycle — docked, floating, split, and round again.
         * A backward cycle key is the same call with {@code -1}.
         */
        @NonNull
        public KeyboardForm cycled(int delta) {
            KeyboardForm[] forms = values();
            int step = ((ordinal() + delta) % forms.length + forms.length) % forms.length;
            return forms[step];
        }

        @NonNull
        public static KeyboardForm parse(@Nullable String value, @NonNull KeyboardForm fallback) {
            if (value != null) {
                for (KeyboardForm form : values()) {
                    if (form.storageValue().equals(value)) return form;
                }
            }
            return fallback;
        }
    }

    @NonNull public final Edge statusBarEdge;
    @NonNull public final RowPlacement appsRow;
    public final boolean azRowShown;
    /** Where the alphabets bar stands when it rides on its own; ignored while it sits under the
     *  apps row, where it always rides along the bottom regardless of what is stored here. */
    @NonNull public final Edge azBarEdge;
    @NonNull public final RowPlacement extraKeys;
    @NonNull public final KeyboardMode keyboardMode;
    @NonNull public final KeyboardForm keyboardForm;
    public final int widgetColumns;
    public final int widgetRows;

    public PlaceLayout(@NonNull Edge statusBarEdge, @NonNull RowPlacement appsRow,
                       boolean azRowShown, @NonNull Edge azBarEdge, @NonNull RowPlacement extraKeys,
                       @NonNull KeyboardMode keyboardMode, @NonNull KeyboardForm keyboardForm,
                       int widgetColumns, int widgetRows) {
        this.statusBarEdge = statusBarEdge;
        this.appsRow = appsRow;
        this.azRowShown = azRowShown;
        this.azBarEdge = azBarEdge;
        this.extraKeys = extraKeys;
        this.keyboardMode = keyboardMode;
        this.keyboardForm = keyboardForm;
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
            && azBarEdge == that.azBarEdge
            && extraKeys == that.extraKeys
            && keyboardMode == that.keyboardMode
            && keyboardForm == that.keyboardForm;
    }

    @Override
    public int hashCode() {
        int result = statusBarEdge.hashCode();
        result = 31 * result + appsRow.hashCode();
        result = 31 * result + (azRowShown ? 1 : 0);
        result = 31 * result + azBarEdge.hashCode();
        result = 31 * result + extraKeys.hashCode();
        result = 31 * result + keyboardMode.hashCode();
        result = 31 * result + keyboardForm.hashCode();
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
            + ", azEdge=" + azBarEdge
            + ", keys=" + extraKeys
            + ", keyboard=" + keyboardMode
            + ", form=" + keyboardForm
            + ", grid=" + widgetColumns + "x" + widgetRows
            + "}";
    }
}
