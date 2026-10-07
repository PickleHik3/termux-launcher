package com.termux.app.chrome.wallpaper;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;

/** The last three applied photos: add, dedupe by content, trim to three, order, missing files. */
public class RecentWallpapersTest {

    @Rule public TemporaryFolder tmp = new TemporaryFolder();

    private File picture(String name, String content) throws IOException {
        File f = new File(tmp.getRoot(), name);
        try (FileOutputStream out = new FileOutputStream(f)) {
            out.write(content.getBytes(StandardCharsets.UTF_8));
        }
        return f;
    }

    private RecentWallpapers store() {
        return new RecentWallpapers(new File(tmp.getRoot(), "recent"));
    }

    private static String read(File f) throws IOException {
        return new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
    }

    @Test
    public void anEmptyStoreListsNothing() {
        assertTrue(store().list().isEmpty());
    }

    @Test
    public void addKeepsACopyNewestFirst() throws IOException {
        RecentWallpapers store = store();
        File a = picture("a.png", "alpha");
        File b = picture("b.png", "bravo");
        File keptA = store.add(a, 1000);
        File keptB = store.add(b, 2000);
        List<File> list = store.list();
        assertEquals(2, list.size());
        assertEquals(keptB, list.get(0));
        assertEquals(keptA, list.get(1));
        assertEquals("a copy, not the source", "alpha", read(list.get(1)));
        assertFalse(keptA.equals(a));
        assertEquals("1000.png", keptA.getName());
        // The source may go; the copy stays.
        assertTrue(a.delete());
        assertEquals(2, store.list().size());
    }

    @Test
    public void theSameBytesMoveToTheFrontWithoutACopy() throws IOException {
        RecentWallpapers store = store();
        File first = store.add(picture("a.png", "alpha"), 1000);
        store.add(picture("b.png", "bravo"), 2000);
        File again = store.add(picture("a-again.png", "alpha"), 3000);
        assertEquals("the kept copy is reused", first, again);
        List<File> list = store.list();
        assertEquals(2, list.size());
        assertEquals(first, list.get(0));
        // Adding a kept copy itself (a recent tile applied again) is the same thing.
        assertEquals(list.get(1), store.add(list.get(1), 4000));
        assertEquals(2, store.list().size());
    }

    @Test
    public void trimsToFiveAndDeletesTheOldest() throws IOException {
        RecentWallpapers store = store();
        File oldest = store.add(picture("1.png", "one"), 1000);
        store.add(picture("2.png", "two"), 2000);
        store.add(picture("3.png", "three"), 3000);
        store.add(picture("4.png", "four"), 4000);
        store.add(picture("5.png", "five"), 5000);
        store.add(picture("6.png", "six"), 6000);
        List<File> list = store.list();
        assertEquals(5, RecentWallpapers.MAX);
        assertEquals(RecentWallpapers.MAX, list.size());
        assertEquals("six", read(list.get(0)));
        assertEquals("two", read(list.get(4)));
        assertFalse("the oldest copy is deleted", oldest.exists());
        File[] files = store.directory().listFiles();
        assertEquals("five pictures and the index", RecentWallpapers.MAX + 1, files == null ? 0 : files.length);
    }

    @Test
    public void oldTwoAndThreeTokenIndexLinesStillParse() throws IOException {
        RecentWallpapers store = store();
        File plain = store.add(picture("p.png", "plain"), 1000);
        File older = store.add(picture("l.png", "alive"), 2000);
        Files.write(new File(store.directory(), "index").toPath(),
            (plain.getName() + " abc\n" + older.getName() + " def living\n").getBytes(StandardCharsets.UTF_8));
        assertEquals(2, store.entries().size());
        assertEquals("abc", store.entries().get(0).hash);
        assertEquals("def", store.entries().get(1).hash);
    }

    @Test
    public void aMissingFileIsSkipped() throws IOException {
        RecentWallpapers store = store();
        File a = store.add(picture("a.png", "alpha"), 1000);
        File b = store.add(picture("b.png", "bravo"), 2000);
        assertTrue(a.delete());
        List<File> list = store.list();
        assertEquals(1, list.size());
        assertEquals(b, list.get(0));
        // The next add drops the gone entry and still keeps three at most.
        store.add(picture("c.png", "charlie"), 3000);
        store.add(picture("d.png", "delta"), 4000);
        list = store.list();
        assertEquals(3, list.size());
        assertEquals("delta", read(list.get(0)));
        assertEquals("bravo", read(list.get(2)));
    }

    @Test
    public void aClashingTimestampGetsTheNextName() throws IOException {
        RecentWallpapers store = store();
        File a = store.add(picture("a.png", "alpha"), 1000);
        File b = store.add(picture("b.png", "bravo"), 1000);
        assertFalse(a.equals(b));
        assertEquals(2, store.list().size());
    }

    @Test(expected = IOException.class)
    public void aMissingSourceIsRefused() throws IOException {
        store().add(new File(tmp.getRoot(), "nope.png"), 1000);
    }
}
