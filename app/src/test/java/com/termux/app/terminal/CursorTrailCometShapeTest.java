package com.termux.app.terminal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** The Comet must read clearly on an ordinary move: Enter, or jumping to the start of a line. */
public class CursorTrailCometShapeTest {

    private static final float CELL_W = 22f;
    private static final float CELL_H = 48f;

    /** Enter at the end of a 30-column line: the cursor drops a line to the first column. */
    private static CursorTrailCometShape enterMove(long atMs) {
        CursorTrailCometShape comet = new CursorTrailCometShape();
        float fromL = 30 * CELL_W, fromT = 5 * CELL_H;
        float toL = 0f, toT = 6 * CELL_H;
        comet.start(fromL, fromT, fromL + CELL_W, fromT + CELL_H,
            toL, toT, toL + CELL_W, toT + CELL_H, atMs);
        return comet;
    }

    @Test
    public void headSitsOnTheCursorsTrailingEdgeNotUnderIt() {
        CursorTrailCometShape comet = enterMove(1_000L);
        assertTrue(comet.layout(1_016L));
        // The move goes left, so the edge facing the old position is the cursor's right edge.
        assertEquals(CELL_W, comet.headX(), 0.01f);
        assertTrue(comet.headY() > 6 * CELL_H && comet.headY() < 7 * CELL_H);
    }

    @Test
    public void tailReachesBackAlongThePathAtTheStart() {
        CursorTrailCometShape comet = enterMove(1_000L);
        assertTrue(comet.layout(1_016L));
        float path = 30 * CELL_W;
        assertTrue("tail " + comet.length(), comet.length() > 0.85f * (path - CELL_W / 2f));
        // The tail points back toward the old cell, up and to the right.
        assertTrue(comet.tailX() > 25 * CELL_W);
        assertTrue(comet.tailY() < comet.headY());
        assertTrue(comet.strength() > 0.95f);
        // As tall as the cursor across a mostly horizontal move.
        assertTrue(comet.halfWidth() >= CELL_H / 2f);
    }

    @Test
    public void staysClearlyVisibleForAQuarterSecondThenEnds() {
        CursorTrailCometShape comet = enterMove(1_000L);
        assertTrue(comet.alive(1_250L));
        assertTrue(comet.layout(1_250L));
        assertTrue("strength " + comet.strength(), comet.strength() > 0.7f);
        assertTrue("tail " + comet.length(), comet.length() > 5 * CELL_W);
        // The tail only ever draws in toward the head.
        float earlier = comet.length();
        assertTrue(comet.layout(1_400L));
        assertTrue(comet.length() < earlier);
        assertFalse(comet.alive(1_000L + CursorTrailCometShape.DURATION_MS));
        assertFalse(comet.layout(1_000L + CursorTrailCometShape.DURATION_MS));
    }

    @Test
    public void outlineTapersToAPointAtTheTail() {
        CursorTrailCometShape comet = enterMove(1_000L);
        assertTrue(comet.layout(1_100L));
        float[] outline = comet.outline();
        int n = comet.pointCount();
        assertEquals(CursorTrailCometShape.MAX_POINTS, n);
        int tip = n / 2;
        assertEquals(comet.tailX(), outline[tip * 2], 1e-3f);
        assertEquals(comet.tailY(), outline[tip * 2 + 1], 1e-3f);
        // Past the head, the whole streak lies outside the cursor cell, behind it.
        for (int i = 2; i < n - 2; i++) {
            assertTrue("point " + i + " x " + outline[i * 2], outline[i * 2] >= CELL_W - 0.01f);
        }
    }

    @Test
    public void aBeamCursorMovingDownStillGetsAVisibleStreak() {
        CursorTrailCometShape comet = new CursorTrailCometShape();
        float beam = CELL_W / 4f;
        comet.start(0f, 0f, beam, CELL_H, 0f, 10 * CELL_H, beam, 11 * CELL_H, 0L);
        assertTrue(comet.layout(16L));
        assertTrue(comet.halfWidth() >= 0.35f * CELL_H / 2f);
    }

    @Test
    public void aMoveThatStaysInsideTheCursorShowsNothing() {
        CursorTrailCometShape comet = new CursorTrailCometShape();
        comet.start(0f, 0f, CELL_W, CELL_H, 0.5f, 0f, CELL_W + 0.5f, CELL_H, 0L);
        assertFalse(comet.active());
        assertFalse(comet.layout(16L));
    }

    @Test
    public void aNewMoveReplacesTheOneInFlight() {
        CursorTrailCometShape comet = enterMove(1_000L);
        comet.start(0f, 0f, CELL_W, CELL_H, 20 * CELL_W, 0f, 21 * CELL_W, CELL_H, 1_200L);
        assertTrue(comet.layout(1_216L));
        // Rightward now: the head is on the new cursor's left edge, the tail far to its left.
        assertEquals(20 * CELL_W, comet.headX(), 0.01f);
        assertTrue(comet.tailX() < 5 * CELL_W);
    }
}
