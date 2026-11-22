package com.termux.app;

import android.content.Context;
import android.graphics.Canvas;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;

/**
 * The terminal's frame line, in either of its two materials. As a plain border it is just a
 * background drawable set from {@code applyTerminalBorderAppearance}, exactly like the View it
 * replaced. When the terminal's glass pane is active it draws the shared {@link GlassRimRenderer}
 * edge instead — the same lit-glass rim the elevated surfaces use — because a 1dp outline stroke
 * over frosted glass reads as a drawn box on top of the material rather than the material's edge.
 */
public final class TerminalGlassFrameView extends View {

    private final GlassRimRenderer mRim;
    private boolean mRimEnabled;
    private float mRadiusPx;

    public TerminalGlassFrameView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        mRim = new GlassRimRenderer(context.getResources().getDisplayMetrics().density);
        setWillNotDraw(false);
    }

    /** Switch to the glass rim (background should be cleared by the caller) or back off it. */
    public void setRim(boolean enabled, float radiusPx) {
        if (mRimEnabled == enabled && mRadiusPx == radiusPx) return;
        mRimEnabled = enabled;
        mRadiusPx = radiusPx;
        invalidate();
    }

    private int mTopGapStartPx;
    private int mTopGapEndPx;
    private int mTopGapDepthPx;

    /**
     * Breaks the line's top over {@code [startPx, endPx)} (this view's own x), down
     * {@code depthPx}, where the camera stands over it; {@code endPx <= startPx} restores it.
     */
    public void setTopGap(int startPx, int endPx, int depthPx) {
        if (endPx <= startPx) {
            startPx = 0;
            endPx = 0;
            depthPx = 0;
        }
        if (startPx == mTopGapStartPx && endPx == mTopGapEndPx && depthPx == mTopGapDepthPx) return;
        mTopGapStartPx = startPx;
        mTopGapEndPx = endPx;
        mTopGapDepthPx = depthPx;
        invalidate();
    }

    /** The view draws nothing but its line, so the gap cuts the background and the rim alike. */
    @Override
    public void draw(Canvas canvas) {
        if (mTopGapEndPx <= mTopGapStartPx) {
            super.draw(canvas);
            return;
        }
        int save = canvas.save();
        try {
            canvas.clipOutRect(mTopGapStartPx, 0, mTopGapEndPx, mTopGapDepthPx);
            super.draw(canvas);
        } finally {
            canvas.restoreToCount(save);
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (mRimEnabled) {
            mRim.draw(canvas, 0f, 0f, getWidth(), getHeight(), mRadiusPx, -1f, 1f);
        }
    }
}
