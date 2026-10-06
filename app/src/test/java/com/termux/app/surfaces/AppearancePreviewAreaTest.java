package com.termux.app.surfaces;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * The shared preview contract: the miniature's visible top stands on one line under the page bar
 * on every page, and the sheet reserve makes it one size on every page.
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
    public void everyRestingPageGetsTheSameRectAndOnlyCustomTakesRoom() {
        int look = Math.round(80 * DENSITY);
        int layout = Math.round(136 * DENSITY);
        int icons = Math.round(184 * DENSITY);
        int custom = Math.round(312 * DENSITY);
        int reserve = Math.max(look, Math.max(layout, icons));
        int top = AppearancePreviewArea.topPx(STATUS_PX, DENSITY);
        float rest = -1f;
        for (int page : new int[] {look, layout, icons}) {
            int sheet = AppearancePreviewArea.sheetPx(reserve, page);
            assertEquals(reserve, sheet);
            float scale = AppearancePreviewArea.scale(CONTAINER_H, 0, NAV_PX, top,
                AppearancePreviewArea.bottomPx(WINDOW_H, sheet, DENSITY));
            if (rest < 0f) rest = scale;
            assertEquals(rest, scale, 0f);
        }
        int customSheet = AppearancePreviewArea.sheetPx(reserve, custom);
        assertEquals(custom, customSheet);
        assertTrue(AppearancePreviewArea.scale(CONTAINER_H, 0, NAV_PX, top,
            AppearancePreviewArea.bottomPx(WINDOW_H, customSheet, DENSITY)) < rest);
    }

    @Test
    public void theBarAndTheContentAgreeOnThePageInset() {
        assertEquals(108, AppearancePreviewArea.pageInsetTopPx(108, 0));
        // A content view that already starts below part of the bar keeps only the rest.
        assertEquals(8, AppearancePreviewArea.pageInsetTopPx(108, 100));
        assertEquals(0, AppearancePreviewArea.pageInsetTopPx(108, 200));
    }
}
