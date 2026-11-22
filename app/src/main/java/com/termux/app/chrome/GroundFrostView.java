package com.termux.app.chrome;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.chrome.scene.LiveSceneFrame;

/**
 * The frosted wallpaper under a turning window switch's gap (#316): in the ground layer, under the
 * ground strip's dim, it draws the frame the windows' glass already samples (the scene's live
 * frame, or the stored pre-blurred frame) through the glass's frost and tint, so the gap between the
 * leaving card and the arriving window reads as glass beside glass instead of the sharp wallpaper.
 * Gone at rest; shown only for a switch's duration. While shown it is left out of every recording
 * ({@link BackdropRecording#leaveOut}), so live glass over it keeps sampling the plain ground.
 * Holds no pixels of its own and allocates nothing in {@link #onDraw}.
 */
public final class GroundFrostView extends View implements LiveSceneFrame.Surface {

    @NonNull private final Paint mPaint = new Paint(Paint.FILTER_BITMAP_FLAG);
    @NonNull private final RectF mDest = new RectF();
    @NonNull private final Rect mFrameRect = new Rect();
    @NonNull private final int[] mAt = new int[2];
    /** The stored frame drawn, or null while the scene's live frame is (or nothing is shown). */
    @Nullable private Bitmap mFrame;
    @NonNull private final Paint mTintPaint = new Paint();
    private boolean mScene;
    private float mParallaxPx;

    public GroundFrostView(@NonNull Context context) {
        this(context, null);
    }

    public GroundFrostView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        setWillNotDraw(false);
    }

    /**
     * Shows the scene's live frame, read at each draw, through {@code frost}, with {@code tint}
     * (the windows' glass tint, ARGB, alpha included) laid over it as the glass's face lays it.
     */
    public void showScene(@NonNull ColorFilter frost, int tint) {
        mScene = true;
        mFrame = null;
        mParallaxPx = 0f;
        mFrameRect.setEmpty();
        show(frost, tint);
    }

    /**
     * Shows the stored frame {@code frame}, standing for {@code frameRect} on screen and sampled
     * {@code parallaxPx} along, through {@code frost} and under {@code tint}: as the panes' glass
     * aims and dresses it.
     */
    public void showFrame(@NonNull Bitmap frame, @NonNull Rect frameRect, float parallaxPx,
                          @NonNull ColorFilter frost, int tint) {
        mScene = false;
        mFrame = frame;
        mFrameRect.set(frameRect);
        mParallaxPx = parallaxPx;
        show(frost, tint);
    }

    private void show(@NonNull ColorFilter frost, int tint) {
        mPaint.setColorFilter(frost);
        mTintPaint.setColor(tint);
        BackdropRecording.leaveOut(this);
        if (getVisibility() != VISIBLE) setVisibility(VISIBLE);
        invalidate();
    }

    /** Back to the plain ground: gone, nothing held, recorded as before. Idempotent. */
    public void hide() {
        BackdropRecording.keepIn(this);
        mFrame = null;
        mScene = false;
        mPaint.setColorFilter(null);
        mTintPaint.setColor(android.graphics.Color.TRANSPARENT);
        if (getVisibility() != GONE) setVisibility(GONE);
    }

    /** Whether the frost is on screen now. */
    public boolean shown() {
        return getVisibility() == VISIBLE;
    }

    @Override
    public void invalidateLive() {
        if (getVisibility() == VISIBLE) invalidate();
    }

    @Override
    protected void onDetachedFromWindow() {
        hide();
        super.onDetachedFromWindow();
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        Bitmap bitmap;
        Rect rect;
        if (mScene) {
            LiveSceneFrame.Frame live = LiveSceneFrame.current();
            if (live == null) return;
            bitmap = live.bitmap;
            rect = LiveSceneFrame.rect();
            // Drawn again at the next publish, as the glass beside it is.
            LiveSceneFrame.noteDrawn(this);
        } else {
            bitmap = mFrame;
            rect = mFrameRect;
        }
        if (bitmap == null || bitmap.isRecycled() || rect.isEmpty()) return;
        getLocationOnScreen(mAt);
        float left = rect.left - mAt[0] - mParallaxPx;
        float top = rect.top - mAt[1];
        mDest.set(left, top, left + rect.width(), top + rect.height());
        canvas.drawBitmap(bitmap, null, mDest, mPaint);
        // The glass's face: its tint over the frost, at the Terminal's Opacity (the alpha).
        if (android.graphics.Color.alpha(mTintPaint.getColor()) > 0)
            canvas.drawRect(mDest, mTintPaint);
    }
}
