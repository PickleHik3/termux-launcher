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

    @Test public void floatTileTakesOneColumnAfterTheOthers() {
        assertEquals(5, PaletteTilePolicy.appSlots(6, false, false, true));
        assertEquals(4, PaletteTilePolicy.appSlots(6, false, true, true));
        assertEquals(3, PaletteTilePolicy.appSlots(6, true, true, true));
        assertEquals(6, PaletteTilePolicy.columnCount(3, true, true, true));
        // Off, nothing changes for an existing dock.
        assertEquals(PaletteTilePolicy.appSlots(6, true, true),
            PaletteTilePolicy.appSlots(6, true, true, false));
        assertEquals(PaletteTilePolicy.columnCount(4, true, true),
            PaletteTilePolicy.columnCount(4, true, true, false));
    }

    @Test public void bothOnTheirDefaultsStandPaletteThenFloatAtTheEnd() {
        int columns = PaletteTilePolicy.columnCount(4, false, true, true);
        int[] at = PaletteTilePolicy.tileColumns(-1, true, PaletteTilePolicy.DEFAULT_COLUMN,
            true, PaletteTilePolicy.DEFAULT_COLUMN, columns);
        assertArrayEquals(new int[] {-1, columns - 2, columns - 1}, at);
    }

    @Test public void floatAloneOnItsDefaultIsTheLastColumn() {
        assertArrayEquals(new int[] {-1, -1, 5}, PaletteTilePolicy.tileColumns(-1, false, -1,
            true, PaletteTilePolicy.DEFAULT_COLUMN, 6));
    }

    @Test public void aStoredFloatColumnStandsWhereItWasDragged() {
        assertArrayEquals(new int[] {0, 5, 2}, PaletteTilePolicy.tileColumns(0, true, 5,
            true, 2, 6));
        // Kept from a wider row: the last.
        assertArrayEquals(new int[] {-1, -1, 3}, PaletteTilePolicy.tileColumns(-1, false, -1,
            true, 9, 4));
    }

    @Test public void threeTilesNeverShareAColumnWhateverIsStored() {
        for (int columns = 3; columns <= 7; columns++) {
            for (int d = 0; d < columns; d++) {
                for (int p = -1; p < columns; p++) {
                    for (int f = -1; f < columns; f++) {
                        int[] at = PaletteTilePolicy.tileColumns(d, true, p, true, f, columns);
                        boolean[] taken = new boolean[columns];
                        for (int c : at) {
                            assertTrue(c >= 0 && c < columns);
                            assertFalse("shared column " + c, taken[c]);
                            taken[c] = true;
                        }
                    }
                }
            }
        }
    }

    @Test public void aDropOnAnotherTilesColumnSwapsTheTwo() {
        // A tile at 5 dragged onto the first other tile's column 2: that tile takes 5.
        assertArrayEquals(new int[] {2, 5, 0}, PaletteTilePolicy.dropped(2, 5, 2, 0));
        // Onto the second other tile.
        assertArrayEquals(new int[] {0, 4, 5}, PaletteTilePolicy.dropped(0, 5, 4, 0));
        assertArrayEquals(new int[] {2, 0, 5}, PaletteTilePolicy.dropped(2, 5, 0, 2));
        // Onto no tile: the others stay, and a missing tile stays missing.
        assertArrayEquals(new int[] {3, 1, -1}, PaletteTilePolicy.dropped(3, 5, 1, -1));
    }

    @Test public void theAppsKeepTheirOrderAroundThreeTiles() {
        int apps = 3;
        int columns = 6;
        int[][] places = {{0, 1, 2}, {5, 4, 3}, {1, 5, 3}, {2, 0, 5}};
        for (int[] t : places) {
            boolean[] taken = new boolean[columns];
            int last = -1;
            for (int app = 0; app < apps; app++) {
                int col = PaletteTilePolicy.slotOf(app, t[0], t[1], t[2], apps);
                assertTrue(col > last && col < columns);
                assertFalse(col == t[0] || col == t[1] || col == t[2]);
                assertFalse(taken[col]);
                taken[col] = true;
                last = col;
                assertEquals(app, PaletteTilePolicy.appAt(col, t[0], t[1], t[2], apps));
            }
            for (int tile : t) assertEquals(-1, PaletteTilePolicy.appAt(tile, t[0], t[1], t[2], apps));
        }
        assertEquals(-1, PaletteTilePolicy.slotOf(apps, 0, 1, 2, apps));
        // A missing tile: as the two-tile maths.
        for (int app = 0; app < 4; app++) {
            assertEquals(DrawerTilePolicy.slotOf(app, 1, 5, 4),
                PaletteTilePolicy.slotOf(app, 1, -1, 5, 4));
        }
    }

    @Test public void aNarrowRowStillKeepsOneAppWithAllThreeTiles() {
        assertEquals(1, PaletteTilePolicy.appSlots(3, true, true, true));
        assertEquals(1, PaletteTilePolicy.appSlots(1, false, false, true));
    }
}
