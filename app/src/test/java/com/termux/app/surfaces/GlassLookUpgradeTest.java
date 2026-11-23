package com.termux.app.surfaces;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;

import com.termux.shared.termux.settings.preferences.FrostStops;
import com.termux.shared.termux.settings.preferences.LookGrid;

import org.junit.Test;

/** The Frost, Depth and Shine upgrade over the grid: the table from the old values to the new. */
public class GlassLookUpgradeTest {

    private static LookGrid grid() {
        return new LookGrid(new LookGrid.MemoryStore());
    }

    @Test
    public void theOldShippedLooksKeepTheirStopAsTheNewLooks() {
        assertEquals("minimal", GlassLookUpgrade.map(FrostStops.CLEAR, 10, 0, 27,
            LookGrid.RIM_HAIRLINE, 40).lookId);
        assertEquals("frost", GlassLookUpgrade.map(FrostStops.MIST, 60, 0, 24,
            LookGrid.RIM_GRADIENT, 0).lookId);
        assertEquals("frost", GlassLookUpgrade.map(FrostStops.MIST, 35, 0, 24,
            LookGrid.RIM_GRADIENT, 0).lookId);
        assertEquals("stock", GlassLookUpgrade.map(FrostStops.TINT, 46, 35, 21,
            LookGrid.RIM_HAIRLINE, 0).lookId);
        assertEquals("solid", GlassLookUpgrade.map(FrostStops.SOLID, 92, 0, 0,
            LookGrid.RIM_HAIRLINE, 0).lookId);
        GlassLookUpgrade.Mapped mist = GlassLookUpgrade.map(FrostStops.MIST, 60, 0, 24,
            LookGrid.RIM_GRADIENT, 0);
        assertEquals(27, mist.bendDp);
        assertEquals(50, mist.shine);
    }

    @Test
    public void anyOtherLookStaysCustomWithMappedValues() {
        GlassLookUpgrade.Mapped custom = GlassLookUpgrade.map(FrostStops.MIST, 20, 0, 15,
            LookGrid.RIM_OFF, 80);
        assertNull(custom.lookId);
        assertEquals(15, custom.bendDp);
        assertEquals(10 + 20, custom.shine);
    }

    @Test
    public void runWritesShineKeepsDepthAndDropsTheRetiredCells() {
        LookGrid grid = grid();
        grid.write(LookGrid.Property.RIM, null, LookGrid.RIM_GRADIENT);
        grid.write(LookGrid.Property.EDGE, null, 100);
        grid.write(LookGrid.Property.DEPTH, null, 12);
        grid.write(LookGrid.Property.EDGE, LookGrid.Element.DOCK, 30);
        grid.write(LookGrid.Property.DEPTH, LookGrid.Element.DOCK, 5);
        grid.write(LookGrid.Property.OPACITY, LookGrid.Element.DOCK, 33);

        GlassLookUpgrade.run(grid);

        assertEquals(100, grid.value(LookGrid.Property.SHINE, null));
        assertEquals(12, grid.value(LookGrid.Property.DEPTH, null));
        assertFalse(grid.has(LookGrid.Property.RIM, null));
        assertFalse(grid.has(LookGrid.Property.EDGE, null));
        assertFalse(grid.has(LookGrid.Property.EDGE, LookGrid.Element.DOCK));
        assertFalse(grid.has(LookGrid.Property.DEPTH, LookGrid.Element.DOCK));
        assertEquals("Frost, Opacity and the rest are untouched", 33,
            grid.value(LookGrid.Property.OPACITY, LookGrid.Element.DOCK));
    }

    @Test
    public void aStoreThatNeverTouchedTheEdgeWritesNothing() {
        LookGrid grid = grid();
        GlassLookUpgrade.run(grid);
        assertFalse(grid.has(LookGrid.Property.SHINE, null));
        assertFalse(grid.has(LookGrid.Property.DEPTH, null));
    }

    @Test
    public void runTwiceChangesNothingMore() {
        LookGrid grid = grid();
        grid.write(LookGrid.Property.RIM, null, LookGrid.RIM_OFF);
        GlassLookUpgrade.run(grid);
        int shine = grid.value(LookGrid.Property.SHINE, null);
        GlassLookUpgrade.run(grid);
        assertEquals(shine, grid.value(LookGrid.Property.SHINE, null));
        assertEquals(10, shine);
    }
}
