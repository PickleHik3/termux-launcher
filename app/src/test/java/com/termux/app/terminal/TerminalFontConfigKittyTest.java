package com.termux.app.terminal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/**
 * kitty compatibility: the {@code ~/.config/kitty/kitty.conf} source and the grammar kitty's own
 * parser accepts. {@link TerminalFontConfigTest} covers the directives we added on top of it.
 */
public class TerminalFontConfigKittyTest {

    @Rule public final TemporaryFolder home = new TemporaryFolder();

    /**
     * The acceptance case: the block herdr-radar writes into kitty.conf, untouched. Bare trailing
     * words are one family name, and the kitty directives around it are none of our business.
     */
    @Test
    public void radarsUntouchedKittyConfSymbolMapsLoad() throws Exception {
        kittyConf("# >>> herdr-radar font block\n"
            + "symbol_map U+E1A0-U+E1B6 Herdr Agent Icons Max\n"
            + "symbol_map U+E1C0-U+E1C5 Herdr Agent Icons Max\n"
            + "# <<< herdr-radar font block\n"
            + "map ctrl+shift+t new_tab\n"
            + "background_opacity 0.9\n"
            + "font_size 11.0\n");

        TerminalFontConfig.Result result = load();

        assertTrue(result.errors.toString(), result.errors.isEmpty());
        assertTrue(result.filePresent);
        assertEquals(2, result.symbolMaps.size());
        assertEquals(TerminalFontConfig.SourceType.FAMILY, result.symbolMaps.get(0).font.type);
        assertEquals("Herdr Agent Icons Max", result.symbolMaps.get(0).font.value);
        assertEquals("Herdr Agent Icons Max", result.symbolMaps.get(1).font.value);
        assertEquals(0xE1A0, result.symbolMaps.get(0).ranges.get(0).first);
        assertEquals(0xE1B6, result.symbolMaps.get(0).ranges.get(0).last);
        assertEquals(0xE1C0, result.symbolMaps.get(1).ranges.get(0).first);
        assertEquals(0xE1C5, result.symbolMaps.get(1).ranges.get(0).last);
    }

    /** kitty.conf is the bottom of the stack: a drop-in and the user's own file both outrank it. */
    @Test
    public void theUsersOwnFilesStillWinOverKittyConf() throws Exception {
        kittyConf("symbol_map U+E1A0-U+E1B6 Herdr Agent Icons Max\n"
            + "font_family Herdr Agent Icons Max\n");
        dropIn("10-launcher.conf", "font_family family=Managed\n"
            + "symbol_map U+F0000-U+FFFFD path=/managed.ttf\n");
        fontsConf("symbol_map U+E1A0-U+E1B6 path=/user.ttf\nfont_family family=User\n");

        TerminalFontConfig.Result result = load();

        assertTrue(result.errors.toString(), result.errors.isEmpty());
        assertEquals("User", result.face(TerminalFontConfig.Face.REGULAR).value);
        // Ranges accumulate in load order and a later one wins the code points it overlaps, so
        // the file read last is the map the renderer reaches first.
        assertEquals(3, result.symbolMaps.size());
        assertEquals("Herdr Agent Icons Max", result.symbolMaps.get(0).font.value);
        assertEquals("/user.ttf", result.symbolMaps.get(2).font.value);
        assertEquals(TerminalFontConfig.SourceType.PATH, result.symbolMaps.get(2).font.type);
    }

    @Test
    public void unknownDirectivesAreNoiseInKittyConfAndAMistakeEverywhereElse() throws Exception {
        kittyConf("map ctrl+shift+t new_tab\ncursor_trail 3\n");
        fontsConf("map ctrl+shift+t new_tab\n");

        TerminalFontConfig.Result result = load();

        assertEquals(result.errors.toString(), 1, result.errors.size());
        assertEquals("line 1: unknown directive 'map'", result.errors.get(0));
    }

    /** A font directive is reported wherever it is written, with the file that carries it. */
    @Test
    public void malformedFontDirectivesInKittyConfAreStillReported() throws Exception {
        kittyConf("symbol_map E1A0 Herdr Agent Icons Max\n");

        TerminalFontConfig.Result result = load();

        assertEquals(result.errors.toString(), 1, result.errors.size());
        assertEquals("kitty.conf: line 1: invalid Unicode range 'E1A0'", result.errors.get(0));
    }

    @Test
    public void symbolMapKeepsOurPrefixedFormsAlongsideKittys() throws Exception {
        fontsConf("symbol_map U+E000-U+E00F path=/nerd.ttf\n"
            + "symbol_map name=box U+2500-U+257F family=\"Box Drawing\"\n"
            + "symbol_map U+E0A0-U+E0A3,U+E0C0-U+E0C7 PowerlineSymbols\n");

        TerminalFontConfig.Result result = load();

        assertTrue(result.errors.toString(), result.errors.isEmpty());
        assertEquals(3, result.symbolMaps.size());
        assertEquals("/nerd.ttf", result.symbolMaps.get(0).font.value);
        assertEquals("box", result.symbolMaps.get(1).name);
        assertEquals("Box Drawing", result.symbolMaps.get(1).font.value);
        assertEquals("PowerlineSymbols", result.symbolMaps.get(2).font.value);
        assertEquals(2, result.symbolMaps.get(2).ranges.size());
        assertEquals(0xE0C7, result.symbolMaps.get(2).ranges.get(1).last);
    }

    @Test
    public void facesAcceptAutoBareNamesAndKittysFontSpec() throws Exception {
        fontsConf("font_family Fira Code\n"
            + "bold_font family=\"Fira Code\" style=Bold postscript_name=FiraCode-Bold wght=600\n"
            + "italic_font auto\n"
            + "bold_italic_font postscript_name=FiraCode-BoldItalic\n");

        TerminalFontConfig.Result result = load();

        assertTrue(result.errors.toString(), result.errors.isEmpty());
        assertEquals(TerminalFontConfig.SourceType.FAMILY,
            result.face(TerminalFontConfig.Face.REGULAR).type);
        assertEquals("Fira Code", result.face(TerminalFontConfig.Face.REGULAR).value);
        // family= outranks the postscript name on the same line.
        assertEquals("Fira Code", result.face(TerminalFontConfig.Face.BOLD).value);
        assertEquals("'wght' 600", result.variations(TerminalFontConfig.FontTarget.BOLD));
        assertNull(result.face(TerminalFontConfig.Face.ITALIC));
        assertEquals("FiraCode-BoldItalic",
            result.face(TerminalFontConfig.Face.BOLD_ITALIC).value);
    }

    @Test
    public void autoClearsAFaceSetByAnEarlierFile() throws Exception {
        dropIn("10-launcher.conf", "bold_font path=/managed.ttf\n");
        fontsConf("bold_font auto\n");

        TerminalFontConfig.Result result = load();

        assertTrue(result.errors.toString(), result.errors.isEmpty());
        assertNull(result.face(TerminalFontConfig.Face.BOLD));
    }

    @Test
    public void aFontSpecCarriesItsOwnFeaturesAndAxes() throws Exception {
        fontsConf("font_family family=SourceCodeVF variable_name=SourceCodeUpright"
            + " features=\"+zero cv01=2\" wght=380\n");

        TerminalFontConfig.Result result = load();

        assertTrue(result.errors.toString(), result.errors.isEmpty());
        assertEquals("SourceCodeVF", result.face(TerminalFontConfig.Face.REGULAR).value);
        assertEquals("'zero' 1, 'cv01' 2", result.features(TerminalFontConfig.FontTarget.REGULAR));
        assertEquals("'wght' 380", result.variations(TerminalFontConfig.FontTarget.REGULAR));
    }

    @Test
    public void rejectsAFontSpecKeyThatIsNeitherKnownNorAnAxis() throws Exception {
        fontsConf("font_family family=Fira weight=600\n");

        TerminalFontConfig.Result result = load();

        assertEquals(result.errors.toString(), 1, result.errors.size());
        assertEquals("line 1: unknown font spec key 'weight'", result.errors.get(0));
        assertNull(result.face(TerminalFontConfig.Face.REGULAR));
    }

    /** kitty's font_features target is a PostScript name, so a family name is the nearest match. */
    @Test
    public void fontFeaturesMayTargetAConfiguredFamily() throws Exception {
        kittyConf("symbol_map U+E1A0-U+E1B6 Herdr Agent Icons Max\n"
            + "font_family Fira Code\n"
            + "font_features 'Fira Code' +zero\n"
            + "font_features 'herdr agent icons max' +calt\n");

        TerminalFontConfig.Result result = load();

        assertTrue(result.errors.toString(), result.errors.isEmpty());
        assertEquals("'zero' 1", result.features(TerminalFontConfig.FontTarget.REGULAR));
        assertEquals("'calt' 1", result.symbolMaps.get(0).features);
    }

    @Test
    public void aFeatureTargetMatchingNothingIsSilentOnlyInKittyConf() throws Exception {
        kittyConf("font_features FiraCode-Retina +zero\n");
        fontsConf("font_features FiraCode-Retina +zero\n");

        TerminalFontConfig.Result result = load();

        assertEquals(result.errors.toString(), 1, result.errors.size());
        assertEquals("line 1: font_features target 'FiraCode-Retina' matches no symbol map or"
            + " configured family", result.errors.get(0));
    }

    /** kitty: only a leading '#' comments a line, and a '\' starts a continuation of the last. */
    @Test
    public void readsKittysLineSyntax() throws Exception {
        fontsConf("   # a comment\n"
            + "symbol_map U+E000-U+E00F Iosevka #2\n"
            + "font_family Fira\n"
            + "\\ Code\n"
            + "\\ Retina\n");

        TerminalFontConfig.Result result = load();

        assertTrue(result.errors.toString(), result.errors.isEmpty());
        assertEquals("Iosevka #2", result.symbolMaps.get(0).font.value);
        assertEquals("Fira Code Retina", result.face(TerminalFontConfig.Face.REGULAR).value);
    }

    @Test
    public void kittyConfIncludesOneLevelDeep() throws Exception {
        kittyConf("include fonts.conf\ninclude deeper.conf\n");
        kittyFile("fonts.conf", "symbol_map U+E1A0-U+E1B6 Herdr Agent Icons Max\n");
        kittyFile("deeper.conf", "include nested.conf\nfont_family Deeper\n");
        kittyFile("nested.conf", "font_family Nested\n");

        TerminalFontConfig.Result result = load();

        assertTrue(result.errors.toString(), result.errors.isEmpty());
        assertEquals("Herdr Agent Icons Max", result.symbolMaps.get(0).font.value);
        // The second level is dropped, so the including file's own line is the one that lands.
        assertEquals("Deeper", result.face(TerminalFontConfig.Face.REGULAR).value);
    }

    @Test
    public void anIncludeMayNotReachOutOfTheKittyConfigDirectory() throws Exception {
        kittyConf("include ../outside.conf\n");
        write(new File(kittyDir().getParentFile(), "outside.conf"), "font_family Outside\n");

        TerminalFontConfig.Result result = load();

        assertEquals(result.errors.toString(), 1, result.errors.size());
        assertEquals("kitty.conf: line 1: include ../outside.conf resolves outside the kitty"
            + " config directory", result.errors.get(0));
        assertNull(result.face(TerminalFontConfig.Face.REGULAR));
    }

    @Test
    public void generatedAndGlobIncludesAreSkippedInSilence() throws Exception {
        kittyConf("globinclude kitty.d/**/*.conf\n"
            + "envinclude KITTY_CONF_*\n"
            + "geninclude ./generate-config\n"
            + "font_family Fira Code\n");

        TerminalFontConfig.Result result = load();

        assertTrue(result.errors.toString(), result.errors.isEmpty());
        assertEquals("Fira Code", result.face(TerminalFontConfig.Face.REGULAR).value);
    }

    @Test
    public void modifyFontTakesKittysUnitsAndRefusesItsPerFontSize() throws Exception {
        fontsConf("modify_font underline_position -2\n"
            + "modify_font underline_thickness 150%\n"
            + "modify_font strikethrough_position 2px\n"
            + "modify_font size Fira Code 12\n");

        TerminalFontConfig.Result result = load();

        assertEquals(result.errors.toString(), 1, result.errors.size());
        assertEquals("line 4: modify_font size is not supported", result.errors.get(0));
        assertEquals(-2d, result.metric(TerminalFontConfig.Metric.UNDERLINE_POSITION).value, 0d);
        assertEquals(TerminalFontConfig.MetricUnit.PIXEL,
            result.metric(TerminalFontConfig.Metric.UNDERLINE_POSITION).unit);
        assertEquals(150d,
            result.metric(TerminalFontConfig.Metric.UNDERLINE_THICKNESS).value, 0d);
        assertEquals(2d,
            result.metric(TerminalFontConfig.Metric.STRIKETHROUGH_POSITION).value, 0d);
    }

    @Test
    public void kittysOwnDefaultsForFontFeaturesAndBoxDrawingScaleParse() throws Exception {
        fontsConf("font_features none\nbox_drawing_scale 0.001, 1, 1.5, 2\n"
            + "narrow_symbols U+E0A0-U+E0A3,U+E0C0-U+E0C7 1\n"
            + "disable_ligatures never\n");

        TerminalFontConfig.Result result = load();

        assertTrue(result.errors.toString(), result.errors.isEmpty());
        assertEquals(0.001d, result.boxDrawingScale.thin, 0d);
        assertEquals(2d, result.boxDrawingScale.veryHeavy, 0d);
        assertEquals(1, result.narrowSymbols.get(0).cells);
    }

    private TerminalFontConfig.Result load() {
        return TerminalFontConfig.load(new File(kittyDir(), TerminalFontConfig.KITTY_FILE_NAME),
            dropInDir(), new File(termuxDir(), TerminalFontConfig.FILE_NAME));
    }

    private File termuxDir() {
        return new File(home.getRoot(), ".termux");
    }

    private File dropInDir() {
        return new File(termuxDir(), TerminalFontConfig.DROP_IN_DIR_NAME);
    }

    private File kittyDir() {
        return new File(home.getRoot(), ".config/kitty");
    }

    private void kittyConf(String content) throws Exception {
        kittyFile(TerminalFontConfig.KITTY_FILE_NAME, content);
    }

    private void kittyFile(String name, String content) throws Exception {
        write(new File(kittyDir(), name), content);
    }

    private void dropIn(String name, String content) throws Exception {
        write(new File(dropInDir(), name), content);
    }

    private void fontsConf(String content) throws Exception {
        write(new File(termuxDir(), TerminalFontConfig.FILE_NAME), content);
    }

    private static void write(File file, String content) throws Exception {
        File parent = file.getParentFile();
        assertTrue(parent.isDirectory() || parent.mkdirs());
        Files.write(file.toPath(), content.getBytes(StandardCharsets.UTF_8));
    }
}
