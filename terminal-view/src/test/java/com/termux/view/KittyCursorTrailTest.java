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

    /**
     * kitty's {@code window_changed}: a move into another window always trails, even one that lands
     * inside the start threshold; the same small move inside one window does not.
     */
    @Test
    public void moveIntoAnotherPaneTrailsInsideTheThreshold() {
        KittyCursorTrail.Config cfg = config(0, 2, 2);
        KittyCursorTrail samePane = new KittyCursorTrail();
        samePane.update(0L, 0f, 0f, CELL_W, CELL_H, true, 0L, false, 7L, CELL_W, CELL_H, cfg);
        samePane.update(16L, CELL_W, 0f, 2 * CELL_W, CELL_H, true, 0L, false, 7L,
            CELL_W, CELL_H, cfg);
        assertFalse(samePane.moveStartedOnLastUpdate());

        KittyCursorTrail otherPane = new KittyCursorTrail();
        otherPane.update(0L, 0f, 0f, CELL_W, CELL_H, true, 0L, false, 7L, CELL_W, CELL_H, cfg);
        assertTrue(otherPane.update(16L, CELL_W, 0f, 2 * CELL_W, CELL_H, true, 0L, false, 8L,
            CELL_W, CELL_H, cfg));
        assertTrue(otherPane.moveStartedOnLastUpdate());
        assertTrue(otherPane.needsRender());
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

    /**
     * kitty spreads decay over the dots of all four corners, a corner that has arrived counting as
     * zero: when an underline cursor turns into a block in place, only the top corners move, and
     * against the settled bottom ones they lead, so they ease with decay_fast.
     */
    @Test
    public void cornersStillMovingAreSpreadAgainstArrivedOnes() {
        KittyCursorTrail trail = new KittyCursorTrail();
        KittyCursorTrail.Config cfg = config(0, 0, 0);
        float underlineTop = CELL_H * 3f / 4f;
        trail.update(0L, 0f, underlineTop, CELL_W, CELL_H, true, 0L, false, CELL_W, CELL_H, cfg);
        trail.update(16L, 0f, 0f, CELL_W, CELL_H, true, 0L, false, CELL_W, CELL_H, cfg);
        // decay_fast 0.1 s over 16 ms covers 1 - 2^-1.6 = 67% of the way; decay_slow only 24%.
        float covered = (underlineTop - trail.cornerY(0)) / underlineTop;
        assertEquals(1f - (float) Math.pow(2.0, -1.6), covered, 1e-3f);
    }

    /** DECTCEM on fades the trail in; off, it fades back out. Both driven by decay_slow. */
    @Test
    public void opacityFadesInWhenCursorIsShownAndOutWhenHidden() {
        KittyCursorTrail trail = new KittyCursorTrail();
        KittyCursorTrail.Config cfg = config(0, 0, 0);
        // First frame has no elapsed time to integrate over, so opacity starts at zero.
        trail.update(0L, 0f, 0f, CELL_W, CELL_H, true, 0L, false, CELL_W, CELL_H, cfg);
        assertEquals(0f, trail.opacity(), 1e-4f);
        // DECTCEM on: opacity climbs towards one over decay_slow seconds, driven in 16 ms frames.
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

    /**
     * kitty stops rendering once every corner is within half a <em>pixel</em> of its edge
     * ({@code g.dx / cell_size.width * 0.5}), not half a cell: a trail that stopped half a cell
     * early left its trailing edge frozen as a strip beside the cursor.
     */
    @Test
    public void trailStopsOnlyWithinHalfAPixelOfTheCursor() {
        KittyCursorTrail trail = new KittyCursorTrail();
        KittyCursorTrail.Config cfg = config(0, 0, 0);
        trail.update(0L, 400f, 0f, 410f, CELL_H, true, 0L, false, CELL_W, CELL_H, cfg);
        // Enter: from column 40 to the first column, three lines down.
        float left = 0f, top = CELL_H * 3, right = CELL_W, bottom = CELL_H * 4;
        long t = 16L;
        while (trail.update(t, left, top, right, bottom, true, 0L, false, CELL_W, CELL_H, cfg)) {
            t += 16L;
            assertTrue("the trail must settle", t < 5_000L);
        }
        for (int i = 0; i < KittyCursorTrail.CORNERS; i++) {
            float edgeX = (i == 0 || i == 1) ? right : left;
            float edgeY = (i == 0 || i == 3) ? top : bottom;
            assertEquals("corner " + i + " x", edgeX, trail.cornerX(i), 0.5f);
            assertEquals("corner " + i + " y", edgeY, trail.cornerY(i), 0.5f);
        }
    }

    /**
     * kitty's loop wakes for the output that moves the cursor, inside the delay, so the move is
     * integrated from about when the cursor moved. Seconds of idle before it must not be folded
     * into the first step, or every trail would start mostly finished.
     */
    @Test
    public void moveAfterLongIdleStartsFromTheOldCursor() {
        KittyCursorTrail trail = new KittyCursorTrail();
        KittyCursorTrail.Config cfg = config(10, 2, 2);
        assertFalse(trail.update(0L, 0f, 0f, CELL_W, CELL_H, true, 0L, false, CELL_W, CELL_H, cfg));
        assertFalse(trail.update(1_000L, 0f, 0f, CELL_W, CELL_H, true, 0L, false, CELL_W, CELL_H,
            cfg));
        // Five seconds later the cursor jumps 50 cells; the first frame lands 12 ms after it.
        long movedAt = 6_000L;
        assertTrue(trail.update(movedAt + 12L, 500f, 0f, 510f, CELL_H, true, movedAt, false,
            CELL_W, CELL_H, cfg));
        assertTrue(trail.moveStartedOnLastUpdate());
        // 12 ms of decay_slow (0.4 s) moves a trailing corner about 19% of the way, and 12 ms of
        // decay_fast about 56%; the old 50 ms clamp moved them 58% and 97%.
        float trailingCovered = trail.cornerX(3) / 500f;
        assertTrue("trailing corner covered " + trailingCovered, trailingCovered < 0.3f);
        float leadingCovered = (trail.cornerX(0) - CELL_W) / 500f;
        assertTrue("leading corner covered " + leadingCovered, leadingCovered < 0.7f);
    }

    /** kitty does not clamp the step: a stalled loop resumes by finishing the trail, not by jumping. */
    @Test
    public void stalledLoopFinishesTheTrail() {
        KittyCursorTrail trail = new KittyCursorTrail();
        KittyCursorTrail.Config cfg = config(0, 0, 0);
        trail.update(0L, 0f, 0f, CELL_W, CELL_H, true, 0L, false, CELL_W, CELL_H, cfg);
        assertTrue(trail.update(16L, 500f, 0f, 510f, CELL_H, true, 0L, false, CELL_W, CELL_H,
            cfg));
        trail.update(3_000L, 500f, 0f, 510f, CELL_H, true, 0L, false, CELL_W, CELL_H, cfg);
        assertFalse(trail.needsRender());
        assertEquals(510f, trail.cornerX(0), 0.5f);
        assertEquals(500f, trail.cornerX(3), 0.5f);
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

    /**
     * A snap asked for while the cursor's own move is still inside the delay (a keyboard resize
     * reflowing the prompt) lands on the new target at once; it must not be spent on the old one
     * and leave the next frame to smear across the resize.
     */
    @Test
    public void snapInsideTheDelayLandsOnTheNewTarget() {
        KittyCursorTrail trail = new KittyCursorTrail();
        KittyCursorTrail.Config cfg = config(10, 2, 2);
        trail.update(0L, 0f, 0f, CELL_W, CELL_H, true, 0L, false, CELL_W, CELL_H, cfg);
        trail.requestSnapOnNextUpdate();
        // The reflow moved the cursor 20 rows at t=1000; the first frame lands 4 ms later.
        trail.update(1_004L, 0f, 400f, CELL_W, 420f, true, 1_000L, false, CELL_W, CELL_H, cfg);
        assertEquals(400f, trail.cornerY(0), 1e-4f);
        assertFalse(trail.moveStartedOnLastUpdate());
        // Past the delay, nothing is left to trail.
        assertFalse(trail.update(1_020L, 0f, 400f, CELL_W, 420f, true, 1_000L, false,
            CELL_W, CELL_H, cfg));
        assertFalse(trail.moveStartedOnLastUpdate());
        assertFalse(trail.needsRender());
    }

    @Test
    public void prevCornersHoldTheLastFramesCorners() {
        KittyCursorTrail trail = new KittyCursorTrail();
        KittyCursorTrail.Config cfg = config(0, 2, 2);
        trail.update(0L, 0f, 0f, CELL_W, CELL_H, true, 0L, false, CELL_W, CELL_H, cfg);
        // The first update is a snap: prev equals current.
        assertEquals(trail.cornerX(0), trail.prevCornerX(0), 1e-6f);
        trail.update(16L, 10 * CELL_W, 0f, 11 * CELL_W, CELL_H, true, 0L, false, CELL_W, CELL_H, cfg);
        float afterFirst = trail.cornerX(0);
        assertEquals(CELL_W, trail.prevCornerX(0), 1e-6f);
        trail.update(32L, 10 * CELL_W, 0f, 11 * CELL_W, CELL_H, true, 0L, false, CELL_W, CELL_H, cfg);
        assertEquals(afterFirst, trail.prevCornerX(0), 1e-6f);
        assertTrue(trail.cornerX(0) > afterFirst);
    }

    @Test
    public void moveStartedIsTrueOnceOnARealMove() {
        KittyCursorTrail trail = new KittyCursorTrail();
        KittyCursorTrail.Config cfg = config(0, 2, 2);
        trail.update(0L, 0f, 0f, CELL_W, CELL_H, true, 0L, false, CELL_W, CELL_H, cfg);
        assertFalse(trail.moveStartedOnLastUpdate());
        trail.update(16L, 10 * CELL_W, 0f, 11 * CELL_W, CELL_H, true, 0L, false, CELL_W, CELL_H, cfg);
        assertTrue(trail.moveStartedOnLastUpdate());
        assertEquals(0f, trail.moveFromEdge(0), 1e-6f);
        assertEquals(CELL_W, trail.moveFromEdge(2), 1e-6f);
        assertEquals(10 * CELL_W, trail.moveToEdge(0), 1e-6f);
        assertEquals(11 * CELL_W, trail.moveToEdge(2), 1e-6f);
        trail.update(32L, 10 * CELL_W, 0f, 11 * CELL_W, CELL_H, true, 0L, false, CELL_W, CELL_H, cfg);
        assertFalse(trail.moveStartedOnLastUpdate());
    }

    @Test
    public void moveStartedIsFalseOnSnapAndWithinThreshold() {
        KittyCursorTrail trail = new KittyCursorTrail();
        KittyCursorTrail.Config cfg = config(0, 2, 2);
        trail.update(0L, 0f, 0f, CELL_W, CELL_H, true, 0L, false, CELL_W, CELL_H, cfg);
        trail.update(16L, CELL_W, 0f, 2 * CELL_W, CELL_H, true, 0L, false, CELL_W, CELL_H, cfg);
        assertFalse(trail.moveStartedOnLastUpdate());
        trail.requestSnapOnNextUpdate();
        trail.update(32L, 20 * CELL_W, 0f, 21 * CELL_W, CELL_H, true, 0L, false, CELL_W, CELL_H, cfg);
        assertFalse(trail.moveStartedOnLastUpdate());
        assertEquals(trail.cornerX(0), trail.prevCornerX(0), 1e-6f);
    }
}
