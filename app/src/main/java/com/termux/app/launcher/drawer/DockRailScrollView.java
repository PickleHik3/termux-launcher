package com.termux.app.launcher.drawer;

import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.ViewConfiguration;
import android.view.ViewParent;
import android.widget.ScrollView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * The landscape dock rail, and the only surface the app drawer can be pulled from there.
 *
 * <p>Landscape collapses the pinned apps row and rebuilds it as this vertical rail, which left the
 * drawer with no gesture at all: the portrait pull-down belongs to the row, and this rail's own
 * vertical axis is its scroll. The pull therefore runs sideways, off the edge the rail is docked to
 * — right for a left rail, left for a right rail — and is arbitrated by the same
 * {@link AppDrawerGestureArbiter} the portrait row uses, so the dominance cone that keeps a scroll
 * from reading as a pull is the one already tuned there.
 *
 * <p>The arbitration lives in {@code dispatchTouchEvent} rather than an {@code OnTouchListener} or
 * {@code onInterceptTouchEvent}: a sideways drag that starts on an icon is consumed by that child
 * and never reaches either of those, and the rail would only ever see the vertical drags it
 * already scrolls with.
 */
public final class DockRailScrollView extends ScrollView {

    /** The host's half of the pull: the vetoes, and the claimed drag handed to the controller. */
    public interface DrawerPullListener {

        /**
         * Every veto plus the rail's pull direction, read once at {@code ACTION_DOWN} — the same
         * snapshot discipline the portrait row uses, for the same reason.
         */
        @NonNull
        AppDrawerGestureArbiter.Eligibility captureDrawerEligibility();

        /** @param downPull the {@code ACTION_DOWN} point projected onto the pull's axis */
        void onDrawerDragBegin(float downPull);

        /** @param pull the current point on the pull's axis; the plane tracks this 1:1 */
        void onDrawerDrag(float pull);

        /** @param velocityPxPerSec release velocity, positive along the pull */
        void onDrawerDragEnd(float velocityPxPerSec);

        void onDrawerDragCancel();
    }

    @Nullable private DrawerPullListener mListener;
    @NonNull private final AppDrawerGestureArbiter mArbiter = new AppDrawerGestureArbiter();
    @NonNull private AppDrawerGestureArbiter.Pull mPull = AppDrawerGestureArbiter.Pull.NONE;
    @Nullable private VelocityTracker mVelocityTracker;
    private final float mSlopPx;
    private float mDownRawX;

    public DockRailScrollView(@NonNull Context context) {
        this(context, null);
    }

    public DockRailScrollView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        mSlopPx = ViewConfiguration.get(context).getScaledTouchSlop();
    }

    public void setDrawerPullListener(@Nullable DrawerPullListener listener) {
        mListener = listener;
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        DrawerPullListener listener = mListener;
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                releaseVelocityTracker();
                mDownRawX = event.getRawX();
                if (listener == null) {
                    mArbiter.reset();
                    mPull = AppDrawerGestureArbiter.Pull.NONE;
                    break;
                }
                AppDrawerGestureArbiter.Eligibility eligibility = listener.captureDrawerEligibility();
                mPull = eligibility.pull;
                mArbiter.begin(event.getRawX(), event.getRawY(), eligibility);
                mVelocityTracker = VelocityTracker.obtain();
                mVelocityTracker.addMovement(event);
                break;
            case MotionEvent.ACTION_MOVE:
                if (mVelocityTracker != null) mVelocityTracker.addMovement(event);
                if (mArbiter.isDrawerDrag()) {
                    if (listener != null) listener.onDrawerDrag(projectOntoPull(event.getRawX()));
                    return true;
                }
                if (listener != null
                    && mArbiter.evaluate(event.getRawX(), event.getRawY(), mSlopPx)
                        == AppDrawerGestureArbiter.Claim.DRAWER_DRAG) {
                    beginPull(event, listener);
                    return true;
                }
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (mVelocityTracker != null) mVelocityTracker.addMovement(event);
                if (mArbiter.isDrawerDrag() && listener != null) {
                    if (event.getActionMasked() == MotionEvent.ACTION_CANCEL) {
                        listener.onDrawerDragCancel();
                    } else {
                        listener.onDrawerDragEnd(releaseVelocityAlongPull());
                    }
                    endGesture();
                    return true;
                }
                endGesture();
                break;
            default:
                // Extra pointers during a claimed pull are swallowed rather than forwarded: the
                // children of this stream have already been cancelled and must not be restarted.
                if (mArbiter.isDrawerDrag()) return true;
                break;
        }
        return super.dispatchTouchEvent(event);
    }

    /**
     * Hands the stream over. The synthetic {@code ACTION_CANCEL} is what stops the rail from
     * finishing its own scroll fling and leaves the pressed icon unpressed — every later event of
     * this stream is consumed here, so without it the child never sees an UP.
     */
    private void beginPull(@NonNull MotionEvent event, @NonNull DrawerPullListener listener) {
        ViewParent parent = getParent();
        if (parent != null) parent.requestDisallowInterceptTouchEvent(true);
        MotionEvent cancel = MotionEvent.obtain(event);
        cancel.setAction(MotionEvent.ACTION_CANCEL);
        super.dispatchTouchEvent(cancel);
        cancel.recycle();
        listener.onDrawerDragBegin(projectOntoPull(mDownRawX));
        listener.onDrawerDrag(projectOntoPull(event.getRawX()));
    }

    /**
     * The pull's axis as a single increasing coordinate, so the controller's drag arithmetic —
     * written for the portrait pull-down — needs no direction of its own.
     */
    private float projectOntoPull(float rawX) {
        return mPull == AppDrawerGestureArbiter.Pull.LEFT ? -rawX : rawX;
    }

    private float releaseVelocityAlongPull() {
        VelocityTracker tracker = mVelocityTracker;
        if (tracker == null) return 0f;
        tracker.computeCurrentVelocity(1000);
        float velocity = tracker.getXVelocity();
        return mPull == AppDrawerGestureArbiter.Pull.LEFT ? -velocity : velocity;
    }

    private void endGesture() {
        mArbiter.reset();
        mPull = AppDrawerGestureArbiter.Pull.NONE;
        releaseVelocityTracker();
    }

    private void releaseVelocityTracker() {
        if (mVelocityTracker != null) {
            mVelocityTracker.recycle();
            mVelocityTracker = null;
        }
    }
}
