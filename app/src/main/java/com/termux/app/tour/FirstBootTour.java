package com.termux.app.tour;

import android.app.Activity;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.R;
import com.termux.ai.TaiDownloadHub;
import com.termux.app.firstrun.TaiWelcomeCard;
import com.termux.app.firstrun.TaiWelcomeDownloads;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;

import java.util.Collections;
import java.util.List;

/**
 * The run, assembled: the state machine, the setup sheet's hand-off, the overlay, the targets and
 * every signal, held in one place so the activity owns a field and a handful of one-line calls
 * rather than a tour.
 *
 * <p>Everything the launcher has to tell it arrives through the {@code on...} calls below, every
 * one of them made from the single place in the chrome that already decides the thing. The states
 * among them are edge-triggered in {@link TourSignalRelay}, because the chrome re-applies them
 * constantly and a card cleared by a state the user never put it in is the failure mode the whole
 * run is built against.
 *
 * <p>Between lessons the home screen is put back the way every lesson starts — {@link
 * HomeHost#resetHome()} — through the launcher's own calls rather than by replaying gestures.
 */
public final class FirstBootTour implements TourController.Listener, TourOverlayView.Callbacks,
    TourSignals.Listener, TourViewTargets.ViewFinder {

    /** How long a cleared lesson's card says "Got it" before the run moves on. */
    static final long GOT_IT_MS = 1100L;
    /** The closing card's downloads line is refreshed no faster than this. */
    static final long MODELS_REFRESH_MS = 1000L;

    /**
     * The controls the tour points at that the chrome measures for itself: a key of the in-app
     * keyboard is a cap inside a rendered keyboard, not a view with an id.
     */
    public interface KeyProbe {
        boolean keyRectOnScreen(@NonNull String keyName, @NonNull android.graphics.Rect out);
    }

    /**
     * What else is in front of the user right now.
     *
     * <p>Asked rather than told, because these surfaces open and close along a dozen paths each and
     * a card left drawing over one of them is exactly what a missed path looks like.
     */
    public interface ChromeProbe {
        boolean isAppDrawerUp();

        boolean isCommandPaletteUp();

        boolean isTerminalSheetUp();

        boolean isSurfaceEditorUp();

        /** Whether the help overlay is up, which every card waits behind. */
        boolean isHelpUp();

        /** The ? of the corner tab that is up, in screen coordinates, or false when none is. */
        default boolean helpButtonRectOnScreen(@NonNull android.graphics.Rect out) {
            return false;
        }

        /** The command palette's glass, in screen coordinates, or false when it is shut. */
        default boolean commandPaletteRectOnScreen(@NonNull android.graphics.Rect out) {
            return false;
        }

        /** The edge the apps row stands on, or the bottom while the row is put away. */
        @NonNull
        default com.termux.app.place.PlaceLayout.Edge appsEdge() {
            return com.termux.app.place.PlaceLayout.Edge.BOTTOM;
        }
    }

    /** The home screen, for the two things the run asks of it. */
    public interface HomeHost {
        /**
         * Back to how every lesson starts: the terminal place, the keyboard up, and the drawer,
         * the palette, the status shade, help and the corner menu closed. Each through the
         * launcher's own call for it, animated as the launcher animates it.
         */
        void resetHome();

        /** Whether the wall has one place only, so there is nowhere to drag the border to. */
        boolean hasOnePlace();
    }

    /** The setup sheet the run is offered on. The activity owns it: it asks for permissions. */
    public interface SetupHost {
        /** Raise the sheet, or keep the one that is up. */
        void showSetup(@NonNull SetupCallbacks callbacks);

        /** Take the sheet down if it is up. */
        void dismissSetup();
    }

    /**
     * The sheet's two ways on. What it left downloading is in {@link
     * TaiWelcomeDownloads#queued}, where the closing card reads it even after a process death.
     */
    public interface SetupCallbacks {
        void onShowMeAround();

        void onSkipTheTour();
    }

    /** The place the run is taught on: the wall's own home page. */
    public static final String HOME_PLACE = com.termux.app.wall.PaneWallPolicy.homePage().name();

    @NonNull private final Activity mActivity;
    @NonNull private final TourController mController;
    @NonNull private final TourSignalRelay mSignals = new TourSignalRelay();
    @Nullable private final KeyProbe mKeyProbe;
    @Nullable private ChromeProbe mChromeProbe;
    @Nullable private HomeHost mHomeHost;
    @Nullable private SetupHost mSetupHost;
    /** A run that was asked for while help was up, held until help goes away. */
    @Nullable private Runnable mStartWaitingForHelp;
    /** Told when the real run ends: the models card follows it when the sheet did not ask. */
    @Nullable private Runnable mAfterRunListener;
    /** Whether the run that is going is a help practice, which writes and ends nothing real. */
    private boolean mPracticeRun;
    /** Whether the pinned-apps sheet is in front of the user, which only it can say. */
    private boolean mPinEditorUp;
    /** What the sheet left downloading, read when the closing card comes up. */
    @NonNull private TaiWelcomeDownloads.Queued mQueuedModels =
        new TaiWelcomeDownloads.Queued(Collections.emptyList(), TaiWelcomeDownloads.Start.NOTHING);
    /** The "Got it" in progress, waiting to move the run on. */
    @Nullable private Runnable mCompletion;

    @Nullable private TourOverlayView mOverlay;
    @Nullable private ViewGroup mOverlayHost;
    @Nullable private ViewTreeObserver.OnGlobalLayoutListener mLayoutListener;
    private int mPresentation = TourCardVisibility.NORMAL;

    @Nullable private TaiDownloadHub.Listener mModelsListener;
    @Nullable private List<TaiDownloadHub.Snapshot> mModelsLatest;
    @Nullable private Runnable mModelsRefresh;
    private long mModelsRefreshedAt;

    /** The removed footage onboarding's own once-per-install preferences file and key. */
    private static final String LEGACY_ONBOARDING_PREFS_NAME = "termux_first_launch";
    private static final String LEGACY_ONBOARDING_COMPLETED_VERSION_KEY =
        "onboarding_completed_version";
    private static final int LEGACY_ONBOARDING_COMPLETED_VERSION = 2;

    public FirstBootTour(@NonNull Activity activity,
                         @NonNull TermuxAppSharedPreferences preferences,
                         @Nullable KeyProbe keyProbe) {
        mActivity = activity;
        mKeyProbe = keyProbe;
        migrateLegacyOnboardingCompletionIfNeeded(activity, preferences);
        mController = new TourController(TourRun.steps(
            new TourRun.RunContext(com.termux.app.place.PlaceLayout.Edge.BOTTOM)),
            new TourPreferences(preferences), SystemClock::uptimeMillis);
        mController.setListener(this);
        mSignals.setTourSignalListener(this);
        mSignals.setHomePlace(HOME_PLACE);
    }

    /**
     * Treats a completed run of the removed footage onboarding as a completed run of this tour, so
     * an install that already sat through the old one is never shown this run too. Runs once, ever,
     * guarded by its own flag.
     */
    private static void migrateLegacyOnboardingCompletionIfNeeded(
            @NonNull Activity activity, @NonNull TermuxAppSharedPreferences preferences) {
        if (preferences.isFirstBootTourLegacyOnboardingMigrated()) return;
        int legacyVersion = activity
            .getSharedPreferences(LEGACY_ONBOARDING_PREFS_NAME, Activity.MODE_PRIVATE)
            .getInt(LEGACY_ONBOARDING_COMPLETED_VERSION_KEY, 0);
        if (TourController.legacyOnboardingCounts(
                legacyVersion, LEGACY_ONBOARDING_COMPLETED_VERSION,
                preferences.getFirstBootTourCompletedVersion())) {
            preferences.setFirstBootTourCompletedVersion(
                TourController.VERSION_BEFORE_THE_WELCOME_CARD);
        }
        preferences.setFirstBootTourLegacyOnboardingMigrated(true);
    }

    /** Called once each time the real run ends. */
    public void setAfterRunListener(@Nullable Runnable listener) {
        mAfterRunListener = listener;
    }

    /** What else is covering the home screen, for the card visibility policy. */
    public void setChromeProbe(@Nullable ChromeProbe probe) {
        mChromeProbe = probe;
        onChromeChanged();
    }

    /** The home screen, which the run puts back between lessons. */
    public void setHomeHost(@Nullable HomeHost host) {
        mHomeHost = host;
    }

    /** The setup sheet the run is offered on. */
    public void setSetupHost(@Nullable SetupHost host) {
        mSetupHost = host;
    }

    /** Offers the run to someone who has never been through one. */
    public void startIfNeeded() {
        if (!mController.isOffered() || mController.isRunning()) return;
        if (waitForHelpToClose(this::startIfNeeded)) return;
        rebuildRunForThisPhone();
        mController.startIfNeeded();
    }

    /**
     * Whether the run is going or still waiting to be offered. Anything else that would put a card
     * of its own up on the way in holds off while this is true, so the two never talk at once.
     */
    public boolean isRunPending() {
        return mController.isOffered() || mController.isRunning();
    }

    /** Starts the run from the setup sheet, whatever came before: what Replay asks for. */
    public void restart() {
        if (waitForHelpToClose(this::restart)) return;
        cancelCompletion();
        removeOverlay();
        rebuildRunForThisPhone();
        mController.start();
    }

    /** Whether help may offer "Try it" at all: not while a real run is up. */
    public boolean canStartPractice() {
        return !mController.isRunning() || mController.isPracticing();
    }

    /** One lesson on its own, for help's "Try it". Writes nothing through. */
    public boolean startPractice(@Nullable String lessonId) {
        if (lessonId == null) return false;
        if (!canStartPractice()) {
            TourLog.d("a run is already up; practice for " + lessonId + " is refused");
            return false;
        }
        if (waitForHelpToClose(() -> startPractice(lessonId))) return true;
        cancelCompletion();
        rebuildRunForThisPhone();
        resetHome();
        boolean started = mController.startPractice(lessonId);
        mPracticeRun = started;
        return started;
    }

    /**
     * Whether the run has to wait: a card drawn over help is in the way of the page of answers the
     * run teaches the user to reach, so anything that would put one up while help is open is held
     * until help closes.
     */
    private boolean waitForHelpToClose(@NonNull Runnable start) {
        if (!isHelpUp()) return false;
        TourLog.d("help is up; the run waits for it to close");
        mStartWaitingForHelp = start;
        return true;
    }

    private boolean isHelpUp() {
        ChromeProbe probe = mChromeProbe;
        return probe != null ? probe.isHelpUp() : mSignals.isHelpShown();
    }

    /**
     * The run, built for the phone it is about to run on: the apps row's edge decides the drawer
     * lesson's sentence, and a wall with one place has no border to drag across.
     */
    private void rebuildRunForThisPhone() {
        mController.setSteps(TourRun.steps(new TourRun.RunContext(
            mChromeProbe == null ? com.termux.app.place.PlaceLayout.Edge.BOTTOM
                : mChromeProbe.appsEdge())));
        if (mHomeHost != null && mHomeHost.hasOnePlace()) mController.dropStep(TourRun.BORDER_DRAG);
    }

    /**
     * Picks an unfinished run back up after a process death or a trip out of the launcher.
     *
     * @return true when a run was actually resumed.
     */
    public boolean resumeIfInProgress() {
        if (mController.isRunning()) return false;
        if (waitForHelpToClose(this::resumeIfInProgress)) return false;
        rebuildRunForThisPhone();
        boolean resumed = mController.resumeIfInProgress();
        // The lesson starts over from its first half, on a home screen put back the way every
        // lesson starts. Nothing the reset settles is what a first half waits for.
        if (resumed) resetHome();
        return resumed;
    }

    /** Whether a card or the sheet is up right now — for suppressing dialogs that would draw over it. */
    public boolean isShowing() {
        return mController.isRunning();
    }

    private void resetHome() {
        if (mHomeHost != null) mHomeHost.resetHome();
    }

    // ---- what the launcher reports -------------------------------------------------------------

    /** The place the wall has settled on. */
    public void onPlaceSettled(@Nullable String placeId) {
        mSignals.onPlaceSettled(placeId);
        refreshCardVisibility();
    }

    /** The status bar's resting state, once it has settled. */
    public void onStatusBarCollapsedSettled(boolean collapsed) {
        mSignals.onStatusBarCollapsedSettled(collapsed);
    }

    /** How many windows the top row is showing, each time it has been rebuilt. */
    public void onWindowCountSettled(int count) {
        mSignals.onWindowCountSettled(count);
    }

    /** The window the top row is showing as current. */
    public void onWindowSelected(@Nullable String windowId) {
        mSignals.onWindowSelected(windowId);
    }

    /**
     * The app drawer's resting state, once it has settled.
     *
     * @param userDriven false when the launcher closed the plane itself, which the run must not
     *     read as the user's swipe
     */
    public void onDrawerOpenSettled(boolean open, boolean userDriven) {
        mSignals.onDrawerOpenSettled(open, userDriven);
    }

    /** The sessions the launcher holds, each time the sessions list has been rebuilt. */
    public void onSessionsSettled(int count, @Nullable String currentSessionId) {
        mSignals.onSessionsSettled(count, currentSessionId);
    }

    /** The window the launcher is showing, each time the active pane has settled on it. */
    public void onActiveWindowSettled(@Nullable String windowId) {
        mSignals.onActiveWindowSettled(windowId);
    }

    /** A split was asked for. */
    public void onPaneSplit() {
        mSignals.onPaneSplit();
    }

    /** A pane's corner menu was raised. */
    public void onPaneCornerMenuOpened() {
        mSignals.onPaneCornerMenuOpened();
    }

    /** A pane's corner menu went away again. */
    public void onPaneControlsDismissed() {
        mSignals.onPaneControlsDismissed();
    }

    /** The A-Z row's scrub launched an app. */
    public void onAppLaunchedFromScrub() {
        mSignals.onAppLaunchedFromScrub();
    }

    /** The command palette came up. */
    public void onPaletteOpened() {
        mSignals.onPaletteOpened();
    }

    /** The command palette went away again, however it was dismissed. */
    public void onPaletteClosed() {
        mSignals.onPaletteClosed();
    }

    /**
     * Whether help is up, once it has settled either way. A run held back while help was open is
     * let go here.
     */
    public void onHelpShownSettled(boolean shown) {
        mSignals.onHelpShownSettled(shown);
        refreshCardVisibility();
        if (shown) return;
        Runnable waiting = mStartWaitingForHelp;
        if (waiting == null) return;
        mStartWaitingForHelp = null;
        waiting.run();
    }

    /** Whether the in-app keyboard is showing, once it has settled either way. */
    public void onKeyboardShownSettled(boolean shown) {
        mSignals.onKeyboardShownSettled(shown);
    }

    /** The pinned-apps editor came up: a window of its own, which every card waits behind. */
    public void onPinEditorOpened() {
        mPinEditorUp = true;
        mSignals.onPinEditorOpened();
        refreshCardVisibility();
    }

    /** The pinned-apps editor went away. */
    public void onPinEditorClosed(boolean saved, int pinnedCount) {
        mPinEditorUp = false;
        mSignals.onPinEditorClosed(saved, pinnedCount);
        refreshCardVisibility();
    }

    /** An Android app was launched from the launcher, however the user found it. */
    public void onAppLaunched() {
        mSignals.onAppLaunched();
    }

    /** The launcher is in front of the user again. */
    public void onLauncherResumed() {
        mSignals.onLauncherResumed();
    }

    /** Something that covers the home screen whole opened or closed. */
    public void onChromeChanged() {
        refreshCardVisibility();
    }

    /**
     * Where the card that is up may draw, given what is covering the home screen. The decision is
     * {@link TourCardVisibility}'s; this reads the chrome and hands the answer to the overlay.
     */
    private void refreshCardVisibility() {
        TourOverlayView overlay = mOverlay;
        if (overlay == null) return;
        TourStep step = mController.currentStep();
        java.util.EnumSet<TourChrome> chrome = chromeUp();
        int presentation = TourCardVisibility.decide(step, chrome, mSignals.isOnHomePlace(),
            mController.isCompleting());
        if (presentation != mPresentation) {
            mPresentation = presentation;
            TourLog.d("card " + (step == null ? "none" : step.id + ":" + mController.currentStage())
                + " is now " + describePresentation(presentation) + " (chrome " + chrome + ")");
        }
        overlay.setPresentation(presentation);
    }

    @NonNull
    private java.util.EnumSet<TourChrome> chromeUp() {
        java.util.EnumSet<TourChrome> up = java.util.EnumSet.noneOf(TourChrome.class);
        // The pin editor is a window of its own rather than a plane of this one, so it is told
        // rather than asked.
        if (mPinEditorUp) up.add(TourChrome.PIN_EDITOR);
        ChromeProbe probe = mChromeProbe;
        if (probe == null) return up;
        if (probe.isAppDrawerUp()) up.add(TourChrome.DRAWER);
        if (probe.isCommandPaletteUp()) up.add(TourChrome.PALETTE);
        if (probe.isTerminalSheetUp()) up.add(TourChrome.TERMINAL_SHEET);
        if (probe.isSurfaceEditorUp()) up.add(TourChrome.SURFACE_EDITOR);
        if (probe.isHelpUp()) up.add(TourChrome.HELP);
        return up;
    }

    @NonNull
    private static String describePresentation(int presentation) {
        if (presentation == TourCardVisibility.HIDDEN) return "hidden";
        if (presentation == TourCardVisibility.AWAY) return "asking for the way back to the terminal";
        if (presentation == TourCardVisibility.COMPLETED) return "saying it is done";
        return "against its control";
    }

    // ---- TourController.Listener ---------------------------------------------------------------

    @Override
    public void onTourSetupShown() {
        TourLog.d("setup sheet shown");
        removeOverlay();
        SetupHost host = mSetupHost;
        if (host == null) {
            // Nowhere to raise the sheet: the run goes straight to its first lesson.
            onSheetLeft(true);
            return;
        }
        host.showSetup(new SetupCallbacks() {
            @Override public void onShowMeAround() {
                onSheetLeft(true);
            }

            @Override public void onSkipTheTour() {
                onSheetLeft(false);
            }
        });
    }

    private void onSheetLeft(boolean showMeAround) {
        if (!mController.isShowingSetup()) return;
        TourLog.d("setup sheet left: " + (showMeAround ? "show me around" : "skip the tour"));
        if (showMeAround) {
            rebuildRunForThisPhone();
            resetHome();
            mController.showMeAround();
        } else {
            mController.skipTheTour();
        }
    }

    @Override
    public void onTourStepShown(@NonNull TourStep step, int stage) {
        TourOverlayView overlay = obtainOverlay();
        if (overlay == null) {
            TourLog.d("card " + step.id + ":" + stage + " has nowhere to show: no content view");
            return;
        }
        overlay.setTranslationZ(0f);
        overlay.showStep(step, stage, mController.currentProgress(), mController.isPracticing());
        refreshCardVisibility();
        if (step.isClosingCard()) startModelsLine();
        else stopModelsLine();
        if (TourLog.enabled()) {
            android.graphics.Rect rect = overlay.currentTargetRect();
            TourLog.d("card " + step.id + ":" + stage + " shown, target \""
                + overlay.currentTargetId() + "\" at " + TourLog.describe(rect)
                + (rect == null ? " (" + overlay.currentMissReason() + ")" : "")
                + ", waiting for " + describeWait(step, stage));
        }
    }

    /**
     * The lesson is cleared. The card says so where it stands — over whatever the gesture opened,
     * help included, which stands higher than everything else — and the run moves on once it has,
     * putting the home screen back on the way.
     */
    @Override
    public void onTourStepCompleted(@NonNull TourStep step) {
        TourLog.d("card " + step.id + " cleared");
        TourOverlayView overlay = mOverlay;
        if (overlay == null) {
            finishCompletion();
            return;
        }
        overlay.showCompleted(mController.currentProgress());
        refreshCardVisibility();
        overlay.setTranslationZ(com.termux.app.help.HelpOverlayView.stackHeightPx(mActivity));
        overlay.bringToFront();
        cancelCompletion();
        Runnable completion = this::finishCompletion;
        mCompletion = completion;
        overlay.postDelayed(completion, GOT_IT_MS);
    }

    private void finishCompletion() {
        mCompletion = null;
        if (mOverlay != null) mOverlay.setTranslationZ(0f);
        if (!mController.isPracticing()) resetHome();
        mController.continueAfterCompletion();
    }

    private void cancelCompletion() {
        Runnable completion = mCompletion;
        mCompletion = null;
        if (completion != null && mOverlay != null) mOverlay.removeCallbacks(completion);
    }

    @Override
    public void onTourFinished(boolean skipped) {
        TourLog.d("run finished, skipped=" + skipped);
        cancelCompletion();
        stopModelsLine();
        removeOverlay();
        boolean practice = mPracticeRun;
        mPracticeRun = false;
        if (practice) return;
        TaiWelcomeDownloads.forgetQueued(mActivity);
        Runnable after = mAfterRunListener;
        if (after != null) after.run();
    }

    // ---- TourOverlayView.Callbacks -------------------------------------------------------------

    @Override
    public void onTourCloseTapped() {
        TourLog.d("close tapped on card " + describeCurrent());
        cancelCompletion();
        if (!mController.isPracticing()) resetHome();
        mController.endTour();
    }

    @Override
    public void onTourSkipTapped() {
        TourLog.d("skip tapped on card " + describeCurrent());
        if (mController.isCompleting()) return;
        resetHome();
        mController.skip();
    }

    @Override
    public void onTourStartTapped() {
        TourLog.d("start tapped");
        mController.finish();
    }

    @Override
    public void onTourGuideTapped() {
        String url = mActivity.getString(R.string.tour_docs_url);
        try {
            mActivity.startActivity(new android.content.Intent(
                android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url))
                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (android.content.ActivityNotFoundException notFound) {
            TourLog.d("no browser to open " + url);
        }
    }

    @NonNull
    private String describeCurrent() {
        TourStep step = mController.currentStep();
        return step == null ? "none" : step.id + ":" + mController.currentStage();
    }

    /** What the card that is up is waiting for, for the log. */
    @NonNull
    private static String describeWait(@NonNull TourStep step, int stage) {
        String signal = step.signalAt(stage);
        return signal == null ? "its own button" : signal;
    }

    // ---- the closing card's downloads ----------------------------------------------------------

    /**
     * The downloads line, while the closing card is up and only when the sheet queued models:
     * real progress from the download layer, refreshed no faster than once a second.
     */
    private void startModelsLine() {
        TourOverlayView overlay = mOverlay;
        if (overlay == null) return;
        TaiWelcomeDownloads.Queued models = TaiWelcomeDownloads.queued(mActivity);
        mQueuedModels = models;
        if (models.start == TaiWelcomeDownloads.Start.NEEDS_WIFI) {
            overlay.setModelsLine(mActivity.getString(R.string.tour_models_need_wifi), -1f);
            return;
        }
        if (models.start != TaiWelcomeDownloads.Start.STARTED || models.modelIds.isEmpty()) {
            overlay.setModelsLine(null, -1f);
            return;
        }
        if (mModelsListener != null) return;
        TaiDownloadHub.Listener listener = downloads -> {
            mModelsLatest = downloads;
            scheduleModelsRefresh();
        };
        mModelsListener = listener;
        TaiDownloadHub.get(mActivity).addListener(listener);
        // Until the hub's first snapshot lands, the line reads what is known: nothing done yet.
        applyModelsLine();
    }

    private void scheduleModelsRefresh() {
        TourOverlayView overlay = mOverlay;
        if (overlay == null || mModelsRefresh != null) return;
        long wait = Math.max(0L,
            MODELS_REFRESH_MS - (SystemClock.uptimeMillis() - mModelsRefreshedAt));
        Runnable refresh = () -> {
            mModelsRefresh = null;
            applyModelsLine();
        };
        mModelsRefresh = refresh;
        overlay.postDelayed(refresh, wait);
    }

    private void applyModelsLine() {
        TourOverlayView overlay = mOverlay;
        if (overlay == null || mModelsListener == null) return;
        mModelsRefreshedAt = SystemClock.uptimeMillis();
        List<TaiDownloadHub.Snapshot> latest = mModelsLatest;
        TaiWelcomeDownloads.Progress progress = TaiWelcomeDownloads.progress(
            mQueuedModels.modelIds, TaiWelcomeDownloads.catalogSizes(), modelId -> {
                if (latest == null) return null;
                TaiDownloadHub.Snapshot newest = null;
                for (TaiDownloadHub.Snapshot snapshot : latest)
                    if (modelId.equals(snapshot.modelId)) newest = snapshot;
                return newest == null ? null : new TaiWelcomeDownloads.ModelState(
                    newest.status, newest.bytesRead, newest.totalBytes);
            });
        overlay.setModelsLine(mActivity.getString(R.string.tour_models_progress,
            TaiWelcomeCard.formatSize(progress.doneBytes),
            TaiWelcomeCard.formatSize(progress.totalBytes)), progress.fraction());
    }

    private void stopModelsLine() {
        TaiDownloadHub.Listener listener = mModelsListener;
        mModelsListener = null;
        mModelsLatest = null;
        if (listener != null) TaiDownloadHub.get(mActivity).removeListener(listener);
        Runnable refresh = mModelsRefresh;
        mModelsRefresh = null;
        if (refresh != null && mOverlay != null) mOverlay.removeCallbacks(refresh);
        if (mOverlay != null) mOverlay.setModelsLine(null, -1f);
    }

    // ---- TourSignals.Listener ------------------------------------------------------------------

    @Override
    public void onTourSignal(String signalId) {
        if (!TourLog.enabled()) {
            mController.onSignal(signalId);
            return;
        }
        // Snapshotted around the call, because "did that clear the card" is the one question a
        // device pass has to be able to answer and nothing downstream records it.
        String before = describeCurrent();
        boolean completingBefore = mController.isCompleting();
        mController.onSignal(signalId);
        String after = describeCurrent();
        boolean moved = !before.equals(after) || completingBefore != mController.isCompleting();
        TourLog.d("signal " + signalId + " while card " + before
            + (moved ? " — cleared it, now " + after : " — ignored"));
    }

    // ---- TourViewTargets.ViewFinder ------------------------------------------------------------

    @Override
    @Nullable
    public View findTourView(int viewId) {
        return mActivity.findViewById(viewId);
    }

    @Override
    public boolean findTourKeyRect(@NonNull String keyName,
                                   @NonNull android.graphics.Rect outOnScreen) {
        return mKeyProbe != null && mKeyProbe.keyRectOnScreen(keyName, outOnScreen);
    }

    @Override
    public boolean findTourHelpButtonRect(@NonNull android.graphics.Rect outOnScreen) {
        return mChromeProbe != null && mChromeProbe.helpButtonRectOnScreen(outOnScreen);
    }

    @Override
    public boolean findTourCommandPaletteRect(@NonNull android.graphics.Rect outOnScreen) {
        return mChromeProbe != null && mChromeProbe.commandPaletteRectOnScreen(outOnScreen);
    }

    // ---- the overlay ---------------------------------------------------------------------------

    @Nullable
    private TourOverlayView obtainOverlay() {
        if (mOverlay != null) return mOverlay;
        ViewGroup content = mActivity.findViewById(android.R.id.content);
        if (content == null) return null;
        TourOverlayView overlay = new TourOverlayView(mActivity);
        overlay.setCallbacks(this);
        overlay.setTargets(new TourViewTargets(this, overlay));
        content.addView(overlay, TourOverlayView.buildLayoutParams());
        mOverlay = overlay;
        mOverlayHost = content;
        mPresentation = TourCardVisibility.NORMAL;
        // Every layout pass, because that is what the keyboard, a rotation, a dock style and a
        // font scale all come through as; a cached rect is a zone around where a control used to
        // be.
        mLayoutListener = () -> {
            refreshCardVisibility();
            overlay.refreshTarget();
        };
        content.getViewTreeObserver().addOnGlobalLayoutListener(mLayoutListener);
        overlay.setOnApplyWindowInsetsListener((view, insets) -> {
            applySystemBarInsets(overlay, insets);
            overlay.refreshTarget();
            return insets;
        });
        android.view.WindowInsets current = overlay.getRootWindowInsets();
        if (current != null) applySystemBarInsets(overlay, current);
        return overlay;
    }

    /**
     * The system bars' keep-out, so a card with nothing to anchor to does not rest under the
     * status bar or the gesture bar. The overlay is added to the content view, which on this
     * launcher runs edge to edge.
     */
    private static void applySystemBarInsets(@NonNull TourOverlayView overlay,
                                             @NonNull android.view.WindowInsets insets) {
        androidx.core.graphics.Insets bars =
            androidx.core.view.WindowInsetsCompat.toWindowInsetsCompat(insets, overlay)
                .getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars());
        overlay.setSystemBarInsets(bars.top, bars.bottom);
    }

    private void removeOverlay() {
        if (mOverlay == null) return;
        cancelCompletion();
        stopModelsLine();
        View overlay = mOverlay;
        mOverlay.dismiss();
        mOverlay = null;
        // The listener belongs to the host's observer, not the overlay's: a detached view answers
        // with a dead one, and the listener would outlive the run on the tree it was added to.
        if (mLayoutListener != null && mOverlayHost != null) {
            ViewTreeObserver observer = mOverlayHost.getViewTreeObserver();
            if (observer.isAlive()) observer.removeOnGlobalLayoutListener(mLayoutListener);
        }
        mLayoutListener = null;
        if (mOverlayHost != null) mOverlayHost.removeView(overlay);
        mOverlayHost = null;
    }
}
