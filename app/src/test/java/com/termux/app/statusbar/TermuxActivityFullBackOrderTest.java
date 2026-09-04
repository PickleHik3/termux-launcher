package com.termux.app.statusbar;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.Assert.assertTrue;

public class TermuxActivityFullBackOrderTest {
    /**
     * The Back order now lives in one place, the overlay registry's registration order, and both
     * Back routes derive from it. This pins that order in the production source.
     */
    @Test public void fullPrecedesPaletteDrawerDockAndNavigationInProductionSource() throws Exception {
        String source = read("app/src/main/java/com/termux/app/TermuxActivity.java");
        int method = source.indexOf("OverlayRegistry createOverlayRegistry()");
        int full = source.indexOf("mFullStatusBarController.onBackPressed()", method);
        int palette = source.indexOf("isCommandPaletteOpen()", method);
        int drawer = source.indexOf("mAppDrawerController == null || !mAppDrawerController.isOpen()", method);
        int dock = source.indexOf("mSurfaceEditor.isActive()", method);
        int navigation = source.indexOf("drawer.isDrawerOpen(Gravity.LEFT)", method);
        assertTrue(method > 0 && method < full && full < palette && palette < drawer && drawer < dock
            && dock < navigation);
    }

    /** onBackPressed() itself no longer carries a chain: it asks the registry and then falls back. */
    @Test public void onBackPressedDelegatesToTheRegistry() throws Exception {
        String source = read("app/src/main/java/com/termux/app/TermuxActivity.java");
        int method = source.indexOf("public void onBackPressed()");
        int delegate = source.indexOf("mOverlays.onBackPressed()", method);
        int end = source.indexOf("void finishActivityIfNotFinishing()", method);
        assertTrue(method > 0 && delegate > method && delegate < end);
    }

    private static String read(String relative) throws Exception {
        Path path = Paths.get(relative); if (!Files.exists(path)) path = Paths.get("..").resolve(relative);
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }
}
