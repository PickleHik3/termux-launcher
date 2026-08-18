package com.termux.app.launcher.drawer;

import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.Test;

public class AppDrawerLiveApplyNoOpTest {
    @Test public void duplicateConfigReturnsBeforeClosingOpenDrawer() throws Exception {
        String source = read("app/src/main/java/com/termux/app/launcher/drawer/AppDrawerController.java");
        int start = source.indexOf("public void onPreferencesReloaded()");
        int end = source.indexOf("public boolean onBackPressedInDrawer", start);
        String method = source.substring(start, end);
        assertTrue(method.indexOf("config.equals(mLayoutConfig)")
            < method.indexOf("closeImmediate()"));
    }

    private static String read(String relative) throws Exception {
        Path root = Paths.get(System.getProperty("user.dir"));
        Path path = root.resolve(relative);
        if (!Files.exists(path)) path = root.getParent().resolve(relative);
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }
}
