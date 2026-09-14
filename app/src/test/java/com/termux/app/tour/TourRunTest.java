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

    /** By id rather than by position, so a card added in the middle does not rewrite every test. */
    private static TourStep step(String id) {
        for (TourStep step : TourRun.steps())
            if (step.id.equals(id)) return step;
        throw new AssertionError("no card " + id + " in the run");
    }

    private static int indexOf(String id) {
        List<TourStep> steps = TourRun.steps();
        for (int i = 0; i < steps.size(); i++)
            if (steps.get(i).id.equals(id)) return i;
        throw new AssertionError("no card " + id + " in the run");
    }

    @Test
    public void theRunIsTheThirteenCardsOfTheSpecInOrder() {
        assertEquals(13, TourRun.steps().size());
        String[] order = {"status_place", "status_expand", "window", "kb_split", "kb_window",
            "kb_session", "kb_session_back", "kb_window_back", "pane_corner", "drawer",
            "az_scrub", "palette", "closing"};
        for (int i = 0; i < order.length; i++)
            assertEquals("card " + i, order[i], TourRun.steps().get(i).id);
    }

    @Test
    public void theKeyboardChapterSitsBetweenTheWindowCardAndThePaneCorner() {
        assertTrue(indexOf("window") < indexOf("kb_split"));
        assertTrue(indexOf("kb_window_back") < indexOf("pane_corner"));
    }

    @Test
    public void theChordCardsWalkTheirModifiersBeforeTheKeyTheyEndOn() {
        TourStep split = step("kb_split");
        assertTrue(split.chordGlow);
        assertEquals(TourTargets.CTRL_KEY, split.targetIdAt(0));
        assertEquals(TourTargets.ALT_KEY, split.targetIdAt(1));
        assertEquals(TourTargets.ENTER_KEY, split.targetIdAt(2));
        assertEquals(TourSignals.PANE_SPLIT, split.signalAt(0));

        TourStep window = step("kb_window");
        assertTrue(window.chordGlow);
        assertEquals(TourTargets.C_KEY, window.targetIdAt(2));
        // The window count going up is the same edge the + card reads; the chord is another way
        // to the same thing, and the run does not care which one the user used.
        assertEquals(TourSignals.WINDOW_OPENED, window.signalAt(0));

        TourStep session = step("kb_session");
        assertTrue(session.chordGlow);
        assertEquals(TourTargets.SHIFT_KEY, session.targetIdAt(2));
        assertEquals(TourTargets.C_KEY, session.targetIdAt(3));
        assertEquals(TourSignals.SESSION_OPENED, session.signalAt(0));
    }

    @Test
    public void theChapterEndsByAskingForTheWayBackToWhereItStarted() {
        TourStep sessionBack = step("kb_session_back");
        assertEquals(TourTargets.SPACE_BAR, sessionBack.targetIdAt(0));
        assertEquals(TourGesture.SWIPE_DOWN_LEFT, sessionBack.gestureAt(0));
        // The arrival, not the swipe: the corner swipes walk a ring, and a user two sessions
        // along has swiped without getting back.
        assertEquals(TourSignals.SESSION_RETURNED, sessionBack.signalAt(0));
        assertFalse(sessionBack.chordGlow);

        TourStep windowBack = step("kb_window_back");
        assertEquals(TourTargets.SPACE_BAR, windowBack.targetIdAt(0));
        assertEquals(TourGesture.SWIPE_UP_LEFT, windowBack.gestureAt(0));
        assertEquals(TourSignals.WINDOW_RETURNED, windowBack.signalAt(0));
        // The session comes back before the window: the window it is asking for lives in that
        // session, and there is no way to it from somewhere else.
        assertTrue(indexOf("kb_session_back") < indexOf("kb_window_back"));
    }

    @Test
    public void theChapterHomeIsRecordedOnItsFirstCard() {
        assertEquals(TourRun.KEYBOARD_CHAPTER_FIRST_STEP, step("kb_split").id);
    }

    @Test
    public void onlyTheChordCardsCarryAChordGlow() {
        for (TourStep step : TourRun.steps())
            assertEquals("chord glow for " + step.id, step.id.startsWith("kb_")
                && !step.id.endsWith("_back"), step.chordGlow);
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
                assertTrue("no copy for " + step.id + ":" + stage, step.copyResAt(stage) != 0);
            }
        }
    }

    @Test
    public void theTwoWiredStepsAskForTheStatusBarGesturesInOrder() {
        TourStep place = step("status_place");
        assertEquals(TourTargets.STATUS_BAR, place.targetId);
        assertEquals(TourSignals.PLACE_CHANGED, place.signalAt(0));
        assertEquals(TourSignals.PLACE_RETURNED, place.signalAt(1));
        assertNull(place.signalAt(2));
        assertFalse(place.showsSecondLineAt(0));
        assertTrue(place.showsSecondLineAt(1));

        TourStep expand = step("status_expand");
        assertEquals(TourTargets.STATUS_BAR, expand.targetId);
        assertEquals(TourSignals.STATUS_BAR_EXPANDED, expand.signalAt(0));
        assertEquals(TourSignals.STATUS_BAR_COLLAPSED, expand.signalAt(1));
        assertEquals(TourGesture.DRAG_DOWN, expand.gestureAt(0));
        assertEquals(TourGesture.DRAG_UP, expand.gestureAt(1));
        assertFalse(expand.showsSecondLineAt(1));
    }

    @Test
    public void theClosingCardEndsOnItsButtonAlone() {
        TourStep closing = step("closing");
        assertEquals(0, closing.signalCount());
        assertEquals(TourGesture.NONE, closing.gestureAt(0));
    }

    @Test
    public void theWindowCardWalksThePlusTheChipAndTheCloseItReveals() {
        TourStep window = step("window");
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
    public void theWindowCardSaysSomethingDifferentOnEveryOneOfItsThreeStages() {
        TourStep window = step("window");
        // Three controls, three taps, three sentences. A card still saying "tap the chip, then ×"
        // while the × is the thing under the finger asks for a gesture already made.
        assertTrue(window.copyResAt(0) != 0);
        assertTrue(window.copyResAt(0) != window.copyResAt(1));
        assertTrue(window.copyResAt(1) != window.copyResAt(2));
        // Past the end the last sentence stands, like the targets do.
        assertEquals(window.copyResAt(2), window.copyResAt(9));
    }

    @Test
    public void aCardWithOneSentenceKeepsItForEveryStage() {
        TourStep expand = step("status_expand");
        assertEquals(expand.copyRes, expand.copyResAt(0));
        assertEquals(expand.copyRes, expand.copyResAt(1));
    }

    @Test
    public void thePaletteCardAsksForTheWayOutOfThePaletteItOpened() {
        TourStep palette = step("palette");
        assertEquals(2, palette.signalCount());
        assertEquals(TourTargets.SPACE_BAR, palette.targetIdAt(0));
        assertEquals(TourGesture.SWIPE_UP, palette.gestureAt(0));
        assertEquals(TourSignals.PALETTE_OPENED, palette.signalAt(0));
        // Nothing to glow once the palette covers the screen, and its own sentence for the way out.
        assertEquals(TourTargets.NONE, palette.targetIdAt(1));
        assertEquals(TourGesture.TAP, palette.gestureAt(1));
        assertEquals(TourSignals.PALETTE_CLOSED, palette.signalAt(1));
        assertTrue(palette.copyResAt(0) != palette.copyResAt(1));
        // The closing card comes only once the palette is out of the way.
        assertTrue(indexOf("palette") < indexOf("closing"));
    }

    @Test
    public void thePaletteDismissStageIsTheOneThatMayDrawOverThePalette() {
        TourStep palette = step("palette");
        assertNull(TourCardVisibility.chromeClosedBy(palette.signalAt(0)));
        assertEquals(TourChrome.PALETTE, TourCardVisibility.chromeClosedBy(palette.signalAt(1)));
    }

    @Test
    public void thePaneCornerCardAsksForTheWayOutOfTheMenuItOpened() {
        TourStep corner = step("pane_corner");
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
        TourStep drawer = step("drawer");
        assertEquals(TourTargets.DOCK, drawer.targetIdAt(0));
        assertEquals(TourTargets.NONE, drawer.targetIdAt(1));
    }

    @Test
    public void aCardThatNamedOneTargetKeepsItForEveryStage() {
        TourStep expand = step("status_expand");
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
            assertEquals(13, TourRun.steps().size());
        }
    }

    @Test
    public void everyCardButTheStatusBarsAndTheClosingOneIsTaughtOnTheTerminal() {
        for (TourStep step : TourRun.steps()) {
            boolean aboutTheStatusBarOrNothing = step.id.startsWith("status_")
                || step.id.equals("closing");
            assertEquals("card " + step.id, !aboutTheStatusBarOrNothing,
                step.taughtOnTheTerminal());
        }
    }
}
