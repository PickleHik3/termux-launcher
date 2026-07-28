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
    public void parsesKittyLigaturePoliciesAndDefaultsToNever() {
        TerminalFontConfig.Result defaultResult = TerminalFontConfig.parse("", true);
        assertEquals(TerminalFontConfig.LigaturePolicy.NEVER, defaultResult.ligaturePolicy);

        TerminalFontConfig.Result result = TerminalFontConfig.parse(
            "disable_ligatures cursor\ndisable_ligatures ALWAYS\n", true);
        assertTrue(result.errors.toString(), result.errors.isEmpty());
        assertEquals(TerminalFontConfig.LigaturePolicy.ALWAYS, result.ligaturePolicy);

        TerminalFontConfig.Result invalid = TerminalFontConfig.parse(
            "disable_ligatures sometimes\n", true);
        assertEquals(TerminalFontConfig.LigaturePolicy.NEVER, invalid.ligaturePolicy);
        assertEquals(1, invalid.errors.size());
    }

    @Test
    public void translatesFaceScopedOpenTypeFeaturesForAndroidPaint() {
        TerminalFontConfig.Result result = TerminalFontConfig.parse(
            "font_features regular +zero,-liga cv01=2\n"
                + "font_features bold +calt\n"
                + "font_features symbols ss01=3\n"
                + "font_features bold none\n", true);

        assertTrue(result.errors.toString(), result.errors.isEmpty());
        assertEquals("'zero' 1, 'liga' 0, 'cv01' 2",
            result.features(TerminalFontConfig.FontTarget.REGULAR));
        assertEquals("'ss01' 3", result.features(TerminalFontConfig.FontTarget.SYMBOLS));
        assertNull(result.features(TerminalFontConfig.FontTarget.BOLD));
    }

    @Test
    public void rejectsMalformedOpenTypeFeaturesWithoutDiscardingOtherTargets() {
        TerminalFontConfig.Result result = TerminalFontConfig.parse(
            "font_features regular +zero\n"
                + "font_features unknown +calt\n"
                + "font_features bold +abc\n"
                + "font_features italic +calt=2\n"
                + "font_features symbols cv01=99999\n", true);

        assertEquals(4, result.errors.size());
        assertEquals("'zero' 1", result.features(TerminalFontConfig.FontTarget.REGULAR));
        assertNull(result.features(TerminalFontConfig.FontTarget.BOLD));
        assertNull(result.features(TerminalFontConfig.FontTarget.ITALIC));
        assertNull(result.features(TerminalFontConfig.FontTarget.SYMBOLS));
    }

    @Test
    public void translatesAndScopesVariableFontAxes() {
        TerminalFontConfig.Result result = TerminalFontConfig.parse(
            "font_variations regular wght=425 wdth=92.5\n"
                + "font_variations italic slnt=-8.25\n"
                + "font_variations symbols opsz=14,wght=500\n"
                + "font_variations italic none\n", true);

        assertTrue(result.errors.toString(), result.errors.isEmpty());
        assertEquals("'wght' 425, 'wdth' 92.5",
            result.variations(TerminalFontConfig.FontTarget.REGULAR));
        assertEquals("'opsz' 14, 'wght' 500",
            result.variations(TerminalFontConfig.FontTarget.SYMBOLS));
        assertNull(result.variations(TerminalFontConfig.FontTarget.ITALIC));
    }

    @Test
    public void rejectsMalformedOrUnboundedVariableAxes() {
        TerminalFontConfig.Result result = TerminalFontConfig.parse(
            "font_variations regular wght=420\n"
                + "font_variations bold weight=500\n"
                + "font_variations italic slnt=NaN\n"
                + "font_variations bold_italic wdth=2000000\n"
                + "font_variations symbols opsz\n", true);

        assertEquals(4, result.errors.size());
        assertEquals("'wght' 420", result.variations(TerminalFontConfig.FontTarget.REGULAR));
        assertNull(result.variations(TerminalFontConfig.FontTarget.BOLD));
        assertNull(result.variations(TerminalFontConfig.FontTarget.ITALIC));
        assertNull(result.variations(TerminalFontConfig.FontTarget.BOLD_ITALIC));
        assertNull(result.variations(TerminalFontConfig.FontTarget.SYMBOLS));
    }

    @Test
    public void parsesBoundedKittyStyleFontMetrics() {
        TerminalFontConfig.Result result = TerminalFontConfig.parse(
            "modify_font cell_width 90%\n"
                + "modify_font cell_height 4px\n"
                + "modify_font baseline -2\n"
                + "modify_font underline_position 1px\n"
                + "modify_font underline_thickness 150%\n"
                + "modify_font strikethrough_position -1px\n"
                + "modify_font strikethrough_thickness 2px\n"
                + "modify_font baseline none\n", true);

        assertTrue(result.errors.toString(), result.errors.isEmpty());
        assertEquals(90d, result.metric(TerminalFontConfig.Metric.CELL_WIDTH).value, 0d);
        assertEquals(TerminalFontConfig.MetricUnit.PERCENT,
            result.metric(TerminalFontConfig.Metric.CELL_WIDTH).unit);
        assertEquals(4d, result.metric(TerminalFontConfig.Metric.CELL_HEIGHT).value, 0d);
        assertNull(result.metric(TerminalFontConfig.Metric.BASELINE));
        assertEquals(150d,
            result.metric(TerminalFontConfig.Metric.UNDERLINE_THICKNESS).value, 0d);
        assertEquals(2d,
            result.metric(TerminalFontConfig.Metric.STRIKETHROUGH_THICKNESS).value, 0d);
    }

    @Test
    public void rejectsUnknownOrUnboundedFontMetrics() {
        TerminalFontConfig.Result result = TerminalFontConfig.parse(
            "modify_font cell_width 100px\n"
                + "modify_font unknown 1px\n"
                + "modify_font cell_height 501%\n"
                + "modify_font baseline NaN\n", true);

        assertEquals(3, result.errors.size());
        assertEquals(100d, result.metric(TerminalFontConfig.Metric.CELL_WIDTH).value, 0d);
        assertNull(result.metric(TerminalFontConfig.Metric.CELL_HEIGHT));
        assertNull(result.metric(TerminalFontConfig.Metric.BASELINE));
    }

    @Test
    public void parsesRepeatableSymbolMapsAndCommaSeparatedRanges() {
        TerminalFontConfig.Result result = TerminalFontConfig.parse(
            "symbol_map U+E000-U+F8FF,U+F0001 path=~/.termux/fonts/nerd.ttf\n"
                + "symbol_map U+E0B0-U+E0D7 'family=Symbols Nerd Font Mono'\n", true);

        assertTrue(result.errors.toString(), result.errors.isEmpty());
        assertEquals(2, result.symbolMaps.size());
        assertEquals(2, result.symbolMaps.get(0).ranges.size());
        assertEquals(0xE000, result.symbolMaps.get(0).ranges.get(0).first);
        assertEquals(0xF8FF, result.symbolMaps.get(0).ranges.get(0).last);
        assertEquals(0xF0001, result.symbolMaps.get(0).ranges.get(1).first);
        assertEquals(TerminalFontConfig.SourceType.PATH, result.symbolMaps.get(0).font.type);
        assertEquals("Symbols Nerd Font Mono", result.symbolMaps.get(1).font.value);
    }

    @Test
    public void rejectsInvalidSymbolRangesWithoutDiscardingValidMaps() {
        TerminalFontConfig.Result result = TerminalFontConfig.parse(
            "symbol_map U+E000-U+E0FF family=valid\n"
                + "symbol_map E100 family=bad\n"
                + "symbol_map U+E200-U+E100 family=bad\n"
                + "symbol_map U+D800-U+DFFF family=bad\n"
                + "symbol_map U+110000 family=bad\n"
                + "symbol_map U+E300 relative.ttf\n", true);

        assertEquals(5, result.errors.size());
        assertEquals(1, result.symbolMaps.size());
        assertEquals(0xE000, result.symbolMaps.get(0).ranges.get(0).first);
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
