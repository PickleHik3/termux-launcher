package com.termux.app.colours;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;

public class TerminalColoursTest {

    @Rule public TemporaryFolder folder = new TemporaryFolder();

    private File mFile;
    private final List<String> mEvents = new ArrayList<>();
    private boolean mWallpaper = true;
    private TerminalColours mColours;

    @Before
    public void setUp() {
        mFile = new File(folder.getRoot(), "colors.properties");
        ColourSchemeFile file = new ColourSchemeFile(mFile) ;
        mColours = new TerminalColours(new TerminalColours.Source() {
            @Override public boolean fromWallpaper() {
                return mWallpaper;
            }

            @Override public void setFromWallpaper(boolean on) {
                // What the file held at the moment Wallpaper colours changed.
                mEvents.add("wallpaper=" + on + " file=" + mFile.exists());
                mWallpaper = on;
            }
        }, file);
    }

    private String text(File file) throws IOException {
        return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
    }

    private Properties load() throws IOException {
        Properties props = new Properties();
        try (FileInputStream in = new FileInputStream(mFile)) {
            props.load(in);
        }
        return props;
    }

    @Test
    public void wallpaperColoursAreOnUntilASchemeIsPicked() {
        assertEquals(TerminalColours.WALLPAPER, mColours.current());
        assertFalse(mFile.exists());
    }

    @Test
    public void pickingASchemeTurnsWallpaperColoursOffBeforeTheFileIsWritten() throws IOException {
        assertTrue(mColours.choose("dracula"));

        assertEquals("off first, while no file stood there", "wallpaper=false file=false", mEvents.get(0));
        assertEquals("dracula", mColours.current());
        Properties props = load();
        assertEquals("#282a36", props.getProperty("background"));
        assertEquals("#f8f8f2", props.getProperty("foreground"));
        assertEquals("#ff5555", props.getProperty("color1"));
        assertEquals("#ffffff", props.getProperty("color15"));
    }

    @Test
    public void everySchemeHasTheTerminalsKeysAndALicence() {
        Set<String> ids = new HashSet<>();
        for (TerminalSchemes.Scheme scheme : TerminalSchemes.all()) {
            assertTrue(scheme.id, ids.add(scheme.id));
            assertEquals(scheme.id, 19, scheme.colours.size());
            for (String colour : scheme.colours.values()) {
                assertTrue(scheme.id + " " + colour, colour.matches("#[0-9a-f]{6}"));
            }
            assertFalse(scheme.licence.isEmpty());
            assertTrue(scheme.home.startsWith("https://"));
        }
    }

    @Test
    public void aFileTheAppDidNotWriteIsKeptAndComesBack() throws IOException {
        mWallpaper = false;
        Files.write(mFile.toPath(), "background=#123456\n".getBytes(StandardCharsets.UTF_8));
        assertEquals(TerminalColours.OWN, mColours.current());
        assertTrue(mColours.hasOwn());

        mColours.choose("nord");
        mColours.choose("gruvbox_dark");
        File backup = new File(folder.getRoot(), "colors.properties.bak");
        assertEquals("kept once, before the first overwrite", "background=#123456\n", text(backup));
        assertFalse(new File(folder.getRoot(), "colors.properties.bak2").exists());
        assertEquals("gruvbox_dark", mColours.current());
        assertTrue(mColours.hasOwn());

        mColours.choose(TerminalColours.OWN);
        assertEquals("background=#123456\n", text(mFile));
        assertFalse(backup.exists());
        assertEquals(TerminalColours.OWN, mColours.current());
        assertTrue("no Wallpaper colours change while off", mEvents.isEmpty());
    }

    @Test
    public void anEarlierBackupIsNeverWrittenOver() throws IOException {
        mWallpaper = false;
        File old = new File(folder.getRoot(), "colors.properties.bak");
        Files.write(old.toPath(), "old\n".getBytes(StandardCharsets.UTF_8));
        Files.write(mFile.toPath(), "mine\n".getBytes(StandardCharsets.UTF_8));

        mColours.choose("nord");
        assertEquals("old\n", text(old));
        assertEquals("mine\n", text(new File(folder.getRoot(), "colors.properties.bak2")));
        mColours.choose(TerminalColours.OWN);
        assertEquals("mine\n", text(mFile));
    }

    @Test
    public void ownWithNoFileBeforeTakesTheAppsFileAway() {
        mColours.choose("nord");
        assertFalse("nothing of the user's to go back to", mColours.hasOwn());
        mColours.choose(TerminalColours.OWN);
        assertFalse(mFile.exists());
        assertEquals(TerminalColours.OWN, mColours.current());
    }

    @Test
    public void wallpaperColoursLeaveTheFileAsItIs() throws IOException {
        mColours.choose("tokyo_night");
        String written = text(mFile);
        mColours.choose(TerminalColours.WALLPAPER);
        assertEquals(TerminalColours.WALLPAPER, mColours.current());
        assertEquals(written, text(mFile));
        assertEquals("wallpaper=true file=true", mEvents.get(mEvents.size() - 1));
    }

    @Test
    public void anUnknownNameChangesNothing() {
        assertFalse(mColours.choose("not-a-scheme"));
        assertEquals(TerminalColours.WALLPAPER, mColours.current());
        assertTrue(mEvents.isEmpty());
        assertNull(TerminalSchemes.of("not-a-scheme"));
    }
}
