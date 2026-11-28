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

    @Test
    public void oneRowAndTheRailAreOneRowThick() {
        assertEquals(96f, ControlBarPlacement.thicknessDp(ControlBarForm.TWO_ROWS), 0.001f);
        assertEquals(44f + 8f, ControlBarPlacement.thicknessDp(ControlBarForm.ONE_ROW), 0.001f);
        assertEquals(44f + 8f, ControlBarPlacement.thicknessDp(ControlBarForm.RAIL), 0.001f);
    }

    @Test
    public void theStackDrawnIsItsLayoutLessItsTranslation() {
        assertEquals(700f, ControlBarPlacement.drawnStackPx(700, 0f), 0f);
        // The keyboard laid out but down: the stack is drawn its keys lower.
        assertEquals(60f, ControlBarPlacement.drawnStackPx(700, 640f), 0f);
        // The phone's keyboard lifts it.
        assertEquals(760f, ControlBarPlacement.drawnStackPx(700, -60f), 0f);
        assertEquals(0f, ControlBarPlacement.drawnStackPx(700, 900f), 0f);
    }

    @Test
    public void theSettledBottomIsTheRestsDrawnStackAndItsGap() {
        // Keyboard up: on the stack.
        assertEquals(700 + 12, ControlBarPlacement.settledBottomPx(48, 700, 0f, 12));
        // Keyboard down, laid out: on what is left of the stack, above the gesture area.
        assertEquals(60 + 12, ControlBarPlacement.settledBottomPx(48, 700, 640f, 12));
        assertEquals(48 + 12, ControlBarPlacement.settledBottomPx(48, 700, 700f, 12));
        // The old answer for a stack drawn where it is laid out.
        assertEquals(30 + ControlBarPlacement.bottomGapPx(48, 30, 12),
            ControlBarPlacement.settledBottomPx(48, 30, 0f, 12));
    }

    @Test
    public void atItsRestTheBarIsNotMoved() {
        int settled = ControlBarPlacement.settledBottomPx(48, 700, 640f, 12);
        assertEquals(0f, ControlBarPlacement.rideTyPx(settled, 48, 700, 640f, 12), 0f);
    }

    @Test
    public void asTheKeyboardRisesTheBarRisesWithTheStacksTop() {
        int settled = ControlBarPlacement.settledBottomPx(48, 700, 640f, 12);
        // Half way: the stack's top 320 px higher, so the bar too.
        assertEquals(-320f, ControlBarPlacement.rideTyPx(settled, 48, 700, 320f, 12), 0.001f);
        // Up: the bar stands its gap above the keyboard.
        assertEquals(-640f, ControlBarPlacement.rideTyPx(settled, 48, 700, 0f, 12), 0.001f);
    }

    @Test
    public void belowTheGestureAreaTheBarWaitsAboveIt() {
        // A stack drawn shorter than the navigation: the bar keeps above the gesture area.
        int settled = ControlBarPlacement.settledBottomPx(48, 700, 700f, 12);
        assertEquals(0f, ControlBarPlacement.rideTyPx(settled, 48, 700, 680f, 12), 0.001f);
        assertEquals(-2f, ControlBarPlacement.rideTyPx(settled, 48, 700, 650f, 12), 0.001f);
    }

    @Test
    public void theCameraIsKeptClearAutomaticallyOrByTheCustomRoom() {
        assertEquals(80 + 8, ControlBarPlacement.cutoutClearPx(false, false, 80, 8));
        assertEquals(0, ControlBarPlacement.cutoutClearPx(false, false, 0, 8));
        assertEquals(8, ControlBarPlacement.cutoutClearPx(false, true, 0, 8));
        assertEquals(80 + 8, ControlBarPlacement.cutoutClearPx(false, true, 80, 8));
        assertEquals(0, ControlBarPlacement.cutoutClearPx(true, true, 80, 8));
    }

    @Test
    public void theRailKeepsClearOfTheCameraAndTheButtonsWhereTheHostDoesNot() {
        // Nothing on that edge: its own air.
        assertEquals(12, ControlBarPlacement.railEdgeMarginPx(12, 0, 0, 0));
        // The camera on that edge, the host reaching the screen's edge.
        assertEquals(12 + 88, ControlBarPlacement.railEdgeMarginPx(12, 0, 88, 0));
        // The host already stands clear of part of it.
        assertEquals(12 + 28, ControlBarPlacement.railEdgeMarginPx(12, 0, 88, 60));
        assertEquals(12, ControlBarPlacement.railEdgeMarginPx(12, 0, 88, 100));
        // Three-button navigation on that edge, wider than the camera's keep-out.
        assertEquals(12 + 126, ControlBarPlacement.railEdgeMarginPx(12, 126, 88, 0));
    }

    /** Decided 2026-11-28: edit mode's lift moves the Workspaces' end with the bar; a rail or full screen keeps its own. */
    @Test
    public void theWorkspacesEndAboveTheLiftedBar() {
        assertEquals(0, ControlBarPlacement.workspacesBottomPx(false, 0, 0));
        assertEquals(140, ControlBarPlacement.workspacesBottomPx(false, 0, 140));
        assertEquals(0, ControlBarPlacement.workspacesBottomPx(false, 0, -5));
        assertEquals("standing on the stack: its settled bottom", 60,
            ControlBarPlacement.workspacesBottomPx(true, 60, 140));
    }
}
