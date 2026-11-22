package com.termux.app;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class DockGlassRenderingTest {
    @Test public void grainAlphaClampsToProductionRange() {
        assertEquals(0, DockGlassRendering.grainAlpha(-1));
        assertEquals(30, DockGlassRendering.grainAlpha(50));
        assertEquals(60, DockGlassRendering.grainAlpha(100));
        assertEquals(60, DockGlassRendering.grainAlpha(500));
    }

    @Test public void theShelfShadowIsSoftAndFadesToNothing() {
        float[] stops = DockGlassRendering.shelfFalloffStops();
        int[] colors = DockGlassRendering.shelfFalloffColors(0xFF000000,
            DockGlassRendering.SHELF_SHADOW_ALPHA);
        assertEquals(stops.length, colors.length);
        assertEquals(0f, stops[0], 0f);
        assertEquals(1f, stops[stops.length - 1], 0f);
        assertEquals(Math.round(255f * DockGlassRendering.SHELF_SHADOW_ALPHA), colors[0] >>> 24);
        assertEquals("nothing at its rim: no hard edge", 0, colors[colors.length - 1] >>> 24);
        for (int i = 1; i < colors.length; i++) {
            assertTrue((colors[i] >>> 24) <= (colors[i - 1] >>> 24));
        }
        assertEquals("the colour is kept", 0, colors[0] & 0x00FFFFFF);
    }

    @Test public void theShelfIsFaintAndStaysUnderTheSlab() {
        assertTrue(DockGlassRendering.SHELF_SHADOW_ALPHA <= 0.3f);
        assertTrue(DockGlassRendering.SHELF_GLOW_ALPHA < DockGlassRendering.SHELF_SHADOW_ALPHA);
        assertTrue(DockGlassRendering.SHELF_GLOW_DEPTH_DP < DockGlassRendering.SHELF_SHADOW_DEPTH_DP);
        float width = 360f * 2.625f;
        float radius = 26f * 2.625f;
        float shadow = DockGlassRendering.shelfShadowHalfWidthPx(width, radius);
        float glow = DockGlassRendering.shelfGlowHalfWidthPx(width, radius);
        assertTrue(shadow < width / 2f);
        assertTrue("the glow sits inside the shadow, clear of the corners", glow < shadow);
        assertEquals(width / 2f - radius, glow, 1e-3f);
        assertEquals(1f, DockGlassRendering.shelfGlowHalfWidthPx(10f, 40f), 0f);
    }
}
