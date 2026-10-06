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

    /** The reference grid the design was drawn on: 88×92 dp cells with 8 dp gaps. */
    public static final int CELL_WIDTH_DP = 88;
    public static final int CELL_HEIGHT_DP = 92;
    public static final int GAP_DP = 8;
    /** How much smaller than its design a bucket may draw before the next one down is used. */
    private static final float TOLERANCE = 0.94f;

    public final int columns;
    public final int rows;
    /** The size the bucket was designed at, in dp. */
    public final int widthDp;
    public final int heightDp;

    BuiltinWidgetSpan(int columns, int rows) {
        this.columns = columns; this.rows = rows;
        widthDp = columns * CELL_WIDTH_DP + (columns - 1) * GAP_DP;
        heightDp = rows * CELL_HEIGHT_DP + (rows - 1) * GAP_DP;
    }

    /**
     * The largest bucket that fits in {@code widthDp}×{@code heightDp}: what a widget draws when
     * it has that much room. Cells differ from grid to grid — an 8×11 grid's cell is a third of
     * the design's — so the room is measured, never counted.
     */
    @NonNull public static BuiltinWidgetSpan forSize(float widthDp, float heightDp) {
        BuiltinWidgetSpan best = ONE_BY_ONE;
        for (BuiltinWidgetSpan span : values()) {
            if (widthDp >= span.widthDp * TOLERANCE && heightDp >= span.heightDp * TOLERANCE
                && span.area() >= best.area()) {
                best = span;
            }
        }
        return best;
    }

    private int area() { return columns * rows; }

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
