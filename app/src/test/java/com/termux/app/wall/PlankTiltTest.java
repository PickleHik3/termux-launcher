package com.termux.app.wall;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * The plank's angle is a function of where the page stands: flat at rest, at its peak half a
 * width out, flat again a width out, where the page has left the screen. So a drag, a spring
 * back and a committed carry-out read one curve and nothing is handed over between them. A held
 * side border adds its press on top, as deep as the page has sunk, and hands over to the travel.
 */
public class PlankTiltTest {

    private static final int WIDTH = 1080;
    private static final float EPS = 0.01f;

    @Test
    public void flatAtRest() {
        assertEquals(0f, PlankTilt.angleDeg(0f, WIDTH), EPS);
    }

    @Test
    public void peaksHalfAWidthOutAndLiesFlatAWidthOut() {
        assertEquals(PlankTilt.MAX_TILT_DEG, Math.abs(PlankTilt.angleDeg(WIDTH / 2f, WIDTH)), EPS);
        assertEquals(0f, PlankTilt.angleDeg(WIDTH, WIDTH), EPS);
        assertEquals(0f, PlankTilt.angleDeg(-WIDTH, WIDTH), EPS);
    }

    @Test
    public void theLeadingSideDips() {
        // A page moved left is on its way to the place on the right: its left edge goes in,
        // which on rotationY is a negative angle (positive takes the right edge away).
        assertTrue(PlankTilt.angleDeg(-300f, WIDTH) < 0f);
        assertTrue(PlankTilt.angleDeg(300f, WIDTH) > 0f);
        assertEquals(-PlankTilt.angleDeg(300f, WIDTH), PlankTilt.angleDeg(-300f, WIDTH), EPS);
    }

    @Test
    public void risesWithTheDragOverTheFirstHalf() {
        float previous = 0f;
        for (int px = 54; px <= WIDTH / 2; px += 54) {
            float angle = PlankTilt.angleDeg(px, WIDTH);
            assertTrue("further out tips further: " + px, angle > previous);
            previous = angle;
        }
    }

    @Test
    public void fallsBackToFlatOverTheSecondHalf() {
        float previous = PlankTilt.MAX_TILT_DEG;
        for (int px = WIDTH / 2 + 54; px <= WIDTH; px += 54) {
            float angle = PlankTilt.angleDeg(px, WIDTH);
            assertTrue("carried out, it lies down again: " + px, angle < previous);
            previous = angle;
        }
    }

    @Test
    public void neverTipsPastThePeakAndClampsBeyondAWidth() {
        for (int px = -2 * WIDTH; px <= 2 * WIDTH; px += 27) {
            assertTrue(Math.abs(PlankTilt.angleDeg(px, WIDTH)) <= PlankTilt.MAX_TILT_DEG + EPS);
        }
        assertEquals(0f, PlankTilt.angleDeg(3 * WIDTH, WIDTH), EPS);
    }

    @Test
    public void aWallWithNoWidthIsFlat() {
        assertEquals(0f, PlankTilt.angleDeg(300f, 0), EPS);
        assertEquals(0f, PlankTilt.angleDeg(Float.NaN, WIDTH), EPS);
    }

    @Test
    public void thePeakIsDeeperThanTheOldTwelveDegrees() {
        assertTrue(PlankTilt.MAX_TILT_DEG >= 18f && PlankTilt.MAX_TILT_DEG <= 20f);
        assertTrue(PlankTilt.HOLD_TILT_DEG > 0f && PlankTilt.HOLD_TILT_DEG < PlankTilt.MAX_TILT_DEG);
    }

    // ---- The press: a held side border pushed in as the page sinks ---------------------------

    @Test
    public void aHeldSideBorderPushesItsSideInAtRestAsThePageSinks() {
        // The right border held: rotationY positive takes the right edge away.
        assertEquals(PlankTilt.HOLD_TILT_DEG, PlankTilt.angleDeg(0f, WIDTH, 1, 1f), EPS);
        assertEquals(-PlankTilt.HOLD_TILT_DEG, PlankTilt.angleDeg(0f, WIDTH, -1, 1f), EPS);
        // Half sunk, half pushed; not sunk, flat.
        assertEquals(PlankTilt.HOLD_TILT_DEG / 2f, PlankTilt.angleDeg(0f, WIDTH, 1, 0.5f), EPS);
        assertEquals(0f, PlankTilt.angleDeg(0f, WIDTH, 1, 0f), EPS);
        // The top and bottom borders have no side to push.
        assertEquals(0f, PlankTilt.angleDeg(0f, WIDTH, 0, 1f), EPS);
    }

    @Test
    public void theSpringsOvershootNeverPushesTheWrongSideOrPastFull() {
        assertEquals("rising a hair past flush is not a push the other way", 0f,
            PlankTilt.angleDeg(0f, WIDTH, 1, -0.1f), EPS);
        assertEquals(PlankTilt.HOLD_TILT_DEG, PlankTilt.angleDeg(0f, WIDTH, 1, 1.05f), EPS);
        assertEquals(0f, PlankTilt.angleDeg(0f, WIDTH, 1, Float.NaN), EPS);
    }

    @Test
    public void thePressHandsOverToTheTravelsTipOverTheFirstQuarter() {
        float quarter = WIDTH * PlankTilt.HOLD_FADE_FRACTION;
        // Past the hand-over the press is gone: the plank is the travel's alone, held or not.
        for (float px : new float[] {quarter, WIDTH / 2f, WIDTH * 0.8f}) {
            assertEquals(PlankTilt.angleDeg(px, WIDTH), PlankTilt.angleDeg(px, WIDTH, 1, 1f), EPS);
            assertEquals(PlankTilt.angleDeg(-px, WIDTH),
                PlankTilt.angleDeg(-px, WIDTH, 1, 1f), EPS);
        }
        // Held on the right and pulled left, the page turns from its pressed side to its leading
        // side as it goes, through flat — continuously, with no jump.
        float previous = PlankTilt.angleDeg(0f, WIDTH, 1, 1f);
        for (int px = -5; px >= -Math.round(quarter); px -= 5) {
            float angle = PlankTilt.angleDeg(px, WIDTH, 1, 1f);
            assertTrue("turns toward the leading side: " + px, angle < previous);
            assertTrue("in small steps: " + px, previous - angle < 1f);
            previous = angle;
        }
        assertTrue(previous < 0f);
    }

    @Test
    public void aHeldSideAndTheTravelTogetherNeverTipPastThePeak() {
        for (int side = -1; side <= 1; side++) {
            for (int px = -2 * WIDTH; px <= 2 * WIDTH; px += 9) {
                assertTrue(Math.abs(PlankTilt.angleDeg(px, WIDTH, side, 1f))
                    <= PlankTilt.MAX_TILT_DEG + EPS);
            }
            assertEquals("a page carried a width out lies flat, held or not", 0f,
                PlankTilt.angleDeg(WIDTH, WIDTH, side, 1f), EPS);
        }
    }

    @Test
    public void theEdgeResistanceKeepsAResistingPlankGentle() {
        // A drag into a line's outer edge moves the wall by at most EDGE_RESISTANCE of a width,
        // so a page with nowhere to go tips well short of its peak and springs back.
        float resisted = PlankTilt.angleDeg(WIDTH * PaneWallPolicy.EDGE_RESISTANCE, WIDTH);
        assertTrue(resisted > 0f);
        assertTrue(resisted < PlankTilt.MAX_TILT_DEG * 0.95f);
    }
}
