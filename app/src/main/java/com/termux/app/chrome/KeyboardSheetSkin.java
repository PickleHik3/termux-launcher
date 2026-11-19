package com.termux.app.chrome;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * What the in-app keyboard's host wears over its own look while the app drawer is open
 * ({@link KeyboardSheetLook}): the sheet's glass, laid over the host's own background at the
 * drawer's fraction, and the top corners the drawer's radius gives it. One instance per host.
 * Allocation free once the glass is set; the only allocation per change is the outline's clip,
 * and only when the radius moves a whole pixel.
 */
public final class KeyboardSheetSkin {

    private float mFraction;
    @Nullable private Drawable mSheet;
    @NonNull private final Layers mLayers = new Layers();

    /** Whether the host is to wear the sheet at all: a fraction above 0 and a glass to wear. */
    public boolean engaged() {
        return mFraction > 0f && mSheet != null;
    }

    public float fraction() {
        return mFraction;
    }

    /** Sets the glass the sheet is drawn with; null lets go of it (and its backdrop). */
    public void setSheet(@Nullable Drawable sheet, int extendBelowPx) {
        mSheet = sheet;
        mLayers.setSheet(sheet, extendBelowPx);
    }

    @Nullable
    public Drawable sheet() {
        return mSheet;
    }

    /** @return whether the fraction moved */
    public boolean setFraction(float fraction) {
        if (fraction == mFraction) return false;
        mFraction = fraction;
        mLayers.setFraction(fraction);
        return true;
    }

    /**
     * What the host's background is, given its own: {@code inner} when the sheet is not engaged,
     * else one drawable that draws {@code inner} and the sheet over it. The same instance each
     * time, so a frame of the drawer's motion changes only its fraction.
     */
    @Nullable
    public Drawable dress(@Nullable Drawable inner) {
        if (!engaged()) return inner;
        mLayers.setInner(inner);
        return mLayers;
    }

    /**
     * {@code clip} with its top corners at {@code topPx} (whole pixels): the same clip when they
     * are already there. Null for no clip and no radius to give.
     */
    @Nullable
    public static LiveChromeShape.Clip withTopRadius(@Nullable LiveChromeShape.Clip clip,
                                                     float topPx) {
        float t = Math.max(0f, Math.round(topPx));
        if (clip == null) {
            return t <= 0f ? null : new LiveChromeShape.Clip(0, 0, 0, 0, t, t, 0f, 0f, true);
        }
        if (clip.topLeft == t && clip.topRight == t) return clip;
        return new LiveChromeShape.Clip(clip.reachLeft, clip.reachTop, clip.reachRight,
            clip.reachBottom, t, t, clip.bottomRight, clip.bottomLeft, true);
    }

    /** The host's own background under the sheet, which fades in over it. */
    private static final class Layers extends Drawable {
        @Nullable private Drawable mInner;
        @Nullable private Drawable mSheet;
        private int mExtendBelowPx;
        private float mFraction;

        void setInner(@Nullable Drawable inner) {
            if (mInner == inner) return;
            mInner = inner;
            if (inner != null) inner.setBounds(getBounds());
            invalidateSelf();
        }

        void setSheet(@Nullable Drawable sheet, int extendBelowPx) {
            mSheet = sheet;
            mExtendBelowPx = Math.max(0, extendBelowPx);
            placeSheet(getBounds());
            invalidateSelf();
        }

        void setFraction(float fraction) {
            mFraction = fraction;
            invalidateSelf();
        }

        private void placeSheet(Rect bounds) {
            // Taller than the host by the sheet's corner radius: the glass is rounded all round,
            // and the host's outline cuts the part below, so the bottom corners stay square.
            if (mSheet != null)
                mSheet.setBounds(bounds.left, bounds.top, bounds.right,
                    bounds.bottom + mExtendBelowPx);
        }

        @Override
        protected void onBoundsChange(Rect bounds) {
            if (mInner != null) mInner.setBounds(bounds);
            placeSheet(bounds);
        }

        @Override
        public void draw(@NonNull Canvas canvas) {
            if (mInner != null) mInner.draw(canvas);
            Drawable sheet = mSheet;
            if (sheet == null || !(mFraction > 0f)) return;
            if (mFraction >= 1f) {
                sheet.draw(canvas);
                return;
            }
            // Mid-handoff only: a few frames, as long as the drawer's own glass takes to arrive.
            Rect b = getBounds();
            int saved = canvas.saveLayerAlpha(b.left, b.top, b.right, b.bottom + mExtendBelowPx,
                Math.round(255f * mFraction));
            sheet.draw(canvas);
            canvas.restoreToCount(saved);
        }

        @Override public void setAlpha(int alpha) {}
        @Override public void setColorFilter(@Nullable ColorFilter colorFilter) {}
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
        @Override public int getIntrinsicWidth() { return 0; }
        @Override public int getIntrinsicHeight() { return 0; }
        @Override public int getMinimumWidth() { return 0; }
        @Override public int getMinimumHeight() { return 0; }
    }
}
