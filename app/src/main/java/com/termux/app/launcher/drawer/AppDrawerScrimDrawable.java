package com.termux.app.launcher.drawer;

import android.graphics.Canvas;
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

    private final Paint mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path mPath = new Path();
    private final RectF mHole = new RectF();
    private float mHoleRadiusPx;
    private boolean mHasHole;
    private boolean mPathDirty = true;

    AppDrawerScrimDrawable() {
        mPaint.setStyle(Paint.Style.FILL);
        mPath.setFillType(Path.FillType.EVEN_ODD);
    }

    void setColor(int color) {
        if (mPaint.getColor() == color) return;
        mPaint.setColor(color);
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
        mPathDirty = true;
        invalidateSelf();
    }

    /** Visible for tests: the hole, or null when the dim covers everything. */
    @Nullable
    RectF getHole() {
        return mHasHole ? new RectF(mHole) : null;
    }

    @Override
    protected void onBoundsChange(@NonNull android.graphics.Rect bounds) {
        mPathDirty = true;
    }

    @Override
    public void draw(@NonNull Canvas canvas) {
        if (mPaint.getAlpha() == 0) return;
        if (!mHasHole) {
            canvas.drawRect(getBounds(), mPaint);
            return;
        }
        if (mPathDirty) {
            mPath.reset();
            mPath.addRect(getBounds().left, getBounds().top, getBounds().right,
                getBounds().bottom, Path.Direction.CW);
            mPath.addRoundRect(mHole, mHoleRadiusPx, mHoleRadiusPx, Path.Direction.CW);
            mPathDirty = false;
        }
        canvas.drawPath(mPath, mPaint);
    }

    @Override public void setAlpha(int alpha) {
        // The colour carries the dim's alpha; the host's own alpha carries the hand-off.
    }

    @Override public void setColorFilter(@Nullable ColorFilter colorFilter) {
        mPaint.setColorFilter(colorFilter);
        invalidateSelf();
    }

    @Override public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }
}
