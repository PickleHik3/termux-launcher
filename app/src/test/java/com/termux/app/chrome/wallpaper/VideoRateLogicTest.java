package com.termux.app.chrome.wallpaper;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class VideoRateLogicTest {

    private static final String FMT = "%1$d fps version · %2$s";

    @Test
    public void a30FpsClipDisables60AndSaysWhy() {
        assertArrayEquals(new boolean[] {true, true, false}, VideoRateLogic.enabled(30f));
        assertTrue(VideoRateLogic.showsReason(30f));
        assertEquals(30, VideoRateLogic.clipFps(30.03f));
    }

    @Test
    public void a60FpsClipEnablesEverythingAndNeedsNoReason() {
        assertArrayEquals(new boolean[] {true, true, true}, VideoRateLogic.enabled(60f));
        assertFalse(VideoRateLogic.showsReason(60f));
    }

    @Test
    public void a24FpsClipOffers15And30() {
        assertArrayEquals(new boolean[] {true, true, false}, VideoRateLogic.enabled(24f));
        assertEquals(24, VideoRateLogic.clipFps(23.976f));
    }

    @Test
    public void tapStartsAVersionOnlyBelowTheOriginalTierAndWhenMissing() {
        boolean[] none = {false, false, false};
        assertTrue(VideoRateLogic.startsPrepare(60f, false, none, 15));
        assertTrue(VideoRateLogic.startsPrepare(60f, false, none, 30));
        assertFalse(VideoRateLogic.startsPrepare(60f, false, none, 60));
        assertFalse(VideoRateLogic.startsPrepare(60f, false, new boolean[] {true, false, false}, 15));
        assertFalse(VideoRateLogic.startsPrepare(30f, false, none, 60));
        assertFalse(VideoRateLogic.startsPrepare(30f, false, none, 45));
    }

    @Test
    public void aGifEncodesEveryAvailableRate() {
        boolean[] none = {false, false, false};
        assertTrue(VideoRateLogic.startsPrepare(15f, true, none, 15));
        assertFalse(VideoRateLogic.startsPrepare(15f, true, none, 30));
        assertFalse(VideoRateLogic.startsPrepare(15f, true, new boolean[] {true, false, false}, 15));
    }

    @Test
    public void suffixOnlyForAReadyMadeVersion() {
        boolean[] ready = {true, false, true};
        long[] size = {3_400_000L, 0L, 9_000_000L};
        assertEquals("15 fps version · 3 MB", VideoRateLogic.statusSuffix(60f, false, ready, size, 15, FMT));
        assertNull(VideoRateLogic.statusSuffix(60f, false, ready, size, 30, FMT));
        assertNull(VideoRateLogic.statusSuffix(60f, false, ready, size, 60, FMT));
        assertEquals("30 fps version · 3 MB", VideoRateLogic.statusSuffix(30f, true,
            new boolean[] {false, true, false}, new long[] {0, 3_000_000L, 0}, 30, FMT));
    }

    @Test
    public void sizeWording() {
        assertEquals("1 KB", VideoRateLogic.formatSize(0));
        assertEquals("850 KB", VideoRateLogic.formatSize(850_000));
        assertEquals("12 MB", VideoRateLogic.formatSize(12_400_000));
    }
}
