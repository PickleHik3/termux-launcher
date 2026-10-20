package com.termux.app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Path;
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
 * <p>The clip is one path, built when the beads move, so beads cost nothing per frame.</p>
 */
public final class DockSurfaceHostLayout extends FrameLayout {

    @Nullable private LensBeadGeometry.Beads mBeads;
    private final Path mBeadPath = new Path();
    @NonNull private View[] mSlabOnly = new View[0];

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
