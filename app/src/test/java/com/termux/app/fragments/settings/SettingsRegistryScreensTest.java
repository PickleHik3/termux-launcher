package com.termux.app.fragments.settings;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.termux.shared.termux.settings.registry.SettingDeclaration;
import com.termux.shared.termux.settings.registry.SettingDeclarations;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Settings screens and the Settings registry (#369): a screen that has migrated saves its rows
 * through {@link RegistryPreferenceDataStore}, so every one of its value rows has a registry
 * entry, its fragment uses the wrapper, and a range its XML sets itself matches the declaration.
 * Screens that have not migrated sit on {@link #NOT_MIGRATED}, which only shrinks: a screen that
 * migrates leaves it and goes on {@link #MIGRATED}. Settings search index XMLs are not screens.
 */
public class SettingsRegistryScreensTest {

    /** Migrated screens: the XML's name, and the fragment source (relative to com/termux) that hosts it. */
    static final Map<String, String> MIGRATED = new LinkedHashMap<>();

    /** Screens still on their old data store. It only shrinks. */
    static final List<String> NOT_MIGRATED = Arrays.asList(
        "about_support_preferences.xml",
        "advanced_diagnostics_preferences.xml",
        "app_drawer_preferences.xml",
        "backup_restore_preferences.xml",
        "general_preferences.xml",
        "home_preferences.xml",
        "launcher_lock_preferences.xml",
        "launcher_preferences.xml",
        "launcher_search_preferences.xml",
        "notifications_preferences.xml",
        "performance_preferences.xml",
        "read_aloud_preferences.xml",
        "root_preferences.xml",
        "services_permissions_preferences.xml",
        "status_bar_preferences.xml",
        "tai_advanced_preferences.xml",
        "tai_api_preferences.xml",
        "tai_remote_preferences.xml",
        "tai_runtime_preferences.xml",
        "terminal_clipboard_preferences.xml",
        "terminal_preferences.xml",
        "terminal_text_preferences.xml",
        "terminal_windows_preferences.xml",
        "termux_ai_preferences.xml",
        "termux_api_preferences.xml",
        "termux_fonts_preferences.xml",
        "termux_keyboard_hardware_preferences.xml",
        "termux_keyboard_layout_preferences.xml",
        "termux_keyboard_preferences.xml",
        "termux_keyboard_typing_preferences.xml",
        "termux_keyboard_voice_details_preferences.xml",
        "termux_keyboard_voice_preferences.xml",
        "termux_privileged_access_preferences.xml",
        "tui_app_themes_preferences.xml",
        "x11_display_input_preferences.xml",
        "x11_display_preferences.xml",
        "x11_display_resolution_preferences.xml",
        "x11_display_startup_preferences.xml",
        "x11_display_troubleshooting_preferences.xml"
    );

    // ---------------------------------------------------------------------------- the rules

    @Test
    public void everyScreenIsMigratedOrOnTheAllowlistAndNoEntryIsStale() throws IOException {
        TreeSet<String> screens = new TreeSet<>(screenNames(xmlDir()));
        List<String> problems = new ArrayList<>();
        for (String screen : screens) {
            boolean migrated = MIGRATED.containsKey(screen);
            boolean listed = NOT_MIGRATED.contains(screen);
            if (migrated && listed) problems.add(screen + " is both migrated and on the allowlist");
            if (!migrated && !listed) problems.add(screen + " is on neither list");
        }
        for (String name : NOT_MIGRATED)
            if (!screens.contains(name)) problems.add("stale allowlist entry " + name);
        for (String name : MIGRATED.keySet())
            if (!screens.contains(name)) problems.add("stale migrated entry " + name);
        assertNone(problems);
    }

    @Test
    public void aMigratedScreenHasARegistryEntryPerRowAndUsesTheWrapper() throws IOException {
        List<String> problems = new ArrayList<>();
        for (Map.Entry<String, String> screen : MIGRATED.entrySet()) {
            String xml = read(xmlDir().resolve(screen.getKey()));
            problems.addAll(rowsWithoutEntry(screen.getKey(), xml,
                RegistryPreferenceDataStore.ROWS.keySet()));
            problems.addAll(rangeProblems(screen.getKey(), xml, RegistryPreferenceDataStore.ROWS,
                SettingDeclarations.ALL));
            String fragment = read(sourceRoot().resolve(screen.getValue()));
            if (!fragment.contains("RegistryPreferenceDataStore"))
                problems.add(screen.getValue() + " hosts " + screen.getKey()
                    + " but does not use RegistryPreferenceDataStore");
        }
        assertNone(problems);
    }

    @Test
    public void everyMigratedRowNamesADeclaration() {
        List<String> problems = new ArrayList<>();
        for (Map.Entry<String, String> row : RegistryPreferenceDataStore.ROWS.entrySet()) {
            boolean found = false;
            for (SettingDeclaration declaration : SettingDeclarations.ALL)
                if (declaration.id.equals(row.getValue())) found = true;
            if (!found) problems.add(row.getKey() + " names the unknown setting " + row.getValue());
        }
        assertNone(problems);
    }

    // ------------------------------------------------------------------- the rules, on samples

    @Test
    public void aValueRowWithoutARegistryEntryIsReported() {
        String xml = "<PreferenceScreen xmlns:app=\"a\" xmlns:android=\"b\">"
            + "<PreferenceCategory app:key=\"cat\" />"
            + "<Preference app:key=\"open_door\" />"
            + "<Preference app:key=\"quiet\" app:persistent=\"false\" />"
            + "<SwitchPreferenceCompat app:key=\"known\" />"
            + "<SwitchPreferenceCompat app:key=\"unknown\" />"
            + "</PreferenceScreen>";
        assertEquals(Collections.singletonList("s.xml: row unknown has no registry entry"),
            rowsWithoutEntry("s.xml", xml, Collections.singleton("known")));
    }

    @Test
    public void aRangeTheXmlSetsItselfMustMatchTheDeclaration() {
        SettingDeclaration size = SettingDeclarations.TEXT_SIZE;
        Map<String, String> rows = Collections.singletonMap("size_row", size.id);
        String good = slider("size_row", size.min, size.max);
        String bad = slider("size_row", size.min, size.max + 10);
        assertTrue(rangeProblems("s.xml", good, rows, SettingDeclarations.ALL).isEmpty());
        assertEquals(1, rangeProblems("s.xml", bad, rows, SettingDeclarations.ALL).size());
        assertTrue(rangeProblems("s.xml", slider("other", 0, 5), rows,
            SettingDeclarations.ALL).isEmpty());
    }

    @Test
    public void searchIndexFilesAreNotScreens() throws IOException {
        for (String name : screenNames(xmlDir()))
            assertTrue(name, !name.endsWith("_search_index.xml"));
    }

    // ------------------------------------------------------------------------------ helpers

    private static String slider(String key, int min, int max) {
        return "<PreferenceScreen xmlns:app=\"a\" xmlns:android=\"b\"><SliderPreference app:key=\""
            + key + "\" app:min=\"" + min + "\" android:max=\"" + max + "\" /></PreferenceScreen>";
    }

    private static final Pattern ELEMENT = Pattern.compile("<([A-Za-z][\\w.]*)\\s([^<>]*?)/?>",
        Pattern.DOTALL);

    private static String attribute(String attributes, String name) {
        Matcher m = Pattern.compile("(?:app|android):" + name + "=\"([^\"]*)\"").matcher(attributes);
        return m.find() ? m.group(1) : null;
    }

    /** Value rows of a screen: a keyed, persistent element that is not a category or a plain door. */
    static List<String> rowsWithoutEntry(String screen, String xml, java.util.Set<String> migrated) {
        List<String> problems = new ArrayList<>();
        Matcher m = ELEMENT.matcher(withoutComments(xml));
        while (m.find()) {
            String tag = m.group(1);
            String key = attribute(m.group(2), "key");
            if (key == null || tag.equals("PreferenceScreen") || tag.equals("PreferenceCategory")
                    || tag.equals("Preference") || "false".equals(attribute(m.group(2), "persistent")))
                continue;
            if (!migrated.contains(key)) problems.add(screen + ": row " + key + " has no registry entry");
        }
        return problems;
    }

    /** Rows with their own min and max in XML, checked against the declaration their key names. */
    static List<String> rangeProblems(String screen, String xml, Map<String, String> rows,
                                      List<SettingDeclaration> declarations) {
        List<String> problems = new ArrayList<>();
        Matcher m = ELEMENT.matcher(withoutComments(xml));
        while (m.find()) {
            String key = attribute(m.group(2), "key");
            String min = attribute(m.group(2), "min");
            String max = attribute(m.group(2), "max");
            String id = key == null ? null : rows.get(key);
            if (id == null || min == null || max == null) continue;
            for (SettingDeclaration declaration : declarations) {
                if (!declaration.id.equals(id)) continue;
                if (declaration.min != Integer.parseInt(min) || declaration.max != Integer.parseInt(max))
                    problems.add(screen + ": row " + key + " is " + min + " to " + max
                        + " in XML but " + declaration.min + " to " + declaration.max + " in " + id);
            }
        }
        return problems;
    }

    private static String withoutComments(String xml) {
        return xml.replaceAll("(?s)<!--.*?-->", "");
    }

    /** XML files whose root is a PreferenceScreen and that are not a search index. */
    static List<String> screenNames(Path dir) throws IOException {
        List<String> names = new ArrayList<>();
        try (DirectoryStream<Path> files = Files.newDirectoryStream(dir, "*.xml")) {
            for (Path file : files) {
                String name = file.getFileName().toString();
                if (name.endsWith("_search_index.xml")) continue;
                String text = withoutComments(read(file));
                Matcher root = Pattern.compile("<([A-Za-z][\\w.]*)").matcher(text);
                if (root.find() && root.group(1).equals("PreferenceScreen")) names.add(name);
            }
        }
        Collections.sort(names);
        return names;
    }

    private static String read(Path path) throws IOException {
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }

    private static Path xmlDir() {
        for (String candidate : new String[]{"src/main/res/xml", "app/src/main/res/xml"}) {
            Path path = Paths.get(candidate);
            if (Files.isDirectory(path)) return path;
        }
        throw new AssertionError("res/xml not found from " + Paths.get("").toAbsolutePath());
    }

    private static Path sourceRoot() {
        for (String candidate : new String[]{"src/main/java/com/termux", "app/src/main/java/com/termux"}) {
            Path path = Paths.get(candidate);
            if (Files.isDirectory(path)) return path;
        }
        throw new AssertionError("app sources not found from " + Paths.get("").toAbsolutePath());
    }

    private static void assertNone(List<String> problems) {
        assertTrue("settings registry rule broken (" + problems.size() + "):\n  "
            + String.join("\n  ", problems), problems.isEmpty());
    }
}
