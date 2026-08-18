package com.termux.app.launcher.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.StringReader;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;

public class LauncherCategoryFileTest {

    @Rule public TemporaryFolder temporaryFolder = new TemporaryFolder();

    private static LauncherCategoryFile parse(String text) throws Exception {
        return LauncherCategoryFile.parse(new StringReader(text));
    }

    @Test public void roundTripsParseWriteParse() throws Exception {
        LauncherCategoryFile original = parse(""
            + "[social]\n"
            + "com.whatsapp\n"
            + "org.telegram.messenger\n"
            + "\n"
            + "[utilities]\n"
            + "com.android.chrome\n");

        File file = new File(temporaryFolder.getRoot(), "app-categories.conf");
        original.write(file);
        LauncherCategoryFile reparsed = LauncherCategoryFile.parse(file);

        assertEquals(original.sectionOrder(), reparsed.sectionOrder());
        assertEquals(original.sections(), reparsed.sections());
        assertTrue(reparsed.warnings().isEmpty());
        assertEquals("social", reparsed.categoryForPackage("com.whatsapp"));
    }

    @Test public void writesHeaderAndSurvivesMissingFile() throws Exception {
        LinkedHashMap<String, List<String>> sections = new LinkedHashMap<>();
        sections.put("games", Arrays.asList("com.mojang.minecraftpe"));
        File file = new File(temporaryFolder.getRoot(), "nested/app-categories.conf");

        LauncherCategoryFile.of(sections).write(file);

        LauncherCategoryFile reparsed = LauncherCategoryFile.parse(file);
        assertEquals(Arrays.asList("games"), reparsed.sectionOrder());
        assertEquals("games", reparsed.categoryForPackage("com.mojang.minecraftpe"));
        assertEquals(0, LauncherCategoryFile.parse(
            new File(temporaryFolder.getRoot(), "absent.conf")).sections().size());
    }

    @Test public void ignoresCommentsAndBlankLines() throws Exception {
        LauncherCategoryFile file = parse(""
            + "# Managed by Termux Launcher.\n"
            + "; semicolons comment too\n"
            + "\n"
            + "   \n"
            + "[social]\n"
            + "   com.whatsapp   \n"
            + "# trailing note\n");

        assertEquals(Arrays.asList("com.whatsapp"), file.sections().get("social"));
        assertTrue(file.warnings().isEmpty());
    }

    @Test public void keepsHashInsidePackageLine() throws Exception {
        LauncherCategoryFile file = parse("[odd]\ncom.example.app#beta\n");

        assertEquals(Arrays.asList("com.example.app#beta"), file.sections().get("odd"));
        assertEquals("odd", file.categoryForPackage("com.example.app#beta"));
    }

    @Test public void skipsAndWarnsPackageBeforeAnySection() throws Exception {
        LauncherCategoryFile file = parse("com.orphan.app\n[social]\ncom.whatsapp\n");

        assertNull(file.categoryForPackage("com.orphan.app"));
        assertEquals(1, file.warnings().size());
        assertEquals("line 1: package before any [section]", file.warnings().get(0));
    }

    @Test public void keepsFirstDuplicatePackageAndWarns() throws Exception {
        LauncherCategoryFile file = parse(""
            + "[social]\n"
            + "com.whatsapp\n"
            + "[utilities]\n"
            + "com.whatsapp\n");

        assertEquals("social", file.categoryForPackage("com.whatsapp"));
        assertEquals(Arrays.asList("com.whatsapp"), file.sections().get("social"));
        assertTrue(file.sections().get("utilities").isEmpty());
        assertEquals(1, file.warnings().size());
        assertTrue(file.warnings().get(0).startsWith("line 4: duplicate package com.whatsapp"));
    }

    @Test public void mergesDuplicateSectionHeadersAndPreservesOrder() throws Exception {
        LauncherCategoryFile file = parse(""
            + "[social]\n"
            + "com.whatsapp\n"
            + "[utilities]\n"
            + "com.android.chrome\n"
            + "[social]\n"
            + "org.telegram.messenger\n");

        assertEquals(Arrays.asList("social", "utilities"), file.sectionOrder());
        assertEquals(Arrays.asList("com.whatsapp", "org.telegram.messenger"),
            file.sections().get("social"));
    }

    @Test public void keepsFreeFormSectionNamesVerbatim() throws Exception {
        LauncherCategoryFile file = parse(""
            + "[ Work Stuff ]\n"
            + "com.slack\n"
            + "[Café ☕ / 日本語]\n"
            + "com.coffee\n");

        assertEquals(Arrays.asList("Work Stuff", "Café ☕ / 日本語"), file.sectionOrder());
        assertEquals("Work Stuff", file.categoryForPackage("com.slack"));
        assertEquals("Café ☕ / 日本語", file.categoryForPackage("com.coffee"));
    }

    @Test public void looksUpPackagesCaseInsensitively() throws Exception {
        LauncherCategoryFile file = parse("[Social]\ncom.WhatsApp\n");

        assertEquals("Social", file.categoryForPackage("COM.whatsapp"));
        assertEquals("Social", file.categoryForPackage("com.WhatsApp"));
        assertNull(file.categoryForPackage(null));
        assertNull(file.categoryForPackage("com.absent"));
    }

    @Test public void emptyInputYieldsNothing() throws Exception {
        LauncherCategoryFile file = parse("");

        assertTrue(file.sections().isEmpty());
        assertTrue(file.sectionOrder().isEmpty());
        assertTrue(file.warnings().isEmpty());
        assertTrue(LauncherCategoryFile.empty().sections().isEmpty());
    }

    @Test public void malformedLinesNeverThrow() throws Exception {
        LauncherCategoryFile file = parse(""
            + "[unterminated\n"
            + "[]\n"
            + "=== junk ===\n"
            + "[social]\n"
            + "com.whatsapp\n"
            + "]stray[\n");

        assertEquals(Arrays.asList("social"), file.sectionOrder());
        assertEquals(Arrays.asList("com.whatsapp", "]stray["), file.sections().get("social"));
        assertEquals(3, file.warnings().size());
    }
}
