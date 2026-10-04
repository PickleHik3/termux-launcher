package com.termux.app.chrome.wallpaper.living;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.json.JSONObject;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

/** Element masks on small synthetic maps (40 x 60): sky above, a cool lower half, no models. */
public class ElementMasksTest {
    private static final int W = 40, H = 60, N = W * H;

    private final int[] rgb = new int[N];
    private final float[] depth = new float[N];
    private final float[] sal = new float[N];
    private final Map<String, float[]> groups = new HashMap<>();

    public ElementMasksTest() {
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) {
                boolean top = y < H / 2;
                rgb[y * W + x] = top ? 0x6090E0 : 0x205080;
                depth[y * W + x] = top ? 0.05f : 0.5f;
            }
        }
    }

    /** {@code kind|name|depth|motion|t,l,b,r} per element. */
    private static ScenePlan plan(String doNotAnimate, String... els) throws Exception {
        StringBuilder sb = new StringBuilder("{\"elements\":[");
        for (int i = 0; i < els.length; i++) {
            String[] p = els[i].split("\\|");
            if (i > 0) sb.append(',');
            sb.append("{\"name\":\"").append(p[1]).append("\",\"kind\":\"").append(p[0])
                .append("\",\"depth\":\"").append(p[2]).append("\",\"motion\":\"").append(p[3])
                .append("\",\"box\":[").append(p[4]).append("]}");
        }
        sb.append("],\"scene\":{\"time\":\"day\",\"weather\":\"clear\",\"light_direction\":\"left\","
            + "\"particles\":\"none\",\"mood\":\"calm\",\"do_not_animate\":[").append(doNotAnimate).append("]}}");
        return ScenePlan.fromJson(new JSONObject(sb.toString()));
    }

    private RegionMasks.Inputs inputs() {
        return new RegionMasks.Inputs(W, H, rgb, depth, sal, groups);
    }

    private static ColourClusters.Result oneCluster() {
        return new ColourClusters.Result(W, H, 1, new int[N], new float[] {1f}, new int[] {0x406080},
            new int[] {W / 2}, new int[] {H / 2});
    }

    private ElementMasks.Result run(ScenePlan p) {
        return ElementMasks.compute(p, inputs(), oneCluster(), W, H);
    }

    private static float at(float[] plane, int x, int y) {
        return plane[y * W + x];
    }

    @Test
    public void depthBandPicksOnlyTheNearHalf() throws Exception {
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) {
                depth[y * W + x] = x < W / 2 ? 0.2f : 0.9f;
                rgb[y * W + x] = x < W / 2 ? 0x2060A0 : 0x3070B0;
            }
        }
        ElementMasks.Result r = run(plan("", "water|pool|near|ripple|0,0,1000,1000"));
        assertTrue(at(r.water, 32, 30) > 0.8f);
        assertTrue(at(r.water, 6, 30) < 0.2f);
        ElementMasks.Result far = run(plan("", "water|pool|far|ripple|0,0,1000,1000"));
        assertTrue(at(far.water, 6, 30) > 0.8f);
        assertTrue(at(far.water, 32, 30) < 0.2f);
    }

    @Test
    public void nearerElementWinsAnOverlap() throws Exception {
        ElementMasks.Result r = run(plan("",
            "water|lake|near|ripple|500,0,1000,1000", "falling_water|fall|far|flow|500,0,1000,1000"));
        assertTrue(at(r.water, 20, 45) > 0.8f);
        assertEquals(0f, at(r.fall, 20, 45), 1e-6f);
    }

    @Test
    public void tieGoesToTheSmallerBox() throws Exception {
        ElementMasks.Result r = run(plan("",
            "water|lake|middle|ripple|500,0,1000,1000", "falling_water|fall|middle|flow|600,300,900,700"));
        assertTrue(at(r.fall, 20, 45) > 0.8f);
        assertEquals(0f, at(r.water, 20, 45), 1e-6f);
        assertTrue(at(r.water, 3, 45) > 0.8f);
    }

    @Test
    public void warmWaterIsDroppedWithAWarning() throws Exception {
        for (int y = H / 2; y < H; y++) for (int x = 0; x < W; x++) rgb[y * W + x] = 0xE8A080;
        ElementMasks.Result r = run(plan("", "water|pink meadow|middle|ripple|500,0,1000,1000"));
        assertEquals(0f, r.stats.water, 1e-6f);
        assertEquals(0f, Planes.coverage(r.water, 0.01f), 1e-6f);
        assertEquals(1, r.warnings.size());
        assertTrue(r.warnings.get(0).contains("pink meadow"));
    }

    @Test
    public void stillCoversDoNotAnimateAndTheSubject() throws Exception {
        for (int y = 20; y < 45; y++) for (int x = 14; x < 26; x++) sal[y * W + x] = 1f;
        for (int y = 0; y < 15; y++) for (int x = 0; x < 12; x++) rgb[y * W + x] = 0x306030;
        ElementMasks.Result r = run(plan("\"Old Tree\"",
            "trees|old tree|middle|sway|0,0,250,300", "figure|monk|near|none|300,300,800,700"));
        assertTrue("do_not_animate element", at(r.still, 5, 7) > 0.5f);
        assertTrue("subject", at(r.still, 20, 32) > 0.5f);
        assertTrue(at(r.subject, 20, 32) > 0.5f);
        assertTrue("elsewhere free", at(r.still, 34, 50) < 0.2f);
        assertTrue("particles zero inside still", at(r.particles, 5, 7) < 0.05f);
        assertTrue("particles zero on the subject", at(r.particles, 20, 32) < 0.05f);
        assertTrue("particles elsewhere", at(r.particles, 34, 50) > 0.3f);
    }

    @Test
    public void segFormerOnlyIntersectsWhenConfident() throws Exception {
        float[] confident = new float[N];
        float[] weak = new float[N];
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) {
                confident[y * W + x] = x < 30 ? 1f : 0f;
                weak[y * W + x] = x < 16 ? 1f : 0f;
            }
        }
        groups.put("water", confident);
        ElementMasks.Result a = run(plan("", "water|lake|middle|ripple|500,0,1000,1000"));
        assertTrue(at(a.water, 10, 45) > 0.8f);
        assertTrue(at(a.water, 36, 45) < 0.2f);
        groups.put("water", weak);
        ElementMasks.Result b = run(plan("", "water|lake|middle|ripple|500,0,1000,1000"));
        assertTrue(at(b.water, 36, 45) > 0.8f);
    }

    @Test
    public void windPlaneComesFromWindWaveGrass() throws Exception {
        for (int y = 36; y < 56; y++) for (int x = 4; x < 20; x++) rgb[y * W + x] = 0x40A040;
        ElementMasks.Result r = run(plan("", "grass|lawn|middle|wind_wave|600,100,950,500"));
        assertTrue(at(r.wind, 12, 46) > 0.2f);
        assertEquals(0f, Planes.coverage(r.sway, 0f), 1e-6f);
        assertEquals(0f, at(r.wind, 34, 46), 1e-6f);
    }

    @Test
    public void swayPlaneComesFromSwayingTreesOnly() throws Exception {
        ElementMasks.Result r = run(plan("",
            "trees|pines|middle|sway|500,0,1000,500", "trees|statue trees|middle|none|500,500,1000,1000"));
        assertTrue(at(r.sway, 10, 45) > 0.2f);
        assertEquals(0f, at(r.sway, 30, 45), 1e-6f);
    }

    @Test
    public void coverageStatsMatchThePlanes() throws Exception {
        ElementMasks.Result r = run(plan("",
            "sky|sky|far|none|0,0,500,1000", "water|lake|middle|ripple|500,0,1000,1000"));
        assertEquals(0.5f, r.stats.sky, 0.1f);
        assertEquals(0.5f, r.stats.water, 0.1f);
        assertEquals(Planes.coverage(r.sky, 0.5f), r.stats.sky, 1e-6f);
        assertEquals(Planes.coverage(r.water, 0.5f), r.stats.water, 1e-6f);
        assertEquals(Planes.coverage(r.glow, 0.5f), r.stats.glow, 1e-6f);
        assertTrue(r.stats.depthSpread > 0.3f);
        assertTrue(r.stats.sceneLum > 0f);
    }
}
