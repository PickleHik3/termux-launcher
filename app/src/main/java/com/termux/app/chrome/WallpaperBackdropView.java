package com.termux.app.chrome;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * The wallpaper the launcher draws for itself, behind every surface.
 *
 * <p>It paints the radius-0 wallpaper frame from {@link WallpaperBlurCache} — the same frame every
 * glass crop is cut from — at the rect it was captured for, then the wall's dim over it. Because
 * backdrop and glass are one picture at one offset, the ROM's own composite zoom stops mattering;
 * see {@link WallpaperBackdropPolicy}.</p>
 *
 * <p>The frame's rect is the decor's, which reaches under the transparent system bars, so the view
 * deliberately paints outside its own bounds — the same thing the status glass does with a negative
 * translation, and for the same reason. Both rely on the no-clip chain the edge-to-edge layout sets
 * up ({@code clipChildren=false} on the root and the terminal container).</p>
 *
 * <p>A frame is handed in when one lands, and the view is re-recorded then. A page sliding over
 * it does not touch this view unless the wallpaper pans: with a managed wallpaper the captured
 * frame is wider than the screen ({@link WallpaperParallax}) and the wall's owner moves the
 * shared offset and invalidates this view per frame of the slide, which draws the same bitmap
 * that much further left — a translate, never a new frame.</p>
 */
public final class WallpaperBackdropView extends View {

    @Nullable private Bitmap mFrame;
    /** The wallpaper's live x-offset, read at draw time; null while nothing pans. */
    @Nullable private WallpaperParallax mParallax;
    /**
     * The frame {@link #mFrame} is fading in from, drawn underneath it while {@link #mCrossfade}
     * has not reached 1. Held (not recycled) here through the fade so a scan for what is still on
     * screen — {@code isFrameInUse} — keeps finding it exactly where {@link #heldFrame()} answers.
     */
    @Nullable private Bitmap mPreviousFrame;
    @NonNull private final FrameCrossfade mCrossfade = new FrameCrossfade();
    /** The screen rect {@link #mFrame} was captured for; empty while no frame is held. */
    @NonNull private final Rect mFrameRect = new Rect();
    /** {@link #mFrameRect} in this view's own coordinates. Reused; never allocated in a draw. */
    @NonNull private final Rect mDest = new Rect();
    @NonNull private final Paint mFramePaint = new Paint(Paint.FILTER_BITMAP_FLAG);
    @NonNull private final Paint mDimPaint = new Paint();
    @NonNull private final int[] mLocation = new int[2];
    private int mDimColor = Color.TRANSPARENT;

    public WallpaperBackdropView(@NonNull Context context) {
        super(context);
        init();
    }

    public WallpaperBackdropView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        setWillNotDraw(false);
        mDimPaint.setStyle(Paint.Style.FILL);
        // Nothing captured yet: the system wallpaper shows through, rather than a black window.
        setVisibility(INVISIBLE);
    }

    /**
     * {@link #showFrame(Bitmap, Rect, int, boolean)} without a crossfade — every swap this makes
     * lands outright, which is what a pending capture, a rotation and every caller that predates
     * the wallpaper crossfade all still want.
     */
    public void showFrame(@Nullable Bitmap frame, @NonNull Rect frameRect, int dimColor) {
        showFrame(frame, frameRect, dimColor, false);
    }

    /**
     * Shows {@code frame} for {@code frameRect} with {@code dimColor} over it.
     *
     * <p>A null frame means the capture is still on the blur worker. The frame already held then
     * stays up while it was captured for this same rect — the wallpaper may have changed underneath
     * it, but its geometry has not, so the worst it shows is the previous picture for a frame or
     * two instead of the misaligned system one. When the rect itself has moved (a rotation, a
     * resize) the held frame would show as a shifted crop, so the backdrop goes away until the new
     * frame lands and the system wallpaper carries that one frame.</p>
     *
     * <p>{@code crossfade} asks for the swap from whatever is up now to {@code frame} to fade over
     * {@link FrameCrossfade#DURATION_MS} instead of landing on the next draw — the caller already
     * knows this frame arrived to replace one a wallpaper change displaced
     * ({@link WallpaperBlurCache#isCrossfadedRadius}), never a rotation or a radius change.</p>
     */
    public void showFrame(@Nullable Bitmap frame, @NonNull Rect frameRect, int dimColor,
                          boolean crossfade) {
        if (frame != null && frame.isRecycled()) frame = null;
        if (frame == null && (mFrame == null || mFrame.isRecycled()
            || !mFrameRect.equals(frameRect))) {
            hide();
            return;
        }
        if (frame != null && frame != mFrame) {
            if (crossfade && mFrame != null && !mFrame.isRecycled() && mFrameRect.equals(frameRect)) {
                mPreviousFrame = mFrame;
                mCrossfade.start();
            } else {
                mPreviousFrame = null;
                mCrossfade.cancel();
            }
            mFrame = frame;
            mFrameRect.set(frameRect);
        }
        mDimColor = dimColor;
        mDimPaint.setColor(dimColor);
        updateDestRect();
        setVisibility(VISIBLE);
        invalidate();
    }

    /**
     * The dim over the frame, on its own. The wall's ground colour is settled after the pass that
     * dresses the glass, so the opacity sliders would otherwise show a pass late on the backdrop
     * while the root's background under it had already moved.
     */
    public void setDimColor(int dimColor) {
        if (mDimColor == dimColor) return;
        mDimColor = dimColor;
        mDimPaint.setColor(dimColor);
        if (mFrame != null) invalidate();
    }

    /**
     * Follow the wallpaper's parallax: the frame is drawn {@code parallax.offsetPx()} further left
     * on every draw. Null stops following. Whoever moves the offset invalidates this view.
     */
    public void setParallax(@Nullable WallpaperParallax parallax) {
        if (mParallax == parallax) return;
        mParallax = parallax;
        if (mFrame != null) invalidate();
    }

    /** Puts the backdrop away and lets go of its frames, so the cache can recycle them. */
    public void hide() {
        boolean wasShowing = mFrame != null;
        mFrame = null;
        mPreviousFrame = null;
        mCrossfade.cancel();
        mFrameRect.setEmpty();
        mDest.setEmpty();
        setVisibility(INVISIBLE);
        if (wasShowing) invalidate();
    }

    /** The frame this view is painting, so the blur cache never recycles it under a draw. */
    @Nullable
    public Bitmap heldFrame() {
        return mFrame;
    }

    /**
     * The frame a crossfade is still fading out of, so the blur cache never recycles it mid-fade —
     * null once the fade has landed on {@link #heldFrame()} alone.
     */
    @Nullable
    public Bitmap fadingFrame() {
        return mCrossfade.isFinished() ? null : mPreviousFrame;
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        updateDestRect();
    }

    private void updateDestRect() {
        if (mFrame == null) {
            mDest.setEmpty();
            return;
        }
        getLocationOnScreen(mLocation);
        mDest.set(mFrameRect);
        mDest.offset(-mLocation[0], -mLocation[1]);
    }

    /**
     * True while the frame covers every pixel of this view: the wallpaper capture is opaque and the
     * dim over it is black, so the output is too, whatever the bitmap's own alpha flag says.
     */
    @Override
    public boolean isOpaque() {
        return mFrame != null && !mFrame.isRecycled()
            && mDest.left <= 0 && mDest.top <= 0
            && mDest.right >= getWidth() && mDest.bottom >= getHeight()
            && getWidth() > 0 && getHeight() > 0;
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        Bitmap frame = mFrame;
        if (frame == null || frame.isRecycled() || mDest.isEmpty()) return;
        float progress = mCrossfade.progress();
        Bitmap previous = mPreviousFrame;
        boolean fading = progress < 1f && previous != null && !previous.isRecycled();
        // The dim covers the view whatever the offset; only the picture slides under it. The
        // frame is wider than the screen by the offset's whole travel, so no edge ever shows.
        float offsetPx = mParallax == null ? 0f : mParallax.offsetPx();
        int save = canvas.save();
        canvas.translate(-offsetPx, 0f);
        if (fading) {
            // Both frames share this backdrop's own rect — a crossfade only ever starts when the
            // geometry held, never across a rotation — so one dest rect draws either of them.
            drawFrame(canvas, previous, 255);
            drawFrame(canvas, frame, Math.round(255f * progress));
            postInvalidateOnAnimation();
        } else {
            mPreviousFrame = null;
            drawFrame(canvas, frame, 255);
        }
        canvas.restoreToCount(save);
        if (Color.alpha(mDimColor) > 0) canvas.drawRect(mDest, mDimPaint);
    }

    private void drawFrame(@NonNull Canvas canvas, @NonNull Bitmap frame, int alpha) {
        boolean sameSize = frame.getWidth() == mDest.width() && frame.getHeight() == mDest.height();
        // The frame was captured at exactly this size; drawing it 1:1 skips the filter, same as
        // before there was an alpha to set at all.
        mFramePaint.setFilterBitmap(!sameSize);
        mFramePaint.setAlpha(alpha);
        if (sameSize) {
            canvas.drawBitmap(frame, mDest.left, mDest.top, mFramePaint);
        } else {
            canvas.drawBitmap(frame, null, mDest, mFramePaint);
        }
    }
}
