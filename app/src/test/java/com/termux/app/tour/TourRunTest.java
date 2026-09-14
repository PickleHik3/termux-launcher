package com.termux.app.tour;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** The run as data: every card has copy, a trace for each of its signals, and a unique id. */
public class TourRunTest {

    @Test
    public void theRunIsTheNineCardsOfTheSpec() {
        assertEquals(9, TourRun.steps().size());
        assertEquals("status_place", TourRun.steps().get(0).id);
        assertEquals("status_expand", TourRun.steps().get(1).id);
        assertEquals("closing", TourRun.steps().get(8).id);
    }

    @Test
    public void everyCardHasCopyAUniqueIdAndAGestureForEverySignal() {
        Set<String> ids = new HashSet<>();
        for (TourStep step : TourRun.steps()) {
            assertTrue("duplicate id " + step.id, ids.add(step.id));
            assertTrue("no copy for " + step.id, step.copyRes != 0);
            for (int stage = 0; stage < step.signalCount(); stage++) {
                assertNotNull("no signal for " + step.id + ":" + stage, step.signalAt(stage));
                assertNotNull("no gesture for " + step.id + ":" + stage, step.gestureAt(stage));
            }
        }
    }

    @Test
    public void theTwoWiredStepsAskForTheStatusBarGesturesInOrder() {
        TourStep place = TourRun.steps().get(0);
        assertEquals(TourTargets.STATUS_BAR, place.targetId);
        assertEquals(TourSignals.PLACE_CHANGED, place.signalAt(0));
        assertEquals(TourSignals.PLACE_RETURNED, place.signalAt(1));
        assertNull(place.signalAt(2));
        assertFalse(place.showsSecondLineAt(0));
        assertTrue(place.showsSecondLineAt(1));

        TourStep expand = TourRun.steps().get(1);
        assertEquals(TourTargets.STATUS_BAR, expand.targetId);
        assertEquals(TourSignals.STATUS_BAR_EXPANDED, expand.signalAt(0));
        assertEquals(TourSignals.STATUS_BAR_COLLAPSED, expand.signalAt(1));
        assertEquals(TourGesture.DRAG_DOWN, expand.gestureAt(0));
        assertEquals(TourGesture.DRAG_UP, expand.gestureAt(1));
        assertFalse(expand.showsSecondLineAt(1));
    }

    @Test
    public void theClosingCardEndsOnItsButtonAlone() {
        TourStep closing = TourRun.steps().get(8);
        assertEquals(0, closing.signalCount());
        assertEquals(TourGesture.NONE, closing.gestureAt(0));
    }

    @Test
    public void theWindowCardWalksThePlusTheChipAndTheCloseItReveals() {
        TourStep window = TourRun.steps().get(2);
        assertEquals(TourTargets.PLUS_BUTTON, window.targetIdAt(0));
        assertEquals(TourTargets.WINDOW_CHIP, window.targetIdAt(1));
        // The chip's tap reveals a close button of its own, and that is what the last half of the
        // card is asking the user to press.
        assertEquals(TourTargets.WINDOW_CLOSE, window.targetIdAt(2));
        assertEquals(TourSignals.WINDOW_OPENED, window.signalAt(0));
        assertEquals(TourSignals.WINDOW_CHIP_SELECTED, window.signalAt(1));
        assertEquals(TourSignals.WINDOW_CLOSED, window.signalAt(2));
    }

    @Test
    public void thePaneCornerCardAsksForTheWayOutOfTheMenuItOpened() {
        TourStep corner = TourRun.steps().get(4);
        assertEquals(2, corner.signalCount());
        assertEquals(TourTargets.PANE_CORNER, corner.targetIdAt(0));
        assertEquals(TourSignals.PANE_CORNER_MENU, corner.signalAt(0));
        // Nothing to glow for "tap anywhere else": the whole screen is the target.
        assertEquals(TourTargets.NONE, corner.targetIdAt(1));
        assertEquals(TourSignals.PANE_CONTROLS_DISMISSED, corner.signalAt(1));
        assertTrue(corner.showsSecondLineAt(1));
    }

    @Test
    public void onlyTheScrubCardRestsAtTheTopOfTheScreen() {
        for (TourStep step : TourRun.steps())
            assertEquals("top anchoring for " + step.id, "az_scrub".equals(step.id),
                step.topAnchored);
    }

    @Test
    public void theDrawerCardStopsPointingAtTheDockOnceTheDrawerCoversIt() {
        TourStep drawer = TourRun.steps().get(5);
        assertEquals(TourTargets.DOCK, drawer.targetIdAt(0));
        assertEquals(TourTargets.NONE, drawer.targetIdAt(1));
    }

    @Test
    public void theSplitCardAsksForTheTapThatActuallySplits() {
        // A swipe up on an extra key commits that key's secondary, which for the split key is
        // "new window"; the split is the plain tap.
        TourStep split = TourRun.steps().get(3);
        assertEquals(TourTargets.SPLIT_KEY, split.targetIdAt(0));
        assertEquals(TourGesture.TAP, split.gestureAt(0));
        assertEquals(TourSignals.PANE_SPLIT, split.signalAt(0));
    }

    @Test
    public void aCardThatNamedOneTargetKeepsItForEveryStage() {
        TourStep expand = TourRun.steps().get(1);
        assertEquals(TourTargets.STATUS_BAR, expand.targetIdAt(0));
        assertEquals(TourTargets.STATUS_BAR, expand.targetIdAt(1));
        assertEquals(TourTargets.STATUS_BAR, expand.targetIdAt(9));
        assertEquals(TourTargets.STATUS_BAR, expand.targetIdAt(-1));
    }

    @Test
    public void theRunIsNotEditable() {
        List<TourStep> steps = TourRun.steps();
        try {
            steps.remove(0);
            throw new AssertionError("the run should not be editable");
        } catch (UnsupportedOperationException expected) {
            assertEquals(9, TourRun.steps().size());
        }
    }
}
