package com.termux.app.place;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * The content container: it keeps its bottom padding ({@link BottomInsets#keptPx}) clear for every
 * child but one, the stack's layout ({@link #setReachingChild}), which it measures and lays out
 * down to its own bottom edge so the dock's surface can reach the screen's bottom (ADR 0018 §1).
 */
public class BottomInsetFrameLayout extends FrameLayout {

    @Nullable private View mReaching;

    public BottomInsetFrameLayout(@NonNull Context context) {
        super(context);
    }

    public BottomInsetFrameLayout(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    public BottomInsetFrameLayout(@NonNull Context context, @Nullable AttributeSet attrs,
                                  int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    /** The one child that ignores the bottom padding. */
    public void setReachingChild(@Nullable View child) {
        if (mReaching == child) return;
        mReaching = child;
        requestLayout();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        View child = reaching();
        if (child == null) return;
        MarginLayoutParams params = (MarginLayoutParams) child.getLayoutParams();
        int height = Math.max(0, getMeasuredHeight() - getPaddingTop() - params.topMargin
            - params.bottomMargin);
        if (child.getMeasuredHeight() == height) return;
        child.measure(MeasureSpec.makeMeasureSpec(child.getMeasuredWidth(), MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY));
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        View child = reaching();
        if (child == null) return;
        int childBottom = child.getTop() + child.getMeasuredHeight();
        if (child.getBottom() != childBottom)
            child.layout(child.getLeft(), child.getTop(), child.getRight(), childBottom);
    }

    /**
     * {@code child}'s height less the part laid out under its container's kept band: the room a
     * reader of the layout's height may use, the same number it read before the layout reached.
     */
    public static int roomHeightPx(@NonNull View child) {
        int height = child.getHeight();
        if (!(child.getParent() instanceof BottomInsetFrameLayout)) return height;
        View container = (View) child.getParent();
        int reached = child.getBottom() - (container.getHeight() - container.getPaddingBottom());
        return height - Math.max(0, reached);
    }

    @Nullable
    private View reaching() {
        View child = mReaching;
        if (child == null || child.getParent() != this || child.getVisibility() == GONE
            || getPaddingBottom() <= 0
            || child.getLayoutParams().height != ViewGroup.LayoutParams.MATCH_PARENT)
            return null;
        return child;
    }
}
