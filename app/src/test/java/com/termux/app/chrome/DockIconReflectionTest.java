package com.termux.app.chrome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** The reflection's picture is small and taken only once a page has settled. */
public class DockIconReflectionTest {

    @Test
    public void thePictureIsAQuarterOfTheRowEachWay() {
        assertEquals(270, DockIconReflection.capturedSizePx(1080));
        assertEquals(41, DockIconReflection.capturedSizePx(161));
        assertEquals(1, DockIconReflection.capturedSizePx(0));
        assertEquals(1, DockIconReflection.capturedSizePx(-5));
        // About 40 KB for a 1080 px row of 160 px icons: well inside any budget.
        int bytes = DockIconReflection.capturedSizePx(1080) * DockIconReflection.capturedSizePx(160) * 4;
        assertTrue(bytes + " bytes", bytes <= 48 * 1024);
    }

    @Test
    public void onlyAWholePageIsSettled() {
        assertTrue(DockIconReflection.pageSettled(0f));
        assertTrue(DockIconReflection.pageSettled(2f));
        assertTrue(DockIconReflection.pageSettled(1.005f));
        assertFalse(DockIconReflection.pageSettled(0.5f));
        assertFalse(DockIconReflection.pageSettled(1.2f));
        assertFalse(DockIconReflection.pageSettled(Float.NaN));
    }

    @Test
    public void theReflectionFadesOverAShortEasedStretchRatherThanPopping() {
        assertTrue(DockIconReflection.FADE_MS >= 100L && DockIconReflection.FADE_MS <= 200L);
        assertEquals(1f, DockIconReflection.fadeStrength(1f, 0f, 0L), 0f);
        assertEquals(0f, DockIconReflection.fadeStrength(1f, 0f, DockIconReflection.FADE_MS), 0f);
        assertEquals(0.5f, DockIconReflection.fadeStrength(0f, 1f, DockIconReflection.FADE_MS / 2), 1e-3f);
        // Eased: slow at both ends, so its first and last frames are small steps.
        float early = DockIconReflection.fadeStrength(0f, 1f, 16L);
        assertTrue(early > 0f && early < 16f / DockIconReflection.FADE_MS);
        // Caught mid-fade it carries on from where it was; past the end it stays there.
        assertEquals(0.4f, DockIconReflection.fadeStrength(0.4f, 1f, 0L), 1e-6f);
        assertEquals(1f, DockIconReflection.fadeStrength(0.4f, 1f, 10_000L), 0f);
    }

    @Test
    public void aBurstOfChangesSettlesBeforeACapture() {
        // Longer than a frame or two, shorter than anyone waits to look.
        assertTrue(DockIconReflection.SETTLE_MS >= 100L && DockIconReflection.SETTLE_MS <= 300L);
    }
}
