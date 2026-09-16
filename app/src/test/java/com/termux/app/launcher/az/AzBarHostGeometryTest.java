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

    /**
     * The column is the band the stack hands it, end to end. It used to subtract a top status
     * bar's height and the dock's, which was true while the side stacks stood outside the content
     * column; since they flank the canvas alone (P4) both of those are outside the band already,
     * and subtracting them left the letters bunched in the top third under a stub of a capsule.
     */
    @Test
    public void aColumnRunsTheWholeBandItIsGiven() {
        assertEquals(1362, AzBarHostGeometry.columnLengthPx(1362));
        assertEquals(0, AzBarHostGeometry.columnLengthPx(0));
        assertEquals(0, AzBarHostGeometry.columnLengthPx(-1));
    }
}
