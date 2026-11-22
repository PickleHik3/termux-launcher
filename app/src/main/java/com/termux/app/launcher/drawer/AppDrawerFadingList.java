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
    private float mTopCap = 1f;
    private float mBottomCap = 1f;

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

    /**
     * The capsule's gap from the card's top and bottom, in px, negative for an edge it does not
     * stand at: the fade there stops where the capsule starts
     * ({@link AppDrawerFloatingList#fadeStrengthCap}). Set on a layout, compared first.
     */
    public void setCapsuleGaps(int topGapPx, int bottomGapPx) {
        int length = getVerticalFadingEdgeLength();
        float top = AppDrawerFloatingList.fadeStrengthCap(topGapPx, length);
        float bottom = AppDrawerFloatingList.fadeStrengthCap(bottomGapPx, length);
        if (top == mTopCap && bottom == mBottomCap) return;
        mTopCap = top;
        mBottomCap = bottom;
        invalidate();
    }

    @Override
    protected float getTopFadingEdgeStrength() {
        return mFadeTop ? Math.min(mTopCap, super.getTopFadingEdgeStrength()) : 0f;
    }

    @Override
    protected float getBottomFadingEdgeStrength() {
        return mFadeBottom ? Math.min(mBottomCap, super.getBottomFadingEdgeStrength()) : 0f;
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
