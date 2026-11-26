package com.termux.app.wall.pageswitch;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import com.termux.app.terminal.ProcessGlyphs;

import org.junit.Test;

public class PageMarkGlyphTest {

    @Test public void aKnownProgramOnTheAlternateScreenHasItsGlyph() {
        assertEquals(ProcessGlyphs.forProcess("vim"), PageMarkGlyph.forProgram(true, "vim"));
    }

    @Test public void theMainScreenHasNoGlyphWhateverTheProgram() {
        assertNull(PageMarkGlyph.forProgram(false, "vim"));
    }

    @Test public void anUnknownProgramHasNoGlyphNotTheDefault() {
        assertNull(PageMarkGlyph.forProgram(true, "no-such-program"));
    }

    @Test public void noProcessHasNoGlyph() {
        assertNull(PageMarkGlyph.forProgram(true, null));
        assertNull(PageMarkGlyph.forProgram(true, "  "));
    }
}
