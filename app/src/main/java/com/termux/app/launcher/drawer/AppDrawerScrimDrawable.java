package com.termux.app.launcher.drawer;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * The drawer host's scene dim, over the whole screen but for one rounded hole.
 *
 * <p>Under Floating the built-in keyboard is a card of its own below the drawer card. The host
 * (which draws above the keyboard) used to be clipped at the keyboard's top so the keys were not
 * dimmed, which left the dim ending in a hard line across the screen and the wallpaper bare around
 * the card. With the hole the dim runs below and around the keyboard card, and the card alone
 * shows through at its own brightness: the keyboard sits on the drawer's backdrop.
 *
 * <p>Per frame only the colour and the hole's position change: no allocation unless the hole's
 * geometry does, and no layout ever.
 */
final class AppDrawerScrimDrawable extends Drawable {

    /**
     * Drawn as four plain rects around the hole and four small corner wedges, never as one
     * full-screen path with a hole in it: the hole moves with the keyboard on every frame of the
     * open, and a full-screen anti-aliased even-odd path re-rasterised each frame cost the
     * RenderThread 5-14 ms of issue time per frame on pong. The rects are exact (no AA, so no
     * seams where they meet); only the wedges, one cached quarter shape drawn four times under a
     * translate/mirror, are anti-aliased.
     */
    private final Paint mPaint = new Paint();
    private final Paint mWedgePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    /** The top-left corner's wedge at the origin: the r x r square less its quarter circle. */
    private final Path mWedge = new Path();
    private float mWedgeRadiusPx = -1f;
    private final RectF mArcScratch = new RectF();
    private final RectF mHole = new RectF();
    private float mHoleRadiusPx;
    private boolean mHasHole;

    AppDrawerScrimDrawable() {
        mPaint.setStyle(Paint.Style.FILL);
        mWedgePaint.setStyle(Paint.Style.FILL);
    }

    void setColor(int color) {
        if (mPaint.getColor() == color) return;
        mPaint.setColor(color);
        mWedgePaint.setColor(color);
        invalidateSelf();
    }

    int getColor() {
        return mPaint.getColor();
    }

    /** The keyboard card's rect in the host's coordinates, or null for a dim with no hole. */
    void setHole(@Nullable RectF hole, float radiusPx) {
        if (hole == null) {
            if (!mHasHole) return;
            mHasHole = false;
        } else {
            if (mHasHole && mHole.equals(hole) && mHoleRadiusPx == radiusPx) return;
            mHasHole = true;
            mHole.set(hole);
            mHoleRadiusPx = Math.max(0f, radiusPx);
        }
        invalidateSelf();
    }

    /** Visible for tests: the hole, or null when the dim covers everything. */
    @Nullable
    RectF getHole() {
        return mHasHole ? new RectF(mHole) : null;
    }

    /** Visible for tests: the corner radius the wedges are drawn at, clamped to the hole. */
    float effectiveRadiusPx() {
        return Math.max(0f, Math.min(mHoleRadiusPx,
            Math.min(mHole.width(), mHole.height()) / 2f));
    }

    @Override
    public void draw(@NonNull Canvas canvas) {
        if (Color.alpha(mPaint.getColor()) == 0) return;
        android.graphics.Rect b = getBounds();
        if (!mHasHole) {
            canvas.drawRect(b, mPaint);
            return;
        }
        RectF h = mHole;
        // Around the hole: above, below, then the two sides between them.
        if (h.top > b.top) canvas.drawRect(b.left, b.top, b.right, h.top, mPaint);
        if (h.bottom < b.bottom) canvas.drawRect(b.left, h.bottom, b.right, b.bottom, mPaint);
        if (h.left > b.left) canvas.drawRect(b.left, h.top, h.left, h.bottom, mPaint);
        if (h.right < b.right) canvas.drawRect(h.right, h.top, b.right, h.bottom, mPaint);
        float r = effectiveRadiusPx();
        if (r <= 0f) return;
        if (mWedgeRadiusPx != r) {
            mWedgeRadiusPx = r;
            mWedge.reset();
            mWedge.moveTo(0f, 0f);
            mWedge.lineTo(r, 0f);
            mArcScratch.set(0f, 0f, 2f * r, 2f * r);
            // From the top of the quarter circle round to its left end, then back to the corner.
            mWedge.arcTo(mArcScratch, 270f, -90f, false);
            mWedge.close();
        }
        drawWedge(canvas, h.left, h.top, 1f, 1f);
        drawWedge(canvas, h.right, h.top, -1f, 1f);
        drawWedge(canvas, h.left, h.bottom, 1f, -1f);
        drawWedge(canvas, h.right, h.bottom, -1f, -1f);
    }

    private void drawWedge(@NonNull Canvas canvas, float x, float y, float sx, float sy) {
        int save = canvas.save();
        canvas.translate(x, y);
        canvas.scale(sx, sy);
        canvas.drawPath(mWedge, mWedgePaint);
        canvas.restoreToCount(save);
    }

    @Override public void setAlpha(int alpha) {
        // The colour carries the dim's alpha; the host's own alpha carries the hand-off.
    }

    @Override public void setColorFilter(@Nullable ColorFilter colorFilter) {
        mPaint.setColorFilter(colorFilter);
        mWedgePaint.setColorFilter(colorFilter);
        invalidateSelf();
    }

    @Override public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }
}
