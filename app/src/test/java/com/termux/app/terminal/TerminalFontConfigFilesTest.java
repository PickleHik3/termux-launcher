package com.termux.app.terminal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/**
 * File level behaviour of the font config: the {@code fonts.d} drop-in directory, its ordering
 * and its bounds. {@link TerminalFontLoaderTest} covers what the typeface loader then does.
 */
public class TerminalFontConfigFilesTest {

    @Rule public final TemporaryFolder temporary = new TemporaryFolder();

    @Test
    public void dropInsApplyInAscendingByteWiseFilenameOrder() throws Exception {
        dropIn("10-a.conf", "bold_font family=ten\nfont_family family=ten-regular\n");
        dropIn("20-b.conf", "bold_font family=twenty\n");
        dropIn("9-z.conf", "bold_font family=nine\n");

        TerminalFontConfig.Result result = load();

        assertTrue(result.errors.toString(), result.errors.isEmpty());
        // '9' sorts after '1' and '2' byte-wise, so 9-z.conf is the last file loaded.
        assertEquals("nine", result.face(TerminalFontConfig.Face.BOLD).value);
        assertEquals("ten-regular", result.face(TerminalFontConfig.Face.REGULAR).value);
        assertTrue("a drop-in alone is an active configuration", result.filePresent);
    }

    @Test
    public void userFontsConfWinsOverEveryDropIn() throws Exception {
        dropIn("10-a.conf", "bold_font family=ten\nsymbol_map U+E000 family=ten\n");
        dropIn("20-b.conf", "bold_font family=twenty\nsymbol_map U+E100 family=twenty\n");
        fontsConf("bold_font family=user\nsymbol_map U+E200 family=user\n");

        TerminalFontConfig.Result result = load();

        assertTrue(result.errors.toString(), result.errors.isEmpty());
        assertEquals("user", result.face(TerminalFontConfig.Face.BOLD).value);
        assertEquals(3, result.symbolMaps.size());
        assertEquals("ten", result.symbolMaps.get(0).font.value);
        assertEquals("twenty", result.symbolMaps.get(1).font.value);
        assertEquals("user", result.symbolMaps.get(2).font.value);
    }

    @Test
    public void malformedDropInLineKeepsEveryOtherFileActive() throws Exception {
        dropIn("10-bad.conf", "font_family relative.ttf\nbold_font family=good-bold\n");
        dropIn("20-ok.conf", "italic_font family=good-italic\n");

        TerminalFontConfig.Result result = load();

        assertEquals(result.errors.toString(), 1, result.errors.size());
        assertEquals("fonts.d/10-bad.conf: line 1: font source must start with path= or family=",
            result.errors.get(0));
        assertEquals("good-bold", result.face(TerminalFontConfig.Face.BOLD).value);
        assertEquals("good-italic", result.face(TerminalFontConfig.Face.ITALIC).value);
        assertNull(result.face(TerminalFontConfig.Face.REGULAR));
    }

    @Test
    public void fontsConfErrorsKeepTheirUnprefixedFormat() throws Exception {
        fontsConf("font_family relative.ttf\n");

        TerminalFontConfig.Result result = load();

        assertEquals(result.errors.toString(), 1, result.errors.size());
        assertEquals("line 1: font source must start with path= or family=",
            result.errors.get(0));
    }

    @Test
    public void dropInFileCountIsBounded() throws Exception {
        for (int i = 1; i <= 33; i++)
            dropIn(String.format("%02d.conf", i), "bold_font family=f" + i + "\n");

        TerminalFontConfig.Result result = load();

        assertEquals(result.errors.toString(), 1, result.errors.size());
        assertEquals("fonts.d: file count exceeds 32", result.errors.get(0));
        assertEquals("f32", result.face(TerminalFontConfig.Face.BOLD).value);
    }

    @Test
    public void aggregateByteBudgetStopsReadingFurtherDropIns() throws Exception {
        for (int i = 1; i <= 5; i++)
            dropIn(String.format("%02d.conf", i), "italic_font family=f" + i + "\n" + padding());
        fontsConf("bold_font family=user\n");

        TerminalFontConfig.Result result = load();

        assertEquals(result.errors.toString(), 1, result.errors.size());
        assertEquals("fonts.d: drop-in set exceeds 262144 bytes; remaining fonts.d files skipped",
            result.errors.get(0));
        assertEquals("f4", result.face(TerminalFontConfig.Face.ITALIC).value);
        // The budget covers the drop-ins only, so the user's own file is still applied.
        assertEquals("user", result.face(TerminalFontConfig.Face.BOLD).value);
    }

    @Test
    public void dropInsThatExhaustTheBudgetCannotSkipTheUsersFontsConf() throws Exception {
        for (int i = 1; i <= 6; i++)
            dropIn(String.format("%02d.conf", i), "italic_font family=f" + i + "\n" + padding());
        fontsConf("font_family path=~/.termux/user.ttf\n");

        TerminalFontConfig.Result result = load();

        assertTrue(result.filePresent);
        assertEquals("~/.termux/user.ttf", result.face(TerminalFontConfig.Face.REGULAR).value);
        assertEquals(TerminalFontConfig.SourceType.PATH,
            result.face(TerminalFontConfig.Face.REGULAR).type);
        assertTrue(result.errors.toString(), result.errors.contains(
            "fonts.d: drop-in set exceeds 262144 bytes; remaining fonts.d files skipped"));
    }

    @Test
    public void oversizedDropInSkipsOnlyThatFile() throws Exception {
        StringBuilder big = new StringBuilder("bold_font family=big\n");
        while (big.length() < 70 * 1024) big.append(padding());
        dropIn("10-big.conf", big.toString());
        dropIn("20-ok.conf", "bold_font family=ok\n");

        TerminalFontConfig.Result result = load();

        assertEquals(result.errors.toString(), 1, result.errors.size());
        assertEquals("fonts.d/10-big.conf: font config exceeds 65536 bytes",
            result.errors.get(0));
        assertEquals("ok", result.face(TerminalFontConfig.Face.BOLD).value);
    }

    @Test
    public void onlyTopLevelConfFilesAreLoaded() throws Exception {
        dropIn("A.CONF", "bold_font family=upper\n");
        dropIn("notes.txt", "bold_font family=text\n");
        File nested = new File(dropInDir(), "nested.conf");
        assertTrue(nested.mkdirs());
        Files.write(new File(nested, "inner.conf").toPath(),
            "bold_font family=nested\n".getBytes(StandardCharsets.UTF_8));

        TerminalFontConfig.Result result = load();

        assertTrue(result.errors.toString(), result.errors.isEmpty());
        assertEquals("upper", result.face(TerminalFontConfig.Face.BOLD).value);
    }

    @Test
    public void symbolMapNameDeclaredInALaterFileStillResolves() throws Exception {
        dropIn("10-features.conf", "font_features nerd +liga\nfont_variations nerd wght=600\n");
        dropIn("20-map.conf", "symbol_map name=nerd U+E000-U+E0FF family=Nerd\n");

        TerminalFontConfig.Result result = load();

        assertTrue(result.errors.toString(), result.errors.isEmpty());
        assertEquals(1, result.symbolMaps.size());
        assertEquals("nerd", result.symbolMaps.get(0).name);
        assertEquals("'liga' 1", result.symbolMaps.get(0).features);
        assertEquals("'wght' 600", result.symbolMaps.get(0).variations);
    }

    @Test
    public void undeclaredSymbolMapNameIsReportedWithItsOriginatingFile() throws Exception {
        dropIn("10-features.conf", "font_features ghost +liga\n");
        fontsConf("font_features nerd +calt\nsymbol_map name=nerd U+E000 family=Nerd\n");

        TerminalFontConfig.Result result = load();

        assertEquals(result.errors.toString(), 1, result.errors.size());
        assertEquals("fonts.d/10-features.conf: line 1: font_features names undeclared symbol"
            + " map 'ghost'", result.errors.get(0));
        assertEquals("'calt' 1", result.symbolMaps.get(0).features);
    }

    @Test
    public void noFontsConfAndNoDropInsIsAnEmptyDefaultConfig() {
        TerminalFontConfig.Result result = load();

        assertFalse(result.filePresent);
        assertTrue(result.faces.isEmpty());
        assertTrue(result.symbolMaps.isEmpty());
        assertTrue(result.fallbackFonts.isEmpty());
        assertTrue(result.errors.toString(), result.errors.isEmpty());
        assertEquals(TerminalFontConfig.BoxDrawingMode.SYNTHESIZE, result.boxDrawing);
        assertEquals(TerminalFontConfig.PowerlineMode.FONT, result.powerlineSymbols);
    }

    private TerminalFontConfig.Result load() {
        return TerminalFontConfig.load(dropInDir(), new File(temporary.getRoot(),
            TerminalFontConfig.FILE_NAME));
    }

    private File dropInDir() {
        return new File(temporary.getRoot(), TerminalFontConfig.DROP_IN_DIR_NAME);
    }

    private void dropIn(String name, String content) throws Exception {
        File dir = dropInDir();
        assertTrue(dir.isDirectory() || dir.mkdirs());
        Files.write(new File(dir, name).toPath(), content.getBytes(StandardCharsets.UTF_8));
    }

    private void fontsConf(String content) throws Exception {
        Files.write(new File(temporary.getRoot(), TerminalFontConfig.FILE_NAME).toPath(),
            content.getBytes(StandardCharsets.UTF_8));
    }

    /** About 60 KiB of comment lines, so a handful of files exhausts the aggregate budget. */
    private static String padding() {
        StringBuilder line = new StringBuilder("#");
        while (line.length() < 3000) line.append('x');
        line.append('\n');
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < 20; i++) result.append(line);
        return result.toString();
    }
}
