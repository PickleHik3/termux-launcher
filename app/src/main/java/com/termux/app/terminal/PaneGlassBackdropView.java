package com.termux.app.terminal;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.BitmapShader;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Shader;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * One pane's glass. Draws the shared pre-blurred wallpaper frame through this pane's own rect, the
 * terminal tint over it, and the film grain on top — the same three layers the whole-terminal glass
 * pane uses, per pane, so a split window reads as several floating slabs rather than one sheet with
 * lines drawn on it.
 *
 * <p>The wallpaper frame is never cropped into a per-pane bitmap: panes resize on every split, drag
 * and keyboard toggle, and cropping would allocate a pane-sized ARGB_8888 bitmap each time. The one
 * cached frame is drawn through a translation matrix instead, recomputed only when this view's
 * position on screen actually changes.
 */
public final class PaneGlassBackdropView extends View {

    private final Paint mFramePaint = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final Paint mTintPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Matrix mFrameMatrix = new Matrix();
    private final int[] mLocation = new int[2];
    private final int[] mRootLocation = new int[2];
    private final Rect mFrameRect = new Rect();
    private final RectF mClipRect = new RectF();

    @Nullable private Bitmap mFrame;
    @Nullable private BitmapShader mFrameShader;
    @Nullable private Drawable mGrain;
    private int mTintColor;
    private float mRadiusPx;
    /** Non-zero while this view paints only the corner arcs of an opaque page. */
    private float mCornerMaskRadiusPx;
    /** Painted in the arcs when there is no frame to paint: a corner is never left open. */
    private int mCornerMaskFallbackColor = android.graphics.Color.TRANSPARENT;
    @Nullable private android.graphics.Path mCornerMaskPath;
    private int mCornerMaskWidth;
    private int mCornerMaskHeight;
    private float mCornerMaskPathRadius;
    private int mLastLeft = Integer.MIN_VALUE;
    private int mLastTop = Integer.MIN_VALUE;
    private int mLastWidth = -1;
    private int mLastHeight = -1;

    public PaneGlassBackdropView(@NonNull Context context) {
        this(context, null);
    }

    /** The inflated-from-XML constructor; the pane leaf layout declares this view. */
    public PaneGlassBackdropView(@NonNull Context context,
                                 @Nullable android.util.AttributeSet attrs) {
        super(context, attrs);
        setWillNotDraw(false);
    }

    /**
     * @param frame     shared pre-blurred wallpaper frame, or null for a tint-and-grain-only pane
     * @param frameRect that frame's rect in screen coordinates
     * @param grain     tiled grain layer, or null when grain is off
     */
    public void setGlass(@Nullable Bitmap frame, @NonNull Rect frameRect, int tintColor,
                         @Nullable Drawable grain, float radiusPx,
                         @Nullable ColorFilter frostFilter) {
        mFrame = frame != null && !frame.isRecycled() ? frame : null;
        // CLAMP, and drawn as a shader rather than as a bitmap: the cached frame does not always
        // reach the full width of the screen (it is downsampled for the blur, and on ROMs that
        // magnify the wallpaper it is captured against a compensated rect), and a plain drawBitmap
        // simply stopped where the image ran out — leaving a strip of sharp wallpaper down the
        // right edge of the pane. Clamping stretches the edge column over the shortfall, which is
        // how every other glass surface here already renders the same frame.
        mFrameShader = mFrame == null
            ? null : new BitmapShader(mFrame, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
        mFramePaint.setShader(mFrameShader);
        mFrameRect.set(frameRect);
        mTintColor = tintColor;
        mGrain = grain;
        mRadiusPx = radiusPx;
        mFramePaint.setColorFilter(frostFilter);
        mLastLeft = Integer.MIN_VALUE;   // force the matrix to be rebuilt against the new frame
        invalidate();
    }

    /** Recompute the frame matrix on the next draw; call after this pane has moved. */
    /**
     * Paint only the four corner arcs, and skip the tint and grain.
     *
     * <p>For a page whose content is an opaque surface the system composites outside the view
     * hierarchy — the X display — so no parent clip can round it. The arcs are painted over the
     * surface with what sits behind the page instead, which rounds the page's corners for the
     * same cost as any other draw.
     *
     * @param radiusPx the corner radius to cut, or 0 to go back to painting the whole slab
     */
    public void setCornerMaskRadius(float radiusPx) {
        float resolved = Math.max(0f, radiusPx);
        if (mCornerMaskRadiusPx == resolved) return;
        mCornerMaskRadiusPx = resolved;
        invalidate();
    }

    /** The colour the arcs take when no frame stands in for what is behind the page. */
    public void setCornerMaskFallbackColor(int color) {
        if (mCornerMaskFallbackColor == color) return;
        mCornerMaskFallbackColor = color;
        invalidate();
    }

    public void invalidateGlassPosition() {
        mLastLeft = Integer.MIN_VALUE;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int width = getWidth();
        int height = getHeight();
        if (width <= 0 || height <= 0) return;
        int save = canvas.save();
        if (mCornerMaskRadiusPx > 0f) {
            // Everything outside the rounded page, which is exactly the four arcs.
            canvas.clipPath(cornerMaskPath(width, height));
        } else if (mRadiusPx > 0f) {
            mClipRect.set(0f, 0f, width, height);
            canvas.clipRect(mClipRect);   // the round clip is the parent frame's; this bounds ours
        }
        if (mCornerMaskRadiusPx > 0f && (mFrame == null || mFrame.isRecycled())
            && android.graphics.Color.alpha(mCornerMaskFallbackColor) > 0) {
            mTintPaint.setColor(mCornerMaskFallbackColor);
            canvas.drawRect(0f, 0f, width, height, mTintPaint);
        }
        if (mFrame != null && !mFrame.isRecycled() && mFrameShader != null) {
            layoutOriginOnScreen(mLocation);
            if (mLocation[0] != mLastLeft || mLocation[1] != mLastTop
                || width != mLastWidth || height != mLastHeight) {
                mLastLeft = mLocation[0];
                mLastTop = mLocation[1];
                mLastWidth = width;
                mLastHeight = height;
                float scaleX = mFrameRect.width() / (float) Math.max(1, mFrame.getWidth());
                float scaleY = mFrameRect.height() / (float) Math.max(1, mFrame.getHeight());
                mFrameMatrix.reset();
                mFrameMatrix.setScale(scaleX, scaleY);
                mFrameMatrix.postTranslate(mFrameRect.left - mLastLeft, mFrameRect.top - mLastTop);
                mFrameShader.setLocalMatrix(mFrameMatrix);
            }
            canvas.drawRect(0f, 0f, width, height, mFramePaint);
        }
        // A corner mask stands in for what is behind the page, so it takes neither the pane
        // tint nor the grain: those belong to a pane's own slab, and the page has none.
        if (mCornerMaskRadiusPx <= 0f) {
            if (android.graphics.Color.alpha(mTintColor) > 0) {
                mTintPaint.setColor(mTintColor);
                canvas.drawRect(0f, 0f, width, height, mTintPaint);
            }
            if (mGrain != null) {
                mGrain.setBounds(0, 0, width, height);
                mGrain.draw(canvas);
            }
        }
        canvas.restoreToCount(save);
    }
    /**
     * This view's position on screen as laid out, ignoring every transform on the way up.
     *
     * <p>{@code getLocationOnScreen} answers with the transforms applied, and the pane frame is
     * transformed constantly — the plank tilts and slides it under a finger, and the FLIP movement
     * animates its translation. Pinning the frost to a transformed position baked the tilt's offset
     * into the matrix: the frost jumped when touched, then stayed shifted once the spring settled,
     * because no further position change ever arrived to correct it. Layout coordinates are the
     * frost's real anchor — the wallpaper does not move when a pane tips over it, and the frost
     * inside the pane then travels with the pane, which is what glass does.
     */
    // Package-private so the regression test can pin it directly.
    /** Rebuilt only when the size or the radius changes, never per draw. */
    @NonNull
    private android.graphics.Path cornerMaskPath(int width, int height) {
        if (mCornerMaskPath != null && mCornerMaskWidth == width && mCornerMaskHeight == height
            && mCornerMaskPathRadius == mCornerMaskRadiusPx) {
            return mCornerMaskPath;
        }
        float radius = PaneShape.radiusForBounds(mCornerMaskRadiusPx, width, height);
        android.graphics.Path path = mCornerMaskPath == null
            ? new android.graphics.Path() : mCornerMaskPath;
        path.reset();
        path.addRect(0f, 0f, width, height, android.graphics.Path.Direction.CW);
        path.addRoundRect(0f, 0f, width, height, radius, radius,
            android.graphics.Path.Direction.CCW);
        path.setFillType(android.graphics.Path.FillType.WINDING);
        mCornerMaskPath = path;
        mCornerMaskWidth = width;
        mCornerMaskHeight = height;
        mCornerMaskPathRadius = mCornerMaskRadiusPx;
        return path;
    }

    void layoutOriginOnScreen(@NonNull int[] out) {
        float x = 0f;
        float y = 0f;
        View view = this;
        while (true) {
            x += view.getLeft();
            y += view.getTop();
            android.view.ViewParent parent = view.getParent();
            if (!(parent instanceof View)) break;
            View parentView = (View) parent;
            x -= parentView.getScrollX();
            y -= parentView.getScrollY();
            view = parentView;
        }
        // `view` is now the root of this hierarchy; it carries no transform of its own, so asking
        // the framework for its screen position is safe.
        view.getLocationOnScreen(mRootLocation);
        out[0] = Math.round(x) + mRootLocation[0];
        out[1] = Math.round(y) + mRootLocation[1];
    }

}
