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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The registry's real order is {@link TerlBackPolicy#ORDER} (#434): the registrations tagged
 * {@code // terl-back: NAME} in {@code createOverlayRegistry} read top to bottom are the policy's
 * surfaces, topmost first.
 */
public class TerlBackOrderSourceTest {

    @Test
    public void registrationOrderIsThePolicyOrder() throws IOException {
        Path root = Paths.get(System.getProperty("user.dir"));
        Path file = root.resolve("src/main/java/com/termux/app/TermuxActivity.java");
        if (!Files.isRegularFile(file))
            file = root.resolve("app/src/main/java/com/termux/app/TermuxActivity.java");
        assertTrue(Files.isRegularFile(file));
        String text = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
        int start = text.indexOf("createOverlayRegistry() {");
        int end = text.indexOf("return registry;", start);
        assertTrue(start > 0 && end > start);
        Matcher m = Pattern.compile("// terl-back: ([A-Z_]+)\\s*\\n(?:\\s*//[^\\n]*\\n)*\\s*registry\\.register\\(")
            .matcher(text.substring(start, end));
        List<String> found = new ArrayList<>();
        while (m.find()) found.add(m.group(1));
        List<String> expected = new ArrayList<>();
        for (TerlBackPolicy.Surface s : TerlBackPolicy.ORDER) expected.add(s.name());
        assertEquals(expected, found);
    }
}
