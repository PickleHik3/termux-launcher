package com.termux.app.terminal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Torpedo must read as one object travelling the path: a body wide at the nose and pointed at the
 * tail, slower than Railgun, leaving a faint wake behind it.
 */
public class CursorTrailTorpedoShapeTest {

    private static final float CELL_W = 22f;
    private static final float CELL_H = 48f;
    /** The app's default slow decay, in seconds. */
    private static final float DECAY_SLOW = 0.4f;
    private static final float PATH_Y = CELL_H / 2f;

    /** The cursor jumps forty columns to the right along one line. */
    private static CursorTrailTorpedoShape longRun(long atMs) {
        CursorTrailTorpedoShape torpedo = new CursorTrailTorpedoShape();
        torpedo.start(0f, 0f, CELL_W, CELL_H, 40 * CELL_W, 0f, 41 * CELL_W, CELL_H, atMs,
            DECAY_SLOW);
        return torpedo;
    }

    @Test
    public void theBodyIsWideAtTheNoseAndPointedAtTheTail() {
        CursorTrailTorpedoShape torpedo = longRun(1_000L);
        assertTrue(torpedo.layout(1_100L));
        assertTrue(torpedo.bodyVisible());
        assertTrue("radius " + torpedo.radius(), torpedo.radius() > 0.35f * CELL_H);
        float[] o = torpedo.outline();
        int n = torpedo.pointCount();
        assertEquals(CursorTrailTorpedoShape.MAX_POINTS, n);
        // The outline starts at the tail's point, on the path.
        assertEquals(torpedo.tailX(), o[0], 1e-3f);
        assertEquals(PATH_Y, o[1], 1e-3f);
        float widest = 0f, widestX = 0f, front = -Float.MAX_VALUE;
        for (int i = 0; i < n; i++) {
            float half = Math.abs(o[i * 2 + 1] - PATH_Y);
            assertTrue(half <= torpedo.radius() + 1e-3f);
            if (half > widest) {
                widest = half;
                widestX = o[i * 2];
            }
            front = Math.max(front, o[i * 2]);
        }
        assertEquals(torpedo.radius(), widest, 1e-3f);
        // Widest by the nose, not the tail.
        float middle = (torpedo.tailX() + torpedo.noseX()) / 2f;
        assertTrue("widest at " + widestX + ", middle " + middle, widestX > middle);
        assertEquals(torpedo.noseX(), front, 1e-3f);
    }

    @Test
    public void halfWidthNarrowsFromTheNoseToAPoint() {
        float r = 20f;
        assertEquals(r, CursorTrailTorpedoShape.halfWidth(r, 0f), 0f);
        assertEquals(0f, CursorTrailTorpedoShape.halfWidth(r, 1f), 0f);
        float previous = r;
        for (int i = 1; i <= 20; i++) {
            float w = CursorTrailTorpedoShape.halfWidth(r, i / 20f);
            assertTrue(w < previous);
            previous = w;
        }
    }

    /** A body a few cells long moving along the path, never a streak back to where it began. */
    @Test
    public void oneBodyTravelsThePath() {
        CursorTrailTorpedoShape torpedo = longRun(1_000L);
        float path = 40 * CELL_W;
        assertTrue(torpedo.layout(1_016L));
        assertTrue("nose " + torpedo.noseX(), torpedo.noseX() < CELL_W / 2f + 0.2f * path);
        float previous = torpedo.noseX();
        for (long t = 1_050L; t < 1_000L + torpedo.travelMs(); t += 50L) {
            assertTrue(torpedo.layout(t));
            assertTrue(torpedo.noseX() >= previous);
            previous = torpedo.noseX();
            float body = torpedo.noseX() - torpedo.tailX();
            assertTrue("body " + body, body <= CursorTrailTorpedoShape.BODY_LENGTH * CELL_H + 1e-3f);
            assertTrue(body < 0.25f * path);
        }
    }

    @Test
    public void itRunsIntoTheCursorAndThenOnlyItsWakeIsLeft() {
        CursorTrailTorpedoShape torpedo = longRun(1_000L);
        long travel = torpedo.travelMs();
        assertTrue(torpedo.layout(1_000L + travel - 10L));
        assertEquals(40.5f * CELL_W, torpedo.noseX(), 1e-2f);
        assertTrue(torpedo.layout(1_000L + travel + 100L));
        assertFalse(torpedo.bodyVisible());
        assertTrue("rings " + torpedo.ringCount(), torpedo.ringCount() > 0);
        long wake = Math.round(CursorTrailTorpedoShape.WAKE_LIFE_SCALE * DECAY_SLOW * 1000f);
        assertTrue(torpedo.alive(1_000L + travel + wake - 1L));
        assertFalse(torpedo.alive(1_000L + travel + wake));
        assertFalse(torpedo.layout(1_000L + travel + wake));
    }

    /** Slower than Railgun's shot, even for a short jump. */
    @Test
    public void slowerThanRailgun() {
        long beam = CursorTrailRailgunShape.beamLifeMs(0.1f);
        assertTrue(CursorTrailTorpedoShape.travelMs(40 * CELL_W, CELL_H, DECAY_SLOW) > beam);
        assertTrue(CursorTrailTorpedoShape.travelMs(2 * CELL_W, CELL_H, DECAY_SLOW) > beam);
    }

    @Test
    public void travelTimeFollowsTheDecayAndGrowsGentlyWithTheJump() {
        long shortJump = CursorTrailTorpedoShape.travelMs(2 * CELL_W, CELL_H, DECAY_SLOW);
        long longJump = CursorTrailTorpedoShape.travelMs(40 * CELL_W, CELL_H, DECAY_SLOW);
        assertTrue(longJump > shortJump);
        assertTrue(longJump < 2 * shortJump);
        assertTrue(CursorTrailTorpedoShape.travelMs(40 * CELL_W, CELL_H, 0.8f) > longJump);
        assertEquals(CursorTrailTorpedoShape.MIN_TRAVEL_MS,
            CursorTrailTorpedoShape.travelMs(40 * CELL_W, CELL_H, 0f));
        assertEquals(CursorTrailTorpedoShape.MAX_TRAVEL_MS,
            CursorTrailTorpedoShape.travelMs(40 * CELL_W, CELL_H, 10f));
    }

    @Test
    public void tailPassesAtInvertsTheTravelCurve() {
        for (int i = 0; i <= 50; i++) {
            float y = i / 50f;
            assertEquals(y, CursorTrailTorpedoShape.travel(
                CursorTrailTorpedoShape.tailPassesAt(y)), 1e-5f);
        }
    }

    /** The wake lies behind the tail, off to the sides, faint and growing as it fades. */
    @Test
    public void theWakeIsFaintAndBehindTheTail() {
        CursorTrailTorpedoShape torpedo = longRun(1_000L);
        assertTrue(torpedo.layout(1_300L));
        int n = torpedo.ringCount();
        assertTrue("rings " + n, n > 0);
        float[] rings = torpedo.rings();
        float r = torpedo.radius();
        for (int i = 0; i < n; i++) {
            int o = i * 4;
            assertTrue("ring " + i + " x " + rings[o], rings[o] <= torpedo.tailX() + 1e-2f);
            assertTrue(Math.abs(rings[o + 1] - PATH_Y) > 0.1f * r);
            assertTrue(rings[o + 2] >= CursorTrailTorpedoShape.WAKE_START_RADIUS * r - 1e-3f);
            assertTrue(rings[o + 2] <= CursorTrailTorpedoShape.WAKE_END_RADIUS * r + 1e-3f);
            assertTrue(rings[o + 3] > 0f && rings[o + 3] <= CursorTrailTorpedoShape.WAKE_ALPHA);
        }
    }

    @Test
    public void boundsHoldTheBodyAndTheWake() {
        CursorTrailTorpedoShape torpedo = new CursorTrailTorpedoShape();
        torpedo.start(0f, 0f, CELL_W, CELL_H, 30 * CELL_W, 12 * CELL_H, 31 * CELL_W, 13 * CELL_H,
            0L, DECAY_SLOW);
        for (long t = 0L; torpedo.layout(t); t += 8L) {
            if (torpedo.bodyVisible()) {
                float[] o = torpedo.outline();
                for (int i = 0; i < torpedo.pointCount(); i++) {
                    assertTrue(o[i * 2] >= torpedo.boundsLeft() && o[i * 2] <= torpedo.boundsRight());
                    assertTrue(o[i * 2 + 1] >= torpedo.boundsTop()
                        && o[i * 2 + 1] <= torpedo.boundsBottom());
                }
            }
            float[] rings = torpedo.rings();
            for (int i = 0; i < torpedo.ringCount() * 4; i += 4) {
                float reach = rings[i + 2];
                assertTrue(rings[i] - reach >= torpedo.boundsLeft());
                assertTrue(rings[i] + reach <= torpedo.boundsRight());
                assertTrue(rings[i + 1] - reach >= torpedo.boundsTop());
                assertTrue(rings[i + 1] + reach <= torpedo.boundsBottom());
            }
        }
    }

    /** A beam cursor moving down still launches a body you can see. */
    @Test
    public void aBeamCursorStillGetsABody() {
        CursorTrailTorpedoShape torpedo = new CursorTrailTorpedoShape();
        float beam = CELL_W / 4f;
        torpedo.start(0f, 0f, beam, CELL_H, 0f, 10 * CELL_H, beam, 11 * CELL_H, 0L, DECAY_SLOW);
        assertTrue(torpedo.layout(80L));
        assertTrue(torpedo.bodyVisible());
        assertTrue(torpedo.radius() >= 0.18f * CELL_H);
    }

    @Test
    public void aMoveOntoTheSameCentreLaunchesNothing() {
        CursorTrailTorpedoShape torpedo = new CursorTrailTorpedoShape();
        torpedo.start(0f, 0f, CELL_W, CELL_H, 0.2f, 0f, CELL_W + 0.2f, CELL_H, 0L, DECAY_SLOW);
        assertFalse(torpedo.active());
        assertFalse(torpedo.layout(16L));
    }
}
