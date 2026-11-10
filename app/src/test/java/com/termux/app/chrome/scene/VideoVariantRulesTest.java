package com.termux.app.chrome.scene;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class VideoVariantRulesTest {

    @Test
    public void originalRateByTier() {
        assertEquals(30, VideoVariantRules.originalRate(24f));
        assertEquals(30, VideoVariantRules.originalRate(30f));
        assertEquals(30, VideoVariantRules.originalRate(30.3f));
        assertEquals(60, VideoVariantRules.originalRate(40f));
        assertEquals(60, VideoVariantRules.originalRate(60f));
        assertEquals(60, VideoVariantRules.originalRate(120f));
        assertEquals(15, VideoVariantRules.originalRate(10f));
        assertEquals(15, VideoVariantRules.originalRate(0f));
    }

    @Test
    public void availableRates() {
        assertArrayEquals(new int[] {15, 30}, VideoVariantRules.availableRates(24f));
        assertArrayEquals(new int[] {15, 30, 60}, VideoVariantRules.availableRates(60f));
        assertArrayEquals(new int[] {15}, VideoVariantRules.availableRates(12f));
    }

    @Test
    public void clampRate() {
        assertEquals(30, VideoVariantRules.clampRate(24f, 60));
        assertEquals(15, VideoVariantRules.clampRate(24f, 15));
        assertEquals(15, VideoVariantRules.clampRate(12f, 60));
        assertEquals(60, VideoVariantRules.clampRate(60f, 60));
        assertEquals(15, VideoVariantRules.clampRate(60f, 5));
    }

    @Test
    public void needsEncode() {
        assertTrue(VideoVariantRules.needsEncode(60f, false, 15));
        assertTrue(VideoVariantRules.needsEncode(60f, false, 30));
        assertFalse(VideoVariantRules.needsEncode(60f, false, 60));
        assertFalse(VideoVariantRules.needsEncode(24f, false, 30));
        assertFalse(VideoVariantRules.needsEncode(24f, false, 60));
        assertTrue(VideoVariantRules.needsEncode(24f, true, 30));
        assertFalse(VideoVariantRules.needsEncode(24f, true, 60));
    }

    @Test
    public void pruneOriginal() {
        assertFalse(VideoVariantRules.pruneOriginal(60f, new boolean[] {true, true, false}));
        assertTrue(VideoVariantRules.pruneOriginal(60f, new boolean[] {true, true, true}));
        assertTrue(VideoVariantRules.pruneOriginal(24f, new boolean[] {true, true, false}));
        assertFalse(VideoVariantRules.pruneOriginal(24f, new boolean[] {false, true, false}));
        assertTrue(VideoVariantRules.pruneOriginal(12f, new boolean[] {true, false, false}));
    }

    @Test
    public void frameKeepArithmetic() {
        long step = VideoTranscode.stepUs(15);
        assertEquals(66_666L, step);
        // 60 fps source to 15: one frame in four.
        long next = 0L;
        int kept = 0;
        for (int i = 0; i < 60; i++) {
            long pts = i * 16_667L;
            if (VideoTranscode.keep(pts, next, step)) {
                kept++;
                next = VideoTranscode.advance(pts, next, step);
            }
        }
        assertEquals(15, kept);
        // Rounded 60 fps timestamps still give 30, not 20.
        long s30 = VideoTranscode.stepUs(30);
        next = 0L;
        kept = 0;
        for (int i = 0; i < 60; i++) {
            long pts = i * 16_666L;
            if (VideoTranscode.keep(pts, next, s30)) {
                kept++;
                next = VideoTranscode.advance(pts, next, s30);
            }
        }
        assertEquals(30, kept);
        // A gap re-anchors instead of bursting.
        assertEquals(5_000_000L + step, VideoTranscode.advance(5_000_000L, 66_666L, step));
    }

    @Test
    public void bitrateScalesWithRate() {
        assertEquals(6_000_000, VideoTranscode.bitrate(1920, 1080, 30));
        assertEquals(3_000_000, VideoTranscode.bitrate(1920, 1080, 15));
        assertEquals(12_000_000, VideoTranscode.bitrate(1920, 1080, 60));
        assertEquals(1_500_000, VideoTranscode.bitrate(320, 240, 15));
    }

    @Test
    public void metaRoundTrip() {
        VideoVariants.Meta m = new VideoVariants.Meta("a b.mp4", "video/avc", 1920, 1080, 59.94f, 12_000L,
            3_000_000L, false, false, Boolean.TRUE, "1920x1080 · 60 fps");
        VideoVariants.Meta r = VideoVariants.Meta.parse(m.toJson());
        assertNotNull(r);
        assertEquals("a b.mp4", r.name);
        assertEquals(1920, r.width);
        assertEquals(59.94f, r.fps, 0.01f);
        assertEquals(12_000L, r.durationMs);
        assertFalse(r.gif);
        assertEquals(Boolean.TRUE, r.hardware);
        assertEquals("1920x1080 · 60 fps", r.hint);
        VideoVariants.Meta g = new VideoVariants.Meta("x.gif", "image/gif", 10, 10, 10f, 1000L, 5L, true, true,
            null, "");
        VideoVariants.Meta gr = VideoVariants.Meta.parse(g.toJson());
        assertNotNull(gr);
        assertTrue(gr.gif);
        assertNull(gr.hardware);
        assertNull(VideoVariants.Meta.parse("{}"));
        assertNull(VideoVariants.Meta.parse("nope"));
    }

    @Test
    public void originalFileNames() {
        assertEquals("original.mp4", VideoVariants.originalFileName("loop.mp4", false));
        assertEquals("original.webm", VideoVariants.originalFileName("a.WEBM", false));
        assertEquals("original.mp4", VideoVariants.originalFileName("noext", false));
        assertEquals("original.gif", VideoVariants.originalFileName("anim.gif", true));
    }
}
