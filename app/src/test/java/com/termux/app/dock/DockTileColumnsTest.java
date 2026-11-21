package com.termux.app.dock;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.termux.app.dock.DrawerTilePolicy.Place;
import com.termux.app.dock.DrawerTilePolicy.Position;

import org.junit.Test;

/**
 * The still tiles in any column of the dock row: the Drawer tile's stored anchors read as the
 * columns they always stood in, a drop on the nearest column, a swap where the two tiles meet,
 * and the apps in order around both tiles wherever they stand.
 */
public class DockTileColumnsTest {

    private static final int SLOTS = 7;

    /** The row's apps and columns for {@code SLOTS} slots and the tiles on. */
    private static int apps(boolean drawer, boolean palette) {
        return PaletteTilePolicy.appSlots(SLOTS, drawer, palette);
    }

    private static int columns(boolean drawer, boolean palette) {
        return PaletteTilePolicy.columnCount(apps(drawer, palette), drawer, palette);
    }

    @Test public void eachAnchorMigratesToTheColumnItAlwaysStoodIn() {
        // The Drawer tile alone: 6 apps, 7 columns.
        int apps = apps(true, false);
        int columns = columns(true, false);
        assertEquals(0, Place.of(Position.START).columnIn(apps, columns));
        assertEquals("the end is the row's last", 6, Place.of(Position.END).columnIn(apps, columns));
        assertEquals("the middle is the apps' centre", 3,
            Place.of(Position.MIDDLE).columnIn(apps, columns));
        for (Position anchor : Position.values()) {
            assertEquals(anchor + ": as the slot it always took",
                DrawerTilePolicy.tileSlot(anchor, apps), Place.of(anchor).columnIn(apps, columns));
        }

        // With the palette tile: 5 apps; the end stands before the palette, which is the last.
        apps = apps(true, true);
        columns = columns(true, true);
        int palette = PaletteTilePolicy.column(PaletteTilePolicy.DEFAULT_COLUMN, columns);
        assertEquals("the palette defaults to the row's last", 6, palette);
        assertEquals(0, Place.of(Position.START).columnIn(apps, columns));
        assertEquals(2, Place.of(Position.MIDDLE).columnIn(apps, columns));
        assertEquals(5, Place.of(Position.END).columnIn(apps, columns));
        for (Position anchor : Position.values()) {
            int drawer = Place.of(anchor).columnIn(apps, columns);
            assertArrayEquals(anchor + ": the two never meet", new int[] {drawer, palette},
                DrawerTilePolicy.apart(drawer, palette, columns));
            // The same row as before columns: the old still columns.
            assertArrayEquals(anchor + ": as it was laid",
                PaletteTilePolicy.stillColumns(anchor, true, apps, false),
                PaletteTilePolicy.stillColumns(drawer, palette, false));
        }
    }

    @Test public void theStoredPlaceReadsBack() {
        for (Position anchor : Position.values()) {
            assertEquals(Place.of(anchor),
                DrawerTilePolicy.parsePlace(DrawerTilePolicy.persisted(Place.of(anchor))));
        }
        assertEquals("start", DrawerTilePolicy.persisted(Place.of(Position.START)));
        assertEquals("4", DrawerTilePolicy.persisted(Place.at(4)));
        assertEquals(Place.at(4), DrawerTilePolicy.parsePlace("4"));
        assertEquals(Place.at(0), DrawerTilePolicy.parsePlace(" 0 "));
        assertEquals(DrawerTilePolicy.DEFAULT_PLACE, DrawerTilePolicy.parsePlace(null));
        assertEquals(DrawerTilePolicy.DEFAULT_PLACE, DrawerTilePolicy.parsePlace("-2"));
        assertEquals(DrawerTilePolicy.DEFAULT_PLACE, DrawerTilePolicy.parsePlace("left"));
        assertTrue(DrawerTilePolicy.isPlaceValue("end"));
        assertTrue(DrawerTilePolicy.isPlaceValue("3"));
        assertFalse(DrawerTilePolicy.isPlaceValue("sideways"));
        assertFalse(DrawerTilePolicy.isPlaceValue("-1"));
        assertFalse(DrawerTilePolicy.isPlaceValue(null));
    }

    @Test public void aColumnKeptFromAWiderRowStandsInsideTheRow() {
        assertEquals(6, Place.at(9).columnIn(6, 7));
        assertEquals(3, Place.at(3).columnIn(6, 7));
        assertEquals(6, PaletteTilePolicy.column(12, 7));
        assertEquals(2, PaletteTilePolicy.column(2, 7));
        assertEquals(0, PaletteTilePolicy.column(PaletteTilePolicy.DEFAULT_COLUMN, 1));
    }

    @Test public void aDropLandsOnTheNearestColumn() {
        assertEquals(0, DrawerTilePolicy.dropColumn(-0.3f, 7));
        assertEquals(0, DrawerTilePolicy.dropColumn(0.1f, 7));
        assertEquals(1, DrawerTilePolicy.dropColumn(0.15f, 7));
        assertEquals(3, DrawerTilePolicy.dropColumn(0.5f, 7));
        assertEquals(5, DrawerTilePolicy.dropColumn(0.85f, 7));
        assertEquals(6, DrawerTilePolicy.dropColumn(0.9f, 7));
        assertEquals(6, DrawerTilePolicy.dropColumn(1.4f, 7));
        assertEquals(0, DrawerTilePolicy.dropColumn(Float.NaN, 7));
        // Each column's own centre lands on it.
        for (int c = 0; c < 7; c++) {
            assertEquals(c, DrawerTilePolicy.dropColumn((c + 0.5f) / 7f, 7));
        }
    }

    @Test public void aDropOnTheOtherTilesColumnSwapsThem() {
        assertArrayEquals("onto the other: they swap", new int[] {6, 0},
            DrawerTilePolicy.dropped(6, 0, 6));
        assertArrayEquals("elsewhere: the other stays", new int[] {3, 6},
            DrawerTilePolicy.dropped(3, 0, 6));
        assertArrayEquals("no other tile", new int[] {4, -1},
            DrawerTilePolicy.dropped(4, 1, -1));
        // Tiles meeting from stored columns (a palette turned on over a dragged Drawer tile).
        assertArrayEquals(new int[] {5, 6}, DrawerTilePolicy.apart(6, 6, 7));
        assertArrayEquals(new int[] {1, 0}, DrawerTilePolicy.apart(0, 0, 7));
        assertArrayEquals(new int[] {2, 4}, DrawerTilePolicy.apart(2, 4, 7));
        assertArrayEquals(new int[] {-1, 4}, DrawerTilePolicy.apart(-1, 4, 7));
    }

    @Test public void theAppsKeepTheirOrderAroundBothTilesInAnyOrder() {
        int apps = apps(true, true);
        int columns = columns(true, true);
        for (int a = 0; a < columns; a++) {
            for (int b = 0; b < columns; b++) {
                if (a == b) continue;
                boolean[] taken = new boolean[columns];
                int last = -1;
                for (int app = 0; app < apps; app++) {
                    int col = DrawerTilePolicy.slotOf(app, a, b, apps);
                    String name = "tiles " + a + "," + b + " app " + app;
                    assertTrue(name + ": in the row", col >= 0 && col < columns);
                    assertTrue(name + ": never a tile's column", col != a && col != b);
                    assertTrue(name + ": in order", col > last);
                    assertFalse(name + ": its own column", taken[col]);
                    taken[col] = true;
                    last = col;
                    assertEquals(name + ": read back", app,
                        DrawerTilePolicy.appAt(col, a, b, apps));
                    assertEquals(name + ": either order", col,
                        DrawerTilePolicy.slotOf(app, b, a, apps));
                }
                assertEquals(-1, DrawerTilePolicy.appAt(a, a, b, apps));
                assertEquals(-1, DrawerTilePolicy.appAt(b, a, b, apps));
            }
        }
        assertEquals(-1, DrawerTilePolicy.appAt(-1, 0, 6, apps));
        assertEquals(-1, DrawerTilePolicy.appAt(columns, 0, 6, apps));
        assertEquals(-1, DrawerTilePolicy.slotOf(apps, 0, 6, apps));
        // One tile, the other -1: as the single-tile maths.
        for (Position anchor : Position.values()) {
            int n = apps(true, false);
            int home = DrawerTilePolicy.tileSlot(anchor, n);
            for (int app = 0; app < n; app++) {
                assertEquals(DrawerTilePolicy.slotOf(app, anchor, n),
                    DrawerTilePolicy.slotOf(app, home, -1, n));
                assertEquals(DrawerTilePolicy.slotOf(app, anchor, n),
                    DrawerTilePolicy.slotOf(app, -1, home, n));
            }
        }
    }

    /**
     * Lens beads: the row's still columns, ascending, are exactly the columns no app takes, for
     * every pair of columns, so each tile keeps its own bead wherever it stands.
     */
    @Test public void theStillColumnsAreTheTilesColumnsAscending() {
        int apps = apps(true, true);
        int columns = columns(true, true);
        for (int d = 0; d < columns; d++) {
            for (int p = 0; p < columns; p++) {
                if (d == p) continue;
                int[] still = PaletteTilePolicy.stillColumns(d, p, false);
                assertArrayEquals(new int[] {Math.min(d, p), Math.max(d, p)}, still);
                boolean[] app = new boolean[columns];
                for (int i = 0; i < apps; i++) app[DrawerTilePolicy.slotOf(i, d, p, apps)] = true;
                for (int c = 0; c < columns; c++)
                    assertEquals(d + "," + p + " column " + c, c == still[0] || c == still[1], !app[c]);
            }
        }
        assertArrayEquals(new int[] {2}, PaletteTilePolicy.stillColumns(2, -1, false));
        assertArrayEquals(new int[] {0}, PaletteTilePolicy.stillColumns(-1, 0, false));
        assertArrayEquals(new int[0], PaletteTilePolicy.stillColumns(-1, -1, false));
        assertArrayEquals("a rail holds none", new int[0],
            PaletteTilePolicy.stillColumns(1, 5, true));
    }
}
