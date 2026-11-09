package com.termux.app.chrome;

import android.graphics.Bitmap;
import android.graphics.ColorSpace;
import android.graphics.HardwareRenderer;
import android.graphics.PixelFormat;
import android.graphics.RenderNode;
import android.hardware.HardwareBuffer;
import android.media.Image;
import android.media.ImageReader;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

/**
 * One picture of a live surface's page layer, rendered offscreen on the GPU as the window draws
 * it ({@link LiveBackdrop}, {@link BackdropLayers.Mode#FROZEN}), the way
 * {@code StackPicture} pictures the dock's stack. Started in a pre-draw and collected by a later
 * one: the render is not waited for, and until it lands the surface keeps its one-layer picture.
 * Once collected only the bitmap is held (its buffer, a quarter-scale layer's bytes); the renderer
 * and the reader are gone.
 */
@RequiresApi(Build.VERSION_CODES.Q)
final class PageLayerCapture {

    /** Frames a capture may take to land before the swipe goes on without it. */
    static final int MAX_WAIT_FRAMES = 4;

    @Nullable private HardwareRenderer mRenderer;
    @Nullable private ImageReader mReader;
    @Nullable private RenderNode mRoot;
    private int mWaited;

    /**
     * Renders {@code root} (already recorded, {@code width} x {@code height}) offscreen. False,
     * with nothing held, when it cannot.
     */
    boolean start(@NonNull RenderNode root, int width, int height) {
        cancel();
        ImageReader reader = null;
        HardwareRenderer renderer = null;
        try {
            reader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 1,
                HardwareBuffer.USAGE_GPU_COLOR_OUTPUT | HardwareBuffer.USAGE_GPU_SAMPLED_IMAGE);
            renderer = new HardwareRenderer();
            renderer.setOpaque(false);
            renderer.setContentRoot(root);
            renderer.setSurface(reader.getSurface());
            renderer.createRenderRequest().syncAndDraw();
        } catch (RuntimeException e) {
            if (renderer != null) renderer.destroy();
            if (reader != null) reader.close();
            root.discardDisplayList();
            return false;
        }
        mReader = reader;
        mRenderer = renderer;
        mRoot = root;
        mWaited = 0;
        return true;
    }

    /** Whether a capture is under way. */
    boolean pending() {
        return mReader != null;
    }

    /**
     * The captured picture once it has landed, and then nothing is held here any more; null while
     * it is still rendering. A capture that has not landed in {@link #MAX_WAIT_FRAMES} calls, or
     * fails, is dropped: {@link #pending} turns false with null returned.
     */
    @Nullable
    Bitmap poll() {
        ImageReader reader = mReader;
        if (reader == null) return null;
        Image image;
        try {
            image = reader.acquireLatestImage();
        } catch (RuntimeException e) {
            cancel();
            return null;
        }
        if (image == null) {
            if (++mWaited > MAX_WAIT_FRAMES) cancel();
            return null;
        }
        Bitmap bitmap = null;
        try {
            HardwareBuffer buffer = image.getHardwareBuffer();
            if (buffer != null) {
                // The bitmap holds its own reference to the buffer; the reader can go.
                bitmap = Bitmap.wrapHardwareBuffer(buffer, ColorSpace.get(ColorSpace.Named.SRGB));
                buffer.close();
            }
        } catch (RuntimeException e) {
            bitmap = null;
        } finally {
            image.close();
            cancel();
        }
        return bitmap;
    }

    /** Drops a capture under way, and everything it holds. */
    void cancel() {
        if (mRenderer != null) mRenderer.destroy();
        if (mReader != null) mReader.close();
        if (mRoot != null) mRoot.discardDisplayList();
        mRenderer = null;
        mReader = null;
        mRoot = null;
        mWaited = 0;
    }
}
