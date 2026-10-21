package com.termux.app;

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
 * Android compiles {@link java.util.regex.Pattern} with ICU, which rejects the JDK's Unicode
 * character class flag ({@code (?U)}, {@code UNICODE_CHARACTER_CLASS}). These tests run on the JDK,
 * which accepts it, so a pattern using it passes here and throws on the device; this scan is the
 * only place the flag can be caught. ICU's {@code \s}, {@code \w} and {@code \b} are Unicode already.
 */
public class IcuRegexCompatTest {

    @Test
    public void noSourceUsesTheUnicodeCharacterClassFlag() throws IOException {
        List<String> hits = new ArrayList<>();
        try (Stream<Path> files = Files.walk(Paths.get("src/main/java"))) {
            for (Path file : (Iterable<Path>) files.filter(p -> p.toString().endsWith(".java"))::iterator) {
                List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
                for (int i = 0; i < lines.size(); i++) {
                    String line = lines.get(i);
                    if (line.matches(".*\\(\\?[a-zA-Z]*U[a-zA-Z]*[):].*") || line.contains("UNICODE_CHARACTER_CLASS")) {
                        hits.add(file + ":" + (i + 1));
                    }
                }
            }
        }
        assertTrue("ICU rejects the Unicode character class flag: " + hits, hits.isEmpty());
    }
}
