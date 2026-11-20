package com.termux.app.wall.pageswitch;

import android.content.Context;
import android.view.MotionEvent;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;

/**
 * The page bar's owner on Home: answers what {@link PageBarView} asks of the activity (haptics,
 * motion, the accent, whether anything covers the wall), claims the bar's touch ahead of the
 * window's own gestures, and carries a chosen target out through the activity's navigator. The
 * slide itself lives in the view ({@link PageBarSlide}).
 */
public final class PageBarController implements PageBarView.Entry {

    /** What the controller needs from the activity. */
    public interface Host extends PageSwitchTargets.Navigator {
        boolean hapticsEnabled();
        boolean isReducedMotionEnabled();
        /** Whether something covers the wall (an editor, the drawer, a sheet, first-run setup): the bar takes no touch. */
        boolean isSuppressed();
        /** The Material accent through the scheme: the current page's glyph before it is made legible. */
        @ColorInt int accent();
    }

    /** Told when a slide begins or ends, for the dock's capsules to dim. */
    public interface SlidingListener {
        void onSlidingChanged(boolean sliding);
    }

    private final Host mHost;
    private final PageSwitchHaptics mHaptics;
    private final ArrayList<PageSwitchHitArea> mHitAreas = new ArrayList<>(1);
    @Nullable private SlidingListener mSlidingListener;
    /** The current stream is the bar's: it landed on the pill. */
    private boolean mStreamClaimed;

    public PageBarController(@NonNull Context context, @NonNull Host host) {
        mHost = host;
        mHaptics = new PageSwitchHaptics(context, host::hapticsEnabled);
    }

    public void setSlidingListener(@Nullable SlidingListener listener) {
        mSlidingListener = listener;
    }

    /**
     * Whether this stream goes to the views ahead of the window's own claims (the dock seam's
     * swipe, the surface long-press): one that lands on the pill.
     */
    public boolean claimsTouch(@NonNull MotionEvent ev) {
        int action = ev.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) {
            boolean claimed = false;
            if (!mHost.isSuppressed()) {
                for (int i = 0; i < mHitAreas.size() && !claimed; i++) {
                    claimed = mHitAreas.get(i).containsRaw(ev.getRawX(), ev.getRawY());
                }
            }
            mStreamClaimed = claimed;
        }
        boolean claimed = mStreamClaimed;
        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            mStreamClaimed = false;
        }
        return claimed;
    }

    @Override public void addHitArea(@NonNull PageSwitchHitArea area) {
        if (!mHitAreas.contains(area)) mHitAreas.add(area);
    }

    @Override public void removeHitArea(@NonNull PageSwitchHitArea area) {
        mHitAreas.remove(area);
    }

    @Override public boolean isSuppressed() {
        return mHost.isSuppressed();
    }

    @Override public boolean isReducedMotion() {
        return mHost.isReducedMotionEnabled();
    }

    @Override public int accent() {
        return mHost.accent();
    }

    @Override public void focusCrossed() {
        mHaptics.crossed();
    }

    /** The page changes here and only here: the tick, then the move. */
    @Override public void chosen(@NonNull PageSwitchModel.Target target) {
        mHaptics.committed();
        PageSwitchTargets.carryOut(target, mHost);
    }

    @Override public void slidingChanged(boolean sliding) {
        if (mSlidingListener != null) mSlidingListener.onSlidingChanged(sliding);
    }
}
