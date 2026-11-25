package com.termux.app.wayland;

import android.content.Context;
import android.content.res.ColorStateList;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.google.android.material.shape.MaterialShapeDrawable;
import com.termux.R;
import com.termux.app.chrome.ChromeShade;
import com.termux.app.material.M3;
import com.termux.view.HoldTiming;

/**
 * The rim tab's menu (spec #384): Close, Minimise, Move and Scale in a small menu-surface card under the
 * tab. Move is a handle, not an action: a tap does nothing and holding it lifts the window
 * ({@link Listener#lift}); the card then goes away and the carry takes the finger. Drawn inside the
 * Linux window's page so it moves with it. Long presses are used nowhere else here.
 */
final class LinuxWindowMenuView extends LinearLayout {

    /** What the rows do. */
    interface Listener {
        void close();
        void minimise();
        void scale();
        /** Move was held: lift the window with the finger at ({@code x}, {@code y}) in the window. */
        boolean lift(float x, float y, int pointerId);
    }

    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private final TextView mScale;
    private final TextView mMove;
    private final int[] mWhere = new int[2];
    @NonNull private final Listener mListener;
    private float mDownX;
    private float mDownY;
    private float mLastX;
    private float mLastY;
    private int mPointerId;
    private boolean mLifted;
    private final Runnable mLift = this::onHeld;

    LinuxWindowMenuView(@NonNull Context context, @NonNull Listener listener) {
        super(context);
        mListener = listener;
        setOrientation(VERTICAL);
        setVisibility(GONE);
        row(R.string.linux_windows_close, v -> {
            dismiss();
            mListener.close();
        });
        row(R.string.linux_window_minimise, v -> {
            dismiss();
            mListener.minimise();
        });
        mMove = row(R.string.linux_window_move, null);
        mMove.setOnTouchListener(this::onMoveTouch);
        mScale = row(R.string.linux_window_scale, v -> {
            dismiss();
            mListener.scale();
        });
    }

    private TextView row(int text, OnClickListener click) {
        TextView row = new TextView(getContext());
        row.setText(text);
        row.setSingleLine(true);
        row.setEllipsize(android.text.TextUtils.TruncateAt.END);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f);
        float density = getResources().getDisplayMetrics().density;
        int h = Math.round(16f * density);
        row.setPadding(h, 0, h, 0);
        row.setMinHeight(Math.round(48f * density));
        row.setMinWidth(Math.round(160f * density));
        if (click != null) row.setOnClickListener(click);
        addView(row, new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));
        return row;
    }

    /**
     * Dresses the card in the launcher's menu surface: the theme's opaque container colour on a
     * medium corner with the menu's outline, ink from the same theme, so labels read over any
     * window and the card follows the theme and the scheme chrome like every other menu.
     */
    void dress() {
        android.content.Context context = getContext();
        MaterialShapeDrawable plate = M3.surface(context,
            com.google.android.material.R.attr.shapeAppearanceCornerMedium,
            M3.surfaceContainer(context));
        plate.setStroke(getResources().getDimension(
                com.google.android.material.R.dimen.m3_comp_outlined_card_outline_width),
            ColorStateList.valueOf(ChromeShade.rim(M3.stateLayer(M3.onSurface(context),
                M3.STATE_DRAGGED))));
        setBackground(plate);
        int ink = M3.onSurface(context);
        for (int i = 0; i < getChildCount(); i++) ((TextView) getChildAt(i)).setTextColor(ink);
    }

    /** Whether Scale can be chosen: an X window shares the server's scale. */
    void show(boolean scaleEnabled) {
        mScale.setEnabled(scaleEnabled);
        mScale.setAlpha(scaleEnabled ? 1f : 0.4f);
        setVisibility(VISIBLE);
    }

    void dismiss() {
        mHandler.removeCallbacks(mLift);
        if (getVisibility() != GONE) setVisibility(GONE);
    }

    boolean isOpen() {
        return getVisibility() == VISIBLE;
    }

    /** Whether ({@code x}, {@code y}) in the parent's coordinates is on the card. */
    boolean contains(float x, float y) {
        return getVisibility() == VISIBLE && x >= getLeft() && x <= getRight()
            && y >= getTop() && y <= getBottom();
    }

    private boolean onMoveTouch(View row, MotionEvent ev) {
        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                mLifted = false;
                mDownX = ev.getX();
                mDownY = ev.getY();
                mLastX = mDownX;
                mLastY = mDownY;
                mPointerId = ev.getPointerId(0);
                row.setPressed(true);
                mHandler.postDelayed(mLift, HoldTiming.holdTimeoutMs());
                return true;
            case MotionEvent.ACTION_MOVE: {
                float slop = ViewConfiguration.get(getContext()).getScaledTouchSlop();
                mLastX = ev.getX();
                mLastY = ev.getY();
                float dx = ev.getX() - mDownX;
                float dy = ev.getY() - mDownY;
                // A finger that sets off before the hold is not lifting anything.
                if (dx * dx + dy * dy > slop * slop) {
                    mHandler.removeCallbacks(mLift);
                    row.setPressed(false);
                }
                return true;
            }
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                mHandler.removeCallbacks(mLift);
                row.setPressed(false);
                return true;
            default:
                return true;
        }
    }

    /** The finger rested on Move for the hold: pick the window up. */
    private void onHeld() {
        if (mLifted) return;
        mLifted = true;
        mMove.setPressed(false);
        mMove.getLocationInWindow(mWhere);
        float x = mWhere[0] + mLastX;
        float y = mWhere[1] + mLastY;
        // The card goes first: the lifted window owns the finger from here.
        dismiss();
        mListener.lift(x, y, mPointerId);
    }
}
