package com.termux.app.chrome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * Every overlay the activity registers has said what it gives to the Home exit (ADR 0034): its
 * views, or an {@code exitViews} that adds none. A surface that says nothing would close on its
 * own on a home press, ahead of the group.
 */
public class OverlayExitStatedTest {

    /** The registrations in {@code createOverlayRegistry}, so a new one has to be counted here. */
    private static final int REGISTERED = 20;

    @Test
    public void everyRegisteredOverlayStatesItsExitViews() throws IOException {
        Path root = Paths.get(System.getProperty("user.dir"));
        Path file = root.resolve("src/main/java/com/termux/app/TermuxActivity.java");
        if (!Files.isRegularFile(file))
            file = root.resolve("app/src/main/java/com/termux/app/TermuxActivity.java");
        assertTrue("TermuxActivity.java has to be findable", Files.isRegularFile(file));
        String text = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);

        int start = text.indexOf("createOverlayRegistry() {");
        int end = text.indexOf("return registry;", start);
        assertTrue(start > 0 && end > start);
        String[] parts = text.substring(start, end).split("registry\\.register\\(");

        List<String> silent = new ArrayList<>();
        // Overview registers its controller, which states its own (checked below).
        boolean controllerStates = new String(
            Files.readAllBytes(file.resolveSibling("overview/OverviewController.java")),
            StandardCharsets.UTF_8).contains("void exitViews(");
        for (int i = 1; i < parts.length; i++) {
            boolean viaController = parts[i].startsWith("overview())") && controllerStates;
            if (!viaController && !parts[i].contains("exitViews(")) silent.add("registration #" + i);
        }
        assertEquals("overlays that have not stated their exit views: " + silent,
            0, silent.size());
        assertEquals("a registration was added or removed; state its exit views and count it",
            REGISTERED, parts.length - 1);
    }
}
