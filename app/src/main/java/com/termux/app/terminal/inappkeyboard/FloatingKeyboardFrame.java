package com.termux.app.terminal.inappkeyboard;

import android.content.Context;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.R;

/**
 * The card a floating keyboard rides in: a grab handle along the top and the keyboard host under
 * it, parked wherever the user last left it.
 *
 * <p>The frame is deliberately thin. It carries no idea of what a keyboard is — the keyboard's own
 * container is moved into {@link #contentHost()} unchanged, keeps its glass, its suggestion strip
 * and its height controls, and is measured by the normal layout pass against the width this frame
 * was given. What the frame owns is the handle and the drag: it clamps the offset it is dragged to
 * against the travel it was handed and reports where it ended up, so the arithmetic stays in
 * {@link FloatingKeyboardGeometry} and the memory stays in the store.</p>
 *
 * <p>It lives in {@code floating_keyboard_host}, which covers the whole content region. That is
 * what makes a keyboard parked halfway up the screen touchable: a view translated outside its
 * parent's bounds still draws, but never receives a touch.</p>
 */
public final class FloatingKeyboardFrame extends LinearLayout {

    /** Narrower than this and there is no keyboard left to type on, whatever the share says. */
    private static final float MIN_WIDTH_DP = 240f;

    /** The user's floating width, read at measure time so the frame is never a pass behind it. */
    public interface WidthScaleSource {
        float widthScale();
    }

    /** Where the frame ended up, in pixels from the content's top-left corner. */
    public interface OnFrameMovedListener {
        /**
         * @param committed true when the finger has left the handle, which is when a new place is
         *     worth remembering; false for the frames in between, which only move the card.
         */
        void onFrameMoved(int xPx, int yPx, boolean committed);
    }

    private final FrameLayout mHandle;
    private final FrameLayout mContentHost;

    @Nullable private OnFrameMovedListener mListener;

    @Nullable private WidthScaleSource mWidthScale;
    private int mTravelXPx;
    private int mTravelYPx;
    private int mPositionXPx;
    private int mPositionYPx;

    private float mDragStartRawX;
    private float mDragStartRawY;
    private int mDragStartXPx;
    private int mDragStartYPx;
    private boolean mDragging;

    public FloatingKeyboardFrame(@NonNull Context context) {
        super(context);
        setOrientation(VERTICAL);
        setClipChildren(false);
        setClipToPadding(false);
        setBackgroundResource(R.drawable.floating_keyboard_card);
        setElevation(dp(8f));

        mHandle = new FrameLayout(context);
        mHandle.setContentDescription(
            context.getString(R.string.termux_in_app_keyboard_floating_handle));
        mHandle.setClickable(true);
        mHandle.setFocusable(true);
        View pill = new View(context);
        pill.setBackgroundResource(R.drawable.floating_keyboard_grab_handle);
        mHandle.addView(pill, new FrameLayout.LayoutParams(
            Math.round(dp(52f)), Math.round(dp(4f)), Gravity.CENTER));
        addView(mHandle, new LayoutParams(LayoutParams.MATCH_PARENT, Math.round(dp(22f))));

        mContentHost = new FrameLayout(context);
        mContentHost.setClipChildren(false);
        mContentHost.setClipToPadding(false);
        addView(mContentHost, new LayoutParams(
            LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        mHandle.setOnTouchListener((view, event) -> onHandleTouch(event));
    }

    /** Where the keyboard's own container goes. */
    @NonNull
    public FrameLayout contentHost() {
        return mContentHost;
    }

    /** The strip the frame is dragged by, for tests and for accessibility focus. */
    @NonNull
    public View grabHandle() {
        return mHandle;
    }

    /**
     * Where the frame reads the user's width share from, every time it is measured. Pull rather
     * than push: the share changes with the orientation, and a width written into the frame from a
     * layout-change listener is a request the parent has already passed — the frame would sit a
     * pass behind every rotation.
     */
    public void setWidthScaleSource(@Nullable WidthScaleSource source) {
        mWidthScale = source;
    }

    /** The width the frame takes in a content region this wide. */
    public int frameWidthPx(int contentWidthPx) {
        return frameWidthPx(getContext(), contentWidthPx,
            mWidthScale == null ? 1f : mWidthScale.widthScale());
    }

    /** The room the frame may be dragged in, from the content bounds it floats over. */
    public void setTravelPx(int travelXPx, int travelYPx) {
        mTravelXPx = Math.max(0, travelXPx);
        mTravelYPx = Math.max(0, travelYPx);
        // Re-seat inside the new travel: a rotation or a wider frame can leave the old offset
        // hanging off the edge.
        applyPositionPx(mPositionXPx, mPositionYPx);
    }

    /** Puts the frame at an offset from the content's top-left corner, clamped to the travel. */
    public void setPositionPx(int xPx, int yPx) {
        applyPositionPx(xPx, yPx);
    }

    public int positionXPx() {
        return mPositionXPx;
    }

    public int positionYPx() {
        return mPositionYPx;
    }

    public void setOnFrameMovedListener(@Nullable OnFrameMovedListener listener) {
        mListener = listener;
    }

    private void applyPositionPx(int xPx, int yPx) {
        mPositionXPx = FloatingKeyboardGeometry.clampPx(xPx, mTravelXPx);
        mPositionYPx = FloatingKeyboardGeometry.clampPx(yPx, mTravelYPx);
        setTranslationX(mPositionXPx);
        setTranslationY(mPositionYPx);
    }

    private boolean onHandleTouch(@NonNull MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                mDragging = true;
                mDragStartRawX = event.getRawX();
                mDragStartRawY = event.getRawY();
                mDragStartXPx = mPositionXPx;
                mDragStartYPx = mPositionYPx;
                return true;
            case MotionEvent.ACTION_MOVE:
                if (!mDragging) return false;
                moveTo(event, false);
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (!mDragging) return false;
                mDragging = false;
                moveTo(event, true);
                return true;
            default:
                return false;
        }
    }

    private void moveTo(@NonNull MotionEvent event, boolean committed) {
        applyPositionPx(
            mDragStartXPx + Math.round(event.getRawX() - mDragStartRawX),
            mDragStartYPx + Math.round(event.getRawY() - mDragStartRawY));
        if (mListener != null)
            mListener.onFrameMoved(mPositionXPx, mPositionYPx, committed);
    }

    /**
     * Collapses to nothing while the keyboard it hosts is gone, so a closed keyboard never leaves
     * its handle floating over the place on its own. {@code INVISIBLE} still lays out — that is the
     * reveal gate staging the first frame, and the frame has to keep its size through it.
     */
    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        View hosted = mContentHost.getChildCount() > 0 ? mContentHost.getChildAt(0) : null;
        if (hosted == null || hosted.getVisibility() == View.GONE) {
            setMeasuredDimension(0, 0);
            return;
        }
        int widthPx = frameWidthPx(MeasureSpec.getSize(widthMeasureSpec));
        super.onMeasure(MeasureSpec.makeMeasureSpec(widthPx, MeasureSpec.EXACTLY),
            heightMeasureSpec);
    }

    /** The frame's width for one content width and the user's width share. */
    public static int frameWidthPx(@NonNull Context context, int contentWidthPx,
                                   float widthScale) {
        return FloatingKeyboardGeometry.frameWidthPx(contentWidthPx, widthScale,
            Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, MIN_WIDTH_DP,
                context.getResources().getDisplayMetrics())));
    }

    private float dp(float value) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value,
            getResources().getDisplayMetrics());
    }

    /**
     * The layout params a frame is added to its host with: the whole content region to be measured
     * against, out of which {@link #onMeasure} keeps the user's share.
     */
    @NonNull
    public static FrameLayout.LayoutParams hostLayoutParams() {
        return new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP | Gravity.START);
    }
}
