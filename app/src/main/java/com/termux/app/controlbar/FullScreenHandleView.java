package com.termux.app.controlbar;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.view.MotionEvent;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.R;

/**
 * The full-screen handle (#426): a transparent touch band with a thin glass pill at its foot,
 * standing where the control bar's bottom was. A slide up on it or a tap asks for the bar, as does
 * TalkBack's click. Geometry is {@link FullScreenHandle}'s.
 */
final class FullScreenHandleView extends FrameLayout {

    /** What the handle asks. */
    interface Listener {
        void onRaise();
    }

    @NonNull private final Listener mListener;
    @NonNull private final View mPill;
    private final float mDensity;
    private final int mTouchSlop;
    private float mDownX;
    private float mDownY;
    private boolean mTracking;
    private boolean mFired;
    /** Whether the pill wears the look's glass rather than the plain ink. */
    private boolean mPillGlass;

    FullScreenHandleView(@NonNull Context context, @NonNull Listener listener) {
        super(context);
        mListener = listener;
        mDensity = context.getResources().getDisplayMetrics().density;
        mTouchSlop = android.view.ViewConfiguration.get(context).getScaledTouchSlop();
        mPill = new View(context);
        mPill.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        addView(mPill, new LayoutParams(0, 0));
        setContentDescription(context.getString(R.string.control_bar_handle));
        setVisibility(GONE);
    }

    /** The pill in the scheme's ink at half strength, until glass cut for its size is ready. */
    void setPillInk(int ink) {
        if (mPillGlass) return;
        GradientDrawable plain = new GradientDrawable();
        plain.setColor((ink & 0x00FFFFFF) | 0x99000000);
        plain.setCornerRadius(FullScreenHandle.PILL_HEIGHT_DP * mDensity / 2f);
        mPill.setBackground(plain);
    }

    /** The pill, for the bar to dress with glass. */
    @NonNull
    View pill() {
        return mPill;
    }

    /** Dresses the pill: {@code glass} cut for its size, or null to keep what it had. */
    void setPillGlass(@Nullable Drawable glass) {
        if (glass == null) return;
        mPillGlass = true;
        mPill.setBackground(glass);
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        float[] pill = FullScreenHandle.pillRect(r - l, b - t, mDensity);
        mPill.layout(Math.round(pill[0]), Math.round(pill[1]), Math.round(pill[2]), Math.round(pill[3]));
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        super.onMeasure(widthSpec, heightSpec);
        float[] pill = FullScreenHandle.pillRect(getMeasuredWidth(), getMeasuredHeight(), mDensity);
        mPill.measure(MeasureSpec.makeMeasureSpec(Math.round(pill[2] - pill[0]), MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(Math.round(pill[3] - pill[1]), MeasureSpec.EXACTLY));
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouchEvent(@NonNull MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                mTracking = FullScreenHandle.bandContains(event.getX(), event.getY(), getWidth(), getHeight());
                mFired = false;
                mDownX = event.getX();
                mDownY = event.getY();
                // The pages host must not take the slide from under the finger.
                if (mTracking && getParent() != null) getParent().requestDisallowInterceptTouchEvent(true);
                return mTracking;
            case MotionEvent.ACTION_MOVE:
                if (mTracking && !mFired && FullScreenHandle.isSlideUp(event.getY() - mDownY,
                        event.getX() - mDownX, mDensity)) {
                    mFired = true;
                    mListener.onRaise();
                }
                return mTracking;
            case MotionEvent.ACTION_UP:
                boolean tracked = mTracking;
                mTracking = false;
                // A tap brings the bar back as a slide does (round 3).
                if (tracked && !mFired && FullScreenHandle.isTap(event.getY() - mDownY,
                        event.getX() - mDownX, mTouchSlop)) {
                    mFired = true;
                    mListener.onRaise();
                }
                return tracked;
            case MotionEvent.ACTION_CANCEL:
                boolean was = mTracking;
                mTracking = false;
                return was;
            default:
                return mTracking;
        }
    }

    @Override
    public boolean performClick() {
        // TalkBack's click: there is no slide to make.
        mListener.onRaise();
        return true;
    }

    @Override
    public void onInitializeAccessibilityNodeInfo(@NonNull AccessibilityNodeInfo info) {
        super.onInitializeAccessibilityNodeInfo(info);
        info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_CLICK);
        info.setClickable(true);
    }
}
