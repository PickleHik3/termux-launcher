package com.termux.app.dock;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class PaletteTilePolicyTest {

    @Test public void noTilesKeepsEverySlotForApps() {
        assertEquals(6, PaletteTilePolicy.appSlots(6, false, false));
        assertEquals(6, PaletteTilePolicy.columnCount(6, false, false));
    }

    @Test public void paletteAloneTakesOneSlotAtTheEnd() {
        assertEquals(5, PaletteTilePolicy.appSlots(6, false, true));
        assertEquals(6, PaletteTilePolicy.columnCount(5, false, true));
        assertEquals(5, PaletteTilePolicy.column(5, false));
    }

    @Test public void drawerAloneTakesOneSlot() {
        assertEquals(5, PaletteTilePolicy.appSlots(6, true, false));
        assertEquals(6, PaletteTilePolicy.columnCount(5, true, false));
    }

    @Test public void bothTilesTakeTwoAndPaletteStandsAfterTheDrawerTile() {
        assertEquals(4, PaletteTilePolicy.appSlots(6, true, true));
        assertEquals(6, PaletteTilePolicy.columnCount(4, true, true));
        // Even with the Drawer tile at the end (column 4), the palette is after it.
        assertEquals(5, PaletteTilePolicy.column(4, true));
        assertEquals(4, DrawerTilePolicy.tileSlot(DrawerTilePolicy.Position.END, 4));
    }

    @Test public void aNarrowRowStillKeepsOneApp() {
        assertEquals(1, PaletteTilePolicy.appSlots(2, true, true));
        assertEquals(1, PaletteTilePolicy.appSlots(1, false, true));
    }

    /** The still columns, the ones a page swipe holds: the tiles', never an app's. */
    @Test public void theStillColumnsAreTheTilesSlots() {
        // None: every column is a page's.
        assertArrayEquals(new int[0], PaletteTilePolicy.stillColumns(null, false, 7, false));
        // The palette alone: the row's last column.
        assertArrayEquals(new int[] {6}, PaletteTilePolicy.stillColumns(null, true, 6, false));
        // The Drawer tile alone, at each of its places.
        assertArrayEquals(new int[] {0}, PaletteTilePolicy.stillColumns(
            DrawerTilePolicy.Position.START, false, 6, false));
        assertArrayEquals(new int[] {3}, PaletteTilePolicy.stillColumns(
            DrawerTilePolicy.Position.MIDDLE, false, 6, false));
        assertArrayEquals(new int[] {6}, PaletteTilePolicy.stillColumns(
            DrawerTilePolicy.Position.END, false, 6, false));
        // Both: the Drawer tile where it stands, the palette after it at the end.
        assertArrayEquals(new int[] {0, 6}, PaletteTilePolicy.stillColumns(
            DrawerTilePolicy.Position.START, true, 5, false));
        assertArrayEquals(new int[] {2, 6}, PaletteTilePolicy.stillColumns(
            DrawerTilePolicy.Position.MIDDLE, true, 5, false));
        assertArrayEquals(new int[] {5, 6}, PaletteTilePolicy.stillColumns(
            DrawerTilePolicy.Position.END, true, 5, false));
    }

    @Test public void theStillColumnsAreNeverAnAppsAndTheAppsFillTheRest() {
        for (DrawerTilePolicy.Position drawer : new DrawerTilePolicy.Position[] {null,
                DrawerTilePolicy.Position.START, DrawerTilePolicy.Position.MIDDLE,
                DrawerTilePolicy.Position.END}) {
            for (boolean palette : new boolean[] {false, true}) {
                int apps = PaletteTilePolicy.appSlots(7, drawer != null, palette);
                int columns = PaletteTilePolicy.columnCount(apps, drawer != null, palette);
                assertEquals(7, columns);
                int[] still = PaletteTilePolicy.stillColumns(drawer, palette, apps, false);
                assertEquals((drawer != null ? 1 : 0) + (palette ? 1 : 0), still.length);
                boolean[] taken = new boolean[columns];
                for (int c : still) taken[c] = true;
                for (int app = 0; app < apps; app++) {
                    int col = drawer == null ? app : DrawerTilePolicy.slotOf(app, drawer, apps);
                    assertFalse(drawer + "/" + palette + " app " + app + " on a tile", taken[col]);
                    taken[col] = true;
                }
                for (int c = 0; c < columns; c++)
                    assertTrue(drawer + "/" + palette + " column " + c + " empty", taken[c]);
            }
        }
    }

    @Test public void aRailHoldsNoStillColumns() {
        assertArrayEquals(new int[0], PaletteTilePolicy.stillColumns(
            DrawerTilePolicy.Position.END, true, 5, true));
        assertArrayEquals(new int[0], PaletteTilePolicy.stillColumns(null, true, 6, true));
    }
}
