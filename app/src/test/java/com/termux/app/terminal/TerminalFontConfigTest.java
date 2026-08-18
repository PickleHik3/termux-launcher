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
    public void namedSymbolMapsResolveTheirOwnFeaturesAndVariations() {
        TerminalFontConfig.Result result = TerminalFontConfig.parse(
            "symbol_map name=nerd U+E000-U+E0FF path=/nerd.ttf\n"
                + "symbol_map U+F0000-U+F0FFF path=/other.ttf\n"
                + "symbol_map U+2500-U+257F name=box family=Box\n"
                + "font_features nerd +liga\n"
                + "font_variations nerd wght=600\n"
                + "font_features symbols ss01=1\n", true);

        assertTrue(result.errors.toString(), result.errors.isEmpty());
        assertEquals(3, result.symbolMaps.size());
        assertEquals("nerd", result.symbolMaps.get(0).name);
        assertEquals("'liga' 1", result.symbolMaps.get(0).features);
        assertEquals("'wght' 600", result.symbolMaps.get(0).variations);
        assertNull(result.symbolMaps.get(1).name);
        assertEquals("'ss01' 1", result.symbolMaps.get(1).features);
        assertNull(result.symbolMaps.get(1).variations);
        // A named map without its own settings still inherits the shared symbols target.
        assertEquals("box", result.symbolMaps.get(2).name);
        assertEquals("'ss01' 1", result.symbolMaps.get(2).features);
        assertEquals("'liga' 1", result.namedFeatures("NERD"));
        assertEquals("'wght' 600", result.namedVariations("nerd"));
    }

    @Test
    public void twoSymbolMapsMayShareOneName() {
        TerminalFontConfig.Result result = TerminalFontConfig.parse(
            "symbol_map name=nerd U+E000 family=Nerd\n"
                + "symbol_map name=nerd U+E100 family=Nerd\n"
                + "font_features nerd +calt\n", true);

        assertTrue(result.errors.toString(), result.errors.isEmpty());
        assertEquals(2, result.symbolMaps.size());
        assertEquals("'calt' 1", result.symbolMaps.get(0).features);
        assertEquals("'calt' 1", result.symbolMaps.get(1).features);
    }

    @Test
    public void rejectsReservedAndMalformedSymbolMapNames() {
        TerminalFontConfig.Result result = TerminalFontConfig.parse(
            "symbol_map name=bold U+E000 family=x\n"
                + "symbol_map name=symbols U+E100 family=x\n"
                + "symbol_map name=bad! U+E200 family=x\n"
                + "symbol_map name= U+E300 family=x\n"
                + "symbol_map name=a name=b U+E400 family=x\n"
                + "symbol_map name=ok U+E500 family=x\n", true);

        assertEquals(result.errors.toString(), 5, result.errors.size());
        assertEquals("line 1: symbol map name 'bold' is a reserved font target",
            result.errors.get(0));
        assertEquals("line 5: symbol_map accepts one name= value", result.errors.get(4));
        assertEquals(1, result.symbolMaps.size());
        assertEquals("ok", result.symbolMaps.get(0).name);
    }

    @Test
    public void rejectsFeatureAndVariationTargetsThatNameNoSymbolMap() {
        TerminalFontConfig.Result result = TerminalFontConfig.parse(
            "symbol_map name=nerd U+E000 family=Nerd\n"
                + "font_features nerd +liga\n"
                + "font_features ghost +liga\n"
                + "font_variations ghost wght=500\n"
                + "font_features 'bad name' +liga\n", true);

        assertEquals(result.errors.toString(), 3, result.errors.size());
        assertEquals("line 3: font_features names undeclared symbol map 'ghost'",
            result.errors.get(1));
        assertEquals("line 4: font_variations names undeclared symbol map 'ghost'",
            result.errors.get(2));
        assertNull(result.namedFeatures("ghost"));
        assertNull(result.namedVariations("ghost"));
        assertEquals("'liga' 1", result.symbolMaps.get(0).features);
    }

    @Test
    public void parsesOrderedFallbackChainAndBoundsItsLength() {
        StringBuilder config = new StringBuilder("fallback_font family=\"Noto Sans Symbols 2\"\n");
        for (int i = 1; i <= 8; i++) config.append("fallback_font path=/f").append(i)
            .append(".ttf\n");

        TerminalFontConfig.Result result = TerminalFontConfig.parse(config.toString(), true);

        assertEquals(result.errors.toString(), 1, result.errors.size());
        assertEquals("line 9: fallback_font count exceeds 8", result.errors.get(0));
        assertEquals(8, result.fallbackFonts.size());
        assertEquals(TerminalFontConfig.SourceType.FAMILY, result.fallbackFonts.get(0).type);
        assertEquals("Noto Sans Symbols 2", result.fallbackFonts.get(0).value);
        assertEquals("/f1.ttf", result.fallbackFonts.get(1).value);
        assertEquals("/f7.ttf", result.fallbackFonts.get(7).value);
    }

    @Test
    public void rejectsMalformedFallbackFontsWithoutDiscardingValidOnes() {
        TerminalFontConfig.Result result = TerminalFontConfig.parse(
            "fallback_font path=/good.ttf\n"
                + "fallback_font relative.ttf\n"
                + "fallback_font\n"
                + "fallback_font path=/a.ttf path=/b.ttf\n", true);

        assertEquals(result.errors.toString(), 3, result.errors.size());
        assertEquals(1, result.fallbackFonts.size());
        assertEquals("/good.ttf", result.fallbackFonts.get(0).value);
    }

    @Test
    public void parsesBoxDrawingAndPowerlineDirectivesWithKittyDefaults() {
        TerminalFontConfig.Result defaults = TerminalFontConfig.parse("", true);
        assertEquals(TerminalFontConfig.BoxDrawingMode.SYNTHESIZE, defaults.boxDrawing);
        assertEquals(TerminalFontConfig.PowerlineMode.SYNTHESIZE, defaults.powerlineSymbols);
        assertEquals(0.001d, defaults.boxDrawingScale.thin, 0d);
        assertEquals(1d, defaults.boxDrawingScale.light, 0d);
        assertEquals(1.5d, defaults.boxDrawingScale.heavy, 0d);
        assertEquals(2d, defaults.boxDrawingScale.veryHeavy, 0d);

        TerminalFontConfig.Result result = TerminalFontConfig.parse(
            "box_drawing FONT\n"
                + "box_drawing_scale 0.5,1,2,3\n"
                + "powerline_symbols synthesize\n", true);
        assertTrue(result.errors.toString(), result.errors.isEmpty());
        assertEquals(TerminalFontConfig.BoxDrawingMode.FONT, result.boxDrawing);
        assertEquals(TerminalFontConfig.PowerlineMode.SYNTHESIZE, result.powerlineSymbols);
        assertEquals(0.5d, result.boxDrawingScale.thin, 0d);
        assertEquals(3d, result.boxDrawingScale.veryHeavy, 0d);

        TerminalFontConfig.Result spaced = TerminalFontConfig.parse(
            "box_drawing_scale 0.001, 1 1.5,2\n", true);
        assertTrue(spaced.errors.toString(), spaced.errors.isEmpty());
        assertEquals(1.5d, spaced.boxDrawingScale.heavy, 0d);
    }

    @Test
    public void rejectsMalformedBoxDrawingAndPowerlineValues() {
        TerminalFontConfig.Result result = TerminalFontConfig.parse(
            "box_drawing_scale 0.5,1,2,4\n"
                + "box_drawing outline\n"
                + "box_drawing\n"
                + "box_drawing_scale 1,2,3\n"
                + "box_drawing_scale 1,2,3,4,5\n"
                + "box_drawing_scale 0,1,2,3\n"
                + "box_drawing_scale 1,2,3,9\n"
                + "box_drawing_scale 1,2,3,x\n"
                + "powerline_symbols draw\n", true);

        assertEquals(result.errors.toString(), 8, result.errors.size());
        assertEquals("line 2: box_drawing must be synthesize or font", result.errors.get(0));
        assertEquals("line 4: expected box_drawing_scale with 4 comma or space separated values",
            result.errors.get(2));
        assertEquals("line 6: box_drawing_scale values must be greater than 0 and at most 8",
            result.errors.get(4));
        assertEquals("line 9: powerline_symbols must be font or synthesize",
            result.errors.get(7));
        assertEquals(TerminalFontConfig.BoxDrawingMode.SYNTHESIZE, result.boxDrawing);
        assertEquals(TerminalFontConfig.PowerlineMode.SYNTHESIZE, result.powerlineSymbols);
        // The one valid line stays active.
        assertEquals(4d, result.boxDrawingScale.veryHeavy, 0d);
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

    @Test
    public void narrowSymbolsDefaultsToOneCellAndKeepsDeclarationOrder() {
        TerminalFontConfig.Result result = TerminalFontConfig.parse(
            "narrow_symbols U+E0A0-U+E0A3,U+E0C0-U+E0C7\n"
                + "narrow_symbols U+F0000-U+FFFFD 3\n", true);

        assertTrue(result.errors.toString(), result.errors.isEmpty());
        assertEquals(2, result.narrowSymbols.size());
        assertEquals(1, result.narrowSymbols.get(0).cells);
        assertEquals(2, result.narrowSymbols.get(0).ranges.size());
        assertEquals(0xE0A0, result.narrowSymbols.get(0).ranges.get(0).first);
        assertEquals(0xE0A3, result.narrowSymbols.get(0).ranges.get(0).last);
        assertEquals(0xE0C7, result.narrowSymbols.get(0).ranges.get(1).last);
        assertEquals(3, result.narrowSymbols.get(1).cells);
        assertEquals(0xFFFFD, result.narrowSymbols.get(1).ranges.get(0).last);
    }

    @Test
    public void narrowSymbolsRejectsCellCountsOutsideKittysRange() {
        TerminalFontConfig.Result zero =
            TerminalFontConfig.parse("narrow_symbols U+E000-U+F8FF 0\n", true);
        assertFalse(zero.errors.isEmpty());
        assertTrue(zero.narrowSymbols.isEmpty());

        TerminalFontConfig.Result tooMany =
            TerminalFontConfig.parse("narrow_symbols U+E000-U+F8FF 6\n", true);
        assertFalse(tooMany.errors.isEmpty());
        assertTrue(tooMany.narrowSymbols.isEmpty());

        TerminalFontConfig.Result notANumber =
            TerminalFontConfig.parse("narrow_symbols U+E000-U+F8FF two\n", true);
        assertFalse(notANumber.errors.isEmpty());
        assertTrue(notANumber.narrowSymbols.isEmpty());

        TerminalFontConfig.Result noRanges = TerminalFontConfig.parse("narrow_symbols\n", true);
        assertFalse(noRanges.errors.isEmpty());
        assertTrue(noRanges.narrowSymbols.isEmpty());
    }

    @Test
    public void narrowSymbolsIsAbsentWithoutTheDirective() {
        TerminalFontConfig.Result result =
            TerminalFontConfig.parse("font_family path=~/.termux/font.ttf\n", true);
        assertTrue(result.narrowSymbols.isEmpty());
    }
}
