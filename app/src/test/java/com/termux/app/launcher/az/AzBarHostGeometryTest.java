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

    /**
     * The Surface (320 dpi, density 2) at b5b19ef99: the rail's pill (2658..2716) is 58 wide on
     * the right edge. The letters were centred on the 34px band left over by the 1dp air and the
     * 11dp chin, at x 19 (2677 on screen, measured 2670..2683), 10 left of the pill's middle at
     * 2687. They stand on the middle now.
     */
    @Test
    public void aColumnsLettersStandOnThePillsMiddle() {
        int column = AzBarHostGeometry.thicknessPx(DENSITY);
        assertEquals(58, column);
        assertEquals(29f, AzBarHostGeometry.columnLetterCentrePx(column, 0f, false), 0f);
        assertEquals(2687f, 2658 + AzBarHostGeometry.columnLetterCentrePx(column, 0f, false), 0f);
        assertEquals(29f, AzBarHostGeometry.columnLetterCentrePx(column, 0f, true), 0f);
        // A phone in landscape (density 2.625): a 76px column, letters on 38.
        int phone = AzBarHostGeometry.thicknessPx(2.625f);
        assertEquals(76, phone);
        assertEquals(38f, AzBarHostGeometry.columnLetterCentrePx(phone, 0f, false), 0f);
    }

    @Test
    public void theWaveCarriesAColumnsLettersAwayFromItsRim() {
        assertEquals(29f - 6f, AzBarHostGeometry.columnLetterCentrePx(58, 6f, false), 0f);
        assertEquals(29f + 6f, AzBarHostGeometry.columnLetterCentrePx(58, 6f, true), 0f);
        assertEquals(29f, AzBarHostGeometry.columnLetterCentrePx(58, Float.NaN, true), 0f);
        assertEquals(0f, AzBarHostGeometry.columnLetterCentrePx(-4, 0f, true), 0f);
    }
}
