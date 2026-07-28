package com.termux.app.terminal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class TerminalFontConfigTest {

    @Test
    public void parsesFourPathAndFamilyFaces() {
        TerminalFontConfig.Result result = TerminalFontConfig.parse(
            "font_family path=~/.termux/font.ttf\n"
                + "bold_font path=/data/local/bold.ttf\n"
                + "italic_font family=\"Roboto Mono Italic\"\n"
                + "bold_italic_font 'family=Roboto Mono Bold Italic' # comment\n", true);

        assertTrue(result.errors.toString(), result.errors.isEmpty());
        assertEquals(TerminalFontConfig.SourceType.PATH,
            result.face(TerminalFontConfig.Face.REGULAR).type);
        assertEquals("~/.termux/font.ttf", result.face(TerminalFontConfig.Face.REGULAR).value);
        assertEquals("/data/local/bold.ttf", result.face(TerminalFontConfig.Face.BOLD).value);
        assertEquals(TerminalFontConfig.SourceType.FAMILY,
            result.face(TerminalFontConfig.Face.ITALIC).type);
        assertEquals("Roboto Mono Italic", result.face(TerminalFontConfig.Face.ITALIC).value);
        assertEquals("Roboto Mono Bold Italic",
            result.face(TerminalFontConfig.Face.BOLD_ITALIC).value);
    }

    @Test
    public void duplicateFaceUsesLastValueLikePropertiesReload() {
        TerminalFontConfig.Result result = TerminalFontConfig.parse(
            "bold_font family=first\nbold_font family=second\n", true);
        assertTrue(result.errors.isEmpty());
        assertEquals("second", result.face(TerminalFontConfig.Face.BOLD).value);
    }

    @Test
    public void invalidLinesDoNotDiscardValidFaces() {
        TerminalFontConfig.Result result = TerminalFontConfig.parse(
            "font_family relative.ttf\n"
                + "bold_font path=relative.ttf\n"
                + "unknown family=nope\n"
                + "italic_font path=/valid.ttf\n"
                + "bold_italic_font family=\"unterminated\n", true);

        assertEquals(4, result.errors.size());
        assertNull(result.face(TerminalFontConfig.Face.REGULAR));
        assertNull(result.face(TerminalFontConfig.Face.BOLD));
        assertEquals("/valid.ttf", result.face(TerminalFontConfig.Face.ITALIC).value);
    }

    @Test
    public void missingFileIsAnEmptyBackwardCompatibleConfig() throws Exception {
        java.io.File missing = new java.io.File(
            System.getProperty("java.io.tmpdir"), "missing-font-config-" + System.nanoTime());
        TerminalFontConfig.Result result = TerminalFontConfig.load(missing);
        assertFalse(result.filePresent);
        assertTrue(result.faces.isEmpty());
        assertTrue(result.errors.isEmpty());
    }
}
