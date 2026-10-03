package com.termux.app.chrome.wallpaper.living;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Map;

/**
 * Turns the raw model maps into the nine region masks and the scene statistics the recipe rules
 * read (living-stills.md, Part C.2). Each group probability (128x128) is resized to the mask size
 * and refined with the photo as the guide so edges follow the art; the subject is the saliency
 * map gated by near depth or subject-like labels; lights are bright saturated small blobs. When
 * Gemma named colour regions, those clusters replace the group's mask; a group Gemma left empty
 * keeps the SegFormer mask only if it is confident (mean probability above 0.6) and big enough
 * (coverage above 3%).
 *
 * <p>Plain Java over float planes at the mask size (photo width / 4, so about 400x600).</p>
 */
public final class RegionMasks {
    private RegionMasks() {}

    static final int GUIDE_RADIUS = 8;
    static final float GUIDE_EPS = 1e-3f;
    static final float MIN_WATER = 0.03f;
    static final float MIN_FOLIAGE = 0.03f;
    static final float MIN_SKY = 0.05f;
    static final float MIN_FALL = 0.005f;
    static final float GEMMA_KEEP_MEAN = 0.6f;
    static final float GEMMA_KEEP_COVERAGE = 0.03f;

    /** Everything the masks are made from, all at {@code w x h} unless noted. */
    public static final class Inputs {
        public final int w;
        public final int h;
        /** Photo at mask size, 0xRRGGBB per pixel. */
        @NonNull public final int[] rgb;
        /** Near = 1. */
        @NonNull public final float[] depth;
        @NonNull public final float[] saliency;
        /** Group probability planes, already at mask size, keyed by scene.json names. */
        @NonNull public final Map<String, float[]> groups;

        public Inputs(int w, int h, @NonNull int[] rgb, @NonNull float[] depth, @NonNull float[] saliency,
                      @NonNull Map<String, float[]> groups) {
            this.w = w;
            this.h = h;
            this.rgb = rgb;
            this.depth = depth;
            this.saliency = saliency;
            this.groups = groups;
        }
    }

    /** Numbers the rules read; coverage values are shares of the picture above 0.5. */
    public static final class Stats {
        public float water, fall, sky, foliage, glow, subject, bob;
        /** Depth 95th minus 5th percentile. */
        public float depthSpread;
        /** Standard deviation of depth inside the water. */
        public float waterDepthStd;
        /** Luminance step across the water's top edge, 0..1. */
        public float horizon;
        public float sceneLum;
        /** Mean luminance inside the sky; equals the scene's when there is no sky. */
        public float skyLum;
        /** Share of the glow that sits inside sign regions. */
        public float lightsInSigns;
        /** Average colour of the far part of the picture, 0..1 each. */
        @NonNull public float[] farColour = {0.5f, 0.5f, 0.5f};
    }

    public static final class Result {
        public final int w;
        public final int h;
        public final float[] water, sway, sky, fall, subject, glow, bob, mist, particles;
        @NonNull public final Stats stats;

        Result(int w, int h, float[] water, float[] sway, float[] sky, float[] fall, float[] subject,
               float[] glow, float[] bob, float[] mist, float[] particles, @NonNull Stats stats) {
            this.w = w;
            this.h = h;
            this.water = water;
            this.sway = sway;
            this.sky = sky;
            this.fall = fall;
            this.subject = subject;
            this.glow = glow;
            this.bob = bob;
            this.mist = mist;
            this.particles = particles;
            this.stats = stats;
        }
    }

    /**
     * @param clusters the colour clusters (any size; read nearest-neighbour at mask size)
     * @param picks    Gemma's picks, group key to 1-based marks; {@code null} when Gemma did not run
     */
    @NonNull
    public static Result compute(@NonNull Inputs in, @NonNull ColourClusters.Result clusters,
                                 @Nullable Map<String, int[]> picks) {
        final int w = in.w, h = in.h, n = w * h;
        final boolean gemma = picks != null;
        float[] lum = new float[n];
        float[] sat = new float[n];
        for (int i = 0; i < n; i++) {
            float r = ((in.rgb[i] >> 16) & 0xFF) / 255f;
            float g = ((in.rgb[i] >> 8) & 0xFF) / 255f;
            float b = (in.rgb[i] & 0xFF) / 255f;
            lum[i] = 0.299f * r + 0.587f * g + 0.114f * b;
            float mx = Math.max(r, Math.max(g, b));
            float mn = Math.min(r, Math.min(g, b));
            sat[i] = mx <= 0f ? 0f : (mx - mn) / mx;
        }
        int[] lab = new int[n];
        for (int y = 0; y < h; y++) {
            int sy = Math.min(clusters.h - 1, y * clusters.h / h);
            for (int x = 0; x < w; x++) {
                lab[y * w + x] = clusters.labels[sy * clusters.w + Math.min(clusters.w - 1, x * clusters.w / w)];
            }
        }

        float[] fallM = group(in, lum, lab, picks, "falling_water", "falling_water", MIN_FALL, gemma);
        float[] waterRaw = group(in, lum, lab, picks, "water", "water", MIN_WATER, gemma);
        float[] skyM = group(in, lum, lab, picks, "sky", "sky", MIN_SKY, gemma);
        float[] foliage = group(in, lum, lab, picks, "foliage", "foliage", MIN_FOLIAGE, gemma);

        // subject: saliency gated by near depth or subject-like labels
        float[] subject;
        int[] subjPick = pick(picks, "subject");
        if (subjPick != null) {
            subject = clusterMask(in, lum, lab, subjPick);
        } else {
            float[] sal = GuidedFilter.filter(lum, in.saliency, w, h, GUIDE_RADIUS, GUIDE_EPS);
            float[] like = in.groups.get("subject_like");
            subject = new float[n];
            for (int i = 0; i < n; i++) {
                float near = Planes.smoothstep(0.45f, 0.65f, in.depth[i]);
                float lk = like == null ? 0f : like[i];
                subject[i] = sal[i] * Math.max(near, lk);
            }
            subject = Planes.smooth(subject, 0.3f, 0.7f);
        }

        float[] fall = new float[n];
        float[] water = new float[n];
        float[] sky = new float[n];
        for (int i = 0; i < n; i++) {
            fall[i] = fallM[i] * (1f - subject[i]);
            water[i] = waterRaw[i] * (1f - fall[i]) * (1f - subject[i]);
            sky[i] = skyM[i] * (1f - subject[i]);
        }

        // lights
        float[] glow = glow(in, lum, sat, lab, picks);

        // bob: the subject where it stands in or beside water
        float[] nearWater = Planes.boxMean(waterRaw, w, h, 12);
        float[] bob = new float[n];
        for (int i = 0; i < n; i++) bob[i] = subject[i] * Planes.smoothstep(0.1f, 0.4f, nearWater[i]);

        float[] sway = sway(foliage, w, h);

        // mist: far and low in the picture, away from the subject
        float[] mist = new float[n];
        float[] particles = new float[n];
        for (int y = 0; y < h; y++) {
            float low = Planes.smoothstep(0.2f, 0.7f, y / (float) Math.max(1, h - 1));
            for (int x = 0; x < w; x++) {
                int i = y * w + x;
                float far = Planes.clamp01((0.75f - in.depth[i]) / 0.6f);
                mist[i] = far * (0.4f + 0.6f * low) * (1f - subject[i]);
                particles[i] = (1f - subject[i]) * (0.35f + 0.65f * (1f - in.depth[i]));
            }
        }

        Stats s = new Stats();
        s.water = Planes.coverage(water, 0.5f);
        s.fall = Planes.coverage(fall, 0.5f);
        s.sky = Planes.coverage(sky, 0.5f);
        s.foliage = Planes.coverage(foliage, 0.5f);
        s.glow = Planes.coverage(glow, 0.5f);
        s.subject = Planes.coverage(subject, 0.5f);
        float subjectArea = s.subject;
        s.bob = subjectArea > 0f ? Planes.coverage(bob, 0.5f) / subjectArea : 0f;
        s.depthSpread = Planes.percentile(in.depth, 0.95f) - Planes.percentile(in.depth, 0.05f);
        s.sceneLum = Planes.mean(lum);
        s.skyLum = s.sky > 0f ? meanWhere(lum, sky) : s.sceneLum;
        s.waterDepthStd = s.water > 0f ? stdWhere(in.depth, water) : 0f;
        s.horizon = s.water > 0f ? horizon(lum, water, w, h) : 0f;
        float[] signs = in.groups.get("signs");
        if (s.glow > 0f && signs != null) {
            int g = 0, inSigns = 0;
            for (int i = 0; i < n; i++) {
                if (glow[i] > 0.5f) {
                    g++;
                    if (signs[i] > 0.4f) inSigns++;
                }
            }
            s.lightsInSigns = g == 0 ? 0f : inSigns / (float) g;
        }
        s.farColour = farColour(in.rgb, in.depth);
        return new Result(w, h, water, sway, sky, fall, subject, glow, bob, mist, particles, s);
    }

    @Nullable
    private static int[] pick(@Nullable Map<String, int[]> picks, String key) {
        if (picks == null) return null;
        int[] p = picks.get(key);
        return p != null && p.length > 0 ? p : null;
    }

    /** Guided, hardened indicator of the picked clusters. */
    @NonNull
    private static float[] clusterMask(Inputs in, float[] lum, int[] lab, int[] marks) {
        int n = in.w * in.h;
        boolean[] want = new boolean[64];
        for (int m : marks) if (m >= 1 && m - 1 < want.length) want[m - 1] = true;
        float[] ind = new float[n];
        for (int i = 0; i < n; i++) ind[i] = lab[i] < want.length && want[lab[i]] ? 1f : 0f;
        return Planes.smooth(GuidedFilter.filter(lum, ind, in.w, in.h, GUIDE_RADIUS, GUIDE_EPS), 0.35f, 0.65f);
    }

    /** One group's mask: Gemma's clusters if it picked, else the SegFormer map if confident. */
    @NonNull
    private static float[] group(Inputs in, float[] lum, int[] lab, @Nullable Map<String, int[]> picks,
                                 String pickKey, String groupName, float minCoverage, boolean gemma) {
        int[] p = pick(picks, pickKey);
        if (p != null) return clusterMask(in, lum, lab, p);
        int n = in.w * in.h;
        float[] raw = in.groups.get(groupName);
        if (raw == null) return new float[n];
        float[] m = Planes.smooth(GuidedFilter.filter(lum, raw, in.w, in.h, GUIDE_RADIUS, GUIDE_EPS), 0.3f, 0.7f);
        float cov = Planes.coverage(m, 0.5f);
        if (cov < minCoverage) return new float[n];
        if (gemma) {
            double sum = 0;
            int c = 0;
            for (int i = 0; i < n; i++) {
                if (m[i] > 0.5f) { sum += raw[i]; c++; }
            }
            float mean = c == 0 ? 0f : (float) (sum / c);
            if (mean <= GEMMA_KEEP_MEAN || cov <= GEMMA_KEEP_COVERAGE) return new float[n];
        }
        return m;
    }

    /** Bright, saturated, small blobs inside light/sign regions, or anywhere in a dark scene. */
    @NonNull
    private static float[] glow(Inputs in, float[] lum, float[] sat, int[] lab, @Nullable Map<String, int[]> picks) {
        int w = in.w, h = in.h, n = w * h;
        int[] p = pick(picks, "lights");
        if (p != null) return clusterMask(in, lum, lab, p);
        float[] lights = in.groups.get("lights");
        float[] signs = in.groups.get("signs");
        boolean dark = Planes.mean(lum) < 0.3f;
        float top = Planes.percentile(lum, 0.95f);
        boolean[] cand = new boolean[n];
        for (int i = 0; i < n; i++) {
            float region = Math.max(lights == null ? 0f : lights[i], signs == null ? 0f : signs[i]);
            boolean inside = dark || region > 0.3f;
            cand[i] = inside && lum[i] >= top && sat[i] > 0.25f;
        }
        Planes.Components c = Planes.components(cand, w, h);
        float[] blob = new float[n];
        int maxSize = Math.max(8, n * 3 / 100);
        for (int i = 0; i < n; i++) {
            int id = c.id[i];
            if (id > 0 && c.size[id] <= maxSize) blob[i] = 1f;
        }
        return Planes.smooth(Planes.boxMean(blob, w, h, 2), 0.05f, 0.4f);
    }

    /**
     * Sway weights: the foliage mask, strongest at the top of each connected part (the tips of
     * plants growing up) and a quarter at its base, so roots and trunks stay put.
     */
    @NonNull
    private static float[] sway(float[] foliage, int w, int h) {
        int n = w * h;
        boolean[] on = new boolean[n];
        for (int i = 0; i < n; i++) on[i] = foliage[i] > 0.5f;
        Planes.Components c = Planes.components(on, w, h);
        float[] out = new float[n];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int i = y * w + x;
                if (foliage[i] <= 0f) continue;
                int id = c.id[i];
                float weight = 0.25f;
                if (id > 0) {
                    float span = Math.max(1, c.bottom[id] - c.top[id]);
                    float t = (y - c.top[id]) / span;
                    weight = 0.25f + 0.75f * (1f - t);
                }
                out[i] = foliage[i] * weight;
            }
        }
        return out;
    }

    private static float meanWhere(float[] v, float[] mask) {
        double s = 0, m = 0;
        for (int i = 0; i < v.length; i++) {
            if (mask[i] > 0.5f) { s += v[i]; m++; }
        }
        return m == 0 ? 0f : (float) (s / m);
    }

    private static float stdWhere(float[] v, float[] mask) {
        double s = 0, s2 = 0, m = 0;
        for (int i = 0; i < v.length; i++) {
            if (mask[i] > 0.5f) { s += v[i]; s2 += (double) v[i] * v[i]; m++; }
        }
        if (m == 0) return 0f;
        double mean = s / m;
        return (float) Math.sqrt(Math.max(0, s2 / m - mean * mean));
    }

    /** Luminance step between the bands just above and just below the water's top row. */
    private static float horizon(float[] lum, float[] water, int w, int h) {
        int top = -1;
        for (int y = 0; y < h && top < 0; y++) {
            int c = 0;
            for (int x = 0; x < w; x++) if (water[y * w + x] > 0.5f) c++;
            if (c > w / 2) top = y;
        }
        if (top < 0) return 0f;
        int band = Math.max(2, h / 100);
        if (top - band < 0 || top + band >= h) return 0f;
        double above = 0, below = 0;
        for (int y = 1; y <= band; y++) {
            for (int x = 0; x < w; x++) {
                above += lum[(top - y) * w + x];
                below += lum[(top + y - 1) * w + x];
            }
        }
        return (float) Math.abs(above - below) / (band * w);
    }

    /** Mean colour of the far third of the picture, as a fog colour. */
    @NonNull
    private static float[] farColour(int[] rgb, float[] depth) {
        float cut = Planes.percentile(depth, 0.33f);
        double r = 0, g = 0, b = 0;
        int c = 0;
        for (int i = 0; i < rgb.length; i++) {
            if (depth[i] > cut) continue;
            r += (rgb[i] >> 16) & 0xFF;
            g += (rgb[i] >> 8) & 0xFF;
            b += rgb[i] & 0xFF;
            c++;
        }
        if (c == 0) return new float[] {0.5f, 0.5f, 0.5f};
        return new float[] {(float) (r / c / 255.0), (float) (g / c / 255.0), (float) (b / c / 255.0)};
    }
}
