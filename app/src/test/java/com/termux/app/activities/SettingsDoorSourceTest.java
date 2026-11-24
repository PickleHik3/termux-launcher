package com.termux.app.activities;

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
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Settings has one door: {@link SettingsDoor} (and {@code SettingsActivity.createFragmentIntent},
 * which calls it). A new {@code Intent(..., SettingsActivity.class)} anywhere else would start
 * Settings without its own task, so a home press clears it on screen again.
 */
public class SettingsDoorSourceTest {

    private static final Pattern DIRECT = Pattern.compile("SettingsActivity\\s*\\.\\s*class");
    private static final Pattern DOOR_FILES = Pattern.compile(".*/activities/Settings(Door|Activity)\\.java");

    private static Path mainSources() {
        for (String base : new String[]{"src/main/java", "app/src/main/java"}) {
            Path p = Paths.get(base);
            if (Files.isDirectory(p)) return p;
        }
        throw new AssertionError("main sources not found");
    }

    /** Offending lines (path:line) in the given sources; comment lines are skipped. */
    static List<String> offenders(Path root) throws IOException {
        List<String> out = new ArrayList<>();
        List<Path> files = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(root)) {
            walk.filter(x -> x.toString().endsWith(".java")).forEach(files::add);
        }
        for (Path f : files) {
            if (DOOR_FILES.matcher(f.toString().replace('\\', '/')).matches()) continue;
            List<String> lines = Files.readAllLines(f, StandardCharsets.UTF_8);
            for (int i = 0; i < lines.size(); i++) {
                String t = lines.get(i).trim();
                if (t.startsWith("*") || t.startsWith("//") || t.startsWith("/*")) continue;
                if (DIRECT.matcher(t).find()) out.add(f + ":" + (i + 1));
            }
        }
        return out;
    }

    @Test
    public void noLaunchSiteBuildsAnIntentForSettingsDirectly() throws IOException {
        assertEquals("Open Settings through SettingsDoor.plain or createFragmentIntent",
            new ArrayList<String>(), offenders(mainSources()));
    }

    @Test
    public void theScanSeesADirectIntent() throws IOException {
        Path dir = Files.createTempDirectory("settingsdoor");
        Path f = dir.resolve("Bad.java");
        Files.write(f, "class Bad { void x(Context c) { c.startActivity(new Intent(c, SettingsActivity.class)); } }\n"
            .getBytes(StandardCharsets.UTF_8));
        try {
            assertEquals(1, offenders(dir).size());
        } finally {
            Files.delete(f);
            Files.delete(dir);
        }
    }

    @Test
    public void theSourceRootIsFound() {
        assertTrue(Files.isDirectory(mainSources()));
    }
}
