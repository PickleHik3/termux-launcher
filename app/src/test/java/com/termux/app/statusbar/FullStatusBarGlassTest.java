package com.termux.app.statusbar;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class FullStatusBarGlassTest {
    @Test public void fullReusesGlassRadiusCacheAndAlignedFrame() throws Exception {
        String cache = read("app/src/main/java/com/termux/app/chrome/WallpaperBlurCache.java");
        assertTrue(cache.contains("MAX_CACHED_WALLPAPER_BLUR_RADII = 3"));
        String painter = read("app/src/main/java/com/termux/app/chrome/WallpaperFrostPainter.java");
        assertTrue(painter.contains("alignFullStatusBar"));
        String method = painter.substring(painter.indexOf("public void alignFullStatusBar()"),
            painter.indexOf("public void releaseFullStatusBar()"));
        assertTrue(method.contains("effectiveStatusBarBlurRadiusDp()"));
        assertTrue(method.contains("mBlurCache.obtain"));
        assertFalse(method.contains("applyWallpaperFrostCrop"));
        assertFalse(method.contains("Bitmap.createBitmap"));
        assertTrue(method.contains("R.id.terminal_window_bar_wallpaper_backdrop"));
    }

    private static String read(String relative) throws Exception {
        Path path = Paths.get(relative);
        if (!Files.exists(path)) path = Paths.get("..").resolve(relative);
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }
}
