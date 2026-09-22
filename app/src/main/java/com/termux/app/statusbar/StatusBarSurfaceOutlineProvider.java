package com.termux.app.statusbar;

import android.graphics.Outline;
import android.view.View;
import android.view.ViewOutlineProvider;

import androidx.annotation.NonNull;

import com.termux.app.place.PlaceLayout.Edge;

/** Mutable, allocation-free outline for the status pane's geometry. */
public final class StatusBarSurfaceOutlineProvider extends ViewOutlineProvider {
    private float radiusPx;
    private boolean innerEdgeOnly;
    @NonNull private Edge edge = Edge.TOP;

    /**
     * Docked keeps the pane flush with the screen on three sides, so only the edge facing the
     * terminal carries corners. See {@link com.termux.app.surfaces.InnerEdgeOutlineProvider} for
     * why the other two are pushed outside the view rather than described with a per-corner path.
     */
    public void setInnerEdgeOnly(boolean innerEdgeOnly) {
        this.innerEdgeOnly = innerEdgeOnly;
    }

    /** The screen edge the bar stands on; the corners land on the opposite side of the view. */
    public void setEdge(@NonNull Edge edge) {
        this.edge = edge;
    }

    /** The radius the pane's every layer — live blur and wallpaper frost included — clips to. */
    public void setFrame(float radiusPx) {
        this.radiusPx = finiteNonNegative(radiusPx);
    }

    @Override
    public void getOutline(View view, @NonNull Outline outline) {
        int width = Math.max(0, view.getWidth());
        int height = Math.max(0, view.getHeight());
        // Overshoot the outer edge — the one the bar stands on — by the radius, so only the two
        // corners facing the terminal land on screen.
        int overshoot = innerEdgeOnly && radiusPx > 0f ? Math.round(radiusPx) : 0;
        int left = edge == Edge.LEFT ? -overshoot : 0;
        int top = edge == Edge.TOP ? -overshoot : 0;
        int right = edge == Edge.RIGHT ? width + overshoot : width;
        int bottom = edge == Edge.BOTTOM ? height + overshoot : height;
        outline.setRoundRect(left, top, right, bottom, radiusPx);
    }

    public boolean clipsCorners() { return radiusPx > 0f; }
    public float radiusPx() { return radiusPx; }

    private static float finiteNonNegative(float value) {
        return Float.isFinite(value) ? Math.max(0f, value) : 0f;
    }
}
