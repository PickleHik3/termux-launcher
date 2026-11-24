package com.termux.app.fragments.settings;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * A key that migrated to the registry is reached through it everywhere: no old data store keeps a
 * case for it (it would answer the default and drop the write), the Appearance editor's door to
 * the settings goes through the wrapper, and each migrated screen binds the wrapper.
 */
public class MigratedKeysReachTheRegistryTest {

    private static final String SETTINGS = "app/fragments/settings/termux/";

    private static final List<String> OLD_STORES = Arrays.asList(
        SETTINGS + "TerminalIOPreferencesDataStore.java",
        SETTINGS + "TerminalViewPreferencesDataStore.java",
        SETTINGS + "TermuxStylePreferencesDataStore.java",
        SETTINGS + "TerminalPreferencesFragment.java");

    @Test
    public void noOldDataStoreStillRoutesAMigratedKey() throws IOException {
        List<String> problems = new ArrayList<>();
        for (String store : OLD_STORES) {
            String source = read(sourceRoot().resolve(store));
            for (String key : RegistryPreferenceDataStore.ROWS.keySet()) {
                if (RegistryPreferenceDataStore.ROWS.get(key).equals(
                        com.termux.app.settings.LowMemoryPresetAdapter.ID)) continue;
                if (source.contains("case \"" + key + "\"") || source.contains("\"" + key + "\".equals"))
                    problems.add(store + " still handles the migrated key " + key);
            }
        }
        assertNone(problems);
    }

    @Test
    public void theAppearanceEditorsDoorReachesMigratedKeysThroughTheWrapper() throws IOException {
        String door = read(sourceRoot().resolve(SETTINGS + "AppearanceSettingsStore.java"));
        assertTrue("AppearanceSettingsStore must send RegistryPreferenceDataStore.ROWS keys through "
            + "RegistryPreferenceDataStore (wallpaper_parallax, app_launcher_notification_dots)",
            door.contains("RegistryPreferenceDataStore"));
    }

    @Test
    public void aMigratedScreenBindsTheWrapper() throws IOException {
        List<String> problems = new ArrayList<>();
        for (Map.Entry<String, String> screen : SettingsRegistryScreensTest.MIGRATED.entrySet()) {
            String fragment = read(sourceRoot().resolve(screen.getValue()));
            if (!fragment.contains(".bind(") || !fragment.contains("setPreferenceDataStore(store)"))
                problems.add(screen.getValue() + " does not bind the registry wrapper and set it "
                    + "as its data store");
        }
        assertNone(problems);
    }

    private static String read(Path path) throws IOException {
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }

    private static Path sourceRoot() {
        for (String candidate : new String[]{"src/main/java/com/termux", "app/src/main/java/com/termux"}) {
            Path path = Paths.get(candidate);
            if (Files.isDirectory(path)) return path;
        }
        throw new AssertionError("app sources not found from " + Paths.get("").toAbsolutePath());
    }

    private static void assertNone(List<String> problems) {
        assertTrue("migrated key rule broken (" + problems.size() + "):\n  "
            + String.join("\n  ", problems), problems.isEmpty());
    }
}
