package com.termux.app.x11;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class DisplayExtraKeysColumnGeometryTest {

    @Test
    public void footprintIsInsetPlusMarginsPlusKeys() {
        assertEquals(0 + 20 + 40, DisplayExtraKeysColumnGeometry.footprintPx(0, 10, 40));
        // Past the rail when they share the edge: the rail width stands in for the cutout.
        assertEquals(120 + 20 + 40, DisplayExtraKeysColumnGeometry.footprintPx(120, 10, 40));
        assertEquals(40, DisplayExtraKeysColumnGeometry.footprintPx(-5, -1, 40));
    }

    @Test
    public void keysKeepThePreferredHeightWhileTheyFit() {
        assertEquals(52, DisplayExtraKeysColumnGeometry.keyHeightPx(1000, 7, 52));
    }

    @Test
    public void keysShrinkToFitAShortColumn() {
        // 7 keys in 280px: 40px each.
        assertEquals(40, DisplayExtraKeysColumnGeometry.keyHeightPx(280, 7, 52));
        // Never below one pixel, however many keys.
        assertEquals(1, DisplayExtraKeysColumnGeometry.keyHeightPx(3, 7, 52));
    }

    @Test
    public void unknownHeightUsesThePreferredAndNoKeysMeansNoHeight() {
        assertEquals(52, DisplayExtraKeysColumnGeometry.keyHeightPx(0, 7, 52));
        assertEquals(0, DisplayExtraKeysColumnGeometry.keyHeightPx(1000, 0, 52));
        assertEquals(0, DisplayExtraKeysColumnGeometry.keyHeightPx(1000, 7, 0));
    }
}
