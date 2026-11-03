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
}
