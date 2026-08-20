package com.termux.app;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * {@link GlassRimRenderer} as a {@link Drawable}, so a surface that already expresses its frame
 * through a foreground drawable can wear the lit-glass edge instead of a flat stroke.
 *
 * <p>Exists for the terminal's panes: each pane states focus by swapping its foreground between a
 * full-strength and a dimmed border, and that mechanism only takes drawables. Alpha is honoured, so
 * the unfocused panes' rims dim exactly the way their strokes used to.
 */
public final class GlassRimDrawable extends Drawable {

    private final GlassRimRenderer mRim;
    private final float mRadiusPx;
    private int mAlpha = 255;

    public GlassRimDrawable(float density, float radiusPx) {
        this(density, radiusPx, 0);
    }

    /** @param tint Material colour the rim reads as, or 0 for the plain white glass edge. */
    public GlassRimDrawable(float density, float radiusPx, int tint) {
        mRim = new GlassRimRenderer(density);
        mRim.setTint(tint);
        // Pane rims are focus indicators, so they draw at one even strength all the way round;
        // the lit-top gradient left their bottom half carried by the faint base stroke alone.
        mRim.setUniformLight(true);
        mRadiusPx = radiusPx;
    }

    public float radiusPx() {
        return mRadiusPx;
    }

    @Override
    public void draw(@NonNull Canvas canvas) {
        Rect bounds = getBounds();
        if (bounds.isEmpty()) return;
        mRim.draw(canvas, bounds.left, bounds.top, bounds.right, bounds.bottom, mRadiusPx,
            -1f, mAlpha / 255f);
    }

    @Override
    public void setAlpha(int alpha) {
        int clamped = Math.max(0, Math.min(255, alpha));
        if (mAlpha == clamped) return;
        mAlpha = clamped;
        invalidateSelf();
    }

    @Override
    public int getAlpha() {
        return mAlpha;
    }

    /** The rim is a highlight, not a tint target; a colour filter would only muddy it. */
    @Override
    public void setColorFilter(@Nullable ColorFilter colorFilter) { }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }
}
