package com.termux.app.chrome.wallpaper.living;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

/** Orphaned living folders go; anything referenced, unfinished or fresh stays. */
public class LivingStillPrunerTest {

    private static final long NOW = 10_000_000_000L;
    private static final long OLD = NOW - 3_600_000L;

    @Rule public TemporaryFolder tmp = new TemporaryFolder();
    private File root;

    @Before
    public void setUp() {
        root = new File(tmp.getRoot(), "living");
        assertTrue(root.mkdirs());
    }

    private File folder(String hash, boolean recipe, long modified) throws IOException {
        File dir = new File(root, hash);
        assertTrue(dir.mkdirs());
        assertTrue(new File(dir, "image.png").createNewFile());
        if (recipe) assertTrue(new File(dir, "recipe.json").createNewFile());
        for (File f : dir.listFiles()) assertTrue(f.setLastModified(modified));
        assertTrue(dir.setLastModified(modified));
        return dir;
    }

    private static Set<String> keep(String... hashes) {
        Set<String> s = new HashSet<>();
        for (String h : hashes) s.add(h);
        return s;
    }

    @Test
    public void anOrphanIsDeleted() throws IOException {
        File orphan = folder("aaaaaaaaaaaaaaaa", true, OLD);
        assertEquals(1, LivingStillPruner.prune(root, keep(), NOW));
        assertFalse(orphan.exists());
    }

    @Test
    public void referencedFoldersStay() throws IOException {
        File recent = folder("1111111111111111", true, OLD);
        File home = folder("2222222222222222", true, OLD);
        File lock = folder("3333333333333333", true, OLD);
        File pending = folder("4444444444444444", true, OLD);
        File orphan = folder("5555555555555555", true, OLD);
        int n = LivingStillPruner.prune(root,
            keep("1111111111111111", "2222222222222222", "3333333333333333", "4444444444444444"), NOW);
        assertEquals(1, n);
        assertTrue(recent.exists() && home.exists() && lock.exists() && pending.exists());
        assertFalse(orphan.exists());
    }

    @Test
    public void aFolderWithoutARecipeStays() throws IOException {
        File building = folder("6666666666666666", false, OLD);
        assertEquals(0, LivingStillPruner.prune(root, keep(), NOW));
        assertTrue(building.exists());
    }

    @Test
    public void aFreshFolderStays() throws IOException {
        File fresh = folder("7777777777777777", true, NOW - 60_000L);
        assertEquals(0, LivingStillPruner.prune(root, keep(), NOW));
        assertTrue(fresh.exists());
    }

    @Test
    public void nothingOutsideTheRootOrOddlyNamedIsTouched() throws IOException {
        File other = new File(tmp.getRoot(), "keepme");
        assertTrue(other.mkdirs());
        File odd = folder("not-a-hash", true, OLD);
        assertEquals(0, LivingStillPruner.prune(root, keep(), NOW));
        assertTrue(other.exists() && odd.exists());
    }
}
