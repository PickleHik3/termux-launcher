package com.termux.app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Path;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.dock.LensBeadGeometry;

/**
 * The dock's glass host ({@code accessory_surface_host}): the frost, its tint and the dock's
 * glass light stand in it. Drawn as Lens beads ({@link #setBeads}), everything in it is clipped
 * to the beads, one round lens under each icon, so the frost, the tint, a launch's wave and a
 * press's light are each bead's on every Android version: below Android 13 the beads are plain
 * frost, from 13 the glass program draws each bead's rim, shelf and reflection as well. The
 * slab's own furniture ({@link #setSlabOnlyChildren}: its top seam, its rim glow) is not drawn
 * while beads are. With no beads it is a plain {@link FrameLayout} and draws exactly as before.
 *
 * <p>The clip is one path, built when the beads move, so beads cost nothing per frame at rest.
 * While a page swipe carries the beads with their icons it is rebuilt on each frame of the swipe,
 * and also held to the span the row draws its icons in ({@link #setBeadSpan}), so a bead leaves
 * and arrives exactly where its icon does.</p>
 */
public final class DockSurfaceHostLayout extends FrameLayout {

    @Nullable private LensBeadGeometry.Beads mBeads;
    private final Path mBeadPath = new Path();
    @NonNull private View[] mSlabOnly = new View[0];
    /** The span the beads are cut to sideways, in this view's coordinates; NaN for none. */
    private float mSpanLeft = Float.NaN, mSpanRight = Float.NaN;
    /**
     * The slab's corner radius while beads melt into it across the seam (they are cut to the
     * slab's outline as they swell over it); NaN at rest, when the beads stand inside it anyway.
     */
    private float mSlabClipRadius = Float.NaN;
    private final Path mSlabClipPath = new Path();
    private final RectF mSlabClipRect = new RectF();

    public DockSurfaceHostLayout(@NonNull Context context) {
        super(context);
    }

    public DockSurfaceHostLayout(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    public DockSurfaceHostLayout(@NonNull Context context, @Nullable AttributeSet attrs,
                                 int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    /** The children that belong to the slab alone, not drawn while the host is beads. */
    public void setSlabOnlyChildren(@NonNull View... children) {
        mSlabOnly = children.clone();
    }

    /** The beads in this view's coordinates, or null for the slab. Redrawn only when they moved. */
    public void setBeads(@Nullable LensBeadGeometry.Beads beads) {
        // The slab keeps no sideways cut: the next beads start uncut, as a swipe's frames set it.
        if (beads == null) {
            mSpanLeft = Float.NaN;
            mSpanRight = Float.NaN;
            mSlabClipRadius = Float.NaN;
        }
        if (beads == null ? mBeads == null : beads.equals(mBeads)) return;
        mBeads = beads;
        mBeadPath.reset();
        if (beads != null) {
            for (int i = 0; i < beads.count(); i++) {
                mBeadPath.addCircle(beads.centerX(i), beads.centerY, beads.radiusPx,
                    Path.Direction.CW);
            }
        }
        invalidate();
    }

    /**
     * Cuts the beads to {@code left..right} sideways (the span the row clips its icons to while
     * a page swipe carries them), or lets go with NaN. Redrawn only on a change.
     */
    public void setBeadSpan(float left, float right) {
        if (sameFloat(mSpanLeft, left) && sameFloat(mSpanRight, right)) return;
        mSpanLeft = left;
        mSpanRight = right;
        if (mBeads != null) invalidate();
    }

    /**
     * Cuts the beads to the slab's outline, a rounded rectangle of this view's bounds with
     * {@code radiusPx} corners, while they swell over it; NaN lets go. Redrawn only on a change.
     */
    public void setSlabClip(float radiusPx) {
        if (sameFloat(mSlabClipRadius, radiusPx)) return;
        mSlabClipRadius = radiusPx;
        if (mBeads != null) invalidate();
    }

    /** The slab clip's corner radius, NaN while there is none. */
    public float slabClipRadius() {
        return mSlabClipRadius;
    }

    private static boolean sameFloat(float a, float b) {
        return Float.compare(a, b) == 0;
    }

    /** The beads drawn now, or null for the slab. */
    @Nullable
    public LensBeadGeometry.Beads beads() {
        return mBeads;
    }

    @Override
    protected void dispatchDraw(@NonNull Canvas canvas) {
        if (mBeads == null) {
            super.dispatchDraw(canvas);
            return;
        }
        int save = canvas.save();
        if (!Float.isNaN(mSpanLeft) && !Float.isNaN(mSpanRight) && mSpanRight > mSpanLeft)
            canvas.clipRect(mSpanLeft, 0f, mSpanRight, getHeight());
        if (!Float.isNaN(mSlabClipRadius)) {
            mSlabClipRect.set(0f, 0f, getWidth(), getHeight());
            mSlabClipPath.reset();
            mSlabClipPath.addRoundRect(mSlabClipRect, mSlabClipRadius, mSlabClipRadius,
                Path.Direction.CW);
            canvas.clipPath(mSlabClipPath);
        }
        canvas.clipPath(mBeadPath);
        super.dispatchDraw(canvas);
        canvas.restoreToCount(save);
    }

    @Override
    protected boolean drawChild(@NonNull Canvas canvas, @NonNull View child, long drawingTime) {
        if (mBeads != null) {
            for (View slabOnly : mSlabOnly) {
                if (slabOnly == child) return false;
            }
        }
        return super.drawChild(canvas, child, drawingTime);
    }
}
