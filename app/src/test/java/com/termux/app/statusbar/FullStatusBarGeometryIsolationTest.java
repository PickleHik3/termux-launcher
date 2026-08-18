package com.termux.app.statusbar;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class FullStatusBarGeometryIsolationTest {
    @Test public void controllerUsesExistingHostAndNeverTouchesAccessoryGeometry() throws Exception {
        String controller = source("app/src/main/java/com/termux/app/statusbar/FullStatusBarController.java");
        String activity = source("app/src/main/java/com/termux/app/TermuxActivity.java");
        assertTrue(controller.contains("host.applyFrame"));
        assertTrue(activity.contains("R.id.terminal_window_bar_host"));
        for (String forbidden : new String[] {"computeCombinedHeight", "requestAccessoryGeometrySync",
            "applyAccessoryGeometryIfNeeded", "setTerminalToolbarHeight"}) {
            assertFalse("FULL controller leaked into accessory geometry: " + forbidden,
                controller.contains(forbidden));
        }
        int listener = activity.indexOf("if (v.getId() == R.id.terminal_pane_host)");
        int fullGuard = activity.indexOf("if (isFullStatusBarEngaged()) return;", listener);
        int post = activity.indexOf("v.post(() ->", listener);
        assertTrue("terminal layout listener must be suppressed before it posts during FULL",
            listener >= 0 && fullGuard > listener && fullGuard < post);
    }

    private static String source(String relative) throws Exception {
        Path path = Paths.get(relative);
        if (!Files.exists(path)) path = Paths.get("..").resolve(relative);
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }
}
