package com.termux.app.launcher.paging;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.termux.app.place.PlaceLayout.Edge;

import org.junit.Test;

/** The page ticks' own arithmetic, which is the same whichever way the strip runs. */
public class PageTickStripTest {

    private static final float DENSITY = 2.75f;

    @Test
    public void theTickNearestThePageIsTheLongOne() {
        float[] lengths = PageTickStrip.lengthsPx(3, 1f, DENSITY);
        assertEquals(3, lengths.length);
        assertEquals(PageTickStrip.ACTIVE_LENGTH_DP * DENSITY, lengths[1], 0.01f);
        assertEquals(PageTickStrip.INACTIVE_LENGTH_DP * DENSITY, lengths[0], 0.01f);
        assertEquals(PageTickStrip.INACTIVE_LENGTH_DP * DENSITY, lengths[2], 0.01f);
    }

    @Test
    public void theWidthMorphsContinuouslyAcrossASwipe() {
        // Halfway between two pages the two ticks share the widening, so nothing jumps.
        float[] half = PageTickStrip.lengthsPx(2, 0.5f, DENSITY);
        assertEquals(half[0], half[1], 0.01f);
        assertTrue(half[0] > PageTickStrip.INACTIVE_LENGTH_DP * DENSITY);
        assertTrue(half[0] < PageTickStrip.ACTIVE_LENGTH_DP * DENSITY);
    }

    @Test
    public void aPositionOutsideTheRunIsClampedIntoIt() {
        float[] before = PageTickStrip.lengthsPx(3, -4f, DENSITY);
        float[] after = PageTickStrip.lengthsPx(3, 9f, DENSITY);
        assertEquals(PageTickStrip.ACTIVE_LENGTH_DP * DENSITY, before[0], 0.01f);
        assertEquals(PageTickStrip.ACTIVE_LENGTH_DP * DENSITY, after[2], 0.01f);
    }

    @Test
    public void theRunIsCentredOnTheStrip() {
        float[] lengths = PageTickStrip.lengthsPx(4, 0f, DENSITY);
        float gap = PageTickStrip.gapPx(lengths, 600f, DENSITY);
        float[] centers = PageTickStrip.centersPx(lengths, gap, 600f);
        assertEquals(4, centers.length);
        float firstEdge = centers[0] - (lengths[0] * 0.5f);
        float lastEdge = centers[3] + (lengths[3] * 0.5f);
        assertEquals("the run's two ends are the same distance from the strip's",
            firstEdge, 600f - lastEdge, 0.01f);
        assertTrue("and both are inside it", firstEdge > 0f);
        for (int page = 1; page < centers.length; page++) {
            assertTrue("tick " + page + " comes after tick " + (page - 1),
                centers[page] > centers[page - 1]);
        }
    }

    @Test
    public void theGapIsConstantUntilTheTicksWouldNotFit() {
        float[] lengths = PageTickStrip.lengthsPx(3, 0f, DENSITY);
        assertEquals(PageTickStrip.GAP_DP * DENSITY,
            PageTickStrip.gapPx(lengths, 600f, DENSITY), 0.01f);
        float[] many = PageTickStrip.lengthsPx(12, 0f, DENSITY);
        float squeezed = PageTickStrip.gapPx(many, 400f, DENSITY);
        assertTrue("squeezed to fit: " + squeezed, squeezed < PageTickStrip.GAP_DP * DENSITY);
        assertTrue("but never to nothing", squeezed > 0f);
    }

    @Test
    public void oneTickNeedsNoGapAtAll() {
        float[] one = PageTickStrip.lengthsPx(1, 0f, DENSITY);
        assertEquals(PageTickStrip.GAP_DP * DENSITY,
            PageTickStrip.gapPx(one, 10f, DENSITY), 0.01f);
        assertEquals(1, PageTickStrip.centersPx(one, 0f, 100f).length);
    }

    @Test
    public void theTicksTakeTheRowsOuterSideOnEveryEdge() {
        // The rule everywhere but next to the canvas: the strip stands on the side of the row the
        // screen edge its stack stands on is, so it reads as the bar's own rim rather than as a
        // divider between the row and the band beyond it. A strip leads its host when that side
        // is the lower coordinate.
        assertTrue("above a row lying along the top", PageTickStrip.ticksLeadRow(Edge.TOP, false));
        assertTrue("left of a left-hand rail, outboard",
            PageTickStrip.ticksLeadRow(Edge.LEFT, false));
        assertFalse("under the dock's own row", PageTickStrip.ticksLeadRow(Edge.BOTTOM, false));
        assertFalse("right of a right-hand rail, outboard",
            PageTickStrip.ticksLeadRow(Edge.RIGHT, false));
    }

    @Test
    public void theRowNextToTheCanvasKeepsItsTicksOnTheCanvasSide() {
        // The one exception: nothing stands between the row and the terminal, so the free side is
        // the canvas side and the ticks take it — which is where the shipped arrangement has
        // always drawn them.
        assertTrue("above the dock's own row", PageTickStrip.ticksLeadRow(Edge.BOTTOM, true));
        assertTrue("left of a right-hand rail, towards the terminal",
            PageTickStrip.ticksLeadRow(Edge.RIGHT, true));
        assertFalse("under a row lying along the top", PageTickStrip.ticksLeadRow(Edge.TOP, true));
        assertFalse("right of a left-hand rail", PageTickStrip.ticksLeadRow(Edge.LEFT, true));
    }

    @Test
    public void theStripRunsTheWayItsRowDoes() {
        assertTrue(PageTickStrip.verticalOn(Edge.LEFT));
        assertTrue(PageTickStrip.verticalOn(Edge.RIGHT));
        assertFalse(PageTickStrip.verticalOn(Edge.TOP));
        assertFalse(PageTickStrip.verticalOn(Edge.BOTTOM));
    }

    @Test
    public void theActivePageIsTheAccentAndTheRestAreMutedByProximity() {
        assertEquals(1f, PageTickStrip.proximity(1, 1f), 0.0001f);
        assertEquals(0f, PageTickStrip.proximity(0, 1f), 0.0001f);
        assertEquals(0.5f, PageTickStrip.proximity(0, 0.5f), 0.0001f);
        assertEquals("nothing further than a page away reads at all",
            0f, PageTickStrip.proximity(0, 2.4f), 0.0001f);

        assertEquals("the page being shown is the accent itself",
            1f, PageTickStrip.alphaFor(1f), 0.0001f);
        assertEquals("and the rest of them are it, muted",
            PageTickStrip.INACTIVE_ALPHA, PageTickStrip.alphaFor(0f), 0.0001f);
        // Continuous across a swipe: halfway between two pages they share the difference.
        assertEquals(PageTickStrip.alphaFor(0.5f),
            (PageTickStrip.alphaFor(0f) + PageTickStrip.alphaFor(1f)) * 0.5f, 0.0001f);

        // The most-used page's own tint sleeps while it is not the page being shown.
        assertEquals(1f, PageTickStrip.dynamicDampFor(1f), 0.0001f);
        assertTrue(PageTickStrip.dynamicDampFor(0f) < 1f);
    }
}
