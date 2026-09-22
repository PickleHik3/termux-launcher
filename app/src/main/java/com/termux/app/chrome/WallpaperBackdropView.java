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
 * <p>Nothing here runs per display frame: a frame is handed in when one lands, and the view is only
 * re-recorded then. A page sliding over it does not touch this view.</p>
 */
public final class WallpaperBackdropView extends View {

    @Nullable private Bitmap mFrame;
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
     * Shows {@code frame} for {@code frameRect} with {@code dimColor} over it.
     *
     * <p>A null frame means the capture is still on the blur worker. The frame already held then
     * stays up while it was captured for this same rect — the wallpaper may have changed underneath
     * it, but its geometry has not, so the worst it shows is the previous picture for a frame or
     * two instead of the misaligned system one. When the rect itself has moved (a rotation, a
     * resize) the held frame would show as a shifted crop, so the backdrop goes away until the new
     * frame lands and the system wallpaper carries that one frame.</p>
     */
    public void showFrame(@Nullable Bitmap frame, @NonNull Rect frameRect, int dimColor) {
        if (frame != null && frame.isRecycled()) frame = null;
        if (frame == null && (mFrame == null || mFrame.isRecycled()
            || !mFrameRect.equals(frameRect))) {
            hide();
            return;
        }
        if (frame != null) {
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

    /** Puts the backdrop away and lets go of its frame, so the cache can recycle it. */
    public void hide() {
        boolean wasShowing = mFrame != null;
        mFrame = null;
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
        if (frame.getWidth() == mDest.width() && frame.getHeight() == mDest.height()) {
            // The frame was captured at exactly this size; drawing it 1:1 skips the filter.
            canvas.drawBitmap(frame, mDest.left, mDest.top, null);
        } else {
            canvas.drawBitmap(frame, null, mDest, mFramePaint);
        }
        if (Color.alpha(mDimColor) > 0) canvas.drawRect(mDest, mDimPaint);
    }
}
