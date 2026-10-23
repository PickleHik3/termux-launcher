package com.termux.app.wall;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** The one page rule (#152): commit, return, fling, and the angle-damped slop. */
public class PagePolicyTest {

    private static final float PAGE = 1000f;
    private static final float DENSITY = 3f;

    private static float dp(float dpPerSec, float density) {
        return dpPerSec * density;
    }

    @Test
    public void aDragCommitsAtFourTenthsAndNotShortOfThem() {
        assertEquals(1, PagePolicy.turn(400f, 0f, PAGE, DENSITY));
        assertEquals(-1, PagePolicy.turn(-400f, 0f, PAGE, DENSITY));
        assertEquals(0, PagePolicy.turn(390f, 0f, PAGE, DENSITY));
        assertEquals(0, PagePolicy.turn(-390f, 0f, PAGE, DENSITY));
    }

    @Test
    public void aReversalPastAThirdReturns() {
        // Past the commit distance, the finger turning back at the least fling speed goes home.
        assertEquals(0, PagePolicy.turn(600f, -dp(250f, DENSITY), PAGE, DENSITY));
        assertEquals(0, PagePolicy.turn(330f, -dp(250f, DENSITY), PAGE, DENSITY));
        // And a hard throw back goes home too, not on to the page beyond.
        assertEquals(0, PagePolicy.turn(-330f, dp(3000f, DENSITY), PAGE, DENSITY));
        // Slower than that, the lift's wobble is no change of mind: position decides.
        assertEquals(1, PagePolicy.turn(600f, -dp(249f, DENSITY), PAGE, DENSITY));
        // Short of a third, a throw back decides on its own.
        assertEquals(-1, PagePolicy.turn(320f, -dp(500f, DENSITY), PAGE, DENSITY));
        assertEquals(0, PagePolicy.turn(320f, -dp(499f, DENSITY), PAGE, DENSITY));
    }

    @Test
    public void aFlingCommitsWhateverTheDistance() {
        assertEquals(1, PagePolicy.turn(20f, dp(500f, DENSITY), PAGE, DENSITY));
        assertEquals(-1, PagePolicy.turn(-20f, -dp(500f, DENSITY), PAGE, DENSITY));
        assertEquals(0, PagePolicy.turn(20f, dp(499f, DENSITY), PAGE, DENSITY));
    }

    @Test
    public void pastAThirdAnEasyFlingCommits() {
        assertEquals(1, PagePolicy.turn(330f, dp(400f, DENSITY), PAGE, DENSITY));
        assertEquals(0, PagePolicy.turn(330f, dp(399f, DENSITY), PAGE, DENSITY));
        assertEquals(0, PagePolicy.turn(320f, dp(400f, DENSITY), PAGE, DENSITY));
    }

    @Test
    public void theFlingIsTheSameInDpAtEveryDensity() {
        for (float density : new float[] {2.6f, 3.5f}) {
            assertEquals(1, PagePolicy.turn(20f, dp(500f, density), PAGE, density));
            assertEquals(0, PagePolicy.turn(20f, dp(499f, density), PAGE, density));
            assertEquals(1, PagePolicy.turn(340f, dp(400f, density), PAGE, density));
            assertEquals(0, PagePolicy.turn(340f, dp(399f, density), PAGE, density));
            assertEquals(0, PagePolicy.turn(600f, -dp(250f, density), PAGE, density));
            assertEquals(1, PagePolicy.turn(600f, -dp(249f, density), PAGE, density));
        }
    }

    @Test
    public void theWallsLeastDistanceIsAParameter() {
        float least = 25f * DENSITY;
        float fast = dp(2000f, DENSITY);
        assertEquals(0, PagePolicy.turn(-least + 1f, -fast, PAGE, DENSITY, least));
        assertEquals(-1, PagePolicy.turn(-least, -fast, PAGE, DENSITY, least));
        // Read on the finger's travel where the pane claimed the drag late.
        assertEquals(-1, PagePolicy.turn(-least + 30f, -least, -fast, PAGE, DENSITY, least));
        assertEquals(0, PagePolicy.turn(-least + 30f, -least + 30f, -fast, PAGE, DENSITY,
            least));
    }

    @Test
    public void theDrawersProgressIsTheTravelAndItsFloorTheLeastDistance() {
        // The drawer reads its progress as the travel over a page of 1.
        assertEquals(1, PagePolicy.turn(0.12f, dp(500f, DENSITY), 1f, DENSITY, 0.12f));
        assertEquals(0, PagePolicy.turn(0.11f, dp(5000f, DENSITY), 1f, DENSITY, 0.12f));
        assertEquals(1, PagePolicy.turn(0.4f, 0f, 1f, DENSITY, 0.12f));
        assertEquals(0, PagePolicy.turn(0.39f, 0f, 1f, DENSITY, 0.12f));
    }

    @Test
    public void degenerateInputsStayPut() {
        assertEquals(0, PagePolicy.turn(900f, 0f, 0f, DENSITY));
        assertEquals(0, PagePolicy.turn(Float.NaN, 0f, PAGE, DENSITY));
        assertEquals(1, PagePolicy.turn(500f, Float.NaN, PAGE, DENSITY));
        assertEquals(0, PagePolicy.turn(0f, 0f, PAGE, DENSITY));
    }

    @Test
    public void theSlopIsDampedFourFoldPastThirtyDegrees() {
        float slop = 24f;
        float along = 50f;
        float at29 = along * (float) Math.tan(Math.toRadians(29));
        float at31 = along * (float) Math.tan(Math.toRadians(31));
        assertEquals(slop, PagePolicy.slopPx(along, at29, slop), 0f);
        assertEquals(4f * slop, PagePolicy.slopPx(along, at31, slop), 0f);
        // At 29 degrees the plain slop lets it go; at 31 it waits for four of them.
        assertTrue(PagePolicy.pastSlop(along, at29, slop));
        assertFalse(PagePolicy.pastSlop(along, at31, slop));
        assertTrue(PagePolicy.pastSlop(4f * slop + 1f,
            (4f * slop + 1f) * (float) Math.tan(Math.toRadians(31)), slop));
        assertFalse(PagePolicy.pastSlop(slop, 0f, slop));
        assertFalse(PagePolicy.pastSlop(Float.NaN, 0f, slop));
    }
}
