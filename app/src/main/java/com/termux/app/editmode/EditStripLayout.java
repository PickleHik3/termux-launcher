package com.termux.app.editmode;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Edit mode's strip row: the cards' chips, the change count, then Undo, Revert and Done, stood as
 * {@link EditStripChips#flow} says: every chip whole on as many lines as it takes, the buttons at
 * the end of the last line (or on one of their own), the count in what that line leaves. Children
 * are the chips in order, then the count and the buttons named in {@link #bind}.
 */
final class EditStripLayout extends ViewGroup {

    @Nullable private View mCount;
    @NonNull private final List<View> mButtons = new ArrayList<>();
    private int mCountCapPx;
    @NonNull private final List<View> mChips = new ArrayList<>();
    @Nullable private EditStripChips.Flow mFlow;
    @NonNull private int[] mLineTop = new int[0];
    @NonNull private int[] mLineHeight = new int[0];

    EditStripLayout(@NonNull Context context) {
        super(context);
    }

    /** The count, Undo/Revert/Done in order, and the count's widest; every other child is a chip. */
    void bind(@NonNull View count, @NonNull List<View> buttons, int countCapPx) {
        mCount = count;
        mButtons.clear();
        mButtons.addAll(buttons);
        mCountCapPx = countCapPx;
    }

    private boolean isChip(@NonNull View child) {
        return child != mCount && !mButtons.contains(child);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getMode(widthMeasureSpec) == MeasureSpec.UNSPECIFIED
            ? Integer.MAX_VALUE / 4 : MeasureSpec.getSize(widthMeasureSpec);
        int inner = Math.max(0, width - getPaddingLeft() - getPaddingRight());
        int loose = MeasureSpec.makeMeasureSpec(inner, MeasureSpec.AT_MOST);
        int free = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED);
        mChips.clear();
        List<Integer> chipWidths = new ArrayList<>();
        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            if (child.getVisibility() == GONE || !isChip(child)) continue;
            child.measure(loose, free);
            mChips.add(child);
            chipWidths.add(child.getMeasuredWidth());
        }
        int buttons = 0;
        int shownButtons = 0;
        for (View button : mButtons) {
            if (button.getVisibility() == GONE) continue;
            button.measure(free, free);
            buttons += button.getMeasuredWidth();
            shownButtons++;
        }
        // Undo, Revert and Done wider than the row (large text): each takes an equal share, its
        // label ellipsized, rather than one standing over another (round 4).
        int share = EditStripChips.buttonShare(inner, buttons, shownButtons);
        if (share > 0) {
            buttons = 0;
            for (View button : mButtons) {
                if (button.getVisibility() == GONE) continue;
                button.measure(MeasureSpec.makeMeasureSpec(share, MeasureSpec.EXACTLY), free);
                buttons += share;
            }
        }
        int countWanted = 0;
        View count = mCount;
        if (count != null && count.getVisibility() != GONE) {
            count.measure(free, free);
            countWanted = count.getMeasuredWidth();
        }
        int[] chips = new int[chipWidths.size()];
        for (int i = 0; i < chips.length; i++) chips[i] = chipWidths.get(i);
        EditStripChips.Flow flow = EditStripChips.flow(inner, chips, buttons, countWanted, mCountCapPx);
        mFlow = flow;
        // Each chip at the width the flow gives it (only a chip wider than the row is narrowed).
        for (int i = 0; i < mChips.size(); i++) {
            View chip = mChips.get(i);
            if (chip.getMeasuredWidth() != flow.chipWidth[i]) {
                chip.measure(MeasureSpec.makeMeasureSpec(flow.chipWidth[i], MeasureSpec.EXACTLY), free);
            }
        }
        if (count != null && count.getVisibility() != GONE) {
            count.measure(MeasureSpec.makeMeasureSpec(flow.countWidth, MeasureSpec.EXACTLY), free);
        }
        int lines = flow.lines();
        mLineHeight = new int[lines];
        mLineTop = new int[lines];
        for (int i = 0; i < mChips.size(); i++) {
            int line = flow.chipLine[i];
            mLineHeight[line] = Math.max(mLineHeight[line], mChips.get(i).getMeasuredHeight());
        }
        int last = flow.actionsLine;
        if (count != null && count.getVisibility() != GONE) {
            mLineHeight[last] = Math.max(mLineHeight[last], count.getMeasuredHeight());
        }
        for (View button : mButtons) {
            if (button.getVisibility() == GONE) continue;
            mLineHeight[last] = Math.max(mLineHeight[last], button.getMeasuredHeight());
        }
        int height = getPaddingTop();
        for (int line = 0; line < lines; line++) {
            mLineTop[line] = height;
            height += mLineHeight[line];
        }
        height += getPaddingBottom();
        setMeasuredDimension(resolveSize(width == Integer.MAX_VALUE / 4 ? getPaddingLeft()
                + getPaddingRight() + Math.max(flow.countX + flow.countWidth + buttons, 0) : width,
                widthMeasureSpec),
            resolveSize(Math.max(height, getSuggestedMinimumHeight()), heightMeasureSpec));
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        EditStripChips.Flow flow = mFlow;
        if (flow == null) return;
        int start = getPaddingLeft();
        int end = r - l - getPaddingRight();
        boolean rtl = getLayoutDirection() == LAYOUT_DIRECTION_RTL;
        for (int i = 0; i < mChips.size() && i < flow.chipX.length; i++) {
            place(mChips.get(i), start + flow.chipX[i], flow.chipLine[i], rtl);
        }
        int last = flow.actionsLine;
        View count = mCount;
        if (count != null && count.getVisibility() != GONE) {
            place(count, start + flow.countX, last, rtl);
        }
        // The buttons stand at the line's end, Done last.
        int x = end;
        for (int i = mButtons.size() - 1; i >= 0; i--) {
            View button = mButtons.get(i);
            if (button.getVisibility() == GONE) continue;
            x -= button.getMeasuredWidth();
            place(button, x, last, rtl);
        }
    }

    /** Lays {@code child} out at {@code x}, centred on {@code line}; mirrored right to left. */
    private void place(@NonNull View child, int x, int line, boolean rtl) {
        int w = child.getMeasuredWidth();
        int h = child.getMeasuredHeight();
        int left = rtl ? getWidth() - x - w : x;
        int top = mLineTop[line] + (mLineHeight[line] - h) / 2;
        child.layout(left, top, left + w, top + h);
    }
}
