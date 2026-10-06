package com.termux.app.launcher.widget.builtin;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;

/**
 * A vertical list of full-width rows that shows only the rows that fit whole. A row that would
 * be cut by the bottom edge, which is what a large font scale does to a fixed-height card, is
 * laid out at zero size instead, so it is neither drawn half nor touchable.
 */
final class CalendarFitColumn extends ViewGroup {
    private final int gapPx;
    private int shown;

    CalendarFitColumn(@NonNull Context context, int gapPx) {
        super(context);
        this.gapPx = gapPx;
    }

    @Override protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        int inner = Math.max(0, width - getPaddingLeft() - getPaddingRight());
        int heightMode = MeasureSpec.getMode(heightMeasureSpec);
        int limit = heightMode == MeasureSpec.UNSPECIFIED ? Integer.MAX_VALUE
            : MeasureSpec.getSize(heightMeasureSpec) - getPaddingTop() - getPaddingBottom();
        int childWidth = MeasureSpec.makeMeasureSpec(inner, MeasureSpec.EXACTLY);
        int childHeight = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED);
        int used = 0;
        shown = 0;
        boolean full = false;
        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            if (child.getVisibility() == GONE) continue;
            child.measure(childWidth, childHeight);
            int next = used + (shown > 0 ? gapPx : 0) + child.getMeasuredHeight();
            if (full || next > limit) {
                full = true;
                continue;
            }
            used = next;
            shown++;
        }
        int height = used + getPaddingTop() + getPaddingBottom();
        setMeasuredDimension(resolveSize(width, widthMeasureSpec),
            heightMode == MeasureSpec.EXACTLY ? MeasureSpec.getSize(heightMeasureSpec)
                : resolveSize(height, heightMeasureSpec));
    }

    @Override protected void onLayout(boolean changed, int l, int t, int r, int b) {
        int left = getPaddingLeft();
        int top = getPaddingTop();
        int placed = 0;
        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            if (child.getVisibility() == GONE) continue;
            if (placed >= shown) {
                child.layout(0, 0, 0, 0);
                continue;
            }
            if (placed > 0) top += gapPx;
            child.layout(left, top, left + child.getMeasuredWidth(), top + child.getMeasuredHeight());
            top += child.getMeasuredHeight();
            placed++;
        }
    }
}
