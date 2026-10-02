package com.termux.app.terminal;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;

import androidx.annotation.NonNull;

/**
 * The Comet cursor trail, this app's own: for a short while after the cursor jumps, the convex hull
 * of the old and new cell is filled with a gradient that is clear at the old cell's centre,
 * brightens along the way and is strongest at the new cell, fading out as it ages, with a faint
 * wider glow behind it.
 */
public final class CursorTrailComet {

    public static final long DURATION_MS = 450L;
    private static final float MID_POSITION = 0.75f;
    private static final float MID_ALPHA = 0.35f;
    private static final float END_ALPHA = 0.80f;
    private static final float GLOW_ALPHA = 0.18f;
    private static final float GLOW_EXPAND_DP = 2f;

    private boolean mActive;
    private long mStartMs;
    private float mFromX, mFromY, mToX, mToY;
    // The eight rect corners, then the hull built from them.
    private final float[] mPx = new float[8];
    private final float[] mPy = new float[8];
    private final int[] mOrder = new int[8];
    private final int[] mHull = new int[16];
    private final Path mPath = new Path();
    private final RectF mBounds = new RectF();
    private final Paint mGlowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mCorePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final int[] mColors = new int[3];
    private final float[] mPositions = {0f, MID_POSITION, 1f};

    public CursorTrailComet() {
        mGlowPaint.setStyle(Paint.Style.FILL_AND_STROKE);
        mGlowPaint.setStrokeJoin(Paint.Join.ROUND);
        mCorePaint.setStyle(Paint.Style.FILL);
    }

    public void reset() {
        mActive = false;
    }

    /** Whether a comet was started and has not yet aged out; clears itself once it has. */
    public boolean alive(long nowMs) {
        if (mActive && nowMs - mStartMs >= DURATION_MS) mActive = false;
        return mActive;
    }

    public boolean active() {
        return mActive;
    }

    /** The box the last started comet can paint inside, before any glow slack. */
    @NonNull
    public RectF bounds() {
        return mBounds;
    }

    public void start(float fromL, float fromT, float fromR, float fromB,
                      float toL, float toT, float toR, float toB, long nowMs) {
        mActive = true;
        mStartMs = nowMs;
        mFromX = (fromL + fromR) * 0.5f;
        mFromY = (fromT + fromB) * 0.5f;
        mToX = (toL + toR) * 0.5f;
        mToY = (toT + toB) * 0.5f;
        mPx[0] = fromL; mPy[0] = fromT;
        mPx[1] = fromR; mPy[1] = fromT;
        mPx[2] = fromR; mPy[2] = fromB;
        mPx[3] = fromL; mPy[3] = fromB;
        mPx[4] = toL; mPy[4] = toT;
        mPx[5] = toR; mPy[5] = toT;
        mPx[6] = toR; mPy[6] = toB;
        mPx[7] = toL; mPy[7] = toB;
        int n = buildHull();
        mPath.reset();
        for (int i = 0; i < n; i++) {
            if (i == 0) mPath.moveTo(mPx[mHull[i]], mPy[mHull[i]]);
            else mPath.lineTo(mPx[mHull[i]], mPy[mHull[i]]);
        }
        mPath.close();
        mPath.computeBounds(mBounds, true);
    }

    /** Monotone-chain convex hull over the eight corners; fills {@link #mHull}, returns its size. */
    private int buildHull() {
        for (int i = 0; i < 8; i++) {
            int j = i;
            while (j > 0 && before(i, mOrder[j - 1])) {
                mOrder[j] = mOrder[j - 1];
                j--;
            }
            mOrder[j] = i;
        }
        int k = 0;
        for (int i = 0; i < 8; i++) {
            while (k >= 2 && cross(mHull[k - 2], mHull[k - 1], mOrder[i]) <= 0f) k--;
            mHull[k++] = mOrder[i];
        }
        int lower = k + 1;
        for (int i = 6; i >= 0; i--) {
            while (k >= lower && cross(mHull[k - 2], mHull[k - 1], mOrder[i]) <= 0f) k--;
            mHull[k++] = mOrder[i];
        }
        return k - 1;
    }

    private boolean before(int a, int b) {
        return mPx[a] < mPx[b] || (mPx[a] == mPx[b] && mPy[a] < mPy[b]);
    }

    private float cross(int o, int a, int b) {
        return (mPx[a] - mPx[o]) * (mPy[b] - mPy[o]) - (mPy[a] - mPy[o]) * (mPx[b] - mPx[o]);
    }

    /**
     * Paints the comet.
     *
     * @param color     trail colour; its alpha channel, when set, scales everything
     * @param strength  the trail's own opacity, 0..1
     * @param density   screen density, for the glow's width
     */
    public void draw(@NonNull Canvas canvas, int color, float strength, long nowMs, float density) {
        if (!mActive) return;
        float age = (nowMs - mStartMs) / (float) DURATION_MS;
        if (age >= 1f) return;
        float dx = mToX - mFromX, dy = mToY - mFromY;
        if (dx * dx + dy * dy < 1e-4f) return;
        float fade = (1f - age) * Math.max(0f, Math.min(1f, strength));
        int baseAlpha = Color.alpha(color) > 0 ? Color.alpha(color) : 255;
        int rgb = color & 0x00FFFFFF;
        mColors[0] = rgb;
        mColors[1] = (Math.round(baseAlpha * MID_ALPHA * fade) << 24) | rgb;
        mColors[2] = (Math.round(baseAlpha * END_ALPHA * fade) << 24) | rgb;
        Shader gradient = new LinearGradient(mFromX, mFromY, mToX, mToY, mColors, mPositions,
            Shader.TileMode.CLAMP);
        mGlowPaint.setShader(gradient);
        mGlowPaint.setAlpha(Math.round(255f * GLOW_ALPHA));
        mGlowPaint.setStrokeWidth(GLOW_EXPAND_DP * 2f * density);
        canvas.drawPath(mPath, mGlowPaint);
        mCorePaint.setShader(gradient);
        canvas.drawPath(mPath, mCorePaint);
    }
}
