package com.termux.app.chrome.wallpaper.living;

import androidx.annotation.NonNull;

import java.util.Arrays;
import java.util.Random;

/**
 * Seeded k-means on the photo in CIE Lab with a small position term (living-stills.md, Part C.1).
 * The photo is small (~270 px wide), so one pass is a few hundred thousand distance sums. After
 * the loop clusters are renumbered by area, largest first, so mark 1 is the biggest region; each
 * cluster's mark sits on its largest connected component. The same seed and pixels always give
 * the same labels, which the tests rely on.
 */
public final class ColourClusters {
    private ColourClusters() {}

    public static final int DEFAULT_K = 10;
    /** How far the full width or height weighs against Lab distance. */
    public static final float DEFAULT_POSITION_WEIGHT = 12f;
    private static final int ITERATIONS = 14;

    public static final class Result {
        public final int w;
        public final int h;
        public final int k;
        /** Cluster index 0..k-1 per pixel; the mark number is index + 1. */
        public final int[] labels;
        /** Share of the picture, per cluster. */
        public final float[] area;
        /** Mean colour per cluster, 0xRRGGBB. */
        public final int[] meanRgb;
        /** Where each cluster's number is drawn: inside its largest connected component. */
        public final int[] markX;
        public final int[] markY;

        Result(int w, int h, int k, int[] labels, float[] area, int[] meanRgb, int[] markX, int[] markY) {
            this.w = w;
            this.h = h;
            this.k = k;
            this.labels = labels;
            this.area = area;
            this.meanRgb = meanRgb;
            this.markX = markX;
            this.markY = markY;
        }

        /** True where a pixel's right or lower neighbour belongs to another cluster. */
        @NonNull
        public boolean[] boundary() {
            boolean[] b = new boolean[w * h];
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    int i = y * w + x;
                    if ((x + 1 < w && labels[i] != labels[i + 1]) || (y + 1 < h && labels[i] != labels[i + w])) b[i] = true;
                }
            }
            return b;
        }
    }

    @NonNull
    public static Result compute(@NonNull int[] argb, int w, int h, int k, long seed) {
        return compute(argb, w, h, k, seed, DEFAULT_POSITION_WEIGHT);
    }

    @NonNull
    public static Result compute(@NonNull int[] argb, int w, int h, int k, long seed, float positionWeight) {
        int n = w * h;
        k = Math.max(1, Math.min(k, n));
        float[][] f = new float[5][n];
        for (int i = 0; i < n; i++) {
            float[] lab = lab(argb[i]);
            f[0][i] = lab[0];
            f[1][i] = lab[1];
            f[2][i] = lab[2];
            f[3][i] = positionWeight * (i % w) / Math.max(1, w - 1);
            f[4][i] = positionWeight * (i / w) / Math.max(1, h - 1);
        }
        Random rnd = new Random(seed);
        float[][] c = new float[k][5];
        // k-means++ seeding
        int first = rnd.nextInt(n);
        for (int d = 0; d < 5; d++) c[0][d] = f[d][first];
        float[] best = new float[n];
        Arrays.fill(best, Float.MAX_VALUE);
        for (int ci = 1; ci < k; ci++) {
            double total = 0;
            for (int i = 0; i < n; i++) {
                float d2 = dist2(f, i, c[ci - 1]);
                if (d2 < best[i]) best[i] = d2;
                total += best[i];
            }
            int pick = n - 1;
            double target = rnd.nextDouble() * total;
            double run = 0;
            for (int i = 0; i < n; i++) {
                run += best[i];
                if (run >= target && best[i] > 0f) { pick = i; break; }
            }
            for (int d = 0; d < 5; d++) c[ci][d] = f[d][pick];
        }
        int[] label = new int[n];
        Arrays.fill(label, -1);
        double[][] sum = new double[k][5];
        int[] count = new int[k];
        for (int it = 0; it < ITERATIONS; it++) {
            boolean moved = false;
            for (int i = 0; i < n; i++) {
                int bi = 0;
                float bd = Float.MAX_VALUE;
                for (int ci = 0; ci < k; ci++) {
                    float d2 = dist2(f, i, c[ci]);
                    if (d2 < bd) { bd = d2; bi = ci; }
                }
                if (label[i] != bi) moved = true;
                label[i] = bi;
            }
            if (!moved) break;
            for (double[] s : sum) Arrays.fill(s, 0);
            Arrays.fill(count, 0);
            for (int i = 0; i < n; i++) {
                int l = label[i];
                count[l]++;
                for (int d = 0; d < 5; d++) sum[l][d] += f[d][i];
            }
            for (int ci = 0; ci < k; ci++) {
                if (count[ci] == 0) continue;
                for (int d = 0; d < 5; d++) c[ci][d] = (float) (sum[ci][d] / count[ci]);
            }
        }
        return finish(argb, w, h, k, label);
    }

    private static Result finish(int[] argb, int w, int h, int k, int[] label) {
        int n = w * h;
        int[] cnt = new int[k];
        for (int l : label) cnt[l]++;
        // renumber by area, largest first; ties by old index
        Integer[] order = new Integer[k];
        for (int i = 0; i < k; i++) order[i] = i;
        Arrays.sort(order, (a, b) -> cnt[a] != cnt[b] ? Integer.compare(cnt[b], cnt[a]) : Integer.compare(a, b));
        int[] remap = new int[k];
        for (int i = 0; i < k; i++) remap[order[i]] = i;
        int[] labels = new int[n];
        float[] area = new float[k];
        double[][] rgb = new double[k][3];
        for (int i = 0; i < n; i++) {
            int l = remap[label[i]];
            labels[i] = l;
            area[l]++;
            rgb[l][0] += (argb[i] >> 16) & 0xFF;
            rgb[l][1] += (argb[i] >> 8) & 0xFF;
            rgb[l][2] += argb[i] & 0xFF;
        }
        int[] mean = new int[k];
        for (int l = 0; l < k; l++) {
            if (area[l] > 0) {
                int r = (int) Math.round(rgb[l][0] / area[l]);
                int g = (int) Math.round(rgb[l][1] / area[l]);
                int b = (int) Math.round(rgb[l][2] / area[l]);
                mean[l] = (r << 16) | (g << 8) | b;
            }
            area[l] /= n;
        }
        // largest connected component per cluster, then the pixel of it nearest its centroid
        int[] comp = new int[n];
        int[] stack = new int[n];
        int[] bestSize = new int[k];
        int[] bestStart = new int[k];
        Arrays.fill(bestStart, -1);
        int nextId = 0;
        for (int s = 0; s < n; s++) {
            if (comp[s] != 0) continue;
            nextId++;
            int l = labels[s];
            int sp = 0;
            stack[sp++] = s;
            comp[s] = nextId;
            int size = 0;
            while (sp > 0) {
                int p = stack[--sp];
                size++;
                int y = p / w;
                int x = p - y * w;
                if (x > 0 && comp[p - 1] == 0 && labels[p - 1] == l) { comp[p - 1] = nextId; stack[sp++] = p - 1; }
                if (x < w - 1 && comp[p + 1] == 0 && labels[p + 1] == l) { comp[p + 1] = nextId; stack[sp++] = p + 1; }
                if (y > 0 && comp[p - w] == 0 && labels[p - w] == l) { comp[p - w] = nextId; stack[sp++] = p - w; }
                if (y < h - 1 && comp[p + w] == 0 && labels[p + w] == l) { comp[p + w] = nextId; stack[sp++] = p + w; }
            }
            if (size > bestSize[l]) { bestSize[l] = size; bestStart[l] = s; }
        }
        int[] markX = new int[k];
        int[] markY = new int[k];
        for (int l = 0; l < k; l++) {
            if (bestStart[l] < 0) continue;
            int id = comp[bestStart[l]];
            double sx = 0, sy = 0;
            int m = 0;
            for (int i = 0; i < n; i++) {
                if (comp[i] == id) { sx += i % w; sy += i / w; m++; }
            }
            double cx = sx / m, cy = sy / m;
            double bd = Double.MAX_VALUE;
            for (int i = 0; i < n; i++) {
                if (comp[i] != id) continue;
                double dx = i % w - cx, dy = i / w - cy;
                double d = dx * dx + dy * dy;
                if (d < bd) { bd = d; markX[l] = i % w; markY[l] = i / w; }
            }
        }
        return new Result(w, h, k, labels, area, mean, markX, markY);
    }

    private static float dist2(float[][] f, int i, float[] c) {
        float s = 0;
        for (int d = 0; d < 5; d++) {
            float t = f[d][i] - c[d];
            s += t * t;
        }
        return s;
    }

    /** sRGB (0xAARRGGBB, alpha ignored) to CIE Lab, D65. */
    @NonNull
    public static float[] lab(int argb) {
        double r = lin(((argb >> 16) & 0xFF) / 255.0);
        double g = lin(((argb >> 8) & 0xFF) / 255.0);
        double b = lin((argb & 0xFF) / 255.0);
        double x = (0.4124564 * r + 0.3575761 * g + 0.1804375 * b) / 0.95047;
        double y = 0.2126729 * r + 0.7151522 * g + 0.0721750 * b;
        double z = (0.0193339 * r + 0.1191920 * g + 0.9503041 * b) / 1.08883;
        double fx = labF(x), fy = labF(y), fz = labF(z);
        return new float[] {(float) (116 * fy - 16), (float) (500 * (fx - fy)), (float) (200 * (fy - fz))};
    }

    private static double lin(double c) {
        return c <= 0.04045 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4);
    }

    private static double labF(double t) {
        return t > 0.008856 ? Math.cbrt(t) : 7.787 * t + 16.0 / 116.0;
    }
}
