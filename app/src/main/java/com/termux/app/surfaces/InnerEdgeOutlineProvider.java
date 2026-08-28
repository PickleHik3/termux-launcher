package com.termux.app.surfaces;

import android.graphics.Outline;
import android.view.View;
import android.view.ViewOutlineProvider;

import androidx.annotation.NonNull;

/**
 * Rounds only the edge of a surface that faces the terminal.
 *
 * <p>A Docked surface is flush with the screen on three sides; the only corner the user can see is
 * on the inner edge — the top of the bottom stack, the bottom of the top stack. Android outlines
 * carry a single radius for all four corners, so the two corners that should stay square are pushed
 * outside the view instead: their rounding happens off the surface and never reaches the screen.
 * The outline therefore stays a plain convex round-rect, which is what keeps
 * {@code setClipToOutline} and the elevation shadow working — a genuinely per-corner path would
 * lose both.
 */
public final class InnerEdgeOutlineProvider extends ViewOutlineProvider {

    /** Which edge of the surface faces the terminal and therefore carries the corners. */
    public enum Edge { TOP, BOTTOM }

    private final Edge edge;
    private float radiusPx;

    public InnerEdgeOutlineProvider(@NonNull Edge edge) {
        this.edge = edge;
    }

    /** @return true when the radius actually changed, so the caller can skip a needless invalidate. */
    public boolean setRadiusPx(float radiusPx) {
        float next = Float.isFinite(radiusPx) ? Math.max(0f, radiusPx) : 0f;
        if (Float.compare(next, this.radiusPx) == 0)
            return false;
        this.radiusPx = next;
        return true;
    }

    public float radiusPx() {
        return radiusPx;
    }

    public boolean roundsCorners() {
        return radiusPx > 0f;
    }

    @Override
    public void getOutline(View view, @NonNull Outline outline) {
        int width = Math.max(0, view.getWidth());
        int height = Math.max(0, view.getHeight());
        if (radiusPx <= 0f) {
            outline.setRect(0, 0, width, height);
            return;
        }
        // Overshoot the outer edge by the radius so only the inner two corners land on screen.
        int top = edge == Edge.BOTTOM ? -Math.round(radiusPx) : 0;
        int bottom = edge == Edge.TOP ? height + Math.round(radiusPx) : height;
        outline.setRoundRect(0, top, width, bottom, radiusPx);
    }
}
