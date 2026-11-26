package com.termux.app.overview;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** Sizes in dp at density 1: a phone of about 411 x 915 dp with the bars and the field taken off. */
public class OverviewGridTest {

    private static final float EPS = 0.01f;
    /** Pong portrait: 411 dp less two 16 dp gutters; the height left between header and field. */
    private static final float PHONE_W = 379f;
    private static final float PHONE_H = 719f;
    private static final float PHONE_ASPECT = 2400f / 1080f;

    private static OverviewGrid.Spec spec(float w, float h, float aspect, int pages, int... rows) {
        OverviewGrid.Spec s = new OverviewGrid.Spec();
        s.width = w;
        s.height = h;
        s.aspect = aspect;
        s.pages = pages;
        s.rows = rows;
        s.rowCount = rows.length;
        s.gap = 8f;
        s.caption = 28f;
        s.head = 24f;
        s.blockGap = 12f;
        s.maxLong = 200f;
        s.minWidth = 64f;
        s.minHeight = 40f;
        s.rowScale = 0.8f;
        return s;
    }

    private static OverviewGrid.Fit fit(OverviewGrid.Spec s) {
        return OverviewGrid.fit(s, new OverviewGrid.Fit());
    }

    private static void assertWithin(OverviewGrid.Spec s, OverviewGrid.Fit f) {
        assertTrue("fits", f.fits);
        assertTrue("height " + f.height + " within " + s.height, f.height <= s.height + EPS);
        float across = f.columns * f.cardWidth + (f.columns - 1) * s.gap;
        assertTrue("wall across " + across, across <= s.width + EPS);
        float rowAcross = f.rowColumns * f.rowCardWidth + (f.rowColumns - 1) * s.gap;
        assertTrue("rows across " + rowAcross, rowAcross <= s.width + EPS);
        assertTrue(f.cardHeight <= f.cardWidth * s.aspect + EPS);
    }

    @Test public void fewPagesAreFourColumnsAtTheScreensShape() {
        OverviewGrid.Spec s = spec(PHONE_W, PHONE_H, PHONE_ASPECT, 3);
        OverviewGrid.Fit f = fit(s);
        assertWithin(s, f);
        assertEquals(4, f.columns);
        assertEquals((PHONE_W - 3 * 8f) / 4f, f.cardWidth, EPS);
        assertEquals(f.cardWidth * PHONE_ASPECT, f.cardHeight, EPS);
        assertTrue("longer side capped", f.cardHeight <= s.maxLong + EPS);
    }

    @Test public void oneAndTwelvePagesBothFitWithoutShorteningTheCards() {
        for (int pages = 1; pages <= 12; pages++) {
            OverviewGrid.Spec s = spec(PHONE_W, PHONE_H, PHONE_ASPECT, pages);
            OverviewGrid.Fit f = fit(s);
            assertWithin(s, f);
            assertEquals("pages " + pages, f.cardWidth * PHONE_ASPECT, f.cardHeight, EPS);
        }
    }

    @Test public void morePagesNeverMeanLargerCards() {
        float last = Float.MAX_VALUE;
        for (int pages = 1; pages <= 40; pages++) {
            OverviewGrid.Fit f = fit(spec(PHONE_W, PHONE_H, PHONE_ASPECT, pages, 3, 4));
            assertTrue("pages " + pages, f.cardWidth * f.cardHeight <= last + EPS);
            last = f.cardWidth * f.cardHeight;
        }
    }

    @Test public void sessionRowsAndSavedAddColumnsBeforeShorteningCards() {
        // Five pages, one Workspace with two waiting sessions (+ its tile), two saved.
        OverviewGrid.Spec s = spec(PHONE_W, PHONE_H, PHONE_ASPECT, 5, 3, 2);
        OverviewGrid.Fit f = fit(s);
        assertWithin(s, f);
        assertEquals(5, f.columns);
        assertEquals(f.cardWidth * PHONE_ASPECT, f.cardHeight, EPS);
        assertEquals(f.cardWidth * 0.8f, f.rowCardWidth, EPS);
        assertEquals(f.cardHeight * 0.8f, f.rowCardHeight, EPS);
    }

    @Test public void twelvePagesThreeRowsAndSavedFitAtTheFloorWithShorterCards() {
        OverviewGrid.Spec s = spec(PHONE_W, PHONE_H, PHONE_ASPECT, 12, 3, 3, 3, 4);
        OverviewGrid.Fit f = fit(s);
        assertWithin(s, f);
        assertTrue(f.cardWidth >= s.minWidth);
        assertTrue("shortened", f.cardHeight < f.cardWidth * PHONE_ASPECT);
        assertTrue(f.cardHeight >= s.minHeight);
        // It takes the whole room: shortened only as far as it has to be.
        assertEquals(s.height, f.height, 0.5f);
    }

    @Test public void manySessionsWrapInTheirRow() {
        OverviewGrid.Spec s = spec(PHONE_W, PHONE_H, PHONE_ASPECT, 4, 13);
        OverviewGrid.Fit f = fit(s);
        assertWithin(s, f);
        assertTrue(OverviewGrid.gridRows(13, f.rowColumns) > 1);
    }

    @Test public void fontScaleOnePointThreeStillFitsWithCardsNoLarger() {
        OverviewGrid.Spec plain = spec(PHONE_W, PHONE_H, PHONE_ASPECT, 8, 3, 2);
        OverviewGrid.Fit a = fit(plain);
        OverviewGrid.Spec big = spec(PHONE_W, PHONE_H, PHONE_ASPECT, 8, 3, 2);
        big.caption = 8f + 13f * 1.3f * 1.35f;
        big.head = 6f + 13f * 1.3f * 1.35f;
        OverviewGrid.Fit b = fit(big);
        assertWithin(big, b);
        assertTrue(b.cardWidth * b.cardHeight <= a.cardWidth * a.cardHeight + EPS);
    }

    @Test public void landscapeCardsAreWideAndFit() {
        float aspect = 1080f / 2400f;
        for (int pages = 1; pages <= 12; pages++) {
            OverviewGrid.Spec s = spec(851f, 250f, aspect, pages, 3);
            OverviewGrid.Fit f = fit(s);
            assertWithin(s, f);
            assertTrue(f.cardWidth > f.cardHeight);
            assertTrue(f.cardWidth <= s.maxLong + EPS);
        }
    }

    @Test public void halfASplitScreenFits() {
        // The launcher in the top half of a split: the phone's width, about 330 dp left down.
        OverviewGrid.Spec s = spec(PHONE_W, 330f, PHONE_ASPECT, 6, 3);
        assertWithin(s, fit(s));
    }

    @Test public void aStripTooShortForTheFloorSaysSoAndKeepsTheFloor() {
        OverviewGrid.Spec s = spec(PHONE_W, 150f, PHONE_ASPECT, 12, 3, 3, 3, 4);
        OverviewGrid.Fit f = fit(s);
        assertFalse(f.fits);
        assertEquals(s.minHeight, f.cardHeight, EPS);
        assertEquals(5, f.columns);
        assertTrue(f.height > s.height);
    }

    @Test public void aRoomNarrowerThanTheFloorIsOneColumn() {
        OverviewGrid.Spec s = spec(50f, 600f, PHONE_ASPECT, 3);
        OverviewGrid.Fit f = fit(s);
        assertEquals(1, f.columns);
        assertEquals(50f, f.cardWidth, EPS);
    }

    @Test public void noPagesStillSizesTheRows() {
        OverviewGrid.Spec s = spec(PHONE_W, PHONE_H, PHONE_ASPECT, 0, 2);
        OverviewGrid.Fit f = fit(s);
        assertWithin(s, f);
        assertTrue(f.rowCardWidth > 0f);
    }
}
