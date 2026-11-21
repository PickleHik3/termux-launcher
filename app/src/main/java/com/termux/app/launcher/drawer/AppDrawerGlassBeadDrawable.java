package com.termux.app.launcher.drawer;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.chrome.ChromeInk;
import com.termux.app.chrome.ChromeShade;
import com.termux.app.chrome.ElevatedGlass;
import com.termux.app.chrome.InsetGlass;

/**
 * The keyboard highlight: a small raised glass bead. A light wash, a lit edge that is brightest
 * along the top and fades to nothing down the sides, and a faint contact shadow inside the bottom
 * edge. Two vector layers, no blur, nothing sampled from behind.
 *
 * <p>It ignores its own bounds and draws in {@link #setInner}'s rectangle, so the cell can hug its
 * content while the background stays the whole view. Shaders are built when that rectangle or the
 * colours change, never in {@link #draw}. Colours come from the chrome's own tokens: the wash and
 * rim from {@link ChromeShade} (resolved against the chrome's polarity), the shadow from
 * {@link InsetGlass#innerShadow} at {@link ElevatedGlass#RIM_GAIN}.</p>
 */
final class AppDrawerGlassBeadDrawable extends Drawable {

    private static final int RIM_SEED = 0xE6FFFFFF;
    private static final int WASH_SEED = 0x26FFFFFF;
    private static final int WASH_REST = 0x08FFFFFF;
    /** Share of the height where the lit edge has fallen to {@link #RIM_MID_SHARE} of its strength. */
    private static final float RIM_MID_AT = 0.4f;
    private static final float RIM_MID_SHARE = 0.3f;
    /** The contact shadow stays clear above this share of the height. */
    private static final float SHADOW_FROM = 0.6f;
    private static final float EDGE_DP = 1.5f;

    private final Paint mFill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mRim = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mShadow = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF mRect = new RectF();
    private final float[] mRimStops = {0f, RIM_MID_AT, 1f};
    private final float[] mShadowStops = {0f, SHADOW_FROM, 1f};
    private final float mEdgePx;
    private float mRadiusPx;
    private int mLeft;
    private int mTop;
    private int mRight;
    private int mBottom;
    private boolean mShaded;

    AppDrawerGlassBeadDrawable(float density) {
        mEdgePx = Math.max(1f, EDGE_DP * density);
        mRim.setStyle(Paint.Style.STROKE);
        mRim.setStrokeWidth(mEdgePx);
        mShadow.setStyle(Paint.Style.STROKE);
        mShadow.setStrokeWidth(mEdgePx);
        resolveColors();
    }

    /** Re-reads the chrome's tokens: its polarity moves with a theme or wallpaper change. */
    void resolveColors() {
        mFill.setColor(ChromeShade.stateFill(WASH_SEED, WASH_REST));
        mShaded = false;
        invalidateSelf();
    }

    /** Sets where the bead is drawn, in the view's own pixels; compares before it rebuilds. */
    void setInner(int left, int top, int right, int bottom, float radiusPx) {
        if (mLeft == left && mTop == top && mRight == right && mBottom == bottom
            && mRadiusPx == radiusPx) return;
        mLeft = left;
        mTop = top;
        mRight = right;
        mBottom = bottom;
        mRadiusPx = radiusPx;
        mShaded = false;
        invalidateSelf();
    }

    int innerLeft() { return mLeft; }
    int innerTop() { return mTop; }
    int innerRight() { return mRight; }
    int innerBottom() { return mBottom; }
    float radiusPx() { return mRadiusPx; }

    private void buildShaders() {
        mShaded = true;
        int rim = ChromeShade.rim(RIM_SEED);
        int rimMid = withAlphaShare(rim, RIM_MID_SHARE);
        int rimEnd = withAlphaShare(rim, 0f);
        mRim.setShader(new LinearGradient(0f, mTop, 0f, mBottom,
            new int[] {rim, rimMid, rimEnd}, mRimStops, Shader.TileMode.CLAMP));
        ChromeInk.Polarity polarity = ChromeShade.polarity();
        boolean dark = polarity == null || polarity == ChromeInk.Polarity.PALE_INK;
        int shadow = withAlphaShare(InsetGlass.innerShadow(dark), ElevatedGlass.RIM_GAIN);
        int clear = withAlphaShare(shadow, 0f);
        mShadow.setShader(new LinearGradient(0f, mTop, 0f, mBottom,
            new int[] {clear, clear, shadow}, mShadowStops, Shader.TileMode.CLAMP));
    }

    @ColorInt
    private static int withAlphaShare(@ColorInt int color, float share) {
        int alpha = Math.max(0, Math.min(255, Math.round(Color.alpha(color) * share)));
        return (color & 0x00FFFFFF) | (alpha << 24);
    }

    @Override
    public void draw(@NonNull Canvas canvas) {
        if (mRight <= mLeft || mBottom <= mTop) return;
        if (!mShaded) buildShaders();
        float half = mEdgePx / 2f;
        mRect.set(mLeft + half, mTop + half, mRight - half, mBottom - half);
        float radius = Math.max(0f, mRadiusPx - half);
        canvas.drawRoundRect(mRect, radius, radius, mFill);
        canvas.drawRoundRect(mRect, radius, radius, mShadow);
        canvas.drawRoundRect(mRect, radius, radius, mRim);
    }

    /** The colours carry their own alpha and the cell fades through the view's, so this is a no-op. */
    @Override public void setAlpha(int alpha) {}

    @Override public void setColorFilter(@Nullable ColorFilter colorFilter) {
        mFill.setColorFilter(colorFilter);
        mRim.setColorFilter(colorFilter);
        mShadow.setColorFilter(colorFilter);
        invalidateSelf();
    }

    @Override public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }
}
