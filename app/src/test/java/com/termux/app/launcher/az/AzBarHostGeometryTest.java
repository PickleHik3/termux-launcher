package com.termux.app.launcher.az;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/** The bar's host away from the dock: its thickness, its footprint and where it starts. */
public class AzBarHostGeometryTest {

    private static final float DENSITY = 2f;

    @Test
    public void theBarKeepsTheDocksOwnLetterBandAndChin() {
        assertEquals(38, AzBarHostGeometry.letterBandPx(DENSITY));
        assertEquals(20, AzBarHostGeometry.chinPx(DENSITY));
        assertEquals(58, AzBarHostGeometry.thicknessPx(DENSITY));
        assertEquals(0, AzBarHostGeometry.thicknessPx(0f));
    }

    @Test
    public void aColumnClaimsWhatItStartsPastPlusItsMarginsAndItself() {
        assertEquals(58 + 24 + 100, AzBarHostGeometry.footprintPx(100, 12, 58));
        assertEquals(58, AzBarHostGeometry.footprintPx(0, 0, 58));
        assertEquals(0, AzBarHostGeometry.footprintPx(-5, -5, 0));
    }

    @Test
    public void aTopHostIsTheBarAndTheAirAroundIt() {
        assertEquals(58 + 16, AzBarHostGeometry.rowHeightPx(8, 58));
        assertEquals(0, AzBarHostGeometry.rowHeightPx(0, 0));
    }

    @Test
    public void theColumnIsInnermostOfWhateverSharesItsEdge() {
        // Nothing else on the edge: it starts past the cutout only.
        assertEquals(44, AzBarHostGeometry.edgeInsetPx(44, 0, 0, 0));
        // The rail holds the same edge, so the bar starts past the rail.
        assertEquals(160, AzBarHostGeometry.edgeInsetPx(44, 160, 0, 0));
        // The extra keys stand past the rail, and the bar past them.
        assertEquals(300, AzBarHostGeometry.edgeInsetPx(44, 160, 300, 0));
        // The status bar's column too; the innermost of them all wins.
        assertEquals(420, AzBarHostGeometry.edgeInsetPx(44, 160, 300, 420));
        assertEquals(0, AzBarHostGeometry.edgeInsetPx(-1, -1, -1, -1));
    }
}
