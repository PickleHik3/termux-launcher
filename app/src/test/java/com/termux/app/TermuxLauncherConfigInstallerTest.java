package com.termux.app;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
public class TermuxLauncherConfigInstallerTest {

    @Test
    public void installWritesExamplesAndSeedsLiveFilesOnce() throws Exception {
        Context context = ApplicationProvider.getApplicationContext();
        File home = home("fresh");

        // Five examples plus three seeded live files.
        assertEquals(8, TermuxLauncherConfigInstaller.install(context, home));

        File examples = new File(home, TermuxLauncherConfigInstaller.EXAMPLES_RELATIVE_PATH);
        assertTrue(new File(examples, "README.md").isFile());
        assertTrue(new File(examples, "termux-launcher-bindings.conf").isFile());
        assertTrue(new File(examples, "fonts.conf").isFile());
        assertTrue(new File(examples, "keyboard-layout.xml").isFile());
        assertTrue(new File(examples, "termux.properties").isFile());

        File bindings = new File(home, "termux-launcher-bindings.conf");
        File fonts = new File(home, "fonts.conf");
        File properties = new File(home, "termux.properties");
        assertTrue(bindings.isFile());
        assertTrue(fonts.isFile());
        assertTrue(properties.isFile());
        assertTrue("the seeded properties file documents terminal-term",
            readFile(properties).contains("terminal-term"));
        assertFalse(bindings.canExecute());

        // The keyboard layout is never seeded: it would replace the bundled layout.
        assertFalse(new File(home, "keyboard/layout.xml").exists());

        long bindingsModified = bindings.lastModified();
        assertEquals(0, TermuxLauncherConfigInstaller.install(context, home));
        assertEquals(bindingsModified, bindings.lastModified());
    }

    @Test
    public void seededFilesActivateNothingAndUserEditsSurvive() throws Exception {
        Context context = ApplicationProvider.getApplicationContext();
        File home = home("edits");
        assertEquals(8, TermuxLauncherConfigInstaller.install(context, home));

        // The one directive the seed may activate: tap-to-open URLs is this fork's default for
        // fresh installs. Anything else live in a seeded file is behavior slipped in unreviewed.
        String allowedLive = "terminal-onclick-url-open = true";
        for (String name : new String[] {"termux-launcher-bindings.conf", "fonts.conf", "termux.properties"}) {
            for (String line : readFile(new File(home, name)).split("\n", -1)) {
                assertTrue(name + " ships an active directive: " + line,
                    line.trim().isEmpty() || line.trim().startsWith("#")
                        || ("termux.properties".equals(name) && line.trim().equals(allowedLive)));
            }
        }

        writeFile(new File(home, "termux-launcher-bindings.conf"), "map ctrl+alt+w app.launch com.whatsapp\n");
        assertEquals(0, TermuxLauncherConfigInstaller.install(context, home));
        assertEquals("map ctrl+alt+w app.launch com.whatsapp\n",
            readFile(new File(home, "termux-launcher-bindings.conf")));
    }

    @Test
    public void staleExampleIsRefreshedWhileUnrelatedFilesAreKept() throws Exception {
        Context context = ApplicationProvider.getApplicationContext();
        File home = home("stale");
        File examples = new File(home, TermuxLauncherConfigInstaller.EXAMPLES_RELATIVE_PATH);
        assertTrue(examples.mkdirs());
        File fonts = new File(examples, "fonts.conf");
        File custom = new File(examples, "my-notes.txt");
        writeFile(fonts, "stale");
        writeFile(custom, "user-owned");

        TermuxLauncherConfigInstaller.install(context, home);

        assertTrue(readFile(fonts).contains("symbol_map"));
        assertEquals("user-owned", readFile(custom));
    }

    @Test
    public void propertiesAreNotSeededOverASecondaryPropertiesFile() throws Exception {
        Context context = ApplicationProvider.getApplicationContext();
        File home = home("secondary");
        // Only the first readable properties file is read, so seeding ~/.termux/termux.properties
        // here would disable the one the user keeps under ~/.config/termux.
        File secondary = new File(home.getParentFile(), ".config/termux/termux.properties");
        assertTrue(secondary.getParentFile().mkdirs());
        writeFile(secondary, "terminal-cursor-style = block\n");

        // Five examples plus only two of the three seeded files.
        assertEquals(7, TermuxLauncherConfigInstaller.install(context, home));

        assertFalse(new File(home, "termux.properties").exists());
        assertEquals("terminal-cursor-style = block\n", readFile(secondary));
        // The reference copy still lands, so the documentation is reachable either way.
        assertTrue(new File(new File(home, TermuxLauncherConfigInstaller.EXAMPLES_RELATIVE_PATH),
            "termux.properties").isFile());
    }

    /**
     * A fake {@code ~/.termux}. Nested under a per-test fake home so a sibling such as
     * {@code ~/.config/termux} belongs to one test only.
     */
    private static File home(String label) {
        Context context = ApplicationProvider.getApplicationContext();
        File fakeHome = new File(context.getCacheDir(), "termux-home-" + label + "-" + System.nanoTime());
        return new File(fakeHome, ".termux");
    }

    private static String readFile(File file) throws IOException {
        byte[] content = new byte[(int) file.length()];
        try (FileInputStream input = new FileInputStream(file)) {
            int offset = 0;
            while (offset < content.length) {
                int count = input.read(content, offset, content.length - offset);
                if (count == -1) break;
                offset += count;
            }
        }
        return new String(content, StandardCharsets.UTF_8);
    }

    private static void writeFile(File file, String content) throws IOException {
        try (FileOutputStream output = new FileOutputStream(file)) {
            output.write(content.getBytes(StandardCharsets.UTF_8));
        }
    }
}
