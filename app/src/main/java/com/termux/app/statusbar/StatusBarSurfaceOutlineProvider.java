package com.termux.app.statusbar;

import android.graphics.Outline;
import android.view.View;
import android.view.ViewOutlineProvider;

import androidx.annotation.NonNull;

/** Mutable, allocation-free outline for the status pane's geometry. */
public final class StatusBarSurfaceOutlineProvider extends ViewOutlineProvider {
    private float radiusPx;
    private boolean innerEdgeOnly;

    /**
     * Docked keeps the pane flush with the screen on three sides, so only its bottom edge - the one
     * facing the terminal - carries corners. See
     * {@link com.termux.app.surfaces.InnerEdgeOutlineProvider} for why the top two are pushed
     * outside the view rather than described with a per-corner path.
     */
    public void setInnerEdgeOnly(boolean innerEdgeOnly) {
        this.innerEdgeOnly = innerEdgeOnly;
    }

    /** The radius the pane's every layer — live blur and wallpaper frost included — clips to. */
    public void setFrame(float radiusPx) {
        this.radiusPx = finiteNonNegative(radiusPx);
    }

    @Override
    public void getOutline(View view, @NonNull Outline outline) {
        int top = innerEdgeOnly && radiusPx > 0f ? -Math.round(radiusPx) : 0;
        outline.setRoundRect(0, top, Math.max(0, view.getWidth()),
            Math.max(0, view.getHeight()), radiusPx);
    }

    public boolean clipsCorners() { return radiusPx > 0f; }
    public float radiusPx() { return radiusPx; }

    private static float finiteNonNegative(float value) {
        return Float.isFinite(value) ? Math.max(0f, value) : 0f;
    }
}
