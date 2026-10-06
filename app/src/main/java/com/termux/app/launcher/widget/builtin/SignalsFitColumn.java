package com.termux.app.launcher.widget.builtin;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;

/**
 * A column that shows as many of its rows as fit whole and leaves the rest out, so a list in a
 * fixed-height card loses its last row at a large font scale instead of showing half of it. The
 * first row is always laid out, clipped if it must be. Children take the column's full width.
 */
final class SignalsFitColumn extends ViewGroup {
    private final int gapPx;

    SignalsFitColumn(@NonNull Context context, int gapPx) {
        super(context);
        this.gapPx = gapPx;
    }

    @Override protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        int inner = Math.max(0, width - getPaddingLeft() - getPaddingRight());
        int childWidth = MeasureSpec.makeMeasureSpec(inner, MeasureSpec.EXACTLY);
        int childHeight = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED);
        int total = getPaddingTop() + getPaddingBottom();
        boolean first = true;
        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            if (child.getVisibility() == GONE) continue;
            child.measure(childWidth, childHeight);
            total += child.getMeasuredHeight() + (first ? 0 : gapPx);
            first = false;
        }
        setMeasuredDimension(width, resolveSize(total, heightMeasureSpec));
    }

    @Override protected void onLayout(boolean changed, int l, int t, int r, int b) {
        int left = getPaddingLeft();
        int right = r - l - getPaddingRight();
        int limit = b - t - getPaddingBottom();
        int y = getPaddingTop();
        boolean first = true, full = false;
        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            if (child.getVisibility() == GONE) continue;
            int top = first ? y : y + gapPx;
            int height = child.getMeasuredHeight();
            if (!first && (full || top + height > limit)) {
                full = true;
                child.layout(left, top, left, top);
                continue;
            }
            child.layout(left, top, right, top + height);
            y = top + height;
            first = false;
        }
    }
}
