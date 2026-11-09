package com.termux.app.chrome.scene;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class VideoImportTest {

    private static void assertSafe(String name) {
        assertFalse(name.isEmpty());
        assertTrue(name, WallpaperMotion.safeName(name));
        assertEquals(WallpaperMotion.Kind.VIDEO, WallpaperMotion.video(name).kind);
    }

    @Test
    public void plainNamesStay() {
        assertEquals("loop.mp4", VideoImport.safeName("loop.mp4", "video/mp4"));
        assertEquals("my loop-1_a.mkv", VideoImport.safeName("my loop-1_a.mkv", "video/x-matroska"));
    }

    @Test
    public void unsafeCharactersBecomeUnderscores() {
        assertEquals("b_c.mp4", VideoImport.safeName("a/b:c.mp4", "video/mp4"));
        String name = VideoImport.safeName("..//etc\\pass:wd\n.mp4", "video/mp4");
        assertSafe(name);
        assertFalse(name.startsWith("."));
        assertFalse(name.contains("/"));
        assertFalse(name.contains(":"));
    }

    @Test
    public void emptyAndDotNamesGetADefault() {
        assertSafe(VideoImport.safeName(null, null));
        assertSafe(VideoImport.safeName("", "video/mp4"));
        assertSafe(VideoImport.safeName("..", "video/mp4"));
        assertSafe(VideoImport.safeName("   ", null));
    }

    @Test
    public void gifGetsItsExtension() {
        assertEquals("anim.gif", VideoImport.safeName("anim", "image/gif"));
        assertEquals("anim.GIF", VideoImport.safeName("anim.GIF", "image/gif"));
        assertTrue(VideoImport.isGif("anim.GIF"));
        assertFalse(VideoImport.isGif("anim.mp4"));
        assertFalse(VideoImport.isGif(null));
    }

    @Test
    public void longNamesAreCutKeepingTheExtension() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 300; i++) sb.append('x');
        String name = VideoImport.safeName(sb + ".mp4", "video/mp4");
        assertTrue(name.length() <= 100);
        assertTrue(name.endsWith(".mp4"));
        assertSafe(name);
    }
}
