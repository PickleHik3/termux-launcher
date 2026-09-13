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
 * Every transition of the run, on a fake clock and a fake store.
 *
 * <p>The two that matter most on a phone are the arming window — the settle callback of the
 * gesture that cleared the last card arrives a frame after the next card appears, and must not
 * clear it — and resume, because the launcher is killed and restarted under the user constantly.
 */
public class TourControllerTest {

    private static final String SIGNAL_A = "a";
    private static final String SIGNAL_B = "b";
    private static final String SIGNAL_C = "c";

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
        TourController fresh = new TourController(threeSteps(), prefs, clock);
        fresh.setListener(listener);
        return fresh;
    }

    /** Two two-signal cards and a closing card with none, the shape of the real run. */
    private static List<TourStep> threeSteps() {
        return Arrays.asList(
            new TourStep("one", 1, 2, TourTargets.STATUS_BAR,
                new String[] {SIGNAL_A, SIGNAL_B},
                new TourGesture[] {TourGesture.SWIPE_LEFT, TourGesture.SWIPE_RIGHT}),
            new TourStep("two", 3, 0, TourTargets.STATUS_BAR,
                new String[] {SIGNAL_C},
                new TourGesture[] {TourGesture.DRAG_DOWN}),
            new TourStep("closing", 4, 0, "",
                new String[] {}, new TourGesture[] {TourGesture.NONE}));
    }

    private void arm() {
        clock.advance(TourController.ARM_DELAY_MS);
    }

    // Starting.

    @Test
    public void startShowsTheFirstCard() {
        assertTrue(controller.start());
        assertTrue(controller.isRunning());
        assertEquals("one", controller.currentStep().id);
        assertEquals(0, controller.currentStage());
        assertEquals(1, listener.shown.size());
        assertEquals("one:0", listener.shown.get(0));
        assertEquals(0, prefs.stepIndex);
        assertEquals(0, prefs.stage);
    }

    @Test
    public void startIfNeededDoesNothingForSomeoneWhoFinished() {
        prefs.completedVersion = TourController.RUN_VERSION;
        assertFalse(controller.startIfNeeded());
        assertFalse(controller.isRunning());
        assertTrue(listener.shown.isEmpty());
    }

    @Test
    public void startIfNeededRunsForAFreshInstall() {
        assertTrue(controller.startIfNeeded());
        assertTrue(controller.isRunning());
    }

    @Test
    public void startIfNeededDoesNotRestartARunThatIsUp() {
        assertTrue(controller.startIfNeeded());
        arm();
        controller.onSignal(SIGNAL_A);
        assertFalse(controller.startIfNeeded());
        assertEquals(1, controller.currentStage());
    }

    @Test
    public void replayStartsOverForSomeoneWhoFinished() {
        prefs.completedVersion = TourController.RUN_VERSION;
        prefs.skipped = true;
        assertTrue(controller.start());
        assertEquals("one", controller.currentStep().id);
        assertEquals(0, prefs.completedVersion);
        assertFalse(prefs.skipped);
    }

    // Signals.

    @Test
    public void aSignalWhileTheCardIsArmingIsIgnored() {
        controller.start();
        clock.advance(TourController.ARM_DELAY_MS - 1);
        controller.onSignal(SIGNAL_A);
        assertEquals(0, controller.currentStage());
    }

    @Test
    public void theExpectedSignalAdvancesToTheSecondLine() {
        controller.start();
        arm();
        controller.onSignal(SIGNAL_A);
        assertEquals(1, controller.currentStage());
        assertTrue(controller.currentStep().showsSecondLineAt(controller.currentStage()));
        assertEquals(1, prefs.stage);
        assertEquals("one:1", listener.shown.get(1));
    }

    @Test
    public void anOutOfOrderSignalIsIgnored() {
        controller.start();
        arm();
        controller.onSignal(SIGNAL_B);
        assertEquals(0, controller.currentStage());
        controller.onSignal("something.else");
        assertEquals(0, controller.currentStage());
        controller.onSignal(null);
        assertEquals(0, controller.currentStage());
    }

    @Test
    public void theSecondStageRearmsSoTheSameGestureCannotClearBothHalves() {
        controller.start();
        arm();
        controller.onSignal(SIGNAL_A);
        controller.onSignal(SIGNAL_B);
        assertEquals("one", controller.currentStep().id);
        assertEquals(1, controller.currentStage());
    }

    @Test
    public void clearingEveryStageMovesToTheNextCard() {
        controller.start();
        arm();
        controller.onSignal(SIGNAL_A);
        arm();
        controller.onSignal(SIGNAL_B);
        assertEquals("two", controller.currentStep().id);
        assertEquals(0, controller.currentStage());
        assertEquals(1, prefs.stepIndex);
        assertEquals(0, prefs.stage);
        assertFalse(prefs.skipped);
    }

    @Test
    public void aCardWithNoSignalsIsNotClearedByOne() {
        controller.start();
        controller.skip();
        controller.skip();
        assertEquals("closing", controller.currentStep().id);
        arm();
        controller.onSignal(SIGNAL_A);
        assertTrue(controller.isRunning());
        assertEquals("closing", controller.currentStep().id);
    }

    // Skip and finish.

    @Test
    public void skipMovesToTheNextCardAndIsRemembered() {
        controller.start();
        controller.skip();
        assertEquals("two", controller.currentStep().id);
        assertTrue(prefs.skipped);
        assertTrue(controller.wasSkipped());
    }

    @Test
    public void skippingTheLastCardEndsTheRun() {
        controller.start();
        controller.skip();
        controller.skip();
        controller.skip();
        assertFalse(controller.isRunning());
        assertNull(controller.currentStep());
        assertEquals(TourController.RUN_VERSION, prefs.completedVersion);
        assertEquals(-1, prefs.stepIndex);
        assertEquals(1, listener.finished.size());
        assertTrue(listener.finished.get(0));
    }

    @Test
    public void finishEndsTheRunUnskipped() {
        controller.start();
        arm();
        controller.onSignal(SIGNAL_A);
        arm();
        controller.onSignal(SIGNAL_B);
        arm();
        controller.onSignal(SIGNAL_C);
        assertEquals("closing", controller.currentStep().id);
        controller.finish();
        assertFalse(controller.isRunning());
        assertTrue(controller.isFinished());
        assertEquals(1, listener.finished.size());
        assertFalse(listener.finished.get(0));
    }

    @Test
    public void nothingHappensAfterTheRunEnds() {
        controller.start();
        controller.finish();
        int shown = listener.shown.size();
        arm();
        controller.onSignal(SIGNAL_A);
        controller.skip();
        controller.finish();
        assertEquals(shown, listener.shown.size());
        assertEquals(1, listener.finished.size());
    }

    // Resume.

    @Test
    public void resumeComesBackOnTheSameCardAndStage() {
        controller.start();
        arm();
        controller.onSignal(SIGNAL_A);

        TourController restarted = newController();
        listener.clear();
        assertTrue(restarted.resumeIfInProgress());
        assertEquals("one", restarted.currentStep().id);
        assertEquals(1, restarted.currentStage());
        assertEquals("one:1", listener.shown.get(0));
    }

    @Test
    public void resumeArmsTheCardAgain() {
        controller.start();
        arm();
        controller.onSignal(SIGNAL_A);

        TourController restarted = newController();
        restarted.resumeIfInProgress();
        controller = restarted;
        controller.onSignal(SIGNAL_B);
        assertEquals(1, controller.currentStage());
        arm();
        controller.onSignal(SIGNAL_B);
        assertEquals("two", controller.currentStep().id);
    }

    @Test
    public void resumeRemembersASkip() {
        controller.start();
        controller.skip();

        TourController restarted = newController();
        assertTrue(restarted.resumeIfInProgress());
        assertEquals("two", restarted.currentStep().id);
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

        TourController restarted = newController();
        assertFalse(restarted.resumeIfInProgress());
        assertFalse(restarted.isRunning());
    }

    @Test
    public void resumeIgnoresAStoredCardThatNoLongerExists() {
        prefs.stepIndex = 97;
        prefs.stage = 4;
        assertFalse(controller.resumeIfInProgress());
    }

    @Test
    public void resumeClampsAStageThatWouldHaveNoSignalLeft() {
        prefs.stepIndex = 1;
        prefs.stage = 9;
        assertTrue(controller.resumeIfInProgress());
        assertEquals(0, controller.currentStage());
    }

    @Test
    public void resumeDoesNothingWhileARunIsAlreadyUp() {
        controller.start();
        assertFalse(controller.resumeIfInProgress());
    }

    @Test
    public void legacyOnboardingCountsWhenItsVersionIsMetAndThisTourIsUntouched() {
        assertTrue(TourController.legacyOnboardingCounts(2, 2, 0));
    }

    @Test
    public void legacyOnboardingDoesNotCountBelowItsRequiredVersion() {
        assertFalse(TourController.legacyOnboardingCounts(1, 2, 0));
    }

    @Test
    public void legacyOnboardingDoesNotCountOnceThisTourIsAlreadyFinished() {
        assertFalse(TourController.legacyOnboardingCounts(2, 2, TourController.RUN_VERSION));
    }

    @Test
    public void legacyOnboardingDoesNotCountWhileAReplayHasZeroedThisTourBackDeliberately() {
        // Same shape as after start()/restart(): completed version is 0 again. The caller must
        // never re-evaluate this after the one-time migration flag is set, which is why the flag
        // — not this function re-run against live state — is what actually guards the migration.
        assertTrue(TourController.legacyOnboardingCounts(2, 2, 0));
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

    private static final class FakePrefs implements TourController.Prefs {
        private int completedVersion;
        private int stepIndex = -1;
        private int stage;
        private boolean skipped;

        @Override
        public int getTourCompletedVersion() {
            return completedVersion;
        }

        @Override
        public void setTourCompletedVersion(int version) {
            completedVersion = version;
        }

        @Override
        public int getTourStepIndex() {
            return stepIndex;
        }

        @Override
        public void setTourStepIndex(int index) {
            stepIndex = index;
        }

        @Override
        public int getTourStepStage() {
            return stage;
        }

        @Override
        public void setTourStepStage(int value) {
            stage = value;
        }

        @Override
        public boolean getTourSkipped() {
            return skipped;
        }

        @Override
        public void setTourSkipped(boolean value) {
            skipped = value;
        }
    }

    private static final class RecordingListener implements TourController.Listener {
        private final List<String> shown = new ArrayList<>();
        private final List<Boolean> finished = new ArrayList<>();

        void clear() {
            shown.clear();
            finished.clear();
        }

        @Override
        public void onTourStepShown(TourStep step, int stage) {
            shown.add(step.id + ":" + stage);
        }

        @Override
        public void onTourFinished(boolean skipped) {
            finished.add(skipped);
        }
    }
}
