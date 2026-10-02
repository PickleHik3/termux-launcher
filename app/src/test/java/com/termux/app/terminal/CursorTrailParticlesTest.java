package com.termux.app.terminal;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class CursorTrailParticlesTest {

    private static final float CELL_W = 10f;
    private static final float CELL_H = 20f;

    /** A long horizontal move, ten cells wide hundred cells over: lots of particles. */
    private static void recordLongMove(CursorTrailParticles p, float t) {
        p.record(0f, 0f, CELL_W, CELL_H, 1000f, 0f, 1000f + CELL_W, CELL_H, t);
    }

    private static float[] buf() {
        return new float[CursorTrailParticles.OUT_SIZE];
    }

    @Test
    public void nothingBeforeAnyMove() {
        CursorTrailParticles p = new CursorTrailParticles();
        assertEquals(0, p.collect(5f, CursorTrailParticles.MODE_RAILGUN, buf()));
        assertFalse(p.alive(5f));
    }

    @Test
    public void particlesExistShortlyAfterALongMove() {
        CursorTrailParticles p = new CursorTrailParticles();
        recordLongMove(p, 2f);
        assertTrue(p.alive(2.1f));
        for (int mode = 0; mode < 3; mode++) {
            int n = p.collect(2.1f, mode, buf());
            assertTrue("mode " + mode, n > 0);
            assertTrue(n <= CursorTrailParticles.MAX_PARTICLES);
        }
    }

    @Test
    public void nothingAliveAfterLifetimePlusEmit() {
        CursorTrailParticles p = new CursorTrailParticles();
        recordLongMove(p, 2f);
        float later = 2f + CursorTrailParticles.LIFETIME + CursorTrailParticles.EMIT_DURATION + 0.01f;
        assertFalse(p.alive(later));
        assertEquals(0, p.collect(later, CursorTrailParticles.MODE_PIXIEDUST, buf()));
    }

    @Test
    public void ringKeepsTheEightNewestMoves() {
        CursorTrailParticles p = new CursorTrailParticles();
        for (int i = 0; i < 12; i++) recordLongMove(p, 2f + i * 0.01f);
        assertEquals(CursorTrailParticles.MAX_MOVES, p.moveCount());
        int n = p.collect(2.15f, CursorTrailParticles.MODE_RAILGUN, buf());
        assertTrue(n <= CursorTrailParticles.MAX_MOVES * CursorTrailParticles.MAX_PARTICLES);
        assertTrue(n > 0);
    }

    /** Torpedo flies backwards: on average the particles sit behind the move's midpoint. */
    @Test
    public void torpedoDriftsAgainstTheMoveDirection() {
        CursorTrailParticles p = new CursorTrailParticles();
        recordLongMove(p, 2f);
        float[] out = buf();
        int n = p.collect(2.3f, CursorTrailParticles.MODE_TORPEDO, out);
        assertTrue(n > 0);
        float sum = 0f;
        for (int i = 0; i < n; i++) sum += out[i * 4];
        float mean = sum / n;
        float midpoint = (CELL_W / 2f + 1000f + CELL_W / 2f) / 2f;
        assertTrue("mean x " + mean + " should be left of " + midpoint, mean < midpoint);
    }

    /** Pixiedust falls, and y is down: the average y grows as the dust ages. */
    @Test
    public void pixiedustFallsTowardPositiveY() {
        CursorTrailParticles p = new CursorTrailParticles();
        recordLongMove(p, 2f);
        float early = meanY(p, 2.1f);
        float late = meanY(p, 2.45f);
        assertTrue("early " + early + " late " + late, late > early);
    }

    private static float meanY(CursorTrailParticles p, float now) {
        float[] out = buf();
        int n = p.collect(now, CursorTrailParticles.MODE_PIXIEDUST, out);
        assertTrue(n > 0);
        float sum = 0f;
        for (int i = 0; i < n; i++) sum += out[i * 4 + 1];
        return sum / n;
    }

    @Test
    public void sameInputsGiveTheSameOutput() {
        CursorTrailParticles a = new CursorTrailParticles();
        CursorTrailParticles b = new CursorTrailParticles();
        recordLongMove(a, 3.25f);
        recordLongMove(b, 3.25f);
        float[] oa = buf(), ob = buf();
        int na = a.collect(3.4f, CursorTrailParticles.MODE_RAILGUN, oa);
        int nb = b.collect(3.4f, CursorTrailParticles.MODE_RAILGUN, ob);
        assertEquals(na, nb);
        assertArrayEquals(oa, ob, 0f);
    }

    @Test
    public void alphaAndRadiusStayInRange() {
        CursorTrailParticles p = new CursorTrailParticles();
        recordLongMove(p, 2f);
        float[] out = buf();
        int n = p.collect(2.2f, CursorTrailParticles.MODE_PIXIEDUST, out);
        for (int i = 0; i < n; i++) {
            assertTrue(out[i * 4 + 2] > 0f);
            assertTrue(out[i * 4 + 3] >= 0f && out[i * 4 + 3] <= 1f);
        }
    }
}
