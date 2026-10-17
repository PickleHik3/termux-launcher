package com.termux.app.chrome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** Vibrancy: 0.7x to 1.5x, 1.0x today, baked only into blurred frames. */
public class GlassVibrancyTest {

    @Test
    public void theRangeIsHeldAndTodayIsOne() {
        assertEquals(1f, GlassVibrancy.saturation(100), 0f);
        assertEquals(0.7f, GlassVibrancy.saturation(70), 1e-6f);
        assertEquals(1.5f, GlassVibrancy.saturation(150), 1e-6f);
        assertEquals("clamped below", 0.7f, GlassVibrancy.saturation(0), 1e-6f);
        assertEquals("clamped above", 1.5f, GlassVibrancy.saturation(900), 1e-6f);
        assertEquals(70, GlassVibrancy.clampPercent(-3));
        assertEquals(150, GlassVibrancy.clampPercent(151));
        assertEquals(120, GlassVibrancy.clampPercent(120));
    }

    @Test
    public void onlyABlurredFrameAwayFromTodayIsBaked() {
        assertFalse("today's frame is untouched", GlassVibrancy.bakes(12, 100));
        assertFalse("the bare wallpaper never takes it", GlassVibrancy.bakes(0, 150));
        assertTrue(GlassVibrancy.bakes(12, 130));
        assertTrue(GlassVibrancy.bakes(4, 70));
        assertTrue("an out-of-range value bakes at its clamp", GlassVibrancy.bakes(12, 900));
        assertFalse("no blur, no frame to bake", GlassVibrancy.bakes(-1, 130));
    }
}
