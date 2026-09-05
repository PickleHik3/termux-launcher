package com.termux.app.wall;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;

import com.termux.app.terminal.Motion;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * The pane wall: fixed, full-size places side by side — Widgets, Terminal, Display — of which
 * exactly one is on screen at rest. It wraps the terminal's pane host as its middle page, so the
 * terminal never resizes for the wall and {@code TerminalPaneController} never learns the wall
 * exists.
 *
 * <p>Not a {@code ViewPager2}: the wall's touches arrive from the status bar rather than from
 * these pages, its pages must never be recycled (a recreated terminal or X surface is a lost
 * session), and its centre page runs its own {@code requestDisallowInterceptTouchEvent} traffic.
 * Three children, one offset and one spring is the whole mechanism.
 *
 * <p>Every page is laid out at the host's size and moved with {@code translationX}, so a page
 * change and a whole drag cost no layout work.
 *
 * <p>The terminal page's margins are every page's margins. The activity lays the pane host out
 * inside the frame insets the surface editor decides — the dock's side gap, the border's air —
 * by setting margins on it, and the places beside it have to sit inside the same frame or the
 * wall reads as three differently sized sheets. So the wall reads the terminal page's
 * {@link MarginLayoutParams} and applies them to all of its pages; margins on the other pages are
 * ignored.
 */
public final class PaneWallLayout extends ViewGroup {

    /**
     * A slide is the terminal's own window pan: the same settle curve over the same time for a
     * full width, shortened in proportion when the wall has less far to go, so a release near
     * rest lands quickly and a tap from one place to the next travels like a window switch.
     */
    private static final long SLIDE_FULL_MS = 560L;
    private static final long SLIDE_MIN_MS = 180L;

    public interface Listener {
        /** The wall has committed to a different page; the slide may still be running. */
        default void onWallPageChanged(@NonNull PaneWallPage page) { }
        /** The slide has stopped, with {@code page} at rest on screen. */
        default void onWallPageSettled(@NonNull PaneWallPage page) { }
        /** The wall moved: {@code offsetPx} is signed distance from the current page's rest. */
        default void onWallOffsetChanged(float offsetPx) { }
        /**
         * A drag was under way and something else moved the wall — {@link #goTo}, or the
         * gestures being switched off. Whoever was driving the drag has to let go of the finger:
         * the wall will ignore it from here on, and a claimant that keeps streaming to it is
         * holding a gesture nobody answers.
         */
        default void onWallDragInterrupted() { }
    }

    private final Map<PaneWallPage, View> mPageViews = new EnumMap<>(PaneWallPage.class);
    private List<PaneWallPage> mPages = Collections.singletonList(PaneWallPage.TERMINAL);
    @NonNull private PaneWallPage mCurrent = PaneWallPage.TERMINAL;
    @Nullable private Listener mListener;

    /** Signed distance from the current page's rest position, in px. */
    private float mOffsetPx;
    @Nullable private ValueAnimator mSlide;
    private boolean mSliding;
    private boolean mDragging;
    private boolean mReducedMotion;
    private boolean mGesturesEnabled = true;

    public PaneWallLayout(@NonNull Context context) {
        this(context, null);
    }

    public PaneWallLayout(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        setClipChildren(false);
        setClipToPadding(false);
    }

    public void setListener(@Nullable Listener listener) {
        mListener = listener;
    }

    public void setReducedMotion(boolean reduced) {
        mReducedMotion = reduced;
    }

    /**
     * Register the view that is one of the wall's places. Pass null to take a place away; the
     * view itself must already be a child of this layout (the terminal page comes from the
     * layout file, the others are added by their controllers).
     */
    public void setPageView(@NonNull PaneWallPage page, @Nullable View view) {
        if (view == null) mPageViews.remove(page);
        else mPageViews.put(page, view);
        applyPagePositions();
    }

    @Nullable
    public View pageView(@NonNull PaneWallPage page) {
        return mPageViews.get(page);
    }

    /**
     * The places this install has, in spatial order (see {@link PaneWallPolicy#availablePages}).
     * A page that goes away while it is showing hands the wall back to the terminal.
     */
    public void setPages(@NonNull List<PaneWallPage> pages) {
        List<PaneWallPage> resolved = new ArrayList<>(pages);
        if (!resolved.contains(PaneWallPage.TERMINAL)) resolved.add(PaneWallPage.TERMINAL);
        if (resolved.equals(mPages)) return;
        mPages = Collections.unmodifiableList(resolved);
        if (!mPages.contains(mCurrent)) {
            mCurrent = PaneWallPolicy.homePage();
            notifyPageChanged();
        }
        applyPagePositions();
    }

    @NonNull
    public List<PaneWallPage> pages() {
        return mPages;
    }

    @NonNull
    public PaneWallPage currentPage() {
        return mCurrent;
    }

    /** True while the wall is anywhere but at rest on its current page. */
    public boolean isMoving() {
        return mDragging || mSliding;
    }

    /** True while a finger is driving the wall. */
    public boolean isDragging() {
        return mDragging;
    }

    /**
     * Signed distance of the wall from the current page's rest, in px: positive when the pages
     * sit to the right of where they will land, so the current page is arriving from the right.
     */
    public float offsetPx() {
        return mOffsetPx;
    }

    /** Off while another surface owns the gesture (the surface editor, for one). */
    public void setGesturesEnabled(boolean enabled) {
        mGesturesEnabled = enabled;
        if (enabled || !mDragging) return;
        // The claimant is told to let go, and the wall goes back to rest on its own — nothing
        // else is going to release this drag now.
        interruptDrag();
        if (mReducedMotion) settleImmediately();
        else startSlide();
    }

    public boolean areGesturesEnabled() {
        return mGesturesEnabled;
    }

    // ---- Navigation ------------------------------------------------------------------------

    /** Go to {@code page}, sliding unless {@code animate} is false or motion is reduced. */
    public boolean goTo(@NonNull PaneWallPage page, boolean animate) {
        if (!mPages.contains(page)) return false;
        interruptDrag();
        if (page == mCurrent && mOffsetPx == 0f) return true;
        // Carry the current visual position across the page change: the new page's rest is one
        // width away per step, so the wall keeps drawing where it already was and springs from
        // there instead of jumping. On a ring the step is the shorter way round, which is also
        // the side the page's tile sits on.
        mOffsetPx += PaneWallPolicy.relativePosition(mPages, mCurrent, page) * (float) getWidth();
        mCurrent = page;
        notifyPageChanged();
        if (!animate || mReducedMotion || getWidth() <= 0) {
            settleImmediately();
        } else {
            startSlide();
        }
        return true;
    }

    /** Go one place left ({@code -1}) or right ({@code +1}). */
    public boolean goBy(int steps, boolean animate) {
        return goTo(PaneWallPolicy.neighbour(mPages, mCurrent, steps), animate);
    }

    // ---- Dragging (driven from the status bar) ---------------------------------------------

    public void beginDrag() {
        if (!mGesturesEnabled) return;
        mDragging = true;
        stopSlide();
    }

    /** Move the wall for a finger that has travelled {@code dxPx} since it went down. */
    public void dragTo(float dxPx) {
        if (!mDragging) return;
        int width = getWidth();
        mOffsetPx = PaneWallPolicy.offsetForDrag(dxPx, width,
            PaneWallPolicy.hasNeighbour(mPages, mCurrent, -1),
            PaneWallPolicy.hasNeighbour(mPages, mCurrent, 1));
        applyPagePositions();
    }

    /** Release the drag at {@code velocityPxPerSec} (positive to the right). */
    public void endDrag(float velocityPxPerSec) {
        if (!mDragging) return;
        mDragging = false;
        int steps = PaneWallPolicy.settle(mOffsetPx, velocityPxPerSec, getWidth(),
            PaneWallPolicy.hasNeighbour(mPages, mCurrent, -1),
            PaneWallPolicy.hasNeighbour(mPages, mCurrent, 1));
        if (steps == 0) {
            if (mReducedMotion) settleImmediately();
            else startSlide();
            return;
        }
        goBy(steps, true);
    }

    /** The claimant let go without a release (its stream was cancelled). */
    public void cancelDrag() {
        if (!mDragging) return;
        mDragging = false;
        if (mReducedMotion) settleImmediately();
        else startSlide();
    }

    /**
     * The wall is taking over from a live drag. Unlike {@link #cancelDrag}, the claimant did not
     * ask for this, so it is told: the rest of that finger's motion is not the wall's to answer.
     */
    private void interruptDrag() {
        if (!mDragging) return;
        mDragging = false;
        if (mListener != null) mListener.onWallDragInterrupted();
    }

    // ---- Motion ----------------------------------------------------------------------------

    private void startSlide() {
        if (mOffsetPx == 0f) {
            settleImmediately();
            return;
        }
        stopSlide();
        int width = Math.max(1, getWidth());
        float fraction = Math.min(1f, Math.abs(mOffsetPx) / width);
        long duration = Math.max(SLIDE_MIN_MS, Math.round(SLIDE_FULL_MS * fraction));
        ValueAnimator slide = ValueAnimator.ofFloat(mOffsetPx, 0f);
        slide.setDuration(duration);
        slide.setInterpolator(Motion.settle());
        slide.addUpdateListener(animation -> {
            mOffsetPx = (Float) animation.getAnimatedValue();
            applyPagePositions();
        });
        slide.addListener(new AnimatorListenerAdapter() {
            private boolean mCancelled;
            @Override public void onAnimationCancel(Animator animation) { mCancelled = true; }
            @Override public void onAnimationEnd(Animator animation) {
                if (mSlide == animation) mSlide = null;
                if (!mCancelled) settleImmediately();
            }
        });
        mSlide = slide;
        mSliding = true;
        slide.start();
    }

    private void stopSlide() {
        mSliding = false;
        ValueAnimator slide = mSlide;
        mSlide = null;
        if (slide != null) slide.cancel();
    }

    private void settleImmediately() {
        stopSlide();
        mOffsetPx = 0f;
        applyPagePositions();
        if (mListener != null) mListener.onWallPageSettled(mCurrent);
    }

    private void notifyPageChanged() {
        if (mListener != null) mListener.onWallPageChanged(mCurrent);
    }

    /**
     * Put every page where the current page and the offset say it goes. Pages are only moved,
     * never re-laid-out, and a page that is completely off screen stops drawing.
     */
    private void applyPagePositions() {
        int width = getWidth();
        boolean moving = isMoving();
        for (Map.Entry<PaneWallPage, View> entry : mPageViews.entrySet()) {
            View view = entry.getValue();
            if (!mPages.contains(entry.getKey())) {
                view.setVisibility(GONE);
                continue;
            }
            // On a ring each page is placed the shorter way round from the current one, so the
            // page past the outer edge is already waiting on the other side when a drag reaches
            // for it.
            float x = PaneWallPolicy.relativePosition(mPages, mCurrent, entry.getKey())
                * (float) width + mOffsetPx;
            view.setTranslationX(x);
            boolean onScreen = width <= 0 || Math.abs(x) < width;
            view.setVisibility(onScreen || moving ? VISIBLE : INVISIBLE);
        }
        if (mListener != null) mListener.onWallOffsetChanged(mOffsetPx);
    }

    // ---- Layout ----------------------------------------------------------------------------

    /**
     * The frame every page sits inside: the terminal page's margins, which the activity sets from
     * the surface editor's insets. A wall with no terminal page registered yet has no frame.
     */
    @NonNull
    private MarginLayoutParams pageMargins() {
        View terminal = mPageViews.get(PaneWallPage.TERMINAL);
        ViewGroup.LayoutParams params = terminal == null ? null : terminal.getLayoutParams();
        if (params instanceof MarginLayoutParams) return (MarginLayoutParams) params;
        return new MarginLayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        int height = MeasureSpec.getSize(heightMeasureSpec);
        setMeasuredDimension(width, height);
        MarginLayoutParams margins = pageMargins();
        int childWidth = MeasureSpec.makeMeasureSpec(Math.max(0, width - getPaddingLeft()
            - getPaddingRight() - margins.leftMargin - margins.rightMargin), MeasureSpec.EXACTLY);
        int childHeight = MeasureSpec.makeMeasureSpec(Math.max(0, height - getPaddingTop()
            - getPaddingBottom() - margins.topMargin - margins.bottomMargin), MeasureSpec.EXACTLY);
        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            if (child.getVisibility() == GONE) continue;
            child.measure(childWidth, childHeight);
        }
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        MarginLayoutParams margins = pageMargins();
        int left = getPaddingLeft() + margins.leftMargin;
        int top = getPaddingTop() + margins.topMargin;
        int right = Math.max(left, r - l - getPaddingRight() - margins.rightMargin);
        int bottom = Math.max(top, b - t - getPaddingBottom() - margins.bottomMargin);
        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            if (child.getVisibility() == GONE) continue;
            child.layout(left, top, right, bottom);
        }
        applyPagePositions();
    }

    // The activity sets the terminal page's frame by writing margins into its layout params and
    // checks they *are* margin params first, so the wall has to hand out that kind — a plain
    // ViewGroup does not, and the pane host silently lost its side gap when it moved in here.

    @Override
    public LayoutParams generateLayoutParams(AttributeSet attrs) {
        return new MarginLayoutParams(getContext(), attrs);
    }

    @Override
    protected LayoutParams generateDefaultLayoutParams() {
        return new MarginLayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);
    }

    @Override
    protected LayoutParams generateLayoutParams(LayoutParams params) {
        return params instanceof MarginLayoutParams ? new MarginLayoutParams((MarginLayoutParams) params)
            : new MarginLayoutParams(params);
    }

    @Override
    protected boolean checkLayoutParams(LayoutParams params) {
        return params instanceof MarginLayoutParams;
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        mSliding = false;
        stopSlide();
    }
}
