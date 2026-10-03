package com.termux.ai;

import androidx.annotation.NonNull;

import java.io.ByteArrayOutputStream;
import java.util.Arrays;
import java.util.zip.CRC32;
import java.util.zip.Deflater;

/**
 * The pure half of the wallpaper analysis ({@link WallpaperVisionRuntime}): resizing a photo to a
 * graph's input, the ImageNet normalisation each graph expects, and turning each graph's raw output
 * into the maps the job writes. No Android types, so every step is covered by JVM tests against
 * the Python reference in {@code wall-alive/litert_check.py}.
 *
 * <p>Pixels are packed ARGB ints in, planar floats out. The resize follows Pillow's algorithm
 * (separable, filter support widened by the shrink factor so a big photo is area-averaged rather
 * than aliased) and rounds to 8 bits like the reference's uint8 images.
 *
 * <p>All maps are written as plain 8-bit gray or RGB PNG, never with alpha: Android, Pillow and
 * browsers premultiply alpha and wipe colour where it is 0.
 */
final class WallpaperVisionMath {
    private WallpaperVisionMath() {}

    static final float[] MEAN = {0.485f, 0.456f, 0.406f};
    static final float[] STD = {0.229f, 0.224f, 0.225f};

    /** Which Pillow filter a graph's reference preprocessing uses. */
    enum Filter { BILINEAR, BICUBIC }

    /** Where each scene group's probability goes; the order here is the order in {@code scene.json}. */
    static final String[] SCENE_GROUP_NAMES = {"water", "falling_water", "sky", "foliage", "lights", "signs", "subject_like"};
    /** ADE20K class ids (0-based, {@code nvidia/segformer-b0-finetuned-ade-512-512}), one row per group. */
    static final int[][] SCENE_GROUP_CLASSES = {
        {21, 26, 60, 109, 128},
        {113, 104},
        {2},
        {4, 9, 17, 66, 72},
        {36, 82, 85, 87, 134},
        {43, 100, 130},
        {12, 126, 20, 76, 127, 116},
    };
    /** Groups packed into one RGB scene PNG. */
    static final int GROUPS_PER_IMAGE = 3;

    static int sceneImageCount() {
        return (SCENE_GROUP_NAMES.length + GROUPS_PER_IMAGE - 1) / GROUPS_PER_IMAGE;
    }

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
     * Planar {@code [1,3,H,W]} floats from interleaved 0..255 RGB: {@code (v/255 - mean) / std}, or
     * with {@code divideByMax} (U-2-Net) {@code v} divided by the image's own largest value first,
     * as {@code rembg} does, before the same mean/std.
     */
    @NonNull
    static float[] toNchw(@NonNull float[] hwc, int w, int h, boolean divideByMax) {
        float scale = 1f / 255f;
        if (divideByMax) {
            float max = 0f;
            for (float v : hwc) if (v > max) max = v;
            scale = max > 0f ? 1f / max : 1f;
        }
        int plane = w * h;
        float[] out = new float[3 * plane];
        for (int i = 0; i < plane; i++) {
            for (int c = 0; c < 3; c++) out[c * plane + i] = (hwc[i * 3 + c] * scale - MEAN[c]) / STD[c];
        }
        return out;
    }

    // ---- Depth -----------------------------------------------------------------------------

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

    // ---- Scene -----------------------------------------------------------------------------

    /**
     * Softmax over the class axis of NHWC logits {@code [h*w, classes]}, then the summed
     * probability of each group's classes: one {@code h*w} map per group, 0..1. A class id past
     * {@code classes} contributes nothing.
     */
    @NonNull
    static float[][] sceneGroups(@NonNull float[] logits, int h, int w, int classes, @NonNull int[][] groups) {
        int pixels = h * w;
        if (logits.length < pixels * classes) throw new IllegalArgumentException("logits shorter than " + pixels + "x" + classes);
        float[][] out = new float[groups.length][pixels];
        float[] exp = new float[classes];
        for (int p = 0; p < pixels; p++) {
            int base = p * classes;
            float max = Float.NEGATIVE_INFINITY;
            for (int c = 0; c < classes; c++) if (logits[base + c] > max) max = logits[base + c];
            double sum = 0.0;
            for (int c = 0; c < classes; c++) {
                exp[c] = (float) Math.exp(logits[base + c] - max);
                sum += exp[c];
            }
            for (int g = 0; g < groups.length; g++) {
                double mass = 0.0;
                for (int c : groups[g]) if (c >= 0 && c < classes) mass += exp[c];
                out[g][p] = (float) (mass / sum);
            }
        }
        return out;
    }

    /**
     * The {@code index}th scene PNG's pixels: interleaved RGB where R, G, B are the next three
     * groups (0..255); a channel with no group is 0.
     */
    @NonNull
    static byte[] packSceneRgb(@NonNull float[][] groups, int pixels, int index) {
        byte[] out = new byte[pixels * 3];
        for (int c = 0; c < GROUPS_PER_IMAGE; c++) {
            int g = index * GROUPS_PER_IMAGE + c;
            if (g >= groups.length) continue;
            byte[] gray = toGray8(groups[g]);
            for (int p = 0; p < pixels; p++) out[p * 3 + c] = gray[p];
        }
        return out;
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
