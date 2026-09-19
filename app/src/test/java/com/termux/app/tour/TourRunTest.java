package com.termux.app.tour;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The run as data: five lessons, a question and a closing card, each naming its own stages, copy,
 * controls and clearing signals.
 *
 * <p>The rule the whole run is built on is asserted here rather than read: nothing in the basics
 * opens a shell, a window or a session, so no lesson may be cleared by a signal about one.
 */
public class TourRunTest {

    /** A phone this launcher is not the home app of, with the keyboard up. */
    private static final TourRun.RunContext GUEST = new TourRun.RunContext(false, true);
    /** A phone this launcher is the home app of, with the keyboard down. */
    private static final TourRun.RunContext HOME = new TourRun.RunContext(true, false);

    private static TourStep step(String id) {
        return step(GUEST, id);
    }

    /** By id rather than by position, so a card added in the middle does not rewrite every test. */
    private static TourStep step(TourRun.RunContext context, String id) {
        for (TourStep step : TourRun.steps(context))
            if (step.id.equals(id)) return step;
        throw new AssertionError("no card " + id + " in the run");
    }

    private static int indexOf(String id) {
        List<TourStep> steps = TourRun.steps(GUEST);
        for (int i = 0; i < steps.size(); i++)
            if (steps.get(i).id.equals(id)) return i;
        throw new AssertionError("no card " + id + " in the run");
    }

    @Test
    public void theRunIsTheFiveLessonsTheHomeQuestionAndTheClosingCardInOrder() {
        assertEquals(7, TourRun.steps(GUEST).size());
        String[] order = {TourRun.FIND_HELP, TourRun.PIN_APPS, TourRun.FIND_APPS, TourRun.KEYBOARD,
            TourRun.FIND_ACTION, TourRun.HOME_CHOICE, TourRun.CLOSING};
        for (int i = 0; i < order.length; i++)
            assertEquals("card " + i, order[i], TourRun.steps(GUEST).get(i).id);
    }

    @Test
    public void theFiveLessonsAreTheFiveLessonsAndNothingElseIs() {
        assertEquals(Arrays.asList(TourRun.FIND_HELP, TourRun.PIN_APPS, TourRun.FIND_APPS,
            TourRun.KEYBOARD, TourRun.FIND_ACTION), TourRun.lessons());
        for (TourStep step : TourRun.steps(GUEST))
            assertEquals("kind of " + step.id, TourRun.lessons().contains(step.id),
                step.kind == TourStep.Kind.LESSON);
    }

    @Test
    public void everyCardHasCopyAUniqueIdAGestureAndATargetForEveryStage() {
        Set<String> ids = new HashSet<>();
        for (TourStep step : TourRun.steps(GUEST)) {
            assertTrue("duplicate id " + step.id, ids.add(step.id));
            assertTrue("no copy for " + step.id, step.copyRes != 0);
            for (int stage = 0; stage < step.stageCount(); stage++) {
                assertEquals("signal for " + step.id + ":" + stage,
                    step.isShownOnlyStage(stage), step.signalAt(stage) == null);
                assertNotNull("no gesture for " + step.id + ":" + stage, step.gestureAt(stage));
                assertNotNull("no target for " + step.id + ":" + stage, step.targetIdAt(stage));
                assertTrue("no copy for " + step.id + ":" + stage, step.copyResAt(stage) != 0);
            }
        }
    }

    @Test
    public void noLessonIsClearedByAWindowASessionOrASplit() {
        // Nothing in the basics opens a shell, a window or a session: the user's first terminal is
        // exactly as they left it when the run ends.
        Set<String> forbidden = new HashSet<>(Arrays.asList(TourSignals.WINDOW_OPENED,
            TourSignals.WINDOW_CLOSED, TourSignals.WINDOW_CHIP_SELECTED,
            TourSignals.WINDOW_RETURNED, TourSignals.SESSION_OPENED,
            TourSignals.SESSION_RETURNED, TourSignals.PANE_SPLIT));
        for (TourRun.RunContext context : new TourRun.RunContext[] {GUEST, HOME})
            for (TourStep step : TourRun.steps(context))
                for (int stage = 0; stage < step.signalCount(); stage++)
                    assertFalse(step.id + ":" + stage + " is cleared by " + step.signalAt(stage),
                        forbidden.contains(step.signalAt(stage)));
    }

    @Test
    public void findHelpWalksTheCornerTheQuestionMarkAndTheWayBackOut() {
        TourStep help = step(TourRun.FIND_HELP);
        assertEquals(3, help.signalCount());
        assertEquals(TourTargets.PANE_CORNER, help.targetIdAt(0));
        assertEquals(TourSignals.PANE_CORNER_MENU, help.signalAt(0));
        assertEquals(TourTargets.HELP_BUTTON, help.targetIdAt(1));
        assertEquals(TourSignals.HELP_OPENED, help.signalAt(1));
        // Help covers the screen, so the last stage has nothing to point at.
        assertEquals(TourTargets.NONE, help.targetIdAt(2));
        assertEquals(TourSignals.HELP_CLOSED, help.signalAt(2));
        // A sentence per stage: each one is a different control and a different tap.
        assertNotEquals(help.copyResAt(0), help.copyResAt(1));
        assertNotEquals(help.copyResAt(1), help.copyResAt(2));
    }

    @Test
    public void findHelpIsTheFirstLessonBecauseItIsTheWayBackToEverythingElse() {
        assertEquals(0, indexOf(TourRun.FIND_HELP));
    }

    @Test
    public void pinYourAppsIsSecondBecauseTheDockOfANewInstallIsEmpty() {
        assertEquals(1, indexOf(TourRun.PIN_APPS));
    }

    @Test
    public void pinYourAppsHoldsTheDockAndThenWaitsForASaveWithAPinInIt() {
        TourStep pin = step(TourRun.PIN_APPS);
        assertEquals(2, pin.signalCount());
        assertEquals(2, pin.stageCount());
        assertEquals(TourTargets.DOCK, pin.targetIdAt(0));
        // The one hold in the run: the dock's own gesture, and the thing nobody guesses.
        assertEquals(TourGesture.HOLD, pin.gestureAt(0));
        assertEquals(TourSignals.PIN_EDITOR_OPENED, pin.signalAt(0));
        // The sheet is a window over the dock, so the second stage points at nothing.
        assertEquals(TourTargets.NONE, pin.targetIdAt(1));
        assertEquals(TourGesture.TAP, pin.gestureAt(1));
        assertEquals(TourSignals.PINNED_APPS_SAVED, pin.signalAt(1));
        assertNotEquals(pin.copyResAt(0), pin.copyResAt(1));
        // That second stage is the one card that may be read while the sheet is up.
        assertNull(TourCardVisibility.chromeAskedAboutBy(pin.signalAt(0)));
        assertEquals(TourChrome.PIN_EDITOR,
            TourCardVisibility.chromeAskedAboutBy(pin.signalAt(1)));
    }

    @Test
    public void findAppsWalksTheDockTheAppAndTheWayBack() {
        TourStep apps = step(TourRun.FIND_APPS);
        assertEquals(3, apps.signalCount());
        assertEquals(TourTargets.DOCK, apps.targetIdAt(0));
        assertEquals(TourGesture.DRAG_DOWN, apps.gestureAt(0));
        assertEquals(TourSignals.DRAWER_OPENED, apps.signalAt(0));
        // The drawer covers the dock, and the launched app covers the launcher.
        assertEquals(TourTargets.NONE, apps.targetIdAt(1));
        assertEquals(TourSignals.APP_LAUNCHED, apps.signalAt(1));
        assertEquals(TourTargets.NONE, apps.targetIdAt(2));
        assertEquals(TourSignals.LAUNCHER_RESUMED, apps.signalAt(2));
    }

    @Test
    public void theScrubIsNotAStageOfItsOwnAnyMore() {
        for (TourStep step : TourRun.steps(GUEST))
            for (int stage = 0; stage < step.signalCount(); stage++)
                assertNotEquals(step.id + " still asks for the scrub",
                    TourSignals.APP_LAUNCHED_FROM_SCRUB, step.signalAt(stage));
    }

    @Test
    public void theWayBackFromAnAppFitsWhatTheHomeButtonWouldDo() {
        // A phone whose home screen is another launcher has no Home button that leads back here.
        int home = step(HOME, TourRun.FIND_APPS).copyResAt(2);
        int guest = step(GUEST, TourRun.FIND_APPS).copyResAt(2);
        assertNotEquals(home, guest);
        // Only that last sentence differs; the lesson is otherwise the same one.
        assertEquals(step(HOME, TourRun.FIND_APPS).copyResAt(0),
            step(GUEST, TourRun.FIND_APPS).copyResAt(0));
    }

    @Test
    public void theKeyboardLessonIsTheKeyboardButtonBothWaysRound() {
        TourStep shown = step(GUEST, TourRun.KEYBOARD);
        assertEquals(2, shown.signalCount());
        assertEquals(TourTargets.KEYBOARD_TOGGLE_KEY, shown.targetIdAt(0));
        assertEquals(TourTargets.KEYBOARD_TOGGLE_KEY, shown.targetIdAt(1));
        assertEquals(TourGesture.TAP, shown.gestureAt(0));
        // The keyboard is up, so it is hidden first and brought back second.
        assertEquals(TourSignals.KEYBOARD_HIDDEN, shown.signalAt(0));
        assertEquals(TourSignals.KEYBOARD_SHOWN, shown.signalAt(1));

        TourStep hidden = step(HOME, TourRun.KEYBOARD);
        // The keyboard is down, so the lesson is the same two taps the other way round.
        assertEquals(TourSignals.KEYBOARD_SHOWN, hidden.signalAt(0));
        assertEquals(TourSignals.KEYBOARD_HIDDEN, hidden.signalAt(1));
        assertNotEquals(shown.copyResAt(0), hidden.copyResAt(0));
        assertNotEquals(shown.copyResAt(1), hidden.copyResAt(1));
        assertNotEquals(hidden.copyResAt(0), hidden.copyResAt(1));
    }

    @Test
    public void findActionOpensThePaletteAndAsksForTheWayOutOfIt() {
        TourStep action = step(TourRun.FIND_ACTION);
        assertEquals(2, action.signalCount());
        assertEquals(TourTargets.SPACE_BAR, action.targetIdAt(0));
        assertEquals(TourGesture.SWIPE_UP, action.gestureAt(0));
        assertEquals(TourSignals.PALETTE_OPENED, action.signalAt(0));
        assertEquals(TourTargets.NONE, action.targetIdAt(1));
        assertEquals(TourSignals.PALETTE_CLOSED, action.signalAt(1));
        assertNotEquals(action.copyResAt(0), action.copyResAt(1));
        // The stage that may be read over the palette is the one asking for its close.
        assertNull(TourCardVisibility.chromeClosedBy(action.signalAt(0)));
        assertEquals(TourChrome.PALETTE,
            TourCardVisibility.chromeClosedBy(action.signalAt(1)));
    }

    @Test
    public void theKeyboardLessonEndsOnTheTerminalsHoldWhichTheRunOnlyShows() {
        TourStep keyboard = step(GUEST, TourRun.KEYBOARD);
        assertTrue(keyboard.endsShown);
        assertEquals(2, keyboard.signalCount());
        assertEquals(3, keyboard.stageCount());
        assertTrue(keyboard.isShownOnlyStage(2));
        assertFalse(keyboard.isShownOnlyStage(1));
        // Nothing to watch for: what the hold does needs a program that follows the mouse.
        assertNull(keyboard.signalAt(2));
        assertEquals(TourTargets.TERMINAL_PANE, keyboard.targetIdAt(2));
        assertEquals(TourGesture.HOLD, keyboard.gestureAt(2));
        // Its own sentence, and the same one whichever way round the lesson began.
        assertNotEquals(keyboard.copyResAt(1), keyboard.copyResAt(2));
        assertEquals(step(HOME, TourRun.KEYBOARD).copyResAt(2), keyboard.copyResAt(2));
    }

    @Test
    public void itIsTheOnlyStageOfTheRunWithNoSignalBehindIt() {
        for (TourStep step : TourRun.steps(GUEST)) {
            if (step.kind != TourStep.Kind.LESSON) continue;
            assertEquals("shown stage of " + step.id, TourRun.KEYBOARD.equals(step.id),
                step.endsShown);
        }
    }

    @Test
    public void everyLessonOffersBackSkipStepAndEndTour() {
        for (String id : TourRun.lessons())
            assertEquals("buttons of " + id, Arrays.asList(TourAction.BACK, TourAction.SKIP_STEP,
                TourAction.END_TOUR), step(id).actions());
    }

    @Test
    public void theHomeQuestionIsAChoiceWithNoGestureAtAll() {
        TourStep choice = step(GUEST, TourRun.HOME_CHOICE);
        assertEquals(TourStep.Kind.CHOICE, choice.kind);
        assertTrue(choice.isChoiceCard());
        assertFalse(choice.isClosingCard());
        assertEquals(0, choice.signalCount());
        assertEquals(TourTargets.NONE, choice.targetIdAt(0));
        assertEquals(Arrays.asList(TourAction.USE_AS_HOME, TourAction.KEEP_TRYING),
            choice.actions());
    }

    @Test
    public void aPhoneThatIsAlreadySetUpThisWayIsToldSoRatherThanAsked() {
        TourStep already = step(HOME, TourRun.HOME_CHOICE);
        assertEquals(Arrays.asList(TourAction.CONTINUE), already.actions());
        assertNotEquals(step(GUEST, TourRun.HOME_CHOICE).copyRes, already.copyRes);
    }

    @Test
    public void theClosingCardEndsOnItsOwnActionAlone() {
        TourStep closing = step(TourRun.CLOSING);
        assertEquals(TourStep.Kind.CLOSING, closing.kind);
        assertTrue(closing.isClosingCard());
        assertEquals(0, closing.signalCount());
        assertEquals(TourGesture.NONE, closing.gestureAt(0));
        assertEquals(Arrays.asList(TourAction.START_USING), closing.actions());
        assertEquals(TourRun.steps(GUEST).size() - 1, indexOf(TourRun.CLOSING));
    }

    @Test
    public void noCardRestsAtTheTopOfTheScreenAndNoneIsAChord() {
        for (TourStep step : TourRun.steps(GUEST)) {
            assertFalse("top anchoring for " + step.id, step.topAnchored);
            assertFalse("chord glow for " + step.id, step.chordGlow);
        }
    }

    @Test
    public void everyLessonIsTaughtOnTheTerminalAndTheLastTwoCardsReadAnywhere() {
        for (String id : TourRun.lessons())
            assertTrue(id + " should be taught on the terminal", step(id).taughtOnTheTerminal());
        assertFalse(step(TourRun.HOME_CHOICE).taughtOnTheTerminal());
        assertFalse(step(TourRun.CLOSING).taughtOnTheTerminal());
    }

    @Test
    public void theRunIsNotEditable() {
        List<TourStep> steps = TourRun.steps(GUEST);
        try {
            steps.remove(0);
            throw new AssertionError("the run should not be editable");
        } catch (UnsupportedOperationException expected) {
            assertEquals(7, TourRun.steps(GUEST).size());
        }
    }
}
