package com.termux.app.surfaces;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.termux.app.place.PlaceLayout;

import org.junit.Test;

/**
 * The frame and the card's detents as arithmetic, on pong's numbers: 411 × 914 dp at 2.625, a
 * 41 dp status bar, gesture navigation (wireframes README).
 */
public class EditorFramePoseTest {

    private static final float DENSITY = 2.625f;
    private static final int WINDOW_H = 2399;
    private static final int STATUS_PX = 108;
    private static final int NAV_PX = 63;
    private static final int CONTAINER_TOP = STATUS_PX;
    private static final int CONTAINER_H = WINDOW_H - STATUS_PX - NAV_PX;
    private static final int VISIBLE_H = CONTAINER_H + NAV_PX;

    private static int dp(float value) {
        return Math.round(value * DENSITY);
    }

    @Test
    public void theRestingFrameFillsTheRoomOverTheBarAtMostPointEightSix() {
        int top = EditorFramePose.topPx(STATUS_PX, DENSITY);
        assertEquals(STATUS_PX + dp(16), top);
        int bottom = EditorFramePose.restBottomPx(WINDOW_H, NAV_PX, dp(48), dp(8), dp(16),
            DENSITY);
        float scale = EditorFramePose.restScale(VISIBLE_H, top, bottom);
        assertTrue("about ×0.8, was " + scale, scale > 0.75f && scale <= 0.86f);
        assertTrue("it fits", top + VISIBLE_H * scale <= bottom + 1);
        // A tall room never scales past the cap.
        assertEquals(EditorFramePose.REST_MAX_SCALE,
            EditorFramePose.restScale(1000, 0, 5000), 0f);
    }

    @Test
    public void aCardShrinksTheFrameToItsDetentsScale() {
        assertEquals(0.6f, EditorFramePose.cardScale(EditorCardSheet.DETENT_HALF, 0.82f), 0f);
        assertEquals(0.44f, EditorFramePose.cardScale(EditorCardSheet.DETENT_FULL, 0.82f), 0f);
        // Never larger than at rest, on a short window.
        assertEquals(0.5f, EditorFramePose.cardScale(EditorCardSheet.DETENT_HALF, 0.5f), 0f);
    }

    @Test
    public void atTheFullDetentTheWholeFrameStandsAboveTheCard() {
        int top = EditorFramePose.topPx(STATUS_PX, DENSITY);
        int full = EditorFramePose.fullSheetPx(WINDOW_H, top, VISIBLE_H, dp(16), DENSITY);
        float ty = EditorFramePose.translationY(top, CONTAINER_TOP, 0,
            EditorFramePose.FULL_SCALE, WINDOW_H - full, null);
        float bottomOnScreen = CONTAINER_TOP + ty + VISIBLE_H * EditorFramePose.FULL_SCALE;
        assertTrue("the frame ends above the half line over the card",
            bottomOnScreen <= WINDOW_H - full - dp(16));
        assertTrue("and the card takes about half the window", full > WINDOW_H * 0.45f);
    }

    @Test
    public void theHalfDetentAsksForTierOneWithinItsShare() {
        int full = 1800;
        assertEquals(Math.round(WINDOW_H * EditorFramePose.HALF_MIN_SHARE),
            EditorFramePose.halfSheetPx(WINDOW_H, 400, full));
        assertEquals(1200, EditorFramePose.halfSheetPx(WINDOW_H, 1200, full));
        assertEquals(Math.round(WINDOW_H * EditorFramePose.HALF_MAX_SHARE),
            EditorFramePose.halfSheetPx(WINDOW_H, 2000, full));
        assertEquals("never taller than the full detent", 900,
            EditorFramePose.halfSheetPx(WINDOW_H, 2000, 900));
    }

    @Test
    public void theSelectedSurfaceStaysAboveTheCard() {
        int top = EditorFramePose.topPx(STATUS_PX, DENSITY);
        float scale = EditorFramePose.HALF_SCALE;
        int visibleBottom = WINDOW_H - 1200 - dp(32);
        // The dock along the container's foot.
        int[] dock = {CONTAINER_H - dp(80), CONTAINER_H};
        float ty = EditorFramePose.translationY(top, CONTAINER_TOP, 0, scale, visibleBottom, dock);
        float dockBottom = CONTAINER_TOP + ty + dock[1] * scale;
        float dockTop = CONTAINER_TOP + ty + dock[0] * scale;
        assertTrue("the dock's foot is above the card", dockBottom <= visibleBottom + 1);
        assertTrue("and its top below the top line", dockTop >= top - 1);
        // A surface already in view does not move the frame.
        int[] status = {0, dp(56)};
        assertEquals(EditorFramePose.translationY(top, CONTAINER_TOP, 0, scale, visibleBottom,
            null), EditorFramePose.translationY(top, CONTAINER_TOP, 0, scale, visibleBottom,
            status), 0f);
    }

    @Test
    public void aSurfaceTallerThanTheRoomKeepsItsTopOnTheLine() {
        int top = 200;
        float scale = 0.6f;
        int[] terminal = {0, 3000};
        float ty = EditorFramePose.translationY(top, 100, 0, scale, 900, terminal);
        assertEquals(top, 100 + ty + terminal[0] * scale, 0.5f);
    }

    @Test
    public void ghostsStandOnTheirEdgeAndStackInwards() {
        int band = 100;
        int margin = 20;
        assertArrayEquals(new int[] {20, 1880, 980, 1980},
            EditorFramePose.ghostRect(PlaceLayout.Edge.BOTTOM, 0, 1000, 2000, band, margin));
        assertArrayEquals(new int[] {20, 1780, 980, 1880},
            EditorFramePose.ghostRect(PlaceLayout.Edge.BOTTOM, 1, 1000, 2000, band, margin));
        assertArrayEquals(new int[] {20, 20, 980, 120},
            EditorFramePose.ghostRect(PlaceLayout.Edge.TOP, 0, 1000, 2000, band, margin));
        assertArrayEquals(new int[] {880, 20, 980, 1980},
            EditorFramePose.ghostRect(PlaceLayout.Edge.RIGHT, 0, 1000, 2000, band, margin));
    }
}
