package com.termux.app.terminal;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.TimeZone;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/** Naming and pruning of the folders clipboard pictures are kept in. */
public class ClipboardFilesTest {

    @Rule public TemporaryFolder tmp = new TemporaryFolder();

    private static final TimeZone UTC = TimeZone.getTimeZone("UTC");

    /** 2026-10-12 08:09:10 UTC. */
    private static final long NOW = 1791792550_000L;

    @Test
    public void extensionsFollowTheType() {
        assertEquals("png", ClipboardFiles.extensionFor("image/png"));
        assertEquals("jpg", ClipboardFiles.extensionFor("IMAGE/JPEG"));
        assertEquals("webp", ClipboardFiles.extensionFor("image/webp"));
        assertEquals("gif", ClipboardFiles.extensionFor("image/gif; charset=binary"));
        assertEquals("svg", ClipboardFiles.extensionFor("image/svg+xml"));
        assertEquals("jxl", ClipboardFiles.extensionFor("image/jxl"));
        assertEquals("img", ClipboardFiles.extensionFor("image/x-weird.thing"));
        assertEquals("img", ClipboardFiles.extensionFor(null));
    }

    @Test
    public void namesCarryTheMomentAndCountUpWithinASecond() throws IOException {
        File dir = tmp.newFolder();
        assertEquals("clip-20261012-080910", ClipboardFiles.baseName(NOW, UTC));
        File first = ClipboardFiles.newFile(dir, NOW, UTC, "png");
        assertEquals("clip-20261012-080910.png", first.getName());
        assertTrue(first.createNewFile());
        File second = ClipboardFiles.newFile(dir, NOW + 500, UTC, "png");
        assertEquals("clip-20261012-080910-2.png", second.getName());
        assertTrue(second.createNewFile());
        assertEquals("clip-20261012-080910-3.png", ClipboardFiles.newFile(dir, NOW, UTC, "png").getName());
        // Another extension is another name.
        assertEquals("clip-20261012-080910.jpg", ClipboardFiles.newFile(dir, NOW, UTC, "jpg").getName());
    }

    @Test
    public void saveCopiesThePictureAndKeepsOnlyTheNewest() throws IOException {
        File dir = new File(tmp.getRoot(), "clipboard"); // Made on the first save.
        File other = null;
        for (int i = 0; i < 4; i++) {
            File file = ClipboardFiles.save(dir, NOW + i * 1000L, "image/png",
                new ByteArrayInputStream(new byte[] {(byte) i}), 1024, 3);
            assertTrue(file.setLastModified(NOW + i * 1000L));
            if (i == 0) {
                other = new File(dir, "notes.txt");
                assertTrue(other.createNewFile());
            }
        }
        String[] names = dir.list();
        Arrays.sort(names);
        assertEquals(4, names.length); // Three pictures and the file that is not ours.
        assertTrue(other.exists());
        File newest = new File(dir, names[2]);
        assertArrayEquals(new byte[] {3}, Files.readAllBytes(newest.toPath()));
    }

    @Test
    public void theFileJustSavedSurvivesAnOldClock() throws IOException {
        File dir = tmp.newFolder();
        for (int i = 0; i < 3; i++) {
            File file = new File(dir, "clip-old-" + i + ".png");
            assertTrue(file.createNewFile());
            assertTrue(file.setLastModified(NOW + 10_000 + i));
        }
        File saved = new File(dir, "clip-new.png");
        assertTrue(saved.createNewFile());
        assertTrue(saved.setLastModified(NOW));
        ClipboardFiles.prune(dir, 2, saved);
        assertTrue(saved.exists());
        assertTrue(new File(dir, "clip-old-2.png").exists());
        assertFalse(new File(dir, "clip-old-1.png").exists());
        assertFalse(new File(dir, "clip-old-0.png").exists());
    }

    @Test
    public void anOversizedPictureLeavesNothingBehind() throws IOException {
        File dir = tmp.newFolder();
        try {
            ClipboardFiles.save(dir, NOW, "image/png", new ByteArrayInputStream(new byte[100]), 99, 5);
            fail("saved a picture over the limit");
        } catch (IOException expected) {
            // Refused.
        }
        assertEquals(0, dir.list().length);
    }
}
