package com.termux.shared.file;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.attribute.BasicFileAttributeView;
import java.nio.file.attribute.FileTime;

public class OlderThanSweeperTest {

    private static final long NOW = 10_000_000_000L;
    private static final long AGE = 60_000L;
    private static final long OLD = NOW - AGE - 1000;
    private static final long NEW = NOW - 1000;

    @Rule public TemporaryFolder tmp = new TemporaryFolder();

    private File file(File dir, String name, int bytes, long mtime) throws IOException {
        File f = new File(dir, name);
        Files.write(f.toPath(), new byte[bytes]);
        assertTrue(f.setLastModified(mtime));
        return f;
    }

    private File dir(File parent, String name) {
        File d = new File(parent, name);
        assertTrue(d.mkdirs());
        return d;
    }

    @Test public void oldFilesGoNewFilesStay() throws IOException {
        File root = tmp.newFolder();
        File old = file(root, "old", 10, OLD);
        File fresh = file(root, "new", 5, NEW);
        OlderThanSweeper.Result r = OlderThanSweeper.sweep(root, AGE, NOW);
        assertFalse(old.exists());
        assertTrue(fresh.exists());
        assertTrue(root.exists());
        assertEquals(1, r.entriesDeleted);
        assertEquals(10, r.bytesFreed);
    }

    @Test public void dirWithOnlyOldEntriesGoes() throws IOException {
        File root = tmp.newFolder();
        File d = dir(root, "d");
        file(d, "a", 7, OLD);
        file(d, "b", 3, OLD);
        d.setLastModified(OLD);
        OlderThanSweeper.Result r = OlderThanSweeper.sweep(root, AGE, NOW);
        assertFalse(d.exists());
        assertEquals(3, r.entriesDeleted);
        assertEquals(10, r.bytesFreed);
    }

    @Test public void dirHoldingNewFileStaysWithIt() throws IOException {
        File root = tmp.newFolder();
        File d = dir(root, "d");
        File old = file(d, "a", 7, OLD);
        File fresh = file(d, "b", 3, NEW);
        d.setLastModified(OLD);
        OlderThanSweeper.Result r = OlderThanSweeper.sweep(root, AGE, NOW);
        assertTrue(d.exists());
        assertTrue(fresh.exists());
        assertFalse(old.exists());
        assertEquals(1, r.entriesDeleted);
        assertEquals(7, r.bytesFreed);
    }

    @Test public void newDirWithOldEntriesStays() throws IOException {
        File root = tmp.newFolder();
        File d = dir(root, "d");
        file(d, "a", 7, OLD);
        d.setLastModified(NEW);
        OlderThanSweeper.sweep(root, AGE, NOW);
        assertTrue(d.exists());
    }

    @Test public void oldLinkToNewTargetIsDeletedTargetKept() throws IOException {
        File root = tmp.newFolder();
        File target = file(tmp.newFolder(), "t", 100, NEW);
        File link = new File(root, "link");
        Files.createSymbolicLink(link.toPath(), target.toPath());
        setLinkTime(link, OLD);
        OlderThanSweeper.Result r = OlderThanSweeper.sweep(root, AGE, NOW);
        assertFalse(Files.exists(link.toPath(), LinkOption.NOFOLLOW_LINKS));
        assertTrue(target.exists());
        assertEquals(1, r.entriesDeleted);
        assertEquals(0, r.bytesFreed);
    }

    @Test public void newLinkToOldTargetStays() throws IOException {
        File root = tmp.newFolder();
        File target = file(tmp.newFolder(), "t", 100, OLD);
        File link = new File(root, "link");
        Files.createSymbolicLink(link.toPath(), target.toPath());
        setLinkTime(link, NEW);
        OlderThanSweeper.sweep(root, AGE, NOW);
        assertTrue(Files.isSymbolicLink(link.toPath()));
        assertTrue(target.exists());
    }

    @Test public void oldLinkToOldTargetDeletesOnlyTheLink() throws IOException {
        File root = tmp.newFolder();
        File target = file(tmp.newFolder(), "t", 100, OLD);
        File link = new File(root, "link");
        Files.createSymbolicLink(link.toPath(), target.toPath());
        setLinkTime(link, OLD);
        OlderThanSweeper.sweep(root, AGE, NOW);
        assertFalse(Files.exists(link.toPath(), LinkOption.NOFOLLOW_LINKS));
        assertTrue(target.exists());
    }

    @Test public void linkToDirectoryIsNotEntered() throws IOException {
        File root = tmp.newFolder();
        File outside = tmp.newFolder();
        File inner = file(outside, "x", 4, OLD);
        File link = new File(root, "dl");
        Files.createSymbolicLink(link.toPath(), outside.toPath());
        setLinkTime(link, OLD);
        OlderThanSweeper.sweep(root, AGE, NOW);
        assertFalse(Files.exists(link.toPath(), LinkOption.NOFOLLOW_LINKS));
        assertTrue(inner.exists());
        assertTrue(outside.exists());
    }

    @Test public void symlinkedRootIsNotSwept() throws IOException {
        File real = tmp.newFolder();
        File old = file(real, "old", 10, OLD);
        File rootLink = new File(tmp.getRoot(), "rootlink");
        Files.createSymbolicLink(rootLink.toPath(), real.toPath());
        OlderThanSweeper.Result r = OlderThanSweeper.sweep(rootLink, AGE, NOW);
        assertTrue(old.exists());
        assertEquals(0, r.entriesDeleted);
    }

    @Test public void missingRootIsEmptyResult() {
        OlderThanSweeper.Result r = OlderThanSweeper.sweep(new File(tmp.getRoot(), "nope"), AGE, NOW);
        assertEquals(0, r.entriesDeleted);
        assertEquals(0, r.bytesFreed);
    }

    private static void setLinkTime(File link, long millis) throws IOException {
        BasicFileAttributeView v = Files.getFileAttributeView(
            link.toPath(), BasicFileAttributeView.class, LinkOption.NOFOLLOW_LINKS);
        FileTime t = FileTime.fromMillis(millis);
        v.setTimes(t, t, null);
    }

    @Test public void sizeOfCountsRegularFilesRecursively() throws IOException {
        File root = tmp.newFolder();
        file(root, "a", 10, NEW);
        file(dir(root, "d"), "b", 5, NEW);
        assertEquals(15, OlderThanSweeper.sizeOf(root));
    }

    @Test public void sizeOfDoesNotFollowSymlinks() throws IOException {
        File outside = tmp.newFolder();
        file(outside, "big", 100, NEW);
        File root = tmp.newFolder();
        file(root, "a", 10, NEW);
        Files.createSymbolicLink(new File(root, "dirLink").toPath(), outside.toPath());
        Files.createSymbolicLink(new File(root, "fileLink").toPath(), new File(outside, "big").toPath());
        assertEquals(10, OlderThanSweeper.sizeOf(root));
    }

    @Test public void sizeOfASymlinkedRootIsZero() throws IOException {
        File real = tmp.newFolder();
        file(real, "a", 10, NEW);
        File link = new File(tmp.newFolder(), "link");
        Files.createSymbolicLink(link.toPath(), real.toPath());
        assertEquals(0, OlderThanSweeper.sizeOf(link));
    }

    @Test public void sizeOfAMissingRootIsZero() throws IOException {
        assertEquals(0, OlderThanSweeper.sizeOf(new File(tmp.newFolder(), "missing")));
    }
}
