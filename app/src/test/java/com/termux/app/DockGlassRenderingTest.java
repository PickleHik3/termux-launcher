package com.termux.app;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class DockGlassRenderingTest {
    @Test public void grainAlphaClampsToProductionRange() {
        assertEquals(0, DockGlassRendering.grainAlpha(-1));
        assertEquals(30, DockGlassRendering.grainAlpha(50));
        assertEquals(60, DockGlassRendering.grainAlpha(100));
        assertEquals(60, DockGlassRendering.grainAlpha(500));
    }
}
