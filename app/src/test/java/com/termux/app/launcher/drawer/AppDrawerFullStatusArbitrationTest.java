package com.termux.app.launcher.drawer;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class AppDrawerFullStatusArbitrationTest {
    @Test public void fullVetoIsReadByActualDownSnapshotAndClearsAfterExit() throws Exception {
        AppDrawerGestureArbiter.Eligibility full = new AppDrawerGestureArbiter.Eligibility(
            true, true, true, true, true, true, true, true, false);
        assertFalse(full.drawerEligible());
        AppDrawerGestureArbiter.Eligibility closed = new AppDrawerGestureArbiter.Eligibility(
            true, true, true, true, true, true, true, true, true);
        assertTrue(closed.drawerEligible());
        String dock = read("app/src/main/java/com/termux/app/SuggestionBarView.java");
        int capture = dock.indexOf("captureDrawerEligibility()");
        assertTrue(dock.indexOf("listener.isFullStatusPaneClosed()", capture) > capture);
        String activity = read("app/src/main/java/com/termux/app/TermuxActivity.java");
        assertTrue(activity.contains("return !TermuxActivity.this.isFullStatusBarEngaged()"));
    }

    private static String read(String relative) throws Exception {
        Path path = Paths.get(relative); if (!Files.exists(path)) path = Paths.get("..").resolve(relative);
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }
}
