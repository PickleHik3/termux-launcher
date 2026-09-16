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
     * The landscape case the column collapsed in: 2400x954 of content, a status bar standing in a
     * column on the same side, and the bar just inside it. The status column is answered by the
     * edge stack and by nothing else — feeding its length to the top of the column, as a row
     * sharing that column would, pushed the bar's glass past the bottom of its own host and left
     * it zero pixels tall.
     */
    @Test
    public void aColumnOnTheStatusBarsEdgeStillRunsTheContentsHeight() {
        int marginPx = Math.round(10f * 2.625f);      // 26: the dock's own edge margin
        int statusColumnFootprintPx = 223;            // the bar's column on the same side
        int contentHeightPx = 954;                    // 2400x954 of content in landscape

        // Nothing on the same side reaches the ends of the column: the bar stands beside the
        // status column on the edge stack, so that footprint never shortens it.
        assertEquals(223, statusColumnFootprintPx);
        assertEquals(marginPx, AzBarHostGeometry.columnTopPaddingPx(marginPx, 0));
        assertEquals(marginPx, AzBarHostGeometry.columnBottomPaddingPx(marginPx, 0));
        assertEquals(contentHeightPx - 2 * marginPx,
            AzBarHostGeometry.columnLengthPx(contentHeightPx, marginPx, 0, 0));
    }

    @Test
    public void onlyChromeCrossingTheContainerShortensTheColumn() {
        // A status bar along the top and the dock's rows along the bottom do cost the bar length.
        assertEquals(954 - (26 + 120) - (26 + 180),
            AzBarHostGeometry.columnLengthPx(954, 26, 120, 180));
        assertEquals(146, AzBarHostGeometry.columnTopPaddingPx(26, 120));
        assertEquals(206, AzBarHostGeometry.columnBottomPaddingPx(26, 180));
        // Never negative, whatever it is handed.
        assertEquals(0, AzBarHostGeometry.columnLengthPx(954, 26, 900, 900));
        assertEquals(0, AzBarHostGeometry.columnLengthPx(-1, -1, -1, -1));
    }
}
