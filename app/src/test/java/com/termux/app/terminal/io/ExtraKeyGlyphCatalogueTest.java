package com.termux.app.terminal.io;

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
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * The shipped catalogue is a reviewed file, so the review is what this asserts: an unsorted,
 * duplicated or malformed row fails here rather than turning into a mis-grouped or missing cap.
 */
public class ExtraKeyGlyphCatalogueTest {

    @Test public void shippedCatalogueIsGroupedSortedUniqueAndWellFormed() throws Exception {
        Path path = Path.of("app/src/main/res/raw/extra_key_glyphs.csv");
        if (!Files.exists(path)) path = Path.of("src/main/res/raw/extra_key_glyphs.csv");
        assertTrue("shipped glyph catalogue is missing", Files.exists(path));
        List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
        assertFalse(lines.isEmpty());
        assertEquals(ExtraKeyGlyphCatalogue.SCHEMA_LINE, lines.get(0));

        Set<Integer> codePoints = new HashSet<>();
        List<String> categoriesSeen = new ArrayList<>();
        String currentCategory = null;
        int previousCodePoint = -1;
        int rows = 0;
        for (String raw : lines) {
            String line = raw.trim();
            if (line.isEmpty() || line.startsWith("#")) continue;
            rows++;
            String[] fields = line.split(",", -1);
            assertEquals("three commas per row: " + line, 4, fields.length);

            int codePoint = ExtraKeyGlyphCatalogue.parseCodePoint(fields[0]);
            assertTrue("malformed code point: " + line, codePoint > 0);
            assertTrue("duplicate code point: " + line, codePoints.add(codePoint));

            String name = fields[1];
            assertFalse("empty name: " + line, name.isEmpty());
            assertEquals("names are lower case: " + line, name.toLowerCase(Locale.ROOT), name);
            assertEquals("name is not padded: " + line, name.trim(), name);
            assertFalse("keywords are required: " + line, fields[2].trim().isEmpty());

            String category = fields[3];
            assertTrue("unknown category: " + line,
                ExtraKeyGlyphCatalogue.CATEGORIES.contains(category));
            if (!category.equals(currentCategory)) {
                assertFalse("category is split across the file: " + category,
                    categoriesSeen.contains(category));
                categoriesSeen.add(category);
                currentCategory = category;
                previousCodePoint = -1;
            }
            assertTrue("rows do not ascend inside " + category + ": " + line,
                codePoint > previousCodePoint);
            previousCodePoint = codePoint;
        }

        assertTrue("expected a real catalogue, found " + rows + " rows", rows >= 250);
        // The Nerd Font group lives in its own generated file, so the reviewed file carries every
        // other declared category, in order.
        List<String> reviewedCategories = new ArrayList<>(ExtraKeyGlyphCatalogue.CATEGORIES);
        reviewedCategories.remove(ExtraKeyGlyphCatalogue.CATEGORY_NERD_FONT);
        assertEquals("groups must follow the declared category order",
            reviewedCategories, categoriesSeen);

        // The parser must accept its own shipped file wholesale, and every declared category has to
        // survive it: an empty group is a category the picker would render as a bare header.
        ExtraKeyGlyphCatalogue catalogue;
        try (var reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            catalogue = ExtraKeyGlyphCatalogue.parse(reader);
        }
        assertEquals(rows, catalogue.size());
        for (String category : reviewedCategories) {
            assertFalse("empty category: " + category, catalogue.byCategory(category).isEmpty());
        }
    }

    /**
     * The Nerd Font file is generated from the bundled symbols face rather than reviewed, so what
     * is asserted here is that generation stayed inside the parser's contract — a row the parser
     * skips is a glyph the picker silently loses.
     */
    @Test public void generatedNerdFontCatalogueParsesWholeAndStaysInItsOwnCategory() throws Exception {
        Path path = Path.of("app/src/main/res/raw/nerd_font_glyphs.csv");
        if (!Files.exists(path)) path = Path.of("src/main/res/raw/nerd_font_glyphs.csv");
        assertTrue("generated Nerd Font catalogue is missing", Files.exists(path));
        List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
        assertEquals(ExtraKeyGlyphCatalogue.SCHEMA_LINE, lines.get(0));

        int rows = 0;
        for (String raw : lines) {
            String line = raw.trim();
            if (line.isEmpty() || line.startsWith("#")) continue;
            rows++;
            String[] fields = line.split(",", -1);
            assertEquals("three commas per row: " + line, 4, fields.length);
            assertEquals("generated rows are all Nerd Font: " + line,
                ExtraKeyGlyphCatalogue.CATEGORY_NERD_FONT, fields[3]);
        }
        assertTrue("expected the whole shipped face, found " + rows + " rows", rows >= 10_000);

        ExtraKeyGlyphCatalogue catalogue;
        try (var reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            catalogue = ExtraKeyGlyphCatalogue.parse(reader);
        }
        assertEquals("every generated row must survive the parser", rows, catalogue.size());
        // nf-md-keyboard_outline, the glyph the KEYBOARD cap now draws.
        assertNotNull(catalogue.byCodePoint(0xF097B));
    }

    @Test public void parserSkipsMalformedRowsWithoutDroppingValidNeighbours() throws Exception {
        ExtraKeyGlyphCatalogue catalogue = ExtraKeyGlyphCatalogue.parse(new StringReader(
            "# schema=1\n2190,leftwards arrow,left,arrows\n"
                + "ZZZZ,not hex,bad,arrows\n"
                + "2191,too,many,fields,arrows\n"
                + "2192,missing category\n"
                + "2193,downwards arrow,down,not_a_category\n"
                + "0009,tab is a control character,tab,technical\n"
                + "2190,duplicate of the first row,left,arrows\n"
                + "2328,keyboard,keys,technical\n"));

        assertEquals(2, catalogue.size());
        assertNotNull(catalogue.byCodePoint(0x2190));
        assertNotNull(catalogue.byCodePoint(0x2328));
        assertEquals("leftwards arrow", catalogue.byCodePoint(0x2190).name);
        assertNull(catalogue.byCodePoint(0x2193));
        assertEquals("⌨", catalogue.byCodePoint(0x2328).text);
    }

    @Test public void unsupportedOrMissingSchemaRejectsTheWholeFile() throws Exception {
        assertTrue(ExtraKeyGlyphCatalogue.parse(new StringReader(
            "# schema=2\n2190,leftwards arrow,left,arrows\n")).isEmpty());
        assertTrue(ExtraKeyGlyphCatalogue.parse(new StringReader(
            "2190,leftwards arrow,left,arrows\n")).isEmpty());
        assertTrue(ExtraKeyGlyphCatalogue.parse(new StringReader(
            "2190,leftwards arrow,left,arrows\n# schema=1\n2191,upwards arrow,up,arrows\n"))
            .isEmpty());
        assertEquals(1, ExtraKeyGlyphCatalogue.parse(new StringReader(
            "# a comment\n\n# schema=1\n2190,leftwards arrow,left,arrows\n")).size());
    }
}
