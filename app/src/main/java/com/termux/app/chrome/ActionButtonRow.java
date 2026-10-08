package com.termux.app.chrome;

import android.content.Context;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * A card's action buttons, laid out the way a Material 3 dialog lays out its actions, decided by
 * measuring the buttons rather than by guessing at their labels.
 *
 * <p>Every child is an action, read in child order, except an optional leading child set with
 * {@link #setLeading} — a link such as "Read the docs" that belongs on the same row but is not an
 * action. When every visible child, at its own width and with {@link #GAP_DP} between each, fits
 * the width the row is given, they share one row: the leading child at the start edge and the
 * actions packed against the end edge in reading order. When they do not, they stack, each as wide
 * as the row: the actions in reverse, so the last one — the confirming action in a dialog's order
 * — is on top, and the leading child at the bottom.
 *
 * <p>The decision is made in {@link #onMeasure} against the width actually offered, so it follows
 * the card's width, the font scale and the labels themselves wherever they change, with nothing to
 * re-evaluate by hand. Every child is held to a 48dp touch height and one line; a label wider than
 * the row even on its own line is ellipsized, which is the last resort after stacking.
 */
public final class ActionButtonRow extends ViewGroup {

    /** The space between two actions, side by side or stacked: Material's dialog action gap. */
    private static final float GAP_DP = 8f;
    /** The smallest touch target an action may have. */
    private static final float MIN_TOUCH_DP = 48f;

    private final int mGap;
    private final int mMinTouch;
    @Nullable private View mLeading;
    private boolean mStacked;

    public ActionButtonRow(@NonNull Context context) {
        super(context);
        float density = context.getResources().getDisplayMetrics().density;
        mGap = Math.round(GAP_DP * density);
        mMinTouch = Math.round(MIN_TOUCH_DP * density);
    }

    /**
     * The child that leads the row rather than being one of its actions, or null for none. It is
     * added as the first child; the one it replaces is removed.
     */
    public void setLeading(@Nullable View leading) {
        if (mLeading == leading) return;
        if (mLeading != null) removeView(mLeading);
        mLeading = leading;
        if (leading != null) addView(leading, 0);
    }

    /** Whether the last measure stacked the children rather than putting them on one row. */
    public boolean isStacked() {
        return mStacked;
    }

    @Override
    public void onViewAdded(View child) {
        super.onViewAdded(child);
        // Held here once, rather than by every card that builds a button.
        if (child instanceof TextView) {
            TextView label = (TextView) child;
            label.setMaxLines(1);
            label.setEllipsize(TextUtils.TruncateAt.END);
            if (label.getMinHeight() < mMinTouch) label.setMinHeight(mMinTouch);
        } else if (child.getMinimumHeight() < mMinTouch) {
            child.setMinimumHeight(mMinTouch);
        }
    }

    @Override
    public void onViewRemoved(View child) {
        super.onViewRemoved(child);
        if (child == mLeading) mLeading = null;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int widthMode = MeasureSpec.getMode(widthMeasureSpec);
        int horizontalPadding = getPaddingLeft() + getPaddingRight();
        int verticalPadding = getPaddingTop() + getPaddingBottom();
        boolean bounded = widthMode != MeasureSpec.UNSPECIFIED;
        int available = bounded
            ? Math.max(0, MeasureSpec.getSize(widthMeasureSpec) - horizontalPadding)
            : Integer.MAX_VALUE;
        int ownWidth = bounded ? MeasureSpec.makeMeasureSpec(available, MeasureSpec.AT_MOST)
            : MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED);
        int anyHeight = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED);

        // Every child at its own width first: that, and nothing estimated, decides the layout.
        int rowWidth = 0;
        int widest = 0;
        int tallest = 0;
        int shown = 0;
        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            if (child.getVisibility() == GONE) continue;
            child.measure(ownWidth, anyHeight);
            rowWidth += child.getMeasuredWidth();
            widest = Math.max(widest, child.getMeasuredWidth());
            tallest = Math.max(tallest, child.getMeasuredHeight());
            shown++;
        }
        if (shown > 1) rowWidth += mGap * (shown - 1);
        mStacked = shown > 1 && rowWidth > available;

        int contentWidth;
        int contentHeight;
        if (mStacked) {
            contentWidth = widthMode == MeasureSpec.EXACTLY ? available
                : Math.min(widest, available);
            int fullWidth = MeasureSpec.makeMeasureSpec(contentWidth, MeasureSpec.EXACTLY);
            contentHeight = mGap * (shown - 1);
            for (int i = 0; i < getChildCount(); i++) {
                View child = getChildAt(i);
                if (child.getVisibility() == GONE) continue;
                child.measure(fullWidth, anyHeight);
                contentHeight += child.getMeasuredHeight();
            }
        } else {
            contentWidth = widthMode == MeasureSpec.EXACTLY ? available : rowWidth;
            contentHeight = tallest;
        }
        setMeasuredDimension(
            resolveSize(contentWidth + horizontalPadding, widthMeasureSpec),
            resolveSize(contentHeight + verticalPadding, heightMeasureSpec));
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        int start = getPaddingLeft();
        int end = (r - l) - getPaddingRight();
        int top = getPaddingTop();
        if (mStacked) {
            int y = top;
            for (int i = getChildCount() - 1; i >= 0; i--) {
                View child = getChildAt(i);
                if (child == mLeading || child.getVisibility() == GONE) continue;
                child.layout(start, y, start + child.getMeasuredWidth(),
                    y + child.getMeasuredHeight());
                y += child.getMeasuredHeight() + mGap;
            }
            if (mLeading != null && mLeading.getVisibility() != GONE) {
                mLeading.layout(start, y, start + mLeading.getMeasuredWidth(),
                    y + mLeading.getMeasuredHeight());
            }
            return;
        }

        int rowHeight = (b - t) - top - getPaddingBottom();
        boolean rtl = getLayoutDirection() == LAYOUT_DIRECTION_RTL;
        // The actions, last first, from the end edge inwards.
        int edge = rtl ? start : end;
        for (int i = getChildCount() - 1; i >= 0; i--) {
            View child = getChildAt(i);
            if (child == mLeading || child.getVisibility() == GONE) continue;
            int width = child.getMeasuredWidth();
            int left = rtl ? edge : edge - width;
            int y = top + (rowHeight - child.getMeasuredHeight()) / 2;
            child.layout(left, y, left + width, y + child.getMeasuredHeight());
            edge = rtl ? edge + width + mGap : edge - width - mGap;
        }
        if (mLeading != null && mLeading.getVisibility() != GONE) {
            int width = mLeading.getMeasuredWidth();
            int left = rtl ? end - width : start;
            int y = top + (rowHeight - mLeading.getMeasuredHeight()) / 2;
            mLeading.layout(left, y, left + width, y + mLeading.getMeasuredHeight());
        }
    }

    @Override
    public boolean shouldDelayChildPressedState() {
        return false;
    }
}
