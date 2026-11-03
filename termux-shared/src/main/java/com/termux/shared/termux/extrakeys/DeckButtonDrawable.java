package com.termux.shared.termux.extrakeys;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;

import androidx.annotation.NonNull;

/**
 * A raised push-button for the deck look, drawn in three layers so the corners stay round: the
 * whole button in the foot colour, the button less its foot in the top colour, and the face from
 * one dp down in the key colour. Pressed, the foot goes and the face moves a quarter of the way
 * to the foot colour. An active button also draws a bar in the lit colour.
 */
final class DeckButtonDrawable extends Drawable {

    static final float RADIUS_DP = 4f;
    static final float FOOT_DP = 2f;
    static final float FACE_TOP_DP = 1f;
    static final float PRESSED_FACE_MIX = 0.25f;
    static final float BAR_HEIGHT_DP = 2f;
    static final float BAR_SIDE_INSET_DP = 6f;
    static final float BAR_BOTTOM_DP = 3f;

    private final float mDensity;
    private final int mKey, mKeyTop, mKeyFoot, mLit;
    private final boolean mActive;
    private final Paint mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF mRect = new RectF();
    private boolean mPressed;

    DeckButtonDrawable(float density, int key, int keyTop, int keyFoot, int lit, boolean active) {
        mDensity = density;
        mKey = key;
        mKeyTop = keyTop;
        mKeyFoot = keyFoot;
        mLit = lit;
        mActive = active;
    }

    /** {l, t, r, b} of the foot layer: the whole button. */
    static int[] footRect(int l, int t, int r, int b) {
        return new int[] {l, t, r, b};
    }

    /** {l, t, r, b} of the top layer: the button less a foot (none when pressed). */
    static int[] topRect(int l, int t, int r, int b, int footPx, boolean pressed) {
        return new int[] {l, t, r, pressed ? b : Math.max(t, b - footPx)};
    }

    /** {l, t, r, b} of the face: from facePx down, ending where the top layer ends. */
    static int[] faceRect(int l, int t, int r, int b, int footPx, int facePx, boolean pressed) {
        int bottom = pressed ? b : Math.max(t, b - footPx);
        return new int[] {l, Math.min(bottom, t + facePx), r, bottom};
    }

    /** {l, t, r, b} of the active bar, inset from each side and above the bottom. */
    static int[] barRect(int l, int t, int r, int b, int sidePx, int barPx, int bottomPx) {
        int bar = Math.max(0, Math.min(barPx, b - t));
        int left = l + sidePx;
        int right = r - sidePx;
        if (right < left) {
            left = right = (l + r) / 2;
        }
        return new int[] {left, b - bottomPx - bar, right, b - bottomPx};
    }

    static int mix(int from, int to, float amount) {
        int a = Math.round(((from >>> 24) & 0xFF) * (1f - amount) + ((to >>> 24) & 0xFF) * amount);
        int r = Math.round(((from >> 16) & 0xFF) * (1f - amount) + ((to >> 16) & 0xFF) * amount);
        int g = Math.round(((from >> 8) & 0xFF) * (1f - amount) + ((to >> 8) & 0xFF) * amount);
        int bl = Math.round((from & 0xFF) * (1f - amount) + (to & 0xFF) * amount);
        return (a << 24) | (r << 16) | (g << 8) | bl;
    }

    private int px(float dp) {
        return Math.max(1, Math.round(dp * mDensity));
    }

    private void layer(Canvas canvas, int[] rect, int color) {
        mRect.set(rect[0], rect[1], rect[2], rect[3]);
        mPaint.setColor(color);
        float radius = Math.min(RADIUS_DP * mDensity, Math.min(mRect.width(), mRect.height()) / 2f);
        canvas.drawRoundRect(mRect, radius, radius, mPaint);
    }

    @Override
    public void draw(@NonNull Canvas canvas) {
        Rect b = getBounds();
        if (b.isEmpty())
            return;
        int foot = px(FOOT_DP);
        int face = px(FACE_TOP_DP);
        mPaint.setStyle(Paint.Style.FILL);
        if (!mPressed)
            layer(canvas, footRect(b.left, b.top, b.right, b.bottom), mKeyFoot);
        layer(canvas, topRect(b.left, b.top, b.right, b.bottom, foot, mPressed), mKeyTop);
        layer(canvas, faceRect(b.left, b.top, b.right, b.bottom, foot, face, mPressed),
            mPressed ? mix(mKey, mKeyFoot, PRESSED_FACE_MIX) : mKey);
        if (mActive) {
            layer(canvas, barRect(b.left, b.top, b.right, b.bottom,
                px(BAR_SIDE_INSET_DP), px(BAR_HEIGHT_DP), px(BAR_BOTTOM_DP)), mLit);
        }
    }

    @Override
    public boolean isStateful() {
        return true;
    }

    @Override
    protected boolean onStateChange(int[] state) {
        boolean pressed = false;
        for (int s : state) {
            if (s == android.R.attr.state_pressed) {
                pressed = true;
                break;
            }
        }
        if (pressed == mPressed)
            return false;
        mPressed = pressed;
        invalidateSelf();
        return true;
    }

    @Override
    public void setAlpha(int alpha) {
    }

    @Override
    public void setColorFilter(ColorFilter colorFilter) {
    }

    @Override
    public int getOpacity() {
        return PixelFormat.OPAQUE;
    }
}
