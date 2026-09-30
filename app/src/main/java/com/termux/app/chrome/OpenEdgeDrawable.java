package com.termux.app.chrome;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * A rim with some of its sides left open: the wrapped stroke is laid out past the drawable's
 * bounds on every open edge, by enough to carry its line and its corner arcs off the surface, and
 * clipped back to the bounds. What is left on screen is the stroke along the closed edges only,
 * running straight out through the open ones. {@link ChromeEdgeRule#strokeEdges} says which.
 */
final class OpenEdgeDrawable extends Drawable {

    @NonNull private final Drawable mRim;
    private final int mOpenEdges;
    private final int mReachPx;
    private final Rect mRimBounds = new Rect();

    /**
     * @param openEdges {@link ChromeEdgeRule} edges with no stroke
     * @param reachPx   how far an open edge is pushed out: the stroke and the corner radius at least
     */
    OpenEdgeDrawable(@NonNull Drawable rim, int openEdges, int reachPx) {
        mRim = rim;
        mOpenEdges = openEdges;
        mReachPx = Math.max(1, reachPx);
    }

    /** The edges left open, for tests. */
    int openEdges() {
        return mOpenEdges;
    }

    @NonNull
    Drawable rim() {
        return mRim;
    }

    @Override
    protected void onBoundsChange(@NonNull Rect bounds) {
        mRimBounds.set(
            bounds.left - ((mOpenEdges & ChromeEdgeRule.LEFT) != 0 ? mReachPx : 0),
            bounds.top - ((mOpenEdges & ChromeEdgeRule.TOP) != 0 ? mReachPx : 0),
            bounds.right + ((mOpenEdges & ChromeEdgeRule.RIGHT) != 0 ? mReachPx : 0),
            bounds.bottom + ((mOpenEdges & ChromeEdgeRule.BOTTOM) != 0 ? mReachPx : 0));
        mRim.setBounds(mRimBounds);
    }

    @Override
    public void draw(@NonNull Canvas canvas) {
        Rect bounds = getBounds();
        if (bounds.isEmpty()) return;
        int save = canvas.save();
        canvas.clipRect(bounds);
        mRim.draw(canvas);
        canvas.restoreToCount(save);
    }

    @Override
    public void setAlpha(int alpha) {
        mRim.setAlpha(alpha);
        invalidateSelf();
    }

    @Override
    public int getAlpha() {
        return mRim.getAlpha();
    }

    @Override
    public void setColorFilter(@Nullable ColorFilter colorFilter) {
        mRim.setColorFilter(colorFilter);
        invalidateSelf();
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }
}
