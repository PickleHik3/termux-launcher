package com.termux.app.controlbar;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/** Where the bar stands above Android's navigation and the keyboard (#424). */
public class ControlBarPlacementTest {

    @Test
    public void withNothingBelowItStandsAboveTheGestureArea() {
        assertEquals(48 + 12, ControlBarPlacement.bottomGapPx(48, 0, 12));
    }

    @Test
    public void aboveThreeButtonNavigationItStandsAboveTheButtons() {
        assertEquals(126 + 12, ControlBarPlacement.bottomGapPx(126, 0, 12));
    }

    @Test
    public void onAStackTallerThanTheNavigationItKeepsOnlyItsGap() {
        // The keyboard's column already reaches over the gesture area.
        assertEquals(12, ControlBarPlacement.bottomGapPx(48, 700, 12));
    }

    @Test
    public void onAStackShorterThanTheNavigationItMakesUpTheRest() {
        assertEquals(12 + 18, ControlBarPlacement.bottomGapPx(48, 30, 12));
    }

    @Test
    public void negativeReadingsCountAsNone() {
        assertEquals(12, ControlBarPlacement.bottomGapPx(-5, -9, 12));
        assertEquals(0, ControlBarPlacement.bottomGapPx(0, 0, -3));
    }

    @Test
    public void theBarsTopIsTheStackItsGapAndItsHeight() {
        assertEquals(48 + 12 + 96, ControlBarPlacement.topInsetPx(48, 0, 12, 96));
        assertEquals(700 + 12 + 96, ControlBarPlacement.topInsetPx(48, 700, 12, 96));
        // The bar's own margin and its top read the same gap.
        assertEquals(30 + ControlBarPlacement.bottomGapPx(48, 30, 12) + 96,
            ControlBarPlacement.topInsetPx(48, 30, 12, 96));
    }

    @Test
    public void aPageUnderTheBarEndsAboveIt() {
        assertEquals(156, ControlBarPlacement.pageInsetPx(48, 156));
        assertEquals(900, ControlBarPlacement.pageInsetPx(900, 156));
        assertEquals(0, ControlBarPlacement.pageInsetPx(-4, -1));
    }

    @Test
    public void theBarIsTwoRowsAndItsPadding() {
        assertEquals(2 * 44 + 2 * 4, ControlBarPlacement.HEIGHT_DP, 0.001f);
    }
}
