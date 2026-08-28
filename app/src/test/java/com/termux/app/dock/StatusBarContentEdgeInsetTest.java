package com.termux.app.dock;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * The inset that keeps the status bar's bottom row — sessions chip on the left, status widgets on
 * the right — clear of the rounded corners it sits in.
 *
 * <p>Docked's numbers are pinned exactly as they were before Floating grew the same behaviour, so a
 * drift in any of them is a visible regression in a style that was already correct.</p>
 */
public class StatusBarContentEdgeInsetTest {

    /** Density 1 keeps dp and px the same number, so the arithmetic is readable. */
    private static final float DENSITY = 1f;
    /** The auto radius a Floating status surface already has at rest. */
    private static final float FLOATING_BASELINE_RADIUS = 26f;

    private static int docked(float radiusPx) {
        return DockLayoutPolicy.statusBarContentEdgeInsetPx(false, radiusPx, 0f, DENSITY);
    }

    private static int floating(float radiusPx) {
        return DockLayoutPolicy.statusBarContentEdgeInsetPx(
            true, radiusPx, FLOATING_BASELINE_RADIUS, DENSITY);
    }

    // ------------------------------------------------------------------ docked, unchanged

    @Test
    public void docked_isSquareAtRestAndSpendsNothingOnCorners() {
        assertEquals(3, docked(0f));
    }

    @Test
    public void docked_movesInByHalfTheRadiusTheUserDialsIn() {
        assertEquals(3 + 5, docked(10f));
        assertEquals(3 + 10, docked(20f));
        assertEquals(3 + 20, docked(40f));
    }

    // ------------------------------------------------------------------ floating, the new half

    /**
     * The whole point of the baseline: a Floating surface is already rounded at rest, and its 8dp
     * was measured against exactly that radius. Growing from zero instead would shove the widgets
     * inward on every stock install for no change in what overlaps them.
     */
    @Test
    public void floating_atItsRestingRadius_keepsTheInsetItAlwaysHad() {
        assertEquals(8, floating(FLOATING_BASELINE_RADIUS));
    }

    @Test
    public void floating_belowItsRestingRadius_doesNotShrinkTheInset() {
        // A collapsed capsule clamps its radius to half the surface height, well under the auto
        // radius. The baseline still covers it, so the row must not drift outward into the curve.
        assertEquals(8, floating(15f));
        assertEquals(8, floating(0f));
    }

    /** The bug this fixes: past the resting radius the arc reaches in further than 8dp answers. */
    @Test
    public void floating_pastItsRestingRadius_movesInByHalfTheExcess() {
        assertEquals(8 + 7, floating(40f));
        assertEquals(8 + 12, floating(50f));
    }

    /**
     * The invariant that matters in the editor: dragging the radius up must never move the content
     * back out. It is not comparable with Docked's numbers — Docked grows by a deliberately
     * conservative half-the-radius from zero, where Floating's baseline was measured against a
     * real surface — so the two curves are only required to be monotonic, not equal.
     */
    @Test
    public void neitherStyleEverMovesContentOutwardAsTheRadiusGrows() {
        int previousFloating = floating(0f);
        int previousDocked = docked(0f);
        for (float radius = 0f; radius <= 60f; radius += 1f) {
            assertTrue("floating at " + radius, floating(radius) >= previousFloating);
            assertTrue("docked at " + radius, docked(radius) >= previousDocked);
            previousFloating = floating(radius);
            previousDocked = docked(radius);
        }
    }

    // ------------------------------------------------------------------ density

    @Test
    public void theBaselineScalesWithDensityAndTheRadiusIsAlreadyInPixels() {
        float density = 2.75f;
        // 8dp at 2.75 is 22px, and the radius arrives in pixels so it is not scaled twice.
        assertEquals(22, DockLayoutPolicy.statusBarContentEdgeInsetPx(
            true, 26f * density, 26f * density, density));
        assertEquals(Math.round(22 + (40f - 26f) * density * 0.5f),
            DockLayoutPolicy.statusBarContentEdgeInsetPx(
                true, 40f * density, 26f * density, density));
    }

    @Test
    public void aNonsensicalDensityStillYieldsAUsableInset() {
        assertEquals(8, DockLayoutPolicy.statusBarContentEdgeInsetPx(true, 0f, 0f, 0f));
        assertEquals(3, DockLayoutPolicy.statusBarContentEdgeInsetPx(false, 0f, 0f, -1f));
    }
}
