package com.termux.app.dock;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Arrays;
import java.util.List;

/**
 * The Wall strip's first row (spec "Wall strip"): the Workspace's terminals as tiles above Home's
 * apps row, a large clock beside them while there are few. Home floats are terminals of the same
 * session and stand in the row as tiles too, after the row's own windows; the row counts them
 * like any terminal.
 *
 * <p>The row shares the apps row's side inset, so the clock's left edge lines up with the first
 * icon and the last tile ends where the last icon ends. The clock takes a share of the row
 * ({@link #clockShare}): {@link #CLOCK_SHARE_FEW} with none or one terminal, {@link
 * #CLOCK_SHARE_TWO} with two, none from three on, when it slides out to the left. The tiles split
 * what is left equally, the {@code +} tile among them and always their size; past
 * {@link #MAX_FITTED_TERMINALS} they keep the width four terminals had and the row scrolls,
 * {@code +} at its end.</p>
 *
 * <p>The clock is a cell of the row like the tiles: its share is of the row plus one gap, and the
 * gap after it is the row's ordinary gap, so there is no extra space between the clock and the
 * terminal area. A share between the rest values is what the clock's slide draws through, and the
 * row stays continuous all the way to no clock.</p>
 *
 * <p>Pure: the view hands it pixels and draws what it answers.</p>
 */
public final class WallStripRowPolicy {

    /** The clock's share of the row with no terminal or one. */
    public static final float CLOCK_SHARE_FEW = 0.56f;
    /** The clock's share with two terminals: clock and terminal area half and half. */
    public static final float CLOCK_SHARE_TWO = 0.50f;
    /** The most terminals the row fits before it scrolls; past it the tiles keep this width. */
    public static final int MAX_FITTED_TERMINALS = 4;
    /** The clock's slide in or out as the terminal count crosses two and three, ms. */
    public static final long CLOCK_SLIDE_MS = 420L;

    /**
     * The row's height, as each layout keeps it per orientation (the Layout editor's Strip
     * handle): today's 84 dp until the user drags it. The least keeps every tile a full 48 dp
     * target with its title on one line and its split shape still drawn; the most is half again
     * the shipped row, so the terminal over the dock keeps its room.
     */
    public static final int DEFAULT_HEIGHT_DP = 84;
    public static final int MIN_HEIGHT_DP = 56;
    public static final int MAX_HEIGHT_DP = 128;
    /** One step of the height, for the canvas's accessibility actions and keys. */
    public static final int HEIGHT_STEP_DP = 4;

    /**
     * The largest the clock grows at {@link #DEFAULT_HEIGHT_DP}: the status bar's own largest fit
     * ({@code TerminalClockWidget.FIT_MAX_SCALE}), so at today's height the Strip's clock is the
     * size the unfolded bar draws at its tallest.
     */
    public static final float CLOCK_MAX_SCALE_AT_DEFAULT =
        com.termux.app.terminal.TerminalClockWidget.FIT_MAX_SCALE;

    private WallStripRowPolicy() {}

    /** A stored or dragged row height held to its range. */
    public static int clampHeightDp(int heightDp) {
        return Math.max(MIN_HEIGHT_DP, Math.min(MAX_HEIGHT_DP, heightDp));
    }

    /**
     * The largest the clock's face may scale in a row {@code rowHeightPx} tall: the bar's largest
     * fit at the shipped height, in proportion to the row, so the clock grows and shrinks with
     * the row as its tiles do. The face still fits the width it is given. The default row's while
     * the row is not measured.
     */
    public static float clockMaxScale(float rowHeightPx, float density) {
        if (rowHeightPx <= 0f || density <= 0f) return CLOCK_MAX_SCALE_AT_DEFAULT;
        return CLOCK_MAX_SCALE_AT_DEFAULT * rowHeightPx / (DEFAULT_HEIGHT_DP * density);
    }

    /** The clock's share of the row at rest with {@code terminals} open. */
    public static float clockShare(int terminals) {
        if (terminals <= 1) return CLOCK_SHARE_FEW;
        if (terminals == 2) return CLOCK_SHARE_TWO;
        return 0f;
    }

    /** Whether the clock stands in the row at rest with {@code terminals} open. */
    public static boolean showsClock(int terminals) {
        return clockShare(terminals) > 0f;
    }

    /**
     * Whether going from {@code before} to {@code after} terminals slides the clock in or out:
     * the count crossed two and three, either way.
     */
    public static boolean slidesClock(int before, int after) {
        return showsClock(Math.max(0, before)) != showsClock(Math.max(0, after));
    }

    /**
     * A tile's corner radius: the dock's radius less the padding between the slab's edge and the
     * row, so the tiles nest in the slab. Never below zero.
     */
    public static float tileRadiusPx(float dockRadiusPx, float dockPaddingPx) {
        return Math.max(0f, dockRadiusPx - Math.max(0f, dockPaddingPx));
    }

    /** One row: the clock's room and every tile's place, in the row view's own pixels. */
    public static final class Row {
        /** The clock's width; zero once it has slid out. */
        public final float clockWidthPx;
        /** Where the terminal area starts, at rest the clock's end plus the row's gap. */
        public final float tilesLeftPx;
        /** Every tile's width, the {@code +} tile's too. */
        public final float tileWidthPx;
        /** Terminal tiles, then the {@code +} tile last: each tile's left edge, before scrolling. */
        @NonNull private final float[] mTileLefts;
        /** How far the row's content reaches past the row's own right inset; zero when it fits. */
        public final float maxScrollPx;
        /** Every tile's corner radius. */
        public final float tileRadiusPx;

        Row(float clockWidthPx, float tilesLeftPx, float tileWidthPx, @NonNull float[] tileLefts,
            float maxScrollPx, float tileRadiusPx) {
            this.clockWidthPx = clockWidthPx;
            this.tilesLeftPx = tilesLeftPx;
            this.tileWidthPx = tileWidthPx;
            mTileLefts = tileLefts;
            this.maxScrollPx = maxScrollPx;
            this.tileRadiusPx = tileRadiusPx;
        }

        /** Terminal tiles plus the {@code +} tile. */
        public int tileCount() {
            return mTileLefts.length;
        }

        /** The left edge of tile {@code index}; the {@code +} tile is the last. */
        public float tileLeft(int index) {
            return mTileLefts[index];
        }

        /** The {@code +} tile's index. */
        public int addTileIndex() {
            return mTileLefts.length - 1;
        }

        /** Whether the clock has any room. */
        public boolean showsClock() {
            return clockWidthPx > 0f;
        }

        /** Whether the tiles reach past the row and it scrolls. */
        public boolean scrolls() {
            return maxScrollPx > 0f;
        }

        /**
         * The tile under {@code x}, in the row view's pixels with the row scrolled by
         * {@code scrollPx}; -1 over the clock or a gap.
         */
        public int tileAt(float x, float scrollPx) {
            float content = x + scrollPx;
            for (int i = 0; i < mTileLefts.length; i++) {
                if (content >= mTileLefts[i] && content < mTileLefts[i] + tileWidthPx) return i;
            }
            return -1;
        }

        @Override
        public boolean equals(@Nullable Object o) {
            if (this == o) return true;
            if (!(o instanceof Row)) return false;
            Row that = (Row) o;
            return clockWidthPx == that.clockWidthPx && tilesLeftPx == that.tilesLeftPx
                && tileWidthPx == that.tileWidthPx && maxScrollPx == that.maxScrollPx
                && tileRadiusPx == that.tileRadiusPx && Arrays.equals(mTileLefts, that.mTileLefts);
        }

        @Override
        public int hashCode() {
            int h = Arrays.hashCode(mTileLefts);
            h = 31 * h + Float.floatToIntBits(clockWidthPx);
            h = 31 * h + Float.floatToIntBits(tileWidthPx);
            return 31 * h + Float.floatToIntBits(maxScrollPx);
        }
    }

    /**
     * The row's side inset: where the apps row's first slot draws its icon ({@code firstIconPx},
     * NaN while no icon is laid out), but never closer to the slab's side than
     * {@code slabInsetPx}, the spec's 14 dp inside the slab. Icons laid out to the edges of a slab
     * that runs to the screen's (Docked, no Margin) would otherwise put the clock and the
     * {@code +} tile on the screen's edges.
     */
    public static float sideInsetPx(float firstIconPx, float slabInsetPx) {
        float floor = Math.max(0f, slabInsetPx);
        return firstIconPx > floor ? firstIconPx : floor;
    }

    /** The row at rest with {@code terminals} open. */
    @NonNull
    public static Row layout(float rowWidthPx, float insetPx, float gapPx, int terminals,
                             float dockRadiusPx, float dockPaddingPx) {
        return layout(rowWidthPx, insetPx, gapPx, terminals, dockRadiusPx, dockPaddingPx,
            clockShare(terminals));
    }

    /**
     * The row with the clock at {@code clockShare} of it, for the clock's slide between its rest
     * shares ({@link #clockShare}); the tiles take whatever the clock leaves.
     */
    @NonNull
    public static Row layout(float rowWidthPx, float insetPx, float gapPx, int terminals,
                             float dockRadiusPx, float dockPaddingPx, float clockShare) {
        int n = Math.max(0, terminals);
        float inset = Math.max(0f, insetPx);
        float gap = Math.max(0f, gapPx);
        float content = Math.max(0f, rowWidthPx - 2f * inset);
        float share = Math.max(0f, Math.min(1f, clockShare));
        // The clock is a cell: its share is of the row plus the one gap that follows it, so at
        // rest it ends a gap short of the terminal area, and at zero share both are gone.
        float clockSpan = Math.min(content, share * (content + gap));
        float clockWidth = Math.max(0f, clockSpan - gap);
        float area = content - clockSpan;
        int fitted = Math.min(n, MAX_FITTED_TERMINALS);
        float tileWidth = Math.max(0f, (area - fitted * gap) / (fitted + 1));
        float[] lefts = new float[n + 1];
        float x = inset + clockSpan;
        for (int i = 0; i <= n; i++) {
            lefts[i] = x;
            x += tileWidth + gap;
        }
        float contentRight = lefts[n] + tileWidth;
        float maxScroll = Math.max(0f, contentRight - (inset + content));
        // Sub-pixel overshoot from the float sums is not a reason to scroll.
        if (maxScroll < 0.5f) maxScroll = 0f;
        return new Row(clockWidth, inset + clockSpan, tileWidth, lefts, maxScroll,
            tileRadiusPx(dockRadiusPx, dockPaddingPx));
    }

    /**
     * The scroll that shows tile {@code index} whole, from {@code scrollPx}: the current tile is
     * brought into view when the row is rebound. Held to the row's scroll range.
     */
    public static float scrollToShow(@NonNull Row row, int index, float scrollPx, float insetPx,
                                     float rowWidthPx) {
        float scroll = Math.max(0f, Math.min(row.maxScrollPx, scrollPx));
        if (index < 0 || index >= row.tileCount()) return scroll;
        float left = row.tileLeft(index) - scroll;
        float right = left + row.tileWidthPx;
        float viewLeft = Math.max(0f, insetPx);
        float viewRight = rowWidthPx - Math.max(0f, insetPx);
        if (left < viewLeft) scroll -= viewLeft - left;
        else if (right > viewRight) scroll += right - viewRight;
        return Math.max(0f, Math.min(row.maxScrollPx, scroll));
    }

    /**
     * How a terminal tile's title and split shape share it ({@link #tileText}): one title line or
     * two, whether the process glyph is drawn, the label's room and the shape's height.
     */
    public static final class TileText {
        /** Whether the label has a line of its own under the number's. */
        public final boolean twoLines;
        /** Whether the process glyph is drawn after the number. */
        public final boolean showsGlyph;
        /** The label's room on its line; the label ellipsizes into it. */
        public final float labelRoomPx;
        /** The split shape's height from the tile's top padding; zero when it has no room. */
        public final float shapeHeightPx;

        TileText(boolean twoLines, boolean showsGlyph, float labelRoomPx, float shapeHeightPx) {
            this.twoLines = twoLines;
            this.showsGlyph = showsGlyph;
            this.labelRoomPx = labelRoomPx;
            this.shapeHeightPx = shapeHeightPx;
        }

        @NonNull @Override public String toString() {
            return "TileText{" + (twoLines ? "two lines" : "one line")
                + (showsGlyph ? ", glyph" : "") + ", label " + labelRoomPx + ", shape "
                + shapeHeightPx + "}";
        }
    }

    /**
     * A terminal tile's title under its split shape. The title is the window's number (and a
     * float's mark), its process glyph, then its label, on one line while the label fits there
     * whole, or keeps at least {@code minLabelPx}; the glyph goes first when room runs short.
     * When the line leaves the label less than that, as at a raised font scale, the label takes a
     * line of its own under the number and the shape shrinks to make room: the shape gives way
     * before the title does, and is left out when less than {@code minShapePx} of it would show.
     * The number is always kept.
     *
     * @param headWidthPx the number and, for a float, its mark, with the gap after them
     * @param glyphWidthPx the process glyph with the gap after it; zero for none
     * @param labelWidthPx the whole label's width; zero for none
     */
    @NonNull
    public static TileText tileText(float tileWidthPx, float tileHeightPx, float padPx,
                                    float lineHeightPx, float titleGapPx, float headWidthPx,
                                    float glyphWidthPx, float labelWidthPx, float minLabelPx,
                                    float minShapePx) {
        float inner = Math.max(0f, tileWidthPx - 2f * padPx);
        float glyph = Math.max(0f, glyphWidthPx);
        float label = Math.max(0f, labelWidthPx);
        float line = inner - Math.max(0f, headWidthPx);
        float oneLineShape = shapeHeight(tileHeightPx, padPx, lineHeightPx, titleGapPx, 1,
            minShapePx);
        if (label <= 0f) {
            return new TileText(false, glyph > 0f && glyph <= line, 0f, oneLineShape);
        }
        if (glyph > 0f && label <= line - glyph) {
            return new TileText(false, true, line - glyph, oneLineShape);
        }
        if (label <= line || line >= minLabelPx) {
            return new TileText(false, false, Math.max(0f, line), oneLineShape);
        }
        if (2f * lineHeightPx + 2f * padPx <= tileHeightPx) {
            return new TileText(true, glyph > 0f && glyph <= line, inner,
                shapeHeight(tileHeightPx, padPx, lineHeightPx, titleGapPx, 2, minShapePx));
        }
        return new TileText(false, false, Math.max(0f, line), oneLineShape);
    }

    private static float shapeHeight(float tileHeightPx, float padPx, float lineHeightPx,
                                     float titleGapPx, int lines, float minShapePx) {
        float height = tileHeightPx - 2f * padPx - lines * lineHeightPx - titleGapPx;
        return height >= Math.max(1f, minShapePx) ? height : 0f;
    }

    /**
     * A window's split shape for its tile: each pane's cell rectangle ({@code left, top, columns,
     * rows}, as {@code TerminalPaneController.paneCellGeometry} gives it) as fractions of the
     * window's extent, four floats a pane (left, top, right, bottom). Null entries are skipped; a
     * window with no sized pane reads as one whole pane.
     */
    @NonNull
    public static float[] splitShape(@Nullable List<int[]> paneCells) {
        int right = 0;
        int bottom = 0;
        int count = 0;
        if (paneCells != null) {
            for (int[] cell : paneCells) {
                if (cell == null || cell.length < 4 || cell[2] <= 0 || cell[3] <= 0) continue;
                right = Math.max(right, cell[0] + cell[2]);
                bottom = Math.max(bottom, cell[1] + cell[3]);
                count++;
            }
        }
        if (count == 0 || right <= 0 || bottom <= 0) return new float[]{0f, 0f, 1f, 1f};
        float[] shape = new float[count * 4];
        int at = 0;
        for (int[] cell : paneCells) {
            if (cell == null || cell.length < 4 || cell[2] <= 0 || cell[3] <= 0) continue;
            shape[at++] = Math.max(0f, cell[0] / (float) right);
            shape[at++] = Math.max(0f, cell[1] / (float) bottom);
            shape[at++] = Math.min(1f, (cell[0] + cell[2]) / (float) right);
            shape[at++] = Math.min(1f, (cell[1] + cell[3]) / (float) bottom);
        }
        return shape;
    }
}
