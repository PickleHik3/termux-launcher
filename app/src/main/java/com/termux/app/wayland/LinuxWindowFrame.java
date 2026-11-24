package com.termux.app.wayland;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewParent;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import com.termux.app.chrome.CornerHold;
import com.termux.app.chrome.CornerHoldArbiter;
import com.termux.app.chrome.CornerZones;
import com.termux.app.haptics.Haptics;
import com.termux.app.x11.DisplayScaleRail;
import com.termux.app.x11.DisplayScaleRailView;
import com.termux.view.HoldTiming;

/**
 * The window area of the Linux windows screen: the shown window and, out of its leading edge,
 * the scale rail X11 Display has ({@link DisplayScaleRail}). A hold on one of the area's corners
 * brings the rail out, as a hold on the display window's corners does; a hold while it is out, or
 * a touch anywhere off it, puts it away.
 *
 * <p>A corner square is not a button: the finger that lands in one is the window's until it has
 * rested for {@link HoldTiming#holdTimeoutMs()} ({@link CornerHoldArbiter}), so an app's own
 * corner controls answer a tap. When the hold fires the window is sent a cancel and the rest of
 * the gesture is this frame's. A mouse never holds a corner.
 *
 * <p>An X window under Xwayland has no scale of its own (one X server, one size for all its
 * apps): its hold shows a short line saying so instead of the rail.
 */
final class LinuxWindowFrame extends FrameLayout {

    /** The screen: the rail's stops are the shown window's scale. */
    interface Host extends DisplayScaleRail.Host {
        /** Whether a window is shown; its corners hold only then. */
        boolean hasShownWindow();

        /** Whether the shown window can take a scale of its own; the rail comes out only then. */
        boolean shownWindowScales();

        /**
         * A line shown with the rail when a new stop does not change the shown window at once (a
         * desktop takes it at its next start); null for none.
         */
        @Nullable
        default String shownWindowScaleNote() {
            return null;
        }
    }

    /** How long the shared-size line stays up. */
    private static final long SHARED_LINE_MS = 2500;

    @NonNull private final DisplayScaleRailView mRailView;
    @NonNull private final DisplayScaleRail mRail;
    private final CornerHoldArbiter mHold = new CornerHoldArbiter();
    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private final Runnable mHoldElapsed = this::onHoldElapsed;
    @NonNull private final Host mHost;
    /** Says an X window shares the X server's one size; shown on its corner hold. */
    @NonNull private final android.widget.TextView mSharedLine;
    private final Runnable mHideSharedLine = this::hideSharedLine;
    /** The cancel sent to the window when a corner claims its finger is passing through. */
    private boolean mCancellingWindow;
    /** The rail was out when the finger landed: a corner hold then leaves it away. */
    private boolean mRailOutAtDown;
    private float mDownX;
    private float mDownY;

    LinuxWindowFrame(@NonNull Context context, @NonNull Host host) {
        super(context);
        mHost = host;
        mRailView = new DisplayScaleRailView(context);
        mRail = new DisplayScaleRail(mRailView, host);
        addView(mRailView, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        mSharedLine = sharedLine(context);
        LayoutParams lp = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT,
            android.view.Gravity.CENTER);
        int margin = dp(24);
        lp.setMargins(margin, margin, margin, margin);
        addView(mSharedLine, lp);
    }

    /** A pill in the launcher's panel colours, hidden until a hold asks for it. */
    @NonNull
    private android.widget.TextView sharedLine(@NonNull Context context) {
        android.widget.TextView line = new android.widget.TextView(context);
        line.setText(com.termux.R.string.linux_windows_scale_shared);
        line.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 14);
        line.setGravity(android.view.Gravity.CENTER);
        line.setTextColor(com.google.android.material.color.MaterialColors.getColor(context,
            com.termux.shared.R.attr.termuxColorOnSurface,
            androidx.core.content.ContextCompat.getColor(context, com.termux.R.color.termux_on_surface)));
        android.graphics.drawable.GradientDrawable pill = new android.graphics.drawable.GradientDrawable();
        pill.setColor(com.google.android.material.color.MaterialColors.getColor(context,
            com.termux.shared.R.attr.termuxColorSurfacePanel,
            androidx.core.content.ContextCompat.getColor(context, com.termux.R.color.termux_surface_panel)));
        pill.setCornerRadius(dp(20));
        line.setBackground(pill);
        line.setPadding(dp(16), dp(10), dp(16), dp(10));
        line.setVisibility(GONE);
        return line;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void showSharedLine() {
        showLine(getContext().getString(com.termux.R.string.linux_windows_scale_shared));
    }

    /** The pill with {@code text}, for {@link #SHARED_LINE_MS}. */
    private void showLine(@NonNull String text) {
        if (!android.text.TextUtils.equals(mSharedLine.getText(), text)) mSharedLine.setText(text);
        if (mSharedLine.getVisibility() != VISIBLE) mSharedLine.setVisibility(VISIBLE);
        mHandler.removeCallbacks(mHideSharedLine);
        mHandler.postDelayed(mHideSharedLine, SHARED_LINE_MS);
    }

    private void hideSharedLine() {
        mHandler.removeCallbacks(mHideSharedLine);
        if (mSharedLine.getVisibility() != GONE) mSharedLine.setVisibility(GONE);
    }

    @VisibleForTesting
    boolean isSharedLineShown() {
        return mSharedLine.getVisibility() == VISIBLE;
    }

    @VisibleForTesting
    @NonNull
    String sharedLineText() {
        return String.valueOf(mSharedLine.getText());
    }

    /** Adds a window (or the empty-state text) below the rail and the shared-size line. */
    void addWindowView(@NonNull View view) {
        addView(view, indexOfChild(mRailView),
            new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
    }

    /** Puts the rail away: the window behind it changed or went. */
    void dismissRail() {
        mRail.dismiss();
        hideSharedLine();
    }

    @VisibleForTesting
    boolean isRailShown() {
        return mRail.isShown();
    }

    @VisibleForTesting
    @NonNull
    DisplayScaleRailView railView() {
        return mRailView;
    }

    @Override
    public boolean onInterceptTouchEvent(@Nullable MotionEvent event) {
        if (event == null || mCancellingWindow) return false;
        if (event.getActionMasked() == MotionEvent.ACTION_DOWN) return onFrameDown(event);
        // Read before the event is played: the hold is claimed by its timer, so an event after it
        // is one the window must not see.
        boolean claimed = mHold.isClaimed();
        onHoldEvent(event);
        return claimed || mRail.isPressed();
    }

    @Override
    public boolean onTouchEvent(@Nullable MotionEvent event) {
        if (event == null || mCancellingWindow) return false;
        if (mRail.isPressed()) return mRail.onTouch(event);
        if (!mHold.isTracking()) return super.onTouchEvent(event);
        if (event.getActionMasked() != MotionEvent.ACTION_DOWN) onHoldEvent(event);
        return true;
    }

    /** The finger landed: the rail takes it at once; a corner only starts its timer. */
    private boolean onFrameDown(@NonNull MotionEvent event) {
        cancelHold();
        mDownX = event.getX();
        mDownY = event.getY();
        mRailOutAtDown = mRail.isShown();
        hideSharedLine();
        if (mRail.down(mDownX, mDownY)) return true;
        if (mRailOutAtDown) mRail.dismiss();
        if (event.getToolType(0) == MotionEvent.TOOL_TYPE_MOUSE || !mHost.hasShownWindow()) return false;
        float slop = ViewConfiguration.get(getContext()).getScaledTouchSlop();
        int corner = mHold.down(mDownX, mDownY, getWidth(), getHeight(),
            getResources().getDisplayMetrics().density, false, slop);
        if (corner == CornerZones.NONE) return false;
        mHandler.postDelayed(mHoldElapsed, HoldTiming.holdTimeoutMs());
        return false;
    }

    private void onHoldEvent(@NonNull MotionEvent event) {
        if (!mHold.isTracking()) return;
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_MOVE:
                if (mHold.move(event.getX(), event.getY()) == CornerHold.Move.ABANDONED)
                    mHandler.removeCallbacks(mHoldElapsed);
                break;
            case MotionEvent.ACTION_POINTER_DOWN:
                // Two fingers are a scroll or a pinch, never a hold.
                if (mHold.secondFinger()) mHandler.removeCallbacks(mHoldElapsed);
                break;
            case MotionEvent.ACTION_UP:
                mHandler.removeCallbacks(mHoldElapsed);
                if (mHold.lift() == CornerHold.Lift.OPEN_TAB && !mRailOutAtDown) {
                    if (mHost.shownWindowScales()) {
                        mRail.show();
                        String note = mHost.shownWindowScaleNote();
                        if (note != null) showLine(note);
                    } else {
                        showSharedLine();
                    }
                }
                break;
            case MotionEvent.ACTION_CANCEL:
                cancelHold();
                break;
            default:
                break;
        }
    }

    /** The hold time passed with the finger still: the window forgets the touch, the hand feels it. */
    private void onHoldElapsed() {
        if (!mHold.holdElapsed()) return;
        cancelWindowGesture();
        Haptics.tick(this, HapticFeedbackConstants.LONG_PRESS);
        ViewParent parent = getParent();
        if (parent != null) parent.requestDisallowInterceptTouchEvent(true);
    }

    /** A cancel down this frame's own dispatch, so the window holding the touch ends it with nothing pressed. */
    private void cancelWindowGesture() {
        long now = SystemClock.uptimeMillis();
        MotionEvent cancel = MotionEvent.obtain(now, now, MotionEvent.ACTION_CANCEL, mDownX, mDownY, 0);
        mCancellingWindow = true;
        try {
            dispatchTouchEvent(cancel);
        } finally {
            mCancellingWindow = false;
            cancel.recycle();
        }
    }

    private void cancelHold() {
        mHandler.removeCallbacks(mHoldElapsed);
        mHold.reset();
    }

    @Override
    protected void onDetachedFromWindow() {
        cancelHold();
        hideSharedLine();
        super.onDetachedFromWindow();
    }
}
