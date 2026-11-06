package com.termux.app.chrome.scene;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.ColorSpace;
import android.graphics.HardwareRenderer;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.RecordingCanvas;
import android.graphics.RenderNode;
import android.hardware.HardwareBuffer;
import android.media.Image;
import android.media.ImageReader;
import android.os.Build;
import android.os.SystemClock;
import android.os.Trace;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * The still of a live scene: its t = 0 frame at the user's knobs, as a software ARGB_8888 bitmap
 * that {@code WallpaperManager.setBitmap} accepts (Home and Lock show it). Drawn by a one-off
 * {@link HardwareRenderer} into an {@link ImageReader}, the way {@code WallpaperGpuBlur} bakes;
 * callable from any thread (not the one that must stay responsive: the GPU wait can take a while).
 * Nothing is kept: every renderer, reader, image and buffer is released before it returns.
 */
public final class SceneStill {

    /** How long the frame may take to reach the reader before the render counts as failed. */
    private static final long IMAGE_WAIT_MS = 2000L;
    private static final long IMAGE_POLL_MS = 2L;
    /** The longest side, in pixels, a still is rendered at (GPU texture limits and memory). */
    static final int MAX_SIDE_PX = 4096;

    private SceneStill() {}

    /** The size actually rendered: both sides at least 1 and the longer at most {@link #MAX_SIDE_PX}, aspect kept. */
    @NonNull
    static int[] fitSize(int widthPx, int heightPx) {
        int w = Math.max(1, widthPx), h = Math.max(1, heightPx);
        int longest = Math.max(w, h);
        if (longest > MAX_SIDE_PX) {
            float k = MAX_SIDE_PX / (float) longest;
            w = Math.max(1, Math.round(w * k));
            h = Math.max(1, Math.round(h * k));
        }
        return new int[] {w, h};
    }

    /**
     * @param kind one of the scene kinds (see {@link LiveWallpaperChoice#USER_KINDS})
     * @param choice the knobs: density and palette apply where the scene has them (speed has no
     *               meaning at t = 0)
     * @return a mutable software ARGB_8888 bitmap, or null when the device cannot render scenes
     *         (below API 33), the kind is unknown, or the thread was interrupted
     * @throws IllegalStateException when the GPU did not deliver the frame
     */
    @Nullable
    public static Bitmap render(@NonNull Context context, @NonNull String kind,
                                @NonNull LiveWallpaperChoice choice, int widthPx, int heightPx) {
        if (!LiveWallpaperChoice.isSupported() || !ScenePrograms.isKind(kind)) return null;
        return renderApi33(context, kind, choice, widthPx, heightPx);
    }

    @androidx.annotation.RequiresApi(Build.VERSION_CODES.TIRAMISU)
    @Nullable
    private static Bitmap renderApi33(@NonNull Context context, @NonNull String kind,
                                      @NonNull LiveWallpaperChoice choice, int widthPx, int heightPx) {
        int[] size = fitSize(widthPx, heightPx);
        int width = size[0], height = size[1];
        Trace.beginSection("Scene.still");
        ImageReader reader = null;
        HardwareRenderer renderer = null;
        RenderNode node = null;
        Image image = null;
        HardwareBuffer buffer = null;
        Bitmap wrapped = null;
        SceneArtwork artwork = null;
        try {
            artwork = new SceneArtwork(context, SceneArtwork.COASTAL_SUNSET_ASSET);
            SceneProgram program = ScenePrograms.factory(kind, new FilamentSource(), artwork).create(1f);
            program.setSize(width, height, context.getResources().getDisplayMetrics().density);
            program.setKnobs(choice.speed, choice.density, choice.palette);
            program.restart();
            program.setTime(0L);

            reader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 1,
                HardwareBuffer.USAGE_GPU_SAMPLED_IMAGE | HardwareBuffer.USAGE_GPU_COLOR_OUTPUT);
            node = new RenderNode("SceneStill");
            node.setPosition(0, 0, width, height);
            Paint paint = new Paint();
            paint.setAntiAlias(false);
            paint.setShader(program.shader());
            RecordingCanvas canvas = node.beginRecording(width, height);
            try {
                canvas.drawRect(0f, 0f, width, height, paint);
            } finally {
                node.endRecording();
            }

            renderer = new HardwareRenderer();
            renderer.setOpaque(true);
            renderer.setSurface(reader.getSurface());
            renderer.setContentRoot(node);
            int sync = renderer.createRenderRequest().setWaitForPresent(true).syncAndDraw();
            if ((sync & (HardwareRenderer.SYNC_FRAME_DROPPED
                | HardwareRenderer.SYNC_CONTEXT_IS_STOPPED
                | HardwareRenderer.SYNC_LOST_SURFACE_REWARD_IF_FOUND)) != 0) {
                throw new IllegalStateException("scene still not drawn, sync result " + sync);
            }

            long deadline = SystemClock.uptimeMillis() + IMAGE_WAIT_MS;
            while ((image = reader.acquireNextImage()) == null) {
                if (SystemClock.uptimeMillis() >= deadline) {
                    throw new IllegalStateException("no scene still within " + IMAGE_WAIT_MS + " ms");
                }
                try {
                    Thread.sleep(IMAGE_POLL_MS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return null;
                }
            }
            buffer = image.getHardwareBuffer();
            if (buffer == null) throw new IllegalStateException("scene still has no hardware buffer");
            wrapped = Bitmap.wrapHardwareBuffer(buffer, ColorSpace.get(ColorSpace.Named.SRGB));
            if (wrapped == null) throw new IllegalStateException("could not wrap the scene still's buffer");
            Bitmap result = wrapped.copy(Bitmap.Config.ARGB_8888, true);
            if (result == null) throw new IllegalStateException("could not copy the scene still to software");
            return result;
        } finally {
            if (wrapped != null) wrapped.recycle();
            if (buffer != null) buffer.close();
            if (image != null) image.close();
            if (renderer != null) renderer.destroy();
            if (node != null) node.discardDisplayList();
            if (reader != null) reader.close();
            if (artwork != null) artwork.release();
            Trace.endSection();
        }
    }
}
