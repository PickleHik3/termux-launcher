package com.termux.app.tour;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The run's state machine: whether the setup sheet is up, which card is up, how far through it the
 * user is, and whether the run is over. Pure — it holds no views, reads no resources and knows the
 * clock only through {@link Clock}, so every transition below is a unit test rather than a phone.
 *
 * <p>Every move is written through to {@link Prefs} as it happens. The launcher is the home
 * screen: the process is killed and restarted under the user constantly, and a run that restarted
 * from the sheet each time would be worse than no run. {@link #resumeIfInProgress()} picks the run
 * back up on the card it was on.
 *
 * <p>A lesson's last gesture does not move the run on by itself. The card says "Got it" first —
 * {@link Listener#onTourStepCompleted} — and the host calls {@link #continueAfterCompletion()}
 * once it has; the stored card has already moved on, so a process death in between resumes on the
 * next lesson rather than repeating this one.
 *
 * <p>The same machine runs a single lesson on its own — {@link #startPractice(String)} — for the
 * "Try it" in help. Practice writes nothing at all.
 *
 * <p>Signals are ignored for {@link #ARM_DELAY_MS} after a card appears. The gesture that cleared
 * the previous card often lands its settle callback a frame or two later, and an un-armed card
 * would clear itself before the user had read it.
 */
public final class TourController {

    /** Bumped when the run changes enough that a run in progress has to be restarted on this one. */
    public static final int RUN_VERSION = 7;

    /**
     * The run before the welcome card. It is what the legacy migration records, so an install
     * that only ever sat through an older introduction counts as having finished one.
     */
    public static final int VERSION_BEFORE_THE_WELCOME_CARD = 3;

    /** How long a freshly shown card ignores signals. */
    public static final long ARM_DELAY_MS = 400L;

    /** Nothing is running and no card has ever been shown. */
    private static final int STEP_NONE = -1;

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
     * Whether the run is offered on its own: only to someone who has never finished or skipped any
     * version of it. A new version of the run is not a reason to stop someone who already said
     * yes or no to the last one; Settings › Replay tour is how they see it.
     *
     * @param completedVersion the version the user last finished or skipped, or 0
     */
    public static boolean isOfferedTo(int completedVersion) {
        return completedVersion <= 0;
    }

    /** The clock, injected so tests can step it. */
    public interface Clock {
        long nowMillis();
    }

    /** The five things the run has to remember across a process death. */
    public interface Prefs {
        /** The {@link #RUN_VERSION} the user has finished or skipped, or 0. */
        int getTourCompletedVersion();

        void setTourCompletedVersion(int version);

        /**
         * The {@link #RUN_VERSION} the run in progress belongs to, or 0 for a run started before
         * the version was recorded. Without it a stored card number says nothing: card 3 of an
         * older run and card 3 of this one are different lessons.
         */
        int getTourRunVersion();

        void setTourRunVersion(int version);

        /** The card the run is on, or -1 when no run is in progress. */
        int getTourStepIndex();

        void setTourStepIndex(int index);

        /** How many of the current card's stages have been cleared. */
        int getTourStepStage();

        void setTourStepStage(int stage);

        /** Whether the user skipped past at least one card in this run. */
        boolean getTourSkipped();

        void setTourSkipped(boolean skipped);
    }

    /** What the host is told; every call is made after the prefs are written. */
    public interface Listener {
        /** The setup sheet is the thing in front of the user now. */
        void onTourSetupShown();

        /** Show this card at this stage. */
        void onTourStepShown(@NonNull TourStep step, int stage);

        /**
         * The lesson that is up has just been cleared. Say so, then call
         * {@link #continueAfterCompletion()}.
         */
        void onTourStepCompleted(@NonNull TourStep step);

        /** The run is over; take the overlay down. */
        void onTourFinished(boolean skipped);
    }

    private final List<TourStep> mSteps;
    /** Cards this run walks past because the phone has nothing for them to point at. */
    private final Set<String> mDropped = new HashSet<>();
    private final Prefs mPrefs;
    private final Clock mClock;

    @Nullable private Listener mListener;
    private int mStepIndex = STEP_NONE;
    private int mStage;
    private long mArmedAt;
    private boolean mRunning;
    /** Whether this is one lesson on its own, which writes nothing through to the prefs. */
    private boolean mPracticing;
    /** Whether the setup sheet is up: the run is offered, and has not begun. */
    private boolean mShowingSetup;
    /** Whether the card is saying "Got it" and waiting for the host to move on. */
    private boolean mCompleting;

    public TourController(@NonNull List<TourStep> steps, @NonNull Prefs prefs,
                          @NonNull Clock clock) {
        mSteps = new ArrayList<>(steps);
        mPrefs = prefs;
        mClock = clock;
    }

    public void setListener(@Nullable Listener listener) {
        mListener = listener;
    }

    /**
     * Re-reads the run for the phone it is about to run on. Ignored while a card of the run is up:
     * changing the run under a running one would move the card the user is reading. The sheet is
     * not a card of the run, so the run can still be rebuilt under it.
     */
    public void setSteps(@Nullable List<TourStep> steps) {
        if ((mRunning && !mShowingSetup) || steps == null || steps.isEmpty()) return;
        mSteps.clear();
        mSteps.addAll(steps);
        mDropped.clear();
    }

    /** Whether the user has finished or skipped a run of any version. */
    public boolean isFinished() {
        return mPrefs.getTourCompletedVersion() >= 1;
    }

    /** Whether the run should be offered on its own: see {@link #isOfferedTo(int)}. */
    public boolean isOffered() {
        return isOfferedTo(mPrefs.getTourCompletedVersion());
    }

    /** Whether the sheet or a card is up right now. */
    public boolean isRunning() {
        return mRunning;
    }

    /** Whether the card that is up is a single lesson practised from help. */
    public boolean isPracticing() {
        return mPracticing;
    }

    /** Whether the setup sheet is up. */
    public boolean isShowingSetup() {
        return mShowingSetup;
    }

    /** Whether the card is saying "Got it". */
    public boolean isCompleting() {
        return mCompleting;
    }

    /** The card that is up, or null — the sheet is not a card. */
    @Nullable
    public TourStep currentStep() {
        if (!mRunning || mShowingSetup) return null;
        return mStepIndex >= 0 && mStepIndex < mSteps.size() ? mSteps.get(mStepIndex) : null;
    }

    /** How many of the current card's stages have been cleared. */
    public int currentStage() {
        return mRunning ? mStage : 0;
    }

    /** The progress the card that is up shows. */
    @NonNull
    public TourProgress currentProgress() {
        return TourProgress.of(mSteps, mDropped, mStepIndex, mCompleting);
    }

    /** Whether the run that is up, or the one that just ended, had a card skipped. */
    public boolean wasSkipped() {
        return mPrefs.getTourSkipped();
    }

    /**
     * Offers the run on the setup sheet, discarding any earlier one. This is what a first launch
     * and Replay both do; the lessons begin on the user's own {@link #showMeAround()}.
     */
    public boolean start() {
        if (mSteps.isEmpty()) return false;
        mPrefs.setTourSkipped(false);
        mPracticing = false;
        mCompleting = false;
        mShowingSetup = true;
        mRunning = true;
        mStepIndex = STEP_NONE;
        mStage = 0;
        if (mListener != null) mListener.onTourSetupShown();
        return true;
    }

    /** The sheet's Show me around: the run begins at its first lesson. */
    public boolean showMeAround() {
        if (!mShowingSetup) return false;
        mShowingSetup = false;
        int first = shownFrom(0);
        return first < mSteps.size() && startAtIndex(first);
    }

    /**
     * The sheet's Skip the tour. The lessons are passed over and the run counts as skipped, but it
     * still ends on the closing card: the tips and the downloads are what an experienced user came
     * for.
     */
    public boolean skipTheTour() {
        if (!mShowingSetup) return false;
        mShowingSetup = false;
        mPrefs.setTourSkipped(true);
        int closing = closingIndex();
        if (closing < 0) {
            end();
            return true;
        }
        return startAtIndex(closing);
    }

    /** Starts a normal run at a named card; everything after it follows in order. */
    public boolean startAt(@Nullable String stepId) {
        int index = indexOf(stepId);
        if (index < 0) return false;
        index = shownFrom(index);
        return index < mSteps.size() && startAtIndex(index);
    }

    private boolean startAtIndex(int index) {
        mPracticing = false;
        mShowingSetup = false;
        mCompleting = false;
        mRunning = true;
        mPrefs.setTourCompletedVersion(0);
        mPrefs.setTourRunVersion(RUN_VERSION);
        moveTo(index);
        return true;
    }

    /**
     * Shows one lesson on its own: help's "Try it". It ends when that lesson is cleared and writes
     * nothing — not the completed version, not the card, not the stage and not the skip flag.
     *
     * <p>Refused outright while a real run is up: replacing the card the run is waiting on loses
     * the run's place, and the signals the practice clears on are the ones the run wanted.
     */
    public boolean startPractice(@Nullable String stepId) {
        if (mRunning && !mPracticing) return false;
        int index = indexOf(stepId);
        if (index < 0 || mDropped.contains(stepId) || mSteps.get(index).isClosingCard())
            return false;
        mPracticing = true;
        mShowingSetup = false;
        mCompleting = false;
        mRunning = true;
        mStepIndex = index;
        mStage = 0;
        mArmedAt = mClock.nowMillis();
        notifyStep();
        return true;
    }

    /** Offers the run unless this user has already been through a run of it. */
    public boolean startIfNeeded() {
        return isOffered() && !mRunning && start();
    }

    /**
     * Picks an unfinished run back up on its own card. A run left over from an older version of
     * the tour starts this one at its first lesson: the cards it was on are not this run's, and the
     * sheet before it has been answered already.
     *
     * <p>A lesson is picked up from its first half. The home screen is put back the way every
     * lesson starts before it is shown, and the second half of the keyboard lesson — bring it back
     * — means nothing over a keyboard that is already up.
     *
     * @return false when there is nothing to resume, in which case nothing is shown or written
     */
    public boolean resumeIfInProgress() {
        if (mRunning || isFinished()) return false;
        int stored = mPrefs.getTourStepIndex();
        if (stored < 0) return false;
        if (mPrefs.getTourRunVersion() < RUN_VERSION) {
            int first = shownFrom(0);
            return first < mSteps.size() && startAtIndex(first);
        }
        if (stored >= mSteps.size()) return false;
        int shown = shownFrom(stored);
        if (shown >= mSteps.size()) return false;
        mPracticing = false;
        mShowingSetup = false;
        mCompleting = false;
        mRunning = true;
        mStepIndex = shown;
        mStage = 0;
        mArmedAt = mClock.nowMillis();
        mPrefs.setTourRunVersion(RUN_VERSION);
        mPrefs.setTourStepIndex(shown);
        mPrefs.setTourStepStage(0);
        notifyStep();
        return true;
    }

    /**
     * A gesture the launcher observed. Moves the card on when it is the one being waited on, and is
     * otherwise ignored — including a signal that arrives while the card is still arming, or while
     * it is saying "Got it".
     */
    public void onSignal(@Nullable String signalId) {
        TourStep step = currentStep();
        if (step == null || signalId == null || mCompleting) return;
        if (mClock.nowMillis() - mArmedAt < ARM_DELAY_MS) return;
        if (!signalId.equals(step.signalAt(mStage))) return;
        mStage++;
        if (mStage < step.stageCount()) {
            // The second half of the same lesson: the card swaps without celebrating the first.
            if (!mPracticing) mPrefs.setTourStepStage(mStage);
            mArmedAt = mClock.nowMillis();
            notifyStep();
            return;
        }
        mCompleting = true;
        if (!mPracticing) {
            int next = shownFrom(mStepIndex + 1);
            mPrefs.setTourStepIndex(next < mSteps.size() ? next : STEP_NONE);
            mPrefs.setTourStepStage(0);
        }
        if (mListener != null) mListener.onTourStepCompleted(step);
    }

    /** The "Got it" has been said: on to the next card, or out of practice. */
    public void continueAfterCompletion() {
        if (!mCompleting) return;
        mCompleting = false;
        advance();
    }

    /**
     * Takes a card out of the rest of this run, wherever the run stands: a lesson whose control
     * this phone does not have. A fresh run — {@link #setSteps} — brings it back.
     */
    public void dropStep(@Nullable String stepId) {
        if (stepId != null) mDropped.add(stepId);
    }

    /** The card's Skip: this lesson is not for this user, move on. */
    public void skip() {
        if (!mRunning || mPracticing || mShowingSetup || mCompleting) return;
        TourStep step = currentStep();
        if (step == null || step.isClosingCard()) return;
        mPrefs.setTourSkipped(true);
        advance();
    }

    /**
     * The card's ✕. The lessons that are left are passed over and the run counts as skipped, but it
     * still ends on the closing card. In practice it is the way out.
     */
    public void endTour() {
        if (!mRunning || mShowingSetup) return;
        if (mPracticing) {
            finishPractice();
            return;
        }
        TourStep step = currentStep();
        if (step == null || step.isClosingCard()) return;
        mCompleting = false;
        mPrefs.setTourSkipped(true);
        int closing = closingIndex();
        if (closing < 0) end();
        else moveTo(closing);
    }

    /** The closing card's Start, and anything else that ends the run deliberately. */
    public void finish() {
        if (!mRunning || mShowingSetup) return;
        if (mPracticing) finishPractice();
        else end();
    }

    private int indexOf(@Nullable String stepId) {
        if (stepId == null) return -1;
        for (int i = 0; i < mSteps.size(); i++)
            if (stepId.equals(mSteps.get(i).id)) return i;
        return -1;
    }

    private int closingIndex() {
        for (int i = 0; i < mSteps.size(); i++)
            if (mSteps.get(i).isClosingCard()) return i;
        return -1;
    }

    private void advance() {
        if (mPracticing) {
            finishPractice();
            return;
        }
        int next = shownFrom(mStepIndex + 1);
        if (next >= mSteps.size()) end();
        else moveTo(next);
    }

    /** The first card at or after {@code index} that is shown, or the run's size when none is. */
    private int shownFrom(int index) {
        int at = Math.max(0, index);
        while (at < mSteps.size() && mDropped.contains(mSteps.get(at).id)) at++;
        return at;
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
        mShowingSetup = false;
        mCompleting = false;
        mStepIndex = STEP_NONE;
        mStage = 0;
        mPrefs.setTourStepIndex(STEP_NONE);
        mPrefs.setTourStepStage(0);
        mPrefs.setTourRunVersion(RUN_VERSION);
        mPrefs.setTourCompletedVersion(RUN_VERSION);
        if (mListener != null) mListener.onTourFinished(mPrefs.getTourSkipped());
    }

    /** The way out of practice: the overlay comes down and the stored run is left as it was. */
    private void finishPractice() {
        mRunning = false;
        mPracticing = false;
        mShowingSetup = false;
        mCompleting = false;
        mStepIndex = STEP_NONE;
        mStage = 0;
        if (mListener != null) mListener.onTourFinished(false);
    }

    private void notifyStep() {
        TourStep step = currentStep();
        if (step != null && mListener != null) mListener.onTourStepShown(step, mStage);
    }
}
