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
         * The Terminal page just went fully off screen, or just came back. Unlike the other
         * places it is never {@code INVISIBLE} (see {@link #applyPagePositions}), so nothing in
         * the view hierarchy notices it leaving on its own any more; this is the signal that
         * stands in for that, for the things a hidden terminal has to pause — kitty animations,
         * the cursor blinker, focus, accessibility — across every pane the terminal page holds.
         */
        default void onTerminalOffScreenChanged(boolean offScreen) { }
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
    /** One page slid in from beside its place on top of wherever the wall puts it. */
    @Nullable private PaneWallPage mNudgePage;
    private float mNudgePx;
    @Nullable private ValueAnimator mNudge;
    private boolean mReducedMotion;
    private boolean mGesturesEnabled = true;
    /**
     * Whether the last {@link #applyPagePositions} call reported the Terminal page off screen, or
     * {@code null} before the first call, so that first call always tells the listener where it
     * stands rather than only on a change from some assumed starting state.
     */
    @Nullable private Boolean mTerminalOffScreen;

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

    /**
     * Whether {@code page}'s pixels are on screen, read off the page view itself rather than off
     * the record of which page is current. The two agree whenever the wall rests where it says it
     * does; a slide that was cut short leaves them apart, and then the pixels are the truth.
     * Before the first layout there are no pixels to ask, so the record answers.
     *
     * <p>The Terminal page stays {@code VISIBLE} even off screen (see {@link #applyPagePositions}),
     * so this reads its translation alone for it; the two-part check below still answers correctly
     * for it too, since an off-screen Terminal page's translation is never inside the width either.
     */
    public boolean isPageOnScreen(@NonNull PaneWallPage page) {
        View view = mPageViews.get(page);
        if (view == null || !mPages.contains(page)) return false;
        int width = getWidth();
        if (width <= 0) return page == mCurrent;
        return view.getVisibility() == VISIBLE && Math.abs(view.getTranslationX()) < width;
    }

    /** True while the wall is at rest but not where its record says: a slide was cut short. */
    public boolean isRestingOffPage() {
        return !isMoving() && mOffsetPx != 0f;
    }

    /**
     * Slide {@code page} in from {@code fromPx} beside its place, on top of wherever the wall puts
     * it. The window bar's arrival used to animate the terminal page's own translation, which the
     * wall writes too; every movement of a page goes through the wall now so the position has one
     * owner. Only the page the wall rests on is moved; a wall that starts moving drops the nudge;
     * {@code onEnd} runs however it ends.
     */
    public void nudgePage(@NonNull PaneWallPage page, float fromPx, long durationMs,
                          @Nullable android.view.animation.Interpolator interpolator,
                          @Nullable Runnable onEnd) {
        stopNudge();
        // Only the page the wall rests on is nudged: a page the wall has put away would otherwise
        // slide across the place the user is looking at.
        if (mPageViews.get(page) == null || page != mCurrent || isMoving() || mOffsetPx != 0f
                || fromPx == 0f) {
            if (onEnd != null) onEnd.run();
            return;
        }
        mNudgePage = page;
        mNudgePx = fromPx;
        applyPagePositions();
        ValueAnimator nudge = ValueAnimator.ofFloat(fromPx, 0f);
        nudge.setDuration(durationMs);
        if (interpolator != null) nudge.setInterpolator(interpolator);
        nudge.addUpdateListener(animation -> {
            mNudgePx = (Float) animation.getAnimatedValue();
            applyPagePositions();
        });
        nudge.addListener(new AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(Animator animation) {
                if (mNudge == animation) mNudge = null;
                mNudgePage = null;
                mNudgePx = 0f;
                applyPagePositions();
                if (onEnd != null) onEnd.run();
            }
        });
        mNudge = nudge;
        nudge.start();
    }

    private void stopNudge() {
        ValueAnimator nudge = mNudge;
        mNudge = null;
        mNudgePage = null;
        mNudgePx = 0f;
        // Cancelling runs the end listener, which repositions the page and runs onEnd.
        if (nudge != null) nudge.cancel();
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
        stopNudge();
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
        stopNudge();
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
     * never re-laid-out, and a page that is completely off screen stops drawing — mid-slide too:
     * the ring's third page, which a slide between the other two never shows, is not drawn along
     * with them.
     *
     * <p>The Terminal page is the one exception, and only since 707920f7 turned out to cost a
     * frame. That commit tightened {@code onScreen || moving ? VISIBLE : INVISIBLE} down to plain
     * {@code onScreen ? VISIBLE : INVISIBLE} so a slide no longer drew the ring's third page for
     * the whole motion — but it also means an off-screen page now turns INVISIBLE the very frame
     * it leaves, which is the frame the framework drops its display lists on, so the frame it
     * comes back has to re-record everything (the pane's rows, its glass, its padding band) from
     * nothing and misses vsync. The Terminal page is instead kept {@code VISIBLE} always and faded
     * with {@code alpha} to 0 while off screen: a zero-alpha node is skipped by the renderer but
     * keeps its display lists, so coming back repaints instead of re-recording. The other places
     * keep 707920f7's INVISIBLE behaviour unchanged — the Display page in particular owns its own
     * surface, which INVISIBLE is exactly right for. Because the Terminal page no longer goes
     * INVISIBLE, nothing in the view hierarchy notices it leaving on its own any more, which is
     * what {@link Listener#onTerminalOffScreenChanged} is for.
     */
    private void applyPagePositions() {
        int width = getWidth();
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
                * (float) width + mOffsetPx
                + (entry.getKey() == mNudgePage ? mNudgePx : 0f);
            view.setTranslationX(x);
            boolean onScreen = width <= 0 || Math.abs(x) < width;
            if (entry.getKey() == PaneWallPage.TERMINAL) {
                view.setVisibility(VISIBLE);
                view.setAlpha(onScreen ? 1f : 0f);
                boolean offScreen = !onScreen;
                if (mListener != null
                        && (mTerminalOffScreen == null || mTerminalOffScreen != offScreen)) {
                    mTerminalOffScreen = offScreen;
                    mListener.onTerminalOffScreenChanged(offScreen);
                }
            } else {
                view.setVisibility(onScreen ? VISIBLE : INVISIBLE);
            }
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
        // A slide cut short (the wall left the window mid-way) leaves the pages displaced and the
        // record ahead of them; the next layout puts both right and says so.
        if (isRestingOffPage()) settleImmediately();
        else applyPagePositions();
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
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (isRestingOffPage()) settleImmediately();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        mSliding = false;
        stopSlide();
        stopNudge();
    }
}
