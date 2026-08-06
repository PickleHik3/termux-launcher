package com.termux.app.fonts;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.EnumMap;
import java.util.Map;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * The managed config's exact text is the contract between the installer, the loader and the
 * user reading the file, so it is asserted character for character across the toggle
 * combinations that actually differ: the family defaults, icons off, a hand-picked ligature
 * policy plus weight, a static family with no axis, and a family with no italic face.
 *
 * <p>The other contract asserted here is a negative one: an install never touches
 * {@code ~/.termux/font.ttf} or {@code font-italic.ttf}.
 */
@RunWith(RobolectricTestRunner.class)
public class FontInstallerTest {

    private static final String SYMBOLS = "SymbolsNerdFontMono.ttf";

    @Rule public TemporaryFolder folder = new TemporaryFolder();

    private FontCatalog.Result catalog;

    @Before
    public void loadCatalog() {
        FontCatalog.resetForTesting();
        catalog = FontCatalog.load(ApplicationProvider.<Context>getApplicationContext());
    }

    // ------------------------------------------------------- managed config text

    @Test
    public void familyDefaults_writeTheVariableMapleMonoConfig() {
        FontCatalog.Family maple = family("maple-mono");
        String config = FontInstaller.buildManagedConfig(
            maple, FontInstaller.Options.recommendedFor(maple), SYMBOLS);
        assertEquals(HEADER
            + "# Family: Maple Mono (maple-mono), release v7.9\n"
            + "# License: SIL Open Font License 1.1\n"
            + "#          https://github.com/subframe7536/maple-font/blob/v7.9/OFL.txt\n"
            + "\n"
            + "font_family      path=~/.termux/fonts/maple-mono/regular.ttf\n"
            + "bold_font        path=~/.termux/fonts/maple-mono/bold.ttf\n"
            + "italic_font      path=~/.termux/fonts/maple-mono/italic.ttf\n"
            + "bold_italic_font path=~/.termux/fonts/maple-mono/bold-italic.ttf\n"
            + "\n"
            + "symbol_map U+E000-U+F8FF   path=~/.termux/fonts/symbols/SymbolsNerdFontMono.ttf\n"
            + "symbol_map U+F0000-U+FFFFD path=~/.termux/fonts/symbols/SymbolsNerdFontMono.ttf\n"
            + "\n"
            + "disable_ligatures cursor\n"
            + "\n"
            + "font_features regular     +zero\n"
            + "font_features bold        +zero\n"
            + "font_features italic      +zero\n"
            + "font_features bold_italic +zero\n"
            + "\n"
            + "font_variations regular     wght=400\n"
            + "font_variations bold        wght=700\n"
            + "font_variations italic      wght=400\n"
            + "font_variations bold_italic wght=700\n", config);
    }

    @Test
    public void iconsOn_mapsBothPrivateUsePlanesAtTheSamePath() {
        FontCatalog.Family maple = family("maple-mono");
        String config = FontInstaller.buildManagedConfig(
            maple, FontInstaller.Options.recommendedFor(maple), SYMBOLS);
        // Parity with the shipped ~/.termux/fonts.conf example: BMP PUA for powerline/devicons,
        // SPUA-A for the Material Design set. Dropping the second would lose nf-md-* glyphs.
        assertTrue(config.contains(
            "symbol_map U+E000-U+F8FF   path=~/.termux/fonts/symbols/SymbolsNerdFontMono.ttf\n"));
        assertTrue(config.contains(
            "symbol_map U+F0000-U+FFFFD path=~/.termux/fonts/symbols/SymbolsNerdFontMono.ttf\n"));
        assertEquals(2, countLinesStartingWith(config, "symbol_map "));
    }

    @Test
    public void iconsOff_dropsTheSymbolMapAndNothingElse() {
        FontCatalog.Family maple = family("maple-mono");
        String config = FontInstaller.buildManagedConfig(
            maple, FontInstaller.Options.recommendedFor(maple).withNerdIcons(false), SYMBOLS);
        assertFalse(config.contains("symbol_map"));
        assertTrue(config.contains("disable_ligatures cursor\n"));
        assertTrue(config.contains("font_features regular     +zero\n"));
        assertTrue(config.contains("font_variations bold        wght=700\n"));
    }

    @Test
    public void missingSymbolsFace_dropsTheSymbolMapEvenWithIconsOn() {
        FontCatalog.Family maple = family("maple-mono");
        assertFalse(FontInstaller.buildManagedConfig(
            maple, FontInstaller.Options.recommendedFor(maple), null).contains("symbol_map"));
    }

    @Test
    public void weightSlider_movesBoldWithItAndKeepsTheContrast() {
        FontCatalog.Family maple = family("maple-mono");
        FontInstaller.Options options = FontInstaller.Options.recommendedFor(maple)
            .withLigatures(FontInstaller.LIGATURES_ALWAYS)
            .withRecommendedFeatures(false)
            .withWeight(350);
        String config = FontInstaller.buildManagedConfig(maple, options, SYMBOLS);
        assertTrue(config.contains("disable_ligatures always\n"));
        assertFalse(config.contains("font_features"));
        assertTrue(config.contains("font_variations regular     wght=350\n"));
        assertTrue(config.contains("font_variations bold        wght=650\n"));
        assertTrue(config.contains("font_variations italic      wght=350\n"));
        assertTrue(config.contains("font_variations bold_italic wght=650\n"));
    }

    @Test
    public void weightAboveTheAxis_isClampedNotWritten() {
        FontCatalog.Family maple = family("maple-mono");
        String config = FontInstaller.buildManagedConfig(maple,
            FontInstaller.Options.recommendedFor(maple).withWeight(9999), SYMBOLS);
        // Axis is 100..800, and bold keeps its offset only as far as the axis allows.
        assertTrue(config.contains("font_variations regular     wght=800\n"));
        assertTrue(config.contains("font_variations bold        wght=800\n"));
    }

    @Test
    public void noItalicFamily_namesOnlyTheFacesItHas() {
        FontCatalog.Family fira = family("fira-code");
        String config = FontInstaller.buildManagedConfig(
            fira, FontInstaller.Options.recommendedFor(fira), SYMBOLS);
        assertEquals(HEADER
            + "# Family: Fira Code (fira-code), release 6.2\n"
            + "# License: SIL Open Font License 1.1\n"
            + "#          https://github.com/tonsky/FiraCode/blob/6.2/LICENSE\n"
            + "\n"
            + "font_family      path=~/.termux/fonts/fira-code/regular.ttf\n"
            + "bold_font        path=~/.termux/fonts/fira-code/bold.ttf\n"
            + "\n"
            + "symbol_map U+E000-U+F8FF   path=~/.termux/fonts/symbols/SymbolsNerdFontMono.ttf\n"
            + "symbol_map U+F0000-U+FFFFD path=~/.termux/fonts/symbols/SymbolsNerdFontMono.ttf\n"
            + "\n"
            + "disable_ligatures cursor\n", config);
    }

    @Test
    public void staticFamilyWithoutFeatures_writesNoAxisAndNoFeatureLines() {
        FontCatalog.Family hack = family("hack");
        String config = FontInstaller.buildManagedConfig(
            hack, FontInstaller.Options.recommendedFor(hack), SYMBOLS);
        assertEquals(HEADER
            + "# Family: Hack (hack), release v3.003\n"
            + "# License: MIT and Bitstream Vera License\n"
            + "#          https://github.com/source-foundry/Hack/blob/v3.003/LICENSE.md\n"
            + "\n"
            + "font_family      path=~/.termux/fonts/hack/regular.ttf\n"
            + "bold_font        path=~/.termux/fonts/hack/bold.ttf\n"
            + "italic_font      path=~/.termux/fonts/hack/italic.ttf\n"
            + "bold_italic_font path=~/.termux/fonts/hack/bold-italic.ttf\n"
            + "\n"
            + "symbol_map U+E000-U+F8FF   path=~/.termux/fonts/symbols/SymbolsNerdFontMono.ttf\n"
            + "symbol_map U+F0000-U+FFFFD path=~/.termux/fonts/symbols/SymbolsNerdFontMono.ttf\n"
            + "\n"
            + "disable_ligatures never\n", config);
    }

    @Test
    public void unknownLigaturePolicy_fallsBackToNever() {
        FontCatalog.Family hack = family("hack");
        assertTrue(FontInstaller.buildManagedConfig(hack,
            new FontInstaller.Options(false, "sometimes", false, 0), SYMBOLS)
            .contains("disable_ligatures never\n"));
    }

    // ------------------------------------------------------------- install / uninstall

    @Test
    public void install_placesFacesAndWritesTheManagedFile() throws IOException {
        File dataHome = folder.newFolder("termux-data-home");
        FontInstaller installer = installer(dataHome);
        FontCatalog.Family maple = family("maple-mono");
        installer.install(maple, stageFaces(maple), catalog.symbolFont,
            FontInstaller.Options.recommendedFor(maple));

        File familyDir = new File(new File(dataHome, "fonts"), "maple-mono");
        for (FontCatalog.FaceSlot slot : maple.faces.keySet()) {
            File face = new File(familyDir, slot.fileName);
            assertTrue(slot.fileName, face.isFile());
            assertEquals(slot.fileName, "font-" + slot.key, read(face));
        }
        // The OFL notice travels with the files.
        assertTrue(read(new File(familyDir, "LICENSE.txt")).contains("SIL Open Font License 1.1"));
        assertTrue(installer.isManaged());
        assertTrue(installer.isInstalled(maple));
        assertTrue(read(installer.getManagedConfigFile())
            .contains("font_family      path=~/.termux/fonts/maple-mono/regular.ttf"));
        assertNoPartialsLeft(dataHome);
    }

    /**
     * The regression this guards: an install used to mirror the regular face onto
     * {@code ~/.termux/font.ttf}, which silently replaced the user's own Nerd Font build and
     * stripped the icon glyphs from every surface drawing with the regular face. The user's file
     * has to come out byte-identical.
     */
    @Test
    public void install_leavesAnExistingFontTtfByteIdentical() throws IOException {
        File dataHome = folder.newFolder("keeps-font-ttf-home");
        File userFont = new File(dataHome, "font.ttf");
        File userItalic = new File(dataHome, "font-italic.ttf");
        FontInstaller.writeAtomically(userFont, "the user's own nerd font build");
        FontInstaller.writeAtomically(userItalic, "the user's own italic");
        byte[] fontBefore = Files.readAllBytes(userFont.toPath());
        byte[] italicBefore = Files.readAllBytes(userItalic.toPath());

        FontInstaller installer = installer(dataHome);
        FontCatalog.Family maple = family("maple-mono");
        installer.install(maple, stageFaces(maple), catalog.symbolFont,
            FontInstaller.Options.recommendedFor(maple));

        assertArrayEquals(fontBefore, Files.readAllBytes(userFont.toPath()));
        assertArrayEquals(italicBefore, Files.readAllBytes(userItalic.toPath()));
        // And the same on the way back out.
        assertTrue(installer.uninstallManagedConfig());
        assertArrayEquals(fontBefore, Files.readAllBytes(userFont.toPath()));
        assertArrayEquals(italicBefore, Files.readAllBytes(userItalic.toPath()));
        assertNoPartialsLeft(dataHome);
    }

    /** With no font.ttf to begin with, an install must not invent one either. */
    @Test
    public void install_createsNoFontTtfWhenNoneExisted() throws IOException {
        File dataHome = folder.newFolder("no-font-ttf-home");
        FontInstaller installer = installer(dataHome);
        FontCatalog.Family maple = family("maple-mono");
        installer.install(maple, stageFaces(maple), catalog.symbolFont,
            FontInstaller.Options.recommendedFor(maple));

        assertFalse(new File(dataHome, "font.ttf").exists());
        assertFalse(new File(dataHome, "font-italic.ttf").exists());
        // The managed config is the whole delivery mechanism, so it must name every face.
        String config = read(installer.getManagedConfigFile());
        assertTrue(config.contains("font_family      path=~/.termux/fonts/maple-mono/regular.ttf\n"));
        assertTrue(config.contains("italic_font      path=~/.termux/fonts/maple-mono/italic.ttf\n"));
        assertNoPartialsLeft(dataHome);
    }

    // -------------------------------------------------- symbols path is never left dangling

    @Test
    public void install_withTheSymbolsFacePresent_writesBothSymbolMaps() throws IOException {
        Context context = ApplicationProvider.getApplicationContext();
        File dataHome = folder.newFolder("symbols-present-home");
        FontInstaller installer = installer(dataHome);
        FontCatalog.Family maple = family("maple-mono");
        installer.ensureSymbolsInstalled(context, catalog.symbolFont);
        installer.install(maple, stageFaces(maple), catalog.symbolFont,
            FontInstaller.Options.recommendedFor(maple));

        String config = read(installer.getManagedConfigFile());
        assertEquals(2, countLinesStartingWith(config, "symbol_map "));
        assertTrue(config.contains("U+E000-U+F8FF   path=~/.termux/fonts/symbols/"));
        assertTrue(config.contains("U+F0000-U+FFFFD path=~/.termux/fonts/symbols/"));
    }

    /**
     * The managed config outlives the install, so the symbols file can disappear underneath it. A
     * dangling {@code symbol_map} path makes every icon cell fall back silently while the config
     * still reads as correct, which is why the path is omitted instead.
     */
    @Test
    public void deletedSymbolsFile_omitsTheSymbolMapInsteadOfDanglingPath() throws IOException {
        Context context = ApplicationProvider.getApplicationContext();
        File dataHome = folder.newFolder("symbols-deleted-home");
        FontInstaller installer = installer(dataHome);
        FontCatalog.Family maple = family("maple-mono");
        installer.ensureSymbolsInstalled(context, catalog.symbolFont);
        FontInstaller.Options options = FontInstaller.Options.recommendedFor(maple);
        installer.install(maple, stageFaces(maple), catalog.symbolFont, options);
        assertEquals(2, countLinesStartingWith(read(installer.getManagedConfigFile()), "symbol_map "));

        // The user wipes ~/.termux/fonts/symbols/ by hand.
        File symbols = installer.getSymbolsFile(catalog.symbolFont);
        assertTrue(symbols.delete());
        assertTrue(symbols.getParentFile().delete());
        assertNull(installer.usableSymbolsFileName(catalog.symbolFont));

        installer.writeManagedConfig(maple, options, catalog.symbolFont);
        String config = read(installer.getManagedConfigFile());
        assertFalse(config, config.contains("symbol_map"));
        assertFalse(config, config.contains("/symbols/"));
        // Everything that does not depend on the symbols face still gets written.
        assertTrue(config.contains("font_family      path=~/.termux/fonts/maple-mono/regular.ttf\n"));
        assertTrue(config.contains("disable_ligatures cursor\n"));
        assertTrue(config.contains("font_variations bold        wght=700\n"));
    }

    @Test
    public void truncatedSymbolsFile_isRefusedOnSizeAlone() throws IOException {
        File dataHome = folder.newFolder("symbols-truncated-home");
        FontInstaller installer = installer(dataHome);
        File symbols = installer.getSymbolsFile(catalog.symbolFont);
        FontInstaller.writeAtomically(symbols, "not the whole font");
        assertNull(installer.usableSymbolsFileName(catalog.symbolFont));
    }

    @Test
    public void unparsableSymbolsFile_isRefusedEvenAtTheRightSize() throws IOException {
        Context context = ApplicationProvider.getApplicationContext();
        File dataHome = folder.newFolder("symbols-unparsable-home");
        // Right name, right size, right digest — but Android cannot load it as a font.
        FontInstaller staging = installer(dataHome);
        staging.ensureSymbolsInstalled(context, catalog.symbolFont);
        FontInstaller refusing = new FontInstaller(dataHome, NOTHING_LOADS);
        assertNull(refusing.usableSymbolsFileName(catalog.symbolFont));

        FontCatalog.Family maple = family("maple-mono");
        refusing.install(maple, stageFaces(maple), catalog.symbolFont,
            FontInstaller.Options.recommendedFor(maple));
        assertFalse(read(refusing.getManagedConfigFile()).contains("symbol_map"));
    }

    @Test
    public void install_withoutAnItalicNamesOnlyTheFacesOnDisk() throws IOException {
        File dataHome = folder.newFolder("no-italic-home");
        FontInstaller installer = installer(dataHome);
        FontCatalog.Family fira = family("fira-code");
        installer.install(fira, stageFaces(fira), catalog.symbolFont,
            FontInstaller.Options.recommendedFor(fira));
        assertTrue(new File(new File(new File(dataHome, "fonts"), "fira-code"), "regular.ttf").isFile());
        String config = read(installer.getManagedConfigFile());
        assertTrue(config.contains("font_family      path=~/.termux/fonts/fira-code/regular.ttf\n"));
        assertFalse(config.contains("italic_font"));
    }

    @Test
    public void uninstall_deletesOnlyTheManagedFile() throws IOException {
        File dataHome = folder.newFolder("uninstall-home");
        FontInstaller installer = installer(dataHome);
        FontCatalog.Family maple = family("maple-mono");
        installer.install(maple, stageFaces(maple), catalog.symbolFont,
            FontInstaller.Options.recommendedFor(maple));

        // The user's own config, plus a sibling fragment some other tool might own.
        File userConfig = new File(dataHome, "fonts.conf");
        FontInstaller.writeAtomically(userConfig, "font_family path=~/.termux/mine.ttf\n");
        File sibling = new File(new File(dataHome, FontInstaller.FONTS_D_DIR_NAME), "20-other.conf");
        FontInstaller.writeAtomically(sibling, "disable_ligatures always\n");

        assertTrue(installer.uninstallManagedConfig());
        assertFalse(installer.getManagedConfigFile().exists());
        assertFalse(installer.isManaged());
        // Everything else survives, including the installed faces.
        assertEquals("font_family path=~/.termux/mine.ttf\n", read(userConfig));
        assertEquals("disable_ligatures always\n", read(sibling));
        assertTrue(installer.isInstalled(maple));
        // A second uninstall is a no-op, not a failure to report.
        assertFalse(installer.uninstallManagedConfig());
        assertTrue(userConfig.isFile());
    }

    // --------------------------------------------------------------- atomic write

    @Test
    public void writeAtomically_replacesContentAndLeavesNoPartial() throws IOException {
        File target = new File(folder.newFolder("atomic"), "nested/dir/config.conf");
        FontInstaller.writeAtomically(target, "first\n");
        assertEquals("first\n", read(target));
        FontInstaller.writeAtomically(target, "second and longer\n");
        assertEquals("second and longer\n", read(target));
        assertFalse(new File(target.getAbsolutePath() + ".part").exists());
    }

    @Test
    public void writeAtomically_failingMidwayLeavesTheOldFileIntact() throws IOException {
        File dir = folder.newFolder("atomic-fail");
        File target = new File(dir, "config.conf");
        FontInstaller.writeAtomically(target, "original\n");
        // A directory sitting where the temp file wants to be makes the write fail; the committed
        // file must be exactly what it was.
        File blocker = new File(target.getAbsolutePath() + ".part");
        assertTrue(blocker.mkdirs());
        boolean threw = false;
        try {
            FontInstaller.writeAtomically(target, "replacement\n");
        } catch (IOException e) {
            threw = true;
        }
        assertTrue(threw);
        assertEquals("original\n", read(target));
    }

    @Test
    public void copyAtomically_leavesNoPartialBehind() throws IOException {
        File dir = folder.newFolder("copy");
        File source = new File(dir, "source.ttf");
        FontInstaller.writeAtomically(source, "bytes");
        File target = new File(new File(dir, "sub"), "target.ttf");
        FontInstaller.copyAtomically(source, target);
        assertEquals("bytes", read(target));
        assertFalse(new File(target.getAbsolutePath() + ".part").exists());
    }

    @Test
    public void ensureSymbolsInstalled_extractsOnceAndIsIdempotent() throws IOException {
        Context context = ApplicationProvider.getApplicationContext();
        File dataHome = folder.newFolder("symbols-home");
        FontInstaller installer = installer(dataHome);
        FontCatalog.SymbolFont symbols = catalog.symbolFont;
        assertNotNull(symbols);

        File first = installer.ensureSymbolsInstalled(context, symbols);
        assertEquals(symbols.sizeBytes, first.length());
        long stamp = first.lastModified();
        // A second call must find the existing copy by size and digest and not rewrite it.
        File second = installer.ensureSymbolsInstalled(context, symbols);
        assertEquals(first.getAbsolutePath(), second.getAbsolutePath());
        assertEquals(stamp, second.lastModified());
        assertFalse(new File(first.getAbsolutePath() + ".part").exists());
    }

    // ------------------------------------------------------------------- helpers

    /** Header every managed file starts with; asserted once here rather than in five places. */
    private static final String HEADER =
        "# ~/.termux/fonts.d/10-launcher.conf — generated by Termux Launcher, do not edit.\n"
            + "#\n"
            + "# This file is written by the app (Settings > Appearance > Terminal fonts).\n"
            + "# Every change made there replaces it completely, so edits here are lost.\n"
            + "#\n"
            + "# Your own ~/.termux/fonts.conf is read after this file, so anything set there\n"
            + "# overrides everything below. To take over by hand: copy the lines you want into\n"
            + "# ~/.termux/fonts.conf, then choose \"Use font.ttf / Termux:Styling\" in the app\n"
            + "# to delete this file.\n"
            + "#\n";

    /**
     * Stands in for {@code Typeface.createFromFile}: Robolectric has no real font parser, and the
     * question these tests ask is "does the installer check the file at all", not "can Android
     * parse a TTF".
     */
    private static final FontDownloader.TypefaceProbe PRESENT_FILES_LOAD =
        file -> file.isFile() && file.length() > 0L;

    private static final FontDownloader.TypefaceProbe NOTHING_LOADS = file -> false;

    private FontInstaller installer(File dataHome) {
        return new FontInstaller(dataHome, PRESENT_FILES_LOAD);
    }

    private FontCatalog.Family family(String id) {
        FontCatalog.Family family = catalog.family(id);
        assertNotNull("catalog is missing " + id, family);
        return family;
    }

    /** Stand-in for {@link FontDownloader#stageFamily}: one tiny file per declared face. */
    private Map<FontCatalog.FaceSlot, File> stageFaces(FontCatalog.Family family) throws IOException {
        File staging = folder.newFolder("staging-" + family.id + "-" + System.nanoTime());
        Map<FontCatalog.FaceSlot, File> staged = new EnumMap<>(FontCatalog.FaceSlot.class);
        for (FontCatalog.FaceSlot slot : family.faces.keySet()) {
            File file = new File(staging, slot.fileName);
            FontInstaller.writeAtomically(file, "font-" + slot.key);
            staged.put(slot, file);
        }
        return staged;
    }

    private static String read(File file) throws IOException {
        return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
    }

    private static int countLinesStartingWith(String text, String prefix) {
        int count = 0;
        for (String line : text.split("\n", -1)) {
            if (line.startsWith(prefix)) count++;
        }
        return count;
    }

    private static void assertNoPartialsLeft(File root) {
        File[] children = root.listFiles();
        if (children == null) return;
        for (File child : children) {
            assertFalse(child.getPath(), child.getName().endsWith(".part"));
            if (child.isDirectory()) assertNoPartialsLeft(child);
        }
    }
}
