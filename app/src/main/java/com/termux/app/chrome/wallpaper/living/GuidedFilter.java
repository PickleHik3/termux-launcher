package com.termux.app.chrome.wallpaper.living;

import androidx.annotation.NonNull;

/**
 * The guided filter of He, Sun and Tang (2010), box-filter form on float planes. The living-still
 * masks use it to pull a soft model map (128x128 SegFormer probabilities, 320x320 saliency) onto
 * the edges of the photo: {@code out = a * guide + b}, with {@code a} and {@code b} fitted per
 * window so the output follows the guide's edges and the source's values. Radius ~8, eps ~1e-3
 * for a guide in 0..1 (living-stills.md, Part C).
 */
public final class GuidedFilter {
    private GuidedFilter() {}

    /**
     * @param guide the photo's luminance in 0..1, {@code w * h}
     * @param src   the map to refine, same size (resize it first)
     * @param r     window radius in pixels
     * @param eps   regularisation; larger is blurrier
     */
    @NonNull
    public static float[] filter(@NonNull float[] guide, @NonNull float[] src, int w, int h, int r, float eps) {
        int n = w * h;
        float[] ip = new float[n];
        float[] ii = new float[n];
        for (int i = 0; i < n; i++) {
            ip[i] = guide[i] * src[i];
            ii[i] = guide[i] * guide[i];
        }
        float[] meanI = Planes.boxMean(guide, w, h, r);
        float[] meanP = Planes.boxMean(src, w, h, r);
        float[] corrIp = Planes.boxMean(ip, w, h, r);
        float[] corrI = Planes.boxMean(ii, w, h, r);
        float[] a = new float[n];
        float[] b = new float[n];
        for (int i = 0; i < n; i++) {
            float varI = corrI[i] - meanI[i] * meanI[i];
            float cov = corrIp[i] - meanI[i] * meanP[i];
            a[i] = cov / (varI + eps);
            b[i] = meanP[i] - a[i] * meanI[i];
        }
        float[] meanA = Planes.boxMean(a, w, h, r);
        float[] meanB = Planes.boxMean(b, w, h, r);
        float[] out = new float[n];
        for (int i = 0; i < n; i++) out[i] = Planes.clamp01(meanA[i] * guide[i] + meanB[i]);
        return out;
    }
}
