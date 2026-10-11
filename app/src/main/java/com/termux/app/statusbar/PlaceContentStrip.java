package com.termux.app.statusbar;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.LinearLayout;

/**
 * The part of the status row that belongs to the place on screen — the badge and the chips. It
 * slides with the pane wall and dissolves at the bar's edges, so a place's row leaves through the
 * lens and the next one arrives through it, while the clock and the stat widgets hold still.
 */
public final class PlaceContentStrip extends LinearLayout {

    /**
     * How far the row's content starts from the bar's edge: just past the visible half of the
     * icon peeking there. Portrait width is scarce, so the two sit close.
     */
    public static final float LENS_WIDTH_DP = 10f;

    /** What a column keeps clear under its chips, before the readings below them. */
    public static final float COLUMN_FOOT_DP = 8f;

    public PlaceContentStrip(Context context, AttributeSet attrs) {
        super(context, attrs);
        setOrientation(HORIZONTAL);
        setClipChildren(true);
        setClipToPadding(false);
        setFadingEdgeLength(Math.round(LENS_WIDTH_DP * getResources().getDisplayMetrics().density));
        setHorizontalFadingEdgeEnabled(true);
    }

    /**
     * Whether the strip stacks its content down a column rather than along a row. The dissolve
     * follows: the strip always fades at the two ends the places arrive and leave through.
     */
    public void setVertical(boolean vertical) {
        setOrientation(vertical ? VERTICAL : HORIZONTAL);
        setHorizontalFadingEdgeEnabled(!vertical);
        setVerticalFadingEdgeEnabled(vertical);
    }

    /**
     * The end facing the readings feathers only while the place slides, growing over the first
     * fade-length of travel. At rest that edge belongs to the window chips, which feather there
     * themselves exactly when they run past it and not otherwise; a second, constant fade on top
     * dimmed the last chip of a row that fit.
     */
    @Override protected float getLeftFadingEdgeStrength() {
        return isRtl() ? travelStrength() : 1f;
    }

    @Override protected float getRightFadingEdgeStrength() {
        return isRtl() ? 1f : travelStrength();
    }

    @Override public void setTranslationX(float translationX) {
        float before = travelStrength();
        super.setTranslationX(translationX);
        // The fade is drawn with the strip's own content, which a translation alone does not
        // redraw; only the first fade-length of travel changes it.
        if (travelStrength() != before) invalidate();
    }

    private float travelStrength() {
        int length = getHorizontalFadingEdgeLength();
        if (length <= 0) return 1f;
        return Math.min(1f, Math.abs(getTranslationX()) / length);
    }

    private boolean isRtl() {
        return getLayoutDirection() == LAYOUT_DIRECTION_RTL;
    }

    @Override protected float getTopFadingEdgeStrength() { return 1f; }

    @Override protected float getBottomFadingEdgeStrength() { return 1f; }
}
