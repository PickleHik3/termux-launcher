package com.termux.app.place;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorSpace;
import android.graphics.HardwareRenderer;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RecordingCanvas;
import android.graphics.RenderNode;
import android.hardware.HardwareBuffer;
import android.media.Image;
import android.media.ImageReader;
import android.os.Build;
import android.os.Trace;
import android.util.DisplayMetrics;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

import com.termux.shared.logger.Logger;

/**
 * The committed half's accessory stack, pictured as it stood when a crossing began, for a crossing
 * that stands it in for the stack ({@link SeamCrossing#wantsPicture}): the stack itself then wears
 * and stands the other half's rows from the first frame and rises over the whole way while this
 * picture sinks over the whole way, in step with the wall. One stack cannot wear both halves at
 * once; a picture of the one leaving can.
 *
 * <p>The picture is taken from the stack's own drawing, rendered offscreen through the GPU as the
 * window draws it — its glass, rows, keyboard and beads, and nothing behind it — so where it
 * stands at rest it is the stack, pixel for pixel. It is a view of the stack's parent, standing
 * just under the stack, the width of the parent; it only ever moves by its translation and is cut
 * at the stack's laid-out bottom as the stack's own travel is, so nothing it draws crosses the
 * navigation area on the way down.
 *
 * <p>It holds one GPU buffer the size of the stack, from the crossing's first frame to its landing,
 * and none otherwise. Android 10 and up; below that no picture is taken and the crossing dips as
 * it always did.
 */
public final class StackPicture extends View {

    private static final String LOG_TAG = "StackPicture";

    /** How far above the stack the picture reaches, for the glow drawn over its top edge. */
    private static final float MARGIN_TOP_DP = 24f;

    /** Light the way the window's renderer does, so the stack's own shadows come out the same. */
    private static final float LIGHT_Z_DP = 600f;
    private static final float LIGHT_RADIUS_DP = 800f;
    private static final float AMBIENT_SHADOW_ALPHA = 0.039f;
    private static final float SPOT_SHADOW_ALPHA = 0.19f;

    @Nullable private Bitmap mBitmap;
    @Nullable private Image mImage;
    @Nullable private ImageReader mReader;
    /** Where the stack's top-left stood in this view, as pictured. */
    private float mStackX;
    private float mStackY;
    /** The stack's height as pictured: how far the picture sinks to be gone. */
    private int mStackHeight;
    private int mMarginTopPx;
    private float mSinkPx = Float.NaN;
    private final Rect mClip = new Rect();
    private final int[] mHere = new int[2];
    private final int[] mThere = new int[2];

    public StackPicture(@NonNull Context context) {
        super(context);
        setWillNotDraw(false);
        setClickable(false);
        setFocusable(false);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        setVisibility(INVISIBLE);
    }

    /**
     * Pictures {@code stack} as it is drawn now. Shows nothing yet ({@link #reveal}). Whether the
     * picture was taken: never below Android 10, nor for a stack not laid out and drawn.
     */
    public boolean capture(@NonNull View stack) {
        release();
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return false;
        if (!isAttachedToWindow() || !stack.isAttachedToWindow() || !stack.isHardwareAccelerated()
            || stack.getVisibility() != VISIBLE || stack.getWidth() <= 0
            || stack.getHeight() <= 0)
            return false;
        Trace.beginSection("Wall.stackPicture");
        try {
            return Api29.capture(this, stack);
        } catch (Throwable t) {
            Logger.logError(LOG_TAG, "stack picture skipped: " + t);
            release();
            return false;
        } finally {
            Trace.endSection();
        }
    }

    /** Whether a picture is held. */
    public boolean holds() {
        return mBitmap != null;
    }

    /**
     * The picture with {@code reveal} of it showing: at the stack's own place at 1, sunk below
     * the stack's laid-out bottom by the rest, gone at 0.
     */
    public void reveal(float reveal) {
        if (mBitmap == null) return;
        float r = reveal < 0f ? 0f : reveal > 1f ? 1f : reveal;
        float sink = mStackHeight * (1f - r);
        if (sink == mSinkPx && getVisibility() == VISIBLE) return;
        mSinkPx = sink;
        setTranslationY(sink);
        // Cut at the stack's laid-out bottom, which stays on the screen while the picture moves.
        int bottom = Math.round(mStackY + mStackHeight - sink);
        // Wide and tall enough on every other side whether or not this view is laid out yet: the
        // first crossing adds it in the frame it is first shown.
        int reach = Math.max(Math.max(getWidth(), mBitmap.getWidth()), Math.round(mStackX))
            + mBitmap.getWidth();
        mClip.set(-reach, -Math.max(bottom, mBitmap.getHeight()) - reach, 2 * reach,
            Math.max(0, bottom));
        setClipBounds(mClip);
        if (getVisibility() != VISIBLE) setVisibility(VISIBLE);
    }

    /**
     * How far the picture's top edge, where it stands now, reaches up past {@code roomBottomPx},
     * a bottom edge in this view's own coordinates: what a pane standing on that edge is covered
     * by. 0 while nothing is pictured or shown.
     */
    public int coverOver(float roomBottomPx) {
        if (mBitmap == null || getVisibility() != VISIBLE || Float.isNaN(mSinkPx)) return 0;
        float top = mStackY + mSinkPx;
        return Math.max(0, Math.round(roomBottomPx - top));
    }

    /**
     * Where {@code view}'s bottom edge stands in this view's coordinates, translations included:
     * the room's bottom a {@link #coverOver} is measured against.
     */
    public float bottomOf(@NonNull View view) {
        getLocationInWindow(mHere);
        view.getLocationInWindow(mThere);
        return mThere[1] - mHere[1] + getTranslationY() + view.getHeight();
    }

    /** The picture goes, and the buffer it held with it. */
    public void release() {
        Bitmap bitmap = mBitmap;
        Image image = mImage;
        ImageReader reader = mReader;
        mBitmap = null;
        mImage = null;
        mReader = null;
        mSinkPx = Float.NaN;
        if (getVisibility() != INVISIBLE) setVisibility(INVISIBLE);
        if (getTranslationY() != 0f) setTranslationY(0f);
        if (getClipBounds() != null) setClipBounds(null);
        if (bitmap != null) bitmap.recycle();
        if (image != null) image.close();
        if (reader != null) reader.close();
    }

    @Override
    protected void onDetachedFromWindow() {
        release();
        super.onDetachedFromWindow();
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        Bitmap bitmap = mBitmap;
        if (bitmap == null) return;
        canvas.drawBitmap(bitmap, mStackX, mStackY - mMarginTopPx, null);
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private static final class Api29 {
        static boolean capture(@NonNull StackPicture picture, @NonNull View stack) {
            DisplayMetrics metrics = picture.getResources().getDisplayMetrics();
            int margin = Math.round(MARGIN_TOP_DP * metrics.density);
            int width = stack.getWidth();
            int height = stack.getHeight() + margin;
            // Where the stack stands in the picture's coordinates, its translation included.
            picture.getLocationInWindow(picture.mHere);
            stack.getLocationInWindow(picture.mThere);
            float stackX = picture.mThere[0] - picture.mHere[0] + picture.getTranslationX();
            float stackY = picture.mThere[1] - picture.mHere[1] + picture.getTranslationY();

            RenderNode root = new RenderNode("stackPicture");
            root.setPosition(0, 0, width, height);
            RecordingCanvas canvas = root.beginRecording(width, height);
            try {
                canvas.translate(0f, margin);
                // The stack's own drawing: its children are drawn by the render nodes the window
                // draws them by, so the picture is what the window shows.
                stack.draw(canvas);
            } finally {
                root.endRecording();
            }
            ImageReader reader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 1,
                HardwareBuffer.USAGE_GPU_COLOR_OUTPUT | HardwareBuffer.USAGE_GPU_SAMPLED_IMAGE);
            HardwareRenderer renderer = new HardwareRenderer();
            Image image = null;
            Bitmap bitmap = null;
            try {
                renderer.setOpaque(false);
                renderer.setContentRoot(root);
                renderer.setSurface(reader.getSurface());
                // The window's light, over the stack where it stands on the screen.
                int[] onScreen = new int[2];
                stack.getLocationOnScreen(onScreen);
                renderer.setLightSourceGeometry(metrics.widthPixels / 2f - onScreen[0],
                    -(onScreen[1] - margin), LIGHT_Z_DP * metrics.density,
                    LIGHT_RADIUS_DP * metrics.density);
                renderer.setLightSourceAlpha(AMBIENT_SHADOW_ALPHA, SPOT_SHADOW_ALPHA);
                renderer.createRenderRequest().setWaitForPresent(true).syncAndDraw();
                image = reader.acquireLatestImage();
                HardwareBuffer buffer = image == null ? null : image.getHardwareBuffer();
                if (buffer != null) {
                    bitmap = Bitmap.wrapHardwareBuffer(buffer,
                        ColorSpace.get(ColorSpace.Named.SRGB));
                    buffer.close();
                }
            } finally {
                renderer.destroy();
                root.discardDisplayList();
            }
            if (bitmap == null) {
                if (image != null) image.close();
                reader.close();
                return false;
            }
            picture.mBitmap = bitmap;
            picture.mImage = image;
            picture.mReader = reader;
            picture.mStackX = stackX;
            picture.mStackY = stackY;
            picture.mStackHeight = stack.getHeight();
            picture.mMarginTopPx = margin;
            picture.mSinkPx = Float.NaN;
            picture.invalidate();
            return true;
        }
    }
}
