package com.termux.app.terminal.inappkeyboard;

import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * The in-app keyboard's container. While the keyboard is laid out but down the stack's
 * translation leaves the strip of it that stands above the screen's bottom gap on the screen,
 * clipped from view; clip bounds do not stop a touch, so it refuses touch, hover and
 * accessibility focus outright and the touch falls to what is under it. Written by the stack's
 * renderer alone.
 */
public class KeyboardContainerLayout extends LinearLayout {

    private boolean mTouchRefused;

    public KeyboardContainerLayout(@NonNull Context context) {
        super(context);
    }

    public KeyboardContainerLayout(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    public KeyboardContainerLayout(@NonNull Context context, @Nullable AttributeSet attrs,
                                   int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    /** Whether the keyboard is down: no touch, no accessibility focus. A write only on a change. */
    public void setTouchRefused(boolean refused) {
        if (mTouchRefused == refused) return;
        mTouchRefused = refused;
        setImportantForAccessibility(refused ? IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
            : IMPORTANT_FOR_ACCESSIBILITY_AUTO);
    }

    public boolean isTouchRefused() {
        return mTouchRefused;
    }

    @Override public boolean dispatchTouchEvent(MotionEvent event) {
        return !mTouchRefused && super.dispatchTouchEvent(event);
    }

    @Override public boolean dispatchGenericMotionEvent(MotionEvent event) {
        return !mTouchRefused && super.dispatchGenericMotionEvent(event);
    }

    @Override public boolean onInterceptHoverEvent(MotionEvent event) {
        return mTouchRefused || super.onInterceptHoverEvent(event);
    }
}
