package com.termux.app.chrome;

import android.graphics.Bitmap;
import android.graphics.ColorSpace;
import android.graphics.HardwareRenderer;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.RecordingCanvas;
import android.graphics.Rect;
import android.graphics.RenderEffect;
import android.graphics.RenderNode;
import android.graphics.Shader;
import android.hardware.HardwareBuffer;
import android.media.Image;
import android.media.ImageReader;
import android.os.SystemClock;
import android.os.Trace;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

/**
 * The wallpaper pre-blur on the GPU (Android 12+): the source is drawn scaled into a
 * {@link RenderNode} carrying {@link RenderEffect#createBlurEffect}, rendered by a one-off
 * {@link HardwareRenderer} into an {@link ImageReader}'s surface, and the frame copied to a
 * software ARGB_8888 bitmap, since the cache and the inks read its pixels. This is the route
 * Android's RenderScript migration guide gives for ScriptIntrinsicBlur.
 *
 * <p>Runs on any thread (the blur cache's worker, or the main thread when it has none). Nothing is
 * kept between bakes: every renderer, reader, image, buffer and bitmap is released before it
 * returns, on every path.</p>
 */
@RequiresApi(31)
final class WallpaperGpuBlur {

    /** How long the frame may take to reach the reader before the bake counts as failed. */
    private static final long IMAGE_WAIT_MS = 2000L;
    private static final long IMAGE_POLL_MS = 2L;

    private WallpaperGpuBlur() {}

    /** Thrown for a bake the GPU did not deliver; the caller falls back to RenderScript. */
    static final class GpuBlurException extends Exception {
        GpuBlurException(String message) {
            super(message);
        }
    }

    /**
     * {@code source} blurred at {@code blurRadius} (a RenderEffect radius) on a
     * {@code width} x {@code height} frame, as a mutable software ARGB_8888 bitmap.
     *
     * @return null only when the thread was interrupted (the worker shutting down)
     * @throws GpuBlurException or any runtime exception when the GPU did not deliver the frame
     */
    @Nullable
    static Bitmap blur(@NonNull Bitmap source, int width, int height, float blurRadius)
        throws GpuBlurException {
        Trace.beginSection("Blur.preBlurGpu");
        ImageReader reader = null;
        HardwareRenderer renderer = null;
        RenderNode node = null;
        Image image = null;
        HardwareBuffer buffer = null;
        Bitmap wrapped = null;
        try {
            reader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 1,
                HardwareBuffer.USAGE_GPU_SAMPLED_IMAGE | HardwareBuffer.USAGE_GPU_COLOR_OUTPUT);
            node = new RenderNode("WallpaperPreBlur");
            node.setPosition(0, 0, width, height);
            if (blurRadius > 0f) {
                // RenderScript clamps at the edges; CLAMP keeps the rim as bright as it was.
                node.setRenderEffect(
                    RenderEffect.createBlurEffect(blurRadius, blurRadius, Shader.TileMode.CLAMP));
            }
            RecordingCanvas canvas = node.beginRecording(width, height);
            try {
                Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG);
                canvas.drawBitmap(source, null, new Rect(0, 0, width, height), paint);
            } finally {
                node.endRecording();
            }

            renderer = new HardwareRenderer();
            renderer.setOpaque(!source.hasAlpha());
            renderer.setSurface(reader.getSurface());
            renderer.setContentRoot(node);
            int sync = renderer.createRenderRequest().setWaitForPresent(true).syncAndDraw();
            if ((sync & (HardwareRenderer.SYNC_FRAME_DROPPED
                | HardwareRenderer.SYNC_CONTEXT_IS_STOPPED
                | HardwareRenderer.SYNC_LOST_SURFACE_REWARD_IF_FOUND)) != 0) {
                throw new GpuBlurException("frame not drawn, sync result " + sync);
            }

            long deadline = SystemClock.uptimeMillis() + IMAGE_WAIT_MS;
            while ((image = reader.acquireNextImage()) == null) {
                if (SystemClock.uptimeMillis() >= deadline) {
                    throw new GpuBlurException("no frame within " + IMAGE_WAIT_MS + " ms");
                }
                try {
                    Thread.sleep(IMAGE_POLL_MS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return null;
                }
            }
            buffer = image.getHardwareBuffer();
            if (buffer == null) {
                throw new GpuBlurException("frame has no hardware buffer");
            }
            wrapped = Bitmap.wrapHardwareBuffer(buffer, ColorSpace.get(ColorSpace.Named.SRGB));
            if (wrapped == null) {
                throw new GpuBlurException("could not wrap the frame's buffer");
            }
            Bitmap result = wrapped.copy(Bitmap.Config.ARGB_8888, true);
            if (result == null) {
                throw new GpuBlurException("could not copy the frame to software");
            }
            return result;
        } finally {
            if (wrapped != null) wrapped.recycle();
            if (buffer != null) buffer.close();
            if (image != null) image.close();
            if (renderer != null) renderer.destroy();
            if (node != null) node.discardDisplayList();
            if (reader != null) reader.close();
            Trace.endSection();
        }
    }
}
