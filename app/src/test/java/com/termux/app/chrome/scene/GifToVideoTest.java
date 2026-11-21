package com.termux.app.chrome.scene;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

public class GifToVideoTest {

    @Test
    public void sizeIsEvenAndCapped() {
        assertArrayEquals(new int[] {800, 1412}, GifToVideo.encodedSize(800, 1412, 2400));
        assertArrayEquals(new int[] {800, 1412}, GifToVideo.encodedSize(801, 1413, 2400));
        // Longer side 3000 down to 1920: 1500 -> 960, 3000 -> 1920.
        assertArrayEquals(new int[] {960, 1920}, GifToVideo.encodedSize(1500, 3000, 1920));
        assertArrayEquals(new int[] {2, 2}, GifToVideo.encodedSize(1, 1, 1920));
        int[] s = GifToVideo.encodedSize(1001, 1999, 1000);
        assertEquals(0, s[0] % 2);
        assertEquals(0, s[1] % 2);
        assertEquals(1000, Math.max(s[0], s[1]));
    }

    @Test
    public void bitrateScalesWithPixels() {
        assertEquals(6_000_000, GifToVideo.bitrate(1920, 1080));
        assertEquals(1_500_000, GifToVideo.bitrate(100, 100));
        assertEquals(12_000_000, GifToVideo.bitrate(3840, 2160));
    }

    @Test
    public void hevcOnlyWithAHardwareEncoder() {
        assertEquals("video/avc", GifToVideo.chooseMime(Collections.<String>emptyList()));
        assertEquals("video/avc", GifToVideo.chooseMime(Arrays.asList("c2.android.hevc.encoder")));
        assertEquals("video/hevc",
            GifToVideo.chooseMime(Arrays.asList("c2.android.hevc.encoder", "c2.qti.hevc.encoder")));
    }
}
