package com.termux.app.statusbar;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.ColorUtils;

import com.google.android.material.color.MaterialColors;
import com.termux.R;

/** Thin bottom-edge handle that collapses or expands the top clock surface. */
public final class StatusBarGrabHandleView extends View {

    private static final int IDLE_ALPHA = 96;
    private static final int ACTIVE_HALO_ALPHA = 112;
    private static final float HIT_TARGET_ABOVE_EDGE_DP = 5f;
    private static final float HIT_TARGET_BELOW_EDGE_DP = 30f;

    public interface Listener {
        void onCollapsedStateRequested(boolean collapsed);

        default void onResizeDragStarted() {}

        default void onResizeDragProgress(float deltaY) {}

        default void onResizeDragFinished(boolean collapsed) {}
    }

    private final Paint mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private int mIdleColor;
    private int mActiveColor;
    @Nullable private Listener mListener;
    private float mDownY;
    private boolean mCollapsed;
    private boolean mGestureStartedCollapsed;
    private boolean mDragging;
    private float mMaximumDragDistance;
    private final int[] mLocationOnScreen = new int[2];

    public StatusBarGrabHandleView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        setClickable(true);
        setFocusable(true);
        setContentDescription("Collapse clock");
        int color = MaterialColors.getColor(context,
            com.termux.shared.R.attr.termuxColorOnSurfaceVariant,
            ContextCompat.getColor(context, R.color.termux_on_surface_variant));
        // Two Material emphasis steps above the old near-invisible edge tint.
        mIdleColor = ColorUtils.setAlphaComponent(color, IDLE_ALPHA);
        mActiveColor = MaterialColors.getColor(context,
            com.termux.shared.R.attr.termuxColorPrimary,
            ContextCompat.getColor(context, R.color.termux_primary));
    }

    public void setListener(@Nullable Listener listener) {
        mListener = listener;
    }

    public void setCollapsed(boolean collapsed) {
        mCollapsed = collapsed;
        setContentDescription(collapsed ? "Expand clock" : "Collapse clock");
    }

    /**
     * The visible strip sits on the physical bottom edge. Its extended target deliberately keeps
     * only a small allowance above that edge (where the window row lives) and places most of the
     * forgiving touch area in the terminal surface below it.
     */
    public boolean containsExtendedTouchPoint(float rawX, float rawY) {
        if (getWidth() <= 0 || getHeight() <= 0) return false;
        getLocationOnScreen(mLocationOnScreen);
        float centerX = mLocationOnScreen[0] + getWidth() / 2f;
        float edgeY = mLocationOnScreen[1] + getHeight() - dp(.75f);
        return rawX >= centerX - getWidth() / 2f
            && rawX <= centerX + getWidth() / 2f
            && rawY >= edgeY - dp(HIT_TARGET_ABOVE_EDGE_DP)
            && rawY <= edgeY + dp(HIT_TARGET_BELOW_EDGE_DP);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                mDownY = event.getRawY();
                mGestureStartedCollapsed = mCollapsed;
                mDragging = false;
                mMaximumDragDistance = 0f;
                setPressed(true);
                invalidate();
                return true;
            case MotionEvent.ACTION_MOVE:
                float moveDelta = event.getRawY() - mDownY;
                mMaximumDragDistance = Math.max(mMaximumDragDistance, Math.abs(moveDelta));
                if (!mDragging && Math.abs(moveDelta) >= dp(1)) {
                    mDragging = true;
                    if (mListener != null) mListener.onResizeDragStarted();
                }
                if (mDragging && mListener != null) {
                    mListener.onResizeDragProgress(moveDelta);
                }
                return true;
            case MotionEvent.ACTION_UP:
                setPressed(false);
                invalidate();
                float dy = event.getRawY() - mDownY;
                mMaximumDragDistance = Math.max(mMaximumDragDistance, Math.abs(dy));
                boolean wasDrag = mDragging && mMaximumDragDistance >= dp(8);
                if (mListener != null) {
                    if (wasDrag) {
                        boolean collapsed = Math.abs(dy) < dp(1)
                            ? mGestureStartedCollapsed : dy < 0;
                        mListener.onResizeDragFinished(collapsed);
                    } else if (mDragging) {
                        // Preserve tap behavior despite normal small finger drift. The previewed
                        // pixels settle into the toggled state rather than snapping first.
                        mListener.onResizeDragFinished(!mGestureStartedCollapsed);
                    } else {
                        mListener.onCollapsedStateRequested(!mGestureStartedCollapsed);
                    }
                }
                if (!wasDrag) performClick();
                mDragging = false;
                return true;
            case MotionEvent.ACTION_CANCEL:
                setPressed(false);
                invalidate();
                if (mDragging && mListener != null) {
                    mListener.onResizeDragFinished(mGestureStartedCollapsed);
                }
                mDragging = false;
                return true;
            default:
                return true;
        }
    }

    @Override
    public boolean performClick() {
        super.performClick();
        return true;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float centerY = getHeight() - dp(.75f);
        // 26dp idle length is 30% longer than the previous 20dp strip.
        float halfWidth = dp(isPressed() ? 15.5f : 13f);
        if (isPressed()) {
            mPaint.setColor(ColorUtils.setAlphaComponent(mActiveColor, ACTIVE_HALO_ALPHA));
            canvas.drawRoundRect(getWidth() / 2f - halfWidth - dp(3), centerY - dp(3),
                getWidth() / 2f + halfWidth + dp(3), centerY + dp(3), dp(3), dp(3), mPaint);
            mPaint.setColor(mActiveColor);
        } else {
            mPaint.setColor(mIdleColor);
        }
        float halfHeight = dp(isPressed() ? 1.25f : .55f);
        canvas.drawRoundRect(getWidth() / 2f - halfWidth, centerY - halfHeight,
            getWidth() / 2f + halfWidth, centerY + halfHeight,
            halfHeight, halfHeight, mPaint);
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }
}
