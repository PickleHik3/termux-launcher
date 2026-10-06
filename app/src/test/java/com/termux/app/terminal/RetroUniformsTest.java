package com.termux.app.terminal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class RetroUniformsTest {

    private static final float DENSITY = 2.75f;
    private static final float PERIOD = RetroUniforms.periodPx(DENSITY);

    /** Where in the period the shader puts local coordinate {@code local}: {@code (p + uOrigin) mod P}. */
    private static float shaderRow(float local, float phase) {
        return RetroUniforms.phase(local + phase, PERIOD);
    }

    /** Two places in the period are the same row when they are close, across the wrap too. */
    private static void assertSameRow(String message, float expected, float actual) {
        float d = Math.abs(expected - actual);
        assertTrue(message + ": " + expected + " vs " + actual, Math.min(d, PERIOD - d) < 1e-2f);
    }

    @Test public void phaseIsAlwaysInsideOnePeriod() {
        for (float origin : new float[] {0f, 1f, 8.25f, 8.2499f, 1999f, -1f, -8.25f, -700.3f}) {
            float phase = RetroUniforms.phase(origin, PERIOD);
            assertTrue(origin + " -> " + phase, phase >= 0f && phase < PERIOD);
        }
        assertEquals(0f, RetroUniforms.phase(5f, 0f), 0f);
        assertEquals(0f, RetroUniforms.phase(Float.NaN, PERIOD), 0f);
    }

    @Test public void everySurfaceDrawsTheScreensRowsWhereverItStands() {
        // The status bar at the top, a pane under it, the dock and a keyboard lifted mid-travel:
        // the same window row lands on the same place in the period on each of them.
        float[] tops = {0f, 263f, 1789f, 1990.6f, -12f};
        for (float windowRow = 0f; windowRow < 2400f; windowRow += 37.3f) {
            float expected = RetroUniforms.phase(windowRow, PERIOD);
            for (float top : tops) {
                RetroUniforms u = new RetroUniforms().set(1080f, 300f, 0f, top, 1f, 1f, DENSITY,
                    0f, 0f, 1080f, 300f, 0f, 0f, 0f);
                assertSameRow("row " + windowRow + " on a view at " + top,
                    expected, shaderRow(windowRow - top, u.phaseY));
            }
        }
    }

    @Test public void aScaledFrameKeepsItsSurfacesInStepInItsOwnUnits() {
        // The Appearance editor scales every surface by one factor: lines are drawn in each view's
        // own units, so two views scaled together put a row at the same window position.
        float scale = 0.76f;
        float[] windowTops = {40f, 40f + 263f * scale, 40f + 1789f * scale};
        for (float windowRow = 50f; windowRow < 1800f; windowRow += 11.1f) {
            Float first = null;
            for (float top : windowTops) {
                if (windowRow < top) continue;
                RetroUniforms u = new RetroUniforms().set(1080f, 300f, 0f, top, scale, scale, DENSITY,
                    0f, 0f, 1080f, 300f, 0f, 0f, 0f);
                float local = (windowRow - top) / scale;
                float row = shaderRow(local, u.phaseY);
                if (first == null) first = row;
                else assertSameRow("row " + windowRow + " on a view at " + top, first, row);
            }
        }
    }

    @Test public void theCardRadiusNeverPassesHalfItsShorterSide() {
        RetroUniforms u = new RetroUniforms().set(200f, 40f, 0f, 0f, 1f, 1f, DENSITY,
            0f, 0f, 200f, 40f, 64f, 0f, 0f);
        assertEquals(20f, u.radius, 0f);
        assertEquals(0f, RetroUniforms.cappedRadius(-3f, 100f, 100f), 0f);
        assertEquals(0f, RetroUniforms.cappedRadius(8f, 0f, 100f), 0f);
        assertEquals(8f, RetroUniforms.cappedRadius(8f, 100f, 100f), 0f);
    }

    @Test public void aStillViewComparesEqualAndAMovedOneDoesNot() {
        RetroUniforms a = new RetroUniforms().set(1080f, 300f, 0f, 1789f, 1f, 1f, DENSITY,
            0f, 0f, 1080f, 300f, 0f, 0f, 0f);
        RetroUniforms b = new RetroUniforms();
        b.copyFrom(a);
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
        b.set(1080f, 300f, 0f, 1790f, 1f, 1f, DENSITY, 0f, 0f, 1080f, 300f, 0f, 0f, 0f);
        assertNotEquals(a, b);
        // A move by whole periods keeps every row where it was: nothing to redraw.
        b.set(1080f, 300f, 0f, 1789f + 4f * PERIOD, 1f, 1f, DENSITY, 0f, 0f, 1080f, 300f, 0f, 0f, 0f);
        assertSameRow("whole periods", a.phaseY, b.phaseY);
    }
}
