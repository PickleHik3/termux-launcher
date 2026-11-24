package com.termux.app.wayland;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
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
 * <p>A press that lands in a corner square is held back from the window until it is known not to
 * be a hold ({@link CornerHoldArbiter}, {@link HoldTiming#holdTimeoutMs()}): a lift, a move past
 * the slop or a second finger hands the window the press and the rest of the gesture, so an app's
 * own corner controls still answer a tap or a drag; a hold is the frame's alone, so the app never
 * sees a touch it could take as its own long press. (The display window instead forwards the
 * press and sends X a cancel when the hold fires; an app's long press can beat that cancel.)
 * A mouse never holds a corner.
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
    /** A press in a corner square, held back from the window until it is known not to be a hold. */
    @Nullable private MotionEvent mHeldDown;
    /** The window that press landed on, which gets it (and the rest) if it is not a hold. */
    @Nullable private View mTarget;
    /** The held-back press was handed to the window: the rest of its gesture follows it there. */
    private boolean mPassing;
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
        // Only the landing decides: a press this frame takes (the rail, a corner) is its own from
        // then on, and a press it leaves goes to the window whole.
        if (event == null || event.getActionMasked() != MotionEvent.ACTION_DOWN) return false;
        return onFrameDown(event);
    }

    @Override
    public boolean onTouchEvent(@Nullable MotionEvent event) {
        if (event == null) return false;
        if (mRail.isPressed()) return mRail.onTouch(event);
        if (mPassing) {
            forward(event);
            if (endsGesture(event)) endPassing();
            return true;
        }
        if (mHeldDown == null) return super.onTouchEvent(event);
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_MOVE:
                if (mHold.move(event.getX(), event.getY()) == CornerHold.Move.ABANDONED) pass(event);
                break;
            case MotionEvent.ACTION_POINTER_DOWN:
                // Two fingers are a scroll or a pinch, never a hold: the window has them both.
                if (mHold.secondFinger()) pass(event);
                break;
            case MotionEvent.ACTION_UP:
                mHandler.removeCallbacks(mHoldElapsed);
                if (!mHold.isClaimed()) {
                    // A tap in the corner: the window gets it whole, on the lift.
                    pass(event);
                    break;
                }
                mHold.lift();
                dropHeldDown();
                if (!mRailOutAtDown) {
                    if (mHost.shownWindowScales()) mRail.show();
                    else showSharedLine();
                }
                break;
            case MotionEvent.ACTION_CANCEL:
                cancelHold();
                break;
            default:
                break;
        }
        return true;
    }

    /**
     * The finger landed: the rail takes it at once. One in a corner square is held back from the
     * window until it is known not to be a hold, so an app never sees the start of a press that
     * turns into one (its own long press could otherwise fire first and stay open).
     */
    private boolean onFrameDown(@NonNull MotionEvent event) {
        cancelHold();
        endPassing();
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
        mHeldDown = MotionEvent.obtain(event);
        mTarget = windowAt(mDownX, mDownY);
        mHandler.postDelayed(mHoldElapsed, HoldTiming.holdTimeoutMs());
        return true;
    }

    /** The hold time passed with the finger still: the corner has it, and the window never saw it. */
    private void onHoldElapsed() {
        if (!mHold.holdElapsed()) return;
        Haptics.tick(this, HapticFeedbackConstants.LONG_PRESS);
        ViewParent parent = getParent();
        if (parent != null) parent.requestDisallowInterceptTouchEvent(true);
    }

    /** Not a hold after all: the window gets the held-back press, then {@code event} and the rest. */
    private void pass(@NonNull MotionEvent event) {
        mHandler.removeCallbacks(mHoldElapsed);
        mHold.reset();
        MotionEvent down = mHeldDown;
        mHeldDown = null;
        mPassing = true;
        if (down != null) {
            forward(down);
            down.recycle();
        }
        forward(event);
        if (endsGesture(event)) endPassing();
    }

    /** {@code event} to the window the press landed on, in its coordinates. */
    private void forward(@NonNull MotionEvent event) {
        View target = mTarget;
        if (target == null) return;
        MotionEvent copy = MotionEvent.obtain(event);
        copy.offsetLocation(getScrollX() - target.getLeft(), getScrollY() - target.getTop());
        target.dispatchTouchEvent(copy);
        copy.recycle();
    }

    private static boolean endsGesture(@NonNull MotionEvent event) {
        int action = event.getActionMasked();
        return action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL;
    }

    private void endPassing() {
        mPassing = false;
        mTarget = null;
    }

    /** The topmost shown window under {@code (x, y)}; the rail and the line are not windows. */
    @Nullable
    private View windowAt(float x, float y) {
        for (int i = getChildCount() - 1; i >= 0; i--) {
            View child = getChildAt(i);
            if (child == mRailView || child == mSharedLine || child.getVisibility() != VISIBLE) continue;
            if (x >= child.getLeft() && x < child.getRight() && y >= child.getTop() && y < child.getBottom()) {
                return child;
            }
        }
        return null;
    }

    private void dropHeldDown() {
        if (mHeldDown != null) mHeldDown.recycle();
        mHeldDown = null;
    }

    private void cancelHold() {
        mHandler.removeCallbacks(mHoldElapsed);
        mHold.reset();
        dropHeldDown();
    }

    @Override
    protected void onDetachedFromWindow() {
        cancelHold();
        hideSharedLine();
        super.onDetachedFromWindow();
    }
}
