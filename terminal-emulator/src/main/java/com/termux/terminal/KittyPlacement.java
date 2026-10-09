package com.termux.terminal;

import android.graphics.Bitmap;

/**
 * One kitty graphics placement ({@code a=T} or {@code a=p}) as kitty itself keeps it: a layer over
 * the cells, not a stamp in them.
 *
 * <p>A placement is anchored to the {@link TerminalRow} its top-left cell is on, so it scrolls with
 * the text, goes into the scrollback with it and dies when that row is erased by scrolling out of
 * the transcript. The cells under it keep whatever text and colours they had: a {@code z < 0}
 * placement is drawn over the cell backgrounds and under the text, one with {@code z >= 0} over
 * both, and writing text never punches a hole in a picture.</p>
 *
 * <p>The pixels are drawn straight from the source bitmap — normally the store's own decoded
 * image, shared, never copied — through {@link #sourceX}..{@link #sourceHeight} (the crop) into a
 * rectangle {@link #width} x {@link #height} pixels big, {@link #offsetX}/{@link #offsetY} into the
 * anchor cell. Those sizes are in the cell pixels the terminal reported to the program
 * ({@link #cellWidth} x {@link #cellHeight}); the renderer scales them once, filtered, by the ratio
 * between its real cell and that one. So moving or re-cropping a placement is a field update, never
 * a decode, a crop or a composite.</p>
 *
 * <p>All fields are written on the terminal's update thread and read by the renderer on the same
 * thread.</p>
 */
public final class KittyPlacement {

    final long imageId;
    final long placementId;

    int z;
    int column;
    /** The row the top-left cell is on; null once that row was erased or the placement deleted. */
    TerminalRow anchor;

    Bitmap bitmap;
    /** Whether {@link #bitmap} belongs to this placement rather than to the image store. */
    boolean ownsBitmap;
    /** Bytes counted against the session for an owned bitmap; 0 for a shared one. */
    long ownedBytes;

    int sourceX, sourceY, sourceWidth, sourceHeight;
    int width, height;
    int offsetX, offsetY;
    int cellWidth, cellHeight;
    /** How many cells across and down the placement covers, clipped at the right screen edge. */
    int columns, rows;

    /** Filled by the collectors: the external row of the anchor at the time of collection. */
    int row;

    KittyPlacement(long imageId, long placementId) {
        this.imageId = imageId;
        this.placementId = placementId;
    }

    public long getImageId() { return imageId; }
    public long getPlacementId() { return placementId; }
    public int getZ() { return z; }
    public int getColumn() { return column; }
    /** The external row of the anchor as of the last collection (negative in the scrollback). */
    public int getRow() { return row; }
    public Bitmap getBitmap() { return bitmap; }
    public int getSourceX() { return sourceX; }
    public int getSourceY() { return sourceY; }
    public int getSourceWidth() { return sourceWidth; }
    public int getSourceHeight() { return sourceHeight; }
    public int getWidth() { return width; }
    public int getHeight() { return height; }
    public int getOffsetX() { return offsetX; }
    public int getOffsetY() { return offsetY; }
    public int getCellWidth() { return cellWidth; }
    public int getCellHeight() { return cellHeight; }
    public int getColumns() { return columns; }
    public int getRows() { return rows; }

    /** Clip the placement's cell span to a screen this many columns wide, keeping it on screen. */
    void fitToColumns(int screenColumns) {
        column = Math.max(0, Math.min(column, screenColumns - 1));
        int wanted = Math.max(1, (width + offsetX + Math.max(1, cellWidth) - 1) / Math.max(1, cellWidth));
        columns = Math.max(1, Math.min(wanted, screenColumns - column));
    }

    /** Whether this placement covers the cell at {@code column}, {@code externalRow}. */
    boolean covers(int column, int externalRow) {
        return column >= this.column && column < this.column + columns
            && externalRow >= row && externalRow < row + rows;
    }

    /** Whether any of its rows lies in {@code [firstRow, firstRow + count)}. */
    boolean intersectsRows(int firstRow, int count) {
        return row < firstRow + count && row + rows > firstRow;
    }

    /**
     * A placement built outside the protocol, for tests of the layer and of the renderer that
     * draws it. Not anchored to anything until a buffer takes it.
     */
    public static KittyPlacement forTest(long imageId, long placementId, Bitmap bitmap, int z,
                                         int column, int row, int sourceX, int sourceY,
                                         int sourceWidth, int sourceHeight, int width, int height,
                                         int offsetX, int offsetY, int cellWidth, int cellHeight) {
        KittyPlacement placement = new KittyPlacement(imageId, placementId);
        placement.bitmap = bitmap;
        placement.z = z;
        placement.column = column;
        placement.row = row;
        placement.sourceX = sourceX;
        placement.sourceY = sourceY;
        placement.sourceWidth = sourceWidth;
        placement.sourceHeight = sourceHeight;
        placement.width = width;
        placement.height = height;
        placement.offsetX = offsetX;
        placement.offsetY = offsetY;
        placement.cellWidth = cellWidth;
        placement.cellHeight = cellHeight;
        placement.columns = Math.max(1, (width + offsetX + cellWidth - 1) / cellWidth);
        placement.rows = Math.max(1, (height + offsetY + cellHeight - 1) / cellHeight);
        return placement;
    }
}
