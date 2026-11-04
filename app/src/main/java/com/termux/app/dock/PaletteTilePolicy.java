package com.termux.app.dock;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * The <b>Command palette tile</b> (editor-redesign wave 6): a tile like the apps' on the dock's
 * apps row, which opens the command palette with the keyboard. It stands in the row's last
 * column until the user drags it to another ({@link #column(int, int)}, a global setting; the
 * Drawer tile's column is per layout), and the two never share one. The Search field is a band of its
 * own under the row, so it never shares a slot with either tile: the three combine freely.
 *
 * <p>Pure: the row asks how many slots the apps keep and which column the tile takes.</p>
 */
public final class PaletteTilePolicy {

    private PaletteTilePolicy() {}

    /** How many apps fit in a row of {@code slots} tiles beside the tiles that stand. */
    public static int appSlots(int slots, boolean drawerTile, boolean paletteTile) {
        int tiles = (drawerTile ? 1 : 0) + (paletteTile ? 1 : 0);
        return Math.max(tiles > 0 ? 1 : 0, Math.max(1, slots) - tiles);
    }

    /** The row's columns: its apps and the tiles that stand. */
    public static int columnCount(int appSlots, boolean drawerTile, boolean paletteTile) {
        return Math.max(0, appSlots) + (drawerTile ? 1 : 0) + (paletteTile ? 1 : 0);
    }

    /** The palette tile's column: after the apps and the Drawer tile. */
    public static int column(int appSlots, boolean drawerTile) {
        return Math.max(0, appSlots) + (drawerTile ? 1 : 0);
    }

    /**
     * The row's columns that hold a tile rather than a page's app, ascending: the Drawer tile's
     * where {@code drawer} stands (null for none) and the palette tile's where it is on, around a
     * page of {@code appSlots} apps. Neither tile is any page's, so a page swipe or switch holds
     * both still where they stand and each keeps its own Lens bead
     * ({@link LensBeadGeometry#follow(LensBeadGeometry.Beads, float, float[], int, float[], int,
     * float, float, float)}); the staged page's apps stand around them as they will once it lands.
     * A rail ({@code vertical}) lays neither tile, so it holds none.
     */
    @NonNull
    public static int[] stillColumns(@Nullable DrawerTilePolicy.Position drawer,
                                     boolean paletteTile, int appSlots, boolean vertical) {
        if (vertical) return new int[0];
        int apps = Math.max(0, appSlots);
        int tiles = (drawer != null ? 1 : 0) + (paletteTile ? 1 : 0);
        int[] columns = new int[tiles];
        int n = 0;
        if (drawer != null) columns[n++] = DrawerTilePolicy.tileSlot(drawer, apps);
        if (paletteTile) columns[n] = column(apps, drawer != null);
        return columns;
    }

    /** The stored column of a palette tile never dragged: the row's last. */
    public static final int DEFAULT_COLUMN = -1;

    /**
     * The palette tile's column in a row of {@code columns} columns, from the stored column
     * ({@code stored} below 0, nothing stored, is the row's last: where it always stood). A
     * column kept from a wider row stands in the last.
     */
    public static int column(int stored, int columns) {
        int last = Math.max(0, columns - 1);
        if (stored < 0) return last;
        return Math.min(last, stored);
    }

    /**
     * The row's still columns, ascending, from the tiles' own columns ({@code drawer} and {@code
     * palette} -1 for a tile the row does not hold), in either order. A rail holds none.
     */
    @NonNull
    public static int[] stillColumns(int drawer, int palette, boolean vertical) {
        if (vertical) return new int[0];
        if (drawer < 0 && palette < 0) return new int[0];
        if (drawer < 0) return new int[] {palette};
        if (palette < 0) return new int[] {drawer};
        return new int[] {Math.min(drawer, palette), Math.max(drawer, palette)};
    }

    // ---- The New float terminal tile: a third still tile, in a column of its own (the row's last
    // until it is dragged, like the palette tile's), swapping with whichever tile it is dropped on.

    /** {@link #appSlots(int, boolean, boolean)} with the Float tile's column taken too. */
    public static int appSlots(int slots, boolean drawerTile, boolean paletteTile,
                               boolean floatTile) {
        int tiles = (drawerTile ? 1 : 0) + (paletteTile ? 1 : 0) + (floatTile ? 1 : 0);
        return Math.max(tiles > 0 ? 1 : 0, Math.max(1, slots) - tiles);
    }

    /** {@link #columnCount(int, boolean, boolean)} with the Float tile's column. */
    public static int columnCount(int appSlots, boolean drawerTile, boolean paletteTile,
                                  boolean floatTile) {
        return columnCount(appSlots, drawerTile, paletteTile) + (floatTile ? 1 : 0);
    }

    /**
     * The three tiles' columns, {@code {drawer, palette, float}}, in a row of {@code columns}
     * columns, each -1 for a tile the row does not hold. {@code drawer} is the Drawer tile's
     * wanted column; {@code palette} and {@code floatTile} are the stored ones ({@link
     * #DEFAULT_COLUMN} the row's last, a column kept from a wider row the last). Where wants meet
     * on one column the Float tile keeps it, then the palette tile, then the Drawer tile, and the
     * one that gives way takes the nearest free column toward the start, else toward the end: so
     * both tiles on their defaults stand palette, Float at the row's end, and no two ever share
     * a column.
     */
    @NonNull
    public static int[] tileColumns(int drawer, boolean paletteOn, int palette,
                                    boolean floatOn, int floatTile, int columns) {
        int n = Math.max(1, columns);
        boolean[] taken = new boolean[n];
        int f = floatOn ? claim(column(floatTile, n), taken) : -1;
        int p = paletteOn ? claim(column(palette, n), taken) : -1;
        int d = drawer >= 0 ? claim(Math.min(drawer, n - 1), taken) : -1;
        return new int[] {d, p, f};
    }

    private static int claim(int wanted, @NonNull boolean[] taken) {
        if (!taken[wanted]) {
            taken[wanted] = true;
            return wanted;
        }
        for (int c = wanted - 1; c >= 0; c--) {
            if (!taken[c]) { taken[c] = true; return c; }
        }
        for (int c = wanted + 1; c < taken.length; c++) {
            if (!taken[c]) { taken[c] = true; return c; }
        }
        return -1;
    }

    /**
     * The three tiles' columns, {@code {moved, other1, other2}}, once the tile at {@code from} is
     * dropped on {@code to}: on another tile's column the two swap, so they never share one; a
     * tile at -1 (none) stays none.
     */
    @NonNull
    public static int[] dropped(int to, int from, int other1, int other2) {
        if (other1 >= 0 && to == other1) return new int[] {to, from, other2};
        if (other2 >= 0 && to == other2) return new int[] {to, other1, from};
        return new int[] {to, other1, other2};
    }

    /** The column app {@code app} of {@code apps} takes around the three tiles (-1 for none). */
    public static int slotOf(int app, int tileA, int tileB, int tileC, int apps) {
        if (app < 0 || app >= Math.max(0, apps)) return -1;
        int low = Math.min(tileA, Math.min(tileB, tileC));
        int high = Math.max(tileA, Math.max(tileB, tileC));
        int mid = tileA + tileB + tileC - low - high;
        int column = app;
        if (low >= 0 && column >= low) column++;
        if (mid >= 0 && column >= mid) column++;
        if (high >= 0 && column >= high) column++;
        return column;
    }

    /**
     * The app at {@code column} of a row of {@code apps} apps around the three tiles (-1 for a
     * tile the row does not hold), as its index among the apps, or -1 for a tile's column and a
     * column outside the row.
     */
    public static int appAt(int column, int tileA, int tileB, int tileC, int apps) {
        int columns = Math.max(0, apps) + (tileA >= 0 ? 1 : 0) + (tileB >= 0 ? 1 : 0)
            + (tileC >= 0 ? 1 : 0);
        if (column < 0 || column >= columns || column == tileA || column == tileB
            || column == tileC) return -1;
        int app = column;
        if (tileA >= 0 && tileA < column) app--;
        if (tileB >= 0 && tileB < column) app--;
        if (tileC >= 0 && tileC < column) app--;
        return app;
    }
}
