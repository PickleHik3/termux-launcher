package com.termux.app.launcher.widget;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/** Hard visual and hit boundary around one provider-owned host view. */
public final class WidgetCellView extends FrameLayout {
    public enum DownRegion { LAUNCHER_GUTTER, NON_INTERACTIVE_PROVIDER,
        INTERACTIVE_PROVIDER, SCROLLING_PROVIDER }

    /**
     * Launcher-owned long-press handler. When the long press fires the provider's stream is
     * cancelled and the remainder of the gesture is delivered here as an edit drag, matching
     * standard launcher widget behavior (the provider must never treat it as a tap).
     */
    public interface LongPressListener {
        void onWidgetLongPress(float rawX, float rawY);
        default void onEditDragMove(float rawX, float rawY) { }
        default void onEditDragEnd(boolean canceled) { }
    }

    private final int gutter;
    private final int touchSlop;
    private boolean touchStreamAccepted;
    @Nullable private LongPressListener longPressListener;
    private final Runnable longPressFire = this::fireLongPress;
    private boolean longPressPending;
    private boolean streamTakenOver;
    private float longPressDownX, longPressDownY;
    private float lastRawX, lastRawY;

    public WidgetCellView(@NonNull Context context) {
        super(context);
        gutter = Math.max(1, Math.round(2f * getResources().getDisplayMetrics().density));
        touchSlop = android.view.ViewConfiguration.get(context).getScaledTouchSlop();
        setPadding(gutter, gutter, gutter, gutter);
        setClipChildren(true);
        setClipToPadding(true);
        setWillNotDraw(false);
    }

    public void setLongPressListener(@Nullable LongPressListener listener) {
        longPressListener = listener;
        if (listener == null) cancelLongPressWatch();
    }

    public void setContent(@NonNull View child) {
        removeAllViews();
        if (child.getParent() instanceof ViewGroup) ((ViewGroup) child.getParent()).removeView(child);
        addView(child, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
    }

    @NonNull public DownRegion classifyDown(float x, float y) {
        if (x < gutter || y < gutter || x >= getWidth() - gutter || y >= getHeight() - gutter) {
            return DownRegion.LAUNCHER_GUTTER;
        }
        View hit = findDeepest(this, x, y);
        if (hit == null || hit == this) return DownRegion.NON_INTERACTIVE_PROVIDER;
        View current = hit;
        boolean interactive = false;
        while (current != null && current != this) {
            if (androidx.core.view.ViewCompat.isNestedScrollingEnabled(current)) {
                return DownRegion.SCROLLING_PROVIDER;
            }
            interactive |= current.isClickable() || current.isLongClickable() || current.isFocusable();
            android.view.ViewParent parent = current.getParent();
            current = parent instanceof View ? (View) parent : null;
        }
        return interactive ? DownRegion.INTERACTIVE_PROVIDER : DownRegion.NON_INTERACTIVE_PROVIDER;
    }

    @Override protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
        super.onSizeChanged(width, height, oldWidth, oldHeight);
        setClipBounds(new Rect(0, 0, width, height));
    }

    @Override protected void dispatchDraw(@NonNull Canvas canvas) {
        int save = canvas.save();
        canvas.clipRect(0, 0, getWidth(), getHeight());
        super.dispatchDraw(canvas);
        canvas.restoreToCount(save);
    }

    @Override public boolean dispatchTouchEvent(@NonNull MotionEvent event) {
        float x = event.getX(), y = event.getY();
        int action = event.getActionMasked();
        lastRawX = event.getRawX(); lastRawY = event.getRawY();
        if (streamTakenOver) {
            switch (action) {
                case MotionEvent.ACTION_MOVE:
                    if (longPressListener != null) {
                        longPressListener.onEditDragMove(event.getRawX(), event.getRawY());
                    }
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    streamTakenOver = false;
                    touchStreamAccepted = false;
                    if (longPressListener != null) {
                        longPressListener.onEditDragEnd(action == MotionEvent.ACTION_CANCEL);
                    }
                    break;
                default:
                    break;
            }
            return true;
        }
        watchLongPress(event, action, x, y);
        if (action == MotionEvent.ACTION_DOWN) {
            touchStreamAccepted = x >= 0 && y >= 0 && x < getWidth() && y < getHeight();
            if (!touchStreamAccepted) return false;
        } else if (!touchStreamAccepted) {
            return false;
        }
        boolean handled = super.dispatchTouchEvent(event);
        // If the provider ignores DOWN, retain only this otherwise-unowned stream so its pending
        // long press can be cancelled on UP. A provider that accepted it keeps the normal result.
        if (longPressListener != null && touchStreamAccepted) handled = true;
        if (action == MotionEvent.ACTION_DOWN && !handled) touchStreamAccepted = false;
        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            touchStreamAccepted = false;
        }
        return handled;
    }

    private void watchLongPress(@NonNull MotionEvent event, int action, float x, float y) {
        if (longPressListener == null) return;
        switch (action) {
            case MotionEvent.ACTION_DOWN:
                longPressDownX = x; longPressDownY = y;
                longPressPending = true;
                postDelayed(longPressFire,
                    android.view.ViewConfiguration.getLongPressTimeout());
                break;
            case MotionEvent.ACTION_MOVE:
                if (longPressPending && Math.hypot(x - longPressDownX, y - longPressDownY)
                    > touchSlop) cancelLongPressWatch();
                break;
            case MotionEvent.ACTION_POINTER_DOWN:
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                cancelLongPressWatch();
                break;
        }
    }

    private void cancelLongPressWatch() {
        longPressPending = false;
        removeCallbacks(longPressFire);
    }

    private void fireLongPress() {
        if (!longPressPending || longPressListener == null) return;
        longPressPending = false;
        // Takeover: the provider's stream ends with CANCEL so its click/long-click can never
        // fire; the rest of this gesture belongs to the launcher as an edit drag.
        streamTakenOver = true;
        long now = android.os.SystemClock.uptimeMillis();
        MotionEvent cancel = MotionEvent.obtain(now, now, MotionEvent.ACTION_CANCEL,
            longPressDownX, longPressDownY, 0);
        super.dispatchTouchEvent(cancel);
        cancel.recycle();
        performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS);
        longPressListener.onWidgetLongPress(lastRawX, lastRawY);
    }

    private static View findDeepest(View view, float x, float y) {
        if (!(view instanceof ViewGroup)) return view;
        ViewGroup group = (ViewGroup) view;
        for (int i = group.getChildCount() - 1; i >= 0; i--) {
            View child = group.getChildAt(i);
            float cx = x - child.getLeft() + child.getScrollX();
            float cy = y - child.getTop() + child.getScrollY();
            if (child.getVisibility() == VISIBLE && cx >= 0 && cy >= 0
                && cx < child.getWidth() && cy < child.getHeight()) {
                return findDeepest(child, cx, cy);
            }
        }
        return view;
    }
}
