package com.termux.app.terminal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** The chip glyph table: the named programs get their own glyph, and every glyph is in the font. */
public class ProcessGlyphsTest {

    private static String glyph(int codePoint) {
        return new String(Character.toChars(codePoint));
    }

    @Test
    public void codingAgentsGetTheirOwnGlyphs() {
        assertEquals(glyph(0xEC82), ProcessGlyphs.forProcess("claude"));
        // npm-installed Claude Code unwraps to its package name.
        assertEquals(glyph(0xEC82), ProcessGlyphs.forProcess("claude-code"));
        assertEquals(glyph(0xEC81), ProcessGlyphs.forProcess("codex"));
        assertEquals(glyph(0xE7F0), ProcessGlyphs.forProcess("gemini"));
        assertEquals(glyph(0xEC1E), ProcessGlyphs.forProcess("copilot"));
        assertEquals(glyph(0xF0CC6), ProcessGlyphs.forProcess("herdr"));
    }

    @Test
    public void multiplexersAndEditorsKeepTheirGlyphs() {
        assertEquals(glyph(0xEBC8), ProcessGlyphs.forProcess("tmux"));
        assertEquals(glyph(0xF0BCC), ProcessGlyphs.forProcess("zellij"));
        assertEquals(glyph(0xE6AE), ProcessGlyphs.forProcess("nvim"));
        assertEquals(glyph(0xE7C5), ProcessGlyphs.forProcess("vim"));
        assertEquals(glyph(0xF023A), ProcessGlyphs.forProcess("fish"));
        assertEquals(glyph(0xE702), ProcessGlyphs.forProcess("lazygit"));
    }

    @Test
    public void unknownBlankAndNullFallBackToTheTerminal() {
        assertEquals(ProcessGlyphs.DEFAULT, ProcessGlyphs.forProcess(null));
        assertEquals(ProcessGlyphs.DEFAULT, ProcessGlyphs.forProcess(""));
        assertEquals(ProcessGlyphs.DEFAULT, ProcessGlyphs.forProcess("   "));
        assertEquals(ProcessGlyphs.DEFAULT, ProcessGlyphs.forProcess("some-unknown-tool"));
        assertEquals(glyph(0xE795), ProcessGlyphs.DEFAULT);
        assertFalse(ProcessGlyphs.knows("some-unknown-tool"));
        assertTrue(ProcessGlyphs.knows("codex"));
    }

    @Test
    public void lookupIgnoresCaseAndSurroundingSpace() {
        assertEquals(glyph(0xEC82), ProcessGlyphs.forProcess("Claude"));
        assertEquals(glyph(0xEC82), ProcessGlyphs.forProcess(" claude "));
    }

    @Test
    public void everyTableEntryNamesADistinctFallbackFreeGlyph() {
        for (Map.Entry<String, Integer> entry : ProcessGlyphs.table().entrySet()) {
            assertNotEquals("entry " + entry.getKey() + " is only the default",
                ProcessGlyphs.DEFAULT_CODE_POINT, (int) entry.getValue());
            assertEquals("entry " + entry.getKey() + " is not lowercase",
                entry.getKey().toLowerCase(Locale.ROOT), entry.getKey());
        }
    }

    /**
     * The bundled Nerd Font catalogue is generated from the shipped {@code SymbolsNerdFontMono.ttf}
     * by its glyph names, so a code point missing from it would render as a box on a phone with no
     * other symbol face.
     */
    @Test
    public void everyGlyphIsInTheBundledNerdFont() throws IOException {
        Set<Integer> shipped = catalogueCodePoints();
        assertTrue("catalogue not found or empty", shipped.size() > 1000);
        assertTrue(shipped.contains(ProcessGlyphs.DEFAULT_CODE_POINT));
        for (Map.Entry<String, Integer> entry : ProcessGlyphs.table().entrySet()) {
            assertTrue(String.format(Locale.ROOT, "%s -> U+%X is not in SymbolsNerdFontMono",
                entry.getKey(), entry.getValue()), shipped.contains(entry.getValue()));
        }
    }

    private static Set<Integer> catalogueCodePoints() throws IOException {
        File csv = new File("src/main/res/raw/nerd_font_glyphs.csv");
        if (!csv.exists()) csv = new File("app/src/main/res/raw/nerd_font_glyphs.csv");
        Set<Integer> points = new HashSet<>();
        try (BufferedReader reader = new BufferedReader(new FileReader(csv))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isEmpty() || line.startsWith("#")) continue;
                int comma = line.indexOf(',');
                if (comma <= 0) continue;
                try {
                    points.add(Integer.parseInt(line.substring(0, comma), 16));
                } catch (NumberFormatException ignored) {
                    // header or malformed row
                }
            }
        }
        return points;
    }
}
