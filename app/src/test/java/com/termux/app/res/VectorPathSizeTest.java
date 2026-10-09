package com.termux.app.res;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * aapt2 cannot encode a resource string over 32767 bytes: it writes "STRING_TOO_LARGE" in its
 * place (a build warning only), and the vector then throws when it inflates on a device. The
 * Codex-drawn lock clock digits did exactly that on 2026-10-03. Every vector path stays far under.
 */
public class VectorPathSizeTest {

    private static final int LIMIT = 16_000;
    private static final Pattern PATH_DATA = Pattern.compile("android:pathData=\"([^\"]*)\"");

    @Test
    public void everyVectorPathFitsAResourceString() throws Exception {
        File res = new File("src/main/res");
        if (!res.isDirectory()) res = new File("app/src/main/res");
        assertTrue("res directory found", res.isDirectory());
        StringBuilder offenders = new StringBuilder();
        File[] dirs = res.listFiles((dir, name) -> name.startsWith("drawable"));
        if (dirs != null) {
            for (File dir : dirs) {
                File[] files = dir.listFiles((d, name) -> name.endsWith(".xml"));
                if (files == null) continue;
                for (File file : files) {
                    String xml = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
                    Matcher m = PATH_DATA.matcher(xml);
                    while (m.find()) {
                        int length = m.group(1).getBytes(StandardCharsets.UTF_8).length;
                        if (length > LIMIT)
                            offenders.append(dir.getName()).append('/').append(file.getName())
                                .append(": ").append(length).append(" bytes\n");
                    }
                }
            }
        }
        assertTrue("vector paths too long for aapt2:\n" + offenders, offenders.length() == 0);
    }
}
