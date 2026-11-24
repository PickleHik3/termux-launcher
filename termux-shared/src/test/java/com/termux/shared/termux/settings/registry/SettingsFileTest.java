package com.termux.shared.termux.settings.registry;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** The settings file: what export writes, and how apply checks every line before any is saved. */
public class SettingsFileTest {

    private final InMemorySettingStore store = new InMemorySettingStore();
    private final SettingsRegistry registry = new SettingsRegistry(which -> store);

    @Test
    public void exportWritesOneIdEqualsValueLinePerSetting() {
        store.putInt("text_size_percent", 120);
        String text = SettingsFile.export(registry);
        assertTrue(text, text.contains("\ntext.size = 120"));
        assertTrue(text, text.contains("\nterminal.contrast = default"));
        assertTrue(text, text.startsWith("#"));
        assertFalse(text, text.endsWith("\n"));
    }

    @Test
    public void aSettingThatIsNotExportedIsAbsent() {
        SettingDeclaration secret = SettingDeclaration.integer("ai.secret",
            SettingDeclaration.Store.APP_PREFERENCES, "ai_secret", 0, 9, 1, 0,
            SettingRefresh.RESTYLE).notExported();
        List<SettingDeclaration> all = new ArrayList<>(SettingDeclarations.ALL);
        all.add(secret);
        SettingsRegistry withSecret = new SettingsRegistry(which -> store, all);
        String text = SettingsFile.export(withSecret);
        assertTrue(text, text.contains("text.size = "));
        assertFalse(text, text.contains("ai.secret"));
        SettingsFile.Result result = SettingsFile.check("ai.secret = 3", withSecret);
        assertEquals(Arrays.asList("line 1: no setting is called \"ai.secret\""), result.errors);
    }

    @Test
    public void exportThenCheckIsClean() {
        store.putString("terminal_contrast_level", "harder");
        SettingsFile.Result result = SettingsFile.check(SettingsFile.export(registry), registry);
        assertTrue(result.errors.toString(), result.errors.isEmpty());
        assertTrue(result.assignments.size() >= 3);
    }

    @Test
    public void commentsAndBlankLinesAreIgnored() {
        SettingsFile.Result result = SettingsFile.check(
            "# my setup\n\n   \n  # indented\ntext.size = 90  \r\n\n", registry);
        assertTrue(result.errors.toString(), result.errors.isEmpty());
        assertEquals(1, result.assignments.size());
        assertEquals("text.size", result.assignments.get(0).id);
        assertEquals("90", result.assignments.get(0).value);
    }

    @Test
    public void everyBadLineIsReportedWithItsNumberAndNothingIsWritten() {
        String text = "# top\ntext.size = 400\ntext.size = 90\nterminal.sparkle = 1\nno equals here\n"
            + "terminal.contrast = loudest\n";
        SettingsFile.Result result = SettingsFile.check(text, registry);
        assertEquals(Arrays.asList(
            "line 2: text.size is 80–150",
            "line 4: no setting is called \"terminal.sparkle\"",
            "line 5: expected id = value",
            "line 6: terminal.contrast is one of softer, default, harder"), result.errors);
        assertEquals("100", registry.get("text.size"));
    }
}
