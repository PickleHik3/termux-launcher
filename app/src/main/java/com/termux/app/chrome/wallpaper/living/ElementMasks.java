package com.termux.app.chrome.wallpaper.living;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Turns the director's element boxes into effect masks without a new model (living-stills Part C
 * v2, round 1 / B). Per element: the feathered box, the depth band read from the depth
 * percentiles inside the box, the colour clusters that sit mostly inside the box, then a guided
 * filter against the photo. Overlaps go to the nearer element; the kinds then map onto the
 * output planes the recipe rules and the shader read. {@link RegionMasks} stays as the fallback
 * when no director ran.
 *
 * <p>Plain Java over float planes at the mask size.</p>
 */
public final class ElementMasks {
    private ElementMasks() {}

    static final int FEATHER = 2;
    static final float BAND_WIDEN = 0.1f;
    static final float CLUSTER_INSIDE = 0.6f;
    static final float SEG_CONFIDENT = 0.6f;
    static final float SKY_ABOVE_DEPTH = 0.15f;

    public static final class Result {
        public final int w, h;
        public final float[] sky, water, fall, sway, wind, glow, subject, still, mist, particles;
        @NonNull public final RegionMasks.Stats stats;
        @NonNull public final List<String> warnings;

        Result(int w, int h, float[] sky, float[] water, float[] fall, float[] sway, float[] wind, float[] glow,
               float[] subject, float[] still, float[] mist, float[] particles,
               @NonNull RegionMasks.Stats stats, @NonNull List<String> warnings) {
            this.w = w;
            this.h = h;
            this.sky = sky;
            this.water = water;
            this.fall = fall;
            this.sway = sway;
            this.wind = wind;
            this.glow = glow;
            this.subject = subject;
            this.still = still;
            this.mist = mist;
            this.particles = particles;
            this.stats = stats;
            this.warnings = warnings;
        }
    }

    /** One element's pixels while the planes are being made. */
    private static final class Live {
        final ScenePlan.Element e;
        final int x0, y0, x1, y1;
        final float area;
        final int rank;
        float[] mask;
        boolean dropped;

        Live(ScenePlan.Element e, int w, int h) {
            this.e = e;
            int top = Math.min(e.box[0], e.box[2]), bottom = Math.max(e.box[0], e.box[2]);
            int left = Math.min(e.box[1], e.box[3]), right = Math.max(e.box[1], e.box[3]);
            y0 = clamp((int) Math.floor(top * h / 1000.0), 0, h - 1);
            y1 = clamp((int) Math.ceil(bottom * h / 1000.0), y0 + 1, h);
            x0 = clamp((int) Math.floor(left * w / 1000.0), 0, w - 1);
            x1 = clamp((int) Math.ceil(right * w / 1000.0), x0 + 1, w);
            area = (x1 - x0) * (float) (y1 - y0);
            rank = "near".equals(e.depth) ? 2 : "middle".equals(e.depth) ? 1 : 0;
        }

        boolean in(int x, int y) {
            return x >= x0 && x < x1 && y >= y0 && y < y1;
        }
    }

    @NonNull
    public static Result compute(@NonNull ScenePlan plan, @NonNull RegionMasks.Inputs in,
                                 @NonNull ColourClusters.Result clusters, int maskW, int maskH) {
        final int w = maskW, h = maskH, n = w * h;
        List<String> warnings = new ArrayList<>();

        float[] lum = new float[n];
        for (int i = 0; i < n; i++) {
            float r = ((in.rgb[i] >> 16) & 0xFF) / 255f;
            float g = ((in.rgb[i] >> 8) & 0xFF) / 255f;
            float b = (in.rgb[i] & 0xFF) / 255f;
            lum[i] = 0.299f * r + 0.587f * g + 0.114f * b;
        }
        int[] lab = new int[n];
        for (int y = 0; y < h; y++) {
            int sy = Math.min(clusters.h - 1, y * clusters.h / h);
            for (int x = 0; x < w; x++) {
                lab[y * w + x] = clusters.labels[sy * clusters.w + Math.min(clusters.w - 1, x * clusters.w / w)];
            }
        }

        List<Live> live = new ArrayList<>();
        for (ScenePlan.Element e : plan.elements) live.add(new Live(e, w, h));

        for (Live l : live) {
            l.mask = elementMask(l, in, lum, lab, clusters.k, w, h);
            if ("water".equals(l.e.kind) && warmWater(l, in, w)) {
                l.dropped = true;
                l.mask = new float[n];
                warnings.add("water element '" + l.e.name + "' dropped: its colour is warm, not water");
            } else {
                crossCheck(l, in, w);
            }
        }

        // Overlaps: the nearer element keeps the pixel, ties to the smaller box.
        List<Live> order = new ArrayList<>(live);
        Collections.sort(order, (a, b) -> a.rank != b.rank ? Integer.compare(b.rank, a.rank)
            : Float.compare(a.area, b.area));
        for (int i = 0; i < n; i++) {
            Live owner = null;
            for (Live l : order) {
                if (l.mask[i] > 0.5f) { owner = l; break; }
            }
            if (owner == null) continue;
            for (Live l : live) if (l != owner) l.mask[i] = 0f;
        }

        float[] sky = new float[n], water = new float[n], fall = new float[n], glow = new float[n];
        float[] swayRaw = new float[n], windRaw = new float[n], foliage = new float[n];
        float[] nonSky = new float[n];
        boolean hasSky = false;
        for (Live l : live) {
            String k = l.e.kind, m = l.e.motion;
            float[] mk = l.mask;
            if ("sky".equals(k) || "clouds".equals(k)) {
                hasSky = true;
                max(sky, mk);
                continue;
            }
            max(nonSky, mk);
            if ("water".equals(k)) max(water, mk);
            else if ("falling_water".equals(k)) max(fall, mk);
            else if (("trees".equals(k) || "flowers".equals(k)) && "sway".equals(m)) max(swayRaw, mk);
            else if (("grass".equals(k) || "ground".equals(k)) && ("sway".equals(m) || "wind_wave".equals(m))) {
                max(windRaw, mk);
            } else if ("lights".equals(k) && ("glow".equals(m) || "flicker".equals(m) || "twinkle".equals(m))) {
                max(glow, mk);
            }
            if ("grass".equals(k) || "trees".equals(k) || "flowers".equals(k)) max(foliage, mk);
        }
        for (int i = 0; i < n; i++) sky[i] *= 1f - nonSky[i];
        if (hasSky) {
            int horizon = horizonRow(nonSky, w, h);
            for (int y = 0; y < horizon; y++) {
                for (int x = 0; x < w; x++) {
                    int i = y * w + x;
                    if (in.depth[i] < SKY_ABOVE_DEPTH) sky[i] = Math.max(sky[i], 1f - nonSky[i]);
                }
            }
        }
        float[] sway = tipWeighted(swayRaw, w, h);
        float[] wind = tipWeighted(windRaw, w, h);

        // subject: saliency, gated by the figure/animal/vehicle boxes when there are any
        float[] gate = null;
        for (Live l : live) {
            String k = l.e.kind;
            if (l.dropped || !("figure".equals(k) || "animal".equals(k) || "vehicle".equals(k))) continue;
            if (gate == null) gate = new float[n];
            max(gate, feathered(l, w, h));
        }
        float[] sal = GuidedFilter.filter(lum, in.saliency, w, h, RegionMasks.GUIDE_RADIUS, RegionMasks.GUIDE_EPS);
        float[] subject = new float[n];
        for (int i = 0; i < n; i++) subject[i] = sal[i] * (gate == null ? 1f : gate[i]);
        subject = Planes.smooth(subject, 0.3f, 0.7f);
        if (Planes.coverage(subject, 0.5f) > RegionMasks.MAX_SUBJECT) {
            for (int i = 0; i < n; i++) subject[i] *= Planes.smoothstep(0.55f, 0.75f, in.depth[i]);
        }

        float[] still = subject.clone();
        for (Live l : live) if (l.e.still) max(still, l.mask);

        float[] mist = new float[n];
        float[] particles = new float[n];
        for (int y = 0; y < h; y++) {
            float low = Planes.smoothstep(0.2f, 0.7f, y / (float) Math.max(1, h - 1));
            for (int x = 0; x < w; x++) {
                int i = y * w + x;
                float far = Planes.clamp01((0.75f - in.depth[i]) / 0.6f);
                mist[i] = far * (0.4f + 0.6f * low) * (1f - subject[i]);
                particles[i] = (1f - subject[i]) * (1f - glow[i]) * (0.35f + 0.65f * (1f - in.depth[i]))
                    * (1f - Planes.clamp01(still[i]));
            }
        }

        RegionMasks.Stats s = new RegionMasks.Stats();
        s.water = Planes.coverage(water, 0.5f);
        s.fall = Planes.coverage(fall, 0.5f);
        s.sky = Planes.coverage(sky, 0.5f);
        s.foliage = Planes.coverage(foliage, 0.5f);
        s.glow = Planes.coverage(glow, 0.5f);
        s.subject = Planes.coverage(subject, 0.5f);
        float[] nearWater = Planes.boxMean(water, w, h, 12);
        float[] bob = new float[n];
        for (int i = 0; i < n; i++) bob[i] = subject[i] * Planes.smoothstep(0.1f, 0.4f, nearWater[i]);
        s.bob = s.subject > 0f ? Planes.coverage(bob, 0.5f) / s.subject : 0f;
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
        return new Result(w, h, sky, water, fall, sway, wind, glow, subject, still, mist, particles, s, warnings);
    }

    // ---------------------------------------------------------------- per element

    /** The box with a 2 px feather, 0..1. */
    @NonNull
    private static float[] feathered(Live l, int w, int h) {
        float[] box = new float[w * h];
        for (int y = l.y0; y < l.y1; y++) Arrays.fill(box, y * w + l.x0, y * w + l.x1, 1f);
        return Planes.boxMean(box, w, h, FEATHER);
    }

    @NonNull
    private static float[] elementMask(Live l, RegionMasks.Inputs in, float[] lum, int[] lab, int k, int w, int h) {
        int n = w * h;
        float[] inside = feathered(l, w, h);

        // depth band from the percentiles inside the box
        int boxN = (l.x1 - l.x0) * (l.y1 - l.y0);
        float[] vals = new float[boxN];
        int c = 0;
        for (int y = l.y0; y < l.y1; y++) for (int x = l.x0; x < l.x1; x++) vals[c++] = in.depth[y * w + x];
        float p40 = Planes.percentile(vals, 0.4f);
        float p60 = Planes.percentile(vals, 0.6f);
        float lo, hi;
        if (l.rank == 2) { lo = p60 - BAND_WIDEN; hi = Float.MAX_VALUE; }
        else if (l.rank == 0) { lo = -Float.MAX_VALUE; hi = p40 + BAND_WIDEN; }
        else { lo = p40 - BAND_WIDEN; hi = p60 + BAND_WIDEN; }

        // colour clusters mostly inside the box
        int kk = Math.max(1, k);
        int[] total = new int[kk], inBox = new int[kk];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int id = Math.min(kk - 1, Math.max(0, lab[y * w + x]));
                total[id]++;
                if (l.in(x, y)) inBox[id]++;
            }
        }
        boolean[] want = new boolean[kk];
        boolean anyWanted = false;
        for (int id = 0; id < kk; id++) {
            if (total[id] > 0 && inBox[id] >= CLUSTER_INSIDE * total[id]) { want[id] = true; anyWanted = true; }
        }

        float[] ind = new float[n];
        for (int i = 0; i < n; i++) {
            float d = in.depth[i];
            boolean band = d >= lo && d <= hi;
            int id = Math.min(kk - 1, Math.max(0, lab[i]));
            boolean colour = !anyWanted || want[id];
            ind[i] = band && colour ? inside[i] : 0f;
        }
        float[] m = Planes.smooth(GuidedFilter.filter(lum, ind, w, h, RegionMasks.GUIDE_RADIUS, RegionMasks.GUIDE_EPS),
            0.3f, 0.7f);
        for (int i = 0; i < n; i++) m[i] *= inside[i];
        return m;
    }

    /** The warm-water guard: Lab b* above 15 and a* above 5 across the element. */
    private static boolean warmWater(Live l, RegionMasks.Inputs in, int w) {
        double r = 0, g = 0, b = 0;
        int c = 0;
        for (int y = l.y0; y < l.y1; y++) {
            for (int x = l.x0; x < l.x1; x++) {
                int i = y * w + x;
                if (l.mask[i] <= 0.5f) continue;
                r += (in.rgb[i] >> 16) & 0xFF;
                g += (in.rgb[i] >> 8) & 0xFF;
                b += in.rgb[i] & 0xFF;
                c++;
            }
        }
        if (c == 0) {
            for (int y = l.y0; y < l.y1; y++) {
                for (int x = l.x0; x < l.x1; x++) {
                    int i = y * w + x;
                    r += (in.rgb[i] >> 16) & 0xFF;
                    g += (in.rgb[i] >> 8) & 0xFF;
                    b += in.rgb[i] & 0xFF;
                    c++;
                }
            }
        }
        int mean = ((int) Math.round(r / c) << 16) | ((int) Math.round(g / c) << 8) | (int) Math.round(b / c);
        float[] lab = ColourClusters.lab(mean);
        return lab[2] > 15f && lab[1] > 5f;
    }

    /** SegFormer cross-check: only when the group's mean inside the box is confident. */
    private static void crossCheck(Live l, RegionMasks.Inputs in, int w) {
        String key = groupFor(l.e.kind);
        if (key == null) return;
        float[] g = in.groups.get(key);
        if (g == null) return;
        double sum = 0;
        int c = 0;
        for (int y = l.y0; y < l.y1; y++) {
            for (int x = l.x0; x < l.x1; x++) { sum += g[y * w + x]; c++; }
        }
        if (c == 0 || sum / c <= SEG_CONFIDENT) return;
        for (int i = 0; i < l.mask.length; i++) l.mask[i] = Math.min(l.mask[i], g[i]);
    }

    @Nullable
    private static String groupFor(String kind) {
        switch (kind) {
            case "sky": case "clouds": return "sky";
            case "water": return "water";
            case "falling_water": return "falling_water";
            case "grass": case "trees": case "flowers": return "foliage";
            case "lights": return "lights";
            default: return null;
        }
    }

    // ---------------------------------------------------------------- helpers

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    private static void max(float[] into, float[] from) {
        for (int i = 0; i < into.length; i++) if (from[i] > into[i]) into[i] = from[i];
    }

    /** First row where the non-sky elements cover over 30% of the width; the frame height if none. */
    private static int horizonRow(float[] nonSky, int w, int h) {
        for (int y = 0; y < h; y++) {
            int on = 0;
            for (int x = 0; x < w; x++) if (nonSky[y * w + x] > 0.5f) on++;
            if (on > w * 3 / 10) return y;
        }
        return h;
    }

    // The rest is copied from RegionMasks, where it is private.

    /** One end of a part must be this much thinner than the other to be called the tip. */
    private static final float TIP_RATIO = 1.3f;
    private static final int MIN_SWAY_ROWS = 6;

    @NonNull
    private static float[] tipWeighted(float[] foliage, int w, int h) {
        int n = w * h;
        boolean[] on = new boolean[n];
        for (int i = 0; i < n; i++) on[i] = foliage[i] > 0.5f;
        Planes.Components c = Planes.components(on, w, h);
        int[][] widths = new int[c.count + 1][];
        for (int id = 1; id <= c.count; id++) {
            int rows = c.bottom[id] - c.top[id] + 1;
            if (rows >= MIN_SWAY_ROWS) widths[id] = new int[rows];
        }
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int id = c.id[y * w + x];
                if (id > 0 && widths[id] != null) widths[id][y - c.top[id]]++;
            }
        }
        int[] end = new int[c.count + 1];
        for (int id = 1; id <= c.count; id++) {
            int[] rw = widths[id];
            if (rw == null) continue;
            int third = Math.max(1, rw.length / 3);
            double top = 0, bottom = 0;
            for (int k = 0; k < third; k++) {
                top += rw[k];
                bottom += rw[rw.length - 1 - k];
            }
            if (bottom > top * TIP_RATIO) end[id] = 1;
            else if (top > bottom * TIP_RATIO) end[id] = -1;
        }
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
                    float near;
                    if (end[id] > 0) near = 1f - t;
                    else if (end[id] < 0) near = t;
                    else near = Math.abs(2f * t - 1f);
                    weight = 0.25f + 0.75f * near;
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
