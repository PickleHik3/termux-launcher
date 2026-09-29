package com.termux.app.chrome;

import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Objects;

/**
 * A glass surface's view onto the shared pre-blurred wallpaper frame: the frame is sampled, on
 * every draw, at wherever the surface is on screen right then, shifted by the wallpaper's live
 * parallax offset. Nothing is ever cut out of the frame.
 *
 * <p>This replaces the per-surface crops the dock, the keyboard, the under-pill strip, the top
 * bars, the palette, the sheet and the drawer plane used to hold. A crop is a picture of where
 * the surface was when it was cut: cut while the stack travelled it landed at the translated
 * position for the rest of the slide, cut wider for the parallax it still had to be re-cut on
 * every geometry change, each one a main-thread bitmap the size of the surface, swapped in with
 * no fade. Here the frame is drawn through a {@link BitmapShader} whose matrix is re-aimed per
 * draw from the surface's {@link GlassAnchor.Origin}, which is exactly what the pane slabs
 * already did — every surface now reads the same frame the same way, so none can drift.</p>
 *
 * <p>The frame is stored at the resolution it was blurred at, so the matrix scales it up to the
 * rect it was captured for; a wallpaper change's replacement fades in over the frame it
 * displaced ({@link FrameCrossfade}) by drawing both, never by compositing a third bitmap. The
 * bitmaps are the cache's own and are never recycled here: {@link #shows} lets the in-use scan
 * find them so the cache leaves them alone while they are on screen.</p>
 *
 * <p>With Fancier Glass on ({@link #setRefraction}), the same aim goes to
 * {@link GlassRefraction} as uniforms and the frame is drawn through its program instead, bent
 * at the rim and lit along it, in the one pass the plain draw already took.</p>
 *
 * <p>The drawable reports no intrinsic size. Every host is a {@code fitXY} {@code ImageView} or
 * a background, both of which size the drawable to the view, and neither host pads, so drawable
 * coordinates are the view's own — which is what the anchor's origin describes.</p>
 */
public final class SharedFrameDrawable extends Drawable {

    @NonNull private final Paint mPaint = new Paint(Paint.FILTER_BITMAP_FLAG);
    @NonNull private final Paint mPreviousPaint = new Paint(Paint.FILTER_BITMAP_FLAG);
    @NonNull private final Matrix mMatrix = new Matrix();
    @NonNull private final float[] mAim = new float[4];
    @NonNull private final float[] mRim = new float[4];
    @NonNull private final Rect mFrameRect = new Rect();
    @NonNull private final int[] mOrigin = new int[2];
    @NonNull private final FrameCrossfade mCrossfade = new FrameCrossfade();
    @NonNull private final GlassAnchor.Origin mAnchor;
    @Nullable private final WallpaperParallax mParallax;

    @Nullable private Bitmap mFrame;
    @Nullable private BitmapShader mShader;
    /** The frame {@link #mFrame} is fading in from; held, not recycled, until the fade lands. */
    @Nullable private Bitmap mPreviousFrame;
    @Nullable private BitmapShader mPreviousShader;
    private int mAlpha = 255;

    /** Fancier Glass, or null for the plain draw. */
    @Nullable private GlassRefraction.Look mLook;
    private float mLookDensity;
    private float mRimRadiusPx;
    private int mSeams;
    /** The program the frame draws through while {@link #mLook} is set and the phone runs one. */
    @Nullable private GlassRefraction.Program mProgram;
    @Nullable private GlassRefraction.Program mPreviousProgram;

    /**
     * @param frame     a resident frame of the blur cache, at whatever resolution it holds it
     * @param frameRect the screen rect that frame was captured for
     * @param parallax  the wallpaper's live offset, or null while nothing pans
     * @param anchor    where this surface samples from; see {@link GlassAnchor}
     */
    public SharedFrameDrawable(@NonNull Bitmap frame, @NonNull Rect frameRect,
                               @Nullable WallpaperParallax parallax,
                               @NonNull GlassAnchor.Origin anchor) {
        mAnchor = anchor;
        mParallax = parallax;
        setFrame(frame, frameRect, false);
    }

    /**
     * Points this surface at another frame — the same radius re-blurred for a new wallpaper, or a
     * rotation's new capture. With {@code crossfade}, and only while the new frame was captured
     * for the very rect the old one was (a wallpaper change, never a rotation), the old one stays
     * underneath and the new one fades in over {@link FrameCrossfade#DURATION_MS}.
     */
    public void setFrame(@NonNull Bitmap frame, @NonNull Rect frameRect, boolean crossfade) {
        if (frame == mFrame && mFrameRect.equals(frameRect)) return;
        if (crossfade && mFrame != null && mFrame != frame && !mFrame.isRecycled()
            && mFrameRect.equals(frameRect)) {
            mPreviousFrame = mFrame;
            mPreviousShader = mShader;
            mCrossfade.start();
        } else {
            mPreviousFrame = null;
            mPreviousShader = null;
            mCrossfade.cancel();
        }
        mFrame = frame;
        // CLAMP: the frame may fall a pixel short of its rect once it has been scaled down for
        // the blur and back up here; the edge column is stretched over the shortfall rather than
        // showing a strip of nothing, as the pane slabs already draw it.
        mShader = new BitmapShader(frame, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
        mFrameRect.set(frameRect);
        syncRefraction();
        invalidateSelf();
    }

    /**
     * Fancier Glass on this surface: the frame is bent under the rim and lit along it, through
     * {@link GlassRefraction}. Null puts the plain draw back — the default mode, and what every
     * phone below API 33 draws whatever it is handed. Idempotent, so every pass may restate it.
     *
     * @param density        the screen's density, which converts the look's dp
     * @param cornerRadiusPx the rounded rect the rim follows: the surface's own corner
     * @param seams          {@link GlassRefraction#SEAM_TOP} and friends: edges another glass
     *                       surface continues past, which take no rim
     */
    public void setRefraction(@Nullable GlassRefraction.Look look, float density,
                              float cornerRadiusPx, int seams) {
        if (Objects.equals(look, mLook) && mLookDensity == density
            && mRimRadiusPx == cornerRadiusPx && mSeams == seams) return;
        mLook = look;
        mLookDensity = density;
        mRimRadiusPx = cornerRadiusPx;
        mSeams = seams;
        syncRefraction();
        invalidateSelf();
    }

    /** True while the frame is drawn through the refraction program rather than plain. */
    public boolean refracts() {
        return mProgram != null;
    }

    /**
     * Points the paints at the program or at the frame's own shader, whichever the look asks for
     * and the phone can run. On every frame change and look change, never per draw: the program
     * captures its input when it is set.
     */
    private void syncRefraction() {
        GlassRefraction.Look look = mLook;
        BitmapShader shader = mShader;
        if (look == null || shader == null || !GlassRefraction.available()) {
            mProgram = null;
            mPreviousProgram = null;
            mPaint.setShader(shader);
            return;
        }
        GlassRefraction.Program program = mProgram;
        if (program == null || program.density() != mLookDensity) {
            program = GlassRefraction.Program.create(mLookDensity);
        }
        mProgram = program;
        if (program == null) {
            // The driver refused the program: the plain draw is the look this phone gets.
            mPreviousProgram = null;
            mPaint.setShader(shader);
            return;
        }
        program.setLook(look);
        program.setInput(shader);
        program.applyTo(mPaint);
        syncRimRect();
        BitmapShader previousShader = mPreviousShader;
        if (previousShader == null) {
            mPreviousProgram = null;
            return;
        }
        GlassRefraction.Program previous = mPreviousProgram;
        if (previous == null || previous.density() != mLookDensity) {
            previous = GlassRefraction.Program.create(mLookDensity);
        }
        mPreviousProgram = previous;
        if (previous != null) {
            previous.setLook(look);
            previous.setInput(previousShader);
            previous.applyTo(mPreviousPaint);
        }
    }

    /** The rim follows the bounds, pushed past them on every seam side; see {@link GlassRefraction#rimRect}. */
    private void syncRimRect() {
        GlassRefraction.Look look = mLook;
        if (look == null) return;
        GlassRefraction.rimRect(mRim, getBounds(),
            GlassRefraction.seamReachPx(look, mLookDensity, mRimRadiusPx), mSeams);
    }

    @Override
    protected void onBoundsChange(@NonNull Rect bounds) {
        super.onBoundsChange(bounds);
        syncRimRect();
    }

    /** The frame this surface draws. */
    @NonNull
    public Bitmap frame() {
        Bitmap frame = mFrame;
        if (frame == null) throw new IllegalStateException("a shared frame drawable always holds a frame");
        return frame;
    }

    /** The screen rect the frame was captured for. Read only. */
    @NonNull
    public Rect frameRect() {
        return mFrameRect;
    }

    /** True while {@code bitmap} is on screen through this drawable: the frame, or one still fading out. */
    public boolean shows(@Nullable Bitmap bitmap) {
        if (bitmap == null) return false;
        if (bitmap == mFrame) return true;
        return bitmap == mPreviousFrame && !mCrossfade.isFinished();
    }

    /**
     * The shared-frame drawable a view holds, looking inside a {@link LayerDrawable} for the
     * keyboard's stacked material; null when the view draws something else.
     */
    @Nullable
    public static SharedFrameDrawable of(@Nullable Drawable drawable) {
        if (drawable instanceof SharedFrameDrawable) return (SharedFrameDrawable) drawable;
        if (drawable instanceof LayerDrawable) {
            LayerDrawable layers = (LayerDrawable) drawable;
            for (int i = 0; i < layers.getNumberOfLayers(); i++) {
                SharedFrameDrawable found = of(layers.getDrawable(i));
                if (found != null) return found;
            }
        }
        return null;
    }

    @Override
    public void draw(@NonNull Canvas canvas) {
        Rect bounds = getBounds();
        Bitmap frame = mFrame;
        BitmapShader shader = mShader;
        if (bounds.isEmpty() || frame == null || frame.isRecycled() || shader == null) return;
        mAnchor.originOnScreen(mOrigin);
        float offsetPx = mParallax == null ? 0f : mParallax.offsetPx();
        GlassRefraction.Program program = mProgram;
        // A software canvas refuses a RuntimeShader outright: a RealtimeBlurView drawing the
        // window into its bitmap is one. That pass only feeds another view's blur, so the frame
        // is left out of it rather than drawn and thrown.
        if (program != null && !canvas.isHardwareAccelerated()) return;
        aim(mAim, mFrameRect, frame.getWidth(), frame.getHeight(), mOrigin[0], mOrigin[1],
            offsetPx);
        if (program != null) {
            // The same aim, as uniforms: the program samples the frame by its own pixels.
            program.setAim(mAim[0], mAim[1], mAim[2], mAim[3]);
            program.setRect(mRim[0], mRim[1], mRim[2], mRim[3], mRimRadiusPx);
        } else {
            matrixOf(mMatrix, mAim);
            shader.setLocalMatrix(mMatrix);
        }
        float progress = mCrossfade.progress();
        Bitmap previous = mPreviousFrame;
        BitmapShader previousShader = mPreviousShader;
        boolean fading = progress < 1f && previous != null && !previous.isRecycled()
            && previousShader != null;
        if (fading) {
            // The retired frame shares this rect; only its own pixel size may differ.
            aim(mAim, mFrameRect, previous.getWidth(), previous.getHeight(), mOrigin[0],
                mOrigin[1], offsetPx);
            GlassRefraction.Program previousProgram = program == null ? null : mPreviousProgram;
            if (previousProgram != null) {
                previousProgram.setAim(mAim[0], mAim[1], mAim[2], mAim[3]);
                previousProgram.setRect(mRim[0], mRim[1], mRim[2], mRim[3], mRimRadiusPx);
            } else {
                matrixOf(mMatrix, mAim);
                previousShader.setLocalMatrix(mMatrix);
                mPreviousPaint.setShader(previousShader);
            }
            mPreviousPaint.setAlpha(mAlpha);
            canvas.drawRect(bounds, mPreviousPaint);
            mPaint.setAlpha(Math.round(mAlpha * progress));
            canvas.drawRect(bounds, mPaint);
            invalidateSelf();
        } else {
            mPreviousFrame = null;
            mPreviousShader = null;
            mPreviousProgram = null;
            mPaint.setAlpha(mAlpha);
            canvas.drawRect(bounds, mPaint);
        }
    }

    /**
     * The aim that puts frame pixel {@code (0, 0)} where the frame's rect begins on screen, seen
     * from a surface whose own origin is {@code (originX, originY)}: the frame is scaled from its
     * stored size up to the rect, then moved so that this surface's local {@code (0, 0)} samples
     * the frame at {@code origin - rect + offset}. The parallax carries the wallpaper left under
     * the surface, so the sample moves right by the same amount.
     *
     * @param out {@code {scaleX, scaleY, translateX, translateY}}: local = frame * scale + translate
     */
    static void aim(@NonNull float[] out, @NonNull Rect frameRect, int frameWidth, int frameHeight,
                    int originX, int originY, float offsetPx) {
        out[0] = frameRect.width() / (float) Math.max(1, frameWidth);
        out[1] = frameRect.height() / (float) Math.max(1, frameHeight);
        out[2] = frameRect.left - originX - offsetPx;
        out[3] = frameRect.top - originY;
    }

    /** {@link #aim(float[], Rect, int, int, int, int, float)} as the shader matrix the plain draw sets. */
    static void aim(@NonNull Matrix out, @NonNull Rect frameRect, int frameWidth, int frameHeight,
                    int originX, int originY, float offsetPx) {
        float[] aim = new float[4];
        aim(aim, frameRect, frameWidth, frameHeight, originX, originY, offsetPx);
        matrixOf(out, aim);
    }

    private static void matrixOf(@NonNull Matrix out, @NonNull float[] aim) {
        out.setScale(aim[0], aim[1]);
        out.postTranslate(aim[2], aim[3]);
    }

    @Override
    public void setAlpha(int alpha) {
        if (mAlpha == alpha) return;
        mAlpha = alpha;
        invalidateSelf();
    }

    @Override
    public int getAlpha() {
        return mAlpha;
    }

    @Override
    public void setColorFilter(@Nullable ColorFilter colorFilter) {
        mPaint.setColorFilter(colorFilter);
        mPreviousPaint.setColorFilter(colorFilter);
        invalidateSelf();
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }
}
