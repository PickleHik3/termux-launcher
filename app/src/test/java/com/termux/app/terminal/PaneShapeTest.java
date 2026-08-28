package com.termux.app.terminal;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * The cap only ever engages on a pane too small to wear the window's radius, which is what makes it
 * safe to apply everywhere a pane's shape is drawn.
 */
public class PaneShapeTest {

    private static final float EPS = 0.001f;

    @Test
    public void fullSizePane_keepsTheRequestedRadius() {
        // 20dp radius at 2.625 density on a half-screen pane: nowhere near the cap.
        assertEquals(52.5f, PaneShape.radiusForBounds(52.5f, 1080, 1100), EPS);
    }

    @Test
    public void paneShorterThanThreeRadii_getsTheCappedRadius() {
        // Five splits deep: a pane 90px tall would have been given a 52.5px arc at every corner,
        // leaving no straight edge between them.
        float radius = PaneShape.radiusForBounds(52.5f, 1080, 90);
        assertEquals(30f, radius, EPS);
        assertTrue("every edge must keep at least a third of its length straight",
            2f * radius <= 90f * (2f / 3f) + EPS);
    }

    @Test
    public void narrowPane_capsOnTheShorterSide() {
        assertEquals(20f, PaneShape.radiusForBounds(52.5f, 60, 1400), EPS);
    }

    @Test
    public void unmeasuredOrSquarePane_roundsNothing() {
        assertEquals(0f, PaneShape.radiusForBounds(52.5f, 0, 0), EPS);
        assertEquals(0f, PaneShape.radiusForBounds(52.5f, 1080, 0), EPS);
        assertEquals(0f, PaneShape.radiusForBounds(0f, 1080, 1400), EPS);
    }

    @Test
    public void capIsMonotonic_soAResizeNeverJumpsTheShape() {
        float previous = 0f;
        for (int height = 1; height <= 400; height += 7) {
            float radius = PaneShape.radiusForBounds(52.5f, 1080, height);
            assertTrue("radius must not shrink as the pane grows", radius >= previous - EPS);
            previous = radius;
        }
    }
}
