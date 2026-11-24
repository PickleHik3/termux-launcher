package com.termux.app.settings;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.termux.shared.termux.settings.registry.SettingDeclaration;
import com.termux.shared.termux.settings.registry.SettingsRegistry;

import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/**
 * The text the Assistant embeds ({@code assets/assistant/settings-corpus.tsv}) is generated from the
 * settings registry and shipped in the APK, so a setting that changes fails this until the file is
 * regenerated: {@code REGENERATE_SETTINGS_CORPUS=1 ./gradlew :app:testDebugUnitTest --tests
 * 'com.termux.app.settings.SettingsCorpusTest'}.
 */
public class SettingsCorpusTest {
    private static final String REGENERATE = "REGENERATE_SETTINGS_CORPUS";
    private static final String ASSET = "assistant/settings-corpus.tsv";

    private static String render() {
        StringBuilder out = new StringBuilder();
        for (SettingsRegistry.Entry entry : SettingsReference.registry().list()) {
            SettingDeclaration d = entry.declaration;
            if (d.kind == SettingDeclaration.Kind.TEXT || d.title.isEmpty()) continue;
            out.append(com.termux.app.assistant.SettingsCorpus.line(d.id, d.title, d.summary, d.phrases))
                .append('\n');
        }
        return out.toString();
    }

    private static File asset() {
        for (String candidate : new String[] {"src/main/assets", "app/src/main/assets"}) {
            File dir = new File(candidate);
            if (dir.isDirectory()) return new File(dir, ASSET);
        }
        throw new AssertionError("assets not found from " + new File(".").getAbsolutePath());
    }

    @Test public void theShippedCorpusIsTheRegistrysText() throws IOException {
        String expected = render();
        File file = asset();
        if (System.getenv(REGENERATE) != null) {
            File dir = file.getParentFile();
            if (!dir.isDirectory()) assertTrue(dir.mkdirs());
            Files.write(file.toPath(), expected.getBytes(StandardCharsets.UTF_8));
            return;
        }
        String actual = file.isFile() ? new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8) : "";
        assertEquals("assets/" + ASSET + " is out of date. Regenerate it with: " + REGENERATE
            + "=1 ./gradlew :app:testDebugUnitTest --tests 'com.termux.app.settings.SettingsCorpusTest'"
            + " and commit the file.", expected, actual);
    }

    @Test public void everyLineIsOneIdAndItsText() {
        for (String line : render().split("\n")) {
            String[] parts = line.split("\t", -1);
            assertEquals(line, 2, parts.length);
            assertTrue(line, !parts[0].isEmpty() && !parts[1].isEmpty());
        }
    }
}
