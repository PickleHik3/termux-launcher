package com.termux.app.dock;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * The extra-keys row's host in the bottom stack. It is a plain frame around the bar except that
 * its own height is the height of what is not the {@link KeysCardLayout} standing in it: the card
 * is laid out at the answer's exact height, which may run below the row, and must never size the
 * row. One pass: the children are measured as a frame measures them, then only the host's own
 * height is worked out again without the card.
 */
public final class KeysRowHostLayout extends FrameLayout {

    public KeysRowHostLayout(@NonNull Context context) {
        this(context, null);
    }

    public KeysRowHostLayout(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        if (MeasureSpec.getMode(heightMeasureSpec) == MeasureSpec.EXACTLY) return;
        int rows = 0;
        boolean sawCard = false;
        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            if (child.getVisibility() == GONE) continue;
            if (child instanceof KeysCardLayout) {
                sawCard = true;
                continue;
            }
            LayoutParams lp = (LayoutParams) child.getLayoutParams();
            rows = Math.max(rows, child.getMeasuredHeight() + lp.topMargin + lp.bottomMargin);
        }
        if (!sawCard) return;
        int height = Math.max(rows + getPaddingTop() + getPaddingBottom(), getSuggestedMinimumHeight());
        setMeasuredDimension(getMeasuredWidthAndState(),
            resolveSizeAndState(height, heightMeasureSpec, 0));
    }
}
