package com.termux.app.terminal.inappkeyboard;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.graphics.Rect;
import android.graphics.drawable.GradientDrawable;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.ViewParent;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.ReducedMotion;
import com.termux.app.chrome.GrabHandle;
import com.termux.app.terminal.Motion;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

import juloo.keyboard2.Keyboard2View;

/**
 * Swipe down from the keyboard's top edge to put it away. Takes the presses {@link Keyboard2View}
 * offers from the strip above its first row, and the presses that land in the empty space just
 * above it — the capsule's own margin and padding, the hairlines and insets at the foot of the
 * dock's rows — through {@link #listenAbove}, classifies them with {@link KeyboardHideSwipe}, and
 * lets the keys follow the finger down ({@code translationY} only, no layout). A release past the
 * threshold settles the keys off the bottom and then runs the very same hide the keyboard's own
 * hide key runs, so the pane room, the dock and the wall see nothing new; a short release springs
 * them back.
 *
 * <p>A tap in that band, or on the keyboard's background between the keys, plays the hint that
 * the keys can be swiped away: {@link KeyboardHideHint}, drawn as a grabber pill in the host's
 * overlay while the keys dip and spring back.
 *
 * <p>Every y the swipe sees is in the keyboard's rest coordinates. The keys move under the finger
 * as it drags, and a moved view is handed touches in its own moved space, so a view-local y would
 * feed the drag back into itself; the view's translation is added back before the swipe reads it.
 */
final class KeyboardHideSwipeGesture implements Keyboard2View.TopEdgeTouchDelegate {

    /** The band grows to this much when the gap above the keys is thinner, never onto a key. */
    static final float MIN_BAND_DP = 12f;
    /**
     * The band reaches this far above the keyboard's top edge, through space no view above
     * wanted: a press a button took never gets here, so only the gaps between the rows and the
     * keys count.
     */
    static final float REACH_ABOVE_DP = 24f;
    /** Released this far below the press, the keyboard goes. */
    static final float THRESHOLD_DP = 28f;
    /** A flick this fast, having moved at least the fling distance, goes too. */
    static final float FLING_DP_PER_S = 500f;
    static final float FLING_MIN_DISTANCE_DP = 12f;
    /** The settle at full travel; a shorter remaining way takes proportionally less. */
    static final long SETTLE_MS = 220L;
    static final long SETTLE_MIN_MS = 120L;
    /** The hint's pill: the keyboard's own label colour at this share, so it follows the scheme. */
    static final float PILL_ALPHA = 0.45f;

    private final Keyboard2View mView;
    private final BooleanSupplier mCanHide;
    private final BooleanSupplier mFloating;
    private final Runnable mHide;
    private final KeyboardHideSwipe mSwipe;
    private final float mDensity;
    private final float mMinBandPx;
    private final float mReachAbovePx;
    private int mPointerId = MotionEvent.INVALID_POINTER_ID;
    /** The view above the keys whose stream the swipe owns, or null while it is the keys' own. */
    @Nullable private View mAboveSource;
    /** From that view's y to the keyboard's rest coordinates, fixed at the press. */
    private float mAboveOffsetY;
    private final List<View> mAboveViews = new ArrayList<>(2);
    private boolean mSettling;

    @Nullable private ValueAnimator mHintAnimator;
    @Nullable private Runnable mHintEnd;
    private final List<GradientDrawable> mPills = new ArrayList<>(2);
    @Nullable private ViewGroup mPillHost;

    /**
     * @param canHide asked at the press; false leaves the press to the keys (which ignore it)
     * @param floating whether the keyboard is the floating card, which carries a handle of its own
     * @param hide the hide path to run once the keys have settled off screen
     */
    KeyboardHideSwipeGesture(@NonNull Keyboard2View view, @NonNull BooleanSupplier canHide,
                             @NonNull BooleanSupplier floating, @NonNull Runnable hide) {
        mView = view;
        mCanHide = canHide;
        mFloating = floating;
        mHide = hide;
        mDensity = view.getResources().getDisplayMetrics().density;
        mMinBandPx = MIN_BAND_DP * mDensity;
        mReachAbovePx = REACH_ABOVE_DP * mDensity;
        mSwipe = new KeyboardHideSwipe(
            ViewConfiguration.get(view.getContext()).getScaledTouchSlop(),
            THRESHOLD_DP * mDensity,
            FLING_DP_PER_S * mDensity,
            FLING_MIN_DISTANCE_DP * mDensity);
    }

    // ------------------------------------------------------------------ the keys' own presses

    @Override
    public boolean onTopEdgeTouchDown(MotionEvent event, float nullBandPx, float firstCapTopPx) {
        if (!canBegin())
            return false;
        // The band is judged where the finger is against the keys as drawn — a dipping hint
        // moves them — and the swipe then tracks the finger against the keys at rest.
        if (!KeyboardHideSwipe.bandContains(event.getY(), nullBandPx, firstCapTopPx, mMinBandPx,
                0f))
            return false;
        float restY = event.getY() + mView.getTranslationY();
        cancelHint();
        mPointerId = event.getPointerId(0);
        mSwipe.begin(event.getX(), restY, event.getEventTime());
        return true;
    }

    @Override
    public void onTopEdgeTouchEvent(MotionEvent event) {
        onStreamEvent(event, mView.getTranslationY());
    }

    @Override
    public void onBackgroundTap(float x, float y) {
        showHint();
    }

    // ------------------------------------------------------------------ presses from above

    /**
     * Joins the non-interactive space of {@code view} to the band: a press there that no child of
     * it took, within the reach above the keyboard's top edge, starts the swipe. Null is ignored.
     */
    void listenAbove(@Nullable View view) {
        if (view == null || mAboveViews.contains(view))
            return;
        mAboveViews.add(view);
        view.setOnTouchListener(this::onAboveTouch);
    }

    /** Lets go of every view given to {@link #listenAbove} and takes the hint down. */
    void release() {
        cancelHint();
        for (View view : mAboveViews)
            view.setOnTouchListener(null);
        mAboveViews.clear();
        if (mAboveSource != null) {
            disallowIntercept(mAboveSource, false);
            mAboveSource = null;
        }
    }

    private boolean onAboveTouch(View source, MotionEvent event) {
        if (mAboveSource == source) {
            onStreamEvent(event, mAboveOffsetY);
            return true;
        }
        if (event.getActionMasked() != MotionEvent.ACTION_DOWN || !canBegin())
            return false;
        int[] sourceAt = new int[2];
        int[] keysAt = new int[2];
        if (!source.isAttachedToWindow() || !mView.isAttachedToWindow() || mView.getWidth() <= 0)
            return false;
        source.getLocationInWindow(sourceAt);
        mView.getLocationInWindow(keysAt);
        // The keys' own window position carries their translation; the band is at their rest.
        float offsetX = sourceAt[0] - (keysAt[0] - mView.getTranslationX());
        float offsetY = sourceAt[1] - (keysAt[1] - mView.getTranslationY());
        float x = event.getX() + offsetX;
        float y = event.getY() + offsetY;
        if (x < 0f || x >= mView.getWidth())
            return false;
        if (!KeyboardHideSwipe.bandContains(y, mView.topEdgeNullBandPx(),
                mView.topEdgeFirstCapTopPx(), mMinBandPx, mReachAbovePx))
            return false;
        cancelHint();
        mAboveSource = source;
        mAboveOffsetY = offsetY;
        mPointerId = event.getPointerId(0);
        mSwipe.begin(x, y, event.getEventTime());
        disallowIntercept(source, true);
        return true;
    }

    private boolean canBegin() {
        return !mSettling && mPointerId == MotionEvent.INVALID_POINTER_ID
            && mAboveSource == null && mCanHide.getAsBoolean();
    }

    private static void disallowIntercept(@NonNull View view, boolean disallow) {
        ViewParent parent = view.getParent();
        if (parent != null)
            parent.requestDisallowInterceptTouchEvent(disallow);
    }

    // ------------------------------------------------------------------ the stream

    /** One event of an owned stream; {@code yOffset} takes its y to the keyboard's rest. */
    private void onStreamEvent(MotionEvent event, float yOffset) {
        int index = event.findPointerIndex(mPointerId);
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_MOVE:
                if (index < 0) return;
                mSwipe.move(event.getX(index), event.getY(index) + yOffset,
                    event.getEventTime());
                mView.setTranslationY(mSwipe.dragPx());
                break;
            case MotionEvent.ACTION_POINTER_UP:
                if (event.getActionIndex() != index) return;
                // fall through: the swiping finger lifted, whatever else is still down
            case MotionEvent.ACTION_UP:
                if (index < 0) {
                    finish(mSwipe.cancel());
                    return;
                }
                finish(mSwipe.release(event.getX(index), event.getY(index) + yOffset,
                    event.getEventTime()));
                break;
            case MotionEvent.ACTION_CANCEL:
                finish(mSwipe.cancel());
                break;
            default:
                break;
        }
    }

    private void finish(KeyboardHideSwipe.Outcome outcome) {
        mPointerId = MotionEvent.INVALID_POINTER_ID;
        if (mAboveSource != null) {
            disallowIntercept(mAboveSource, false);
            mAboveSource = null;
        }
        switch (outcome) {
            case HIDE:
                settleOut();
                break;
            case SPRING_BACK:
                springBack();
                break;
            case TAP:
                mView.setTranslationY(0f);
                showHint();
                break;
            default:
                mView.setTranslationY(0f);
                break;
        }
    }

    private void settleOut() {
        float height = mView.getHeight();
        float from = mView.getTranslationY();
        if (height <= 0f || from >= height) {
            hideNow();
            return;
        }
        mSettling = true;
        mView.animate()
            .translationY(height)
            .setDuration(settleDuration(height - from, height))
            .setInterpolator(Motion.settle())
            .withEndAction(this::hideNow)
            .start();
    }

    /** The hide first, the reset after: the keys must not stand at rest for a frame on the way out. */
    private void hideNow() {
        mSettling = false;
        mHide.run();
        mView.setTranslationY(0f);
    }

    private void springBack() {
        float from = mView.getTranslationY();
        if (from <= 0f) {
            mView.setTranslationY(0f);
            return;
        }
        mView.animate()
            .translationY(0f)
            .setDuration(settleDuration(from, Math.max(from, mView.getHeight())))
            .setInterpolator(Motion.settle())
            .start();
    }

    static long settleDuration(float remainingPx, float fullPx) {
        if (fullPx <= 0f) return SETTLE_MIN_MS;
        float share = Math.max(0f, Math.min(1f, remainingPx / fullPx));
        return Math.max(SETTLE_MIN_MS, Math.round(SETTLE_MS * share));
    }

    // ------------------------------------------------------------------ the hint

    /**
     * Plays the hint once, if nothing else has the keys: not mid-swipe, not settling away, not
     * while the keyboard may not be hidden, and not over a hint already playing.
     */
    private void showHint() {
        if (!canBegin() || mHintAnimator != null || mHintEnd != null)
            return;
        boolean reduced = ReducedMotion.isEnabled(mView.getContext());
        boolean pill = KeyboardHideHint.showsPill(mFloating.getAsBoolean()) && raisePills();
        if (reduced) {
            // Animators end at once on such a phone, so the pill stands on its own clock.
            if (!pill)
                return;
            setPillAlpha(1f);
            mHintEnd = this::endHint;
            mView.postDelayed(mHintEnd, KeyboardHideHint.durationMs());
            return;
        }
        KeyboardHideHint hint = new KeyboardHideHint(KeyboardHideHint.NUDGE_DP * mDensity, false);
        mView.animate().cancel();
        ValueAnimator animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(KeyboardHideHint.durationMs());
        animator.setInterpolator(null);
        animator.addUpdateListener(a -> {
            long elapsed = Math.round(a.getAnimatedFraction() * KeyboardHideHint.durationMs());
            mView.setTranslationY(hint.nudgePx(elapsed));
            setPillAlpha(hint.pillAlpha(elapsed));
        });
        animator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                if (mHintAnimator == animation)
                    endHint();
            }
        });
        mHintAnimator = animator;
        animator.start();
    }

    /** Takes a playing hint down at once: the keys back at rest, the pill gone. */
    private void cancelHint() {
        ValueAnimator animator = mHintAnimator;
        if (animator != null) {
            mHintAnimator = null;
            animator.cancel();
        }
        endHint();
    }

    private void endHint() {
        mHintAnimator = null;
        if (mHintEnd != null) {
            mView.removeCallbacks(mHintEnd);
            mHintEnd = null;
        }
        if (!mSettling && mPointerId == MotionEvent.INVALID_POINTER_ID)
            mView.setTranslationY(0f);
        lowerPills();
    }

    /**
     * Stands the pill — two, one over each half of a split keyboard — in the overlay of the view
     * the keys sit in, centred in the strip above the first row so the dipping keys reveal it.
     * False when the keys have no host to draw in.
     */
    private boolean raisePills() {
        lowerPills();
        ViewParent parent = mView.getParent();
        if (!(parent instanceof ViewGroup) || mView.getWidth() <= 0)
            return false;
        ViewGroup host = (ViewGroup) parent;
        int width = Math.round(GrabHandle.PILL_WIDTH_DP * mDensity);
        int height = GrabHandle.pillHeightPx(mDensity);
        float centreY = mView.getTop()
            + Math.max(mView.topEdgeFirstCapTopPx() / 2f, height / 2f);
        int color = withAlpha(mView.getKeyboardLabelColor(), PILL_ALPHA);
        for (float centreX : pillCentresX()) {
            GradientDrawable pill = new GradientDrawable();
            pill.setShape(GradientDrawable.RECTANGLE);
            pill.setCornerRadius(height / 2f);
            pill.setColor(color);
            int left = Math.round(mView.getLeft() + centreX - width / 2f);
            int top = Math.round(centreY - height / 2f);
            pill.setBounds(left, top, left + width, top + height);
            pill.setAlpha(0);
            host.getOverlay().add(pill);
            mPills.add(pill);
        }
        mPillHost = host;
        return !mPills.isEmpty();
    }

    /** Where the pills stand along the keys, in the keys' own x: one per run of keys. */
    private float[] pillCentresX() {
        Rect gap = new Rect();
        if (mView.getSplitGapBounds(gap)) {
            float margin = (mView.getWidth() - mView.getKeyContentWidthPx()) / 2f;
            return new float[] {
                (margin + gap.left) / 2f,
                (gap.right + mView.getWidth() - margin) / 2f
            };
        }
        return new float[] {mView.getWidth() / 2f};
    }

    private void setPillAlpha(float alpha) {
        int a = Math.round(255f * Math.max(0f, Math.min(1f, alpha)));
        for (GradientDrawable pill : mPills)
            pill.setAlpha(a);
    }

    private void lowerPills() {
        if (mPillHost != null) {
            for (GradientDrawable pill : mPills)
                mPillHost.getOverlay().remove(pill);
        }
        mPills.clear();
        mPillHost = null;
    }

    private static int withAlpha(int color, float share) {
        int alpha = Math.round(255f * Math.max(0f, Math.min(1f, share)));
        return (color & 0x00FFFFFF) | (alpha << 24);
    }
}
