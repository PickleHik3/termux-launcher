package com.termux.app.dock;

import static org.junit.Assert.assertEquals;

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
}
