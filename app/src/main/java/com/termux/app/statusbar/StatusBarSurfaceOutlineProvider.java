package com.termux.app.statusbar;

import android.graphics.Outline;
import android.view.View;
import android.view.ViewOutlineProvider;

import androidx.annotation.NonNull;

import com.termux.app.place.PlaceLayout.Edge;

/**
 * Mutable, allocation-free outline for a chrome surface standing on one screen edge: the status
 * pane, the sheets off the dock, and the docked dock itself.
 *
 * <p>A Docked surface is flush with the screen on three sides; the only corners the user can see
 * are on the inner edge — the top of the bottom stack, the bottom of the top stack. Android
 * outlines carry a single radius for all four corners, so with {@link #setInnerEdgeOnly} the two
 * corners that should stay square are pushed outside the view instead: their rounding happens off
 * the surface and never reaches the screen. The outline therefore stays a plain convex
 * round-rect, which is what keeps {@code setClipToOutline} and the elevation shadow working — a
 * genuinely per-corner path would lose both.</p>
 */
public final class StatusBarSurfaceOutlineProvider extends ViewOutlineProvider {
    private float radiusPx;
    private boolean innerEdgeOnly;
    @NonNull private Edge edge = Edge.TOP;

    /** Only the edge facing the terminal carries corners; see the class comment. */
    public void setInnerEdgeOnly(boolean innerEdgeOnly) {
        this.innerEdgeOnly = innerEdgeOnly;
    }

    /** The screen edge the surface stands on; the corners land on the opposite side of the view. */
    public void setEdge(@NonNull Edge edge) {
        this.edge = edge;
    }

    /**
     * The radius the surface's every layer — live blur and wallpaper frost included — clips to.
     *
     * @return true when the radius actually changed, so the caller can skip a needless invalidate
     */
    public boolean setFrame(float radiusPx) {
        float next = finiteNonNegative(radiusPx);
        if (Float.compare(next, this.radiusPx) == 0) return false;
        this.radiusPx = next;
        return true;
    }

    @Override
    public void getOutline(View view, @NonNull Outline outline) {
        int width = Math.max(0, view.getWidth());
        int height = Math.max(0, view.getHeight());
        // Overshoot the outer edge — the one the surface stands on — by the radius, so only the
        // two corners facing the terminal land on screen.
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
