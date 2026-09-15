package com.termux.app.tour;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Every transition of the run, on a fake clock and a fake store, over the real four-lesson run.
 *
 * <p>The three that matter most on a phone are the arming window — the settle callback of the
 * gesture that cleared the last card arrives a frame after the next one appears, and must not
 * clear it — resume, because the launcher is killed and restarted under the user constantly, and
 * practice, which must leave every one of those stored values exactly as it found them.
 */
public class TourControllerTest {

    /** A phone this launcher is not the home app of, with the keyboard up. */
    private static final TourRun.RunContext PHONE = new TourRun.RunContext(false, true);

    private FakeClock clock;
    private FakePrefs prefs;
    private RecordingListener listener;
    private TourController controller;

    @Before
    public void setUp() {
        clock = new FakeClock();
        prefs = new FakePrefs();
        listener = new RecordingListener();
        controller = newController();
    }

    private TourController newController() {
        TourController fresh = new TourController(TourRun.steps(PHONE), prefs, clock);
        fresh.setListener(listener);
        return fresh;
    }

    private void arm() {
        clock.advance(TourController.ARM_DELAY_MS);
    }

    /** Does the gesture the card that is up is waiting for. */
    private void doTheGesture() {
        TourStep step = controller.currentStep();
        arm();
        controller.onSignal(step.signalAt(controller.currentStage()));
    }

    /** Does every gesture the card that is up is waiting for. */
    private void clearTheCard() {
        TourStep step = controller.currentStep();
        while (controller.currentStep() == step) doTheGesture();
    }

    // Starting.

    @Test
    public void startShowsTheFirstLesson() {
        assertTrue(controller.start());
        assertTrue(controller.isRunning());
        assertEquals(TourRun.FIND_HELP, controller.currentStep().id);
        assertEquals(0, controller.currentStage());
        assertEquals(Arrays.asList(TourRun.FIND_HELP + ":0"), listener.shown);
        assertEquals(0, prefs.stepIndex);
        assertEquals(0, prefs.stage);
        assertEquals(TourController.RUN_VERSION, prefs.runVersion);
    }

    @Test
    public void startIfNeededDoesNothingForSomeoneWhoFinished() {
        prefs.completedVersion = TourController.RUN_VERSION;
        assertFalse(controller.startIfNeeded());
        assertFalse(controller.isRunning());
        assertTrue(listener.shown.isEmpty());
    }

    @Test
    public void aFinishedRunOfTheOlderTourStaysFinished() {
        // Upgrade shows nothing: someone who sat through the thirteen-card run, or skipped their
        // way out of it, has been through a run.
        prefs.completedVersion = 1;
        prefs.skipped = true;
        assertTrue(controller.isFinished());
        assertFalse(controller.startIfNeeded());
        assertFalse(controller.resumeIfInProgress());
        assertTrue(listener.shown.isEmpty());
    }

    @Test
    public void startIfNeededRunsForAFreshInstall() {
        assertTrue(controller.startIfNeeded());
        assertTrue(controller.isRunning());
    }

    @Test
    public void replayStartsOverForSomeoneWhoFinished() {
        prefs.completedVersion = TourController.RUN_VERSION;
        prefs.skipped = true;
        assertTrue(controller.start());
        assertEquals(TourRun.FIND_HELP, controller.currentStep().id);
        assertEquals(0, prefs.completedVersion);
        assertFalse(prefs.skipped);
    }

    @Test
    public void startAtEntersThatLessonAtItsFirstStage() {
        assertTrue(controller.startAt(TourRun.KEYBOARD));
        assertTrue(controller.isRunning());
        assertFalse(controller.isPracticing());
        assertEquals(TourRun.KEYBOARD, controller.currentStep().id);
        assertEquals(0, controller.currentStage());
        assertEquals(2, prefs.stepIndex);
        assertEquals(0, prefs.stage);
        // A normal run: everything after that lesson still follows.
        clearTheCard();
        assertEquals(TourRun.FIND_ACTION, controller.currentStep().id);
    }

    @Test
    public void startAtAnUnknownLessonDoesNothing() {
        assertFalse(controller.startAt("no_such_lesson"));
        assertFalse(controller.startAt(null));
        assertFalse(controller.isRunning());
        assertTrue(listener.shown.isEmpty());
    }

    // Signals.

    @Test
    public void aSignalWhileTheCardIsArmingIsIgnored() {
        controller.start();
        clock.advance(TourController.ARM_DELAY_MS - 1);
        controller.onSignal(TourSignals.PANE_CORNER_MENU);
        assertEquals(0, controller.currentStage());
    }

    @Test
    public void theExpectedSignalAdvancesToTheNextStage() {
        controller.start();
        doTheGesture();
        assertEquals(1, controller.currentStage());
        assertEquals(1, prefs.stage);
        assertEquals(TourRun.FIND_HELP + ":1", listener.shown.get(1));
    }

    @Test
    public void anOutOfOrderSignalIsIgnored() {
        controller.start();
        arm();
        controller.onSignal(TourSignals.HELP_CLOSED);
        assertEquals(0, controller.currentStage());
        controller.onSignal("something.else");
        assertEquals(0, controller.currentStage());
        controller.onSignal(null);
        assertEquals(0, controller.currentStage());
    }

    @Test
    public void theNextStageRearmsSoOneGestureCannotClearTwoOfThem() {
        controller.start();
        arm();
        controller.onSignal(TourSignals.PANE_CORNER_MENU);
        controller.onSignal(TourSignals.HELP_OPENED);
        assertEquals(TourRun.FIND_HELP, controller.currentStep().id);
        assertEquals(1, controller.currentStage());
    }

    @Test
    public void findHelpAdvancesOnlyAfterHelpHasBeenOpenedAndClosed() {
        controller.start();
        arm();
        controller.onSignal(TourSignals.PANE_CORNER_MENU);
        arm();
        controller.onSignal(TourSignals.HELP_OPENED);
        assertEquals(TourRun.FIND_HELP, controller.currentStep().id);
        assertEquals(2, controller.currentStage());
        arm();
        controller.onSignal(TourSignals.HELP_CLOSED);
        assertEquals(TourRun.FIND_APPS, controller.currentStep().id);
        assertEquals(0, controller.currentStage());
        assertEquals(1, prefs.stepIndex);
        assertFalse(prefs.skipped);
    }

    @Test
    public void aCardWithNoSignalsIsNotClearedByOne() {
        controller.startAt(TourRun.HOME_CHOICE);
        arm();
        controller.onSignal(TourSignals.PALETTE_CLOSED);
        assertTrue(controller.isRunning());
        assertEquals(TourRun.HOME_CHOICE, controller.currentStep().id);
    }

    // Back, skip and end.

    @Test
    public void skipStepMovesToTheNextLessonAndIsRemembered() {
        controller.start();
        controller.skip();
        assertEquals(TourRun.FIND_APPS, controller.currentStep().id);
        assertTrue(prefs.skipped);
        assertTrue(controller.wasSkipped());
    }

    @Test
    public void backReturnsToThePreviousLessonsFirstStage() {
        controller.startAt(TourRun.KEYBOARD);
        doTheGesture();
        assertEquals(1, controller.currentStage());
        controller.back();
        assertEquals(TourRun.FIND_APPS, controller.currentStep().id);
        assertEquals(0, controller.currentStage());
        assertEquals(1, prefs.stepIndex);
        assertEquals(0, prefs.stage);
        // Back again, and again: the first lesson is where it stops.
        controller.back();
        assertEquals(TourRun.FIND_HELP, controller.currentStep().id);
        controller.back();
        assertEquals(TourRun.FIND_HELP, controller.currentStep().id);
        assertEquals(0, prefs.stepIndex);
    }

    @Test
    public void backIsNotASkip() {
        controller.startAt(TourRun.FIND_APPS);
        controller.back();
        assertFalse(prefs.skipped);
    }

    @Test
    public void endTourFinishesTheRunAsSkipped() {
        controller.start();
        controller.endTour();
        assertFalse(controller.isRunning());
        assertNull(controller.currentStep());
        assertTrue(controller.isFinished());
        assertEquals(TourController.RUN_VERSION, prefs.completedVersion);
        assertEquals(-1, prefs.stepIndex);
        assertEquals(Arrays.asList(Boolean.TRUE), listener.finished);
    }

    @Test
    public void theClosingCardsActionEndsTheRunUnskipped() {
        controller.startAt(TourRun.CLOSING);
        controller.finish();
        assertFalse(controller.isRunning());
        assertTrue(controller.isFinished());
        assertEquals(Arrays.asList(Boolean.FALSE), listener.finished);
    }

    @Test
    public void nothingHappensAfterTheRunEnds() {
        controller.start();
        controller.finish();
        int shown = listener.shown.size();
        arm();
        controller.onSignal(TourSignals.PANE_CORNER_MENU);
        controller.skip();
        controller.back();
        controller.endTour();
        controller.finish();
        assertEquals(shown, listener.shown.size());
        assertEquals(1, listener.finished.size());
    }

    @Test
    public void everyLessonOffersItsOwnThreeButtons() {
        controller.start();
        assertEquals(Arrays.asList(TourAction.BACK, TourAction.SKIP_STEP, TourAction.END_TOUR),
            controller.currentActions());
        controller.finish();
        assertTrue(controller.currentActions().isEmpty());
    }

    // The home-screen question.

    @Test
    public void eitherAnswerToTheHomeQuestionMovesTheRunOn() {
        for (TourController.Choice choice : new TourController.Choice[] {
                TourController.Choice.USE_AS_HOME, TourController.Choice.KEEP_TRYING}) {
            setUp();
            controller.startAt(TourRun.HOME_CHOICE);
            assertEquals(Arrays.asList(TourAction.USE_AS_HOME, TourAction.KEEP_TRYING),
                controller.currentActions());
            controller.choose(choice);
            assertEquals(Arrays.asList(choice), listener.chosen);
            assertEquals(TourRun.CLOSING, controller.currentStep().id);
            assertFalse(prefs.skipped);
        }
    }

    @Test
    public void aChoiceIsOnlyEverTakenOnTheCardThatAsksOne() {
        controller.start();
        controller.choose(TourController.Choice.USE_AS_HOME);
        assertEquals(TourRun.FIND_HELP, controller.currentStep().id);
        assertTrue(listener.chosen.isEmpty());
        controller.startAt(TourRun.HOME_CHOICE);
        controller.choose(null);
        assertEquals(TourRun.HOME_CHOICE, controller.currentStep().id);
        assertTrue(listener.chosen.isEmpty());
    }

    // Practice.

    @Test
    public void practiceShowsOneLessonAndWritesNothingAtAll() {
        prefs.stepIndex = 3;
        prefs.stage = 1;
        assertTrue(controller.startPractice(TourRun.KEYBOARD));
        assertTrue(controller.isRunning());
        assertTrue(controller.isPracticing());
        assertEquals(TourRun.KEYBOARD, controller.currentStep().id);
        assertEquals(0, controller.currentStage());
        assertEquals(Arrays.asList(TourAction.DONE, TourAction.END_PRACTICE),
            controller.currentActions());
        assertEquals(0, prefs.writes);
        assertEquals(3, prefs.stepIndex);
        assertEquals(1, prefs.stage);
    }

    @Test
    public void practiceClearsOnTheLessonsOwnSignalsAndThenEnds() {
        controller.startPractice(TourRun.KEYBOARD);
        doTheGesture();
        assertEquals(1, controller.currentStage());
        assertTrue(controller.isRunning());
        doTheGesture();
        assertFalse(controller.isRunning());
        assertFalse(controller.isPracticing());
        assertNull(controller.currentStep());
        assertEquals(Arrays.asList(Boolean.FALSE), listener.finished);
        // Not the next lesson: practice is one lesson and then the overlay comes down.
        assertEquals(Arrays.asList(TourRun.KEYBOARD + ":0", TourRun.KEYBOARD + ":1"),
            listener.shown);
        assertEquals(0, prefs.writes);
    }

    @Test
    public void leavingPracticeWritesNothingEither() {
        controller.startPractice(TourRun.FIND_ACTION);
        controller.endPractice();
        assertFalse(controller.isRunning());
        assertEquals(0, prefs.writes);
        assertEquals(0, prefs.completedVersion);
        assertFalse(prefs.skipped);
        assertEquals(-1, prefs.stepIndex);
        assertEquals(Arrays.asList(Boolean.FALSE), listener.finished);
    }

    @Test
    public void practiceNeverSkipsEndsOrMovesTheRealRun() {
        controller.startPractice(TourRun.FIND_APPS);
        controller.skip();
        controller.back();
        assertEquals(TourRun.FIND_APPS, controller.currentStep().id);
        assertEquals(0, controller.currentStage());
        controller.endTour();
        assertFalse(controller.isRunning());
        assertFalse(controller.isFinished());
        assertEquals(0, prefs.writes);
    }

    @Test
    public void practisingALessonLeavesAFinishedRunFinished() {
        prefs.completedVersion = TourController.RUN_VERSION;
        prefs.writes = 0;
        assertTrue(controller.startPractice(TourRun.FIND_HELP));
        controller.finish();
        assertTrue(controller.isFinished());
        assertEquals(0, prefs.writes);
    }

    @Test
    public void practiceOnlyEverNamesALessonThatExists() {
        assertFalse(controller.startPractice("no_such_lesson"));
        assertFalse(controller.isRunning());
    }

    @Test
    public void practiceIsRefusedWhileARunIsUpAndLeavesItWhereItWas() {
        controller.start();
        clearTheCard();
        assertEquals(TourRun.FIND_APPS, controller.currentStep().id);
        doTheGesture();
        int stage = controller.currentStage();
        assertFalse(controller.startPractice(TourRun.KEYBOARD));
        assertFalse(controller.isPracticing());
        assertEquals(TourRun.FIND_APPS, controller.currentStep().id);
        assertEquals(stage, controller.currentStage());
    }

    @Test
    public void practiceIsStillAllowedWhileAnotherLessonIsBeingPractised() {
        assertTrue(controller.startPractice(TourRun.KEYBOARD));
        assertTrue(controller.startPractice(TourRun.FIND_ACTION));
        assertEquals(TourRun.FIND_ACTION, controller.currentStep().id);
    }

    // Resume.

    @Test
    public void resumeComesBackOnTheSameCardAndStage() {
        controller.start();
        doTheGesture();

        TourController restarted = newController();
        listener.clear();
        assertTrue(restarted.resumeIfInProgress());
        assertEquals(TourRun.FIND_HELP, restarted.currentStep().id);
        assertEquals(1, restarted.currentStage());
        assertEquals(TourRun.FIND_HELP + ":1", listener.shown.get(0));
    }

    @Test
    public void resumeArmsTheCardAgain() {
        controller.start();
        doTheGesture();

        controller = newController();
        controller.resumeIfInProgress();
        controller.onSignal(TourSignals.HELP_OPENED);
        assertEquals(1, controller.currentStage());
        arm();
        controller.onSignal(TourSignals.HELP_OPENED);
        assertEquals(2, controller.currentStage());
    }

    @Test
    public void resumeRemembersASkip() {
        controller.start();
        controller.skip();

        TourController restarted = newController();
        assertTrue(restarted.resumeIfInProgress());
        assertEquals(TourRun.FIND_APPS, restarted.currentStep().id);
        assertTrue(restarted.wasSkipped());
    }

    @Test
    public void thereIsNothingToResumeBeforeAnyRun() {
        assertFalse(controller.resumeIfInProgress());
        assertFalse(controller.isRunning());
        assertTrue(listener.shown.isEmpty());
    }

    @Test
    public void thereIsNothingToResumeAfterTheRunFinished() {
        controller.start();
        controller.finish();
        assertFalse(newController().resumeIfInProgress());
    }

    @Test
    public void resumeIgnoresAStoredCardThatNoLongerExists() {
        prefs.runVersion = TourController.RUN_VERSION;
        prefs.stepIndex = 97;
        prefs.stage = 4;
        assertFalse(controller.resumeIfInProgress());
    }

    @Test
    public void resumeClampsAStageThatWouldHaveNoSignalLeft() {
        prefs.runVersion = TourController.RUN_VERSION;
        prefs.stepIndex = 2;
        prefs.stage = 9;
        assertTrue(controller.resumeIfInProgress());
        assertEquals(TourRun.KEYBOARD, controller.currentStep().id);
        assertEquals(1, controller.currentStage());
    }

    @Test
    public void resumeDoesNothingWhileARunIsAlreadyUp() {
        controller.start();
        assertFalse(controller.resumeIfInProgress());
    }

    // The older run's progress.

    @Test
    public void theOlderRunsCardsMapOntoTheLessonThatCoversTheSameControl() {
        assertEquals(TourRun.FIND_HELP, TourController.migratedLessonFor(8));
        assertEquals(TourRun.FIND_APPS, TourController.migratedLessonFor(9));
        assertEquals(TourRun.FIND_APPS, TourController.migratedLessonFor(10));
        for (int keyboardCard = 3; keyboardCard <= 7; keyboardCard++)
            assertEquals("old card " + keyboardCard, TourRun.KEYBOARD,
                TourController.migratedLessonFor(keyboardCard));
        assertEquals(TourRun.FIND_ACTION, TourController.migratedLessonFor(11));
        // The status bar cards, the old closing card and anything out of range have no equivalent.
        for (int noEquivalent : new int[] {0, 1, 2, 12, -1, 99})
            assertNull("old card " + noEquivalent,
                TourController.migratedLessonFor(noEquivalent));
    }

    @Test
    public void anInterruptedOlderRunResumesOnTheLessonItMapsTo() {
        prefs.runVersion = 0;
        prefs.stepIndex = 9;
        prefs.stage = 1;
        assertTrue(controller.resumeIfInProgress());
        assertTrue(controller.isRunning());
        assertEquals(TourRun.FIND_APPS, controller.currentStep().id);
        // The lesson is entered at its first stage: the old stage counted other gestures.
        assertEquals(0, controller.currentStage());
        assertEquals(TourController.RUN_VERSION, prefs.runVersion);
        assertEquals(1, prefs.stepIndex);
        assertEquals(0, prefs.stage);
    }

    @Test
    public void anOlderCardWithNoEquivalentAsksTheUserWhatToDo() {
        prefs.runVersion = 0;
        prefs.stepIndex = 12;
        assertTrue(controller.resumeIfInProgress());
        assertTrue(controller.isAwaitingResumeChoice());
        assertFalse(controller.isRunning());
        assertNull(controller.currentStep());
        assertEquals(1, listener.resumeOrRestartAsked);
        assertTrue(listener.shown.isEmpty());
        // Nothing starts behind the question.
        assertFalse(controller.startIfNeeded());
        assertFalse(controller.resumeIfInProgress());
    }

    @Test
    public void bothAnswersToThatQuestionBeginAtTheFirstLesson() {
        prefs.runVersion = 0;
        prefs.stepIndex = 0;
        assertTrue(controller.resumeIfInProgress());
        assertTrue(controller.resumeChosen());
        assertFalse(controller.isAwaitingResumeChoice());
        assertEquals(TourRun.FIND_HELP, controller.currentStep().id);

        setUp();
        prefs.runVersion = 0;
        prefs.stepIndex = 2;
        assertTrue(controller.resumeIfInProgress());
        assertTrue(controller.restartChosen());
        assertEquals(TourRun.FIND_HELP, controller.currentStep().id);
        assertEquals(0, controller.currentStage());
    }

    @Test
    public void thereIsNothingToAnswerWhenNoQuestionWasAsked() {
        assertFalse(controller.resumeChosen());
        assertFalse(controller.restartChosen());
        assertFalse(controller.isRunning());
    }

    @Test
    public void aRunOfThisVersionIsNeverMigrated() {
        controller.start();
        controller.skip();
        controller.skip();
        assertEquals(TourRun.KEYBOARD, controller.currentStep().id);

        TourController restarted = newController();
        listener.clear();
        assertTrue(restarted.resumeIfInProgress());
        assertEquals(TourRun.KEYBOARD, restarted.currentStep().id);
        assertEquals(0, listener.resumeOrRestartAsked);
    }

    // Permissions are none of the run's business.

    @Test
    public void legacyOnboardingCountsWhenItsVersionIsMetAndThisTourIsUntouched() {
        assertTrue(TourController.legacyOnboardingCounts(2, 2, 0));
    }

    @Test
    public void legacyOnboardingDoesNotCountBelowItsRequiredVersion() {
        assertFalse(TourController.legacyOnboardingCounts(1, 2, 0));
    }

    @Test
    public void legacyOnboardingDoesNotCountOnceAnyRunIsAlreadyFinished() {
        assertFalse(TourController.legacyOnboardingCounts(2, 2, TourController.RUN_VERSION));
        assertFalse(TourController.legacyOnboardingCounts(2, 2, 1));
    }

    private static final class FakeClock implements TourController.Clock {
        private long now = 1_000L;

        void advance(long millis) {
            now += millis;
        }

        @Override
        public long nowMillis() {
            return now;
        }
    }

    /** Counts its writes, because "practice writes nothing" is most of what is asserted here. */
    private static final class FakePrefs implements TourController.Prefs {
        private int completedVersion;
        private int runVersion;
        private int stepIndex = -1;
        private int stage;
        private boolean skipped;
        private int writes;

        @Override
        public int getTourCompletedVersion() {
            return completedVersion;
        }

        @Override
        public void setTourCompletedVersion(int version) {
            writes++;
            completedVersion = version;
        }

        @Override
        public int getTourRunVersion() {
            return runVersion;
        }

        @Override
        public void setTourRunVersion(int version) {
            writes++;
            runVersion = version;
        }

        @Override
        public int getTourStepIndex() {
            return stepIndex;
        }

        @Override
        public void setTourStepIndex(int index) {
            writes++;
            stepIndex = index;
        }

        @Override
        public int getTourStepStage() {
            return stage;
        }

        @Override
        public void setTourStepStage(int value) {
            writes++;
            stage = value;
        }

        @Override
        public boolean getTourSkipped() {
            return skipped;
        }

        @Override
        public void setTourSkipped(boolean value) {
            writes++;
            skipped = value;
        }
    }

    private static final class RecordingListener implements TourController.Listener {
        private final List<String> shown = new ArrayList<>();
        private final List<Boolean> finished = new ArrayList<>();
        private final List<TourController.Choice> chosen = new ArrayList<>();
        private int resumeOrRestartAsked;

        void clear() {
            shown.clear();
            finished.clear();
            chosen.clear();
            resumeOrRestartAsked = 0;
        }

        @Override
        public void onTourStepShown(TourStep step, int stage) {
            shown.add(step.id + ":" + stage);
        }

        @Override
        public void onTourFinished(boolean skipped) {
            finished.add(skipped);
        }

        @Override
        public void onTourHomeChoice(TourController.Choice choice) {
            chosen.add(choice);
        }

        @Override
        public void onTourResumeOrRestart() {
            resumeOrRestartAsked++;
        }
    }
}
