package com.termux.app.launcher.drawer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
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
        for (String raw : lines) {
            String line = raw.trim();
            if (line.isEmpty() || line.startsWith("#")) continue;
            assertFalse(line.contains("\""));
            assertFalse(line.contains("*"));
            String[] fields = line.split(",", -1);
            assertEquals("one comma per row: " + line, 2, fields.length);
            String packageName = fields[0];
            assertEquals(packageName.toLowerCase(Locale.US), packageName);
            assertTrue(AppDrawerCuratedCategoryMap.isValidPackage(packageName));
            assertTrue("duplicate package: " + packageName, packages.add(packageName));
            if (previous != null) assertTrue("rows not lexical", previous.compareTo(packageName) < 0);
            previous = packageName;
            AppDrawerCategory category = AppDrawerCategory.fromSlug(fields[1]);
            assertNotNull("unknown category: " + fields[1], category);
            assertFalse("synthetic category in curated map", category.synthetic);
        }
    }

    @Test public void runtimeParserSkipsMalformedRowsWithoutDroppingValidNeighbors() throws Exception {
        AppDrawerCuratedCategoryMap map = AppDrawerCuratedCategoryMap.parse(new StringReader(
            "# schema=1\ncom.example.alpha,finance\nnot-a-package,health\n"
                + "com.example.bad,suggestions\ncom.example.too,many,fields\n"
                + "com.example.omega,travel\n"));
        assertEquals(AppDrawerCategory.FINANCE, map.categoryForPackage("COM.EXAMPLE.ALPHA"));
        assertEquals(AppDrawerCategory.TRAVEL, map.categoryForPackage("com.example.omega"));
        assertEquals(2, map.asMap().size());
    }
}
