package com.termux.app.statusbar;

import android.graphics.Outline;
import android.view.View;
import android.view.ViewOutlineProvider;

import androidx.annotation.NonNull;

/** Mutable, allocation-free outline shared by normal and transient FULL status-pane geometry. */
public final class StatusBarSurfaceOutlineProvider extends ViewOutlineProvider {
    private float normalRadiusPx;
    private float fullRadiusPx;
    private float fullProgress;
    private float radiusPx;

    /**
     * Keeps normal surface styling at progress zero and reaches the pane's existing rounded-style
     * radius at FULL. The same spring progress that owns the real height owns this interpolation,
     * so neither end of the transition has a shape-state pop.
     */
    public void setFrame(float normalRadiusPx, float fullRadiusPx, float fullProgress) {
        this.normalRadiusPx = finiteNonNegative(normalRadiusPx);
        this.fullRadiusPx = finiteNonNegative(fullRadiusPx);
        this.fullProgress = finiteUnit(fullProgress);
        radiusPx = this.normalRadiusPx
            + (this.fullRadiusPx - this.normalRadiusPx) * this.fullProgress;
    }

    @Override
    public void getOutline(View view, @NonNull Outline outline) {
        outline.setRoundRect(0, 0, Math.max(0, view.getWidth()),
            Math.max(0, view.getHeight()), radiusPx);
    }

    public boolean clipsCorners() { return radiusPx > 0f; }
    public float radiusPx() { return radiusPx; }
    public float fullRadiusPx() { return fullRadiusPx; }
    public float fullProgress() { return fullProgress; }

    private static float finiteNonNegative(float value) {
        return Float.isFinite(value) ? Math.max(0f, value) : 0f;
    }

    private static float finiteUnit(float value) {
        return Float.isFinite(value) ? Math.max(0f, Math.min(1f, value)) : 0f;
    }
}
