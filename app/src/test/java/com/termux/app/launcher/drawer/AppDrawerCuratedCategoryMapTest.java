package com.termux.app.launcher.drawer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class AppDrawerCuratedCategoryMapTest {

    @Test public void shippedMapHasExactSchemaSortedUniqueValidTaxonomyRows() throws Exception {
        Path path = Path.of("app/src/main/res/raw/app_drawer_category_overrides.csv");
        if (!Files.exists(path)) path = Path.of("src/main/res/raw/app_drawer_category_overrides.csv");
        assertTrue("shipped curated resource is missing", Files.exists(path));
        List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
        assertFalse(lines.isEmpty());
        assertEquals(AppDrawerCuratedCategoryMap.SCHEMA_LINE, lines.get(0));

        String previous = null;
        Set<String> packages = new HashSet<>();
        int rows = 0;
        for (String raw : lines) {
            String line = raw.trim();
            if (line.isEmpty() || line.startsWith("#")) continue;
            rows++;
            assertFalse(line.contains("\""));
            assertFalse(line.contains("*"));
            String[] fields = line.split(",", -1);
            assertEquals("two commas per row: " + line, 3, fields.length);
            String packageName = fields[0];
            assertEquals(packageName.toLowerCase(Locale.US), packageName);
            assertTrue(AppDrawerCuratedCategoryMap.isValidPackage(packageName));
            assertTrue("duplicate package: " + packageName, packages.add(packageName));
            if (previous != null) assertTrue("rows not lexical", previous.compareTo(packageName) < 0);
            previous = packageName;
            AppDrawerCategory category = AppDrawerCategory.fromSlug(fields[1]);
            assertNotNull("unknown category: " + fields[1], category);
            assertFalse("synthetic category in curated map", category.synthetic);
            assertTrue("unknown mode: " + fields[2],
                AppDrawerCuratedCategoryMap.MODE_FILL.equals(fields[2])
                    || AppDrawerCuratedCategoryMap.MODE_FORCE.equals(fields[2]));
        }
        assertTrue("expected a real curated map, found " + rows + " rows", rows >= 40);
        // The parser must accept its own shipped file wholesale.
        try (var reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            assertEquals(rows, AppDrawerCuratedCategoryMap.parse(reader).asMap().size());
        }
    }

    @Test public void runtimeParserSkipsMalformedRowsWithoutDroppingValidNeighbors() throws Exception {
        AppDrawerCuratedCategoryMap map = AppDrawerCuratedCategoryMap.parse(new StringReader(
            "# schema=2\ncom.example.alpha,finance,fill\nnot-a-package,health,fill\n"
                + "com.example.bad,suggestions,fill\ncom.example.too,many,fields,extra\n"
                + "com.example.nomode,travel\ncom.example.badmode,travel,sometimes\n"
                + "com.example.omega,travel,force\n"));
        assertEquals(AppDrawerCategory.FINANCE, map.fillCategoryForPackage("COM.EXAMPLE.ALPHA"));
        assertNull(map.forcedCategoryForPackage("com.example.alpha"));
        assertEquals(AppDrawerCategory.TRAVEL, map.forcedCategoryForPackage("com.example.omega"));
        assertNull(map.fillCategoryForPackage("com.example.omega"));
        assertEquals(2, map.asMap().size());
    }

    @Test public void modeIsStoredPerEntry() throws Exception {
        AppDrawerCuratedCategoryMap map = AppDrawerCuratedCategoryMap.parse(new StringReader(
            "# schema=2\ncom.example.fill,social,fill\ncom.example.force,games,force\n"));
        AppDrawerCuratedCategoryMap.Entry fill = map.entryForPackage("com.example.fill");
        AppDrawerCuratedCategoryMap.Entry force = map.entryForPackage("com.example.force");
        assertNotNull(fill);
        assertNotNull(force);
        assertFalse(fill.force);
        assertTrue(force.force);
        assertEquals(AppDrawerCategory.SOCIAL, fill.category);
        assertEquals(AppDrawerCategory.GAMES, force.category);
    }

    @Test public void unsupportedOrMissingSchemaRejectsTheWholeFile() throws Exception {
        // Old schema versions are refused rather than misread.
        assertTrue(AppDrawerCuratedCategoryMap.parse(new StringReader(
            "# schema=1\ncom.example.alpha,finance,fill\n")).asMap().isEmpty());
        // No directive at all.
        assertTrue(AppDrawerCuratedCategoryMap.parse(new StringReader(
            "com.example.alpha,finance,fill\n")).asMap().isEmpty());
        // A data row before the directive means the directive cannot be trusted either.
        assertTrue(AppDrawerCuratedCategoryMap.parse(new StringReader(
            "com.example.alpha,finance,fill\n# schema=2\ncom.example.beta,travel,fill\n"))
            .asMap().isEmpty());
        // Comments and blank lines before the directive stay legal.
        assertEquals(1, AppDrawerCuratedCategoryMap.parse(new StringReader(
            "# a comment\n\n# schema=2\ncom.example.alpha,finance,fill\n")).asMap().size());
        // An empty-but-versioned file is a valid empty map, not a rejection.
        assertTrue(AppDrawerCuratedCategoryMap.parse(new StringReader("# schema=2\n"))
            .asMap().isEmpty());
    }
}
