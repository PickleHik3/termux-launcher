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
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** Matching a {@code family=} name against the font files the user actually installed. */
public class FontFamilyIndexTest {

    @Rule public final TemporaryFolder home = new TemporaryFolder();

    @Test
    public void matchesAFamilyNameAgainstAFileNameWithoutItsStyleSuffix() throws Exception {
        font(".termux/fonts/herdr", "HerdrAgentIconsMax-Regular.ttf");

        FontFamilyIndex index = index();

        assertEquals("HerdrAgentIconsMax-Regular.ttf",
            index.find("Herdr Agent Icons Max", FontFamilyIndex.REGULAR).getName());
        // The full file name answers too, however it is punctuated.
        assertEquals("HerdrAgentIconsMax-Regular.ttf",
            index.find("herdr agent icons max regular", FontFamilyIndex.REGULAR).getName());
        assertNull(index.find("Something Else", FontFamilyIndex.REGULAR));
    }

    @Test
    public void picksTheFileForTheRequestedStyleAndFallsBackWhenItIsMissing() throws Exception {
        font(".fonts", "MapleMono-NF-Regular.ttf");
        font(".fonts", "MapleMono-NF-Bold.ttf");
        font(".fonts", "MapleMono-NF-Italic.ttf");

        FontFamilyIndex index = index();

        assertEquals("MapleMono-NF-Regular.ttf",
            index.find("Maple Mono NF", FontFamilyIndex.REGULAR).getName());
        assertEquals("MapleMono-NF-Bold.ttf",
            index.find("Maple Mono NF", FontFamilyIndex.BOLD).getName());
        assertEquals("MapleMono-NF-Italic.ttf",
            index.find("Maple Mono NF", FontFamilyIndex.ITALIC).getName());
        // No bold italic file, so the nearest real face stands in and Android synthesizes the rest.
        assertEquals("MapleMono-NF-Italic.ttf",
            index.find("Maple Mono NF", FontFamilyIndex.BOLD_ITALIC).getName());
    }

    @Test
    public void scansTheKittyDirectoriesRecursivelyAndIgnoresEverythingElse() throws Exception {
        font(".local/share/fonts/vendor/iosevka", "Iosevka.otf");
        File notes = new File(home.getRoot(), ".fonts");
        assertTrue(notes.mkdirs());
        Files.write(new File(notes, "notes.txt").toPath(),
            "not a font".getBytes(StandardCharsets.UTF_8));

        FontFamilyIndex index = index();

        assertEquals("Iosevka.otf", index.find("Iosevka", FontFamilyIndex.REGULAR).getName());
        assertNull(index.find("notes", FontFamilyIndex.REGULAR));
    }

    @Test
    public void theFirstDirectoryToClaimANameKeepsIt() throws Exception {
        font(".termux/fonts", "Iosevka-Regular.ttf");
        font(".fonts/second", "Iosevka-Regular.ttf");

        File found = index().find("Iosevka", FontFamilyIndex.REGULAR);

        assertTrue(found.getPath(), found.getPath().contains(".termux"));
    }

    /** herdr-radar's real install: a build hash where a style name usually sits. */
    @Test
    public void aBuildHashSuffixStillAnswersToTheFamilyName() throws Exception {
        font(".local/share/fonts", "HerdrAgentIconsMax-d77910ea.ttf");

        FontFamilyIndex index = index();

        assertEquals("HerdrAgentIconsMax-d77910ea.ttf",
            index.find("Herdr Agent Icons Max", FontFamilyIndex.REGULAR).getName());
        // A hash says nothing about which face the file is, so it stands in for every style.
        assertEquals("HerdrAgentIconsMax-d77910ea.ttf",
            index.find("Herdr Agent Icons Max", FontFamilyIndex.BOLD).getName());
        assertEquals("HerdrAgentIconsMax-d77910ea.ttf",
            index.find("HerdrAgentIconsMax-d77910ea", FontFamilyIndex.REGULAR).getName());
    }

    @Test
    public void onlyOneTokenThatDoesNotNameAFaceIsEverDropped() throws Exception {
        font(".fonts", "IosevkaTerm-Curly-Regular.ttf");

        FontFamilyIndex index = index();

        // The style token is free, and one more may go after it.
        assertEquals("IosevkaTerm-Curly-Regular.ttf",
            index.find("IosevkaTerm Curly", FontFamilyIndex.REGULAR).getName());
        assertEquals("IosevkaTerm-Curly-Regular.ttf",
            index.find("IosevkaTerm", FontFamilyIndex.REGULAR).getName());
        // Shedding a second one would let a plain Iosevka request land on a Term build.
        assertNull(index.find("Iosevka", FontFamilyIndex.REGULAR));
    }

    @Test
    public void aFileThatSpellsTheFamilyOutInFullBeatsOneThatHadToBeShortened() throws Exception {
        font(".fonts", "Iosevka-Term-Regular.ttf");
        font(".fonts", "Iosevka.ttf");

        // 'Iosevka-Term-Regular.ttf' sorts first, so only the two-pass build gets this right.
        assertEquals("Iosevka.ttf", index().find("Iosevka", FontFamilyIndex.REGULAR).getName());
    }

    @Test
    public void anEmptyIndexAnswersNothing() {
        assertTrue(FontFamilyIndex.EMPTY.isEmpty());
        assertNull(FontFamilyIndex.EMPTY.find("Iosevka", FontFamilyIndex.REGULAR));
        assertTrue(FontFamilyIndex.of(Collections.singletonList(
            new File(home.getRoot(), "nowhere"))).isEmpty());
    }

    private FontFamilyIndex index() {
        List<File> roots = Arrays.asList(
            new File(home.getRoot(), ".termux/fonts"),
            new File(home.getRoot(), ".fonts"),
            new File(home.getRoot(), ".local/share/fonts"));
        return FontFamilyIndex.of(roots);
    }

    private void font(String directory, String name) throws Exception {
        File dir = new File(home.getRoot(), directory);
        assertTrue(dir.isDirectory() || dir.mkdirs());
        Files.write(new File(dir, name).toPath(), new byte[64]);
    }
}
