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

        // Four examples plus two seeded live files.
        assertEquals(6, TermuxLauncherConfigInstaller.install(context, home));

        File examples = new File(home, TermuxLauncherConfigInstaller.EXAMPLES_RELATIVE_PATH);
        assertTrue(new File(examples, "README.md").isFile());
        assertTrue(new File(examples, "termux-launcher-bindings.conf").isFile());
        assertTrue(new File(examples, "fonts.conf").isFile());
        assertTrue(new File(examples, "keyboard-layout.xml").isFile());

        File bindings = new File(home, "termux-launcher-bindings.conf");
        File fonts = new File(home, "fonts.conf");
        assertTrue(bindings.isFile());
        assertTrue(fonts.isFile());
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
        assertEquals(6, TermuxLauncherConfigInstaller.install(context, home));

        for (String name : new String[] {"termux-launcher-bindings.conf", "fonts.conf"}) {
            for (String line : readFile(new File(home, name)).split("\n", -1)) {
                assertTrue(name + " ships an active directive: " + line,
                    line.trim().isEmpty() || line.trim().startsWith("#"));
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

    private static File home(String label) {
        Context context = ApplicationProvider.getApplicationContext();
        return new File(context.getCacheDir(), "termux-data-" + label + "-" + System.nanoTime());
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
