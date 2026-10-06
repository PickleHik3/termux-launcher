package com.termux.app.launcher.widget.builtin;

import androidx.annotation.NonNull;

/**
 * The five layouts every built-in widget is designed at. A cell span on the grid snaps to one of
 * them — the largest bucket the span contains — so a widget stretched to 3×1 draws its 2×1
 * layout with room to breathe rather than a sixth layout nobody designed. This is the size-bucket
 * strategy the platform recommends for app widgets, applied to the launcher's own.
 */
public enum BuiltinWidgetSpan {
    ONE_BY_ONE(1, 1),
    TWO_BY_ONE(2, 1),
    TWO_BY_TWO(2, 2),
    FOUR_BY_ONE(4, 1),
    FOUR_BY_TWO(4, 2);

    public final int columns;
    public final int rows;

    BuiltinWidgetSpan(int columns, int rows) { this.columns = columns; this.rows = rows; }

    /** The bucket a {@code columns}×{@code rows} cell span draws as. */
    @NonNull public static BuiltinWidgetSpan forCells(int columns, int rows) {
        boolean wide = columns >= 4;
        boolean tall = rows >= 2;
        if (columns <= 1) return ONE_BY_ONE;
        if (wide) return tall ? FOUR_BY_TWO : FOUR_BY_ONE;
        return tall ? TWO_BY_TWO : TWO_BY_ONE;
    }

    public boolean isWide() { return columns >= 4; }
    public boolean isTall() { return rows >= 2; }
}
