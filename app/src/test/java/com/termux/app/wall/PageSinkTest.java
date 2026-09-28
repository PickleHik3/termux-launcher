package com.termux.app.wall;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * The sink a held border gives its page: down with weight on a firm spring, up again on a looser
 * one that rises a hair past flush before it settles, and the scale and dim it draws.
 */
public class PageSinkTest {

    private static final float EPS = 0.001f;

    private static float down(long ms) {
        return PageSink.value(0f, 1f, ms, PageSink.SINK_STIFFNESS, PageSink.SINK_DAMPING);
    }

    private static float up(long ms) {
        return PageSink.value(1f, 0f, ms, PageSink.RISE_STIFFNESS, PageSink.RISE_DAMPING);
    }

    @Test
    public void theSpringStartsWhereItStandsAndEndsWhereItIsSent() {
        assertEquals(0f, down(0L), EPS);
        assertEquals(1f, up(0L), EPS);
        long sinkMs = PageSink.durationMs(PageSink.SINK_STIFFNESS, PageSink.SINK_DAMPING);
        long riseMs = PageSink.durationMs(PageSink.RISE_STIFFNESS, PageSink.RISE_DAMPING);
        assertEquals(1f, down(sinkMs), 0.01f);
        assertEquals(0f, up(riseMs), 0.01f);
        // And it does not wander off again after.
        assertEquals(1f, down(sinkMs * 3), 0.005f);
        assertEquals(0f, up(riseMs * 3), 0.005f);
    }

    @Test
    public void bothWaysTakeAGesturesTimeNotASlidesWorth() {
        long sinkMs = PageSink.durationMs(PageSink.SINK_STIFFNESS, PageSink.SINK_DAMPING);
        long riseMs = PageSink.durationMs(PageSink.RISE_STIFFNESS, PageSink.RISE_DAMPING);
        assertTrue("down: " + sinkMs, sinkMs >= 200L && sinkMs <= 600L);
        assertTrue("up: " + riseMs, riseMs >= 250L && riseMs <= 700L);
        // Most of the way down lands quickly, so the hold reads as taken at once.
        assertTrue(down(120L) > 0.75f);
    }

    @Test
    public void theWayDownLandsWithOnlyATraceOfGive() {
        float deepest = 0f;
        for (long ms = 0L; ms <= 800L; ms += 4L) deepest = Math.max(deepest, down(ms));
        assertTrue("it overshoots a little: " + deepest, deepest > 1f);
        assertTrue("but only a trace: " + deepest, deepest < 1.06f);
    }

    @Test
    public void theWayUpRisesAHairPastFlushBeforeItSettles() {
        float highest = 0f;
        for (long ms = 0L; ms <= 900L; ms += 4L) highest = Math.min(highest, up(ms));
        assertTrue("it rises past flush: " + highest, highest < -0.05f);
        assertTrue("by a little: " + highest, highest > -0.2f);
        float overshootScale = PageSink.scale(highest);
        assertTrue(overshootScale > 1f && overshootScale < 1.012f);
    }

    @Test
    public void aSunkPageIsSmallerAndDimmerAndARisenOneIsAsItWas() {
        assertEquals(1f, PageSink.scale(0f), EPS);
        assertEquals(PageSink.SCALE, PageSink.scale(1f), EPS);
        assertTrue(PageSink.SCALE >= 0.92f && PageSink.SCALE <= 0.96f);
        assertEquals(0f, PageSink.dim(0f), EPS);
        assertEquals(PageSink.DIM, PageSink.dim(1f), EPS);
        assertEquals("never brighter than at rest", 0f, PageSink.dim(-0.1f), EPS);
        assertEquals("never dimmer than fully sunk", PageSink.DIM, PageSink.dim(1.04f), EPS);
    }

    @Test
    public void theDimIsAGreyMultiplierInLevels() {
        int steps = 16;
        assertEquals(0, PageSink.dimLevel(0f, steps));
        assertEquals(steps - 1, PageSink.dimLevel(1f, steps));
        assertEquals(steps - 1, PageSink.dimLevel(1.05f, steps));
        assertEquals(0, PageSink.dimLevel(-0.1f, steps));
        assertEquals(0xFFFFFF, PageSink.dimMultiplier(0, steps));
        int full = PageSink.dimMultiplier(steps - 1, steps);
        int channel = Math.round(255f * (1f - PageSink.DIM));
        assertEquals((channel << 16) | (channel << 8) | channel, full);
        int previous = 256;
        for (int level = 0; level < steps; level++) {
            int blue = PageSink.dimMultiplier(level, steps) & 0xFF;
            assertTrue(blue <= previous);
            previous = blue;
        }
    }
}
