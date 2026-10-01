package com.termux.app.dock;

import com.termux.app.chrome.ChromePolicy;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * The bands under the keyboard. Floating they are a card: the Corners radius, the dock's side inset
 * and a clearance above the navigation area. Docked they join the frame flush below the keyboard
 * and every figure but the height is zero.
 */
public class UnderKeyboardBandTest {

    private static final float DENSITY = 2.75f;
    private static final int INSET_DP = 10;

    private static DockLayout dock(boolean capsule, int cornerDp) {
        return DockLayoutPolicy.compute(DockLayoutPolicy.DockInputs.builder()
            .preferencesAvailable(true)
            .capsule(capsule)
            .appsRowPageStripShown(true)
            .density(DENSITY)
            .barHeightScale(DockLayoutPolicy.sizePreset(2))
            .dockHorizontalInsetDp(INSET_DP)
            .configuredCornerRadiusDp(cornerDp)
            .appsRowEnabledPref(true)
            .azRowEnabledPref(true)
            .baseToolbarHeightPx(103)
            .build());
    }

    /** The gap the stack itself keeps under everything it holds, as the activity resolves it. */
    private static int edgeGap(DockLayout dock) {
        return ChromePolicy.bottomEdgeGapPx(false, dock.capsule, dock.capsuleBottomGapPx);
    }

    @Test
    public void floatingTheCardStandsTheCapsuleGapClearOfTheNavigationArea() {
        DockLayout dock = dock(true, -1);
        int clearance = UnderKeyboardBand.bottomAirPx(dock, edgeGap(dock)) + edgeGap(dock);
        assertEquals(dock.capsuleBottomGapPx, clearance);
        assertEquals(Math.round(6f * DENSITY), clearance);
    }

    @Test
    public void floatingsStackAlreadyKeepsTheGapSoTheCardAddsNoneUnderItself() {
        DockLayout floating = dock(true, -1);
        assertEquals(0, UnderKeyboardBand.bottomAirPx(floating, edgeGap(floating)));
    }

    @Test
    public void dockedTheBandJoinsTheFrameWithNoAirAboveOrUnder() {
        DockLayout docked = dock(false, -1);
        assertEquals(0, UnderKeyboardBand.navClearancePx(docked));
        assertEquals(0, UnderKeyboardBand.bottomAirPx(docked, edgeGap(docked)));
        assertEquals(0, UnderKeyboardBand.topGapPx(docked));
        assertEquals(0, UnderKeyboardBand.airPx(docked, edgeGap(docked), true));
    }

    @Test
    public void theAirIsCountedOnlyWhileABandStandsUnderTheKeyboard() {
        DockLayout floating = dock(true, -1);
        assertEquals(0, UnderKeyboardBand.airPx(floating, edgeGap(floating), false));
        assertEquals(UnderKeyboardBand.keyboardGapPx(DENSITY),
            UnderKeyboardBand.airPx(floating, edgeGap(floating), true));
    }

    @Test
    public void theGapUnderTheKeyboardIsTheFloatingKeyboardsGapOverIt() {
        assertEquals(11, UnderKeyboardBand.keyboardGapPx(DENSITY));
        assertEquals(UnderKeyboardBand.keyboardGapPx(DENSITY),
            UnderKeyboardBand.topGapPx(dock(true, -1)));
    }

    @Test
    public void theCardTakesTheCornersClampedToAHalfCapsule() {
        DockLayout configured = dock(true, 24);
        assertEquals(24f * DENSITY, UnderKeyboardBand.cornerRadiusPx(configured, 400), 0.001f);
        assertEquals("a one-row card is a true capsule, not a lozenge", 30f,
            UnderKeyboardBand.cornerRadiusPx(configured, 60), 0.001f);
        assertEquals("Docked the band is square: the frame's clip owns the corners", 0f,
            UnderKeyboardBand.cornerRadiusPx(dock(false, -1), 400), 0.001f);
    }

    @Test
    public void theCardsSidesAreTheDocksOwnInset() {
        DockLayout floating = dock(true, -1);
        assertEquals(floating.horizontalInsetPx, UnderKeyboardBand.sideInsetPx(floating));
        assertEquals(Math.round(INSET_DP * DENSITY), UnderKeyboardBand.sideInsetPx(floating));
        assertEquals("Docked is flush at the sides", 0,
            UnderKeyboardBand.sideInsetPx(dock(false, -1)));
    }
}
