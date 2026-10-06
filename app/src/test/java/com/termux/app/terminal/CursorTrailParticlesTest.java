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
        assertEquals(0, p.collect(5_000L, CursorTrailParticles.MODE_RAILGUN, buf()));
        assertFalse(p.alive(5_000L));
    }

    @Test
    public void particlesExistShortlyAfterALongMove() {
        CursorTrailParticles p = new CursorTrailParticles();
        recordLongMove(p, 2_000L);
        assertTrue(p.alive(2_100L));
        for (int mode = 0; mode < 3; mode++) {
            int n = p.collect(2_100L, mode, buf());
            assertTrue("mode " + mode, n > 0);
            assertTrue(n <= CursorTrailParticles.MAX_PARTICLES);
        }
    }

    /** kitty emits for the whole move: a hundred cells at 3 per line height is the 48 cap. */
    @Test
    public void aLongMoveEmitsTheFullCountOverTheEmitDuration() {
        CursorTrailParticles p = new CursorTrailParticles();
        recordLongMove(p, 2_000L);
        // Just after the move only the first few particles have left the old position.
        int early = p.collect(2_001L, CursorTrailParticles.MODE_TORPEDO, buf());
        int emitted = p.collect(2_000L + 90L, CursorTrailParticles.MODE_TORPEDO, buf());
        assertTrue("early " + early + " emitted " + emitted, early < emitted);
        assertTrue("emitted " + emitted, emitted > CursorTrailParticles.MAX_PARTICLES / 2);
    }

    @Test
    public void nothingAliveAfterLifetimePlusEmit() {
        CursorTrailParticles p = new CursorTrailParticles();
        recordLongMove(p, 2_000L);
        long later = 2_000L + CursorTrailParticles.MAX_AGE_MS + 1L;
        assertFalse(p.alive(later));
        assertEquals(0, p.collect(later, CursorTrailParticles.MODE_PIXIEDUST, buf()));
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
            for (int mode = 0; mode < 3; mode++) assertEquals(0, p.collect(now, mode, out));
        }
    }

    /** Two moves after an idle spell each get their own pattern, not the same one twice. */
    @Test
    public void everyMoveGetsItsOwnPattern() {
        CursorTrailParticles p = new CursorTrailParticles();
        recordLongMove(p, 2_000L);
        float[] first = buf();
        int n1 = p.collect(2_200L, CursorTrailParticles.MODE_PIXIEDUST, first);
        // The very same move again, long after the first burst died.
        recordLongMove(p, 9_000L);
        float[] second = buf();
        int n2 = p.collect(9_200L, CursorTrailParticles.MODE_PIXIEDUST, second);
        assertTrue(n1 > 0 && n2 > 0);
        assertFalse("identical bursts",
            Arrays.equals(Arrays.copyOf(first, n1 * 4), Arrays.copyOf(second, n2 * 4)));
    }

    @Test
    public void ringKeepsTheEightNewestMoves() {
        CursorTrailParticles p = new CursorTrailParticles();
        for (int i = 0; i < 12; i++) recordLongMove(p, 2_000L + i * 10L);
        assertEquals(CursorTrailParticles.MAX_MOVES, p.moveCount());
        int n = p.collect(2_150L, CursorTrailParticles.MODE_RAILGUN, buf());
        assertTrue(n <= CursorTrailParticles.MAX_MOVES * CursorTrailParticles.MAX_PARTICLES);
        assertTrue(n > CursorTrailParticles.MAX_PARTICLES);
    }

    /** Torpedo flies backwards: on average the particles sit behind the move's midpoint. */
    @Test
    public void torpedoDriftsAgainstTheMoveDirection() {
        CursorTrailParticles p = new CursorTrailParticles();
        recordLongMove(p, 2_000L);
        float[] out = buf();
        int n = p.collect(2_300L, CursorTrailParticles.MODE_TORPEDO, out);
        assertTrue(n > 0);
        float sum = 0f;
        for (int i = 0; i < n; i++) sum += out[i * 4];
        float mean = sum / n;
        float midpoint = (CELL_W / 2f + 1000f + CELL_W / 2f) / 2f;
        assertTrue("mean x " + mean + " should be left of " + midpoint, mean < midpoint);
    }

    /**
     * Railgun fires sideways off the path. In kitty's y-up space the first particles of a
     * rightward move leave along +perp, which is up the screen; the port must not mirror that.
     */
    @Test
    public void railgunFiresItsFirstParticlesUpForARightwardMove() {
        CursorTrailParticles p = new CursorTrailParticles();
        // One line height long, so the spiral turns at most 1.5 rad along it.
        p.record(0f, 100f, CELL_W, 100f + CELL_H, 2 * CELL_W, 100f, 3 * CELL_W, 100f + CELL_H,
            2_000L);
        float[] out = buf();
        int n = p.collect(2_120L, CursorTrailParticles.MODE_RAILGUN, out);
        assertTrue(n > 0);
        float pathY = 100f + CELL_H / 2f;
        int above = 0;
        for (int i = 0; i < n; i++) if (out[i * 4 + 1] < pathY) above++;
        assertTrue(above + " of " + n + " above the path", above * 2 > n);
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
        recordLongMove(a, 3_250L);
        recordLongMove(b, 3_250L);
        float[] oa = buf(), ob = buf();
        int na = a.collect(3_400L, CursorTrailParticles.MODE_RAILGUN, oa);
        int nb = b.collect(3_400L, CursorTrailParticles.MODE_RAILGUN, ob);
        assertEquals(na, nb);
        assertArrayEquals(oa, ob, 0f);
    }

    @Test
    public void alphaAndRadiusStayInRange() {
        CursorTrailParticles p = new CursorTrailParticles();
        recordLongMove(p, 2_000L);
        float[] out = buf();
        int n = p.collect(2_200L, CursorTrailParticles.MODE_PIXIEDUST, out);
        for (int i = 0; i < n; i++) {
            assertTrue(out[i * 4 + 2] > 0f);
            assertTrue(out[i * 4 + 3] >= 0f && out[i * 4 + 3] <= 1f);
        }
    }
}
