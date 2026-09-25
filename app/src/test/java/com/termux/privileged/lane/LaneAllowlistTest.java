package com.termux.privileged.lane;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Plain JUnit on a temporary "files dir": the path rules (inside the files dir after
 * canonicalisation, a regular file, a symlink that escapes is refused) and the digest gate
 * against a catalog, plus the mtime+size cache that spares a re-hash.
 */
public class LaneAllowlistTest {

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    private File filesDir;
    private File binDir;
    private File baseCatalog;
    private File userCatalog;

    @Before
    public void setUp() throws IOException {
        filesDir = folder.newFolder("files");
        binDir = new File(filesDir, "home/.local/bin");
        assertTrue(binDir.mkdirs());
        baseCatalog = new File(folder.getRoot(), "catalog.tsv");
        userCatalog = new File(folder.getRoot(), "user-catalog.tsv");  // absent unless a test writes it
    }

    private static String row(String name, String kind, String digest, String options) {
        return String.join("\t", name, kind, "1.0", "com.termux", "binaries:" + name + "@1.0",
            digest, "~/.local/bin/" + name, "-", options, "A tool.", "System") + "\n";
    }

    private File writeBinary(String name, String contents) throws IOException {
        File file = new File(binDir, name);
        Files.write(file.toPath(), contents.getBytes(StandardCharsets.UTF_8));
        return file;
    }

    private void writeCatalog(File catalog, String... rows) throws IOException {
        Files.write(catalog.toPath(), ("# tlstore catalog\tserial=1\n" + String.join("", rows)).getBytes(StandardCharsets.UTF_8));
    }

    @Test
    public void isUnderIsAComponentWisePrefixTest() {
        assertTrue(LaneAllowlist.isUnder("/data/data/com.termux/files/home/.local/bin/btop", "/data/data/com.termux/files"));
        assertTrue(LaneAllowlist.isUnder("/data/data/com.termux/files", "/data/data/com.termux/files"));
        assertTrue(LaneAllowlist.isUnder("/data/data/com.termux/files/x", "/data/data/com.termux/files/"));
        assertFalse("/a/bc is not under /a/b", LaneAllowlist.isUnder("/data/data/com.termux/files2/x", "/data/data/com.termux/files"));
        assertFalse(LaneAllowlist.isUnder("/data/data/com.termux", "/data/data/com.termux/files"));
        assertFalse(LaneAllowlist.isUnder("/system/bin/sh", "/data/data/com.termux/files"));
        assertTrue(LaneAllowlist.isUnder("/anything", "/"));
    }

    @Test
    public void aCatalogBinaryInsideTheFilesDirResolvesToItsRow() throws Exception {
        File btop = writeBinary("btop", "#!/bin/false\nfake btop\n");
        String digest = LaneAllowlist.sha256(btop);
        writeCatalog(baseCatalog, row("btop", "binary", digest, "priv=shizuku"));

        LaneAllowlist allowlist = new LaneAllowlist(filesDir, baseCatalog, userCatalog);
        LaneAllowlist.Resolved resolved = allowlist.resolve(btop.getPath());
        assertEquals(btop.getCanonicalFile(), resolved.file);
        assertEquals(digest, resolved.digest);
        assertEquals("btop", resolved.name);
    }

    @Test
    public void theStagingNameIsTheRowsNameNotTheFilesName() throws Exception {
        File renamed = writeBinary("top-but-better", "payload\n");
        writeCatalog(baseCatalog, row("btop", "binary", LaneAllowlist.sha256(renamed), "priv=shizuku"));
        assertEquals("btop", new LaneAllowlist(filesDir, baseCatalog, userCatalog).resolve(renamed.getPath()).name);
    }

    @Test
    public void aFileOutsideTheFilesDirIsRefusedEvenViaDotDot() throws Exception {
        File outside = new File(folder.getRoot(), "elsewhere");
        Files.write(outside.toPath(), "x".getBytes(StandardCharsets.UTF_8));
        writeCatalog(baseCatalog, row("btop", "binary", LaneAllowlist.sha256(outside), "priv=shizuku"));
        LaneAllowlist allowlist = new LaneAllowlist(filesDir, baseCatalog, userCatalog);

        assertRefused(allowlist, outside.getPath(), "only a file under");
        assertRefused(allowlist, new File(binDir, "../../../../elsewhere").getPath(), "only a file under");
    }

    @Test
    public void aSymlinkThatEscapesTheFilesDirIsRefused() throws Exception {
        File outside = new File(folder.getRoot(), "real-btop");
        Files.write(outside.toPath(), "x".getBytes(StandardCharsets.UTF_8));
        File link = new File(binDir, "btop");
        try {
            Files.createSymbolicLink(link.toPath(), outside.toPath());
        } catch (UnsupportedOperationException | IOException cannotLink) {
            return;  // A filesystem without symlinks has nothing to escape with.
        }
        writeCatalog(baseCatalog, row("btop", "binary", LaneAllowlist.sha256(outside), "priv=shizuku"));
        assertRefused(new LaneAllowlist(filesDir, baseCatalog, userCatalog), link.getPath(), "only a file under");
    }

    @Test
    public void aSymlinkThatStaysInsideResolvesToItsTarget() throws Exception {
        File real = writeBinary("btop-2.0", "x");
        File link = new File(binDir, "btop");
        try {
            Files.createSymbolicLink(link.toPath(), real.toPath());
        } catch (UnsupportedOperationException | IOException cannotLink) {
            return;
        }
        writeCatalog(baseCatalog, row("btop", "binary", LaneAllowlist.sha256(real), "priv=shizuku"));
        assertEquals(real.getCanonicalFile(),
            new LaneAllowlist(filesDir, baseCatalog, userCatalog).resolve(link.getPath()).file);
    }

    @Test
    public void directoriesAndMissingFilesAreRefused() throws Exception {
        writeCatalog(baseCatalog);
        LaneAllowlist allowlist = new LaneAllowlist(filesDir, baseCatalog, userCatalog);
        assertRefused(allowlist, binDir.getPath(), "not a regular file");
        assertRefused(allowlist, new File(binDir, "nothing").getPath(), "not a regular file");
    }

    @Test
    public void aBinaryTheCatalogDoesNotMarkPrivilegedIsRefused() throws Exception {
        File fastfetch = writeBinary("fastfetch", "ff");
        File btop = writeBinary("btop", "bt");
        writeCatalog(baseCatalog,
            row("fastfetch", "binary", LaneAllowlist.sha256(fastfetch), "host=launcher"),
            row("btop", "binary", "0000000000000000000000000000000000000000000000000000000000000000", "priv=shizuku"));
        LaneAllowlist allowlist = new LaneAllowlist(filesDir, baseCatalog, userCatalog);
        assertRefused(allowlist, fastfetch.getPath(), "priv=shizuku");
        assertRefused(allowlist, btop.getPath(), "priv=shizuku");  // right name, wrong bytes
    }

    @Test
    public void aNewerUserCatalogIsTheOneConsulted() throws Exception {
        File btop = writeBinary("btop", "new build");
        String digest = LaneAllowlist.sha256(btop);
        writeCatalog(baseCatalog, row("btop", "binary", "1111111111111111111111111111111111111111111111111111111111111111", "priv=shizuku"));
        Files.write(userCatalog.toPath(), ("# tlstore catalog\tserial=2\n" + row("btop", "binary", digest, "priv=shizuku")).getBytes(StandardCharsets.UTF_8));
        assertEquals(digest, new LaneAllowlist(filesDir, baseCatalog, userCatalog).resolve(btop.getPath()).digest);
    }

    @Test
    public void theDigestIsCachedByMtimeAndSizeAndRecomputedWhenTheFileChanges() throws Exception {
        File btop = writeBinary("btop", "version one");
        String first = LaneAllowlist.sha256(btop);
        writeCatalog(baseCatalog, row("btop", "binary", first, "priv=shizuku"));
        LaneAllowlist allowlist = new LaneAllowlist(filesDir, baseCatalog, userCatalog);

        allowlist.resolve(btop.getPath());
        assertEquals(first, allowlist.cachedDigest(btop.getCanonicalFile()));

        // Same size, different bytes, and an mtime a whole second later so a coarse filesystem
        // clock still sees the change.
        Files.write(btop.toPath(), "version two".getBytes(StandardCharsets.UTF_8));
        assertTrue(btop.setLastModified(btop.lastModified() + 5_000));
        String second = LaneAllowlist.sha256(btop);
        writeCatalog(baseCatalog, row("btop", "binary", second, "priv=shizuku"));
        assertEquals(second, allowlist.resolve(btop.getPath()).digest);
        assertEquals(second, allowlist.cachedDigest(btop.getCanonicalFile()));
    }

    @Test
    public void sha256MatchesAKnownVector() throws Exception {
        File abc = writeBinary("abc", "abc");
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", LaneAllowlist.sha256(abc));
        assertNotNull(LaneAllowlist.toHex(new byte[0]));
        assertEquals("00ff", LaneAllowlist.toHex(new byte[] { 0, (byte) 0xff }));
    }

    private static void assertRefused(LaneAllowlist allowlist, String path, String messageFragment) throws IOException {
        try {
            allowlist.resolve(path);
            fail("expected refusal of " + path);
        } catch (LaneRequest.Refused e) {
            assertTrue("message '" + e.getMessage() + "' should mention " + messageFragment,
                e.getMessage().contains(messageFragment));
        }
    }
}
