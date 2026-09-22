package com.termux.app.place;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class ExtraKeysColumnGeometryTest {

    @Test
    public void footprintIsInsetPlusMarginsPlusKeys() {
        assertEquals(0 + 20 + 40, ExtraKeysColumnGeometry.footprintPx(0, 10, 40));
        // Past the rail when they share the edge: the rail width stands in for the cutout.
        assertEquals(120 + 20 + 40, ExtraKeysColumnGeometry.footprintPx(120, 10, 40));
        assertEquals(40, ExtraKeysColumnGeometry.footprintPx(-5, -1, 40));
    }

    @Test
    public void keysKeepThePreferredHeightWhileTheyFit() {
        assertEquals(52, ExtraKeysColumnGeometry.keyHeightPx(1000, 7, 52));
    }

    @Test
    public void keysShrinkToFitAShortColumn() {
        // 7 keys in 280px: 40px each.
        assertEquals(40, ExtraKeysColumnGeometry.keyHeightPx(280, 7, 52));
        // Never below one pixel, however many keys.
        assertEquals(1, ExtraKeysColumnGeometry.keyHeightPx(3, 7, 52));
    }

    @Test
    public void unknownHeightUsesThePreferredAndNoKeysMeansNoHeight() {
        assertEquals(52, ExtraKeysColumnGeometry.keyHeightPx(0, 7, 52));
        assertEquals(0, ExtraKeysColumnGeometry.keyHeightPx(1000, 0, 52));
        assertEquals(0, ExtraKeysColumnGeometry.keyHeightPx(1000, 7, 0));
    }
}
