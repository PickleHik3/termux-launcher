package com.termux.app.wall;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * How far a plank tips is a function of where the page stands: flat at rest, at its peak half a
 * width out, flat again a width out, where the page has left the screen. So a drag, a spring back
 * and a committed carry-out read one curve and nothing is handed over between them, and the page
 * leaving and the page arriving, a width apart, tip by the same amount. Which way each tips is its
 * lean toward the finger's weight. A held side border adds its press on top, as deep as the page
 * has sunk, and hands over to the travel.
 */
public class PlankTiltTest {

    private static final int WIDTH = 1080;
    private static final float EPS = 0.01f;

    @Test
    public void flatAtRest() {
        assertEquals(0f, PlankTilt.angleDeg(0f, WIDTH, 1f), EPS);
        assertEquals(0f, PlankTilt.angleDeg(0f, WIDTH, -1f), EPS);
    }

    @Test
    public void peaksHalfAWidthOutAndLiesFlatAWidthOut() {
        assertEquals(PlankTilt.MAX_TILT_DEG, PlankTilt.angleDeg(WIDTH / 2f, WIDTH, 1f), EPS);
        assertEquals(PlankTilt.MAX_TILT_DEG, PlankTilt.angleDeg(-WIDTH / 2f, WIDTH, 1f), EPS);
        assertEquals(0f, PlankTilt.angleDeg(WIDTH, WIDTH, 1f), EPS);
        assertEquals(0f, PlankTilt.angleDeg(-WIDTH, WIDTH, -1f), EPS);
    }

    @Test
    public void theLeanSaysWhichEdgeDips() {
        // rotationY positive takes the right edge away from the viewer: a lean to the right.
        assertTrue(PlankTilt.angleDeg(-300f, WIDTH, 1f) > 0f);
        assertTrue(PlankTilt.angleDeg(-300f, WIDTH, -1f) < 0f);
        assertEquals(-PlankTilt.angleDeg(300f, WIDTH, 1f), PlankTilt.angleDeg(300f, WIDTH, -1f),
            EPS);
        // Half a lean, half the tip.
        assertEquals(PlankTilt.angleDeg(-300f, WIDTH, 1f) / 2f,
            PlankTilt.angleDeg(-300f, WIDTH, 0.5f), EPS);
    }

    @Test
    public void theLeavingAndTheArrivingPageTipAlike() {
        // The two pages on screen stand a width apart; whatever the wall's offset, each tips by
        // the same amount, so they read as one motion.
        for (int offset = -WIDTH + 27; offset < 0; offset += 27) {
            float leaving = Math.abs(PlankTilt.angleDeg(offset, WIDTH, 1f));
            float arriving = Math.abs(PlankTilt.angleDeg(offset + WIDTH, WIDTH, -1f));
            assertEquals("at " + offset, leaving, arriving, EPS);
        }
    }

    @Test
    public void risesWithTheDragOverTheFirstHalfAndFallsBackOverTheSecond() {
        float previous = 0f;
        for (int px = 54; px <= WIDTH / 2; px += 54) {
            float angle = PlankTilt.angleDeg(px, WIDTH, 1f);
            assertTrue("further out tips further: " + px, angle > previous);
            previous = angle;
        }
        for (int px = WIDTH / 2 + 54; px <= WIDTH; px += 54) {
            float angle = PlankTilt.angleDeg(px, WIDTH, 1f);
            assertTrue("carried out, it lies down again: " + px, angle < previous);
            previous = angle;
        }
    }

    @Test
    public void neverTipsPastThePeakAndClampsBeyondAWidth() {
        for (int px = -2 * WIDTH; px <= 2 * WIDTH; px += 27) {
            assertTrue(Math.abs(PlankTilt.angleDeg(px, WIDTH, 1f)) <= PlankTilt.MAX_TILT_DEG + EPS);
        }
        assertEquals(0f, PlankTilt.angleDeg(3 * WIDTH, WIDTH, 1f), EPS);
        assertTrue(Math.abs(PlankTilt.angleDeg(300f, WIDTH, 7f)) <= PlankTilt.MAX_TILT_DEG + EPS);
    }

    @Test
    public void aWallWithNoWidthIsFlat() {
        assertEquals(0f, PlankTilt.angleDeg(300f, 0, 1f), EPS);
        assertEquals(0f, PlankTilt.angleDeg(Float.NaN, WIDTH, 1f), EPS);
        assertEquals(0f, PlankTilt.angleDeg(300f, WIDTH, Float.NaN), EPS);
        assertEquals(0f, PlankTilt.lean(300f, Float.NaN, WIDTH), EPS);
        assertEquals(0f, PlankTilt.lean(300f, 0f, 0), EPS);
    }

    @Test
    public void thePeakIsDeeperThanTheOldTwelveDegrees() {
        assertTrue(PlankTilt.MAX_TILT_DEG >= 18f && PlankTilt.MAX_TILT_DEG <= 20f);
        assertTrue(PlankTilt.HOLD_TILT_DEG > 0f && PlankTilt.HOLD_TILT_DEG < PlankTilt.MAX_TILT_DEG);
    }

    // ---- The lean: toward the finger's weight -----------------------------------------------

    @Test
    public void bothPagesLeanTowardAFingerOnTheSeam() {
        // Held by the right border and pulled 300 px left: the finger is on the seam between the
        // page leaving (at -300) and the page arriving (at 780).
        float weight = -300f + WIDTH / 2f;
        assertEquals("the page leaving dips its right edge", 1f,
            PlankTilt.lean(-300f, weight, WIDTH), EPS);
        assertEquals("the page arriving dips its left edge", -1f,
            PlankTilt.lean(-300f + WIDTH, weight, WIDTH), EPS);
    }

    @Test
    public void bothPagesLeanTowardAFingerOnTheFarSide() {
        // Held by the left border and pulled left: the finger is on the page's far side, and both
        // pages dip the edge nearer it.
        float weight = -300f - WIDTH / 2f;
        assertEquals(-1f, PlankTilt.lean(-300f, weight, WIDTH), EPS);
        assertEquals(-1f, PlankTilt.lean(-300f + WIDTH, weight, WIDTH), EPS);
    }

    @Test
    public void aFingerOnTheCentreLineStillLeansThePageTowardItsPartner() {
        // Held on the middle of the top border and pulled left: the page leaving still tips,
        // toward the page arriving on its right, and the page arriving leans toward the finger.
        float weight = -300f;
        assertEquals(PlankTilt.CENTRE_LEAN, PlankTilt.lean(-300f, weight, WIDTH), EPS);
        assertEquals(-1f, PlankTilt.lean(-300f + WIDTH, weight, WIDTH), EPS);
        // Pulled right instead, it leans toward the page arriving on its left.
        assertEquals(-PlankTilt.CENTRE_LEAN, PlankTilt.lean(300f, 300f, WIDTH), EPS);
        assertTrue(Math.abs(PlankTilt.angleDeg(-300f, WIDTH, PlankTilt.CENTRE_LEAN)) > 5f);
    }

    @Test
    public void theLeanIsContinuousInTheFingersPlace() {
        float previous = PlankTilt.lean(-300f, -300f - WIDTH / 2f, WIDTH);
        for (int dx = -WIDTH / 2 + 5; dx <= WIDTH / 2; dx += 5) {
            float lean = PlankTilt.lean(-300f, -300f + dx, WIDTH);
            assertTrue("in small steps at " + dx, Math.abs(lean - previous) < 0.05f);
            assertTrue(lean >= -1f - EPS && lean <= 1f + EPS);
            previous = lean;
        }
    }

    // ---- The press: a held side border pushed in as the page sinks ---------------------------

    @Test
    public void aHeldSideBorderPushesItsSideInAtRestAsThePageSinks() {
        // The right border held: rotationY positive takes the right edge away.
        assertEquals(PlankTilt.HOLD_TILT_DEG, PlankTilt.angleDeg(0f, WIDTH, 1f, 1, 1f), EPS);
        assertEquals(-PlankTilt.HOLD_TILT_DEG, PlankTilt.angleDeg(0f, WIDTH, -1f, -1, 1f), EPS);
        // Half sunk, half pushed; not sunk, flat.
        assertEquals(PlankTilt.HOLD_TILT_DEG / 2f, PlankTilt.angleDeg(0f, WIDTH, 1f, 1, 0.5f),
            EPS);
        assertEquals(0f, PlankTilt.angleDeg(0f, WIDTH, 1f, 1, 0f), EPS);
        // The top and bottom borders have no side to push.
        assertEquals(0f, PlankTilt.angleDeg(0f, WIDTH, 1f, 0, 1f), EPS);
    }

    @Test
    public void theSpringsOvershootNeverPushesTheWrongSideOrPastFull() {
        assertEquals("rising a hair past flush is not a push the other way", 0f,
            PlankTilt.angleDeg(0f, WIDTH, 1f, 1, -0.1f), EPS);
        assertEquals(PlankTilt.HOLD_TILT_DEG, PlankTilt.angleDeg(0f, WIDTH, 1f, 1, 1.05f), EPS);
        assertEquals(0f, PlankTilt.angleDeg(0f, WIDTH, 1f, 1, Float.NaN), EPS);
    }

    @Test
    public void thePressHandsOverToTheTravelsTipOverTheFirstQuarter() {
        float quarter = WIDTH * PlankTilt.HOLD_FADE_FRACTION;
        // Past the hand-over the press is gone: the plank is the travel's alone, held or not.
        for (float px : new float[] {quarter, WIDTH / 2f, WIDTH * 0.8f}) {
            assertEquals(PlankTilt.angleDeg(-px, WIDTH, 1f),
                PlankTilt.angleDeg(-px, WIDTH, 1f, 1, 1f), EPS);
        }
        // Held on the right and pulled left, the press and the tip lean the same way: the page
        // goes on dipping its right edge as it goes, continuously, with no jump.
        float previous = PlankTilt.angleDeg(0f, WIDTH, 1f, 1, 1f);
        for (int px = -5; px >= -Math.round(quarter); px -= 5) {
            float angle = PlankTilt.angleDeg(px, WIDTH, 1f, 1, 1f);
            assertTrue("keeps dipping the held side: " + px, angle > 0f);
            assertTrue("in small steps: " + px, Math.abs(previous - angle) < 1f);
            previous = angle;
        }
    }

    @Test
    public void aHeldSideAndTheTravelTogetherNeverTipPastThePeak() {
        for (int side = -1; side <= 1; side++) {
            for (int px = -2 * WIDTH; px <= 2 * WIDTH; px += 9) {
                assertTrue(Math.abs(PlankTilt.angleDeg(px, WIDTH, side, side, 1f))
                    <= PlankTilt.MAX_TILT_DEG + EPS);
            }
            assertEquals("a page carried a width out lies flat, held or not", 0f,
                PlankTilt.angleDeg(WIDTH, WIDTH, side, side, 1f), EPS);
        }
    }

    @Test
    public void theEdgeResistanceKeepsAResistingPlankGentle() {
        // A drag into a line's outer edge moves the wall by at most EDGE_RESISTANCE of a width,
        // so a page with nowhere to go tips well short of its peak and springs back.
        float resisted = PlankTilt.angleDeg(WIDTH * PaneWallPolicy.EDGE_RESISTANCE, WIDTH, 1f);
        assertTrue(resisted > 0f);
        assertTrue(resisted < PlankTilt.MAX_TILT_DEG * 0.95f);
    }
}
