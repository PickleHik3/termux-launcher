package com.termux.app.chrome;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/** The glass motion profiles: classic is today's numbers, mist is Obsidian's, springs settle. */
public class GlassMotionTest {

    @Test
    public void classicIsTheSheetCardAsItAlwaysRan() {
        GlassMotion classic = GlassMotion.CLASSIC;
        // TerminalSheetController's centred card: 170 ms in from 0.94, 110 ms out to 0.94.
        assertEquals(0.94f, classic.enterScaleFrom, 0f);
        assertEquals(170L, classic.enterAlphaMs);
        assertEquals(110L, classic.exitAlphaMs);
        assertEquals(0.94f, classic.exitScaleTo, 0f);
        // TerminalDrawerMetrics.SCRIM_ALPHA, out of 255.
        assertEquals(71, Math.round(classic.backdropDim * 255f));
        // No press feedback, no blur, no spring: the tween path.
        assertEquals(1f, classic.pressScale, 0f);
        assertEquals(0f, classic.enterBlurFromDp, 0f);
        assertNull(classic.enterScaleSpring);
        assertFalse(classic.springy());
        assertFalse(classic.obsidianCurves);
    }

    @Test
    public void mistIsObsidiansMotion() {
        GlassMotion mist = GlassMotion.MIST;
        assertEquals(0.92f, mist.pressScale, 0f);
        assertEquals(new GlassMotion.Spring(0.75f, 400f), mist.pressSpring);
        assertEquals(new GlassMotion.Spring(0.55f, 300f), mist.releaseSpring);
        assertEquals(0.9f, mist.enterScaleFrom, 0f);
        assertEquals(new GlassMotion.Spring(0.8f, 400f), mist.enterScaleSpring);
        assertEquals(20f, mist.enterBlurFromDp, 0f);
        assertEquals(new GlassMotion.Spring(0.82f, 400f), mist.enterBlurSpring);
        assertEquals(280L, mist.enterAlphaMs);
        assertEquals(200L, mist.exitAlphaMs);
        assertEquals(0.55f, mist.backdropDim, 0f);
        assertTrue(mist.springy());
    }

    @Test
    public void anIdIsResolvedAndAnUnknownOneIsClassic() {
        assertSame(GlassMotion.MIST, GlassMotion.forId("mist"));
        assertSame(GlassMotion.CLASSIC, GlassMotion.forId("classic"));
        assertSame(GlassMotion.CLASSIC, GlassMotion.forId("from-a-newer-build"));
        assertSame(GlassMotion.CLASSIC, GlassMotion.forId(null));
    }

    @Test
    public void aSpringStartsAtZeroSettlesOnOneAndOvershootsWhenUnderDamped() {
        GlassMotion.Spring spring = GlassMotion.MIST.enterScaleSpring;
        assertEquals(0f, spring.valueAt(0f), 0f);
        long settle = spring.settleMillis();
        assertTrue("settles in a fraction of a second, not never: " + settle,
            settle > 100L && settle < 1500L);
        assertEquals(1f, spring.valueAt(settle / 1000f + 0.5f), 0.002f);
        float peak = 0f;
        for (float t = 0f; t < 1f; t += 0.005f) peak = Math.max(peak, spring.valueAt(t));
        assertTrue("0.8 damping overshoots a little", peak > 1f && peak < 1.05f);
    }

    @Test
    public void aCriticallyDampedSpringNeverOvershoots() {
        GlassMotion.Spring spring = new GlassMotion.Spring(1f, 400f);
        for (float t = 0f; t < 1f; t += 0.005f)
            assertTrue(spring.valueAt(t) <= 1.0001f);
    }
}
