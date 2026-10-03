package com.termux.app.chrome.wallpaper.living;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

/** Masks on a synthetic picture: sky above, water below, a lit subject on the water. */
public class RegionMasksTest {
    private static final int W = 48, H = 64;

    private static RegionMasks.Inputs scene(boolean waterProb) {
        int n = W * H;
        int[] rgb = new int[n];
        float[] depth = new float[n];
        float[] sal = new float[n];
        float[] sky = new float[n];
        float[] water = new float[n];
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) {
                int i = y * W + x;
                boolean top = y < H / 2;
                rgb[i] = top ? 0x6090E0 : 0x205080;
                depth[i] = top ? 0.1f : 0.4f;
                sky[i] = top ? 0.95f : 0.02f;
                water[i] = waterProb && !top ? 0.9f : 0.02f;
                if (x > 18 && x < 30 && y > 36 && y < 52) {
                    rgb[i] = 0xE0E0D0;
                    depth[i] = 0.95f;
                    sal[i] = 1f;
                    water[i] = 0.02f;
                }
            }
        }
        Map<String, float[]> g = new HashMap<>();
        g.put("sky", sky);
        g.put("water", water);
        g.put("subject_like", new float[n]);
        return new RegionMasks.Inputs(W, H, rgb, depth, sal, g);
    }

    private static ColourClusters.Result clusters(RegionMasks.Inputs in) {
        return ColourClusters.compute(in.rgb, W, H, 3, 9L);
    }

    @Test
    public void skyWaterAndSubjectAreFound() {
        RegionMasks.Inputs in = scene(true);
        RegionMasks.Result r = RegionMasks.compute(in, clusters(in), null);
        assertTrue(r.sky[5 * W + 5] > 0.8f);
        assertTrue(r.sky[60 * W + 5] < 0.2f);
        assertTrue(r.water[60 * W + 5] > 0.8f);
        assertTrue(r.subject[44 * W + 24] > 0.8f);
        assertTrue(r.subject[5 * W + 5] < 0.2f);
        assertTrue("subject on water bobs", r.bob[44 * W + 24] > 0.5f);
        assertEquals(0.5f, r.stats.sky, 0.1f);
        assertTrue(r.stats.depthSpread > 0.3f);
    }

    @Test
    public void tinyGroupsAreDropped() {
        RegionMasks.Inputs in = scene(false);
        RegionMasks.Result r = RegionMasks.compute(in, clusters(in), null);
        assertEquals(0f, r.stats.water, 0f);
    }

    @Test
    public void gemmaPickReplacesTheGroup() {
        RegionMasks.Inputs in = scene(false);
        ColourClusters.Result c = clusters(in);
        int lower = c.labels[60 * W + 5] + 1;
        Map<String, int[]> picks = new HashMap<>();
        picks.put("water", new int[] {lower});
        RegionMasks.Result r = RegionMasks.compute(in, c, picks);
        assertTrue(r.water[60 * W + 5] > 0.8f);
        assertTrue(r.stats.water > 0.2f);
    }

    @Test
    public void gemmaRunDropsUnconfidentSegformerGroups() {
        RegionMasks.Inputs in = scene(true);
        // water prob 0.9 > 0.6 and coverage 50%: kept even when Gemma named nothing for it
        RegionMasks.Result kept = RegionMasks.compute(in, clusters(in), new HashMap<>());
        assertTrue(kept.stats.water > 0.3f);
        for (int i = 0; i < in.groups.get("water").length; i++) {
            if (in.groups.get("water")[i] > 0.5f) in.groups.get("water")[i] = 0.55f;
        }
        RegionMasks.Result dropped = RegionMasks.compute(in, clusters(in), new HashMap<>());
        assertEquals(0f, dropped.stats.water, 0f);
    }

    /** A foliage part: a wide band (trunk or canopy edge) at one end narrowing to thin strands at the other. */
    private static RegionMasks.Inputs foliageScene(boolean wideAtTop, boolean taper) {
        int n = W * H;
        int[] rgb = new int[n];
        float[] depth = new float[n];
        float[] foliage = new float[n];
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) {
                int i = y * W + x;
                rgb[i] = 0x205030;
                depth[i] = 0.4f;
                if (y >= 8 && y < 56) {
                    float t = (y - 8) / 47f; // 0 top .. 1 bottom
                    float k = wideAtTop ? 1f - t : t; // 1 at the wide end
                    int half = taper ? 2 + Math.round(k * 16f) : 10;
                    // The leaves are their own colour, as in a picture, so the guided filter has an edge to keep.
                    if (Math.abs(x - W / 2) <= half) {
                        foliage[i] = 0.9f;
                        rgb[i] = 0x58B048;
                    }
                }
            }
        }
        Map<String, float[]> g = new HashMap<>();
        g.put("foliage", foliage);
        g.put("sky", new float[n]);
        g.put("water", new float[n]);
        return new RegionMasks.Inputs(W, H, rgb, depth, new float[n], g);
    }

    @Test
    public void swayFollowsTheThinEndOfHangingFoliage() {
        RegionMasks.Inputs in = foliageScene(true, true); // wide at the top, strands hang down
        RegionMasks.Result r = RegionMasks.compute(in, clusters(in), null);
        float tips = r.sway[54 * W + W / 2];
        float base = r.sway[10 * W + W / 2];
        assertTrue("hanging tips sway more than the anchored top", tips > base + 0.3f);
    }

    @Test
    public void swayStillFollowsTheThinEndOfUpwardFoliage() {
        RegionMasks.Inputs in = foliageScene(false, true); // wide at the bottom, tips up
        RegionMasks.Result r = RegionMasks.compute(in, clusters(in), null);
        assertTrue(r.sway[10 * W + W / 2] > r.sway[54 * W + W / 2] + 0.3f);
    }

    @Test
    public void swayFavoursBothEndsWhenNeitherIsThinner() {
        RegionMasks.Inputs in = foliageScene(true, false); // a uniform band
        RegionMasks.Result r = RegionMasks.compute(in, clusters(in), null);
        float mid = r.sway[32 * W + W / 2];
        assertTrue(r.sway[10 * W + W / 2] > mid + 0.2f);
        assertTrue(r.sway[54 * W + W / 2] > mid + 0.2f);
    }
}
