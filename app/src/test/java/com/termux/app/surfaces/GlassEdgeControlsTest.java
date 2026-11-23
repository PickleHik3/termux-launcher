package com.termux.app.surfaces;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/** The Depth slider over the stored thickness; the numbers are GlassMaterial's (its own test). */
public class GlassEdgeControlsTest {

    @Test
    public void everyPositionReadsBackAWriteOfItsOwnThickness() {
        for (int t = GlassEdgeControls.MIN; t <= GlassEdgeControls.MAX; t++) {
            int bend = GlassEdgeControls.bendForDepth(t);
            assertEquals(bend, GlassEdgeControls.bendForDepth(GlassEdgeControls.depthFor(bend)));
        }
    }

    @Test
    public void aRememberedPositionIsKeptWhileItStillWritesTheStoredThickness() {
        int bend = GlassEdgeControls.bendForDepth(30);
        assertEquals(30, GlassEdgeControls.read(bend, 30));
        assertEquals("a position that no longer writes it is not kept",
            GlassEdgeControls.depthFor(bend), GlassEdgeControls.read(bend, 90));
        assertEquals(GlassEdgeControls.depthFor(bend),
            GlassEdgeControls.read(bend, GlassEdgeControls.NONE));
    }
}
