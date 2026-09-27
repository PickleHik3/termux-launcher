package com.termux.view;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Pins the kitty semantics {@link KittyCursorTrail} ports one-to-one: the start threshold, the
 * {@code cursor_trail} delay, the decay shape that makes leading corners arrive first, the opacity
 * fade on DECTCEM, and the settle tail that keeps rendering for one extra frame.
 */
public class KittyCursorTrailTest {

    private static final float CELL_W = 10f;
    private static final float CELL_H = 20f;

    private static KittyCursorTrail.Config config(long delayMs, int thresholdX, int thresholdY) {
        return new KittyCursorTrail.Config(delayMs, 0.10f, 0.40f, thresholdX, thresholdY);
    }

    /** First ever update has nothing to trail from, so it must snap rather than smear in from 0,0. */
    @Test
    public void firstUpdateSnapsWithNoSmear() {
        KittyCursorTrail trail = new KittyCursorTrail();
        trail.update(0L, 100f, 100f, 110f, 120f, true, 0L, false, CELL_W, CELL_H, config(0, 2, 2));
        assertEquals(110f, trail.cornerX(0), 1e-4f); // top-right
        assertEquals(100f, trail.cornerY(0), 1e-4f);
        assertEquals(100f, trail.cornerX(3), 1e-4f); // top-left
        assertFalse(trail.needsRender());
    }

    /** A move inside the start threshold is skipped: the corners jump straight to the new cell. */
    @Test
    public void moveWithinThresholdIsSkipped() {
        KittyCursorTrail trail = new KittyCursorTrail();
        KittyCursorTrail.Config cfg = config(0, 2, 2);
        trail.update(0L, 0f, 0f, CELL_W, CELL_H, true, 0L, false, CELL_W, CELL_H, cfg);
        // One cell to the right: within the default threshold of 2 cells, so no smear starts.
        trail.update(16L, CELL_W, 0f, 2 * CELL_W, CELL_H, true, 0L, false, CELL_W, CELL_H, cfg);
        assertEquals(2 * CELL_W, trail.cornerX(0), 1e-4f);
        assertFalse(trail.needsRender());
    }

    /** A move beyond the start threshold begins a real smear: the corner lags behind the target. */
    @Test
    public void moveBeyondThresholdAnimates() {
        KittyCursorTrail trail = new KittyCursorTrail();
        KittyCursorTrail.Config cfg = config(0, 2, 2);
        trail.update(0L, 0f, 0f, CELL_W, CELL_H, true, 0L, false, CELL_W, CELL_H, cfg);
        // Ten cells away: well past the threshold.
        boolean needsFrame = trail.update(16L, 10 * CELL_W, 0f, 11 * CELL_W, CELL_H, true, 0L,
            false, CELL_W, CELL_H, cfg);
        assertTrue(needsFrame);
        assertTrue(trail.needsRender());
        // The corner has started moving but, one frame in, has not arrived yet.
        assertTrue(trail.cornerX(0) > CELL_W);
        assertTrue(trail.cornerX(0) < 11 * CELL_W);
    }

    /** Until {@code cursor_trail}'s delay elapses, the target must not move at all. */
    @Test
    public void delayGatesWhenTheTargetIsPickedUp() {
        KittyCursorTrail trail = new KittyCursorTrail();
        KittyCursorTrail.Config cfg = config(50, 0, 0);
        // Cursor last actually moved at t=0; the trail is asked to update at t=10, inside the delay.
        boolean needsFrame = trail.update(10L, 500f, 0f, 510f, CELL_H, true, 0L, false,
            CELL_W, CELL_H, cfg);
        // The corner should still be sitting at its snapped starting point (0,0 rect), not the
        // far-away target, and another frame must be requested to retry once the delay elapses.
        assertEquals(0f, trail.cornerX(0), 1e-4f);
        assertTrue(needsFrame);
        // Past the delay, the target is finally picked up.
        trail.update(60L, 500f, 0f, 510f, CELL_H, true, 0L, false, CELL_W, CELL_H, cfg);
        assertTrue(trail.needsRender());
    }

    /**
     * kitty's law: the corner leading the direction of travel decays with {@code decay_fast}, the
     * trailing one with {@code decay_slow}, so the leading edge of the smear arrives first and the
     * quad shears rather than sliding as a rigid copy.
     */
    @Test
    public void leadingCornerArrivesBeforeTrailingCorner() {
        KittyCursorTrail trail = new KittyCursorTrail();
        KittyCursorTrail.Config cfg = config(0, 0, 0);
        trail.update(0L, 0f, 0f, CELL_W, CELL_H, true, 0L, false, CELL_W, CELL_H, cfg);
        // A long rightward jump: corners 0 (top-right) and 1 (bottom-right) lead, 2 and 3 trail.
        float farRight = 500f;
        trail.update(50L, farRight, 0f, farRight + CELL_W, CELL_H, true, 0L, false,
            CELL_W, CELL_H, cfg);
        float leadingRemaining = (farRight + CELL_W) - trail.cornerX(0);
        float trailingRemaining = farRight - trail.cornerX(3);
        assertTrue("leading corner should have covered more ground than the trailing one",
            leadingRemaining < trailingRemaining);
    }

    /** DECTCEM on fades the trail in; off, it fades back out. Both driven by decay_slow. */
    @Test
    public void opacityFadesInWhenCursorIsShownAndOutWhenHidden() {
        KittyCursorTrail trail = new KittyCursorTrail();
        KittyCursorTrail.Config cfg = config(0, 0, 0);
        // First frame has no elapsed time to integrate over, so opacity starts at zero.
        trail.update(0L, 0f, 0f, CELL_W, CELL_H, true, 0L, false, CELL_W, CELL_H, cfg);
        assertEquals(0f, trail.opacity(), 1e-4f);
        // DECTCEM on: opacity climbs towards one over decay_slow seconds. Driven in 16 ms frames,
        // since the engine clamps a single step to 1/20 s so a paused app cannot jump the trail.
        long t = runFrames(trail, 0L, 200L, true, cfg);
        assertTrue(trail.opacity() > 0f);
        assertTrue(trail.opacity() < 1f);
        t = runFrames(trail, t, 800L, true, cfg);
        assertEquals(1f, trail.opacity(), 1e-4f);
        // DECTCEM off: opacity falls back towards zero, at the same rate.
        t = runFrames(trail, t, 200L, false, cfg);
        assertTrue(trail.opacity() < 1f);
        assertTrue(trail.opacity() > 0f);
        runFrames(trail, t, 800L, false, cfg);
        assertEquals(0f, trail.opacity(), 1e-4f);
    }

    /** Step the trail at a fixed cursor in 16 ms frames for {@code spanMillis}; returns the end time. */
    private static long runFrames(KittyCursorTrail trail, long fromMillis, long spanMillis,
                                  boolean dectcemOn, KittyCursorTrail.Config cfg) {
        long t = fromMillis;
        long end = fromMillis + spanMillis;
        while (t < end) {
            t = Math.min(end, t + 16L);
            trail.update(t, 0f, 0f, CELL_W, CELL_H, dectcemOn, 0L, false, CELL_W, CELL_H, cfg);
        }
        return t;
    }

    /** needs_render stays true for one extra frame after every corner has actually settled. */
    @Test
    public void needsRenderStaysTrueOneExtraFrameAfterSettling() {
        KittyCursorTrail trail = new KittyCursorTrail();
        KittyCursorTrail.Config cfg = config(0, 0, 0);
        trail.update(0L, 0f, 0f, CELL_W, CELL_H, true, 0L, false, CELL_W, CELL_H, cfg);
        long t = 16L;
        boolean needsFrame = true;
        int frames = 0;
        // Run the smear for a couple of seconds of frames, well past any reasonable settle time.
        while (needsFrame && frames < 500) {
            needsFrame = trail.update(t, 500f, 0f, 510f, CELL_H, true, 0L, false,
                CELL_W, CELL_H, cfg);
            t += 16L;
            frames++;
        }
        assertFalse("the trail must eventually stop asking for frames", needsFrame);
        assertFalse(trail.needsRender());
    }

    /** A live-resize-style discontinuity snaps the corners with no smear across the jump. */
    @Test
    public void snapRequestSkipsTheSmearOnce() {
        KittyCursorTrail trail = new KittyCursorTrail();
        KittyCursorTrail.Config cfg = config(0, 0, 0);
        trail.update(0L, 0f, 0f, CELL_W, CELL_H, true, 0L, false, CELL_W, CELL_H, cfg);
        trail.requestSnapOnNextUpdate();
        trail.update(16L, 500f, 0f, 510f, CELL_H, true, 0L, false, CELL_W, CELL_H, cfg);
        assertEquals(510f, trail.cornerX(0), 1e-4f);
        assertFalse(trail.needsRender());
    }
}
