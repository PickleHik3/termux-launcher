package com.termux.app.launcher.drawer;

import android.content.Context;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.ViewCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.PagerSnapHelper;
import androidx.recyclerview.widget.RecyclerView;

/** Horizontal pages plus a vertical nested-scroll relay for the drawer-close claim. */
public final class AppDrawerHorizontalPagerView extends RecyclerView
    implements AppDrawerAppCellView.ClickGate {

    public interface PageSelectionListener {
        void onPageSelected(int page);
    }

    private final AppDrawerGestureArbiter mArbiter = new AppDrawerGestureArbiter();
    private final LockableLayoutManager mLayoutManager;
    private final PagerSnapHelper mSnapHelper = new PagerSnapHelper();
    private final float mTouchSlopPx;
    private final float mMinimumFlingVelocityPx;
    private final int[] mNestedConsumed = new int[2];
    @Nullable private PageSelectionListener mPageSelectionListener;
    @Nullable private VelocityTracker mVelocityTracker;
    private boolean mCloseNestedActive;
    private boolean mSuppressCellClick;
    private boolean mSuppressCellClickDuringTerminalDispatch;
    private float mLastRawY;
    private int mSelectedPage;

    public AppDrawerHorizontalPagerView(@NonNull Context context) {
        super(context);
        mTouchSlopPx = ViewConfiguration.get(context).getScaledTouchSlop();
        mMinimumFlingVelocityPx = ViewConfiguration.get(context).getScaledMinimumFlingVelocity();
        mLayoutManager = new LockableLayoutManager(context);
        setLayoutManager(mLayoutManager);
        setHasFixedSize(true);
        setItemViewCacheSize(1);
        setItemAnimator(null);
        setOverScrollMode(OVER_SCROLL_NEVER);
        setClipToPadding(false);
        mSnapHelper.attachToRecyclerView(this);
        addOnScrollListener(new OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                updateSelectedFromNearest();
            }

            @Override
            public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
                if (newState == SCROLL_STATE_IDLE) updateSelectedFromSnap();
            }
        });
    }

    public void setPageSelectionListener(@Nullable PageSelectionListener listener) {
        mPageSelectionListener = listener;
    }

    public int getSelectedPage() {
        return mSelectedPage;
    }

    public void setSelectedPage(int page, boolean smooth) {
        Adapter<?> adapter = getAdapter();
        int count = adapter == null ? 0 : adapter.getItemCount();
        mSelectedPage = AppDrawerPageModel.clampPage(page, count);
        if (count > 0) {
            if (smooth) smoothScrollToPosition(mSelectedPage);
            else scrollToPosition(mSelectedPage);
        }
        notifyPageSelected();
    }

    public void clampSelectedPage() {
        setSelectedPage(mSelectedPage, false);
    }

    public void stopForModeChange() {
        stopScroll();
        if (mCloseNestedActive) stopNestedScroll(ViewCompat.TYPE_TOUCH);
        resetGesture();
    }

    public boolean isCloseClaimed() {
        return mArbiter.isDrawerDrag();
    }

    public boolean isHorizontalScrollLocked() {
        return !mLayoutManager.isHorizontalEnabled();
    }

    public void setDragLocked(boolean locked) {
        if (locked) stopScroll();
        mLayoutManager.setHorizontalEnabled(!locked);
    }

    @Override
    public boolean suppressCellClick() {
        return mSuppressCellClick || mSuppressCellClickDuringTerminalDispatch;
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        int action = ev.getActionMasked();
        boolean terminal = action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL;
        if (action == MotionEvent.ACTION_DOWN) beginGesture(ev);
        if (action == MotionEvent.ACTION_MOVE) observeMove(ev);
        mSuppressCellClickDuringTerminalDispatch = terminal && mSuppressCellClick;
        if (terminal && mArbiter.isDrawerDrag()) {
            if (action == MotionEvent.ACTION_UP && mVelocityTracker != null) {
                mVelocityTracker.addMovement(ev);
                mVelocityTracker.computeCurrentVelocity(1000);
                float velocityY = mVelocityTracker.getYVelocity();
                if (Math.abs(velocityY) >= mMinimumFlingVelocityPx) {
                    // Finger-down velocity is positive; nested scroll velocity is negative.
                    dispatchNestedPreFling(0f, -velocityY);
                }
            }
        }
        // Observer only. The retained child target receives the stream exactly once.
        try {
            boolean handled = super.dispatchTouchEvent(ev);
            if (terminal) {
                if (mCloseNestedActive) stopNestedScroll(ViewCompat.TYPE_TOUCH);
                resetGesture();
            }
            return handled;
        } finally {
            mSuppressCellClickDuringTerminalDispatch = false;
        }
    }

    private void beginGesture(@NonNull MotionEvent ev) {
        resetGesture();
        mLayoutManager.setHorizontalEnabled(false);
        mLastRawY = ev.getRawY();
        mArbiter.begin(ev.getRawX(), ev.getRawY(),
            AppDrawerGestureArbiter.Eligibility.allClear());
        mVelocityTracker = VelocityTracker.obtain();
        mVelocityTracker.addMovement(ev);
    }

    private void observeMove(@NonNull MotionEvent ev) {
        if (mVelocityTracker != null) mVelocityTracker.addMovement(ev);
        AppDrawerGestureArbiter.Claim before = mArbiter.claim();
        AppDrawerGestureArbiter.Claim claim = mArbiter.evaluate(
            ev.getRawX(), ev.getRawY(), mTouchSlopPx);
        float deltaRawY = ev.getRawY() - mLastRawY;
        mLastRawY = ev.getRawY();
        if (claim == AppDrawerGestureArbiter.Claim.PAGE_SWIPE) {
            if (before != AppDrawerGestureArbiter.Claim.PAGE_SWIPE)
                mLayoutManager.setHorizontalEnabled(true);
            return;
        }
        if (claim != AppDrawerGestureArbiter.Claim.DRAWER_DRAG) return;
        if (before != AppDrawerGestureArbiter.Claim.DRAWER_DRAG) {
            mLayoutManager.setHorizontalEnabled(false);
            mSuppressCellClick = true;
            cancelLongPress();
            for (int i = 0; i < getChildCount(); i++) {
                View page = getChildAt(i);
                if (page != null) page.cancelLongPress();
            }
            mCloseNestedActive = startNestedScroll(ViewCompat.SCROLL_AXIS_VERTICAL,
                ViewCompat.TYPE_TOUCH);
        }
        if (mCloseNestedActive) {
            mNestedConsumed[0] = 0;
            mNestedConsumed[1] = 0;
            dispatchNestedPreScroll(0, Math.round(-deltaRawY), mNestedConsumed, null,
                ViewCompat.TYPE_TOUCH);
        }
    }

    private void resetGesture() {
        mArbiter.reset();
        mCloseNestedActive = false;
        mSuppressCellClick = false;
        mLayoutManager.setHorizontalEnabled(true);
        if (mVelocityTracker != null) {
            mVelocityTracker.recycle();
            mVelocityTracker = null;
        }
    }

    private void updateSelectedFromSnap() {
        View snap = mSnapHelper.findSnapView(mLayoutManager);
        if (snap != null) selectPage(mLayoutManager.getPosition(snap));
    }

    private void updateSelectedFromNearest() {
        if (getScrollState() == SCROLL_STATE_IDLE) return;
        View nearest = mSnapHelper.findSnapView(mLayoutManager);
        if (nearest != null) selectPage(mLayoutManager.getPosition(nearest));
    }

    private void selectPage(int page) {
        Adapter<?> adapter = getAdapter();
        int count = adapter == null ? 0 : adapter.getItemCount();
        int selected = AppDrawerPageModel.clampPage(page, count);
        if (selected == mSelectedPage) return;
        mSelectedPage = selected;
        notifyPageSelected();
    }

    private void notifyPageSelected() {
        PageSelectionListener listener = mPageSelectionListener;
        if (listener != null) listener.onPageSelected(mSelectedPage);
    }

    @NonNull
    public PagerSnapHelper getPagerSnapHelper() {
        return mSnapHelper;
    }

    private static final class LockableLayoutManager extends LinearLayoutManager {
        private boolean mHorizontalEnabled = true;

        LockableLayoutManager(@NonNull Context context) {
            super(context, HORIZONTAL, false);
        }

        void setHorizontalEnabled(boolean enabled) {
            mHorizontalEnabled = enabled;
        }

        boolean isHorizontalEnabled() {
            return mHorizontalEnabled;
        }

        @Override
        public boolean canScrollHorizontally() {
            return mHorizontalEnabled && super.canScrollHorizontally();
        }
    }
}
