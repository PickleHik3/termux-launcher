package com.termux.app.chrome;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.ColorSpace;
import android.graphics.HardwareRenderer;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.RecordingCanvas;
import android.graphics.RenderEffect;
import android.graphics.RenderNode;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.hardware.HardwareBuffer;
import android.media.Image;
import android.media.ImageReader;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.ImageView;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

import com.termux.R;

/**
 * The picture behind a panel's glass (the app drawer, the command palette, the terminal sheet, a
 * popup menu): what the window draws beneath the panel, from {@link LiveBackdrop}'s recorder, held
 * by the panel's backdrop {@link ImageView}. {@link PanelBackdropPolicy} picks the kind:
 *
 * <ul>
 *   <li><b>Live</b> (Live blur on): the recorder's {@link LivePicture}, refreshed on frames where
 *   something beneath changed.</li>
 *   <li><b>Snapshot</b> (Live blur off): the same recording blurred once into a GPU bitmap on its
 *   own {@link HardwareRenderer} when the panel opens and when it comes to rest. While it moves
 *   nothing is recorded or rendered: the bitmap stands. The UI thread only records (it reuses the
 *   views' display lists) and hands the frame over; the blur runs on the render thread and the
 *   bitmap arrives on a later frame.</li>
 *   <li><b>Stored</b>: {@link #open} answers false and the controller shows the stored
 *   pre-blurred wallpaper frame ({@link WallpaperFrostPainter}) instead.</li>
 * </ul>
 *
 * <p>A controller calls {@link #open} on each open, {@link #setMoving} as its motion starts and
 * stops, and {@link #close} once shut, which gives back every layer and buffer: a shut panel
 * holds no pixels.</p>
 */
public final class PanelBackdrop {

    /** The app drawer plane's blur, as its {@code RealtimeBlurView} had it. */
    public static final float DRAWER_BLUR_RADIUS_DP = 10f;
    /** The command palette's and the terminal sheet's blur, as their {@code RealtimeBlurView}s had it. */
    public static final float PROMPT_BLUR_RADIUS_DP = 18f;

    /** The activity's recorder and switch, found through the decor view ({@link #install}). */
    public interface Source {
        /** The window's recorder, made on first use; null when the window has no root for it. */
        @Nullable LiveBackdrop recorder();

        /** {@link LiveBlurPolicy#live} as it stands now. */
        boolean liveBlur();
    }

    /**
     * Puts the scene a Snapshot should show in place for the length of one recording, and puts
     * the shown frame back after it: the drawer records the settled-open scene from any frame.
     */
    public interface Scene {
        void beforeSnapshot();

        void afterSnapshot();
    }

    /** Makes {@code source} the backdrop source of every panel in {@code decor}'s window and its popups. */
    public static void install(@NonNull View decor, @Nullable Source source) {
        decor.setTag(R.id.panel_backdrop_source, source);
    }

    /** Images the snapshot reader may hold: the one shown, the one being drawn, one spare. */
    private static final int MAX_IMAGES = 3;

    @Nullable private static Handler sMainHandler;

    @NonNull private final ImageView mSurface;
    @NonNull private final View mAnchor;
    @Nullable private Scene mScene;
    @ColorInt private int mVeilColor;
    @Nullable private ColorFilter mVeil;
    private float mRadiusPx;

    /** The kind of the open panel; null while shut. */
    @Nullable private PanelBackdropPolicy.Kind mKind;
    private boolean mMoving;
    @Nullable private LiveBackdrop mRecorder;
    @Nullable private Object mSnapshot;
    private boolean mWaitingForLayout;
    @NonNull private final View.OnLayoutChangeListener mLayoutWait =
        (v, l, t, r, b, ol, ot, or, ob) -> {
            if (v.getWidth() <= 0 || v.getHeight() <= 0) return;
            stopWaitingForLayout();
            takeSnapshot();
        };

    /**
     * @param surface the panel's backdrop view: the lowest view of its glass, so nothing of the
     *                panel is beneath it and nothing of the panel is in its picture
     * @param anchor  a view in the launcher's window ({@code surface} itself unless the panel is a
     *                popup in a window of its own)
     */
    public PanelBackdrop(@NonNull ImageView surface, @NonNull View anchor) {
        mSurface = surface;
        mAnchor = anchor;
    }

    public void setScene(@Nullable Scene scene) {
        mScene = scene;
    }

    /**
     * A colour laid over the picture, as a dim the panel's old blur recorded beneath it (the
     * drawer's scene dim, which is its host's background and so is never in a recording).
     * Transparent for none.
     */
    public void setVeil(@ColorInt int color) {
        if (color == mVeilColor) return;
        mVeilColor = color;
        mVeil = Color.alpha(color) == 0 ? null
            : new PorterDuffColorFilter(color, PorterDuff.Mode.SRC_OVER);
        if (mKind != null && mKind != PanelBackdropPolicy.Kind.STORED) mSurface.setColorFilter(mVeil);
    }

    /** Whether the panel shows a recorded picture now (Live or Snapshot). */
    public boolean isOpen() {
        return mKind != null && mKind != PanelBackdropPolicy.Kind.STORED;
    }

    /** The kind the panel opened with; null while shut. */
    @Nullable
    public PanelBackdropPolicy.Kind kind() {
        return mKind;
    }

    /**
     * Opens the panel's picture at {@code blurRadiusPx} (its old {@code RealtimeBlurView} radius)
     * and starts it moving. False when the panel shows the stored frame instead.
     */
    public boolean open(float blurRadiusPx) {
        mRadiusPx = Math.max(0f, blurRadiusPx);
        Source source = source();
        LiveBackdrop recorder = source == null ? null : source.recorder();
        boolean sameWindow = recorder != null
            && mSurface.getRootView() == recorder.root().getRootView();
        PanelBackdropPolicy.Kind kind = PanelBackdropPolicy.kind(Build.VERSION.SDK_INT,
            recorder != null, source != null && source.liveBlur(), sameWindow,
            recorder != null && recorder.size() < LiveBackdrop.MAX_LIVE_SURFACES);
        if (kind != mKind || recorder != mRecorder) drop();
        mRecorder = recorder;
        mMoving = true;
        if (kind == PanelBackdropPolicy.Kind.LIVE && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            LivePicture picture = recorder.attach(mSurface, mRadiusPx, true);
            if (picture != null) {
                mKind = kind;
                show(picture.drawable());
                return true;
            }
            kind = PanelBackdropPolicy.Kind.SNAPSHOT;
        }
        mKind = kind;
        if (kind == PanelBackdropPolicy.Kind.STORED || Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            mKind = PanelBackdropPolicy.Kind.STORED;
            mSurface.setImageDrawable(null);
            return false;
        }
        show(snapshot().mDrawable);
        if (PanelBackdropPolicy.snapshotOnOpen(kind)) takeSnapshot();
        return true;
    }

    /** The panel's motion started or stopped; a Snapshot is retaken when it comes to rest. */
    public void setMoving(boolean moving) {
        boolean was = mMoving;
        mMoving = moving;
        if (mKind != null && PanelBackdropPolicy.snapshotOnMotion(mKind, true, was, moving)) {
            takeSnapshot();
        }
    }

    /** Shut: the picture, its layer and its buffers go. */
    public void close() {
        drop();
        mKind = null;
        mRecorder = null;
    }

    @Nullable
    private Source source() {
        Object tag = mAnchor.getRootView().getTag(R.id.panel_backdrop_source);
        return tag instanceof Source ? (Source) tag : null;
    }

    private void show(@NonNull Drawable drawable) {
        if (mSurface.getDrawable() != drawable) mSurface.setImageDrawable(drawable);
        mSurface.setColorFilter(mVeil);
    }

    /** Lets go of whatever picture the surface shows. */
    private void drop() {
        stopWaitingForLayout();
        if (mKind == PanelBackdropPolicy.Kind.LIVE && mRecorder != null) mRecorder.detach(mSurface);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && mSnapshot != null) {
            ((Snapshot) mSnapshot).release();
        }
        if (mSurface.getDrawable() != null) mSurface.setImageDrawable(null);
    }

    private void takeSnapshot() {
        if (mKind != PanelBackdropPolicy.Kind.SNAPSHOT || mRecorder == null
            || Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return;
        if (!mSurface.isAttachedToWindow() || mSurface.getWidth() <= 0 || mSurface.getHeight() <= 0) {
            // A popup not laid out yet: the snapshot waits for its first layout.
            if (!mWaitingForLayout) {
                mWaitingForLayout = true;
                mSurface.addOnLayoutChangeListener(mLayoutWait);
            }
            return;
        }
        snapshot().request();
    }

    private void stopWaitingForLayout() {
        if (!mWaitingForLayout) return;
        mWaitingForLayout = false;
        mSurface.removeOnLayoutChangeListener(mLayoutWait);
    }

    @RequiresApi(Build.VERSION_CODES.S)
    @NonNull
    private Snapshot snapshot() {
        if (mSnapshot == null) mSnapshot = new Snapshot();
        return (Snapshot) mSnapshot;
    }

    @NonNull
    private static Handler mainHandler() {
        if (sMainHandler == null) sMainHandler = new Handler(Looper.getMainLooper());
        return sMainHandler;
    }

    /**
     * One panel's Snapshot: the recording drawn at a quarter scale through the blur into an
     * {@link ImageReader}'s buffer on the render thread, then shown as a hardware bitmap. At most
     * one render is in flight; a request while one is becomes one more render after it lands.
     */
    @RequiresApi(Build.VERSION_CODES.S)
    private final class Snapshot implements ImageReader.OnImageAvailableListener {
        @NonNull private final RenderNode mRecord = new RenderNode("PanelBackdrop.record");
        @NonNull private final RenderNode mBlur = new RenderNode("PanelBackdrop.blur");
        @NonNull private final RenderNode mContent = new RenderNode("PanelBackdrop.snapshot");
        @NonNull final SnapshotDrawable mDrawable = new SnapshotDrawable();
        @Nullable private HardwareRenderer mRenderer;
        @Nullable private ImageReader mReader;
        @Nullable private Image mHeld;
        private int mWidth = -1;
        private int mHeight = -1;
        private float mEffectRadius = -1f;
        private int mMarginInFlight;
        private boolean mInFlight;
        private boolean mPending;

        void request() {
            LiveBackdrop recorder = mRecorder;
            if (recorder == null) return;
            if (mInFlight) {
                mPending = true;
                return;
            }
            int margin = Math.max(1, (int) Math.ceil(mRadiusPx));
            boolean sameWindow = mSurface.getRootView() == recorder.root().getRootView();
            Scene scene = mScene;
            if (scene != null) scene.beforeSnapshot();
            try {
                boolean recorded = sameWindow ? recorder.recordBelow(mSurface, mRecord, margin)
                    : recorder.recordWindowBeneath(mSurface, mRecord, margin);
                if (!recorded) return;
                int w = Math.max(1, (mRecord.getWidth() + LivePicture.SCALE - 1) / LivePicture.SCALE);
                int h = Math.max(1, (mRecord.getHeight() + LivePicture.SCALE - 1) / LivePicture.SCALE);
                if (!ensureTarget(w, h)) return;
                float r = LivePicture.panelQuarterRadius(mRadiusPx);
                if (r != mEffectRadius) {
                    mEffectRadius = r;
                    mBlur.setRenderEffect(r > 0f
                        ? RenderEffect.createBlurEffect(r, r, Shader.TileMode.CLAMP) : null);
                }
                mBlur.setPosition(0, 0, w, h);
                RecordingCanvas canvas = mBlur.beginRecording(w, h);
                try {
                    canvas.scale(1f / LivePicture.SCALE, 1f / LivePicture.SCALE);
                    canvas.drawRenderNode(mRecord);
                } finally {
                    mBlur.endRecording();
                }
                mContent.setPosition(0, 0, w, h);
                canvas = mContent.beginRecording(w, h);
                try {
                    canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);
                    canvas.drawRenderNode(mBlur);
                } finally {
                    mContent.endRecording();
                }
                // Syncs inside the scene, so the render sees the scene's properties; returns once
                // synced, and the render thread draws on its own.
                mRenderer.createRenderRequest().syncAndDraw();
                mMarginInFlight = margin;
                mInFlight = true;
            } finally {
                if (scene != null) scene.afterSnapshot();
            }
        }

        /** The reader and renderer for a {@code w} x {@code h} snapshot; made again only on a new size. */
        private boolean ensureTarget(int w, int h) {
            if (mReader != null && mRenderer != null && w == mWidth && h == mHeight) return true;
            releaseTarget();
            try {
                mReader = ImageReader.newInstance(w, h, PixelFormat.RGBA_8888, MAX_IMAGES,
                    HardwareBuffer.USAGE_GPU_SAMPLED_IMAGE | HardwareBuffer.USAGE_GPU_COLOR_OUTPUT);
                mReader.setOnImageAvailableListener(this, mainHandler());
                mRenderer = new HardwareRenderer();
                mRenderer.setContentRoot(mContent);
                mRenderer.setSurface(mReader.getSurface());
            } catch (RuntimeException e) {
                releaseTarget();
                return false;
            }
            mWidth = w;
            mHeight = h;
            return true;
        }

        @Override
        public void onImageAvailable(ImageReader reader) {
            Image image;
            try {
                image = reader.acquireLatestImage();
            } catch (RuntimeException e) {
                image = null;
            }
            if (reader != mReader) {
                // A reader let go of while its frame was drawn.
                if (image != null) image.close();
                return;
            }
            mInFlight = false;
            if (image != null) {
                HardwareBuffer buffer = image.getHardwareBuffer();
                Bitmap bitmap = buffer == null ? null
                    : Bitmap.wrapHardwareBuffer(buffer, ColorSpace.get(ColorSpace.Named.SRGB));
                if (buffer != null) buffer.close();
                if (bitmap == null) {
                    image.close();
                } else {
                    Image old = mHeld;
                    mHeld = image;
                    mDrawable.show(bitmap, mMarginInFlight);
                    // One frame, for the new picture; the surface draws nothing else.
                    mSurface.invalidate();
                    if (old != null) old.close();
                }
            }
            if (mPending) {
                mPending = false;
                request();
            }
        }

        private void releaseTarget() {
            mDrawable.show(null, 0);
            if (mHeld != null) {
                mHeld.close();
                mHeld = null;
            }
            if (mRenderer != null) {
                mRenderer.destroy();
                mRenderer = null;
            }
            if (mReader != null) {
                mReader.close();
                mReader = null;
            }
            mWidth = -1;
            mHeight = -1;
            mInFlight = false;
            mPending = false;
        }

        void release() {
            releaseTarget();
            mRecord.discardDisplayList();
            mBlur.discardDisplayList();
            mContent.discardDisplayList();
        }
    }

    /** Draws the quarter-scale snapshot back at four times, its margin outside the surface. */
    private final class SnapshotDrawable extends Drawable {
        @NonNull private final Paint mPaint = new Paint(Paint.FILTER_BITMAP_FLAG);
        @Nullable private Bitmap mBitmap;
        private int mMargin;

        void show(@Nullable Bitmap bitmap, int marginPx) {
            mBitmap = bitmap;
            mMargin = marginPx;
        }

        @Override
        public void draw(@NonNull Canvas canvas) {
            Bitmap bitmap = mBitmap;
            // A hardware bitmap cannot be drawn by a software canvas (a screenshot of the view).
            if (bitmap == null || !canvas.isHardwareAccelerated()) return;
            int save = canvas.save();
            // The holder (an ImageView) has already moved the canvas to its content box.
            canvas.translate(-mMargin - mSurface.getPaddingLeft(), -mMargin - mSurface.getPaddingTop());
            canvas.scale(LivePicture.SCALE, LivePicture.SCALE);
            canvas.drawBitmap(bitmap, 0f, 0f, mPaint);
            canvas.restoreToCount(save);
        }

        @Override
        public void setAlpha(int alpha) {
            mPaint.setAlpha(alpha);
        }

        @Override
        public void setColorFilter(@Nullable ColorFilter colorFilter) {
            mPaint.setColorFilter(colorFilter);
        }

        @Override
        public int getOpacity() {
            return PixelFormat.TRANSLUCENT;
        }
    }
}
