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
    public static final int RUN_VERSION = 4;

    /**
     * The run before the welcome card. Its lessons are this run's lessons, under the same numbers
     * — the welcome card is offered before the run rather than counted inside it — so a run in
     * progress from it is picked up exactly where it stopped.
     *
     * <p>It is also what the legacy migration records, so an install that only ever sat through
     * an older introduction is offered the welcome card once.
     */
    public static final int VERSION_BEFORE_THE_WELCOME_CARD = 3;

    /**
     * The run before the pin lesson was put second. Its card numbers mean different lessons than
     * this run's, so a run in progress from it is re-indexed by {@link #versionTwoCardFor} rather
     * than read at face value.
     */
    private static final int VERSION_BEFORE_THE_PIN_LESSON = 2;

    /** The six cards of that run, in the order it showed them. */
    private static final List<String> VERSION_TWO_CARDS = Collections.unmodifiableList(
        Arrays.asList(TourRun.FIND_HELP, TourRun.FIND_APPS, TourRun.KEYBOARD,
            TourRun.FIND_ACTION, TourRun.HOME_CHOICE, TourRun.CLOSING));

    /** The seven cards of the run before the welcome card, in the order it showed them. */
    private static final List<String> VERSION_THREE_CARDS = Collections.unmodifiableList(
        Arrays.asList(TourRun.FIND_HELP, TourRun.PIN_APPS, TourRun.FIND_APPS, TourRun.KEYBOARD,
            TourRun.FIND_ACTION, TourRun.HOME_CHOICE, TourRun.CLOSING));

    /** How long a freshly shown card ignores signals. */
    public static final long ARM_DELAY_MS = 400L;

    /** Nothing is running and no card has ever been shown. */
    private static final int STEP_NONE = -1;

    /** The buttons a practice hint offers instead of a lesson's Skip step and End tour. */
    private static final List<TourAction> PRACTICE_ACTIONS = Collections.unmodifiableList(
        Arrays.asList(TourAction.DONE, TourAction.END_PRACTICE));

    /**
     * The buttons a stage that is only shown offers: the same three every other stage offers. The
     * card the run is not waiting on used to say Done instead, and a middle button that changes
     * its word halfway through a lesson reads as a different button doing a different thing. It
     * is the same way on, so it is the same word; moving past a card that asked for nothing is
     * not counted as a lesson skipped.
     */
    private static final List<TourAction> SHOWN_STAGE_ACTIONS = Collections.unmodifiableList(
        Arrays.asList(TourAction.BACK, TourAction.SKIP_STEP, TourAction.END_TOUR));

    /** What the user answered on a card that is a question. */
    public enum Choice {
        /** Make the launcher the phone's home app. */
        USE_AS_HOME,
        /** Leave the home app as it is for now. */
        KEEP_TRYING,
        /** Nothing to decide: the card had one way on. */
        CONTINUE,
        /** Take this release's row of keys. */
        SWITCH_KEY_ROW,
        /** Keep the row of keys the user already has. */
        KEEP_KEY_ROW
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

    /**
     * The card {@code stepIndex} meant in the run before the welcome card, or null when that run
     * had no such card.
     *
     * <p>Every number means what it always meant: the welcome card is offered before the run and
     * is not one of its steps, so nothing after it moved.
     */
    public static String versionThreeCardFor(int stepIndex) {
        return stepIndex >= 0 && stepIndex < VERSION_THREE_CARDS.size()
            ? VERSION_THREE_CARDS.get(stepIndex) : null;
    }

    /**
     * The card {@code stepIndex} meant in the run before the pin lesson, or null when that run had
     * no such card.
     *
     * <p>Every card of that run is still in this one, under the same id, so nothing is lost and
     * nothing is asked twice: the number moved because a lesson was inserted second, and the id is
     * what the stored place really meant. The stage is kept with it — the cards themselves did not
     * change — so a user halfway through Find help comes back exactly where they were.
     */
    public static String versionTwoCardFor(int stepIndex) {
        return stepIndex >= 0 && stepIndex < VERSION_TWO_CARDS.size()
            ? VERSION_TWO_CARDS.get(stepIndex) : null;
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
         * The user answered the key-row card. The run moves on either way; writing the row, and
         * remembering that the question has been put, is the part only the launcher can do.
         */
        default void onTourKeyRowChoice(Choice choice) {}

        /**
         * A run from an older version of the tour was interrupted somewhere this run has no
         * equivalent for. Ask the user, then call {@link #resumeChosen()} or
         * {@link #restartChosen()}: both begin at the first lesson, and the difference is only
         * what the card says.
         */
        default void onTourResumeOrRestart() {}
    }

    private final List<TourStep> mSteps;
    /** Cards this run walks past because the phone has nothing for them to point at. */
    private final java.util.Set<String> mDropped = new java.util.HashSet<>();
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
    /** Whether the card up is the welcome card, which is offered before the run and not in it. */
    private boolean mShowingWelcome;

    /**
     * The card the run is offered on. Held apart from {@link #mSteps} on purpose: it is not a
     * lesson, it is never stored, and keeping it out of the list is what leaves every lesson's
     * stored number meaning what it meant before this card existed.
     */
    private final TourStep mWelcome = TourRun.welcome();

    public TourController(List<TourStep> steps, Prefs prefs, Clock clock) {
        mSteps = new ArrayList<>(steps);
        mPrefs = prefs;
        mClock = clock;
    }

    public void setListener(Listener listener) {
        mListener = listener;
    }

    /**
     * Re-reads the run for the phone it is about to run on. The two sentences that depend on a
     * system setting — the way back from an app, and which way round the keyboard lesson goes —
     * are only right if they are built from what the phone says when the run starts, so the host
     * hands the run back in before every start, replay, resume and practice. Ignored while a card
     * of the run is up: changing the run under a running one would move the card the user is
     * reading. The welcome card is not one of them — it is offered before the run — so the run can
     * still be rebuilt under it, which is what makes the keyboard lesson right for the keyboard
     * the user actually has when they take the tour.
     */
    public void setSteps(List<TourStep> steps) {
        if ((mRunning && !mShowingWelcome) || steps == null || steps.isEmpty()) return;
        mSteps.clear();
        mSteps.addAll(steps);
        mDropped.clear();
    }

    /**
     * Whether the user has already been through a run of some version. This is the "a run ever
     * completed" question — what {@link #resumeIfInProgress()} asks before looking for a run to
     * pick back up — and not the question of whether to offer this one, which is
     * {@link #isOffered()}.
     */
    public boolean isFinished() {
        return mPrefs.getTourCompletedVersion() >= 1;
    }

    /**
     * Whether this run should be offered at all: the user has never finished this version of it.
     *
     * <p>Someone who sat through an older run is offered this one once, on the welcome card,
     * because the launcher they finished that run on is not the one they have now. Their answer —
     * the tour taken to its end, or "Not now" — records this version, so they are asked once and
     * not again until the run changes.
     */
    public boolean isOffered() {
        return mPrefs.getTourCompletedVersion() < RUN_VERSION;
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

    /** Whether the card up is the one the run is offered on. */
    public boolean isShowingWelcome() {
        return mShowingWelcome;
    }

    /** The card that is up, or null. */
    public TourStep currentStep() {
        if (mShowingWelcome) return mWelcome;
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
        if (mPracticing) return PRACTICE_ACTIONS;
        return step.isShownOnlyStage(mStage) ? SHOWN_STAGE_ACTIONS : step.actions();
    }

    /** Whether the run that is up, or the one that just ended, had a card skipped. */
    public boolean wasSkipped() {
        return mPrefs.getTourSkipped();
    }

    /**
     * Offers the run on the welcome card, discarding any earlier one. This is what a first launch
     * and Replay both do; the lessons begin on the user's own {@link #takeTheTour()}.
     */
    public boolean start() {
        if (mSteps.isEmpty()) return false;
        mPrefs.setTourSkipped(false);
        mPracticing = false;
        mAwaitingResumeChoice = false;
        mShowingWelcome = true;
        mRunning = true;
        mStepIndex = STEP_NONE;
        mStage = 0;
        mArmedAt = mClock.nowMillis();
        notifyStep();
        return true;
    }

    /** The welcome card's yes: the run begins at its first lesson. */
    public boolean takeTheTour() {
        if (!mShowingWelcome) return false;
        mShowingWelcome = false;
        return !mSteps.isEmpty() && startAt(mSteps.get(0).id);
    }

    /**
     * The welcome card's other answer. The run ends where it stands and records this version, so
     * the offer is not made again until the run itself changes; it counts as skipped, which is
     * what it is.
     */
    public boolean notNow() {
        if (!mShowingWelcome) return false;
        mShowingWelcome = false;
        mPrefs.setTourSkipped(true);
        end();
        return true;
    }

    /**
     * Starts a normal run at a named card, which is where it stays: everything after it follows in
     * order. Used by the version migration, and by anything that wants the run to begin at a
     * lesson other than the first.
     */
    public boolean startAt(String stepId) {
        int index = indexOf(stepId);
        if (index < 0) return false;
        index = shownFrom(index);
        if (index >= mSteps.size()) return false;
        mPracticing = false;
        mAwaitingResumeChoice = false;
        mShowingWelcome = false;
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
     *
     * <p>Refused outright while a real run is up: replacing the card the run is waiting on loses
     * the run's place, and the signals the practice clears on are the ones the run wanted.
     */
    public boolean startPractice(String stepId) {
        if (mRunning && !mPracticing) return false;
        int index = indexOf(stepId);
        if (index < 0) return false;
        mPracticing = true;
        mAwaitingResumeChoice = false;
        mShowingWelcome = false;
        mRunning = true;
        mStepIndex = index;
        mStage = 0;
        mArmedAt = mClock.nowMillis();
        notifyStep();
        return true;
    }

    /** Offers the run unless this user has already been through this version of it. */
    public boolean startIfNeeded() {
        return isOffered() && !mRunning && !mAwaitingResumeChoice && start();
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
        return resumeAt(stored, mPrefs.getTourStepStage());
    }

    private boolean resumeOlderRun(int storedStepIndex) {
        if (mPrefs.getTourRunVersion() >= VERSION_BEFORE_THE_WELCOME_CARD) {
            // The run the welcome card was added to: same lessons, same numbers, so the card the
            // user stopped on is picked back up and the welcome card is not put in their way.
            int index = indexOf(versionThreeCardFor(storedStepIndex));
            return index >= 0 && resumeAt(index, mPrefs.getTourStepStage());
        }
        if (mPrefs.getTourRunVersion() >= VERSION_BEFORE_THE_PIN_LESSON) {
            int index = indexOf(versionTwoCardFor(storedStepIndex));
            // Nothing to ask about: every card of that run is a card of this one, so the only way
            // here is a stored number that run never had, and there is nothing to resume from it.
            return index >= 0 && resumeAt(index, mPrefs.getTourStepStage());
        }
        String lesson = migratedLessonFor(storedStepIndex);
        if (lesson != null) return startAt(lesson);
        mAwaitingResumeChoice = true;
        if (mListener != null) mListener.onTourResumeOrRestart();
        return true;
    }

    /** Picks the run up on {@code index}, at the furthest stage of that card {@code stage} can be. */
    private boolean resumeAt(int index, int stage) {
        // The phone may have been made this launcher's home screen since the run was stored, in
        // which case the card it stopped on is one this run no longer shows: it is walked past,
        // and the card after it starts where every card starts.
        int shown = shownFrom(index);
        if (shown >= mSteps.size()) return false;
        if (shown != index) stage = 0;
        index = shown;
        mPracticing = false;
        mAwaitingResumeChoice = false;
        mShowingWelcome = false;
        mRunning = true;
        mStepIndex = index;
        mStage = clampStage(mSteps.get(index), stage);
        mArmedAt = mClock.nowMillis();
        mPrefs.setTourRunVersion(RUN_VERSION);
        mPrefs.setTourStepIndex(index);
        mPrefs.setTourStepStage(mStage);
        notifyStep();
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

    /** Whether a button that moves the run is being pressed on a card that is not part of it. */
    private boolean offTheRun() {
        return !mRunning || mPracticing || mShowingWelcome;
    }

    /**
     * A gesture the launcher observed. Advances the card when it is the one being waited on, and
     * is otherwise ignored — including a signal that arrives while the card is still arming.
     */
    public void onSignal(String signalId) {
        TourStep step = currentStep();
        if (step == null || signalId == null || mShowingWelcome) return;
        if (mClock.nowMillis() - mArmedAt < ARM_DELAY_MS) return;
        if (!signalId.equals(step.signalAt(mStage))) return;
        mStage++;
        if (mStage >= step.stageCount()) {
            advance();
        } else {
            if (!mPracticing) mPrefs.setTourStepStage(mStage);
            mArmedAt = mClock.nowMillis();
            notifyStep();
        }
    }

    /** The user answered a question card. The run moves on whichever answer they gave. */
    public void choose(Choice choice) {
        TourStep step = currentStep();
        if (step == null || !step.isChoiceCard() || choice == null) return;
        if (mListener != null) {
            if (TourRun.KEY_ROW.equals(step.id)) mListener.onTourKeyRowChoice(choice);
            else mListener.onTourHomeChoice(choice);
        }
        advance();
    }

    /**
     * Takes a card out of the rest of this run, wherever the run stands. For the one card whose
     * control may not exist on this phone at all: the keyboard lesson points at a key of the
     * shipped row, and a user who has just answered the key-row card with "Keep mine" may have no
     * such key. The card is passed over in both directions from then on, exactly as a question
     * with nothing to ask is, and a fresh run — {@link #setSteps} — brings it back.
     */
    public void dropStep(String stepId) {
        if (stepId != null) mDropped.add(stepId);
    }

    /** Back to the first stage of the lesson before this one; nothing to do on the first. */
    public void back() {
        if (offTheRun() || mStepIndex <= 0) return;
        int previous = shownBackFrom(mStepIndex - 1);
        if (previous < 0) return;
        moveTo(previous);
    }

    /**
     * The Skip step button: this lesson is not for this user, move on. On the one stage the run
     * only shows it is the way on rather than a skip — there is no gesture to pass over — so the
     * run is not marked as skipped for it.
     */
    public void skip() {
        if (offTheRun()) return;
        if (continueShownStage()) return;
        mPrefs.setTourSkipped(true);
        advance();
    }

    /**
     * The End tour button: the lessons are not wanted and the run counts as skipped, but the way
     * out still passes the home-screen question and the closing card, so leaving early never
     * costs the one card an experienced user came for. A run with no choice card left ahead ends.
     */
    public void endTour() {
        if (!mRunning || mShowingWelcome) return;
        if (mPracticing) {
            endPractice();
            return;
        }
        mPrefs.setTourSkipped(true);
        int choice = indexOf(TourRun.HOME_CHOICE);
        int target = choice >= 0 ? shownFrom(choice) : mSteps.size();
        if (target > mStepIndex && target < mSteps.size()) {
            moveTo(target);
            return;
        }
        end();
    }

    /**
     * The Done button, wherever it appears. On a practice hint it is the way out; on a stage the
     * run only shows, it is the way on, because there is no gesture for the launcher to report.
     */
    public void done() {
        if (mShowingWelcome) return;
        if (mPracticing) {
            finishPractice();
            return;
        }
        continueShownStage();
    }

    /**
     * Past a stage that is only shown. Ignored on any other stage, so a Done tapped on a card the
     * run has since replaced cannot skip a gesture the run is still waiting to see.
     */
    public boolean continueShownStage() {
        TourStep step = currentStep();
        if (step == null || offTheRun() || !step.isShownOnlyStage(mStage)) return false;
        // The shown stage is the last of its card, so there is nothing after it but the next card.
        advance();
        return true;
    }

    /** The End practice button: it leaves without writing anything. */
    public void endPractice() {
        if (!mRunning || !mPracticing) return;
        finishPractice();
    }

    /** The closing card's action, and anything else that ends the run deliberately. */
    public void finish() {
        if (!mRunning || mShowingWelcome) return;
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
            return;
        }
        int next = shownFrom(mStepIndex + 1);
        if (next >= mSteps.size()) end();
        else moveTo(next);
    }

    /**
     * Whether this card is passed over rather than shown. The home-screen question on a phone the
     * launcher is already the home app of has nothing to ask and nothing to answer, so the run
     * walks past it in both directions instead of stopping the user on a card with one button.
     *
     * <p>The card stays in the run either way: every stored card number, and every number the
     * older runs are mapped onto, means the card it has always meant.
     */
    private boolean isPassedOver(TourStep step) {
        if (mDropped.contains(step.id)) return true;
        return step.isChoiceCard() && step.actions().size() == 1
            && step.actions().get(0) == TourAction.CONTINUE;
    }

    /** The first card at or after {@code index} that is shown, or the run's size when none is. */
    private int shownFrom(int index) {
        int at = Math.max(0, index);
        while (at < mSteps.size() && isPassedOver(mSteps.get(at))) at++;
        return at;
    }

    /** The last card at or before {@code index} that is shown, or -1 when none is. */
    private int shownBackFrom(int index) {
        int at = Math.min(index, mSteps.size() - 1);
        while (at >= 0 && isPassedOver(mSteps.get(at))) at--;
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
        mShowingWelcome = false;
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
        mShowingWelcome = false;
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
        return Math.min(stage, Math.max(0, step.stageCount() - 1));
    }
}
