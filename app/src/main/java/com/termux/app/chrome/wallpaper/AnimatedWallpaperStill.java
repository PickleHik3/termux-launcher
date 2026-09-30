package com.termux.app.chrome.wallpaper;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorSpace;
import android.graphics.HardwareBufferRenderer;
import android.graphics.Paint;
import android.graphics.RenderNode;
import android.graphics.RuntimeShader;
import android.hardware.HardwareBuffer;
import android.hardware.SyncFence;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * The rest-pose still of a generated background: its picture at energy 0, time 0, no moments.
 * This is what the system gets as the wallpaper, so the lock screen and other apps show what the
 * launcher settles on.
 *
 * <p>Route: a {@link RenderNode} that fills its bounds with the shader, drawn by a
 * {@link HardwareBufferRenderer} into an RGBA_8888 {@link HardwareBuffer}, wrapped as a hardware
 * bitmap and copied to a software ARGB_8888 bitmap. Chosen over Picture to Bitmap because the
 * pipeline already uses HardwareBufferRenderer (API 34, same floor), and it draws on the GPU
 * without a window or a view. Blocks until the GPU finishes (up to 10 s); call it off the main
 * thread.</p>
 */
@RequiresApi(34)
public final class AnimatedWallpaperStill {

    private AnimatedWallpaperStill() {}

    /** Software ARGB_8888 bitmap of the rest pose, or null when the GPU route fails. */
    @Nullable
    public static Bitmap render(@NonNull AnimatedWallpaper w, @NonNull int[] palette,
                                int widthPx, int heightPx) {
        if (widthPx <= 0 || heightPx <= 0) return null;
        RuntimeShader shader = WallpaperUniforms.newShader(w);
        WallpaperUniforms.applyRest(shader, palette, widthPx, heightPx);

        RenderNode node = new RenderNode("animated-wallpaper-still");
        node.setPosition(0, 0, widthPx, heightPx);
        Canvas canvas = node.beginRecording(widthPx, heightPx);
        try {
            Paint paint = new Paint();
            paint.setShader(shader);
            canvas.drawRect(0f, 0f, widthPx, heightPx, paint);
        } finally {
            node.endRecording();
        }

        HardwareBuffer buffer = HardwareBuffer.create(widthPx, heightPx, HardwareBuffer.RGBA_8888, 1,
            HardwareBuffer.USAGE_GPU_COLOR_OUTPUT | HardwareBuffer.USAGE_GPU_SAMPLED_IMAGE);
        HardwareBufferRenderer renderer = new HardwareBufferRenderer(buffer);
        try {
            renderer.setContentRoot(node);
            final CountDownLatch done = new CountDownLatch(1);
            final boolean[] ok = {false};
            renderer.obtainRenderRequest()
                .setColorSpace(ColorSpace.get(ColorSpace.Named.SRGB))
                .draw(Runnable::run, result -> {
                    try (SyncFence fence = result.getFence()) {
                        fence.awaitForever();
                        ok[0] = result.getStatus() == HardwareBufferRenderer.RenderResult.SUCCESS;
                    } finally {
                        done.countDown();
                    }
                });
            if (!done.await(10, TimeUnit.SECONDS) || !ok[0]) return null;
            Bitmap hardware = Bitmap.wrapHardwareBuffer(buffer, ColorSpace.get(ColorSpace.Named.SRGB));
            if (hardware == null) return null;
            try {
                return hardware.copy(Bitmap.Config.ARGB_8888, false);
            } finally {
                hardware.recycle();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        } finally {
            renderer.close();
            node.discardDisplayList();
            buffer.close();
        }
    }
}
