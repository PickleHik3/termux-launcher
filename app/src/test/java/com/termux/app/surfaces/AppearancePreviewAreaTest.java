package com.termux.app.surfaces;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * The shared preview contract: the miniature's visible top stands on one line under the page bar
 * on every page, the sheet reserve makes it one size on every page, and the band above the
 * container wraps the element at the top of the screen with the air it has beside it.
 */
public class AppearancePreviewAreaTest {

    /** A Nothing Phone (2): 411x918dp at 2.625, a 41dp status bar with its cutout, gesture nav. */
    private static final float DENSITY = 2.625f;
    private static final int WINDOW_H = 2410;
    private static final int STATUS_PX = 108;
    private static final int NAV_PX = 42;
    private static final int CONTAINER_TOP = STATUS_PX;
    private static final int CONTAINER_H = WINDOW_H - STATUS_PX - NAV_PX;
    private static final int CONTAINER_W = 1080;
    private static final float DISPLAY_RADIUS = 105f;

    @Test
    public void thePreviewStandsTheGapUnderTheBar() {
        int top = AppearancePreviewArea.topPx(STATUS_PX, DENSITY);
        assertEquals(STATUS_PX + Math.round(72 * DENSITY), top);
        for (int reveal : new int[] {0, 21, STATUS_PX}) {
            int sheet = Math.round(160 * DENSITY);
            float scale = AppearancePreviewArea.scale(CONTAINER_H, reveal, NAV_PX, top,
                AppearancePreviewArea.bottomPx(WINDOW_H, sheet, DENSITY));
            float ty = AppearancePreviewArea.translationY(top, CONTAINER_TOP, reveal, scale);
            // The clip's top is the shown band above the container's scaled top: on the line.
            float visibleTop = CONTAINER_TOP + ty - reveal * scale;
            assertEquals("reveal " + reveal, top, visibleTop, 0.5f);
            float visibleBottom = CONTAINER_TOP + ty + (CONTAINER_H + NAV_PX) * scale;
            assertTrue("reveal " + reveal + " clears the sheet's gap",
                visibleBottom <= WINDOW_H - sheet - Math.round(8 * DENSITY) + 0.5f);
        }
    }

    @Test
    public void everyPageAndStopGetsTheSameRectCustomIncluded() {
        int look = Math.round(80 * DENSITY);
        int layout = Math.round(136 * DENSITY);
        int icons = Math.round(184 * DENSITY);
        int custom = Math.round(312 * DENSITY);
        int top = AppearancePreviewArea.topPx(STATUS_PX, DENSITY);
        int cap = AppearancePreviewArea.sheetCapPx(WINDOW_H, top, CONTAINER_H, 0, NAV_PX, DENSITY);
        int reserve = AppearancePreviewArea.reservePx(Math.max(look, Math.max(layout, icons)),
            custom, cap);
        // The phone has the room: the reserve is the Custom row's own height.
        assertEquals(custom, reserve);
        float rest = -1f;
        for (int page : new int[] {look, layout, icons, custom}) {
            int sheet = AppearancePreviewArea.sheetPx(reserve, page);
            assertEquals(reserve, sheet);
            float scale = AppearancePreviewArea.scale(CONTAINER_H, 0, NAV_PX, top,
                AppearancePreviewArea.bottomPx(WINDOW_H, sheet, DENSITY));
            if (rest < 0f) rest = scale;
            assertEquals(rest, scale, 0f);
        }
        assertTrue(rest > AppearanceEditorFrame.MIN_SCALE);
    }

    @Test
    public void onAShortPhoneTheReserveStopsWhereThePreviewWouldTurnUnreadable() {
        int window = 780;
        int top = AppearancePreviewArea.topPx(24, 1f);
        int container = window - 24;
        int cap = AppearancePreviewArea.sheetCapPx(window, top, container, 0, 0, 1f);
        int reserve = AppearancePreviewArea.reservePx(160, 340, cap);
        assertEquals("the Custom row scrolls inside the cap", cap, reserve);
        float scale = AppearancePreviewArea.scale(container, 0, 0, top,
            AppearancePreviewArea.bottomPx(window, reserve, 1f));
        assertEquals(AppearanceEditorFrame.MIN_SCALE, scale, 0.002f);
        // The other pages never scroll: their tallest is the floor, cap or not.
        assertEquals(200, AppearancePreviewArea.reservePx(200, 340, 150));
    }

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
