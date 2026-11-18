package com.termux.app.wall;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.Test;

/** The wall tells the Display's seam, not its Host, where the display view is bound and lands. */
public class WallDisplaySeamSourceTest {

    private static String read(String name) throws Exception {
        Path file = Paths.get("src/main/java/com/termux/app/" + name);
        if (!Files.exists(file)) file = Paths.get("app").resolve(file);
        return new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
    }

    private static String hostBody() throws Exception {
        String src = read("wall/PaneWallController.java");
        int start = src.indexOf("public interface Host {");
        // The controller has no Host: the whole controller must name none of them.
        if (start < 0) return src;
        int depth = 0;
        for (int i = src.indexOf('{', start); i < src.length(); i++) {
            char c = src.charAt(i);
            if (c == '{') depth++;
            if (c == '}' && --depth == 0) return src.substring(start, i);
        }
        throw new AssertionError("Host is not closed");
    }

    @Test
    public void theHostDeclaresNoDisplayPageMethods() throws Exception {
        String body = hostBody();
        assertFalse(body.contains("bindDisplayPage"));
        assertFalse(body.contains("onDisplayPageLanded"));
    }

    @Test
    public void theActivityKeepsNoDisplayPageBinding() throws Exception {
        String activity = read("TermuxActivity.java");
        assertFalse(activity.contains("private int mBoundDisplayPage"));
        assertFalse(activity.contains("private void applyDisplayPageStill()"));
        assertFalse(activity.contains("private void keepDisplayPagePicture("));
    }
}
