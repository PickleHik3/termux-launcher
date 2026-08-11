package com.termux.app.statusbar;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.Assert.assertTrue;

public class TermuxActivityFullBackOrderTest {
    @Test public void fullPrecedesPaletteDrawerDockAndNavigationInProductionSource() throws Exception {
        String source = read("app/src/main/java/com/termux/app/TermuxActivity.java");
        int method = source.indexOf("public void onBackPressed()");
        int full = source.indexOf("mFullStatusBarController.onBackPressed()", method);
        int palette = source.indexOf("isCommandPaletteOpen()", method);
        int drawer = source.indexOf("mAppDrawerController != null", method);
        int dock = source.indexOf("mDockTuningMode", method);
        int navigation = source.indexOf("getDrawer().isDrawerOpen", method);
        assertTrue(method < full && full < palette && palette < drawer && drawer < dock
            && dock < navigation);
    }

    private static String read(String relative) throws Exception {
        Path path = Paths.get(relative); if (!Files.exists(path)) path = Paths.get("..").resolve(relative);
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }
}
