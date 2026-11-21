package com.termux.app.terminal.extrakeys;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.animation.PathInterpolator;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * The Home pill's two one-shot flights at a page change. {@link #flyToDock} carries the pill onto
 * the docked bar that has just come up on a Terminal page; {@link #armToPill} followed by the pill
 * showing carries it from where the docked bar stood to its own spot on Home. Both are draw-only
 * (translation, scale, alpha), last {@link HomePillFlight#DURATION_MS} and end at their final
 * state; anything that hides the docked bar gives it back (alpha 1, translation 0, scale 1) when
 * the flight ends, is cancelled, is replaced or the view goes.
 *
 * <p>A flight ends at its final state, at once, when {@link #finish} is called: a new settle, a
 * keyboard change, a pickup, a pause and a detach all call it.
 */
public final class HomePillFlightPlayer {

    /** How long a flight to the dock waits for the bar's layout before it gives up. */
    public static final long LAYOUT_BACKSTOP_MS = 100L;
    /** How long an armed flight to the pill waits for the pill to show. */
    public static final long ARM_BACKSTOP_MS = 400L;

    public interface Listener {
        /** A flight ended (or was skipped): a pill held on for a flight to the dock lets go. */
        void onFlightEnded();
    }

    private enum Phase { IDLE, WAIT_DOCK, FLY_DOCK, ARMED_PILL, FLY_PILL }

    private final HomePillLayer mLayer;
    private final HomePillView mPill;
    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private final PathInterpolator mPath = new PathInterpolator(0.3f, 0.2f, 0.05f, 1f);
    private final int[] mScreen = new int[2];
    private final int[] mLayerAt = new int[2];
    private final float[] mPillOut = new float[4];
    private final float[] mBarOut = new float[4];
    private final Runnable mBackstop = this::onBackstop;
    private final ViewTreeObserver.OnPreDrawListener mPreDraw = this::onPreDraw;

    @Nullable private Listener mListener;
    @NonNull private Phase mPhase = Phase.IDLE;
    @Nullable private View mBar;
    @Nullable private HomePillSpot.Rect mCaptured;
    @Nullable private HomePillSpot.Rect mFrom;
    @Nullable private HomePillSpot.Rect mTo;
    @Nullable private HomePillSpot.Rect mPillBase;
    @Nullable private ValueAnimator mAnimator;
    private boolean mPreDrawArmed;
    private float mRestTx;
    private float mRestTy;
    private float mBarTx;
    private float mBarTy;

    public HomePillFlightPlayer(@NonNull HomePillLayer layer, @NonNull HomePillView pill) {
        mLayer = layer;
        mPill = pill;
    }

    public void setListener(@Nullable Listener listener) {
        mListener = listener;
    }

    /** A flight is carrying the pill (or about to): it takes no touch and no keyboard pose. */
    public boolean isFlying() {
        return mPhase == Phase.WAIT_DOCK || mPhase == Phase.FLY_DOCK || mPhase == Phase.FLY_PILL;
    }

    // ---- Capturing, before a settle re-dresses the chrome.

    /**
     * Remembers where the docked bar stands, in the layer's pixels, if it is on screen; read before
     * the settle hides it. False (and nothing kept) when it is not.
     */
    public boolean captureDockedBar(@Nullable View bar) {
        mCaptured = null;
        if (!usable(bar) || !mLayer.isAttachedToWindow()) return false;
        mCaptured = rectOf(bar);
        return true;
    }

    // ---- To the dock.

    /**
     * The pill, which stays on screen though Home is gone, flies onto the bar the settle brings up.
     * The bar is held at alpha 0 until its next layout gives the rect to fly to; if that does not
     * come in {@link #LAYOUT_BACKSTOP_MS} the flight is skipped, the bar left as it is.
     */
    public void flyToDock(@Nullable View bar) {
        finish();
        // Where the pill rests, kept first: every way out puts it back there.
        mRestTx = mPill.getTranslationX();
        mRestTy = mPill.getTranslationY();
        if (bar == null || mPill.getVisibility() != View.VISIBLE || mPill.getWidth() <= 0
                || !mLayer.isAttachedToWindow()) {
            skipDock();
            return;
        }
        mBar = bar;
        mBarTx = bar.getTranslationX();
        mBarTy = bar.getTranslationY();
        bar.setAlpha(0f);
        mFrom = visualRectOfPill();
        mPhase = Phase.WAIT_DOCK;
        ViewTreeObserver observer = bar.getViewTreeObserver();
        if (observer.isAlive()) {
            observer.addOnPreDrawListener(mPreDraw);
            mPreDrawArmed = true;
        }
        mHandler.postDelayed(mBackstop, LAYOUT_BACKSTOP_MS);
    }

    private boolean onPreDraw() {
        View bar = mBar;
        if (mPhase != Phase.WAIT_DOCK || bar == null) {
            disarmPreDraw();
            return true;
        }
        // The bar is laid out by now only if it is up and has no layout waiting.
        if (!usable(bar) || bar.isLayoutRequested()) return true;
        disarmPreDraw();
        mHandler.removeCallbacks(mBackstop);
        mTo = rectOf(bar);
        mPillBase = mFrom;
        mPhase = Phase.FLY_DOCK;
        startAnimator();
        return true;
    }

    private void skipDock() {
        View bar = mBar;
        mBar = null;
        if (bar != null) restoreBar(bar);
        mPhase = Phase.IDLE;
        resetPill();
        mLayer.setPillShown(false);
        if (mListener != null) mListener.onFlightEnded();
    }

    // ---- To the pill.

    /**
     * The next time the pill shows, it flies from where the docked bar was captured to its spot.
     * Nothing happens (and the arm lapses) if it does not show within {@link #ARM_BACKSTOP_MS}.
     */
    public void armToPill() {
        finish();
        if (mCaptured == null) return;
        mPhase = Phase.ARMED_PILL;
        mHandler.removeCallbacks(mBackstop);
        mHandler.postDelayed(mBackstop, ARM_BACKSTOP_MS);
    }

    /**
     * The layer is about to show the pill, already placed at its spot: starts an armed flight. Runs
     * before the pill is visible, so its first drawn frame is the flight's first.
     */
    void onPillShown() {
        HomePillSpot.Rect captured = mCaptured;
        if (mPhase != Phase.ARMED_PILL || captured == null || mPill.getWidth() <= 0) return;
        mHandler.removeCallbacks(mBackstop);
        mRestTx = mPill.getTranslationX();
        mRestTy = mPill.getTranslationY();
        mFrom = captured;
        mTo = visualRectOfPill();
        mPillBase = mTo;
        mPhase = Phase.FLY_PILL;
        startAnimator();
    }

    /** The layer hid the pill: whatever flight was on ends at its final state. */
    void onPillHidden() {
        if (mPhase != Phase.IDLE) finish();
    }

    // ---- The flight.

    private void startAnimator() {
        ValueAnimator animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(HomePillFlight.DURATION_MS);
        // Linear in time: the path eases the movement, the cross-fade runs on the clock.
        animator.setInterpolator(null);
        animator.addUpdateListener(a -> onFrame(a.getAnimatedFraction()));
        animator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                if (animation == mAnimator) land();
            }
        });
        mAnimator = animator;
        // The first frame is posed before it is drawn.
        onFrame(0f);
        animator.start();
    }

    /** One frame: translation, scale and alpha only, written into preallocated arrays. */
    private void onFrame(float time) {
        HomePillSpot.Rect from = mFrom;
        HomePillSpot.Rect to = mTo;
        HomePillSpot.Rect base = mPillBase;
        if (from == null || to == null || base == null) return;
        float t = mPath.getInterpolation(time);
        float c = HomePillFlight.crossFade(time);
        HomePillFlight.transform(from, to, t, base, mPillOut);
        mPill.setTranslationX(mRestTx + mPillOut[0]);
        mPill.setTranslationY(mRestTy + mPillOut[1]);
        mPill.setScaleX(mPillOut[2]);
        mPill.setScaleY(mPillOut[3]);
        if (mPhase == Phase.FLY_DOCK) {
            mPill.pageView().setAlpha(1f - c);
            View bar = mBar;
            if (bar != null) {
                // The bar is laid out at the to rect and rides inside the moving object.
                HomePillFlight.transform(from, to, t, to, mBarOut);
                bar.setTranslationX(mBarTx + mBarOut[0]);
                bar.setTranslationY(mBarTy + mBarOut[1]);
                bar.setScaleX(mBarOut[2]);
                bar.setScaleY(mBarOut[3]);
                bar.setAlpha(c);
            }
        } else {
            mPill.pageView().setAlpha(c);
        }
    }

    /** The animation ended or was cancelled: the final state. */
    private void land() {
        mAnimator = null;
        Phase phase = mPhase;
        mPhase = Phase.IDLE;
        View bar = mBar;
        mBar = null;
        if (bar != null) restoreBar(bar);
        resetPill();
        if (phase == Phase.FLY_DOCK) mLayer.setPillShown(false);
        if (mListener != null) mListener.onFlightEnded();
    }

    /**
     * Ends whatever is on at its final state, at once: a flight to the dock leaves the pill hidden
     * and the bar whole; a flight to the pill leaves it at its spot. Safe to call when idle.
     */
    public void finish() {
        mHandler.removeCallbacks(mBackstop);
        disarmPreDraw();
        ValueAnimator animator = mAnimator;
        if (animator != null) {
            // Cancel runs the end listener, which lands.
            animator.cancel();
            if (mAnimator == animator) land();
            return;
        }
        switch (mPhase) {
            case WAIT_DOCK:
                skipDock();
                break;
            case ARMED_PILL:
                mPhase = Phase.IDLE;
                break;
            default:
                break;
        }
    }

    private void onBackstop() {
        if (mPhase == Phase.WAIT_DOCK) {
            disarmPreDraw();
            skipDock();
        } else if (mPhase == Phase.ARMED_PILL) {
            mPhase = Phase.IDLE;
        }
    }

    private void disarmPreDraw() {
        if (!mPreDrawArmed) return;
        mPreDrawArmed = false;
        View bar = mBar;
        if (bar == null) return;
        ViewTreeObserver observer = bar.getViewTreeObserver();
        if (observer.isAlive()) observer.removeOnPreDrawListener(mPreDraw);
    }

    // ---- Helpers.

    private void restoreBar(@NonNull View bar) {
        bar.setAlpha(1f);
        bar.setTranslationX(mBarTx);
        bar.setTranslationY(mBarTy);
        bar.setScaleX(1f);
        bar.setScaleY(1f);
    }

    /** The pill at its own size and spot, keys whole. */
    private void resetPill() {
        mPill.setTranslationX(mRestTx);
        mPill.setTranslationY(mRestTy);
        mPill.setScaleX(1f);
        mPill.setScaleY(1f);
        mPill.pageView().setAlpha(1f);
    }

    private static boolean usable(@Nullable View view) {
        return view != null && view.getVisibility() == View.VISIBLE && view.isShown()
            && view.getWidth() > 0 && view.getHeight() > 0;
    }

    /** {@code view}'s on-screen rect in the layer's pixels, transforms included. */
    @NonNull
    private HomePillSpot.Rect rectOf(@NonNull View view) {
        view.getLocationOnScreen(mScreen);
        mLayer.getLocationOnScreen(mLayerAt);
        float left = mScreen[0] - mLayerAt[0];
        float top = mScreen[1] - mLayerAt[1];
        return new HomePillSpot.Rect(left, top, left + view.getWidth() * view.getScaleX(),
            top + view.getHeight() * view.getScaleY());
    }

    /** The pill as drawn now: laid out at the layer's top-left, moved and scaled about its centre. */
    @NonNull
    private HomePillSpot.Rect visualRectOfPill() {
        float w = mPill.getWidth() * mPill.getScaleX();
        float h = mPill.getHeight() * mPill.getScaleY();
        float cx = mPill.getLeft() + mPill.getTranslationX() + mPill.getWidth() * 0.5f;
        float cy = mPill.getTop() + mPill.getTranslationY() + mPill.getHeight() * 0.5f;
        return new HomePillSpot.Rect(cx - w * 0.5f, cy - h * 0.5f, cx + w * 0.5f, cy + h * 0.5f);
    }
}
