package com.termux.app.dock;

import com.termux.app.chrome.ChromePolicy;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.termux.app.place.EdgeStackPolicy;
import com.termux.app.place.Element;
import com.termux.app.place.PlaceLayout;
import com.termux.app.place.PlaceLayout.Edge;
import com.termux.app.place.PlaceLayout.RowPlacement;
import com.termux.app.place.Slot;
import com.termux.app.wall.PaneWallPage;

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
        return ChromePolicy.bottomEdgeGapPx(dock.capsule, dock.capsuleBottomGapPx);
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

    /** A 411 dp phone's Home as shipped: the apps row with the A–Z row under it, over the keyboard. */
    private static final PlaceLayout PHONE_HOME = new PlaceLayout(Edge.TOP, RowPlacement.BOTTOM,
        true, Edge.BOTTOM, RowPlacement.HIDDEN, PlaceLayout.KeyboardMode.RESIZE,
        PlaceLayout.KeyboardForm.DOCKED, 4, 5);

    /**
     * A 912 dp tablet's landscape Home: the apps row alone on the dock, placed under the keyboard,
     * as the reported device's view tree stands it.
     */
    private static final PlaceLayout TABLET_LANDSCAPE_HOME = new PlaceLayout(Edge.TOP,
        RowPlacement.BOTTOM, false, Edge.BOTTOM, RowPlacement.HIDDEN,
        PlaceLayout.KeyboardMode.RESIZE, PlaceLayout.KeyboardForm.DOCKED, 4, 5)
        .withSlot(Element.APPS, new Slot(false, Edge.BOTTOM,
            PHONE_HOME.slot(Element.APPS).order, true));

    /** The rule the activity dresses the under-keyboard stack by, for {@code layout} on Home at rest. */
    private static boolean sheetWorn(PlaceLayout layout, boolean peek) {
        boolean bands = !EdgeStackPolicy.underKeyboard(layout).isEmpty();
        boolean card = DockPlacement.peekCardStandsOn(PaneWallPage.WIDGETS, peek, true, layout,
            false, false, false, false);
        return UnderKeyboardBand.wearsSheet(bands, true, card);
    }

    @Test
    public void peekWornTheCardIsTheOnlyGlassAroundTheIconsOnATabletsLandscape() {
        assertFalse("the apps row stands under the keyboard",
            EdgeStackPolicy.underKeyboard(TABLET_LANDSCAPE_HOME).isEmpty());
        assertTrue("the card stands for the row under the keyboard",
            DockPlacement.peekCardStandsOn(PaneWallPage.WIDGETS, true, true,
                TABLET_LANDSCAPE_HOME, false, false, false, false));
        assertFalse("no sheet over the card's top", sheetWorn(TABLET_LANDSCAPE_HOME, true));
        assertTrue("any other dock type keeps the sheet", sheetWorn(TABLET_LANDSCAPE_HOME, false));
    }

    @Test
    public void aPhoneHasNoBandUnderTheKeyboardAndSoNoSheetWhateverTheDock() {
        assertTrue(EdgeStackPolicy.underKeyboard(PHONE_HOME).isEmpty());
        assertFalse(sheetWorn(PHONE_HOME, true));
        assertFalse(sheetWorn(PHONE_HOME, false));
    }

    @Test
    public void theSheetNeedsABandAndTheDockUp() {
        assertTrue(UnderKeyboardBand.wearsSheet(true, true, false));
        assertFalse(UnderKeyboardBand.wearsSheet(false, true, false));
        assertFalse(UnderKeyboardBand.wearsSheet(true, false, false));
        assertFalse(UnderKeyboardBand.wearsSheet(true, true, true));
    }
}
