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
     * The controls the tour points at that the chrome measures for itself: a key of the in-app
     * keyboard is a cap inside a rendered keyboard, not a view with an id.
     */
    public interface KeyProbe {
        boolean keyRectOnScreen(@NonNull String keyName, @NonNull android.graphics.Rect out);
    }

    /**
     * What else is in front of the user right now.
     *
     * <p>Asked rather than told, because these four surfaces open and close along a dozen paths
     * each and a card left drawing over one of them is exactly what a missed path looks like. It
     * is read on every layout pass — every one of them lays the window out — and again whenever
     * the chrome says something moved.
     */
    public interface ChromeProbe {
        boolean isAppDrawerUp();

        boolean isCommandPaletteUp();

        boolean isTerminalSheetUp();

        boolean isSurfaceEditorUp();
    }

    /**
     * The wall, for the one thing the run does to it: a run that begins — first launch, Replay, a
     * resume after a process death — begins on the terminal, because that is where every card but
     * the first two is taught. Mid-run the run never moves the wall itself; a card whose control
     * is on another place asks the user back instead.
     */
    public interface WallHost {
        void returnToTerminal();
    }

    /** The place the run is taught on: the wall's own home page. */
    public static final String HOME_PLACE = com.termux.app.wall.PaneWallPolicy.homePage().name();

    @NonNull private final Activity mActivity;
    @NonNull private final TourController mController;
    @NonNull private final TourSignalRelay mSignals = new TourSignalRelay();
    @Nullable private final KeyProbe mKeyProbe;
    @Nullable private ChromeProbe mChromeProbe;
    @Nullable private WallHost mWallHost;
    /** What the in-app keyboard has latched, for the chord cards' walking glow. */
    private boolean mCtrlLatched;
    private boolean mAltLatched;
    private boolean mShiftLatched;

    @Nullable private TourOverlayView mOverlay;
    @Nullable private ViewGroup mOverlayHost;
    @Nullable private ViewTreeObserver.OnGlobalLayoutListener mLayoutListener;
    private int mPresentation = TourCardVisibility.NORMAL;

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
        // The two facts the run varies on are read from the phone when the run is wired up; until
        // then the run is built for a launcher that is not the home app with the keyboard down.
        mController = new TourController(TourRun.steps(new TourRun.RunContext(false, false)),
            new TourPreferences(preferences), SystemClock::uptimeMillis);
        mController.setListener(this);
        mSignals.setTourSignalListener(this);
        mSignals.setHomePlace(HOME_PLACE);
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

    /** What else is covering the home screen, for the card visibility policy. */
    public void setChromeProbe(@Nullable ChromeProbe probe) {
        mChromeProbe = probe;
        onChromeChanged();
    }

    /** The wall, for bringing the run home when it begins. */
    public void setWallHost(@Nullable WallHost host) {
        mWallHost = host;
    }

    /**
     * Starts the run for someone who has just finished first launch. Does nothing for a user who
     * has already been through one, which is what keeps an upgrade silent.
     */
    public void startIfNeeded() {
        if (mController.isFinished() || mController.isRunning()) return;
        bringTheWallHome();
        mController.startIfNeeded();
    }

    /** Starts the run from card one, whatever came before: what Replay will ask for. */
    public void restart() {
        bringTheWallHome();
        mController.start();
    }

    /**
     * Picks an unfinished run back up after a process death or a trip out of the launcher.
     *
     * @return true when a run was actually resumed.
     */
    public boolean resumeIfInProgress() {
        boolean resumed = mController.resumeIfInProgress();
        if (resumed) bringTheWallHome();
        return resumed;
    }

    /**
     * A run begins on the terminal. Settings hands the launcher back on whatever place it was
     * left on, and a process death restores the wall to the place it last rested on; a card
     * asking for the + or the keyboard from the display place asks for a control that is not
     * there. The wall settles and reports the place through {@link #onPlaceSettled}, so the card
     * that is up is re-decided the moment it lands.
     */
    private void bringTheWallHome() {
        if (mSignals.isOnHomePlace()) return;
        // Except for the card that is asking the user to swipe back themselves: a resume on the
        // first card's second half is not helped by having the swipe made for it.
        TourStep step = mController.currentStep();
        if (step != null
                && TourSignals.PLACE_RETURNED.equals(step.signalAt(mController.currentStage())))
            return;
        TourLog.d("the run begins on the " + HOME_PLACE.toLowerCase(java.util.Locale.ROOT)
            + " place; bringing the wall back to it");
        if (mWallHost != null) mWallHost.returnToTerminal();
    }

    /** Whether a card is up right now — for suppressing dialogs that would draw over it. */
    public boolean isShowing() {
        return mController.isRunning();
    }

    /**
     * The place the wall has settled on. Besides the first card's own signals this decides
     * whether the card that is up can be taught here at all: the wall moved, so the card is
     * re-decided on the spot rather than on the next layout pass.
     */
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
     * @param userDriven false when the launcher closed the plane itself (HOME, a rotation, a
     *     preference reload), which the run must not read as the user's swipe
     */
    public void onDrawerOpenSettled(boolean open, boolean userDriven) {
        mSignals.onDrawerOpenSettled(open, userDriven);
    }

    /**
     * The sessions the launcher holds, each time the sessions list has been rebuilt.
     *
     * @param count how many there are, or -1 when there is nothing to count yet
     * @param currentSessionId the one that is current, or null when there is none
     */
    public void onSessionsSettled(int count, @Nullable String currentSessionId) {
        mSignals.onSessionsSettled(count, currentSessionId);
    }

    /** The window the launcher is showing, each time the active pane has settled on it. */
    public void onActiveWindowSettled(@Nullable String windowId) {
        mSignals.onActiveWindowSettled(windowId);
    }

    /**
     * What the in-app keyboard has latched. The chord cards glow the key they are still waiting
     * for, so the glow walks Ctrl, then Alt, then the key itself as the user taps each one.
     */
    public void onKeyboardModifiersChanged(boolean ctrl, boolean alt, boolean shift) {
        if (mCtrlLatched == ctrl && mAltLatched == alt && mShiftLatched == shift) return;
        mCtrlLatched = ctrl;
        mAltLatched = alt;
        mShiftLatched = shift;
        applyChordGlow();
    }

    private void applyChordGlow() {
        TourOverlayView overlay = mOverlay;
        if (overlay == null) return;
        overlay.setChordGlowIndex(TourChordGlow.indexFor(mController.currentStep(),
            mCtrlLatched, mAltLatched, mShiftLatched));
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
     * Something that covers the home screen whole opened or closed. Cheap, and safe to call on
     * anything that might have moved one of them.
     */
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
        int presentation = TourCardVisibility.decide(step, mController.currentStage(), chrome,
            mSignals.isOnHomePlace());
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
        ChromeProbe probe = mChromeProbe;
        if (probe == null) return up;
        if (probe.isAppDrawerUp()) up.add(TourChrome.DRAWER);
        if (probe.isCommandPaletteUp()) up.add(TourChrome.PALETTE);
        if (probe.isTerminalSheetUp()) up.add(TourChrome.TERMINAL_SHEET);
        if (probe.isSurfaceEditorUp()) up.add(TourChrome.SURFACE_EDITOR);
        return up;
    }

    @NonNull
    private static String describePresentation(int presentation) {
        if (presentation == TourCardVisibility.HIDDEN) return "hidden";
        if (presentation == TourCardVisibility.COMPACT_TOP) return "at the top of the screen";
        if (presentation == TourCardVisibility.AWAY) return "asking for the way back to the terminal";
        return "against its control";
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
        applyChordGlow();
        refreshCardVisibility();
        if (TourLog.enabled()) {
            android.graphics.Rect rect = overlay.currentTargetRect();
            TourLog.d("card " + step.id + ":" + stage + " shown, target \""
                + overlay.currentTargetId() + "\" at " + TourLog.describe(rect)
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
    public void onTourCopyCommandTapped(int commandRes) {
        android.content.ClipboardManager clipboard = (android.content.ClipboardManager)
            mActivity.getSystemService(android.content.Context.CLIPBOARD_SERVICE);
        if (clipboard == null) return;
        TourEdition edition = TourEdition.of(mActivity.getPackageName());
        String text = commandRes == 0
            ? TourClosingCard.copyAllText(edition, mActivity::getString)
            : mActivity.getString(commandRes);
        if (text.isEmpty()) return;
        clipboard.setPrimaryClip(android.content.ClipData.newPlainText(
            mActivity.getString(R.string.tour_copy_commands), text));
    }

    @Override
    public void onTourDocsTapped() {
        String url = mActivity.getString(R.string.tour_docs_url);
        try {
            mActivity.startActivity(new android.content.Intent(
                android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url))
                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (android.content.ActivityNotFoundException notFound) {
            TourLog.d("no browser to open " + url);
        }
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
    public boolean findTourKeyRect(@NonNull String keyName,
                                   @NonNull android.graphics.Rect outOnScreen) {
        return mKeyProbe != null && mKeyProbe.keyRectOnScreen(keyName, outOnScreen);
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
