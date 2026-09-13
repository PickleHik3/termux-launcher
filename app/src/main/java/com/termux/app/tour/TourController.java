package com.termux.app.tour;

import java.util.ArrayList;
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
 * <p>Signals are ignored for {@link #ARM_DELAY_MS} after a card appears. The gesture that cleared
 * the previous card often lands its settle callback a frame or two later, and an un-armed card
 * would clear itself before the user had read it.
 */
public final class TourController {

    /** Bumped when the run changes enough that someone who finished the old one should see it. */
    public static final int RUN_VERSION = 1;

    /** How long a freshly shown card ignores signals. */
    public static final long ARM_DELAY_MS = 400L;

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
            && currentTourCompletedVersion < RUN_VERSION;
    }

    /** Nothing is running and no card has ever been shown. */
    private static final int STEP_NONE = -1;

    /** The clock, injected so tests can step it. */
    public interface Clock {
        long nowMillis();
    }

    /** The four things the run has to remember across a process death. */
    public interface Prefs {
        /** The {@link #RUN_VERSION} the user has finished, or 0. */
        int getTourCompletedVersion();

        void setTourCompletedVersion(int version);

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

    /** What the overlay is told; both calls are made after the prefs are written. */
    public interface Listener {
        /** Show this card at this stage. */
        void onTourStepShown(TourStep step, int stage);

        /** The run is over; take the overlay down. */
        void onTourFinished(boolean skipped);
    }

    private final List<TourStep> mSteps;
    private final Prefs mPrefs;
    private final Clock mClock;

    private Listener mListener;
    private int mStepIndex = STEP_NONE;
    private int mStage;
    private long mArmedAt;
    private boolean mRunning;

    public TourController(List<TourStep> steps, Prefs prefs, Clock clock) {
        mSteps = new ArrayList<>(steps);
        mPrefs = prefs;
        mClock = clock;
    }

    public void setListener(Listener listener) {
        mListener = listener;
    }

    /** Whether the user has already been through a run of this version. */
    public boolean isFinished() {
        return mPrefs.getTourCompletedVersion() >= RUN_VERSION;
    }

    /** Whether a card is up right now. */
    public boolean isRunning() {
        return mRunning;
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

    /** Whether the run that is up, or the one that just ended, had a card skipped. */
    public boolean wasSkipped() {
        return mPrefs.getTourSkipped();
    }

    /** Starts at card one, discarding any earlier run. This is what Replay does. */
    public boolean start() {
        if (mSteps.isEmpty()) return false;
        mPrefs.setTourCompletedVersion(0);
        mPrefs.setTourSkipped(false);
        mRunning = true;
        moveTo(0);
        return true;
    }

    /** Starts a run unless this user has already finished one. */
    public boolean startIfNeeded() {
        return !isFinished() && !mRunning && start();
    }

    /**
     * Picks an unfinished run back up on its own card.
     *
     * @return false when there is nothing to resume — no run was ever started, or the last one
     *     finished — in which case nothing is shown and nothing is written.
     */
    public boolean resumeIfInProgress() {
        if (mRunning || isFinished()) return false;
        int stored = mPrefs.getTourStepIndex();
        if (stored < 0 || stored >= mSteps.size()) return false;
        mRunning = true;
        mStepIndex = stored;
        mStage = clampStage(mSteps.get(stored), mPrefs.getTourStepStage());
        mArmedAt = mClock.nowMillis();
        notifyStep();
        return true;
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
            mPrefs.setTourStepStage(mStage);
            mArmedAt = mClock.nowMillis();
            notifyStep();
        }
    }

    /** The Skip button: this card is not for this user, move on. */
    public void skip() {
        if (!mRunning) return;
        mPrefs.setTourSkipped(true);
        advance();
    }

    /** The closing card's Done button, and anything else that ends the run deliberately. */
    public void finish() {
        if (!mRunning) return;
        end();
    }

    private void advance() {
        if (mStepIndex + 1 >= mSteps.size()) {
            end();
        } else {
            moveTo(mStepIndex + 1);
        }
    }

    private void moveTo(int index) {
        mStepIndex = index;
        mStage = 0;
        mArmedAt = mClock.nowMillis();
        mPrefs.setTourStepIndex(index);
        mPrefs.setTourStepStage(0);
        notifyStep();
    }

    private void end() {
        mRunning = false;
        mStepIndex = STEP_NONE;
        mStage = 0;
        mPrefs.setTourStepIndex(STEP_NONE);
        mPrefs.setTourStepStage(0);
        mPrefs.setTourCompletedVersion(RUN_VERSION);
        if (mListener != null) mListener.onTourFinished(mPrefs.getTourSkipped());
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
