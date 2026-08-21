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
        String source = read("app/src/main/java/com/termux/app/TermuxActivity.java");
        assertTrue(source.contains("MAX_CACHED_WALLPAPER_BLUR_RADII = 3"));
        assertTrue(source.contains("alignFullStatusBarWallpaperFrost"));
        String method = source.substring(source.indexOf("private void alignFullStatusBarWallpaperFrost"),
            source.indexOf("private void releaseFullStatusBarWallpaperFrost"));
        assertTrue(method.contains("getEffectiveStatusBarBlurRadius()"));
        assertTrue(method.contains("obtainCachedAccessoryWallpaperBlur"));
        assertFalse(method.contains("applyWallpaperFrostCrop"));
        assertFalse(method.contains("Bitmap.createBitmap"));
        assertTrue(source.contains("R.id.terminal_window_bar_wallpaper_backdrop"));
    }

    private static String read(String relative) throws Exception {
        Path path = Paths.get(relative);
        if (!Files.exists(path)) path = Paths.get("..").resolve(relative);
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }
}
