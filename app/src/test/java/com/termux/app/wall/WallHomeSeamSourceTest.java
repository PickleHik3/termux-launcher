package com.termux.app.wall;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/** The wall asks Home's seam, not its Host, for Home's run, press page and shift. */
public class WallHomeSeamSourceTest {

    private static String hostBody() throws Exception {
        Path file = Paths.get("src/main/java/com/termux/app/wall/PaneWallController.java");
        if (!Files.exists(file)) file = Paths.get("app").resolve(file);
        String src = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
        int start = src.indexOf("public interface Host {");
        assertTrue(start >= 0);
        int depth = 0;
        for (int i = src.indexOf('{', start); i < src.length(); i++) {
            char c = src.charAt(i);
            if (c == '{') depth++;
            if (c == '}' && --depth == 0) return src.substring(start, i);
        }
        throw new AssertionError("Host is not closed");
    }

    @Test
    public void theHostDeclaresNoHomeMethods() throws Exception {
        String body = hostBody();
        assertFalse(body.contains("bindHomeRun"));
        assertFalse(body.contains("homePressPage"));
        assertFalse(body.contains("innerShiftFraction"));
    }
}
