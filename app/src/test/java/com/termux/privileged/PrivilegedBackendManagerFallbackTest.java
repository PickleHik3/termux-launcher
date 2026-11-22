package com.termux.privileged;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static com.termux.privileged.PrivilegedBackendManager.FallbackAfterShizuku.KEEP_SHELL;
import static com.termux.privileged.PrivilegedBackendManager.FallbackAfterShizuku.NONE;
import static com.termux.privileged.PrivilegedBackendManager.FallbackAfterShizuku.SHELL;
import static com.termux.privileged.PrivilegedBackendManager.decideFallback;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PrivilegedBackendManagerFallbackTest {

    @Test
    public void masterOffAlwaysDropsToNothing() {
        assertEquals(NONE, decideFallback(false, true, true));
        assertEquals(NONE, decideFallback(false, false, true));
        assertEquals(NONE, decideFallback(false, false, false));
    }

    @Test
    public void aHealthyShellBackendIsKept() {
        assertEquals(KEEP_SHELL, decideFallback(true, true, true));
        assertEquals(KEEP_SHELL, decideFallback(true, true, false));
    }

    @Test
    public void withoutAHealthyShellTheSettingDecides() {
        assertEquals(SHELL, decideFallback(true, false, true));
        assertEquals(NONE, decideFallback(true, false, false));
    }

    @Test
    public void theResumeRefreshNeverForcesShizukuOnly() throws IOException {
        Path root = Paths.get(System.getProperty("user.dir"));
        Path file = root.resolve("src/main/java/com/termux/app/TermuxActivity.java");
        if (!Files.isRegularFile(file)) {
            file = root.resolve("app/src/main/java/com/termux/app/TermuxActivity.java");
        }
        assertTrue(Files.isRegularFile(file));
        String src = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
        int start = src.indexOf("private void refreshPrivilegedBackendIfNeeded()");
        assertTrue(start >= 0);
        int end = src.indexOf("\n    }\n", start);
        assertTrue(end > start);
        assertFalse("the A-Z Shizuku lock must not knock out the su backend on resume",
            src.substring(start, end).contains("initializeShizukuOnly"));
    }
}
