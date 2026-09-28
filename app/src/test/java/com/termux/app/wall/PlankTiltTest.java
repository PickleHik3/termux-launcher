package com.termux.app.wall;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * The plank's angle is a function of where the page stands: flat at rest, at its peak half a
 * width out, flat again a width out, where the page has left the screen. So a drag, a spring
 * back and a committed carry-out read one curve and nothing is handed over between them.
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
    public void theEdgeResistanceKeepsAResistingPlankGentle() {
        // A drag into a line's outer edge moves the wall by at most EDGE_RESISTANCE of a width,
        // so a page with nowhere to go tips well short of its peak and springs back.
        float resisted = PlankTilt.angleDeg(WIDTH * PaneWallPolicy.EDGE_RESISTANCE, WIDTH);
        assertTrue(resisted > 0f);
        assertTrue(resisted < PlankTilt.MAX_TILT_DEG * 0.95f);
    }
}
