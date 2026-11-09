package com.termux.app.chrome.scene;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Arrays;
import java.util.Collections;

public class VideoHintTest {

    private static VideoHint.Fields video(String mime, Boolean hardware) {
        VideoHint.Fields f = new VideoHint.Fields();
        f.mime = mime;
        f.width = 1080;
        f.height = 2400;
        f.frameRate = 30f;
        f.durationMs = 12_000L;
        f.sizeBytes = 18_400_000L;
        f.hardware = hardware;
        return f;
    }

    @Test
    public void hardwareHevc() {
        assertEquals("H.265 · 1080×2400 · 30 fps · 12 s · 18 MB · hardware decoded",
            VideoHint.describe(video("video/hevc", true)));
    }

    @Test
    public void softwareAv1() {
        VideoHint.Fields f = video("video/av01", false);
        f.frameRate = 24f;
        f.sizeBytes = 0;
        f.durationMs = 0;
        assertEquals("AV1 · 1080×2400 · 24 fps · software decoded on this phone, expect more battery",
            VideoHint.describe(f));
    }

    @Test
    public void gifHasNoFrameRateAndDecodesOnTheCpu() {
        VideoHint.Fields f = new VideoHint.Fields();
        f.mime = "image/gif";
        f.width = 480;
        f.height = 854;
        f.frameRate = 12f;
        f.durationMs = 3_000L;
        f.sizeBytes = 6_000_000L;
        assertEquals("GIF · 480×854 · 3 s · 6 MB · decodes on the CPU", VideoHint.describe(f));
    }

    @Test
    public void missingFieldsAreLeftOut() {
        VideoHint.Fields f = new VideoHint.Fields();
        assertEquals("Video", VideoHint.describe(f));
        f.mime = "video/avc";
        f.sizeBytes = 400_000L;
        assertEquals("H.264 · 400 KB", VideoHint.describe(f));
    }

    @Test
    public void unknownMimeAndFractionalRateAndLongDuration() {
        VideoHint.Fields f = new VideoHint.Fields();
        f.mime = "video/x-foo";
        f.frameRate = 29.97f;
        f.durationMs = 65_000L;
        assertEquals("X-FOO · 30 fps · 1 min 5 s", VideoHint.describe(f));
        f.frameRate = 23.5f;
        assertEquals("X-FOO · 23.5 fps · 1 min 5 s", VideoHint.describe(f));
    }

    @Test
    public void codecNaming() {
        assertTrue(VideoHint.isHardwareName("c2.qti.hevc.decoder"));
        assertTrue(VideoHint.isHardwareName("OMX.qcom.video.decoder.avc"));
        assertFalse(VideoHint.isHardwareName("c2.android.av1.decoder"));
        assertFalse(VideoHint.isHardwareName("OMX.google.h264.decoder"));
        assertFalse(VideoHint.isHardwareName(null));
        assertNull(VideoHint.hardwareFor(Collections.<String>emptyList()));
        assertEquals(Boolean.FALSE,
            VideoHint.hardwareFor(Arrays.asList("c2.android.av1.decoder", "OMX.google.av1.decoder")));
        assertEquals(Boolean.TRUE,
            VideoHint.hardwareFor(Arrays.asList("c2.android.av1.decoder", "c2.qti.av1.decoder")));
    }

    private static byte[] gif(int w, int h, int... delaysCs) {
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        byte[] sig = {'G', 'I', 'F', '8', '9', 'a'};
        o.write(sig, 0, 6);
        o.write(w & 0xff); o.write(w >> 8); o.write(h & 0xff); o.write(h >> 8);
        o.write(0x80); o.write(0); o.write(0);            // global table of 2 colours
        o.write(0); o.write(0); o.write(0); o.write(255); o.write(255); o.write(255);
        for (int d : delaysCs) {
            o.write(0x21); o.write(0xf9); o.write(4); o.write(0); o.write(d & 0xff); o.write(d >> 8);
            o.write(0); o.write(0);
            o.write(0x2c);
            for (int i = 0; i < 4; i++) o.write(0);
            o.write(w & 0xff); o.write(w >> 8); o.write(h & 0xff); o.write(h >> 8); o.write(0);
            o.write(2); o.write(2); o.write(0x44); o.write(1); o.write(0); // code size, a sub-block, end
        }
        o.write(0x3b);
        return o.toByteArray();
    }

    @Test
    public void gifInfoSumsDelays() throws Exception {
        VideoHint.GifInfo info = VideoHint.GifInfo.read(new ByteArrayInputStream(gif(480, 854, 100, 100, 100)));
        assertNotNull(info);
        assertEquals(480, info.width);
        assertEquals(854, info.height);
        assertEquals(3000L, info.durationMs);
        // A zero delay counts as 100 ms.
        assertEquals(100L, VideoHint.GifInfo.read(new ByteArrayInputStream(gif(2, 2, 0))).durationMs);
    }

    @Test
    public void frameCountDecidesAnimated() throws Exception {
        assertEquals(3, VideoHint.GifInfo.read(new ByteArrayInputStream(gif(4, 4, 10, 10, 10))).frames);
        assertEquals(1, VideoHint.GifInfo.read(new ByteArrayInputStream(gif(4, 4, 10))).frames);
        assertTrue(VideoHint.isAnimated(2));
        assertFalse(VideoHint.isAnimated(1));
        assertFalse(VideoHint.isAnimated(0));
    }

    @Test
    public void convertedGifHint() {
        VideoHint.Fields f = new VideoHint.Fields();
        f.convertedFromGif = true;
        f.mime = "video/avc";
        f.width = 800;
        f.height = 1412;
        f.durationMs = 5_000L;
        f.sizeBytes = 3_100_000L;
        f.hardware = Boolean.TRUE;
        assertEquals("GIF converted to video · 800×1412 · 5 s · 3 MB · hardware decoded", VideoHint.describe(f));
        f.hardware = null;
        assertEquals("GIF converted to video · 800×1412 · 5 s · 3 MB", VideoHint.describe(f));
    }

    @Test
    public void gifInfoRejectsOtherFilesAndSurvivesTruncation() throws Exception {
        assertNull(VideoHint.GifInfo.read(new ByteArrayInputStream("not a gif at all".getBytes("UTF-8"))));
        byte[] whole = gif(10, 10, 50, 50);
        byte[] cut = Arrays.copyOf(whole, whole.length - 12);
        VideoHint.GifInfo info = VideoHint.GifInfo.read(new ByteArrayInputStream(cut));
        assertNotNull(info);
        assertEquals(10, info.width);
    }
}
