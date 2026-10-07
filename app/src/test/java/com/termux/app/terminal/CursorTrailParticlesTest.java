package com.termux.app.terminal;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;

public class CursorTrailParticlesTest {

    private static final float CELL_W = 10f;
    private static final float CELL_H = 20f;

    /** A long horizontal move, a hundred cells over: lots of particles. */
    private static void recordLongMove(CursorTrailParticles p, long atMs) {
        p.record(0f, 0f, CELL_W, CELL_H, 1000f, 0f, 1000f + CELL_W, CELL_H, atMs);
    }

    private static float[] buf() {
        return new float[CursorTrailParticles.OUT_SIZE];
    }

    @Test
    public void nothingBeforeAnyMove() {
        CursorTrailParticles p = new CursorTrailParticles();
        assertEquals(0, p.collect(5_000L, buf()));
        assertFalse(p.alive(5_000L));
    }

    @Test
    public void particlesExistShortlyAfterALongMove() {
        CursorTrailParticles p = new CursorTrailParticles();
        recordLongMove(p, 2_000L);
        assertTrue(p.alive(2_100L));
        int n = p.collect(2_100L, buf());
        assertTrue(n > 0);
        assertTrue(n <= CursorTrailParticles.MAX_PARTICLES);
    }

    /** kitty emits for the whole move: a hundred cells at 3 per line height is the 48 cap. */
    @Test
    public void aLongMoveEmitsTheFullCountOverTheEmitDuration() {
        CursorTrailParticles p = new CursorTrailParticles();
        recordLongMove(p, 2_000L);
        // Just after the move only the first few particles have left the old position.
        int early = p.collect(2_001L, buf());
        int emitted = p.collect(2_000L + 90L, buf());
        assertTrue("early " + early + " emitted " + emitted, early < emitted);
        assertTrue("emitted " + emitted, emitted > CursorTrailParticles.MAX_PARTICLES / 2);
    }

    @Test
    public void nothingAliveAfterLifetimePlusEmit() {
        CursorTrailParticles p = new CursorTrailParticles();
        recordLongMove(p, 2_000L);
        long later = 2_000L + CursorTrailParticles.MAX_AGE_MS + 1L;
        assertFalse(p.alive(later));
        assertEquals(0, p.collect(later, buf()));
    }

    /**
     * A move only ever gets older: however long the overlay sleeps and whenever it wakes again (a
     * cursor blink, new output), a burst that has finished never plays again.
     */
    @Test
    public void aFinishedBurstNeverReplays() {
        CursorTrailParticles p = new CursorTrailParticles();
        recordLongMove(p, 2_000L);
        float[] out = buf();
        for (long now = 2_000L + CursorTrailParticles.MAX_AGE_MS; now < 60_000L; now += 530L) {
            assertFalse("alive at " + now, p.alive(now));
            assertEquals(0, p.collect(now, out));
        }
    }

    /** Two moves after an idle spell each get their own pattern, not the same one twice. */
    @Test
    public void everyMoveGetsItsOwnPattern() {
        CursorTrailParticles p = new CursorTrailParticles();
        recordLongMove(p, 2_000L);
        float[] first = buf();
        int n1 = p.collect(2_200L, first);
        // The very same move again, long after the first burst died.
        recordLongMove(p, 9_000L);
        float[] second = buf();
        int n2 = p.collect(9_200L, second);
        assertTrue(n1 > 0 && n2 > 0);
        assertFalse("identical bursts",
            Arrays.equals(Arrays.copyOf(first, n1 * 4), Arrays.copyOf(second, n2 * 4)));
    }

    @Test
    public void ringKeepsTheEightNewestMoves() {
        CursorTrailParticles p = new CursorTrailParticles();
        for (int i = 0; i < 12; i++) recordLongMove(p, 2_000L + i * 10L);
        assertEquals(CursorTrailParticles.MAX_MOVES, p.moveCount());
        int n = p.collect(2_150L, buf());
        assertTrue(n <= CursorTrailParticles.MAX_MOVES * CursorTrailParticles.MAX_PARTICLES);
        assertTrue(n > CursorTrailParticles.MAX_PARTICLES);
    }

    /** Pixiedust falls, and y is down: the average y grows as the dust ages. */
    @Test
    public void pixiedustFallsTowardPositiveY() {
        CursorTrailParticles p = new CursorTrailParticles();
        recordLongMove(p, 2_000L);
        float early = meanY(p, 2_100L);
        float late = meanY(p, 2_450L);
        assertTrue("early " + early + " late " + late, late > early);
    }

    private static float meanY(CursorTrailParticles p, long now) {
        float[] out = buf();
        int n = p.collect(now, out);
        assertTrue(n > 0);
        float sum = 0f;
        for (int i = 0; i < n; i++) sum += out[i * 4 + 1];
        return sum / n;
    }

    @Test
    public void sameInputsGiveTheSameOutput() {
        CursorTrailParticles a = new CursorTrailParticles();
        CursorTrailParticles b = new CursorTrailParticles();
        recordLongMove(a, 3_250L);
        recordLongMove(b, 3_250L);
        float[] oa = buf(), ob = buf();
        int na = a.collect(3_400L, oa);
        int nb = b.collect(3_400L, ob);
        assertEquals(na, nb);
        assertArrayEquals(oa, ob, 0f);
    }

    @Test
    public void alphaAndRadiusStayInRange() {
        CursorTrailParticles p = new CursorTrailParticles();
        recordLongMove(p, 2_000L);
        float[] out = buf();
        int n = p.collect(2_200L, out);
        for (int i = 0; i < n; i++) {
            assertTrue(out[i * 4 + 2] > 0f);
            assertTrue(out[i * 4 + 3] >= 0f && out[i * 4 + 3] <= 1f);
        }
    }
}
