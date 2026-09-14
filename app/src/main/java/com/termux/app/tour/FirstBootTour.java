package com.termux.app.tour;

import android.app.Activity;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.R;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;

/**
 * The run, assembled: the state machine, the overlay, the targets and every signal, held in one
 * place so the activity owns a field and a handful of one-line calls rather than a tour.
 *
 * <p>Everything the launcher has to tell it arrives through the {@code on...} calls below, every
 * one of them made from the single place in the chrome that already decides the thing — a Host
 * interface's new default method, or a funnel every path through the feature already takes. The
 * states among them are edge-triggered in {@link TourSignalRelay}, because the chrome re-applies
 * them constantly and a card cleared by a state the user never put it in is the failure mode the
 * whole run is built against.
 */
public final class FirstBootTour implements TourController.Listener, TourOverlayView.Callbacks,
    TourSignals.Listener, TourViewTargets.ViewFinder {

    /**
     * The one control the tour points at that the chrome measures for itself: the in-app
     * keyboard's space bar is a key inside a rendered keyboard, not a view with an id.
     */
    public interface SpaceBarProbe {
        boolean spaceBarRectOnScreen(@NonNull android.graphics.Rect out);
    }

    @NonNull private final Activity mActivity;
    @NonNull private final TourController mController;
    @NonNull private final TourSignalRelay mSignals = new TourSignalRelay();
    @Nullable private final SpaceBarProbe mSpaceBarProbe;

    @Nullable private TourOverlayView mOverlay;
    @Nullable private ViewGroup mOverlayHost;
    @Nullable private ViewTreeObserver.OnGlobalLayoutListener mLayoutListener;

    /** The removed footage onboarding's own once-per-install preferences file and key. */
    private static final String LEGACY_ONBOARDING_PREFS_NAME = "termux_first_launch";
    private static final String LEGACY_ONBOARDING_COMPLETED_VERSION_KEY =
        "onboarding_completed_version";
    private static final int LEGACY_ONBOARDING_COMPLETED_VERSION = 2;

    public FirstBootTour(@NonNull Activity activity,
                         @NonNull TermuxAppSharedPreferences preferences,
                         @Nullable SpaceBarProbe spaceBarProbe) {
        mActivity = activity;
        mSpaceBarProbe = spaceBarProbe;
        migrateLegacyOnboardingCompletionIfNeeded(activity, preferences);
        mController = new TourController(TourRun.steps(), new TourPreferences(preferences),
            SystemClock::uptimeMillis);
        mController.setListener(this);
        mSignals.setTourSignalListener(this);
    }

    /**
     * Treats a completed run of the removed footage onboarding (`FirstLaunchOnboarding`, dropped
     * when this overlay tour replaced it) as a completed run of this tour, so an install that
     * already sat through the old one is never shown this run too. Runs once, ever, guarded by
     * its own flag rather than by {@link TermuxAppSharedPreferences#getFirstBootTourCompletedVersion()}
     * — Replay legitimately zeroes that version, and reading it back after that would look
     * exactly like "never migrated" and clobber the replay to "seen".
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
            preferences.setFirstBootTourCompletedVersion(TourController.RUN_VERSION);
            // The footage tour ran the permission chain once already; an upgrade stays silent.
            preferences.setFirstRunChainDone(true);
        }
        preferences.setFirstBootTourLegacyOnboardingMigrated(true);
    }

    /**
     * Starts the run for someone who has just finished first launch. Does nothing for a user who
     * has already been through one, which is what keeps an upgrade silent.
     */
    public void startIfNeeded() {
        mController.startIfNeeded();
    }

    /** Starts the run from card one, whatever came before: what Replay will ask for. */
    public void restart() {
        mController.start();
    }

    /**
     * Picks an unfinished run back up after a process death or a trip out of the launcher.
     *
     * @return true when a run was actually resumed.
     */
    public boolean resumeIfInProgress() {
        return mController.resumeIfInProgress();
    }

    /** Whether a card is up right now — for suppressing dialogs that would draw over it. */
    public boolean isShowing() {
        return mController.isRunning();
    }

    /** The place the status bar is showing for, once it has settled there. */
    public void onPlaceSettled(@Nullable String placeId) {
        mSignals.onPlaceSettled(placeId);
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
     * @param userDriven false when the launcher closed the plane itself (HOME, a rotation, a
     *     preference reload), which the run must not read as the user's swipe
     */
    public void onDrawerOpenSettled(boolean open, boolean userDriven) {
        mSignals.onDrawerOpenSettled(open, userDriven);
    }

    /** A split was asked for. */
    public void onPaneSplit() {
        mSignals.onPaneSplit();
    }

    /** A pane's corner menu was raised. */
    public void onPaneCornerMenuOpened() {
        mSignals.onPaneCornerMenuOpened();
    }

    /** The A-Z row's scrub launched an app. */
    public void onAppLaunchedFromScrub() {
        mSignals.onAppLaunchedFromScrub();
    }

    /** The command palette came up. */
    public void onPaletteOpened() {
        mSignals.onPaletteOpened();
    }

    // TourController.Listener

    @Override
    public void onTourStepShown(TourStep step, int stage) {
        TourOverlayView overlay = obtainOverlay();
        if (overlay == null) {
            TourLog.d("card " + step.id + ":" + stage + " has nowhere to show: no content view");
            return;
        }
        overlay.showStep(step, stage);
        if (TourLog.enabled()) {
            android.graphics.Rect rect = overlay.currentTargetRect();
            TourLog.d("card " + step.id + ":" + stage + " shown, target \""
                + step.targetIdAt(stage) + "\" at " + TourLog.describe(rect)
                + (rect == null ? " (" + overlay.currentMissReason() + ")" : "")
                + ", waiting for " + describeWait(step, stage));
        }
    }

    @Override
    public void onTourFinished(boolean skipped) {
        TourLog.d("run finished, skipped=" + skipped);
        removeOverlay();
    }

    // TourOverlayView.Callbacks

    @Override
    public void onTourSkipTapped() {
        TourStep step = mController.currentStep();
        TourLog.d("skip tapped on card " + (step == null ? "none" : step.id)
            + ":" + mController.currentStage());
        mController.skip();
    }

    @Override
    public void onTourFinishTapped() {
        TourLog.d("done tapped on the closing card");
        mController.finish();
    }

    /** What the card that is up is waiting for, for the log. */
    @NonNull
    private static String describeWait(@NonNull TourStep step, int stage) {
        String signal = step.signalAt(stage);
        return signal == null ? "its own button" : signal;
    }

    @Override
    public void onTourCopyCommandsTapped() {
        android.content.ClipboardManager clipboard = (android.content.ClipboardManager)
            mActivity.getSystemService(android.content.Context.CLIPBOARD_SERVICE);
        if (clipboard == null) return;
        TourEdition edition = TourEdition.of(mActivity.getPackageName());
        clipboard.setPrimaryClip(android.content.ClipData.newPlainText(
            mActivity.getString(R.string.tour_copy_commands),
            mActivity.getString(TourClosingCard.commands(edition))));
    }

    // TourSignals.Listener

    @Override
    public void onTourSignal(String signalId) {
        if (!TourLog.enabled()) {
            mController.onSignal(signalId);
            return;
        }
        // Snapshotted around the call, because "did that clear the card" is the one question a
        // device pass has to be able to answer and nothing downstream records it.
        TourStep before = mController.currentStep();
        int stageBefore = mController.currentStage();
        mController.onSignal(signalId);
        TourStep after = mController.currentStep();
        int stageAfter = mController.currentStage();
        boolean moved = before != after || stageBefore != stageAfter;
        TourLog.d("signal " + signalId + " while card "
            + (before == null ? "none" : before.id + ":" + stageBefore)
            + (moved ? " — cleared it, now " + (after == null ? "the run is over"
                : after.id + ":" + stageAfter)
            : " — ignored"));
    }

    // TourViewTargets.ViewFinder

    @Override
    @Nullable
    public View findTourView(int viewId) {
        return mActivity.findViewById(viewId);
    }

    @Override
    public boolean findTourSpaceBarRect(@NonNull android.graphics.Rect outOnScreen) {
        return mSpaceBarProbe != null && mSpaceBarProbe.spaceBarRectOnScreen(outOnScreen);
    }

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
        // Every layout pass, because that is what the keyboard, a rotation, a dock style and a
        // font scale all come through as; a cached rect is a glow around where a control used to
        // be.
        mLayoutListener = overlay::refreshTarget;
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
