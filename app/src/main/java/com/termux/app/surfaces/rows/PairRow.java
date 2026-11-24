package com.termux.app.surfaces.rows;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Two cells on one row (spec #326): side by side with the card's 16dp gutter at both ends and
 * {@link PairFit#GAP_DP} between them (a {@code separator} such as the Grid's x standing between,
 * centred on the cells' first line), or one under the other where {@link PairFit} says a cell
 * would not hold its least width. Laid out by hand, like {@code SliderRow.Line}: the choice is
 * made while measuring and a restatement never asks for a layout. A cell that is gone leaves the
 * other the whole row.
 */
public final class PairRow extends ViewGroup {

    @NonNull private final View mFirst;
    @NonNull private final View mSecond;
    @Nullable private final View mSeparator;
    private final int mMinCellPx;
    private final int mGutterPx;
    private final int mGapPx;
    private final boolean mSeparatorStacks;
    private boolean mSideBySide;

    public PairRow(@NonNull Context context, @NonNull View first, @NonNull View second,
                   @Nullable View separator, int minCellPx) {
        this(context, first, second, separator, minCellPx, false);
    }

    /**
     * As above; with {@code separatorStacks} the separator (a control, as the opacity link is)
     * stands centred between the cells when they stack, instead of going.
     */
    public PairRow(@NonNull Context context, @NonNull View first, @NonNull View second,
                   @Nullable View separator, int minCellPx, boolean separatorStacks) {
        super(context);
        mSeparatorStacks = separatorStacks;
        mFirst = first;
        mSecond = second;
        mSeparator = separator;
        mMinCellPx = minCellPx;
        mGutterPx = RowKit.dp(context, RowKit.EDGE_DP);
        mGapPx = RowKit.dp(context, PairFit.GAP_DP);
        addView(first);
        if (separator != null)
            addView(separator);
        addView(second);
    }

    /** Whether the cells stood on one line at the last measure. */
    public boolean isSideBySide() {
        return mSideBySide;
    }

    private boolean standing(@NonNull View view) {
        return view.getVisibility() != GONE;
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        int full = MeasureSpec.getMode(widthSpec) == MeasureSpec.UNSPECIFIED
            ? RowKit.defaultRowWidthPx(getContext()) : MeasureSpec.getSize(widthSpec);
        int inner = Math.max(0, full - 2 * mGutterPx);
        int unspecified = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED);
        int separatorPx = 0;
        boolean both = standing(mFirst) && standing(mSecond);
        if (mSeparator != null && both) {
            mSeparator.measure(unspecified, unspecified);
            separatorPx = mSeparator.getMeasuredWidth();
        }
        mSideBySide = both && PairFit.sideBySide(inner, mGapPx, separatorPx, mMinCellPx);
        int height = 0;
        if (mSideBySide) {
            int cell = MeasureSpec.makeMeasureSpec(PairFit.cellPx(inner, mGapPx, separatorPx),
                MeasureSpec.EXACTLY);
            mFirst.measure(cell, unspecified);
            mSecond.measure(cell, unspecified);
            height = Math.max(mFirst.getMeasuredHeight(), mSecond.getMeasuredHeight());
        } else {
            if (stackedSeparator(both)) {
                mSeparator.measure(unspecified, unspecified);
                height += mSeparator.getMeasuredHeight();
            }
            int whole = MeasureSpec.makeMeasureSpec(inner, MeasureSpec.EXACTLY);
            for (View cell : new View[] {mFirst, mSecond}) {
                if (!standing(cell))
                    continue;
                cell.measure(whole, unspecified);
                height += cell.getMeasuredHeight();
            }
        }
        setMeasuredDimension(full, resolveSize(height, heightSpec));
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        int width = r - l;
        boolean rtl = getLayoutDirection() == LAYOUT_DIRECTION_RTL;
        if (mSideBySide) {
            int separatorPx = mSeparator == null ? 0 : mSeparator.getMeasuredWidth();
            int firstAt = mGutterPx;
            place(mFirst, firstAt, 0, width, rtl);
            if (mSeparator != null) {
                int sepAt = firstAt + mFirst.getMeasuredWidth() + mGapPx;
                // Centred on the cells' last line: the 48dp controls a stepper stands on.
                int controls = RowKit.dp(getContext(), RowKit.TOUCH_DP);
                place(mSeparator, sepAt, Math.max(0, b - t - (controls
                    + mSeparator.getMeasuredHeight()) / 2), width, rtl);
            }
            int secondAt = firstAt + mFirst.getMeasuredWidth() + mGapPx
                + (separatorPx > 0 ? separatorPx + mGapPx : 0);
            place(mSecond, secondAt, 0, width, rtl);
            return;
        }
        int top = 0;
        boolean separate = stackedSeparator(standing(mFirst) && standing(mSecond));
        for (View cell : new View[] {mFirst, mSecond}) {
            if (!standing(cell))
                continue;
            place(cell, mGutterPx, top, width, rtl);
            top += cell.getMeasuredHeight();
            if (separate && cell == mFirst) {
                place(mSeparator, (width - mSeparator.getMeasuredWidth()) / 2, top, width, false);
                top += mSeparator.getMeasuredHeight();
            }
        }
        // Stacked, a plain separator (the Grid's x) has no place: it takes none.
        if (mSeparator != null && !separate)
            mSeparator.layout(0, 0, 0, 0);
    }

    /** Whether a separator stands between stacked cells: a control, with both cells standing. */
    private boolean stackedSeparator(boolean both) {
        return mSeparator != null && mSeparatorStacks && both
            && mSeparator.getVisibility() != GONE;
    }

    private static void place(@NonNull View child, int start, int top, int width, boolean rtl) {
        int w = child.getMeasuredWidth();
        int left = rtl ? width - start - w : start;
        child.layout(left, top, left + w, top + child.getMeasuredHeight());
    }

    @Override
    public boolean shouldDelayChildPressedState() {
        return false;
    }
}
