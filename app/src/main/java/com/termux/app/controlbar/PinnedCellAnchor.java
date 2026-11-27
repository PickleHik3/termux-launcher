package com.termux.app.controlbar;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Rect;
import android.view.View;

import androidx.annotation.NonNull;

/**
 * Stands for one pinned app's cell on the bar, so a card opened by holding the app points at that
 * icon and not at the whole bar. It is never attached: it has the cell's size and reports the
 * cell's place on the screen through the bar it stands on.
 */
@SuppressLint("ViewConstructor")
final class PinnedCellAnchor extends View {
    @NonNull private final View mBar;
    @NonNull private final Rect mCell;

    PinnedCellAnchor(@NonNull Context context, @NonNull View bar, @NonNull Rect cellInBar) {
        super(context);
        mBar = bar;
        mCell = new Rect(cellInBar);
        layout(0, 0, mCell.width(), mCell.height());
    }

    @Override public void getLocationOnScreen(@NonNull int[] outLocation) {
        mBar.getLocationOnScreen(outLocation);
        outLocation[0] += mCell.left;
        outLocation[1] += mCell.top;
    }

    @Override public boolean isAttachedToWindow() {
        return mBar.isAttachedToWindow();
    }
}
