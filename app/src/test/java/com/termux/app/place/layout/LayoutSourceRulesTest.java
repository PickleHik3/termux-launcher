package com.termux.app.place.layout;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Keeps stragglers out (spec §7, §8): after ADR 0020 no code outside the store, its migration and
 * the document router builds a flat layout key, asks for a page layout or takes the retired dock
 * rule. A page wears its kind's layout through DressResolver.layoutOf and nothing else.
 */
public class LayoutSourceRulesTest {

    private static final List<String> ALLOWED = Arrays.asList(
        "place/PlaceLayoutStore.java", "place/LayoutsMigration.java",
        "place/LayoutDocumentPrefs.java", "place/LayoutVariant.java");

    private static final Pattern RETIRED = Pattern.compile(
        "\"layout\\.|\"place\\.dress\\.|\"place\\.page_layout|hasPageLayout\\(|setPageLayout\\("
            + "|dropPageLayout\\(|dockLayoutOf\\(|LayoutVariant\\.page\\(|homePageDockDesign\\(");

    @Test
    public void noCodeOutsideTheStoreReadsTheRetiredLayoutShapes() throws IOException {
        Path root = source();
        List<String> offenders = new ArrayList<>();
        try (Stream<Path> files = Files.walk(root)) {
            for (Path file : (Iterable<Path>) files.filter(p -> p.toString().endsWith(".java"))::iterator) {
                String relative = root.relativize(file).toString().replace('\\', '/');
                if (ALLOWED.contains(relative)) continue;
                String text = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
                if (RETIRED.matcher(text).find()) offenders.add(relative);
            }
        }
        assertTrue("reads a retired layout shape: " + offenders, offenders.isEmpty());
    }

    private static Path source() {
        for (String candidate : new String[]{"src/main/java/com/termux/app",
            "app/src/main/java/com/termux/app"}) {
            Path path = Paths.get(candidate);
            if (Files.isDirectory(path)) return path;
        }
        throw new AssertionError("app sources not found from " + Paths.get("").toAbsolutePath());
    }
}
