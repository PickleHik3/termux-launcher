package com.termux.app.chrome;

/**
 * The size and strength of the wallpaper pre-blur ({@link WallpaperBlurRenderer}), the same for
 * the GPU bake and RenderScript so both give one frame. Pure.
 *
 * <p>Low radii must keep the source crisp: a fixed 4x down/up resample softened the frame far
 * beyond the requested blur and shifted content by a few pixels, so at 1-5dp the glass read as
 * showing a different wallpaper than the one right next to it. The factor is the smallest that
 * keeps the script radius inside RenderScript's 25px cap. The factor is also the frame's stored
 * scale, so a radius under 25px keeps a full-size frame. The GPU bake has no cap but keeps these
 * sizes: the low-radius rule is about the picture, and the frame must not cost more memory.</p>
 */
final class WallpaperBlurSizing {

    private WallpaperBlurSizing() {}

    /** The downsample factor (and stored scale) for a blur of {@code blurRadiusPx}; at least 1. */
    static float downsampleFactor(float blurRadiusPx) {
        float cap = LivePicture.STORED_SCRIPT_CAP_PX;
        float factor = Math.max(1f, Math.min(ChromePolicy.ACCESSORY_BLUR_DOWNSAMPLE_FACTOR,
            (float) Math.ceil(blurRadiusPx / cap)));
        if (blurRadiusPx / factor > cap) {
            factor = (float) Math.ceil(blurRadiusPx / cap);
        }
        return factor;
    }

    /** RenderScript's radius on the downsampled frame, inside its 0.1..25 px range. */
    static float scriptRadius(float blurRadiusPx, float downsampleFactor) {
        float r = blurRadiusPx / downsampleFactor;
        return Math.max(0.1f, Math.min(LivePicture.STORED_SCRIPT_CAP_PX, r));
    }

    /** One side of the downsampled frame, at least 1 px. */
    static int scaledSide(int sourceSide, float downsampleFactor) {
        return Math.max(1, Math.round(sourceSide / downsampleFactor));
    }

    /**
     * The {@code RenderEffect.createBlurEffect} radius that blurs as softly as RenderScript's
     * intrinsic at {@code scriptRadius}, on the same downsampled frame.
     *
     * <p>ScriptIntrinsicBlur reads a radius as sigma = 0.4 r + 0.6 (frameworks/rs
     * cpu_ref/rsCpuIntrinsicBlur.cpp, ComputeGaussianWeights). RenderEffect hands its radius to
     * hwui's Blur::convertRadiusToSigma, sigma = 0.57735 R + 0.5 (frameworks/base
     * libs/hwui/utils/Blur.cpp). Equal sigmas give R = (0.4 r + 0.6 - 0.5) / 0.57735, the same
     * constants {@link LivePicture} calibrates Live against.</p>
     */
    static float gpuBlurRadius(float scriptRadius) {
        float sigma = LivePicture.STORED_SIGMA_SLOPE * scriptRadius + LivePicture.STORED_SIGMA_BASE;
        return Math.max(0f, (sigma - LivePicture.SKIA_SIGMA_BASE) / LivePicture.SKIA_SIGMA_SLOPE);
    }
}
