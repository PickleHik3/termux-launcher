package com.termux.app.settings;

import static org.junit.Assert.assertTrue;

import com.termux.shared.termux.settings.registry.SettingDeclaration;
import com.termux.shared.termux.settings.registry.SettingDeclarations;
import com.termux.shared.termux.settings.registry.SettingRefresh;

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
 * A setting that declares {@link SettingRefresh#LISTENER} promises that something already
 * listens: the applier adds no refresh for it. The check is a source scan: the stored key must
 * appear in a launcher or preferences source outside the registry and the Settings rows, the
 * reader that picks the value up.
 */
public class ListenerSettingsHaveAReaderTest {

    @Test
    public void everyListenerSettingsKeyIsReadByTheLauncher() throws IOException {
        List<Path> sources = new ArrayList<>();
        for (String root : new String[] {"src/main/java", "app/src/main/java",
                "../termux-shared/src/main/java", "termux-shared/src/main/java"}) {
            Path path = Paths.get(root);
            if (!Files.isDirectory(path)) continue;
            try (Stream<Path> walk = Files.walk(path)) {
                walk.filter(p -> p.toString().endsWith(".java")
                    && !p.toString().contains("/settings/registry/")
                    && !p.toString().contains("/fragments/settings/")).forEach(sources::add);
            }
        }
        assertTrue("no sources found from " + Paths.get("").toAbsolutePath(), !sources.isEmpty());
        List<String> texts = new ArrayList<>();
        for (Path source : sources) texts.add(new String(Files.readAllBytes(source),
            StandardCharsets.UTF_8));
        List<String> problems = new ArrayList<>();
        for (SettingDeclaration declaration : SettingDeclarations.ALL) {
            if (declaration.refresh != SettingRefresh.LISTENER) continue;
            boolean read = false;
            for (String text : texts) read |= text.contains("\"" + declaration.key + "\"");
            if (!read) problems.add(declaration.id + " declares LISTENER but nothing reads "
                + declaration.key);
        }
        assertTrue(String.join("\n", problems), problems.isEmpty());
    }
}
