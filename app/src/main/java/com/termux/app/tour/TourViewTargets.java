package com.termux.app.tour;

import android.graphics.Rect;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.R;

/**
 * {@link TourTargets} over the live chrome: a view id per target, measured in the overlay's own
 * space every time it is asked.
 *
 * <p>The views are looked up through a one-method finder rather than held, because the chrome the
 * run points at is re-inflated and re-parented under it — a rotation, a place change and the
 * surface editor all replace the status bar the tour is glowing.
 *
 * <p>Only the status bar answers today. The others return null until their phase lands, which the
 * overlay is built to expect: a card with no glow is a card the user can still read and skip.
 */
public final class TourViewTargets implements TourTargets {

    /** The single seam the tour needs into the activity's view tree. */
    public interface ViewFinder {
        @Nullable
        View findTourView(int viewId);
    }

    @NonNull private final ViewFinder mFinder;
    @NonNull private final View mOverlay;
    private final int[] mLocation = new int[2];

    public TourViewTargets(@NonNull ViewFinder finder, @NonNull View overlay) {
        mFinder = finder;
        mOverlay = overlay;
    }

    @Override
    @Nullable
    public Rect rectFor(@NonNull String targetId) {
        int viewId = viewIdFor(targetId);
        return viewId == 0 ? null : rectInOverlay(mFinder.findTourView(viewId));
    }

    private static int viewIdFor(@NonNull String targetId) {
        if (STATUS_BAR.equals(targetId)) return R.id.terminal_window_bar_host;
        // TODO(tour phase 2): the plus button, window chip, split key, pane corner, dock,
        //  A-Z row and space bar, each measured from the control the card points at.
        return 0;
    }

    /** A view's bounds in the overlay's space, or null while it is not on screen. */
    @Nullable
    private Rect rectInOverlay(@Nullable View view) {
        if (view == null || view.getVisibility() != View.VISIBLE
            || view.getWidth() <= 0 || view.getHeight() <= 0
            || mOverlay.getWidth() <= 0 || mOverlay.getHeight() <= 0)
            return null;
        mOverlay.getLocationInWindow(mLocation);
        int overlayLeft = mLocation[0];
        int overlayTop = mLocation[1];
        view.getLocationInWindow(mLocation);
        int left = mLocation[0] - overlayLeft;
        int top = mLocation[1] - overlayTop;
        return new Rect(left, top, left + view.getWidth(), top + view.getHeight());
    }
}
