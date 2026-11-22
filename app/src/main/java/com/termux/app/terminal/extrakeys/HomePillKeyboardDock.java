package com.termux.app.terminal.extrakeys;

import android.os.Handler;
import android.os.Looper;
import android.view.ViewTreeObserver;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * The Home pill docking over the in-app keyboard. While the pill shows on Home and the keyboard
 * comes up it does not hide: it rides the keyboard's own reveal from its spot to the docked bar's
 * rect, so a swipe that reverses tracks it, and at rest with the keyboard up it is laid out at the
 * docked size with its keys acting on a hold as the bar's do. Going back is the same ride the
 * other way.
 *
 * <p>Per frame it only moves the pill by translation and scale (the pill is laid out once when it
 * docks and once when it lets go) and allocates nothing. A keyboard change that brings no reveal
 * progress (a key press, not a swipe) snaps to the end pose at the next frame, with a
 * {@link #FAILSAFE_MS} backstop.
 */
public final class HomePillKeyboardDock {

    /** How long a keyboard change may wait for reveal progress before the pill snaps. */
    public static final long FAILSAFE_MS = 400L;

    /** What only the activity knows: the stack's geometry. */
    public interface Source {
        /**
         * Fills {@code out} with the docked rect in the layer's pixels (left, top, right, bottom):
         * the span the docked bar takes in the accessory row stack, the extra-keys row's height,
         * standing on the top of the stack at rest with the keyboard up. False while it is not
         * known (the stack has not been laid out yet).
         */
        boolean readDockedRect(@NonNull float[] out);

        /** How far the stack is translated at reveal {@code progress} (0 down, 1 up), in pixels. */
        float stackTravelPx(float progress);
    }

    private enum State { FLOATING, TRAVELLING, DOCKED }

    private final HomePillLayer mLayer;
    private final HomePillView mPill;
    private final Handler mHandler = new Handler(Looper.getMainLooper());
    /** The pill's resting rect, kept from the moment it leaves its spot until it is back. */
    private final float[] mFloat = new float[4];
    private final float[] mDocked = new float[4];
    private final float[] mTarget = new float[4];
    /** The rect the pill is posed on now: left, top, right, bottom. */
    private final float[] mPose = new float[4];
    private final Runnable mFailsafe = this::onFailsafe;

    @Nullable private Source mSource;
    @NonNull private State mState = State.FLOATING;
    private boolean mWantDocked;
    private boolean mProgressSeen;
    private boolean mPoseActive;
    private boolean mSnapArmed;
    /** The wait for the next frame that snaps the pill when no swipe reports; null when none. */
    @Nullable private com.termux.app.window.FirstLayout.Handle mPreDrawWait;

    public HomePillKeyboardDock(@NonNull HomePillLayer layer, @NonNull HomePillView pill) {
        mLayer = layer;
        mPill = pill;
        // The pose is by actual size: when a layout lands at the docked (or the pill's own) size
        // the pose is applied again, so no frame shows the old size under the new pose.
        mPill.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> onPillLaidOut());
    }

    public void setSource(@Nullable Source source) {
        mSource = source;
    }

    /** The dock owns the pill's transform: it is on the keyboard's way, or docked. */
    public boolean ownsPose() {
        return mState != State.FLOATING || mPoseActive;
    }

    public boolean isDocked() {
        return mState == State.DOCKED;
    }

    // ---- What the controller says.

    /**
     * The controller decided the pill is (or is no longer) docked for the keyboard. With no swipe
     * under way the pill snaps to that state's pose at the next frame; during a swipe the reveal's
     * own progress and its landing do it, the backstop covering a swipe that never reports.
     */
    public void onDockedChanged(boolean docked, boolean swiping) {
        mWantDocked = docked;
        if (docked == (mState == State.DOCKED)) return;
        mProgressSeen = false;
        cancelSnap();
        mSnapArmed = true;
        mHandler.postDelayed(mFailsafe, FAILSAFE_MS);
        if (!swiping) mPreDrawWait = com.termux.app.window.FirstLayout.beforeNextDraw(mPill, this::onPreDraw);
    }

    /** The keyboard swipe landed: the pill takes the pose of where the keyboard ended. */
    public void onKeyboardSettled() {
        cancelSnap();
        snapToWanted();
    }

    /** The pill is hidden or gone: floating again, whatever it was doing. */
    public void reset() {
        cancelSnap();
        mWantDocked = false;
        floatingRest();
    }

    // ---- Per frame.

    /**
     * One frame of the keyboard's reveal: {@code progress} 0 is the keyboard down, 1 up. Moves the
     * pill by translation and scale only and allocates nothing.
     */
    public void onProgress(float progress) {
        Source source = mSource;
        if (source == null || mPill.getVisibility() != android.view.View.VISIBLE
                || mLayer.flights().isFlying() || mPill.isPickedUp()) {
            return;
        }
        mProgressSeen = true;
        if (mSnapArmed) cancelSnap();
        if (progress >= 1f) {
            dockedRest();
            return;
        }
        if (mState == State.DOCKED) leaveDocked();
        if (progress <= 0f) {
            floatingRest();
            return;
        }
        if (mState == State.FLOATING && !beginTravel()) return;
        if (!source.readDockedRect(mDocked)) return;
        float travel = source.stackTravelPx(progress);
        mTarget[0] = mDocked[0];
        mTarget[1] = mDocked[1] + travel;
        mTarget[2] = mDocked[2];
        mTarget[3] = mDocked[3] + travel;
        HomePillFlight.keyboardPose(mFloat, mTarget, progress, mPose);
        mPoseActive = true;
        applyPose();
    }

    // ---- States.

    /** The pill leaves its spot: its rect is kept and the reveal moves it. */
    private boolean beginTravel() {
        if (mPill.getWidth() <= 0 || mPill.getHeight() <= 0) return false;
        float left = mPill.getLeft() + mPill.getTranslationX();
        float top = mPill.getTop() + mPill.getTranslationY();
        mFloat[0] = left;
        mFloat[1] = top;
        mFloat[2] = left + mPill.getWidth();
        mFloat[3] = top + mPill.getHeight();
        mState = State.TRAVELLING;
        return true;
    }

    /** At rest with the keyboard up: laid out at the docked size, on the docked rect. */
    private void dockedRest() {
        Source source = mSource;
        if (source == null) return;
        if (!source.readDockedRect(mDocked)) {
            // No rect to dock on: it stays on its spot rather than hang half way.
            floatingRest();
            return;
        }
        int width = Math.round(mDocked[2] - mDocked[0]);
        int height = Math.round(mDocked[3] - mDocked[1]);
        if (width <= 0 || height <= 0 || (mState == State.FLOATING && !beginTravel())) return;
        mPill.setDockedSize(width, height);
        mPill.setFloating(false);
        mState = State.DOCKED;
        System.arraycopy(mDocked, 0, mPose, 0, 4);
        mPoseActive = true;
        applyPose();
    }

    /** The first progress below 1 with the pill docked: its own size and look again, once. */
    private void leaveDocked() {
        mPill.clearDockedSize();
        mPill.setFloating(true);
        mState = State.TRAVELLING;
    }

    /** At rest with the keyboard down: identity at its spot. */
    private void floatingRest() {
        if (mState == State.FLOATING) return;
        mState = State.FLOATING;
        mPill.clearDockedSize();
        mPill.setFloating(true);
        // The layout at its own size lands next frame; until then the pose keeps it on its spot.
        System.arraycopy(mFloat, 0, mPose, 0, 4);
        mPoseActive = true;
        applyPose();
        if (sizeMatchesPose()) mPoseActive = false;
    }

    private void snapToWanted() {
        if (mWantDocked) dockedRest();
        else floatingRest();
    }

    // ---- The pose.

    /** Poses the pill on {@link #mPose} by translation and scale, about its centre, by its size now. */
    private void applyPose() {
        float w = mPill.getWidth();
        float h = mPill.getHeight();
        if (w <= 0f || h <= 0f) return;
        float poseW = mPose[2] - mPose[0];
        float poseH = mPose[3] - mPose[1];
        boolean sameW = Math.abs(poseW - w) < 1f;
        boolean sameH = Math.abs(poseH - h) < 1f;
        mPill.setScaleX(sameW ? 1f : poseW / w);
        mPill.setScaleY(sameH ? 1f : poseH / h);
        mPill.setTranslationX(sameW ? mPose[0] : (mPose[0] + mPose[2]) * 0.5f - w * 0.5f);
        mPill.setTranslationY(sameH ? mPose[1] : (mPose[1] + mPose[3]) * 0.5f - h * 0.5f);
    }

    private boolean sizeMatchesPose() {
        return Math.abs((mPose[2] - mPose[0]) - mPill.getWidth()) < 1f
            && Math.abs((mPose[3] - mPose[1]) - mPill.getHeight()) < 1f;
    }

    private void onPillLaidOut() {
        if (!mPoseActive) return;
        applyPose();
        if (mState == State.FLOATING && sizeMatchesPose()) mPoseActive = false;
    }

    // ---- The snap for a change that brings no progress.

    private void onPreDraw() {
        mPreDrawWait = null;
        if (mSnapArmed && !mProgressSeen) {
            cancelSnap();
            snapToWanted();
        }
    }

    private void onFailsafe() {
        mSnapArmed = false;
        disarmPreDraw();
        if (!mProgressSeen) snapToWanted();
    }

    private void cancelSnap() {
        if (mSnapArmed) mHandler.removeCallbacks(mFailsafe);
        mSnapArmed = false;
        disarmPreDraw();
    }

    private void disarmPreDraw() {
        com.termux.app.window.FirstLayout.Handle wait = mPreDrawWait;
        mPreDrawWait = null;
        if (wait != null) wait.cancel();
    }
}
