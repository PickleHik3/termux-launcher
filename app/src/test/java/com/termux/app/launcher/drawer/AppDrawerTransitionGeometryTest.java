package com.termux.app.launcher.drawer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.termux.app.launcher.drawer.AppDrawerTransitionGeometry.Frame;

import org.junit.Test;

/**
 * The drag→pixels mapping.
 *
 * <p>The load-bearing case is the last one: at {@code p == 0} the plane rectangle must be the dock
 * rectangle, exactly, or the 100ms cross-fade between the dock glass and the plane glass stops
 * being a swap of two identical rectangles and becomes a visible seam at the start of every open.
 */
public class AppDrawerTransitionGeometryTest {

    private static final float EPS = 1e-4f;

    /** dp(120) and dp(260) at 3x, resolved by the caller since this class never sees a Context. */
    private static final float MIN_TRAVEL = 360f;
    private static final float MAX_TRAVEL = 780f;

    private static final float SLOP = 24f;

    @Test
    public void travelClampsAtBothEnds() {
        // A short root would give a 30% travel under the floor…
        assertEquals(MIN_TRAVEL,
            AppDrawerTransitionGeometry.resolveOpenTravelPx(600f, MIN_TRAVEL, MAX_TRAVEL), EPS);
        // …and a tall one over the ceiling.
        assertEquals(MAX_TRAVEL,
            AppDrawerTransitionGeometry.resolveOpenTravelPx(4000f, MIN_TRAVEL, MAX_TRAVEL), EPS);
        // In between it is the plain fraction.
        assertEquals(720f,
            AppDrawerTransitionGeometry.resolveOpenTravelPx(2400f, MIN_TRAVEL, MAX_TRAVEL), EPS);
    }

    @Test
    public void progressClampsToUnitRange() {
        float travel = 600f;
        // Inside the claim's slop dead zone: still 0, not a jump.
        assertEquals(0f,
            AppDrawerTransitionGeometry.progressForDrag(1020f, 1000f, SLOP, travel), EPS);
        assertEquals(0f,
            AppDrawerTransitionGeometry.progressForDrag(500f, 1000f, SLOP, travel), EPS);
        // One dead zone plus half the travel.
        float half = 1000f + (SLOP * AppDrawerTransitionGeometry.SLOP_FACTOR) + (travel / 2f);
        assertEquals(0.5f,
            AppDrawerTransitionGeometry.progressForDrag(half, 1000f, SLOP, travel), EPS);
        assertEquals(1f,
            AppDrawerTransitionGeometry.progressForDrag(9000f, 1000f, SLOP, travel), EPS);
        // A degenerate travel must not produce a NaN that would poison the spring.
        assertEquals(0f,
            AppDrawerTransitionGeometry.progressForDrag(9000f, 1000f, SLOP, 0f), EPS);
    }

    @Test
    public void rampHitsItsEndpointsAndHoldsOutside() {
        assertEquals(0f, AppDrawerTransitionGeometry.ramp(0.05f, 0.2f, 0.6f), EPS);
        assertEquals(0f, AppDrawerTransitionGeometry.ramp(0.2f, 0.2f, 0.6f), EPS);
        assertEquals(0.5f, AppDrawerTransitionGeometry.ramp(0.4f, 0.2f, 0.6f), EPS);
        assertEquals(1f, AppDrawerTransitionGeometry.ramp(0.6f, 0.2f, 0.6f), EPS);
        assertEquals(1f, AppDrawerTransitionGeometry.ramp(0.9f, 0.2f, 0.6f), EPS);

        // The dock lift is paid back: nothing may be left translated at either end.
        assertEquals(0f, AppDrawerTransitionGeometry.dockLiftFraction(0f), EPS);
        assertEquals(0f, AppDrawerTransitionGeometry.dockLiftFraction(1f), EPS);
        assertTrue(AppDrawerTransitionGeometry.dockLiftFraction(0.2f) > 0.9f);
    }

    @Test
    public void seedFrameAtZeroProgressIsTheDockRectExactly() {
        Frame dock = new Frame(42f, 1780f, 1038f, 1996f);
        Frame open = new Frame(24f, 96f, 1056f, 2340f);
        assertEquals(dock, AppDrawerTransitionGeometry.resolvePlaneFrame(dock, open, 0f, 0f));
        assertEquals(open, AppDrawerTransitionGeometry.resolvePlaneFrame(dock, open, 1f, 0f));

        // Mid-drag the lift applies to the seed end only, so it fades out with the seed.
        Frame mid = AppDrawerTransitionGeometry.resolvePlaneFrame(dock, open, 0.5f, -24f);
        assertEquals(33f, mid.left, EPS);
        assertEquals(1047f, mid.right, EPS);
        assertEquals((1780f - 24f + 96f) / 2f, mid.top, EPS);
        assertEquals((1996f - 24f + 2340f) / 2f, mid.bottom, EPS);
    }

    @Test
    public void radiusAndInsetLerpEndpointsForBothDockStyles() {
        float openRadius = 60f;   // dp(20) at 3x
        float roundedSeed = 84f;  // dock capsule radius
        float defaultSeed = 0f;   // the default dock has square corners

        assertEquals(roundedSeed,
            AppDrawerTransitionGeometry.resolveRadiusPx(roundedSeed, openRadius, 0f), EPS);
        assertEquals(openRadius,
            AppDrawerTransitionGeometry.resolveRadiusPx(roundedSeed, openRadius, 1f), EPS);
        assertEquals(72f,
            AppDrawerTransitionGeometry.resolveRadiusPx(roundedSeed, openRadius, 0.5f), EPS);

        assertEquals(defaultSeed,
            AppDrawerTransitionGeometry.resolveRadiusPx(defaultSeed, openRadius, 0f), EPS);
        assertEquals(openRadius,
            AppDrawerTransitionGeometry.resolveRadiusPx(defaultSeed, openRadius, 1f), EPS);

        assertEquals(42f, AppDrawerTransitionGeometry.resolveInsetPx(42f, 24f, 0f), EPS);
        assertEquals(24f, AppDrawerTransitionGeometry.resolveInsetPx(42f, 24f, 1f), EPS);
        assertEquals(33f, AppDrawerTransitionGeometry.resolveInsetPx(42f, 24f, 0.5f), EPS);
        // Progress arrives clamped even if a caller overshoots.
        assertEquals(24f, AppDrawerTransitionGeometry.resolveInsetPx(42f, 24f, 1.4f), EPS);
    }

    @Test
    public void searchRevealLiftsOnlyThePlaneBottomAndIsFreeAtZero() {
        float openBottom = 2340f;
        float pinTop = 1800f;   // the in-app keyboard's captured top
        float gap = 12f;        // the captured dock↔keyboard gap

        // Byte-identical at zero: the controller routes the bottom edge through here on every open
        // frame, keyboard or no keyboard, so a closed keyboard must cost the plane nothing.
        assertEquals(openBottom,
            AppDrawerTransitionGeometry.resolveSearchPlaneBottom(openBottom, pinTop, gap, 0f), 0f);
        // Fully revealed the plane stops exactly one captured gap above the keyboard.
        assertEquals(pinTop - gap,
            AppDrawerTransitionGeometry.resolveSearchPlaneBottom(openBottom, pinTop, gap, 1f), EPS);
        assertEquals((openBottom + (pinTop - gap)) / 2f,
            AppDrawerTransitionGeometry.resolveSearchPlaneBottom(openBottom, pinTop, gap, 0.5f), EPS);
        // The reveal runs on its own spring, which overshoots; the fraction arrives clamped.
        assertEquals(pinTop - gap,
            AppDrawerTransitionGeometry.resolveSearchPlaneBottom(openBottom, pinTop, gap, 1.3f), EPS);
        assertEquals(openBottom,
            AppDrawerTransitionGeometry.resolveSearchPlaneBottom(openBottom, pinTop, gap, -0.4f), 0f);
    }

    @Test
    public void theRevealClipRunsFromTheHostBottomToTheKeyboardTopWithNoGap() {
        assertEquals(2400f, AppDrawerTransitionGeometry.resolveRevealClipBottom(2400f, 1700f, 0f), 0f);
        assertEquals(2050f, AppDrawerTransitionGeometry.resolveRevealClipBottom(2400f, 1700f, 0.5f), 0f);
        assertEquals(1700f, AppDrawerTransitionGeometry.resolveRevealClipBottom(2400f, 1700f, 1f), 0f);
        // The plane itself stops a gap short of the same edge.
        assertEquals(1680f, AppDrawerTransitionGeometry.resolveSearchPlaneBottom(2400f, 1700f, 20f, 1f), 0f);
    }
}
