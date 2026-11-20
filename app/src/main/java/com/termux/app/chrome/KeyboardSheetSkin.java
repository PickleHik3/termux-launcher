package com.termux.app.chrome;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.ColorFilter;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.RectF;
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

    /**
     * The radius of the keyboard card's top corners, whole pixels. The host itself stays a plain
     * rectangle (its outline is not rounded), so the corners are drawn: the host's own background
     * is cut to the curve and the sheet's glass runs on beyond it, so what lies outside the curve
     * is the drawer's tinted glass and never the bare scene.
     */
    public void setCardTopRadius(float px) {
        mLayers.setCardTopRadius(Math.max(0f, Math.round(px)));
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
        private float mTopRadius;
        private int mDim;
        private final Paint mDimPaint = new Paint();
        private final Path mCard = new Path();
        private final RectF mCardRect = new RectF();
        private final float[] mCardRadii = new float[8];

        void setCardTopRadius(float px) {
            if (px == mTopRadius) return;
            mTopRadius = px;
            buildCard(getBounds());
            invalidateSelf();
        }

        private void buildCard(Rect b) {
            mCard.rewind();
            if (mTopRadius <= 0f) return;
            mCardRect.set(b);
            mCardRadii[0] = mCardRadii[1] = mCardRadii[2] = mCardRadii[3] = mTopRadius;
            mCard.addRoundRect(mCardRect, mCardRadii, Path.Direction.CW);
        }

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
            // Taller than the host by the sheet's corner radius, above and below: the glass is
            // rounded all round, and the host's outline cuts both extensions, so none of its
            // corners shows and it fills the host edge to edge.
            if (mSheet != null)
                mSheet.setBounds(bounds.left, bounds.top - mExtendBelowPx, bounds.right,
                    bounds.bottom + mExtendBelowPx);
        }

        @Override
        protected void onBoundsChange(Rect bounds) {
            if (mInner != null) mInner.setBounds(bounds);
            placeSheet(bounds);
            buildCard(bounds);
        }

        @Override
        public void draw(@NonNull Canvas canvas) {
            if (mInner != null) {
                if (mTopRadius > 0f) {
                    int clip = canvas.save();
                    canvas.clipPath(mCard);
                    mInner.draw(canvas);
                    canvas.restoreToCount(clip);
                } else {
                    mInner.draw(canvas);
                }
            }
            Drawable sheet = mSheet;
            if (sheet == null || !(mFraction > 0f)) return;
            Rect b = getBounds();
            if (mDim > 0) {
                canvas.drawRect(b.left, b.top - mExtendBelowPx, b.right,
                    b.bottom + mExtendBelowPx, mDimPaint);
            }
            if (mFraction >= 1f) {
                sheet.draw(canvas);
                return;
            }
            // Mid-handoff only: a few frames, as long as the drawer's own glass takes to arrive.
            int saved = canvas.saveLayerAlpha(b.left, b.top - mExtendBelowPx, b.right,
                b.bottom + mExtendBelowPx,
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
