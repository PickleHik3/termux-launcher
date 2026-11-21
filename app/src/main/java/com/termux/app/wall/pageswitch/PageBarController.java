package com.termux.app.wall.pageswitch;

import android.content.Context;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;

/**
 * The page bar's owner: answers what {@link PageBarView} asks of the activity (haptics, motion,
 * the accent, whether anything covers the wall), claims the bar's touch ahead of the window's own
 * gestures, and carries a chosen target out through the activity's navigator. The slide itself
 * lives in the view ({@link PageBarSlide}).
 *
 * <p>It owns both entries. Home's bar lives in the wall. The extra-keys page switcher key pops up
 * a second bar in the overlay layer, made on the first touch of the key, a small air above the
 * band the key stands on and as wide as the layer. The key feeds it the finger in raw screen
 * coordinates ({@link #keyDown}, {@link #keyMove}, {@link #keyUp}); a slide and release commit as on
 * Home and put it away, a release without a slide leaves it up for one use ({@link PageBarPopup}).</p>
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
        /** The wall's pages as the bar shows them, right now (the key's popup asks as it opens). */
        @NonNull PageBarModel pageBarModel();
        /** The ink sheets and menus draw their labels in: the popup's glyphs and outline. */
        @ColorInt int ink();
    }

    /** Told when a slide begins or ends, for the dock's capsules to dim. */
    public interface SlidingListener {
        void onSlidingChanged(boolean sliding);
    }

    private final Host mHost;
    private final PageSwitchHaptics mHaptics;
    private final Context mContext;
    @Nullable private final FrameLayout mLayer;
    private final PageBarPopup mPopup = new PageBarPopup();
    private final int[] mLayerAt = new int[2];
    @Nullable private PageBarView mPopupView;
    /** The popup's left and top on the screen, for turning raw coordinates into the bar's. */
    private float mOriginX;
    private float mOriginY;
    /** An open popup's current stream began outside the bar and was consumed as a dismissal. */
    private boolean mDismissStream;
    private final ArrayList<PageSwitchHitArea> mHitAreas = new ArrayList<>(1);
    @Nullable private SlidingListener mSlidingListener;
    /** The current stream is the bar's: it landed on the pill. */
    private boolean mStreamClaimed;

    /** @param layer the overlay layer the key's popup is raised in; null means no popup */
    public PageBarController(@NonNull Context context, @NonNull Host host,
                             @Nullable FrameLayout layer) {
        mContext = context;
        mHost = host;
        mLayer = layer;
        mHaptics = new PageSwitchHaptics(context, host::hapticsEnabled);
        if (layer != null) {
            // The lifted glyphs rise above the popup's pill, outside the bar's own bounds.
            layer.setClipChildren(false);
            layer.setOnTouchListener((v, ev) -> onLayerTouch(ev));
        }
    }

    public boolean isPopupOpen() {
        return mPopup.shown();
    }

    /** Back, a pause, a detach or a page change by other means: put the popup away, switching nothing. */
    public void close() {
        if (mPopup.shown()) putAway();
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
            boolean claimed = mPopup.shown();
            if (!claimed && !mHost.isSuppressed()) {
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

    // ---- The key's popup, in raw screen coordinates -----------------------------------------------

    /**
     * The key was touched: the bar appears with its touch row's bottom on {@code bandTopRaw}, the
     * top of the extra-keys band the key stands on, so its slab stands {@link PageBarLayout#GAP_DP}
     * above the keys.
     *
     * @return whether the popup opened; false leaves the key to its plain behaviour
     */
    public boolean keyDown(float rawX, float rawY, int bandTopRaw) {
        FrameLayout layer = mLayer;
        if (layer == null || mHost.isSuppressed() || layer.getWidth() <= 0) return false;
        PageBarModel model = mHost.pageBarModel();
        if (model.entries().isEmpty()) return false;
        if (mPopup.shown()) putAway();
        PageBarView view = ensurePopupView(layer);
        view.setFixedInk(mHost.ink(), mHost.accent());
        view.setModel(model);
        int width = layer.getWidth();
        int height = Math.round(PageBarLayout.bandHeightDp()
            * mContext.getResources().getDisplayMetrics().density);
        layer.getLocationOnScreen(mLayerAt);
        int top = Math.max(0, bandTopRaw - mLayerAt[1] - height);
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) view.getLayoutParams();
        if (lp.topMargin != top || lp.height != height) {
            lp.topMargin = top;
            lp.height = height;
            view.setLayoutParams(lp);
        }
        if (view.getWidth() != width || view.getHeight() != height || view.getTop() != top) {
            view.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
            view.layout(0, top, width, top + height);
        }
        mOriginX = mLayerAt[0];
        mOriginY = mLayerAt[1] + top;
        view.setVisibility(View.VISIBLE);
        mPopup.keyDown();
        view.feed(MotionEvent.ACTION_DOWN, rawX - mOriginX, rawY - mOriginY);
        return true;
    }

    public void keyMove(float rawX, float rawY) {
        PageBarView view = mPopupView;
        if (view == null || !mPopup.touching()) return;
        view.feed(MotionEvent.ACTION_MOVE, rawX - mOriginX, rawY - mOriginY);
    }

    public void keyUp(float rawX, float rawY) {
        PageBarView view = mPopupView;
        if (view == null || !mPopup.touching()) return;
        if (view.isSliding()) {
            view.feed(MotionEvent.ACTION_UP, rawX - mOriginX, rawY - mOriginY);
            // Whatever the slide ended in, the bar is done.
            putAway();
            return;
        }
        // A tap on the key: nothing chosen, the bar stays for one use.
        view.abandon();
        mPopup.keyReleased(false);
        if (mLayer != null) mLayer.setClickable(true);
    }

    public void keyCancel() {
        if (mPopup.shown()) putAway();
    }

    @NonNull
    private PageBarView ensurePopupView(@NonNull FrameLayout layer) {
        PageBarView view = mPopupView;
        if (view != null) return view;
        view = new PageBarView(mContext);
        view.setKeyEntry(true);
        view.setVisibility(View.GONE);
        view.setEntry(this);
        view.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            @Override public void onViewAttachedToWindow(View v) {
            }

            @Override public void onViewDetachedFromWindow(View v) {
                mPopup.putAway();
            }
        });
        layer.addView(view, new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT));
        mPopupView = view;
        return view;
    }

    /** GONE, its lift given back, and the layer no longer takes touches. */
    private void putAway() {
        mPopup.putAway();
        PageBarView view = mPopupView;
        if (view != null) {
            view.release();
            view.setVisibility(View.GONE);
        }
        if (mLayer != null) mLayer.setClickable(false);
    }

    /** A touch on the open popup's layer that no glyph took: a tap anywhere else puts it away. */
    private boolean onLayerTouch(@NonNull MotionEvent ev) {
        boolean open = mPopup.state() == PageBarPopup.State.OPEN;
        if (!open && !mDismissStream) return false;
        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                if (open) {
                    putAway();
                    mDismissStream = true;
                }
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                mDismissStream = false;
                return true;
            default:
                return true;
        }
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
    @Override public void chosen(@NonNull PageBarModel.Target target) {
        mHaptics.committed();
        PageSwitchTargets.carryOut(target, mHost);
    }

    @Override public void touchEnded() {
        if (mPopup.shown()) putAway();
    }

    @Override public void slidingChanged(boolean sliding) {
        if (mSlidingListener != null) mSlidingListener.onSlidingChanged(sliding);
    }
}
