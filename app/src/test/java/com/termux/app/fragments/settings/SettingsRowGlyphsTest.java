package com.termux.app.fragments.settings;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A settings screen whose rows wear glyphs gives each row its own: two rows sharing one glyph read
 * as one setting (Composite signal wore the Retro effect's TV, Idle glitch rate the Idle
 * animations glyph).
 */
public class SettingsRowGlyphsTest {

    private static final Pattern ICON = Pattern.compile("app:icon=\"(@drawable/[\\w]+)\"");
    private static final Pattern KEY = Pattern.compile("app:key=\"([\\w.]+)\"");
    private static final Pattern ELEMENT = Pattern.compile("<[A-Za-z][\\w.]*\\s[^<>]*?/?>", Pattern.DOTALL);

    @Test
    public void noTwoRowsOnAScreenShareAGlyph() throws IOException {
        List<String> problems = new ArrayList<>();
        try (DirectoryStream<Path> files = Files.newDirectoryStream(xmlDir(), "*_preferences.xml")) {
            for (Path file : files) {
                String xml = new String(Files.readAllBytes(file), StandardCharsets.UTF_8)
                    .replaceAll("(?s)<!--.*?-->", "");
                Map<String, String> owner = new HashMap<>();
                Matcher element = ELEMENT.matcher(xml);
                while (element.find()) {
                    Matcher icon = ICON.matcher(element.group());
                    if (!icon.find()) continue;
                    Matcher key = KEY.matcher(element.group());
                    String row = key.find() ? key.group(1) : element.group();
                    String first = owner.putIfAbsent(icon.group(1), row);
                    if (first != null) {
                        problems.add(file.getFileName() + ": " + row + " wears " + icon.group(1)
                            + ", already " + first + "'s");
                    }
                }
            }
        }
        assertTrue("rows sharing a glyph:\n  " + String.join("\n  ", problems), problems.isEmpty());
    }

    private static Path xmlDir() {
        for (String candidate : new String[]{"src/main/res/xml", "app/src/main/res/xml"}) {
            Path path = Paths.get(candidate);
            if (Files.isDirectory(path)) return path;
        }
        throw new AssertionError("res/xml not found from " + Paths.get("").toAbsolutePath());
    }
}
