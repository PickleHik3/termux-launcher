package com.termux.app.dock;

import android.graphics.Canvas;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.InsetDrawable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * The joined Docked frame's one sheet of glass with the apps row's band left out, where the row
 * wears its own Lens beads (2026-11-03, {@link LensBeadGeometry#rowWearsBeadsCuttingFrame}). It
 * wraps the frame's drawable unchanged and only clips the band out as it draws: with no band set
 * it draws the frame whole, so the frame without beads is pixel for pixel what it was.
 *
 * <p>The band is in the drawable's own coordinates and spans the frame's full width, so it is
 * square where it meets the screen's edges, where the frame's sheet ends anyway; its top and
 * bottom are the seams the frame's glass already draws at every edge.</p>
 */
public final class FrameBandCutDrawable extends InsetDrawable {

    @Nullable private RectF mCut;

    public FrameBandCutDrawable(@NonNull Drawable frame) {
        super(frame, 0);
    }

    /** The band to leave out, in this drawable's coordinates; null draws the frame whole. */
    public void setCut(@Nullable RectF cut) {
        RectF next = cut == null || cut.isEmpty() ? null : new RectF(cut);
        if (next == null ? mCut == null : next.equals(mCut)) return;
        mCut = next;
        invalidateSelf();
    }

    @Nullable
    public RectF cut() {
        return mCut == null ? null : new RectF(mCut);
    }

    @Override
    public void draw(@NonNull Canvas canvas) {
        if (mCut == null) {
            super.draw(canvas);
            return;
        }
        int saved = canvas.save();
        canvas.clipOutRect(mCut);
        super.draw(canvas);
        canvas.restoreToCount(saved);
    }
}
