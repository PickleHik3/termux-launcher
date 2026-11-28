package com.termux.app.editmode;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * One Look on the All glass card's filmstrip, drawn as that glass (decided 2026-11-28): its own
 * small picture of the wallpaper at the Look's blur ({@link LookTileFrames}), and over it a pane at
 * the Look's Opacity in its tint, its rim as thick and as lit as the Look's slab and Shine, ringed
 * while the Look is worn. Draws only when told something changed.
 */
final class LookTileView extends View {

    /** The tile's corners and its pane's inset, in dp. */
    static final float CORNER_DP = 12f;
    static final float PANE_INSET_DP = 7f;
    static final float PANE_CORNER_DP = 7f;

    private final float mDensity;
    private final Paint mFramePaint = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);
    private final Paint mFill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mPane = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mPaneRim = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mRing = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path mClip = new Path();
    private final RectF mBounds = new RectF();
    private final RectF mPaneRect = new RectF();
    private final Rect mSource = new Rect();
    private final Rect mTarget = new Rect();

    /** The wallpaper frame drawn under the pane, or null (a plain fill stands in for it). */
    @Nullable private Bitmap mFrame;
    private int mPaneColour;
    private int mOpacityPercent;
    private boolean mWorn;
    private int mAccent;
    private int mHairline;
    private int mBendDp;
    private int mShine;

    LookTileView(@NonNull Context context) {
        super(context);
        mDensity = context.getResources().getDisplayMetrics().density;
        mPaneRim.setStyle(Paint.Style.STROKE);
        mPaneRim.setStrokeWidth(Math.max(1f, mDensity));
        mRing.setStyle(Paint.Style.STROKE);
    }

    /** The wallpaper under the glass, a frame of the whole screen; null for none. */
    void setFrame(@Nullable Bitmap frame) {
        if (frame == mFrame) return;
        mFrame = frame;
        invalidate();
    }

    /** The plain fill drawn where there is no wallpaper frame. */
    void setFill(int colour) {
        if (mFill.getColor() == colour) return;
        mFill.setColor(colour);
        invalidate();
    }

    /** The pane: {@code colour} (the glass's ink-side tint) at the Look's {@code opacityPercent}. */
    void setPane(int colour, int opacityPercent, int hairline) {
        if (mPaneColour == colour && mOpacityPercent == opacityPercent && mHairline == hairline) return;
        mPaneColour = colour;
        mOpacityPercent = opacityPercent;
        mHairline = hairline;
        invalidate();
    }

    /** The Look's slab and light: its rim's width follows the thickness, its brightness Shine. */
    void setSlab(int bendDp, int shine) {
        if (mBendDp == bendDp && mShine == shine) return;
        mBendDp = bendDp;
        mShine = shine;
        invalidate();
    }

    /** The pane rim's width, px, for a slab {@code bendDp} thick (0 to 45). */
    static float rimWidthPx(int bendDp, float density) {
        return density * (0.75f + 2.25f * Math.max(0, Math.min(45, bendDp)) / 45f);
    }

    /** The pane rim's alpha, 0..255, for Shine 0..100. */
    static int rimAlpha(int shine) {
        return 0x20 + Math.round(0xB0 * Math.max(0, Math.min(100, shine)) / 100f);
    }

    /** Whether the Look is worn: ringed in {@code accent}. */
    void setWorn(boolean worn, int accent) {
        if (mWorn == worn && mAccent == accent) return;
        mWorn = worn;
        mAccent = accent;
        setSelected(worn);
        invalidate();
    }

    boolean worn() {
        return mWorn;
    }

    /** The frame drawn under the pane, for tests. */
    @Nullable
    Bitmap frame() {
        return mFrame;
    }

    /** The rim's width and alpha now, for tests. */
    float rimWidth() {
        return rimWidthPx(mBendDp, mDensity);
    }

    int paneColour() {
        return mPaneColour;
    }

    /** The pane's opacity, for tests. */
    int opacityPercent() {
        return mOpacityPercent;
    }

    /** The pane's alpha, 0..255, for a Look at {@code opacityPercent}: what the Look's glass lets through. */
    static int paneAlpha(int opacityPercent) {
        return Math.round(255f * Math.max(0, Math.min(100, opacityPercent)) / 100f);
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        int width = getWidth();
        int height = getHeight();
        if (width <= 0 || height <= 0) return;
        float corner = CORNER_DP * mDensity;
        mBounds.set(0, 0, width, height);
        mClip.reset();
        mClip.addRoundRect(mBounds, corner, corner, Path.Direction.CW);
        canvas.save();
        canvas.clipPath(mClip);
        Bitmap frame = mFrame;
        // The cache may let its frame go at any time: a recycled one is never drawn.
        if (frame != null && !frame.isRecycled() && frame.getWidth() > 0 && frame.getHeight() > 0) {
            mSource.set(0, 0, frame.getWidth(), frame.getHeight());
            mTarget.set(0, 0, width, height);
            canvas.drawBitmap(frame, mSource, mTarget, mFramePaint);
        } else {
            canvas.drawRect(mBounds, mFill);
        }
        float inset = PANE_INSET_DP * mDensity;
        float paneCorner = PANE_CORNER_DP * mDensity;
        mPaneRect.set(inset, inset, width - inset, height - inset);
        mPane.setColor((mPaneColour & 0x00FFFFFF) | (paneAlpha(mOpacityPercent) << 24));
        canvas.drawRoundRect(mPaneRect, paneCorner, paneCorner, mPane);
        // The slab's rim: a thick, bright edge for Liquid, a quiet hairline for Solid.
        mPaneRim.setStrokeWidth(rimWidthPx(mBendDp, mDensity));
        mPaneRim.setColor((0x00FFFFFF) | (rimAlpha(mShine) << 24));
        canvas.drawRoundRect(mPaneRect, paneCorner, paneCorner, mPaneRim);
        canvas.restore();
        float ring = (mWorn ? 2f : 1f) * mDensity;
        mRing.setStrokeWidth(ring);
        mRing.setColor(mWorn ? mAccent : mHairline);
        mBounds.inset(ring / 2f, ring / 2f);
        canvas.drawRoundRect(mBounds, corner, corner, mRing);
    }

    /**
     * The part of a {@code frameWidth} x {@code frameHeight} frame a {@code width} x
     * {@code height} tile shows: the frame's lower middle (where the card stands on the screen),
     * at the tile's aspect, as large as fits.
     */
    static void cropCentre(int frameWidth, int frameHeight, int width, int height, @NonNull Rect out) {
        float aspect = width / (float) height;
        int cropWidth = frameWidth;
        int cropHeight = Math.round(cropWidth / aspect);
        if (cropHeight > frameHeight) {
            cropHeight = frameHeight;
            cropWidth = Math.round(cropHeight * aspect);
        }
        // A third of the frame's width is about a tile's worth of the screen: the glass's grain.
        cropWidth = Math.max(1, cropWidth / 3);
        cropHeight = Math.max(1, cropHeight / 3);
        int left = (frameWidth - cropWidth) / 2;
        int top = Math.max(0, Math.min(frameHeight - cropHeight, frameHeight * 2 / 3 - cropHeight / 2));
        out.set(left, top, left + cropWidth, top + cropHeight);
    }
}
