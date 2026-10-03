package com.termux.app.chrome.wallpaper.living;

import androidx.annotation.NonNull;

import java.util.Arrays;

/**
 * Small float-plane helpers shared by the living-still maths (living-stills.md, Part C): bilinear
 * resize, a box mean with clamped windows, smoothstep, percentiles and 4-connected components.
 * Planes are row-major {@code float[w * h]}. No Android types, so it is a unit test.
 */
public final class Planes {
    private Planes() {}

    public static float smoothstep(float a, float b, float v) {
        float t = (v - a) / (b - a);
        if (t < 0f) t = 0f;
        else if (t > 1f) t = 1f;
        return t * t * (3f - 2f * t);
    }

    @NonNull
    public static float[] smooth(@NonNull float[] src, float a, float b) {
        float[] out = new float[src.length];
        for (int i = 0; i < src.length; i++) out[i] = smoothstep(a, b, src[i]);
        return out;
    }

    public static float clamp01(float v) {
        return v < 0f ? 0f : Math.min(v, 1f);
    }

    /** Bilinear resize with pixel-centre alignment. */
    @NonNull
    public static float[] resize(@NonNull float[] src, int sw, int sh, int dw, int dh) {
        float[] out = new float[dw * dh];
        if (sw == dw && sh == dh) {
            System.arraycopy(src, 0, out, 0, src.length);
            return out;
        }
        for (int y = 0; y < dh; y++) {
            float sy = (y + 0.5f) * sh / dh - 0.5f;
            int y0 = (int) Math.floor(sy);
            float fy = sy - y0;
            int y1 = Math.min(sh - 1, Math.max(0, y0 + 1));
            y0 = Math.min(sh - 1, Math.max(0, y0));
            for (int x = 0; x < dw; x++) {
                float sx = (x + 0.5f) * sw / dw - 0.5f;
                int x0 = (int) Math.floor(sx);
                float fx = sx - x0;
                int x1 = Math.min(sw - 1, Math.max(0, x0 + 1));
                x0 = Math.min(sw - 1, Math.max(0, x0));
                float top = src[y0 * sw + x0] * (1f - fx) + src[y0 * sw + x1] * fx;
                float bot = src[y1 * sw + x0] * (1f - fx) + src[y1 * sw + x1] * fx;
                out[y * dw + x] = top * (1f - fy) + bot * fy;
            }
        }
        return out;
    }

    /** Mean over the (2r+1) square window, shrunk at the borders so edges are not darkened. */
    @NonNull
    public static float[] boxMean(@NonNull float[] src, int w, int h, int r) {
        int iw = w + 1;
        double[] sum = new double[iw * (h + 1)];
        for (int y = 0; y < h; y++) {
            double row = 0;
            for (int x = 0; x < w; x++) {
                row += src[y * w + x];
                sum[(y + 1) * iw + x + 1] = sum[y * iw + x + 1] + row;
            }
        }
        float[] out = new float[w * h];
        for (int y = 0; y < h; y++) {
            int y0 = Math.max(0, y - r);
            int y1 = Math.min(h, y + r + 1);
            for (int x = 0; x < w; x++) {
                int x0 = Math.max(0, x - r);
                int x1 = Math.min(w, x + r + 1);
                double s = sum[y1 * iw + x1] - sum[y0 * iw + x1] - sum[y1 * iw + x0] + sum[y0 * iw + x0];
                out[y * w + x] = (float) (s / ((double) (x1 - x0) * (y1 - y0)));
            }
        }
        return out;
    }

    public static float mean(@NonNull float[] v) {
        if (v.length == 0) return 0f;
        double s = 0;
        for (float f : v) s += f;
        return (float) (s / v.length);
    }

    /** The share of pixels above {@code threshold}. */
    public static float coverage(@NonNull float[] v, float threshold) {
        if (v.length == 0) return 0f;
        int n = 0;
        for (float f : v) if (f > threshold) n++;
        return n / (float) v.length;
    }

    /** {@code p} in 0..1, nearest rank. */
    public static float percentile(@NonNull float[] v, float p) {
        if (v.length == 0) return 0f;
        float[] s = Arrays.copyOf(v, v.length);
        Arrays.sort(s);
        int i = Math.round(p * (s.length - 1));
        return s[Math.max(0, Math.min(s.length - 1, i))];
    }

    /** 4-connected components of a mask: ids 1..count, 0 for background. */
    public static final class Components {
        public final int[] id;
        public final int count;
        /** Size, top row and bottom row of each component, index = id. */
        public final int[] size;
        public final int[] top;
        public final int[] bottom;

        Components(int[] id, int count, int[] size, int[] top, int[] bottom) {
            this.id = id;
            this.count = count;
            this.size = size;
            this.top = top;
            this.bottom = bottom;
        }
    }

    @NonNull
    public static Components components(@NonNull boolean[] on, int w, int h) {
        int[] id = new int[w * h];
        int[] stack = new int[w * h];
        int count = 0;
        int[] size = new int[16];
        int[] top = new int[16];
        int[] bottom = new int[16];
        for (int start = 0; start < on.length; start++) {
            if (!on[start] || id[start] != 0) continue;
            count++;
            if (count >= size.length) {
                size = Arrays.copyOf(size, size.length * 2);
                top = Arrays.copyOf(top, top.length * 2);
                bottom = Arrays.copyOf(bottom, bottom.length * 2);
            }
            int sp = 0;
            stack[sp++] = start;
            id[start] = count;
            top[count] = h;
            bottom[count] = -1;
            while (sp > 0) {
                int p = stack[--sp];
                int y = p / w;
                int x = p - y * w;
                size[count]++;
                if (y < top[count]) top[count] = y;
                if (y > bottom[count]) bottom[count] = y;
                if (x > 0 && on[p - 1] && id[p - 1] == 0) { id[p - 1] = count; stack[sp++] = p - 1; }
                if (x < w - 1 && on[p + 1] && id[p + 1] == 0) { id[p + 1] = count; stack[sp++] = p + 1; }
                if (y > 0 && on[p - w] && id[p - w] == 0) { id[p - w] = count; stack[sp++] = p - w; }
                if (y < h - 1 && on[p + w] && id[p + w] == 0) { id[p + w] = count; stack[sp++] = p + w; }
            }
        }
        return new Components(id, count, size, top, bottom);
    }
}
