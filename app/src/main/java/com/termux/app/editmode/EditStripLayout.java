package com.termux.app.editmode;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Edit mode's strip row (terl-integration-1): the cards' chips scroll in what the count, Undo,
 * Revert and Done leave, and the count never takes the chips' last {@link #chipsMinPx}: before
 * each measure the count is given at most the room left ({@link EditStripChips#countWidthPx}),
 * ellipsized, down to none. Only the count's width field is set here, inside the measure, so
 * nothing asks for another layout.
 */
final class EditStripLayout extends LinearLayout {

    @Nullable private View mCount;
    @Nullable private View mChips;
    private int mChipsMinPx;
    private int mCountCapPx;

    EditStripLayout(@NonNull Context context) {
        super(context);
        setOrientation(HORIZONTAL);
    }

    /** The count, the chips' scroller, the chips' least width and the count's widest. */
    void bind(@NonNull View count, @NonNull View chips, int chipsMinPx, int countCapPx) {
        mCount = count;
        mChips = chips;
        mChipsMinPx = chipsMinPx;
        mCountCapPx = countCapPx;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        View count = mCount;
        if (count != null && MeasureSpec.getMode(widthMeasureSpec) != MeasureSpec.UNSPECIFIED) {
            int inner = MeasureSpec.getSize(widthMeasureSpec) - getPaddingLeft() - getPaddingRight();
            int unspecified = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED);
            int buttons = 0;
            for (int i = 0; i < getChildCount(); i++) {
                View child = getChildAt(i);
                if (child == count || child == mChips || child.getVisibility() == GONE) continue;
                child.measure(unspecified, unspecified);
                buttons += child.getMeasuredWidth() + margins(child);
            }
            count.measure(unspecified, unspecified);
            int width = EditStripChips.countWidthPx(count.getMeasuredWidth(), inner, buttons, mChipsMinPx,
                mCountCapPx);
            ViewGroup.LayoutParams params = count.getLayoutParams();
            // The field alone: a setLayoutParams here would ask for another pass.
            if (params != null) params.width = width;
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }

    private static int margins(@NonNull View child) {
        ViewGroup.LayoutParams params = child.getLayoutParams();
        if (!(params instanceof MarginLayoutParams)) return 0;
        return ((MarginLayoutParams) params).leftMargin + ((MarginLayoutParams) params).rightMargin;
    }
}
