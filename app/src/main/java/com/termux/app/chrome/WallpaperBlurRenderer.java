package com.termux.app.chrome;

import android.content.Context;
import android.graphics.Bitmap;
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
 */
final class WallpaperBlurRenderer {

    private static final String LOG_TAG = "ChromeRenderer";

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

        // Low radii must keep the source crisp: a fixed 4x down/up resample softened the frame far
        // beyond the requested blur and shifted content by a few pixels, so at 1-5dp the glass read
        // as showing a different wallpaper than the one right next to it. Use the smallest factor
        // that keeps the script radius inside RenderScript's 25px cap instead. The factor is also
        // the frame's stored scale, so a radius under 25px keeps a full-size frame.
        float downsampleFactor = Math.max(1f, Math.min(ChromePolicy.ACCESSORY_BLUR_DOWNSAMPLE_FACTOR,
            (float) Math.ceil(blurRadiusPx / 25f)));
        float scriptRadius = blurRadiusPx / downsampleFactor;
        if (scriptRadius > 25f) {
            downsampleFactor = (float) Math.ceil(blurRadiusPx / 25f);
            scriptRadius = blurRadiusPx / downsampleFactor;
        }
        scriptRadius = Math.max(0.1f, Math.min(25f, scriptRadius));

        int scaledWidth = Math.max(1, Math.round(sourceBitmap.getWidth() / downsampleFactor));
        int scaledHeight = Math.max(1, Math.round(sourceBitmap.getHeight() / downsampleFactor));
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
