package com.termux.app.chrome;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Two dresses of one surface, the one it leaves ({@code here}) and the one it goes to
 * ({@code there}), drawn one over the other by a share of 0 to 1: at 0 exactly {@code here}, at 1
 * exactly {@code there}, alpha only between. Where only one of them is given it is drawn whole,
 * and whoever holds the surface fades the surface itself. Moving the share redraws; it builds
 * nothing.
 */
public final class CrossfadeDrawable extends Drawable {

    @Nullable private final Drawable mHere;
    @Nullable private final Drawable mThere;
    private float mShare;
    private int mAlpha = 255;

    public CrossfadeDrawable(@Nullable Drawable here, @Nullable Drawable there, float share) {
        mHere = here;
        mThere = there;
        mShare = clamp01(share);
        if (here != null) here.setCallback(null);
        if (there != null) there.setCallback(null);
    }

    @Nullable public Drawable here() {
        return mHere;
    }

    @Nullable public Drawable there() {
        return mThere;
    }

    public float share() {
        return mShare;
    }

    /**
     * The dress the blend has come to rest on, for a holder ending it: {@code there} once the
     * share has gone past the middle, {@code here} before (a cancelled crossing, faded back).
     * At either end that is exactly what the blend draws.
     */
    @Nullable public Drawable endpoint() {
        return mShare >= 0.5f ? mThere : mHere;
    }

    /** The share moved; whether it did. */
    public boolean setShare(float share) {
        float s = clamp01(share);
        if (s == mShare) return false;
        mShare = s;
        invalidateSelf();
        return true;
    }

    /** The alpha {@code here} is drawn at, 0 to 255, before this drawable's own alpha. */
    public int hereAlpha() {
        if (mHere == null) return 0;
        return mThere == null ? 255 : Math.round(255f * (1f - mShare));
    }

    /** The alpha {@code there} is drawn at, 0 to 255, before this drawable's own alpha. */
    public int thereAlpha() {
        if (mThere == null) return 0;
        return mHere == null ? 255 : Math.round(255f * mShare);
    }

    @Override
    protected void onBoundsChange(@NonNull Rect bounds) {
        if (mHere != null) mHere.setBounds(bounds);
        if (mThere != null) mThere.setBounds(bounds);
    }

    @Override
    public void draw(@NonNull Canvas canvas) {
        drawLayer(canvas, mHere, hereAlpha());
        drawLayer(canvas, mThere, thereAlpha());
    }

    private void drawLayer(@NonNull Canvas canvas, @Nullable Drawable layer, int share) {
        int alpha = Math.round(share * (mAlpha / 255f));
        if (layer == null || alpha <= 0) return;
        if (alpha >= 255) {
            layer.draw(canvas);
            return;
        }
        Rect b = getBounds();
        int save = canvas.saveLayerAlpha(b.left, b.top, b.right, b.bottom, alpha);
        try {
            layer.draw(canvas);
        } finally {
            canvas.restoreToCount(save);
        }
    }

    @Override
    public void setAlpha(int alpha) {
        if (alpha == mAlpha) return;
        mAlpha = alpha;
        invalidateSelf();
    }

    @Override
    public int getAlpha() {
        return mAlpha;
    }

    @Override
    public void setColorFilter(@Nullable ColorFilter colorFilter) {
        if (mHere != null) mHere.setColorFilter(colorFilter);
        if (mThere != null) mThere.setColorFilter(colorFilter);
    }

    @Override
    @SuppressWarnings("deprecation")
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }

    private static float clamp01(float v) {
        return Float.isNaN(v) ? 0f : v < 0f ? 0f : v > 1f ? 1f : v;
    }
}
