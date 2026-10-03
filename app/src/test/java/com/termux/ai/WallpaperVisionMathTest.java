package com.termux.ai;

import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Arrays;
import java.util.HashSet;
import java.util.zip.Inflater;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/** The pure pre- and post-processing of the wallpaper analysis, against the reference in wall-alive/litert_check.py. */
public class WallpaperVisionMathTest {
    private static final float EPS = 1e-4f;

    private static int argb(int r, int g, int b) {
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    @Test
    public void resizeToTheSameSizeIsTheIdentity() {
        int[] pixels = new int[4 * 3];
        for (int i = 0; i < pixels.length; i++) pixels[i] = argb(i * 20, 255 - i * 10, i);
        for (WallpaperVisionMath.Filter filter : WallpaperVisionMath.Filter.values()) {
            float[] out = WallpaperVisionMath.resizeRgb(pixels, 4, 3, 4, 3, filter);
            assertEquals(4 * 3 * 3, out.length);
            for (int i = 0; i < pixels.length; i++) {
                assertEquals(filter + " r" + i, (pixels[i] >> 16) & 0xFF, out[i * 3], 0f);
                assertEquals(filter + " g" + i, (pixels[i] >> 8) & 0xFF, out[i * 3 + 1], 0f);
                assertEquals(filter + " b" + i, pixels[i] & 0xFF, out[i * 3 + 2], 0f);
            }
        }
    }

    @Test
    public void aFlatImageStaysFlatWhateverTheSize() {
        int[] pixels = new int[40 * 30];
        Arrays.fill(pixels, argb(200, 100, 50));
        for (WallpaperVisionMath.Filter filter : WallpaperVisionMath.Filter.values()) {
            float[] out = WallpaperVisionMath.resizeRgb(pixels, 40, 30, 7, 11, filter);
            for (int i = 0; i < out.length; i += 3) {
                assertEquals(200f, out[i], 0f);
                assertEquals(100f, out[i + 1], 0f);
                assertEquals(50f, out[i + 2], 0f);
            }
        }
    }

    @Test
    public void downscalingASplitImageKeepsItsOrderAndRange() {
        // Left half black, right half white, 8 wide to 4 wide: a smooth, monotonic edge inside 0..255.
        int[] pixels = new int[8];
        for (int i = 0; i < 8; i++) pixels[i] = i < 4 ? argb(0, 0, 0) : argb(255, 255, 255);
        for (WallpaperVisionMath.Filter filter : WallpaperVisionMath.Filter.values()) {
            float[] out = WallpaperVisionMath.resizeRgb(pixels, 8, 1, 4, 1, filter);
            assertTrue(out[0] < 50f);
            assertTrue(out[9] > 200f);
            for (int x = 1; x < 4; x++) assertTrue(filter + " x" + x, out[x * 3] >= out[(x - 1) * 3]);
            for (float v : out) assertTrue(v >= 0f && v <= 255f);
        }
    }

    @Test
    public void nchwSplitsChannelsAndNormalisesWithImageNetStats() {
        // Two pixels: (255,0,0) and (0,255,0).
        float[] hwc = {255f, 0f, 0f, 0f, 255f, 0f};
        float[] out = WallpaperVisionMath.toNchw(hwc, 2, 1, false);
        assertEquals(6, out.length);
        assertEquals((1f - 0.485f) / 0.229f, out[0], EPS);
        assertEquals((0f - 0.485f) / 0.229f, out[1], EPS);
        assertEquals((0f - 0.456f) / 0.224f, out[2], EPS);
        assertEquals((1f - 0.456f) / 0.224f, out[3], EPS);
        assertEquals((0f - 0.406f) / 0.225f, out[4], EPS);
        assertEquals((0f - 0.406f) / 0.225f, out[5], EPS);
    }

    @Test
    public void u2NetDividesByTheImageMaximumInsteadOfBy255() {
        float[] hwc = {100f, 50f, 0f, 20f, 10f, 5f};
        float[] out = WallpaperVisionMath.toNchw(hwc, 2, 1, true);
        // The brightest value (100) maps to 1.0 before mean/std.
        assertEquals((1f - 0.485f) / 0.229f, out[0], EPS);
        assertEquals((0.2f - 0.485f) / 0.229f, out[1], EPS);
        assertEquals((0.5f - 0.456f) / 0.224f, out[2], EPS);
        // An all-black image must not divide by zero.
        float[] black = WallpaperVisionMath.toNchw(new float[6], 2, 1, true);
        for (float v : black) assertTrue(Float.isFinite(v));
    }

    @Test
    public void percentileMatchesNumpyLinearInterpolation() {
        float[] values = {10f, 0f, 20f, 30f, 40f};
        assertEquals(0.0, WallpaperVisionMath.percentile(values, 0), 1e-9);
        assertEquals(20.0, WallpaperVisionMath.percentile(values, 50), 1e-9);
        assertEquals(40.0, WallpaperVisionMath.percentile(values, 100), 1e-9);
        // numpy.percentile([0,10,20,30,40], 25) == 10 and 90 == 36.
        assertEquals(10.0, WallpaperVisionMath.percentile(values, 25), 1e-9);
        assertEquals(36.0, WallpaperVisionMath.percentile(values, 90), 1e-9);
    }

    @Test
    public void depthIsClippedToItsPercentilesWithNearAtOne() {
        float[] raw = new float[101];
        for (int i = 0; i <= 100; i++) raw[i] = i;
        // Disparity (DA2): high stays high. 1st percentile is 1, 99th is 99.
        float[] disparity = WallpaperVisionMath.depthNearOne(raw, false);
        assertEquals(0f, disparity[0], EPS);
        assertEquals(0f, disparity[1], EPS);
        assertEquals(0.5f, disparity[50], EPS);
        assertEquals(1f, disparity[99], EPS);
        assertEquals(1f, disparity[100], EPS);
    }

    @Test
    public void depthAnything3IsInvertedSoNearIsHigh() {
        float[] raw = new float[101];
        for (int i = 0; i <= 100; i++) raw[i] = i;
        float[] near = WallpaperVisionMath.depthNearOne(raw, true);
        assertEquals(1f, near[0], EPS);
        assertEquals(0.5f, near[50], EPS);
        assertEquals(0f, near[100], EPS);
        // Distance and disparity of the same map are mirror images.
        float[] disparity = WallpaperVisionMath.depthNearOne(raw, false);
        for (int i = 0; i < raw.length; i++) assertEquals(1f, near[i] + disparity[i], EPS);
    }

    @Test
    public void aFlatDepthMapDoesNotDivideByZero() {
        float[] flat = new float[16];
        Arrays.fill(flat, 3f);
        for (float v : WallpaperVisionMath.depthNearOne(flat, false)) assertEquals(0f, v, 0f);
        for (float v : WallpaperVisionMath.depthNearOne(flat, true)) assertEquals(1f, v, 0f);
    }

    @Test
    public void gray8ClipsAndRounds() {
        assertArrayEquals(new byte[] {0, 0, (byte) 128, (byte) 255, (byte) 255},
            WallpaperVisionMath.toGray8(new float[] {-0.5f, 0f, 0.5f, 1f, 2f}));
    }

    @Test
    public void sceneGroupsSoftmaxOverTheClassAxisAndSumEachGroup() {
        // Two pixels, four classes, NHWC. Pixel 0 is all class 1; pixel 1 is split evenly over 2 and 3.
        float[] logits = {
            -50f, 50f, -50f, -50f,
            -50f, -50f, 5f, 5f,
        };
        int[][] groups = {{1}, {2, 3}, {0}, {1, 9}};
        float[][] out = WallpaperVisionMath.sceneGroups(logits, 1, 2, 4, groups);
        assertEquals(1f, out[0][0], EPS);
        assertEquals(0f, out[0][1], EPS);
        assertEquals(0f, out[1][0], EPS);
        assertEquals(1f, out[1][1], EPS);
        assertEquals(0f, out[2][0], EPS);
        // Class 9 does not exist here: it adds nothing.
        assertEquals(1f, out[3][0], EPS);
        // Every pixel's probability over all classes sums to 1.
        float[][] all = WallpaperVisionMath.sceneGroups(logits, 1, 2, 4, new int[][] {{0, 1, 2, 3}});
        assertEquals(1f, all[0][0], EPS);
        assertEquals(1f, all[0][1], EPS);
        // An even split stays even.
        float[][] even = WallpaperVisionMath.sceneGroups(logits, 1, 2, 4, new int[][] {{2}, {3}});
        assertEquals(0.5f, even[0][1], EPS);
        assertEquals(0.5f, even[1][1], EPS);
    }

    @Test
    public void sceneGroupTableCoversTheAde20kClassesFromTheSpec() {
        assertEquals(WallpaperVisionMath.SCENE_GROUP_NAMES.length, WallpaperVisionMath.SCENE_GROUP_CLASSES.length);
        assertEquals("water", WallpaperVisionMath.SCENE_GROUP_NAMES[0]);
        assertEquals("sky", WallpaperVisionMath.SCENE_GROUP_NAMES[2]);
        assertArrayEquals(new int[] {2}, WallpaperVisionMath.SCENE_GROUP_CLASSES[2]);
        assertArrayEquals(new int[] {113, 104}, WallpaperVisionMath.SCENE_GROUP_CLASSES[1]);
        HashSet<Integer> seen = new HashSet<>();
        for (int[] classes : WallpaperVisionMath.SCENE_GROUP_CLASSES) {
            for (int id : classes) {
                assertTrue(id >= 0 && id < 150);
                assertTrue("class " + id + " is in two groups", seen.add(id));
            }
        }
        // Seven groups pack three to a PNG: scene0, scene1, scene2.
        assertEquals(3, WallpaperVisionMath.sceneImageCount());
    }

    @Test
    public void packedSceneImagesPutThreeGroupsInRgbAndPadWithZero() {
        float[][] groups = new float[7][2];
        for (int g = 0; g < 7; g++) {
            groups[g][0] = g / 10f;
            groups[g][1] = 1f;
        }
        byte[] first = WallpaperVisionMath.packSceneRgb(groups, 2, 0);
        assertEquals(6, first.length);
        assertEquals(0, first[0] & 0xFF);
        assertEquals(Math.round(0.1f * 255f), first[1] & 0xFF);
        assertEquals(Math.round(0.2f * 255f), first[2] & 0xFF);
        assertEquals(255, first[3] & 0xFF);
        byte[] last = WallpaperVisionMath.packSceneRgb(groups, 2, 2);
        assertEquals(Math.round(0.6f * 255f), last[0] & 0xFF);
        assertEquals(0, last[1] & 0xFF);
        assertEquals(0, last[2] & 0xFF);
        assertEquals(255, last[3] & 0xFF);
        assertEquals(0, last[4] & 0xFF);
    }

    @Test
    public void pngIsPlainGrayOrRgbWithoutAlphaAndRoundTrips() throws Exception {
        byte[] gray = {0, 64, (byte) 128, (byte) 255, 1, 2};
        byte[] png = WallpaperVisionMath.encodePng(gray, 3, 2, 1);
        assertArrayEquals(new byte[] {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A}, Arrays.copyOf(png, 8));
        assertEquals(3, readInt(png, 16));
        assertEquals(2, readInt(png, 20));
        assertEquals(8, png[24]);
        assertEquals("gray colour type", 0, png[25]);
        assertArrayEquals(gray, unfilter(png, 3, 2, 1));

        byte[] rgb = new byte[2 * 2 * 3];
        for (int i = 0; i < rgb.length; i++) rgb[i] = (byte) (i * 20);
        byte[] rgbPng = WallpaperVisionMath.encodePng(rgb, 2, 2, 3);
        assertEquals("rgb colour type", 2, rgbPng[25]);
        assertArrayEquals(rgb, unfilter(rgbPng, 2, 2, 3));
    }

    @Test(expected = IllegalArgumentException.class)
    public void pngRefusesAlphaChannels() {
        WallpaperVisionMath.encodePng(new byte[16], 2, 2, 4);
    }

    private static int readInt(byte[] data, int at) {
        return ((data[at] & 0xFF) << 24) | ((data[at + 1] & 0xFF) << 16) | ((data[at + 2] & 0xFF) << 8) | (data[at + 3] & 0xFF);
    }

    /** Inflates the PNG's IDAT and strips each row's filter byte (the encoder only writes filter 0). */
    private static byte[] unfilter(byte[] png, int w, int h, int channels) throws Exception {
        int at = 8;
        ByteArrayOutputStream idat = new ByteArrayOutputStream();
        while (at < png.length) {
            int length = readInt(png, at);
            String type = new String(png, at + 4, 4, "US-ASCII");
            if (type.equals("IDAT")) idat.write(png, at + 8, length);
            at += 12 + length;
        }
        Inflater inflater = new Inflater();
        inflater.setInput(idat.toByteArray());
        int stride = w * channels;
        byte[] raw = new byte[(stride + 1) * h];
        int done = 0;
        while (done < raw.length && !inflater.finished()) done += inflater.inflate(raw, done, raw.length - done);
        inflater.end();
        assertEquals(raw.length, done);
        byte[] pixels = new byte[stride * h];
        for (int y = 0; y < h; y++) {
            assertEquals(0, raw[y * (stride + 1)]);
            System.arraycopy(raw, y * (stride + 1) + 1, pixels, y * stride, stride);
        }
        return pixels;
    }
}
