package com.termux.app.launcher.drawer;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

/**
 * A drawer list whose rows fade out over {@link AppDrawerFloatingList#FADE_DP} at the view's own
 * top and bottom (the card's edges), not at its padding's: the platform's vertical fading edge,
 * moved out over the padding. Which edges fade is set once; the fade shows only while the list can
 * scroll that way, as every fading edge does. No allocation and no layout per frame.
 */
public class AppDrawerFadingList extends RecyclerView {

    private boolean mFadeTop = true;
    private boolean mFadeBottom = true;

    public AppDrawerFadingList(@NonNull Context context) {
        super(context);
        setVerticalFadingEdgeEnabled(true);
        setFadingEdgeLength(AppDrawerFloatingList.fadeLengthPx(
            context.getResources().getDisplayMetrics().density));
    }

    /** Which edges dissolve rows; an edge that is not the card's (under a header) stays hard. */
    public void setFadeEdges(boolean top, boolean bottom) {
        mFadeTop = top;
        mFadeBottom = bottom;
    }

    @Override
    protected float getTopFadingEdgeStrength() {
        return mFadeTop ? super.getTopFadingEdgeStrength() : 0f;
    }

    @Override
    protected float getBottomFadingEdgeStrength() {
        return mFadeBottom ? super.getBottomFadingEdgeStrength() : 0f;
    }

    @Override
    protected boolean isPaddingOffsetRequired() {
        return true;
    }

    @Override
    protected int getTopPaddingOffset() {
        return AppDrawerFloatingList.topFadeOffsetPx(getPaddingTop());
    }

    @Override
    protected int getBottomPaddingOffset() {
        return AppDrawerFloatingList.bottomFadeOffsetPx(getPaddingBottom());
    }
}
