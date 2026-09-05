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

    public PlaceContentStrip(Context context, AttributeSet attrs) {
        super(context, attrs);
        setOrientation(HORIZONTAL);
        setClipChildren(true);
        setClipToPadding(false);
        setHorizontalFadingEdgeEnabled(true);
        setFadingEdgeLength(Math.round(LENS_WIDTH_DP * getResources().getDisplayMetrics().density));
    }

    @Override protected float getLeftFadingEdgeStrength() { return 1f; }

    @Override protected float getRightFadingEdgeStrength() { return 1f; }
}
