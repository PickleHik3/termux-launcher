package com.termux.app.launcher.widget.builtin;

import androidx.annotation.NonNull;

/**
 * The five layouts every built-in widget is designed at. A widget's bucket is chosen from the
 * cells it spans and the room those cells give, the way Android expects a widget to behave: the
 * bucket of its cell span is kept as long as the room meets the platform's minimum for it (the
 * handheld ranges of the widget design guide: a 2×1 must work from 109×56 dp, a 4×2 from
 * 245×115 dp), and a larger bucket is drawn only when the room reaches that bucket's design size,
 * so a widget stretched to 3×1 draws its 2×1 layout with room to breathe rather than a sixth
 * layout nobody designed. Counting alone would squash a 2×1 on a dense grid; measuring alone
 * cannot tell two small rows from one big one (a two-row minimum, 115 dp, is less than one row of
 * the default grid). The layouts are drawn at the design sizes below and must not clip anywhere
 * between their minimum and well past the design.
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

    /** How much of a bucket's design size the room must reach before the bucket is drawn. */
    private static final float DESIGN_FIT = 0.94f;

    /**
     * The bucket for a widget spanning {@code cellColumns}×{@code cellRows} cells that give it
     * {@code widthDp}×{@code heightDp} of room. Each axis is decided alone: the span's own count
     * holds while the room meets its minimum, and a larger count is drawn when the room meets
     * its design. Below the one-row minimum there is only the 1×1.
     */
    @NonNull public static BuiltinWidgetSpan forSize(float widthDp, float heightDp,
                                                    int cellColumns, int cellRows) {
        int columns = axis(widthDp, cellColumns, FOUR_BY_ONE, TWO_BY_ONE, true);
        int rows = axis(heightDp, cellRows, TWO_BY_TWO, TWO_BY_ONE, false);
        if (columns == 1 || rows == 0) return ONE_BY_ONE;
        return forCells(columns, rows);
    }

    /** The bucket for room whose cell span is not known: by design size alone, as a preview. */
    @NonNull public static BuiltinWidgetSpan forSize(float widthDp, float heightDp) {
        return forSize(widthDp, heightDp, 1, 1);
    }

    /**
     * One axis: the larger count's cells when the span has them and the room meets their
     * minimum, or when the room meets their design; else the smaller count's on the same terms;
     * else 1 — or, for rows, 0 when even one row's minimum is not met.
     */
    private static int axis(float roomDp, int cells, BuiltinWidgetSpan larger,
                            BuiltinWidgetSpan smaller, boolean width) {
        int largerCount = width ? larger.columns : larger.rows;
        int smallerCount = width ? smaller.columns : smaller.rows;
        if (fits(roomDp, cells, largerCount, width ? larger.minWidthDp : larger.minHeightDp,
            width ? larger.widthDp : larger.heightDp)) return largerCount;
        if (fits(roomDp, cells, smallerCount, width ? smaller.minWidthDp : smaller.minHeightDp,
            width ? smaller.widthDp : smaller.heightDp)) return smallerCount;
        return width || roomDp >= smaller.minHeightDp ? 1 : 0;
    }

    private static boolean fits(float roomDp, int cells, int count, int minDp, int designDp) {
        return (cells >= count && roomDp >= minDp) || roomDp >= designDp * DESIGN_FIT;
    }

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
