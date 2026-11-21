package com.termux.app.chrome;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.ColorFilter;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * What the in-app keyboard's host wears over its own look while the app drawer is open
 * ({@link KeyboardSheetLook}): the drawer's dim and the sheet's glass, laid over the host's own
 * background at the drawer's fraction. The host's outline keeps the card's own shape. One
 * instance per host; allocation free once the glass is set.
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

    /**
     * Sets the glass the sheet is drawn with; null lets go of it (and its backdrop). The glass
     * runs {@code extendBelowPx} past the host's bottom, so its own bottom corners fall outside
     * the host's outline and only its top corners, the card's, show.
     */
    public void setSheet(@Nullable Drawable sheet, int extendBelowPx) {
        mSheet = sheet;
        mLayers.setSheet(sheet, extendBelowPx);
    }

    @Nullable
    public Drawable sheet() {
        return mSheet;
    }

    /**
     * The drawer's own dim over the scene, as the keyboard wears it (0..255 black alpha): drawn
     * under the sheet's glass, so the glass sits over the same darkened ground the drawer's does.
     */
    public void setDim(int alpha) {
        mLayers.setDim(alpha);
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

    /** The host's own background under the sheet, which fades in over it. */
    private static final class Layers extends Drawable {
        @Nullable private Drawable mInner;
        @Nullable private Drawable mSheet;
        private int mExtendBelowPx;
        private float mFraction;
        private int mDim;
        private final Paint mDimPaint = new Paint();

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

        void setDim(int alpha) {
            int a = Math.max(0, Math.min(255, alpha));
            if (a == mDim) return;
            mDim = a;
            mDimPaint.setColor(a << 24);
            invalidateSelf();
        }

        void setFraction(float fraction) {
            mFraction = fraction;
            invalidateSelf();
        }

        private void placeSheet(Rect bounds) {
            // The host's top and sides, run on below it: its top corners are the card's, and its
            // bottom ones fall outside the host's outline.
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
            Rect b = getBounds();
            if (mDim > 0) {
                canvas.drawRect(b.left, b.top, b.right, b.bottom + mExtendBelowPx, mDimPaint);
            }
            if (mFraction >= 1f) {
                sheet.draw(canvas);
                return;
            }
            // Mid-handoff only: a few frames, as long as the drawer's own glass takes to arrive.
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
