package com.termux.app.look;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * The chrome wears one look on every page (ADR 0031, #282): no code builds or draws a second look
 * for a surface both ends of a crossing share. The names below are the blends that did; none may
 * come back under them.
 */
public class OneLookSourceTest {

    private static final String[] GONE = {
        // The stack's look blend and its crossfade sheet, and the dock's opacity between looks.
        "beginStackLookBlend", "syncStackLookBlend", "lookBlendValue", "dockSurfaceOf(",
        // The dock's frost radius and its program's cover between looks.
        "beginDockFrostBlend", "mDockFrostBlendOn", "mDockCoverBlendOn",
        // The bars' arriving frost and their inks under the arriving look.
        "buildArrivingTopFrost", "setArrivingFrost", "onGlassWearing",
        "statusBandOnGlassWearingLook", "arrivingLookAtBegin",
        // The look grid's kind row.
        "ShippedLooks", "SHIPPED_BY_KIND", "LookGridKindSeed", "seedKind(",
    };

    @Test
    public void noSecondLookIsBuiltForASharedSurface() throws IOException {
        List<String> found = new ArrayList<>();
        for (Path root : roots()) {
            try (Stream<Path> files = Files.walk(root)) {
                files.filter(path -> path.toString().endsWith(".java")).forEach(path -> {
                    String text;
                    try {
                        text = new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
                    } catch (IOException e) {
                        throw new AssertionError(e);
                    }
                    for (String name : GONE)
                        if (text.contains(name)) found.add(root.relativize(path) + ": " + name);
                });
            }
        }
        assertTrue(String.join("\n", found), found.isEmpty());
    }

    private static List<Path> roots() {
        List<Path> out = new ArrayList<>();
        for (String candidate : new String[] {"src/main/java", "app/src/main/java",
                "../termux-shared/src/main/java", "termux-shared/src/main/java"}) {
            Path path = Paths.get(candidate);
            if (Files.isDirectory(path)) out.add(path);
        }
        if (out.isEmpty())
            throw new AssertionError("sources not found from " + Paths.get("").toAbsolutePath());
        return out;
    }
}
