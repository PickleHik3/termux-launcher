package com.termux.app.surfaces;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * The display round the editor's frame: the content inset the frame's top line starts under, and
 * the band above the container that wraps the element at the top of the screen with the air it
 * has beside it.
 */
public class AppearancePreviewAreaTest {

    private static final float DENSITY = 2.625f;
    private static final int WINDOW_H = 2410;
    private static final int STATUS_PX = 108;
    private static final int NAV_PX = 42;
    private static final int CONTAINER_TOP = STATUS_PX;
    private static final int CONTAINER_H = WINDOW_H - STATUS_PX - NAV_PX;
    private static final int CONTAINER_W = 1080;
    private static final float DISPLAY_RADIUS = 105f;


    @Test
    public void theBarAndTheContentAgreeOnThePageInset() {
        assertEquals(108, AppearancePreviewArea.pageInsetTopPx(108, 0));
        // A content view that already starts below part of the bar keeps only the rest.
        assertEquals(8, AppearancePreviewArea.pageInsetTopPx(108, 100));
        assertEquals(0, AppearancePreviewArea.pageInsetTopPx(108, 200));
    }

    @Test
    public void aFloatingCardWithEqualAirNeedsNoBand() {
        int gap = Math.round(12 * DENSITY);
        int[] card = {gap, gap, CONTAINER_W - gap};
        // A rounded card well inside the display's arc: the air above it is already its own.
        assertEquals(0, AppearancePreviewArea.topRevealPx(CONTAINER_W, 0, STATUS_PX, 0,
            DISPLAY_RADIUS, card, 26 * DENSITY));
    }

    @Test
    public void aCardFlushWithTheContainerTopGetsItsSideAirAbove() {
        int gap = Math.round(12 * DENSITY);
        int[] card = {gap, 0, CONTAINER_W - gap};
        int reveal = AppearancePreviewArea.topRevealPx(CONTAINER_W, 0, STATUS_PX, 0,
            DISPLAY_RADIUS, card, 26 * DENSITY);
        assertTrue("at least the side's air: " + reveal, reveal >= gap);
        assertTrue("never the whole band: " + reveal, reveal < STATUS_PX);
    }

    @Test
    public void aSquareFlushBarShowsTheBandTheArcNeedsToCutNothing() {
        int[] docked = {0, 0, CONTAINER_W};
        // Its corner sits at the arc's foot: the arc stands wholly in the band above it.
        assertEquals((int) Math.ceil(DISPLAY_RADIUS), AppearancePreviewArea.topRevealPx(
            CONTAINER_W, 0, STATUS_PX, 0, DISPLAY_RADIUS, docked, 0f));
        // And never more than the display has above the container.
        assertEquals(60, AppearancePreviewArea.topRevealPx(CONTAINER_W, 0, 60, 0,
            DISPLAY_RADIUS, docked, 0f));
    }

    @Test
    public void nothingToWrapKeepsTheDisplaysOwnTop() {
        assertEquals(STATUS_PX, AppearancePreviewArea.topRevealPx(CONTAINER_W, 0, STATUS_PX, 0,
            DISPLAY_RADIUS, null, 0f));
        // And no band where the display has none.
        assertEquals(0, AppearancePreviewArea.topRevealPx(CONTAINER_W, 0, 0, 0,
            DISPLAY_RADIUS, new int[] {0, 0, CONTAINER_W}, 0f));
    }

    @Test
    public void theArcHoldsTheCornerItWasCleared() {
        int gap = Math.round(4 * DENSITY);
        float radius = 8 * DENSITY;
        int[] card = {gap, 0, CONTAINER_W - gap};
        int reveal = AppearancePreviewArea.topRevealPx(CONTAINER_W, 0, 500, 0, DISPLAY_RADIUS,
            card, radius);
        // Every point of the card's corner arc is inside the display's arc at that reveal.
        float cx = DISPLAY_RADIUS;
        float cy = DISPLAY_RADIUS - reveal;
        for (int i = 0; i <= 32; i++) {
            double a = Math.PI / 2 * i / 32;
            double x = gap + radius - radius * Math.cos(a);
            double y = radius - radius * Math.sin(a);
            if (x >= cx || y >= cy) continue;
            assertTrue("point " + i, Math.hypot(cx - x, cy - y) <= DISPLAY_RADIUS + 0.75);
        }
    }
}
