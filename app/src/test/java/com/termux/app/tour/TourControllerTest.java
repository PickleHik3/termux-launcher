package com.termux.app.tour;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.termux.app.place.PlaceLayout;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Every transition of the run, on a fake clock and a fake store, over the real run: the sheet,
 * six lessons, the "Got it" between them and the closing card.
 *
 * <p>What matters most on a phone: the arming window, resume — the launcher is killed and
 * restarted under the user constantly — the version rule, which must never offer the run again to
 * someone who has already said yes or no to one, and practice, which must leave every stored value
 * exactly as it found them.
 */
public class TourControllerTest {

    private static final TourRun.RunContext PHONE = new TourRun.RunContext(PlaceLayout.Edge.BOTTOM);

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

    private static int cardNumber(String id) {
        List<TourStep> steps = TourRun.steps(PHONE);
        for (int i = 0; i < steps.size(); i++)
            if (steps.get(i).id.equals(id)) return i;
        throw new AssertionError("no card " + id + " in the run");
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

    /** Clears every stage of the lesson that is up and lets the "Got it" pass. */
    private void clearTheLesson() {
        TourStep step = controller.currentStep();
        int stages = step.stageCount();
        for (int i = 0; i < stages; i++) doTheGesture();
        assertTrue(controller.isCompleting());
        controller.continueAfterCompletion();
    }

    private String current() {
        TourStep step = controller.currentStep();
        return step == null ? null : step.id + ":" + controller.currentStage();
    }

    // ---- the sheet -------------------------------------------------------------------------------

    @Test public void aFreshInstallIsOfferedTheRunOnTheSheet() {
        assertTrue(controller.isOffered());
        assertTrue(controller.startIfNeeded());
        assertTrue(controller.isShowingSetup());
        assertTrue(controller.isRunning());
        assertNull("the sheet is not a card", controller.currentStep());
        assertEquals(1, listener.setupShown);
        assertEquals(-1, prefs.stepIndex);
    }

    @Test public void showMeAroundBeginsAtTheFirstLesson() {
        controller.start();
        assertTrue(controller.showMeAround());
        assertFalse(controller.isShowingSetup());
        assertEquals(TourRun.BORDER_DRAG + ":0", current());
        assertEquals(cardNumber(TourRun.BORDER_DRAG), prefs.stepIndex);
        assertEquals(TourController.RUN_VERSION, prefs.runVersion);
        assertEquals(0, prefs.completedVersion);
    }

    @Test public void skipTheTourGoesStraightToTheClosingCard() {
        controller.start();
        assertTrue(controller.skipTheTour());
        assertEquals(TourRun.CLOSING + ":0", current());
        assertTrue(controller.wasSkipped());
        controller.finish();
        assertFalse(controller.isRunning());
        assertEquals(TourController.RUN_VERSION, prefs.completedVersion);
        assertEquals(Arrays.asList(true), listener.finished);
    }

    @Test public void theSheetsButtonsDoNothingOnceItIsLeft() {
        controller.start();
        controller.showMeAround();
        assertFalse(controller.showMeAround());
        assertFalse(controller.skipTheTour());
        assertEquals(TourRun.BORDER_DRAG + ":0", current());
    }

    // ---- lessons ---------------------------------------------------------------------------------

    @Test public void aClearedLessonSaysGotItBeforeTheRunMovesOn() {
        controller.start();
        controller.showMeAround();
        doTheGesture();
        assertTrue(controller.isCompleting());
        assertEquals(Arrays.asList(TourRun.BORDER_DRAG), listener.completed);
        // Still the same card while it says so, and the stored card has already moved on.
        assertEquals(TourRun.BORDER_DRAG + ":1", current());
        assertEquals(cardNumber(TourRun.KEYBOARD), prefs.stepIndex);
        controller.continueAfterCompletion();
        assertEquals(TourRun.KEYBOARD + ":0", current());
        assertFalse(controller.isCompleting());
    }

    @Test public void aTwoStageLessonSwapsWithoutCelebratingItsFirstHalf() {
        controller.start();
        controller.showMeAround();
        clearTheLesson();
        assertEquals(TourRun.KEYBOARD + ":0", current());
        arm();
        controller.onSignal(TourSignals.KEYBOARD_HIDDEN);
        assertEquals(TourRun.KEYBOARD + ":1", current());
        assertFalse(controller.isCompleting());
        assertEquals(Arrays.asList(TourRun.BORDER_DRAG), listener.completed);
        assertEquals(1, prefs.stage);
        arm();
        controller.onSignal(TourSignals.KEYBOARD_SHOWN);
        assertTrue(controller.isCompleting());
    }

    @Test public void theHelpLessonSwapsFromTheCornerToTheQuestionMarkWithNoGotIt() {
        controller.startAt(TourRun.FIND_HELP);
        arm();
        controller.onSignal(TourSignals.PANE_CORNER_MENU);
        assertEquals(TourRun.FIND_HELP + ":1", current());
        assertTrue(listener.completed.isEmpty());
        arm();
        controller.onSignal(TourSignals.HELP_OPENED);
        assertTrue(controller.isCompleting());
        controller.continueAfterCompletion();
        assertEquals(TourRun.CLOSING + ":0", current());
    }

    @Test public void theWholeRunWalksSixLessonsToTheClosingCard() {
        controller.start();
        controller.showMeAround();
        for (int i = 0; i < 6; i++) clearTheLesson();
        assertEquals(TourRun.CLOSING + ":0", current());
        assertEquals(TourRun.lessons(), listener.completed);
        controller.finish();
        assertEquals(TourController.RUN_VERSION, prefs.completedVersion);
        assertEquals(Arrays.asList(false), listener.finished);
    }

    @Test public void signalsWhileArmingWhileSayingGotItOrOutOfTurnAreIgnored() {
        controller.start();
        controller.showMeAround();
        controller.onSignal(TourSignals.PLACE_CHANGED);
        assertEquals("still arming", TourRun.BORDER_DRAG + ":0", current());
        arm();
        controller.onSignal(TourSignals.KEYBOARD_HIDDEN);
        assertEquals("not this card's", TourRun.BORDER_DRAG + ":0", current());
        controller.onSignal(TourSignals.PLACE_CHANGED);
        assertTrue(controller.isCompleting());
        arm();
        controller.onSignal(TourSignals.KEYBOARD_HIDDEN);
        assertEquals("still saying it", 1, listener.completed.size());
        assertTrue(controller.isCompleting());
    }

    @Test public void skipPassesALessonAndCountsTheRunAsSkipped() {
        controller.start();
        controller.showMeAround();
        controller.skip();
        assertEquals(TourRun.KEYBOARD + ":0", current());
        assertTrue(controller.wasSkipped());
        assertTrue(listener.completed.isEmpty());
    }

    @Test public void skipIsIgnoredWhileSayingGotIt() {
        controller.start();
        controller.showMeAround();
        doTheGesture();
        controller.skip();
        assertEquals(TourRun.BORDER_DRAG + ":1", current());
        assertFalse(controller.wasSkipped());
    }

    @Test public void closeJumpsToTheClosingCard() {
        controller.start();
        controller.showMeAround();
        controller.endTour();
        assertEquals(TourRun.CLOSING + ":0", current());
        assertTrue(controller.wasSkipped());
        assertEquals(cardNumber(TourRun.CLOSING), prefs.stepIndex);
        // The closing card has no ✕ of its own to move on to.
        controller.endTour();
        assertEquals(TourRun.CLOSING + ":0", current());
    }

    // ---- a phone with one place ------------------------------------------------------------------

    @Test public void aDroppedLessonIsWalkedPast() {
        controller.dropStep(TourRun.BORDER_DRAG);
        controller.start();
        controller.showMeAround();
        assertEquals(TourRun.KEYBOARD + ":0", current());
        assertFalse(controller.startPractice(TourRun.BORDER_DRAG));
    }

    // ---- resume and the version rule -------------------------------------------------------------

    @Test public void aRunIsPickedUpOnItsCardFromItsFirstHalf() {
        controller.start();
        controller.showMeAround();
        clearTheLesson();
        arm();
        controller.onSignal(TourSignals.KEYBOARD_HIDDEN);
        assertEquals(1, prefs.stage);

        controller = newController();
        listener.shown.clear();
        assertTrue(controller.resumeIfInProgress());
        assertEquals(TourRun.KEYBOARD + ":0", current());
        assertEquals(Arrays.asList(TourRun.KEYBOARD + ":0"), listener.shown);
        assertEquals("the sheet is not shown again", 1, listener.setupShown);
    }

    @Test public void aDeathWhileSayingGotItResumesOnTheNextLesson() {
        controller.start();
        controller.showMeAround();
        doTheGesture();
        controller = newController();
        assertTrue(controller.resumeIfInProgress());
        assertEquals(TourRun.KEYBOARD + ":0", current());
    }

    @Test public void anOlderRunInProgressRestartsAtTheFirstLessonWithoutTheSheet() {
        prefs.runVersion = TourController.RUN_VERSION - 1;
        prefs.stepIndex = 4;
        prefs.stage = 1;
        assertTrue(controller.resumeIfInProgress());
        assertEquals(TourRun.BORDER_DRAG + ":0", current());
        assertEquals(0, listener.setupShown);
        assertEquals(TourController.RUN_VERSION, prefs.runVersion);
    }

    @Test public void nobodyWhoFinishedOrSkippedAnEarlierRunIsOfferedThisOne() {
        for (int version = 1; version <= TourController.RUN_VERSION; version++) {
            prefs.completedVersion = version;
            assertFalse("finished " + version, controller.isOffered());
            assertFalse(controller.startIfNeeded());
            assertFalse(controller.resumeIfInProgress());
        }
        assertTrue(TourController.isOfferedTo(0));
        assertFalse(TourController.isOfferedTo(TourController.RUN_VERSION - 1));
        assertEquals(0, listener.setupShown);
    }

    @Test public void replayRunsTheWholeRunFromTheSheetForSomeoneWhoFinished() {
        prefs.completedVersion = TourController.RUN_VERSION - 1;
        assertTrue(controller.start());
        assertTrue(controller.isShowingSetup());
        controller.showMeAround();
        assertEquals(TourRun.BORDER_DRAG + ":0", current());
    }

    @Test public void nothingToResumeWritesNothing() {
        assertFalse(controller.resumeIfInProgress());
        assertEquals(0, prefs.writes);
    }

    @Test public void theLegacyOnboardingCountsOnlyForAnInstallThatNeverFinishedThisTour() {
        assertTrue(TourController.legacyOnboardingCounts(2, 2, 0));
        assertFalse(TourController.legacyOnboardingCounts(1, 2, 0));
        assertFalse(TourController.legacyOnboardingCounts(2, 2, 3));
    }

    // ---- practice --------------------------------------------------------------------------------

    @Test public void practiceRunsOneLessonAndWritesNothing() {
        prefs.completedVersion = TourController.RUN_VERSION;
        int writes = prefs.writes;
        assertTrue(controller.startPractice(TourRun.FIND_HELP));
        assertTrue(controller.isPracticing());
        assertEquals(TourRun.FIND_HELP + ":0", current());
        arm();
        controller.onSignal(TourSignals.PANE_CORNER_MENU);
        arm();
        controller.onSignal(TourSignals.HELP_OPENED);
        assertTrue(controller.isCompleting());
        controller.continueAfterCompletion();
        assertFalse(controller.isRunning());
        assertEquals(Arrays.asList(false), listener.finished);
        assertEquals(writes, prefs.writes);
        assertEquals(TourController.RUN_VERSION, prefs.completedVersion);
    }

    @Test public void closeInPracticeLeavesWithoutWriting() {
        int writes = prefs.writes;
        controller.startPractice(TourRun.KEYBOARD);
        controller.endTour();
        assertFalse(controller.isRunning());
        assertEquals(writes, prefs.writes);
    }

    @Test public void practiceIsRefusedWhileARunIsUpAndForTheClosingCard() {
        assertFalse(controller.startPractice(TourRun.CLOSING));
        assertFalse(controller.startPractice("pin_apps"));
        controller.start();
        controller.showMeAround();
        assertFalse(controller.startPractice(TourRun.KEYBOARD));
        assertEquals(TourRun.BORDER_DRAG + ":0", current());
    }

    // ---- progress --------------------------------------------------------------------------------

    @Test public void theProgressFollowsTheRun() {
        controller.start();
        controller.showMeAround();
        clearTheLesson();
        TourProgress progress = controller.currentProgress();
        assertEquals(1, progress.chapterNumber);
        assertEquals(3, progress.chapterCount);
        assertEquals(Arrays.asList(TourProgress.Segment.DONE, TourProgress.Segment.CURRENT),
            progress.groups.get(0));
        doTheGesture();
        doTheGesture();
        assertEquals("the cleared lesson shows as done while it says so",
            Arrays.asList(TourProgress.Segment.DONE, TourProgress.Segment.DONE),
            controller.currentProgress().groups.get(0));
    }

    // ---- fakes -----------------------------------------------------------------------------------

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

        @Override public int getTourCompletedVersion() {
            return completedVersion;
        }

        @Override public void setTourCompletedVersion(int version) {
            writes++;
            completedVersion = version;
        }

        @Override public int getTourRunVersion() {
            return runVersion;
        }

        @Override public void setTourRunVersion(int version) {
            writes++;
            runVersion = version;
        }

        @Override public int getTourStepIndex() {
            return stepIndex;
        }

        @Override public void setTourStepIndex(int index) {
            writes++;
            stepIndex = index;
        }

        @Override public int getTourStepStage() {
            return stage;
        }

        @Override public void setTourStepStage(int stage) {
            writes++;
            this.stage = stage;
        }

        @Override public boolean getTourSkipped() {
            return skipped;
        }

        @Override public void setTourSkipped(boolean skipped) {
            writes++;
            this.skipped = skipped;
        }
    }

    private static final class RecordingListener implements TourController.Listener {
        private final List<String> shown = new ArrayList<>();
        private final List<String> completed = new ArrayList<>();
        private final List<Boolean> finished = new ArrayList<>();
        private int setupShown;

        @Override public void onTourSetupShown() {
            setupShown++;
        }

        @Override public void onTourStepShown(TourStep step, int stage) {
            shown.add(step.id + ":" + stage);
        }

        @Override public void onTourStepCompleted(TourStep step) {
            completed.add(step.id);
        }

        @Override public void onTourFinished(boolean skipped) {
            finished.add(skipped);
        }
    }
}
