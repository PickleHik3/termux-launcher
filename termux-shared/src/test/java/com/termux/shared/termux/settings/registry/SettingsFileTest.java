package com.termux.shared.termux.settings.registry;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.junit.Test;

import java.util.Collections;

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

    @Test
    public void oneLineIsTheLineExportWrites() {
        store.putInt("text_size_percent", 120);
        assertEquals("text.size = 120", SettingsFile.line(registry, "text.size"));
        assertTrue(SettingsFile.export(registry).contains("\n" + SettingsFile.line(registry,
            "terminal.contrast") + "\n"));
    }

    @Test
    public void oneLineOfASettingTheFileLeavesOutIsNull() {
        SettingDeclaration secret = SettingDeclaration.integer("ai.secret",
            SettingDeclaration.Store.APP_PREFERENCES, "ai_secret", 0, 9, 1, 0,
            SettingRefresh.RESTYLE).notExported();
        List<SettingDeclaration> all = new ArrayList<>(SettingDeclarations.ALL);
        all.add(secret);
        SettingsRegistry withSecret = new SettingsRegistry(which -> store, all);
        assertNull(SettingsFile.line(withSecret, "ai.secret"));
        assertNull(SettingsFile.line(withSecret, "terminal.sparkle"));
    }

    @Test
    public void oneLineOfASurfaceOnGlobalSaysFollow() {
        SettingDeclaration surface = SettingDeclaration.integer("look.bar.opacity",
            SettingDeclaration.Store.APP_PREFERENCES, "x", 0, 100, 1, 50, SettingRefresh.LISTENER);
        int[] own = {-1};
        SettingAdapter adapter = new SettingAdapter() {
            @Nullable @Override public SettingDeclaration declaration(@NonNull String id) {
                return id.equals(surface.id) ? surface : null;
            }
            @NonNull @Override public List<SettingDeclaration> declarations() {
                return Collections.singletonList(surface);
            }
            @Override public boolean canFollow(@NonNull SettingDeclaration d) { return true; }
            @Override public boolean overrides(@NonNull SettingDeclaration d) { return own[0] >= 0; }
            @NonNull @Override public String get(@NonNull SettingDeclaration d) {
                return String.valueOf(own[0] >= 0 ? own[0] : 70);
            }
            @Nullable @Override public SettingsRegistry.Refusal check(@NonNull SettingDeclaration d,
                                                                     @NonNull String value) {
                return null;
            }
            @NonNull @Override public SettingsRegistry.Outcome set(@NonNull SettingDeclaration d,
                                                                  @NonNull String value) {
                String old = get(d);
                own[0] = Integer.parseInt(value);
                return new SettingsRegistry.Change(d, old, value);
            }
        };
        SettingsRegistry withSurface = new SettingsRegistry(which -> store, SettingDeclarations.ALL,
            Collections.singletonList(adapter));
        assertEquals("look.bar.opacity = follow", SettingsFile.line(withSurface, surface.id));
        own[0] = 40;
        assertEquals("look.bar.opacity = 40", SettingsFile.line(withSurface, surface.id));
    }
}
