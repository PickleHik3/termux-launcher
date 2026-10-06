package com.termux.app.terminal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** Railgun must read as a shot: a thin hot beam that is there and gone, and a few sparks. */
public class CursorTrailRailgunShapeTest {

    private static final float CELL_W = 22f;
    private static final float CELL_H = 48f;
    /** The app's default fast decay, in seconds. */
    private static final float DECAY_FAST = 0.1f;
    private static final float PATH_Y = CELL_H / 2f;

    /** The cursor jumps forty columns to the right along one line. */
    private static CursorTrailRailgunShape longShot(long atMs) {
        CursorTrailRailgunShape gun = new CursorTrailRailgunShape();
        gun.start(0f, 0f, CELL_W, CELL_H, 40 * CELL_W, 0f, 41 * CELL_W, CELL_H, atMs, DECAY_FAST);
        return gun;
    }

    @Test
    public void theBeamJoinsTheOldPositionToTheCursorTheMomentItFires() {
        CursorTrailRailgunShape gun = longShot(1_000L);
        assertTrue(gun.layout(1_000L));
        assertTrue(gun.beamVisible());
        assertEquals(CELL_W / 2f, gun.beamTailX(), 1e-3f);
        assertEquals(40.5f * CELL_W, gun.beamHeadX(), 1e-3f);
        assertEquals(PATH_Y, gun.beamTailY(), 1e-3f);
        assertEquals(1f, gun.beamAlpha(), 1e-6f);
    }

    /** A fraction of the cell wide: well under half the cursor's narrow side. */
    @Test
    public void theBeamIsThin() {
        CursorTrailRailgunShape gun = longShot(1_000L);
        assertTrue(gun.layout(1_000L));
        assertTrue("core " + gun.beamWidth(), gun.beamWidth() < CELL_W / 4f);
        assertTrue(gun.beamWidth() >= CursorTrailRailgunShape.MIN_CORE_PX);
    }

    @Test
    public void theFarEndSnapsInWhileTheBeamThinsAndFades() {
        CursorTrailRailgunShape gun = longShot(1_000L);
        assertTrue(gun.layout(1_050L));
        float tail = gun.beamTailX(), width = gun.beamWidth(), alpha = gun.beamAlpha();
        assertTrue(gun.layout(1_150L));
        assertTrue(gun.beamVisible());
        assertTrue(gun.beamTailX() > tail);
        assertTrue(gun.beamWidth() < width);
        assertTrue(gun.beamAlpha() < alpha);
    }

    /** Very short: the beam is gone in about a fifth of a second, the sparks soon after. */
    @Test
    public void theShotIsOverQuickly() {
        long beam = CursorTrailRailgunShape.beamLifeMs(DECAY_FAST);
        assertTrue("beam " + beam, beam <= 250L);
        CursorTrailRailgunShape gun = longShot(1_000L);
        assertTrue(gun.layout(1_000L + beam));
        assertFalse(gun.beamVisible());
        long end = Math.round(beam * CursorTrailRailgunShape.SPARK_LIFE_SCALE);
        assertTrue(end < 400L);
        assertFalse(gun.alive(1_000L + end));
        assertFalse(gun.layout(1_000L + end));
    }

    @Test
    public void beamLifeFollowsTheFastDecayWithinBounds() {
        assertTrue(CursorTrailRailgunShape.beamLifeMs(0.3f)
            > CursorTrailRailgunShape.beamLifeMs(0.1f));
        assertEquals(CursorTrailRailgunShape.MIN_BEAM_MS, CursorTrailRailgunShape.beamLifeMs(0f));
        assertEquals(CursorTrailRailgunShape.MAX_BEAM_MS, CursorTrailRailgunShape.beamLifeMs(5f));
    }

    /** Every spark has left the path, and leans the way the shot went. */
    @Test
    public void sparksFlyOffThePathLeaningForward() {
        CursorTrailRailgunShape gun = longShot(1_000L);
        assertTrue(gun.layout(1_100L));
        int n = gun.sparkCount();
        assertTrue("sparks " + n, n >= CursorTrailRailgunShape.MIN_SPARKS);
        float[] s = gun.sparks();
        for (int i = 0; i < n; i++) {
            int o = i * 5;
            assertTrue("spark " + i + " y " + s[o + 3], Math.abs(s[o + 3] - PATH_Y) > 0.3f * CELL_H);
            assertTrue("spark " + i + " leans back", s[o + 2] > s[o]);
            assertTrue(s[o + 4] > 0f && s[o + 4] <= 1f);
        }
    }

    @Test
    public void aFewSparksNeverMany() {
        assertEquals(CursorTrailRailgunShape.MIN_SPARKS,
            CursorTrailRailgunShape.sparkCount(10f, CELL_H));
        assertEquals(CursorTrailRailgunShape.MAX_SPARKS,
            CursorTrailRailgunShape.sparkCount(10_000f, CELL_H));
        int previous = 0;
        for (float len = 0f; len < 3_000f; len += 10f) {
            int n = CursorTrailRailgunShape.sparkCount(len, CELL_H);
            assertTrue(n >= previous);
            previous = n;
        }
    }

    @Test
    public void theCoreIsWhiteHot() {
        assertEquals(0xCCCCFF, CursorTrailRailgunShape.hotColor(0x0000FF));
        assertEquals(0xFFFFFF, CursorTrailRailgunShape.hotColor(0xFFFFFF));
    }

    @Test
    public void boundsHoldTheBeamAndEverySpark() {
        CursorTrailRailgunShape gun = new CursorTrailRailgunShape();
        gun.start(0f, 0f, CELL_W, CELL_H, 30 * CELL_W, 12 * CELL_H, 31 * CELL_W, 13 * CELL_H,
            0L, DECAY_FAST);
        for (long t = 0L; gun.layout(t); t += 8L) {
            if (gun.beamVisible()) {
                assertTrue(gun.beamTailX() >= gun.boundsLeft());
                assertTrue(gun.beamTailY() >= gun.boundsTop());
            }
            float[] s = gun.sparks();
            for (int i = 0; i < gun.sparkCount() * 5; i += 5) {
                for (int p = 0; p < 4; p += 2) {
                    assertTrue(s[i + p] >= gun.boundsLeft() && s[i + p] <= gun.boundsRight());
                    assertTrue(s[i + p + 1] >= gun.boundsTop()
                        && s[i + p + 1] <= gun.boundsBottom());
                }
            }
        }
    }

    @Test
    public void aMoveOntoTheSameCentreFiresNothing() {
        CursorTrailRailgunShape gun = new CursorTrailRailgunShape();
        gun.start(0f, 0f, CELL_W, CELL_H, 0.2f, 0f, CELL_W + 0.2f, CELL_H, 0L, DECAY_FAST);
        assertFalse(gun.active());
        assertFalse(gun.layout(16L));
    }
}
