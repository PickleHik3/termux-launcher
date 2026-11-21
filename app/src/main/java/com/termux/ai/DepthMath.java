package com.termux.ai;

import androidx.annotation.NonNull;

import java.io.ByteArrayOutputStream;
import java.util.Arrays;
import java.util.zip.CRC32;
import java.util.zip.Deflater;

/**
 * The pure half of the depth maker ({@link DepthMaker}): resizing a photo to the graph's input,
 * the ImageNet normalisation, the model output as a 0..1 near-is-white map, its resampling to the
 * photo's aspect and the 8-bit grey PNG. No Android types, so every step is covered by JVM tests.
 *
 * <p>Pixels are packed ARGB ints in, planar floats out. The resize follows Pillow's algorithm
 * (separable, filter support widened by the shrink factor so a big photo is area-averaged rather
 * than aliased) and rounds to 8 bits like the reference's uint8 images. The PNG is plain 8-bit
 * grey, never with alpha: Android and browsers premultiply alpha and wipe colour where it is 0.
 */
final class DepthMath {
    private DepthMath() {}

    static final float[] MEAN = {0.485f, 0.456f, 0.406f};
    static final float[] STD = {0.229f, 0.224f, 0.225f};

    /** The depth image's longer side never exceeds this, nor the photo's own longer side. */
    static final int MAX_SIDE = 1024;

    /** Which Pillow filter a graph's reference preprocessing uses. */
    enum Filter { BILINEAR, BICUBIC }

    // ---- Resize ----------------------------------------------------------------------------

    /**
     * Resizes {@code argb} (row-major, {@code srcW x srcH}) to {@code dstW x dstH} and returns
     * interleaved RGB floats (HWC) holding whole numbers 0..255.
     */
    @NonNull
    static float[] resizeRgb(@NonNull int[] argb, int srcW, int srcH, int dstW, int dstH, @NonNull Filter filter) {
        if (argb.length < srcW * srcH) throw new IllegalArgumentException("pixels shorter than " + srcW + "x" + srcH);
        // Horizontal pass: srcH rows of dstW pixels, three channels, kept as floats.
        Coeffs horizontal = coeffs(srcW, dstW, filter);
        float[] mid = new float[srcH * dstW * 3];
        for (int y = 0; y < srcH; y++) {
            int rowIn = y * srcW;
            int rowOut = y * dstW * 3;
            for (int x = 0; x < dstW; x++) {
                int start = horizontal.start[x];
                float[] w = horizontal.weights[x];
                float r = 0f, g = 0f, b = 0f;
                for (int k = 0; k < w.length; k++) {
                    int p = argb[rowIn + start + k];
                    r += w[k] * ((p >> 16) & 0xFF);
                    g += w[k] * ((p >> 8) & 0xFF);
                    b += w[k] * (p & 0xFF);
                }
                // Pillow keeps the intermediate as 8-bit.
                mid[rowOut + x * 3] = clamp8(r);
                mid[rowOut + x * 3 + 1] = clamp8(g);
                mid[rowOut + x * 3 + 2] = clamp8(b);
            }
        }
        Coeffs vertical = coeffs(srcH, dstH, filter);
        float[] out = new float[dstH * dstW * 3];
        int rowStride = dstW * 3;
        for (int y = 0; y < dstH; y++) {
            int start = vertical.start[y];
            float[] w = vertical.weights[y];
            for (int i = 0; i < rowStride; i++) {
                float sum = 0f;
                for (int k = 0; k < w.length; k++) sum += w[k] * mid[(start + k) * rowStride + i];
                out[y * rowStride + i] = clamp8(sum);
            }
        }
        return out;
    }

    private static float clamp8(float v) {
        float rounded = (float) Math.floor(v + 0.5f);
        return rounded < 0f ? 0f : Math.min(rounded, 255f);
    }

    private static final class Coeffs {
        final int[] start;
        final float[][] weights;

        Coeffs(int[] start, float[][] weights) {
            this.start = start;
            this.weights = weights;
        }
    }

    /** Pillow's {@code precompute_coeffs}: one window of normalised weights per output index. */
    @NonNull
    private static Coeffs coeffs(int inSize, int outSize, @NonNull Filter filter) {
        double scale = (double) inSize / outSize;
        double filterScale = Math.max(scale, 1.0);
        double support = (filter == Filter.BICUBIC ? 2.0 : 1.0) * filterScale;
        int[] start = new int[outSize];
        float[][] weights = new float[outSize][];
        for (int xx = 0; xx < outSize; xx++) {
            double center = (xx + 0.5) * scale;
            int xmin = (int) (center - support + 0.5);
            if (xmin < 0) xmin = 0;
            int xmax = (int) (center + support + 0.5);
            if (xmax > inSize) xmax = inSize;
            int n = Math.max(1, xmax - xmin);
            if (xmin + n > inSize) xmin = inSize - n;
            double[] k = new double[n];
            double total = 0.0;
            for (int x = 0; x < n; x++) {
                double w = weight(filter, (x + xmin - center + 0.5) / filterScale);
                k[x] = w;
                total += w;
            }
            float[] normalised = new float[n];
            if (total != 0.0) {
                for (int x = 0; x < n; x++) normalised[x] = (float) (k[x] / total);
            } else {
                normalised[n / 2] = 1f;
            }
            start[xx] = xmin;
            weights[xx] = normalised;
        }
        return new Coeffs(start, weights);
    }

    private static double weight(@NonNull Filter filter, double x) {
        x = Math.abs(x);
        if (filter == Filter.BILINEAR) return x < 1.0 ? 1.0 - x : 0.0;
        final double a = -0.5;
        if (x < 1.0) return ((a + 2.0) * x - (a + 3.0)) * x * x + 1.0;
        if (x < 2.0) return (((x - 5.0) * x + 8.0) * x - 4.0) * a;
        return 0.0;
    }

    // ---- Normalisation ---------------------------------------------------------------------

    /**
     * Planar {@code [1,3,H,W]} floats from interleaved 0..255 RGB: {@code (v/255 - mean) / std}.
     */
    @NonNull
    static float[] toNchw(@NonNull float[] hwc, int w, int h) {
        float scale = 1f / 255f;
        int plane = w * h;
        float[] out = new float[3 * plane];
        for (int i = 0; i < plane; i++) {
            for (int c = 0; c < 3; c++) out[c * plane + i] = (hwc[i * 3 + c] * scale - MEAN[c]) / STD[c];
        }
        return out;
    }

    /** numpy's linear-interpolation percentile of {@code values}, {@code p} in 0..100. */
    static double percentile(@NonNull float[] values, double p) {
        if (values.length == 0) return 0.0;
        float[] sorted = values.clone();
        Arrays.sort(sorted);
        double pos = p / 100.0 * (sorted.length - 1);
        int lo = (int) Math.floor(pos);
        int hi = Math.min(lo + 1, sorted.length - 1);
        double frac = pos - lo;
        return sorted[lo] + (sorted[hi] - sorted[lo]) * frac;
    }

    /**
     * A depth output as 0..1 with near = 1: clipped between its 1st and 99th percentile, and
     * flipped when the graph reports distance (Depth Anything 3: far = high) rather than disparity
     * (Depth Anything V2: near = high).
     */
    @NonNull
    static float[] depthNearOne(@NonNull float[] raw, boolean distance) {
        double lo = percentile(raw, 1.0);
        double hi = percentile(raw, 99.0);
        double span = hi - lo;
        float[] out = new float[raw.length];
        for (int i = 0; i < raw.length; i++) {
            double n = span > 1e-12 ? (raw[i] - lo) / span : 0.0;
            n = n < 0.0 ? 0.0 : Math.min(n, 1.0);
            out[i] = (float) (distance ? 1.0 - n : n);
        }
        return out;
    }

    /** 0..1 floats to 8-bit gray, clipped and rounded. */
    @NonNull
    static byte[] toGray8(@NonNull float[] unit) {
        byte[] out = new byte[unit.length];
        for (int i = 0; i < unit.length; i++) {
            float v = unit[i];
            out[i] = (byte) Math.round((v < 0f ? 0f : Math.min(v, 1f)) * 255f);
        }
        return out;
    }

    // ---- Output size and resample ----------------------------------------------------------

    /** {@code [width, height]} of the depth image: the photo's aspect, longer side {@code min(1024, photo longer side)}. */
    @NonNull
    static int[] outputSize(int photoW, int photoH) {
        int longer = Math.max(photoW, photoH);
        int side = Math.min(MAX_SIDE, longer);
        if (photoW >= photoH) return new int[] {side, Math.max(1, Math.round((float) photoH * side / photoW))};
        return new int[] {Math.max(1, Math.round((float) photoW * side / photoH)), side};
    }

    /** Bilinear resample of a {@code srcW x srcH} plane (pixel centres aligned) to {@code dstW x dstH}. */
    @NonNull
    static float[] resamplePlane(@NonNull float[] src, int srcW, int srcH, int dstW, int dstH) {
        if (src.length < srcW * srcH) throw new IllegalArgumentException("plane shorter than " + srcW + "x" + srcH);
        float[] out = new float[dstW * dstH];
        float sx = (float) srcW / dstW;
        float sy = (float) srcH / dstH;
        for (int y = 0; y < dstH; y++) {
            float fy = Math.max(0f, Math.min((y + 0.5f) * sy - 0.5f, srcH - 1));
            int y0 = (int) fy;
            int y1 = Math.min(y0 + 1, srcH - 1);
            float wy = fy - y0;
            for (int x = 0; x < dstW; x++) {
                float fx = Math.max(0f, Math.min((x + 0.5f) * sx - 0.5f, srcW - 1));
                int x0 = (int) fx;
                int x1 = Math.min(x0 + 1, srcW - 1);
                float wx = fx - x0;
                float top = src[y0 * srcW + x0] * (1f - wx) + src[y0 * srcW + x1] * wx;
                float bottom = src[y1 * srcW + x0] * (1f - wx) + src[y1 * srcW + x1] * wx;
                out[y * dstW + x] = top * (1f - wy) + bottom * wy;
            }
        }
        return out;
    }

    /**
     * The whole output step: the graph's raw {@code rawW x rawH} output to the PNG bytes of an 8-bit
     * grey image (white = near) at {@link #outputSize} of the photo.
     */
    @NonNull
    static byte[] depthPng(@NonNull float[] raw, int rawW, int rawH, boolean distance, int photoW, int photoH) {
        int[] size = outputSize(photoW, photoH);
        float[] near = depthNearOne(raw, distance);
        float[] resampled = resamplePlane(near, rawW, rawH, size[0], size[1]);
        return encodePng(toGray8(resampled), size[0], size[1], 1);
    }

    // ---- PNG -------------------------------------------------------------------------------

    /**
     * A PNG of 8-bit gray ({@code channels} 1) or RGB (3) pixels, row-major, no alpha and no
     * interlacing. Written by hand because {@code Bitmap.compress} cannot make a gray PNG and
     * writes an alpha channel for ARGB bitmaps.
     */
    @NonNull
    static byte[] encodePng(@NonNull byte[] pixels, int w, int h, int channels) {
        if (channels != 1 && channels != 3) throw new IllegalArgumentException("channels must be 1 or 3");
        if (pixels.length != w * h * channels) throw new IllegalArgumentException("pixels do not match " + w + "x" + h + "x" + channels);
        int stride = w * channels;
        byte[] raw = new byte[(stride + 1) * h];
        for (int y = 0; y < h; y++) {
            // Filter byte 0 (none) for every row.
            System.arraycopy(pixels, y * stride, raw, y * (stride + 1) + 1, stride);
        }
        Deflater deflater = new Deflater(6);
        deflater.setInput(raw);
        deflater.finish();
        ByteArrayOutputStream compressed = new ByteArrayOutputStream(raw.length / 2 + 64);
        byte[] buffer = new byte[16 * 1024];
        while (!deflater.finished()) {
            int n = deflater.deflate(buffer);
            compressed.write(buffer, 0, n);
        }
        deflater.end();
        ByteArrayOutputStream png = new ByteArrayOutputStream(compressed.size() + 128);
        png.write(new byte[] {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A}, 0, 8);
        byte[] header = new byte[13];
        putInt(header, 0, w);
        putInt(header, 4, h);
        header[8] = 8;
        header[9] = (byte) (channels == 1 ? 0 : 2);
        writeChunk(png, "IHDR", header);
        writeChunk(png, "IDAT", compressed.toByteArray());
        writeChunk(png, "IEND", new byte[0]);
        return png.toByteArray();
    }

    private static void writeChunk(@NonNull ByteArrayOutputStream out, @NonNull String type, @NonNull byte[] data) {
        byte[] length = new byte[4];
        putInt(length, 0, data.length);
        out.write(length, 0, 4);
        byte[] typeBytes = type.getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        out.write(typeBytes, 0, 4);
        out.write(data, 0, data.length);
        CRC32 crc = new CRC32();
        crc.update(typeBytes);
        crc.update(data);
        byte[] sum = new byte[4];
        putInt(sum, 0, (int) crc.getValue());
        out.write(sum, 0, 4);
    }

    private static void putInt(@NonNull byte[] out, int at, int value) {
        out[at] = (byte) (value >>> 24);
        out[at + 1] = (byte) (value >>> 16);
        out[at + 2] = (byte) (value >>> 8);
        out[at + 3] = (byte) value;
    }
}
