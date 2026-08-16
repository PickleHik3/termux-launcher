package com.termux.app.terminal.io;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

/** Search and {@code hasGlyph} filtering, the two things standing between the file and a cap. */
public class ExtraKeyGlyphSearchTest {

    private static final String FILE = "# schema=1\n"
        + "2190,leftwards arrow,left west back arrow,arrows\n"
        + "2192,rightwards arrow,right east forward arrow,arrows\n"
        + "2588,full block,block solid full bar,blocks\n"
        + "E0B0,powerline right filled separator,separator right solid powerline nerd,powerline\n"
        + "2328,keyboard,keyboard input keys,technical\n"
        + "232B,erase to the left,backspace delete,technical\n"
        + "2713,check mark,check tick ok pass success,terminal_marks\n";

    private static ExtraKeyGlyphCatalogue catalogue() throws Exception {
        return ExtraKeyGlyphCatalogue.parse(new StringReader(FILE));
    }

    @Test public void nameMatchesAreFoundAndOutrankKeywordMatches() throws Exception {
        List<ExtraKeyGlyphCatalogue.Glyph> hits = catalogue().search("keyboard");

        // "keyboard" is the name of one row and a keyword of that same row only, so the name hit
        // has to lead rather than tie with anything else that merely mentions keys.
        assertFalse(hits.isEmpty());
        assertEquals(0x2328, hits.get(0).codePoint);
    }

    @Test public void keywordMatchesFindGlyphsWhoseNameNeverSaysIt() throws Exception {
        assertEquals(codePoints(catalogue().search("backspace")), List.of(0x232B));
        assertEquals(codePoints(catalogue().search("tick")), List.of(0x2713));
        // "nerd" only ever appears in the keyword column.
        assertEquals(codePoints(catalogue().search("nerd")), List.of(0xE0B0));
    }

    @Test public void prefixesAndPastedGlyphsAndCodePointsAllResolve() throws Exception {
        assertTrue(codePoints(catalogue().search("arrow")).containsAll(List.of(0x2190, 0x2192)));
        assertEquals(codePoints(catalogue().search("✓")), List.of(0x2713));
        assertEquals(codePoints(catalogue().search("2588")), List.of(0x2588));
        assertEquals(codePoints(catalogue().search("U+2588")), List.of(0x2588));
        assertTrue(catalogue().search("nothing matches this").isEmpty());
        // A blank query is the browse case, not a search that failed.
        assertEquals(7, catalogue().search("  ").size());
    }

    @Test public void glyphsTheDeviceCannotDrawNeverReachThePicker() throws Exception {
        // Stands in for Paint.hasGlyph on a device whose UI font carries no private use area, which
        // is every device without a Nerd Font: the Powerline rows must disappear entirely rather
        // than be offered as tofu.
        ExtraKeyGlyphCatalogue drawable = catalogue().filter(
            glyph -> glyph.codePoint < 0xE000 || glyph.codePoint > 0xF8FF);

        assertEquals(6, drawable.size());
        assertTrue(drawable.byCategory(ExtraKeyGlyphCatalogue.CATEGORY_POWERLINE).isEmpty());
        assertTrue(drawable.search("nerd").isEmpty());
        assertTrue(drawable.search("separator").isEmpty());
        assertFalse(drawable.byCategory(ExtraKeyGlyphCatalogue.CATEGORY_TECHNICAL).isEmpty());
        // The unfiltered catalogue is untouched, so one device's font never edits the shipped file.
        assertEquals(7, catalogue().size());
    }

    private static List<Integer> codePoints(@androidx.annotation.NonNull
                                            List<ExtraKeyGlyphCatalogue.Glyph> glyphs) {
        List<Integer> codePoints = new ArrayList<>();
        for (ExtraKeyGlyphCatalogue.Glyph glyph : glyphs) codePoints.add(glyph.codePoint);
        return codePoints;
    }
}
