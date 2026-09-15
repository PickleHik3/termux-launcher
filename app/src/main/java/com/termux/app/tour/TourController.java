package com.termux.app.tour;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * The run's state machine: which card is up, how far through it the user is, and whether the run
 * is over. Pure — it holds no views, reads no resources and knows the clock only through
 * {@link Clock}, so every transition below is a unit test rather than a phone.
 *
 * <p>Every move is written through to {@link Prefs} as it happens. The launcher is the home
 * screen: the process is killed and restarted under the user constantly, and a tour that restarted
 * from card one each time would be worse than no tour. {@link #resumeIfInProgress()} picks the run
 * back up on the card it was on.
 *
 * <p>The same machine runs a single lesson on its own — {@link #startPractice(String)} — for the
 * "Try it" in help. Practice writes nothing at all: a user who practises the keyboard lesson a
 * year later has not restarted, skipped or finished the first run, and must not be told they have.
 *
 * <p>Signals are ignored for {@link #ARM_DELAY_MS} after a card appears. The gesture that cleared
 * the previous card often lands its settle callback a frame or two later, and an un-armed card
 * would clear itself before the user had read it.
 */
public final class TourController {

    /** Bumped when the run changes enough that a run in progress has to be mapped onto the new one. */
    public static final int RUN_VERSION = 2;

    /** How long a freshly shown card ignores signals. */
    public static final long ARM_DELAY_MS = 400L;

    /** Nothing is running and no card has ever been shown. */
    private static final int STEP_NONE = -1;

    /** The buttons a practice hint offers instead of a lesson's Skip step and End tour. */
    private static final List<TourAction> PRACTICE_ACTIONS = Collections.unmodifiableList(
        Arrays.asList(TourAction.DONE, TourAction.END_PRACTICE));

    /** What the user answered on the home-screen card. */
    public enum Choice {
        /** Make the launcher the phone's home app. */
        USE_AS_HOME,
        /** Leave the home app as it is for now. */
        KEEP_TRYING,
        /** Nothing to decide: the launcher is already the home app. */
        CONTINUE
    }

    /**
     * Whether a completed run of some earlier once-per-install introduction should count as a
     * completed run of this tour, so an install that already sat through it is not shown this run
     * too. A pure function so the migration decision is a unit test rather than a device check.
     *
     * @param legacyCompletedVersion the version stamp the earlier introduction recorded, or 0
     * @param legacyRequiredVersion the version that earlier introduction considers "finished"
     * @param currentTourCompletedVersion this tour's own completed version right now
     */
    public static boolean legacyOnboardingCounts(int legacyCompletedVersion,
            int legacyRequiredVersion, int currentTourCompletedVersion) {
        return legacyCompletedVersion >= legacyRequiredVersion
            && currentTourCompletedVersion <= 0;
    }

    /**
     * The lesson that covers what card {@code versionOneStepIndex} of the thirteen-card run was
     * teaching, or null when nothing in this run covers it.
     *
     * <p>The pane corner card became the first half of "find help"; the drawer and the A–Z scrub
     * both became "find Android apps"; the whole keyboard chapter became "control the keyboard";
     * the palette card became "find an action". The two status-bar cards and the old closing card
     * have no equivalent at all — the run does not teach page swipes any more — and the user is
     * asked whether to pick the tour up or start it over instead of being dropped somewhere
     * arbitrary.
     */
    public static String migratedLessonFor(int versionOneStepIndex) {
        switch (versionOneStepIndex) {
            case 3: case 4: case 5: case 6: case 7:
                return TourRun.KEYBOARD;
            case 8:
                return TourRun.FIND_HELP;
            case 9: case 10:
                return TourRun.FIND_APPS;
            case 11:
                return TourRun.FIND_ACTION;
            default:
                return null;
        }
    }

    /** The clock, injected so tests can step it. */
    public interface Clock {
        long nowMillis();
    }

    /** The five things the run has to remember across a process death. */
    public interface Prefs {
        /** The {@link #RUN_VERSION} the user has finished, or 0. */
        int getTourCompletedVersion();

        void setTourCompletedVersion(int version);

        /**
         * The {@link #RUN_VERSION} the run in progress belongs to, or 0 for a run started before
         * the version was recorded. Without it a stored card number says nothing: card 3 of the
         * old run and card 3 of this one are different lessons.
         */
        int getTourRunVersion();

        void setTourRunVersion(int version);

        /** The card the run is on, or -1 when no run is in progress. */
        int getTourStepIndex();

        void setTourStepIndex(int index);

        /** How many of the current card's signals have landed. */
        int getTourStepStage();

        void setTourStepStage(int stage);

        /** Whether the user skipped past at least one card in this run. */
        boolean getTourSkipped();

        void setTourSkipped(boolean skipped);
    }

    /** What the overlay is told; every call is made after the prefs are written. */
    public interface Listener {
        /** Show this card at this stage. */
        void onTourStepShown(TourStep step, int stage);

        /** The run is over; take the overlay down. */
        void onTourFinished(boolean skipped);

        /**
         * The user answered the home-screen card. The run moves on either way; this is the part
         * only the launcher can do.
         */
        default void onTourHomeChoice(Choice choice) {}

        /**
         * A run from an older version of the tour was interrupted somewhere this run has no
         * equivalent for. Ask the user, then call {@link #resumeChosen()} or
         * {@link #restartChosen()}: both begin at the first lesson, and the difference is only
         * what the card says.
         */
        default void onTourResumeOrRestart() {}
    }

    private final List<TourStep> mSteps;
    private final Prefs mPrefs;
    private final Clock mClock;

    private Listener mListener;
    private int mStepIndex = STEP_NONE;
    private int mStage;
    private long mArmedAt;
    private boolean mRunning;
    /** Whether this is one lesson on its own, which writes nothing through to the prefs. */
    private boolean mPracticing;
    /** Whether the user has been asked to resume or restart and has not answered yet. */
    private boolean mAwaitingResumeChoice;

    public TourController(List<TourStep> steps, Prefs prefs, Clock clock) {
        mSteps = new ArrayList<>(steps);
        mPrefs = prefs;
        mClock = clock;
    }

    public void setListener(Listener listener) {
        mListener = listener;
    }

    /**
     * Whether the user has already been through a run. Any finished run counts, whatever version
     * it was: someone who sat through the thirteen-card run is not shown this one on upgrade.
     */
    public boolean isFinished() {
        return mPrefs.getTourCompletedVersion() >= 1;
    }

    /** Whether a card is up right now. */
    public boolean isRunning() {
        return mRunning;
    }

    /** Whether the card that is up is a single lesson practised from help. */
    public boolean isPracticing() {
        return mPracticing;
    }

    /** Whether the user is being asked to resume an older run or start this one over. */
    public boolean isAwaitingResumeChoice() {
        return mAwaitingResumeChoice;
    }

    /** The card that is up, or null. */
    public TourStep currentStep() {
        return mRunning && mStepIndex >= 0 && mStepIndex < mSteps.size()
            ? mSteps.get(mStepIndex) : null;
    }

    /** How many of the current card's signals have landed. */
    public int currentStage() {
        return mRunning ? mStage : 0;
    }

    /**
     * The buttons the card that is up offers. A lesson offers its own three; the same lesson
     * practised from help offers the two that leave practice without touching the run.
     */
    public List<TourAction> currentActions() {
        TourStep step = currentStep();
        if (step == null) return Collections.emptyList();
        return mPracticing ? PRACTICE_ACTIONS : step.actions();
    }

    /** Whether the run that is up, or the one that just ended, had a card skipped. */
    public boolean wasSkipped() {
        return mPrefs.getTourSkipped();
    }

    /** Starts at the first lesson, discarding any earlier run. This is what Replay does. */
    public boolean start() {
        if (mSteps.isEmpty()) return false;
        mPrefs.setTourSkipped(false);
        return startAt(mSteps.get(0).id);
    }

    /**
     * Starts a normal run at a named card, which is where it stays: everything after it follows in
     * order. Used by the version migration, and by anything that wants the run to begin at a
     * lesson other than the first.
     */
    public boolean startAt(String stepId) {
        int index = indexOf(stepId);
        if (index < 0) return false;
        mPracticing = false;
        mAwaitingResumeChoice = false;
        mRunning = true;
        mPrefs.setTourCompletedVersion(0);
        mPrefs.setTourRunVersion(RUN_VERSION);
        moveTo(index);
        return true;
    }

    /**
     * Shows one lesson on its own: help's "Try it". It clears on that lesson's own signals and
     * ends there, and writes nothing — not the completed version, not the card, not the stage and
     * not the skip flag — so practising a lesson can never finish, restart or skip the real run.
     */
    public boolean startPractice(String stepId) {
        int index = indexOf(stepId);
        if (index < 0) return false;
        mPracticing = true;
        mAwaitingResumeChoice = false;
        mRunning = true;
        mStepIndex = index;
        mStage = 0;
        mArmedAt = mClock.nowMillis();
        notifyStep();
        return true;
    }

    /** Starts a run unless this user has already finished one. */
    public boolean startIfNeeded() {
        return !isFinished() && !mRunning && !mAwaitingResumeChoice && start();
    }

    /**
     * Picks an unfinished run back up on its own card, mapping a run left over from an older
     * version of the tour onto the lesson that covers the same control.
     *
     * @return false when there is nothing to resume — no run was ever started, or the last one
     *     finished — in which case nothing is shown and nothing is written. True also covers the
     *     older run whose card has no equivalent here: the listener is asked to put the resume or
     *     restart question to the user.
     */
    public boolean resumeIfInProgress() {
        if (mRunning || mAwaitingResumeChoice || isFinished()) return false;
        int stored = mPrefs.getTourStepIndex();
        if (stored < 0) return false;
        if (mPrefs.getTourRunVersion() < RUN_VERSION) return resumeOlderRun(stored);
        if (stored >= mSteps.size()) return false;
        mPracticing = false;
        mRunning = true;
        mStepIndex = stored;
        mStage = clampStage(mSteps.get(stored), mPrefs.getTourStepStage());
        mArmedAt = mClock.nowMillis();
        notifyStep();
        return true;
    }

    private boolean resumeOlderRun(int storedStepIndex) {
        String lesson = migratedLessonFor(storedStepIndex);
        if (lesson != null) return startAt(lesson);
        mAwaitingResumeChoice = true;
        if (mListener != null) mListener.onTourResumeOrRestart();
        return true;
    }

    /** The user chose to pick the older run up: this run begins at its first lesson. */
    public boolean resumeChosen() {
        return answerResumeChoice();
    }

    /** The user chose to start over: the same first lesson, said differently. */
    public boolean restartChosen() {
        return answerResumeChoice();
    }

    private boolean answerResumeChoice() {
        if (!mAwaitingResumeChoice) return false;
        mAwaitingResumeChoice = false;
        return !mSteps.isEmpty() && startAt(mSteps.get(0).id);
    }

    /**
     * A gesture the launcher observed. Advances the card when it is the one being waited on, and
     * is otherwise ignored — including a signal that arrives while the card is still arming.
     */
    public void onSignal(String signalId) {
        TourStep step = currentStep();
        if (step == null || signalId == null) return;
        if (mClock.nowMillis() - mArmedAt < ARM_DELAY_MS) return;
        if (!signalId.equals(step.signalAt(mStage))) return;
        mStage++;
        if (mStage >= step.signalCount()) {
            advance();
        } else {
            if (!mPracticing) mPrefs.setTourStepStage(mStage);
            mArmedAt = mClock.nowMillis();
            notifyStep();
        }
    }

    /** The user answered the home-screen card. The run moves on whichever answer they gave. */
    public void choose(Choice choice) {
        TourStep step = currentStep();
        if (step == null || !step.isChoiceCard() || choice == null) return;
        if (mListener != null) mListener.onTourHomeChoice(choice);
        advance();
    }

    /** Back to the first stage of the lesson before this one; nothing to do on the first. */
    public void back() {
        if (!mRunning || mPracticing || mStepIndex <= 0) return;
        moveTo(mStepIndex - 1);
    }

    /** The Skip step button: this lesson is not for this user, move on. */
    public void skip() {
        if (!mRunning || mPracticing) return;
        mPrefs.setTourSkipped(true);
        advance();
    }

    /** The End tour button: the rest of the run is not wanted, and the run counts as skipped. */
    public void endTour() {
        if (!mRunning) return;
        if (mPracticing) {
            endPractice();
            return;
        }
        mPrefs.setTourSkipped(true);
        end();
    }

    /** The End practice button, and the Done beside it: both leave without writing anything. */
    public void endPractice() {
        if (!mRunning || !mPracticing) return;
        finishPractice();
    }

    /** The closing card's action, and anything else that ends the run deliberately. */
    public void finish() {
        if (!mRunning) return;
        if (mPracticing) finishPractice();
        else end();
    }

    private int indexOf(String stepId) {
        if (stepId == null) return -1;
        for (int i = 0; i < mSteps.size(); i++)
            if (stepId.equals(mSteps.get(i).id)) return i;
        return -1;
    }

    private void advance() {
        if (mPracticing) {
            finishPractice();
        } else if (mStepIndex + 1 >= mSteps.size()) {
            end();
        } else {
            moveTo(mStepIndex + 1);
        }
    }

    private void moveTo(int index) {
        mStepIndex = index;
        mStage = 0;
        mArmedAt = mClock.nowMillis();
        if (!mPracticing) {
            mPrefs.setTourStepIndex(index);
            mPrefs.setTourStepStage(0);
        }
        notifyStep();
    }

    private void end() {
        mRunning = false;
        mPracticing = false;
        mStepIndex = STEP_NONE;
        mStage = 0;
        mPrefs.setTourStepIndex(STEP_NONE);
        mPrefs.setTourStepStage(0);
        mPrefs.setTourRunVersion(RUN_VERSION);
        mPrefs.setTourCompletedVersion(RUN_VERSION);
        if (mListener != null) mListener.onTourFinished(mPrefs.getTourSkipped());
    }

    /** The way out of practice: the overlay comes down and the stored run is left exactly as it was. */
    private void finishPractice() {
        mRunning = false;
        mPracticing = false;
        mStepIndex = STEP_NONE;
        mStage = 0;
        if (mListener != null) mListener.onTourFinished(false);
    }

    private void notifyStep() {
        TourStep step = currentStep();
        if (step != null && mListener != null) mListener.onTourStepShown(step, mStage);
    }

    private static int clampStage(TourStep step, int stage) {
        if (stage <= 0) return 0;
        return Math.min(stage, Math.max(0, step.signalCount() - 1));
    }
}
