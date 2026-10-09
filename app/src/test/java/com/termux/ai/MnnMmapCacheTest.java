package com.termux.ai;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

/**
 * The pure, static parts of the MNN mmap weight cache's stale-cache defense: the fingerprint that
 * ties a cache directory to everything that should invalidate it (runtime build, app build, weight
 * file identity, load-affecting settings), and the pruning that keeps only the current fingerprint
 * on disk. See {@link MnnTaiRuntime#extraConfigJson} for why this exists at all.
 */
public class MnnMmapCacheTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    @Test
    public void fingerprint_isStableForTheSameInputs() {
        String a = MnnTaiRuntime.mmapFingerprint("3.6.1", 1000L, 500L, 2000L, "cpu", "low", "low", 4, "");
        String b = MnnTaiRuntime.mmapFingerprint("3.6.1", 1000L, 500L, 2000L, "cpu", "low", "low", 4, "");
        assertEquals(a, b);
        assertFalse(a.isEmpty());
    }

    @Test
    public void fingerprint_changesWithRuntimeVersion() {
        String a = MnnTaiRuntime.mmapFingerprint("3.6.1", 1000L, 500L, 2000L, "cpu", "low", "low", 4, "");
        String b = MnnTaiRuntime.mmapFingerprint("3.6.2", 1000L, 500L, 2000L, "cpu", "low", "low", 4, "");
        assertNotEquals(a, b);
    }

    @Test
    public void fingerprint_changesWithAppUpdateStamp() {
        String a = MnnTaiRuntime.mmapFingerprint("3.6.1", 1000L, 500L, 2000L, "cpu", "low", "low", 4, "");
        String b = MnnTaiRuntime.mmapFingerprint("3.6.1", 1001L, 500L, 2000L, "cpu", "low", "low", 4, "");
        assertNotEquals(a, b);
    }

    @Test
    public void fingerprint_changesWithWeightFileSizeOrMtime() {
        String base = MnnTaiRuntime.mmapFingerprint("3.6.1", 1000L, 500L, 2000L, "cpu", "low", "low", 4, "");
        String differentSize = MnnTaiRuntime.mmapFingerprint("3.6.1", 1000L, 501L, 2000L, "cpu", "low", "low", 4, "");
        String differentMtime = MnnTaiRuntime.mmapFingerprint("3.6.1", 1000L, 500L, 2001L, "cpu", "low", "low", 4, "");
        assertNotEquals(base, differentSize);
        assertNotEquals(base, differentMtime);
    }

    @Test
    public void fingerprint_changesWithEachLoadAffectingSetting() {
        String base = MnnTaiRuntime.mmapFingerprint("3.6.1", 1000L, 500L, 2000L, "cpu", "low", "low", 4, "");
        assertNotEquals(base, MnnTaiRuntime.mmapFingerprint("3.6.1", 1000L, 500L, 2000L, "opencl", "low", "low", 4, ""));
        assertNotEquals(base, MnnTaiRuntime.mmapFingerprint("3.6.1", 1000L, 500L, 2000L, "cpu", "high", "low", 4, ""));
        assertNotEquals(base, MnnTaiRuntime.mmapFingerprint("3.6.1", 1000L, 500L, 2000L, "cpu", "low", "high", 4, ""));
        assertNotEquals(base, MnnTaiRuntime.mmapFingerprint("3.6.1", 1000L, 500L, 2000L, "cpu", "low", "low", 8, ""));
        assertNotEquals(base, MnnTaiRuntime.mmapFingerprint("3.6.1", 1000L, 500L, 2000L, "cpu", "low", "low", 4, "eagle3"));
    }

    @Test
    public void pruneMmapCacheSiblings_keepsOnlyTheCurrentFingerprintDirectory() throws IOException {
        File modelRoot = tmp.newFolder("model-root");
        File current = new File(modelRoot, "aaaa1111");
        File stale = new File(modelRoot, "bbbb2222");
        assertTrue(current.mkdirs());
        assertTrue(stale.mkdirs());
        File staleFile = new File(stale, "0_0_0_0_0.static");
        assertTrue(staleFile.createNewFile());

        MnnTaiRuntime.pruneMmapCacheSiblings(modelRoot, "aaaa1111");

        assertTrue(current.isDirectory());
        assertFalse(stale.exists());
    }

    @Test
    public void pruneMmapCacheSiblings_deletesLegacyFlatFilesUnderTheModelRoot() throws IOException {
        File modelRoot = tmp.newFolder("legacy-model-root");
        File legacyWeight = new File(modelRoot, "0_0_0_0_0.static");
        File legacySync = new File(modelRoot, "0_0_0_0_sync.static");
        assertTrue(legacyWeight.createNewFile());
        assertTrue(legacySync.createNewFile());

        MnnTaiRuntime.pruneMmapCacheSiblings(modelRoot, "fresh-fingerprint");

        assertFalse(legacyWeight.exists());
        assertFalse(legacySync.exists());
    }

    @Test
    public void pruneMmapCacheSiblings_toleratesAMissingModelRoot() {
        File missing = new File(tmp.getRoot(), "does-not-exist");
        // Must not throw: the model may never have loaded on MNN before.
        MnnTaiRuntime.pruneMmapCacheSiblings(missing, "whatever");
    }
}
