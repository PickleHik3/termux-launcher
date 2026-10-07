package com.termux.app.terminal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** Motion blur must read as a blur: soft, lagging, lingering, and never one crisp copy. */
public class CursorTrailBlurShapeTest {

    private static final float CELL_W = 22f;
    private static final float CELL_H = 48f;
    /** The app's default slow decay, in seconds. */
    private static final float DECAY_SLOW = 0.4f;

    /** The cursor jumps forty columns to the right along one line. */
    private static CursorTrailBlurShape longMove(long atMs) {
        CursorTrailBlurShape blur = new CursorTrailBlurShape();
        blur.start(0f, 0f, CELL_W, CELL_H, 40 * CELL_W, 0f, 41 * CELL_W, CELL_H, atMs,
            DECAY_SLOW);
        return blur;
    }

    @Test
    public void theHeadLagsBehindTheCursorAndTheTailLagsFurther() {
        CursorTrailBlurShape blur = longMove(1_000L);
        assertTrue(blur.layout(1_050L));
        float cursorX = 40.5f * CELL_W;
        assertTrue("head " + blur.headX(), blur.headX() < cursorX - 5 * CELL_W);
        assertTrue("tail " + blur.tailX(), blur.tailX() < blur.headX() - 3 * CELL_W);
        assertTrue(blur.tailX() >= CELL_W / 2f);
    }

    /** Default settles in under half a second at these decays; the blur is still showing. */
    @Test
    public void lingersLongerThanTheDefaultQuadThenEnds() {
        CursorTrailBlurShape blur = longMove(1_000L);
        assertTrue(blur.alive(1_600L));
        assertTrue(blur.layout(1_600L));
        assertTrue("strength " + blur.strength(), blur.strength() > 0.2f);
        long lifetime = CursorTrailBlurShape.lifetimeMs(DECAY_SLOW);
        assertFalse(blur.alive(1_000L + lifetime));
        assertFalse(blur.layout(1_000L + lifetime));
    }

    /** A user's slower decay makes the smear last longer, within bounds. */
    @Test
    public void lifetimeFollowsTheSlowDecay() {
        assertTrue(CursorTrailBlurShape.lifetimeMs(0.8f) > CursorTrailBlurShape.lifetimeMs(0.4f));
        assertEquals(CursorTrailBlurShape.MIN_LIFETIME_MS, CursorTrailBlurShape.lifetimeMs(0.01f));
        assertEquals(CursorTrailBlurShape.MAX_LIFETIME_MS, CursorTrailBlurShape.lifetimeMs(60f));
    }

    @Test
    public void manyGhostsFromHeadToTailFadingTowardTheTail() {
        CursorTrailBlurShape blur = longMove(1_000L);
        assertTrue(blur.layout(1_060L));
        int n = blur.ghostCount();
        assertTrue("ghosts " + n, n >= 6);
        assertEquals(blur.headX(), blur.ghostX(0), 1e-3f);
        assertEquals(blur.tailX(), blur.ghostX(n - 1), 1e-3f);
        for (int i = 1; i < n; i++) {
            assertTrue(blur.ghostX(i) <= blur.ghostX(i - 1));
            assertTrue(blur.ghostAlpha(i) < blur.ghostAlpha(i - 1));
        }
    }

    /** Neighbouring ghosts always overlap, so even a screen-wide jump is one continuous smear. */
    @Test
    public void ghostsOverlapEvenOnAVeryLongJump() {
        CursorTrailBlurShape blur = new CursorTrailBlurShape();
        blur.start(0f, 0f, CELL_W, CELL_H, 200 * CELL_W, 0f, 201 * CELL_W, CELL_H, 0L,
            DECAY_SLOW);
        assertTrue(blur.layout(40L));
        int n = blur.ghostCount();
        assertEquals(CursorTrailBlurShape.MAX_GHOSTS, n);
        float spacing = Math.abs(blur.ghostX(0) - blur.ghostX(1));
        assertTrue("along " + blur.along() + " spacing " + spacing, 2f * blur.along() > spacing);
    }

    /** Wider than the cursor: the feather spreads past the cell on every side. */
    @Test
    public void ghostsAreWiderThanTheCursor() {
        CursorTrailBlurShape blur = longMove(1_000L);
        assertTrue(blur.layout(1_100L));
        assertTrue(blur.across() > CELL_H / 2f);
        assertTrue(blur.along() > CELL_W / 2f);
        assertEquals(0f, blur.degrees(), 1e-3f);
    }

    /** Low contrast: however deep the ghosts pile, together they reach only the peak alpha. */
    @Test
    public void ghostAlphaAddsUpToThePeakAtAnyDepth() {
        for (int count = 2; count <= CursorTrailBlurShape.MAX_GHOSTS; count++) {
            for (float spacing : new float[] {0f, 1f, 7f, 30f, 200f}) {
                float a = CursorTrailBlurShape.ghostAlpha(60f, spacing, count);
                float depth = spacing > 0f ? Math.max(1f, Math.min(count, 60f / spacing)) : count;
                float combined = 1f - (float) Math.pow(1f - a, depth);
                assertEquals(CursorTrailBlurShape.PEAK_ALPHA, combined, 1e-4f);
                assertTrue(a > 0f && a <= CursorTrailBlurShape.PEAK_ALPHA);
            }
        }
    }

    @Test
    public void ghostCountGrowsOneAtATimeAndIsCapped() {
        assertEquals(2, CursorTrailBlurShape.ghostCount(0f, 20f));
        int previous = 2;
        for (float span = 0f; span < 5_000f; span += 3f) {
            int n = CursorTrailBlurShape.ghostCount(span, 20f);
            assertTrue(n - previous <= 1 && n >= previous);
            previous = n;
        }
        assertEquals(CursorTrailBlurShape.MAX_GHOSTS, previous);
    }

    /** A second jump continues from where the smear is on screen, not from the old cell. */
    @Test
    public void aNewMovePicksUpFromTheVisibleHead() {
        CursorTrailBlurShape blur = longMove(1_000L);
        assertTrue(blur.layout(1_040L));
        float headX = blur.headX();
        blur.start(40 * CELL_W, 0f, 41 * CELL_W, CELL_H, 0f, 5 * CELL_H, CELL_W, 6 * CELL_H,
            1_040L, DECAY_SLOW);
        assertTrue(blur.layout(1_040L));
        assertEquals(headX, blur.tailX(), 0.5f);
    }

    @Test
    public void aMoveOntoTheSameCentreShowsNothing() {
        CursorTrailBlurShape blur = new CursorTrailBlurShape();
        blur.start(0f, 0f, CELL_W, CELL_H, 0.2f, 0f, CELL_W + 0.2f, CELL_H, 0L, DECAY_SLOW);
        assertFalse(blur.active());
        assertFalse(blur.layout(16L));
    }

    @Test
    public void boundsHoldEveryGhost() {
        CursorTrailBlurShape blur = new CursorTrailBlurShape();
        blur.start(0f, 0f, CELL_W, CELL_H, 200 * CELL_W, 9 * CELL_H, 201 * CELL_W, 10 * CELL_H,
            0L, DECAY_SLOW);
        for (long t = 0L; t < CursorTrailBlurShape.lifetimeMs(DECAY_SLOW); t += 16L) {
            assertTrue(blur.layout(t));
            float reach = Math.max(blur.along(), blur.across());
            for (int i = 0; i < blur.ghostCount(); i++) {
                assertTrue(blur.ghostX(i) - reach >= blur.boundsLeft());
                assertTrue(blur.ghostX(i) + reach <= blur.boundsRight());
                assertTrue(blur.ghostY(i) - reach >= blur.boundsTop());
                assertTrue(blur.ghostY(i) + reach <= blur.boundsBottom());
            }
        }
    }
}
