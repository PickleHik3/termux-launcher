package com.termux.app.launcher.drawer;

import static org.junit.Assert.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.Test;

public class AppDrawerLiveApplyGeometryIsolationTest {
    @Test public void dedicatedFlowContainsNoFullStyleAccessoryResizeOrRecreateCall() throws Exception {
        String controller = source("app/src/main/java/com/termux/app/launcher/drawer/AppDrawerController.java");
        String apply = controller.substring(controller.indexOf("private void applyLayoutConfig()"),
            controller.indexOf("private float resolveCellLabelHeightPx"));
        for (String forbidden : new String[]{"reloadActivityStyling", "setMargins(",
                "applySuggestionBar", "requestAccessoryGeometrySync", "updateSize(", "recreate("})
            assertFalse(forbidden, apply.contains(forbidden));
        String activity = source("app/src/main/java/com/termux/app/TermuxActivity.java");
        int action = activity.indexOf("case TERMUX_ACTIVITY.ACTION_RELOAD_APP_DRAWER:");
        String handler = activity.substring(action, activity.indexOf("case TERMUX_ACTIVITY.ACTION_REQUEST_PERMISSIONS", action));
        assertTrue(handler.contains("onPreferencesReloaded"));
        assertFalse(handler.contains("reloadActivityStyling"));
        assertFalse(handler.contains("requestAccessoryGeometrySync"));
    }
    private static String source(String relative) throws Exception {
        Path root = Paths.get(System.getProperty("user.dir"));
        Path path = root.resolve(relative);
        if (!Files.exists(path)) path = root.getParent().resolve(relative);
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }
}
