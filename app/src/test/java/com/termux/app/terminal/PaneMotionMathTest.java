package com.termux.app.terminal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * The guards, not the pixels. Every one of these pins a fault the pane motion actually shipped
 * with: ghosts over panes nobody could see, a smear computed from a detached view's stale size,
 * and a frame-rate-dependent step.
 */
public class PaneMotionMathTest {

    /**
     * The one that matters: a detached view keeps its last measured size, so a size-only check
     * passes while getLocationOnScreen reports 0,0 and every derived rect lands at the origin.
     */
    @Test
    public void measuredButDetachedIsNotAnimatable() {
        assertFalse(PaneMotionMath.canAnimate(false, true, 800, 600));
        assertFalse(PaneMotionMath.canAnimate(true, false, 800, 600));
        assertFalse(PaneMotionMath.canAnimate(true, true, 0, 600));
        assertTrue(PaneMotionMath.canAnimate(true, true, 800, 600));
    }

    @Test
    public void aNudgeIsNotWorthASmear() {
        float cell = 12f;
        assertFalse(PaneMotionMath.isTravelWorthAnimating(cell, cell));
        assertTrue(PaneMotionMath.isTravelWorthAnimating(cell * 2f, cell));
        // No cell size means no threshold to compare against, so nothing is animated.
        assertFalse(PaneMotionMath.isTravelWorthAnimating(400f, 0f));
    }

    /**
     * kitty's law is frame-rate independent: two steps at 60fps must land where one step at 30fps
     * lands. This is the property that lets the smear survive a dropped frame without overshooting.
     */
    @Test
    public void theStepIsFrameRateIndependent() {
        float decay = PaneMotionMath.DECAY_SLOW;
        float remaining = 1f;
        remaining *= 1f - PaneMotionMath.step(1f / 60f, decay);
        remaining *= 1f - PaneMotionMath.step(1f / 60f, decay);
        float oneBigStep = 1f - PaneMotionMath.step(1f / 30f, decay);
        assertEquals(oneBigStep, remaining, 1e-5f);
    }

    @Test
    public void theStepNeverOvershoots() {
        assertEquals(1f, PaneMotionMath.step(10f, PaneMotionMath.DECAY_FAST), 1e-6f);
        assertTrue(PaneMotionMath.step(1f / 60f, PaneMotionMath.DECAY_SLOW) > 0f);
        assertTrue(PaneMotionMath.step(1f / 60f, PaneMotionMath.DECAY_SLOW) < 1f);
    }

    /** Leading corners take the fast decay, trailing ones the slow: that difference is the shear. */
    @Test
    public void leadingCornersDecayFasterThanTrailingOnes() {
        assertEquals(PaneMotionMath.DECAY_FAST, PaneMotionMath.cornerDecay(1f), 1e-6f);
        assertEquals(PaneMotionMath.DECAY_SLOW, PaneMotionMath.cornerDecay(0f), 1e-6f);
        assertTrue(PaneMotionMath.cornerDecay(0.9f) < PaneMotionMath.cornerDecay(0.1f));
    }

    @Test
    public void alignmentsAreSpreadAcrossTheFourCorners() {
        float[] alignments = {-0.5f, 0f, 0.25f, 0.5f};
        PaneMotionMath.normaliseAlignments(alignments);
        assertEquals(0f, alignments[0], 1e-6f);
        assertEquals(1f, alignments[3], 1e-6f);
        assertTrue(alignments[1] > 0f && alignments[1] < 1f);
    }

    /** A travel with no direction must not divide by its own zero spread. */
    @Test
    public void adirectionlessTravelCollapsesToAPlainMove() {
        float[] alignments = {0f, 0f, 0f, 0f};
        PaneMotionMath.normaliseAlignments(alignments);
        for (float value : alignments) assertEquals(1f, value, 1e-6f);
    }

    @Test
    public void settlingIsMeasuredInCells() {
        float cell = 20f;
        assertTrue(PaneMotionMath.hasSettled(new float[] {1f, -2f, 3f, 0f}, cell));
        assertFalse(PaneMotionMath.hasSettled(new float[] {1f, -2f, 30f, 0f}, cell));
    }
}
