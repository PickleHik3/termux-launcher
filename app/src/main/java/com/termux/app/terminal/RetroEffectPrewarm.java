package com.termux.app.terminal;

import android.graphics.Color;
import android.graphics.HardwareRenderer;
import android.graphics.PixelFormat;
import android.graphics.RecordingCanvas;
import android.graphics.RenderEffect;
import android.graphics.RenderNode;
import android.hardware.HardwareBuffer;
import android.media.ImageReader;
import android.os.Build;
import android.os.Trace;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;

import com.termux.shared.logger.Logger;

/**
 * Compiles the retro shaders' GPU programs once, on a worker thread, so the compile does not land
 * on the first typed character after the effect is turned on. One tiny frame is drawn through the
 * same {@link RenderEffect} the panes use, into an offscreen surface nobody sees; the render
 * thread keeps what it compiled for the life of the process. Best effort: any failure is logged
 * and the first real frame compiles as it always did.
 */
final class RetroEffectPrewarm {

    private static final String LOG_TAG = "RetroEffectPrewarm";
    private static final int SIZE_PX = 16;

    /** Bit 0 CRT shader, bit 1 TFT shader: what has been asked for already. */
    private static int sRequested;

    private RetroEffectPrewarm() {}

    /** Warms the program {@code style} uses, once per process per program. */
    static synchronized void request(@NonNull PaneRetroStyle style) {
        if (!PaneRetroEffect.available() || style == PaneRetroStyle.NONE) return;
        int bit = style.usesCrt() ? 1 : 2;
        if ((sRequested & bit) != 0) return;
        sRequested |= bit;
        Thread t = new Thread(() -> run(style), "RetroPrewarm");
        t.setDaemon(true);
        t.start();
    }

    private static void run(@NonNull PaneRetroStyle style) {
        Trace.beginSection("Effect.prewarm");
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) Api33.draw(style);
        } catch (Throwable t) {
            Logger.logError(LOG_TAG, "prewarm skipped: " + t);
        } finally {
            Trace.endSection();
        }
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private static final class Api33 {
        static void draw(@NonNull PaneRetroStyle style) {
            RetroUniforms u = new RetroUniforms().set(SIZE_PX, SIZE_PX, 0f, 0f, 1f, 1f, 1f,
                0f, 0f, SIZE_PX, SIZE_PX, 0f, 0f, 0f);
            RenderEffect effect = new PaneRetroEffect().effectFor(style, u);
            if (effect == null) return;
            RenderNode content = new RenderNode("retroPrewarmContent");
            content.setPosition(0, 0, SIZE_PX, SIZE_PX);
            content.setRenderEffect(effect);
            RecordingCanvas c = content.beginRecording();
            c.drawColor(Color.DKGRAY);
            content.endRecording();
            RenderNode root = new RenderNode("retroPrewarmRoot");
            root.setPosition(0, 0, SIZE_PX, SIZE_PX);
            RecordingCanvas rc = root.beginRecording();
            rc.drawRenderNode(content);
            root.endRecording();
            ImageReader reader = ImageReader.newInstance(SIZE_PX, SIZE_PX, PixelFormat.RGBA_8888, 1,
                HardwareBuffer.USAGE_GPU_COLOR_OUTPUT | HardwareBuffer.USAGE_GPU_SAMPLED_IMAGE);
            HardwareRenderer renderer = new HardwareRenderer();
            try {
                renderer.setContentRoot(root);
                renderer.setSurface(reader.getSurface());
                renderer.createRenderRequest().setWaitForPresent(true).syncAndDraw();
            } finally {
                renderer.destroy();
                reader.close();
            }
        }
    }
}
