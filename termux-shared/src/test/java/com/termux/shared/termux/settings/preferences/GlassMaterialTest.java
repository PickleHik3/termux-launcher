package com.termux.shared.termux.settings.preferences;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class GlassMaterialTest {

    @Test
    public void depthDefaultIsTheRealisticSlabAndTheEndsGoPastIt() {
        assertEquals(0, GlassMaterial.bendForDepth(0));
        assertEquals(27, GlassMaterial.bendForDepth(GlassMaterial.DEPTH_DEFAULT));
        assertEquals(45, GlassMaterial.bendForDepth(GlassMaterial.MAX));
        assertEquals("out-of-range positions clamp", 45, GlassMaterial.bendForDepth(500));
        for (int t = 1; t <= 100; t++)
            assertTrue(GlassMaterial.bendForDepth(t) >= GlassMaterial.bendForDepth(t - 1));
    }

    @Test
    public void theIndexIsOrdinaryGlassUpToTheRealisticSlabAndRisesToOnePointNine() {
        assertEquals(1.5f, GlassMaterial.iorForBend(0), 1e-6f);
        assertEquals(1.5f, GlassMaterial.iorForBend(27), 1e-6f);
        assertEquals(1.9f, GlassMaterial.iorForBend(45), 1e-6f);
        assertEquals(1f, GlassMaterial.thicknessBoost(27), 1e-6f);
        assertEquals(1.445f, GlassMaterial.thicknessBoost(45), 0.01f);
    }

    @Test
    public void aStoredThicknessReadsAsTheNearestPositionAndTheDefaultReadsAsItself() {
        assertEquals(GlassMaterial.DEPTH_DEFAULT, GlassMaterial.depthForBend(27));
        for (int bend = 0; bend <= 45; bend++)
            assertEquals(bend, GlassMaterial.bendForDepth(GlassMaterial.depthForBend(bend)));
    }

    @Test
    public void shineIsDarkAtZeroTheRoomLightAtFiftyAndPastRealismAtTheTop() {
        assertEquals(0f, GlassMaterial.shineScale(0), 1e-6f);
        assertEquals(1f, GlassMaterial.shineScale(GlassMaterial.SHINE_DEFAULT), 1e-6f);
        assertEquals(2.5f, GlassMaterial.shineScale(100), 1e-6f);
    }

    @Test
    public void colourSplitComesOnlyAtTheHighEnd() {
        assertEquals(0, GlassMaterial.dispersionPercent(0));
        assertEquals(0, GlassMaterial.dispersionPercent(GlassMaterial.SHINE_DEFAULT));
        assertEquals(0, GlassMaterial.dispersionPercent(60));
        assertTrue(GlassMaterial.dispersionPercent(80) > 0);
        assertEquals(60, GlassMaterial.dispersionPercent(100));
    }

    @Test
    public void theOldRimAndEdgeMapToTheNearestShineAndNeverLowerIt() {
        assertEquals(10, GlassMaterial.shineForRimAndEdge(LookGrid.RIM_OFF, 0));
        assertEquals(50, GlassMaterial.shineForRimAndEdge(LookGrid.RIM_HAIRLINE, 0));
        assertEquals("Clear's glint is the shipped Edge", 50,
            GlassMaterial.shineForRimAndEdge(LookGrid.RIM_HAIRLINE, 40));
        assertEquals(72, GlassMaterial.shineForRimAndEdge(LookGrid.RIM_GRADIENT, 0));
        assertEquals(80, GlassMaterial.shineForRimAndEdge(LookGrid.RIM_HAIRLINE, 100));
        for (int rim = LookGrid.RIM_OFF; rim <= LookGrid.RIM_GRADIENT; rim++)
            for (int edge = 1; edge <= 100; edge++)
                assertTrue(GlassMaterial.shineForRimAndEdge(rim, edge)
                    >= GlassMaterial.shineForRimAndEdge(rim, edge - 1));
        assertEquals(100, GlassMaterial.shineForRimAndEdge(LookGrid.RIM_GRADIENT, 100));
    }
}
