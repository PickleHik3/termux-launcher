package com.termux.app.dock;

/**
 * The <b>Command palette tile</b> (editor-redesign wave 6): a tile like the apps' on the dock's
 * apps row, which opens the command palette with the keyboard. It is the row's trailing slot, so
 * the row reads apps, then the Drawer tile where it stands, then the palette tile at the end
 * whatever the Drawer tile's own place (start, middle or end). The Search field is a band of its
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
}
