package com.termux.app.chrome;

import android.content.Context;
import android.graphics.Bitmap;
import android.os.Build;
import android.os.Trace;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.github.mmin18.widget.AndroidStockBlurImpl;
import com.termux.shared.logger.Logger;
import com.termux.shared.view.ViewUtils;

/**
 * Turns a captured wallpaper frame into the pre-blurred frame every glass surface samples.
 *
 * <p>The frame comes back at the resolution it was blurred at — the capture downsampled by the
 * factor below, a quarter of the screen at the usual radii — and every surface scales it up as it
 * draws ({@link SharedFrameDrawable}, the pane slabs). It used to be scaled back up to the
 * capture's size here, so a 1.5×-wide parallax frame cost ~15.6 MB per radius instead of ~1 MB,
 * and the byte budget evicted radii mid-session. Bilinear up on draw is the same resample the
 * upscale did, so the picture is the one it was.</p>
 *
 * <p>On Android 12+ the blur runs on the GPU ({@link WallpaperGpuBlur}): RenderScript has no GPU
 * driver on many devices and ran its intrinsic on every CPU core. Below Android 12, and for the
 * rest of the process once a GPU bake fails, RenderScript blurs it. Both share
 * {@link WallpaperBlurSizing}, so the frame's size and softness are the same either way.</p>
 */
final class WallpaperBlurRenderer {

    private static final String LOG_TAG = "ChromeRenderer";

    /** Set once a GPU bake failed; RenderScript bakes every frame for the rest of the process. */
    private static volatile boolean sGpuFailed;

    private WallpaperBlurRenderer() {}

    @Nullable
    static Bitmap preBlur(@NonNull Context context, @NonNull Bitmap sourceBitmap, int blurRadiusDp) {
        Trace.beginSection("Blur.preBlur");
        try {
            return doPreBlur(context, sourceBitmap, blurRadiusDp);
        } finally {
            Trace.endSection();
        }
    }

    @Nullable
    private static Bitmap doPreBlur(@NonNull Context context, @NonNull Bitmap sourceBitmap, int blurRadiusDp) {
        float blurRadiusPx = ViewUtils.dpToPx(context, Math.max(0, blurRadiusDp));
        if (blurRadiusPx <= 0f) {
            return sourceBitmap;
        }

        float downsampleFactor = WallpaperBlurSizing.downsampleFactor(blurRadiusPx);
        float scriptRadius = WallpaperBlurSizing.scriptRadius(blurRadiusPx, downsampleFactor);
        int scaledWidth = WallpaperBlurSizing.scaledSide(sourceBitmap.getWidth(), downsampleFactor);
        int scaledHeight = WallpaperBlurSizing.scaledSide(sourceBitmap.getHeight(), downsampleFactor);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !sGpuFailed) {
            try {
                // Null only for an interrupted worker; RenderScript would be wasted on it too.
                return WallpaperGpuBlur.blur(sourceBitmap, scaledWidth, scaledHeight,
                    WallpaperBlurSizing.gpuBlurRadius(scriptRadius));
            } catch (Throwable e) {
                // Once is enough: a GPU that failed a bake leaves RenderScript the process's blur.
                sGpuFailed = true;
                Logger.logStackTraceWithMessage(LOG_TAG,
                    "GPU wallpaper blur failed, using RenderScript from now on", e);
            }
        }
        return renderScriptBlur(context, sourceBitmap, scaledWidth, scaledHeight, scriptRadius);
    }

    /** RenderScript's intrinsic, below Android 12 and after a GPU bake failed. */
    @Nullable
    private static Bitmap renderScriptBlur(@NonNull Context context, @NonNull Bitmap sourceBitmap,
                                           int scaledWidth, int scaledHeight, float scriptRadius) {
        Bitmap blurInput = null;
        Bitmap blurOutput = null;
        AndroidStockBlurImpl blurImpl = new AndroidStockBlurImpl();
        try {
            blurInput = Bitmap.createScaledBitmap(sourceBitmap, scaledWidth, scaledHeight, true);
            blurOutput = Bitmap.createBitmap(scaledWidth, scaledHeight, Bitmap.Config.ARGB_8888);
            if (!blurImpl.prepare(context, blurInput, scriptRadius)) {
                return null;
            }
            blurImpl.blur(blurInput, blurOutput);
            // The blurred frame is the result, at the size it was blurred at.
            Bitmap result = blurOutput;
            blurOutput = null;
            return result;
        } catch (Throwable e) {
            Logger.logStackTraceWithMessage(LOG_TAG, "Failed to create cached accessory wallpaper blur", e);
            return null;
        } finally {
            blurImpl.release();
            if (blurInput != null && blurInput != sourceBitmap) {
                blurInput.recycle();
            }
            if (blurOutput != null) {
                blurOutput.recycle();
            }
        }
    }
}
