package com.termux.app.launcher.paging;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

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
}
