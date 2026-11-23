package com.termux.app.statusbar;

import android.content.Context;
import android.graphics.Canvas;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import android.widget.LinearLayout;

/**
 * The status row's readings (CPU, RAM, battery, weather and the rest, each with the dot before
 * it). A plain row of them, except that the status row may tell it to keep only its first few
 * shown items when the room beside the camera is short ({@link StatusRowKeepOut#partAtGap}): it
 * is then only as wide as those, and the rest are neither drawn nor reachable by touch, since
 * they stand outside its bounds. Their visibility is left alone: StatusBarWidgets owns it and
 * sets it on every refresh.
 */
public final class StatusReadingsCluster extends LinearLayout {

    /** {@link #setKept}: every shown item stands, as in any linear layout. */
    public static final int ALL = -1;

    private int mKept = ALL;

    public StatusReadingsCluster(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    /**
     * Keeps the first {@code kept} shown (not gone) items, or {@link #ALL}. Called by the status
     * row inside its own measure: a change marks this view and its parents up to the row for a
     * fresh measure (flags only, no layout request), so the row's next measure of them reads it.
     */
    void setKept(int kept, @NonNull View row) {
        int value = kept < 0 ? ALL : kept;
        if (value == mKept) return;
        mKept = value;
        View view = this;
        while (view != null && view != row) {
            view.forceLayout();
            view = view.getParent() instanceof View ? (View) view.getParent() : null;
        }
    }

    /** How many shown items stand, or {@link #ALL}. */
    public int kept() {
        return mKept;
    }

    /** Whether {@code child} is one of the items left out for room. */
    public boolean isLeftOut(@NonNull View child) {
        if (mKept == ALL || child.getVisibility() == GONE) return false;
        int shown = 0;
        for (int i = 0; i < getChildCount(); i++) {
            View item = getChildAt(i);
            if (item == child) return shown >= mKept;
            if (item.getVisibility() != GONE) shown++;
        }
        return false;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        if (mKept == ALL || getOrientation() != HORIZONTAL) return;
        int width = getPaddingLeft() + getPaddingRight();
        int shown = 0;
        for (int i = 0; i < getChildCount() && shown < mKept; i++) {
            View child = getChildAt(i);
            if (child.getVisibility() == GONE) continue;
            LayoutParams params = (LayoutParams) child.getLayoutParams();
            width += params.leftMargin + child.getMeasuredWidth() + params.rightMargin;
            shown++;
        }
        setMeasuredDimension(width, getMeasuredHeight());
    }

    @Override
    protected boolean drawChild(@NonNull Canvas canvas, @NonNull View child, long drawingTime) {
        if (mKept != ALL && getOrientation() == HORIZONTAL && isLeftOut(child)) return false;
        return super.drawChild(canvas, child, drawingTime);
    }
}
