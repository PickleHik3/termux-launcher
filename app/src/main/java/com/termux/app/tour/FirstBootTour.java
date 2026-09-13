package com.termux.app.tour;

import android.app.Activity;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;

/**
 * The run, assembled: the state machine, the overlay, the targets and the two signals that are
 * wired today, held in one place so the activity owns a field and three calls rather than a tour.
 *
 * <p>Everything the launcher has to tell it arrives through {@link #onPlaceSettled} and
 * {@link #onStatusBarCollapsedSettled} — both edge-triggered, both called from where the chrome
 * already decides the thing, so no controller grows a second listener slot and the activity grows
 * no public method.
 */
public final class FirstBootTour implements TourController.Listener, TourOverlayView.Callbacks,
    TourSignals.Listener, TourViewTargets.ViewFinder {

    @NonNull private final Activity mActivity;
    @NonNull private final TourController mController;
    @NonNull private final TourSignalRelay mSignals = new TourSignalRelay();

    @Nullable private TourOverlayView mOverlay;
    @Nullable private ViewGroup mOverlayHost;
    @Nullable private ViewTreeObserver.OnGlobalLayoutListener mLayoutListener;

    public FirstBootTour(@NonNull Activity activity,
                         @NonNull TermuxAppSharedPreferences preferences) {
        mActivity = activity;
        mController = new TourController(TourRun.steps(), new TourPreferences(preferences),
            SystemClock::uptimeMillis);
        mController.setListener(this);
        mSignals.setTourSignalListener(this);
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

    /** Picks an unfinished run back up after a process death or a trip out of the launcher. */
    public void resumeIfInProgress() {
        mController.resumeIfInProgress();
    }

    /** The place the status bar is showing for, once it has settled there. */
    public void onPlaceSettled(@Nullable String placeId) {
        mSignals.onPlaceSettled(placeId);
    }

    /** The status bar's resting state, once it has settled. */
    public void onStatusBarCollapsedSettled(boolean collapsed) {
        mSignals.onStatusBarCollapsedSettled(collapsed);
    }

    // TourController.Listener

    @Override
    public void onTourStepShown(TourStep step, int stage) {
        TourOverlayView overlay = obtainOverlay();
        if (overlay == null) return;
        overlay.showStep(step, stage);
    }

    @Override
    public void onTourFinished(boolean skipped) {
        removeOverlay();
    }

    // TourOverlayView.Callbacks

    @Override
    public void onTourSkipTapped() {
        mController.skip();
    }

    @Override
    public void onTourFinishTapped() {
        mController.finish();
    }

    // TourSignals.Listener

    @Override
    public void onTourSignal(String signalId) {
        mController.onSignal(signalId);
    }

    // TourViewTargets.ViewFinder

    @Override
    @Nullable
    public View findTourView(int viewId) {
        return mActivity.findViewById(viewId);
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
            overlay.refreshTarget();
            return insets;
        });
        return overlay;
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
