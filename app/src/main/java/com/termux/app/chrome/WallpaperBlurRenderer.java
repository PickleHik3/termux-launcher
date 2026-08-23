package com.termux.app.chrome;

import android.content.Context;
import android.graphics.Bitmap;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.github.mmin18.widget.AndroidStockBlurImpl;
import com.termux.shared.logger.Logger;
import com.termux.shared.view.ViewUtils;

/** Turns a captured wallpaper frame into the pre-blurred frame the glass surfaces are cut from. */
final class WallpaperBlurRenderer {

    private static final String LOG_TAG = "ChromeRenderer";

    private WallpaperBlurRenderer() {}

    @Nullable
    static Bitmap preBlur(@NonNull Context context, @NonNull Bitmap sourceBitmap, int blurRadiusDp) {
        float blurRadiusPx = ViewUtils.dpToPx(context, Math.max(0, blurRadiusDp));
        if (blurRadiusPx <= 0f) {
            return sourceBitmap;
        }

        // Low radii must keep the source crisp: a fixed 4x down/up resample softened the frame far
        // beyond the requested blur and shifted content by a few pixels, so at 1-5dp the glass read
        // as showing a different wallpaper than the one right next to it. Use the smallest factor
        // that keeps the script radius inside RenderScript's 25px cap instead.
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
            return Bitmap.createScaledBitmap(blurOutput, sourceBitmap.getWidth(), sourceBitmap.getHeight(), true);
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
