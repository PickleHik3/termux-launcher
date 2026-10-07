package com.termux.app.launcher.widget.builtin;

import androidx.annotation.NonNull;

/**
 * The five layouts every built-in widget is designed at. The room a widget is given snaps to one
 * of them — the largest bucket whose Android minimum the room meets — so a widget stretched to
 * 3×1 draws its 2×1 layout with room to breathe rather than a sixth layout nobody designed. The
 * minimums are the handheld ranges of the platform's widget design guide (a 2×1 must work from
 * 109×56 dp, a 4×2 from 245×115 dp); the layouts are drawn at the design sizes below and must
 * not clip anywhere between the minimum and well past the design.
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

    public final int columns;
    public final int rows;
    /** The size the bucket was designed at, in dp. */
    public final int widthDp;
    public final int heightDp;
    /** The smallest room, in dp, the bucket is used at: Android's minimum for its cell span. */
    public final int minWidthDp;
    public final int minHeightDp;

    BuiltinWidgetSpan(int columns, int rows) {
        this.columns = columns; this.rows = rows;
        widthDp = columns * CELL_WIDTH_DP + (columns - 1) * GAP_DP;
        heightDp = rows * CELL_HEIGHT_DP + (rows - 1) * GAP_DP;
        // Android design guide, handheld: 2 cells = 109 wide, 4 cells = 245; 1 row = 56, 2 = 115.
        // The 1x1 has no range of its own: it is what is left below the 2-cell minimums.
        minWidthDp = columns == 1 ? 0 : columns == 2 ? 109 : 245;
        minHeightDp = columns == 1 ? 0 : rows == 1 ? 56 : 115;
    }

    /**
     * The largest bucket whose minimum fits in {@code widthDp}×{@code heightDp}: what a widget
     * draws when it has that much room. Cells differ from grid to grid — an 8×11 grid's cell is a third of
     * the design's — so the room is measured, never counted.
     */
    @NonNull public static BuiltinWidgetSpan forSize(float widthDp, float heightDp) {
        BuiltinWidgetSpan best = ONE_BY_ONE;
        for (BuiltinWidgetSpan span : values()) {
            if (widthDp >= span.minWidthDp && heightDp >= span.minHeightDp
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
