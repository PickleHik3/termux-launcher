package com.termux.app.res;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The Roundline drawables are plain white, so a layout ImageView or ImageButton that shows one
 * must tint it itself; a MaterialButton tints its own icon.
 */
public class RoundlineLayoutTintTest {

    private static final Pattern IMAGE_VIEW = Pattern.compile(
        "<(ImageView|ImageButton|androidx\\.appcompat\\.widget\\.AppCompat\\w+)\\b[^>]*>", Pattern.DOTALL);

    @Test
    public void layoutImageViewsShowingRoundlineIconsAreTinted() throws Exception {
        File dir = new File("src/main/res/layout");
        File[] files = dir.listFiles((d, n) -> n.endsWith(".xml"));
        assertTrue("layout dir found", files != null && files.length > 0);
        StringBuilder bad = new StringBuilder();
        for (File file : files) {
            String xml = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
            Matcher m = IMAGE_VIEW.matcher(xml);
            while (m.find()) {
                String tag = m.group();
                if (tag.contains("@drawable/ic_rl_") && !tag.contains(":tint=")
                    && !tag.contains("imageTint")) {
                    bad.append(file.getName()).append(' ');
                }
            }
        }
        assertTrue("untinted Roundline ImageViews in: " + bad, bad.length() == 0);
    }
}
