package com.termux.app.fonts;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Archive extraction is the one place in the font pipeline that reads attacker-shaped input
 * (a downloaded zip) and writes files, so every guard gets a crafted archive: traversal names,
 * absolute names, too many entries, and a member larger than the cap.
 *
 * <p>Plain JUnit, no Robolectric: none of this touches Android, and none of it touches the
 * network — the real URLs were verified with curl while the catalog was built.
 */
public class FontDownloaderTest {

    @Rule public TemporaryFolder folder = new TemporaryFolder();

    @Test
    public void extractMember_writesTheRequestedMemberOnly() throws IOException {
        File zip = zip("ok.zip", entry("ttf/Face-Regular.ttf", "regular-bytes"),
            entry("ttf/Face-Bold.ttf", "bold-bytes"), entry("README.txt", "docs"));
        File target = new File(folder.newFolder("out"), "regular.ttf");
        FontDownloader.extractMember(zip, "ttf/Face-Regular.ttf", target);
        assertEquals("regular-bytes", read(target));
        assertFalse(new File(target.getAbsolutePath() + ".part").exists());
    }

    @Test
    public void extractMember_rejectsATraversalMemberRequest() throws IOException {
        File zip = zip("t1.zip", entry("a.ttf", "x"));
        expectFailure(zip, "../escaped.ttf", "unsafe archive member path");
    }

    @Test
    public void extractMember_rejectsAnAbsoluteMemberRequest() throws IOException {
        File zip = zip("t2.zip", entry("a.ttf", "x"));
        expectFailure(zip, "/etc/passwd", "unsafe archive member path");
    }

    /**
     * The requested name is safe but the archive itself carries a traversal entry. Refusing the
     * whole archive is the point: an archive that tries to escape is not the archive the catalog
     * digest was taken over, whichever member we happen to want out of it.
     */
    @Test
    public void extractMember_rejectsAnArchiveHoldingATraversalEntry() throws IOException {
        File zip = zip("slip.zip", entry("a.ttf", "x"), entry("../../evil.sh", "rm -rf"));
        expectFailure(zip, "a.ttf", "unsafe entry");
        assertFalse(new File(folder.getRoot(), "evil.sh").exists());
        assertFalse(new File(folder.getRoot().getParentFile(), "evil.sh").exists());
    }

    @Test
    public void extractMember_rejectsAnArchiveHoldingAnAbsoluteEntry() throws IOException {
        File zip = zip("abs.zip", entry("a.ttf", "x"), entry("/tmp/evil.sh", "rm -rf"));
        expectFailure(zip, "a.ttf", "unsafe entry");
    }

    @Test
    public void extractMember_rejectsAnArchiveWithTooManyEntries() throws IOException {
        Entry[] entries = new Entry[5];
        for (int i = 0; i < entries.length; i++) entries[i] = entry("f" + i + ".ttf", "x");
        File zip = zip("many.zip", entries);
        try {
            FontDownloader.extractMember(zip, "f0.ttf",
                new File(folder.newFolder("many-out"), "f.ttf"), 3, 1024L, 1024L);
            fail("expected the entry-count cap to reject the archive");
        } catch (IOException e) {
            assertTrue(String.valueOf(e.getMessage()), String.valueOf(e.getMessage())
                .contains("entries"));
        }
    }

    @Test
    public void extractMember_rejectsAMemberOverTheUncompressedCap() throws IOException {
        StringBuilder big = new StringBuilder();
        while (big.length() < 512) big.append("padding-");
        File zip = zip("bomb.zip", entry("big.ttf", big.toString()));
        File target = new File(folder.newFolder("bomb-out"), "big.ttf");
        try {
            FontDownloader.extractMember(zip, "big.ttf", target, 16, 64L, 4096L);
            fail("expected the member size cap to reject the archive");
        } catch (IOException e) {
            assertTrue(String.valueOf(e.getMessage()), String.valueOf(e.getMessage())
                .contains("byte limit") || String.valueOf(e.getMessage()).contains("expands past"));
        }
        assertFalse(target.exists());
        assertFalse(new File(target.getAbsolutePath() + ".part").exists());
    }

    @Test
    public void extractMember_rejectsAnArchiveOverTheAggregateCap() throws IOException {
        File zip = zip("total.zip", entry("a.ttf", "aaaaaaaaaa"), entry("b.ttf", "bbbbbbbbbb"));
        try {
            FontDownloader.extractMember(zip, "a.ttf",
                new File(folder.newFolder("total-out"), "a.ttf"), 16, 1024L, 15L);
            fail("expected the aggregate cap to reject the archive");
        } catch (IOException e) {
            assertTrue(String.valueOf(e.getMessage()),
                String.valueOf(e.getMessage()).contains("uncompressed bytes"));
        }
    }

    @Test
    public void extractMember_rejectsAMissingMember() throws IOException {
        File zip = zip("missing.zip", entry("a.ttf", "x"));
        expectFailure(zip, "b.ttf", "has no member");
    }

    @Test
    public void extractMember_rejectsAnEmptyMember() throws IOException {
        File zip = zip("empty.zip", entry("a.ttf", ""));
        expectFailure(zip, "a.ttf", "is empty");
    }

    @Test
    public void extractMember_toleratesDirectoryEntries() throws IOException {
        File zip = folder.newFile("dirs.zip");
        try (ZipOutputStream out = new ZipOutputStream(new FileOutputStream(zip))) {
            out.putNextEntry(new ZipEntry("ttf/"));
            out.closeEntry();
            out.putNextEntry(new ZipEntry("ttf/a.ttf"));
            out.write("payload".getBytes(StandardCharsets.UTF_8));
            out.closeEntry();
        }
        File target = new File(folder.newFolder("dirs-out"), "a.ttf");
        FontDownloader.extractMember(zip, "ttf/a.ttf", target);
        assertEquals("payload", read(target));
    }

    @Test
    public void commit_replacesAnExistingTargetAtomically() throws IOException {
        File dir = folder.newFolder("commit");
        File target = new File(dir, "face.ttf");
        Files.write(target.toPath(), "old".getBytes(StandardCharsets.UTF_8));
        File partial = new File(target.getAbsolutePath() + ".part");
        Files.write(partial.toPath(), "new".getBytes(StandardCharsets.UTF_8));
        FontDownloader.commit(partial, target);
        assertEquals("new", read(target));
        assertFalse(partial.exists());
    }

    @Test
    public void sha256_matchesTheKnownDigestOfAKnownFile() throws IOException {
        File file = folder.newFile("digest.bin");
        Files.write(file.toPath(), "abc".getBytes(StandardCharsets.UTF_8));
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            FontDownloader.sha256(file));
    }

    @Test
    public void shortName_takesTheLastPathSegmentWithoutTheQuery() {
        assertEquals("MapleMono-Variable.zip",
            FontDownloader.shortName("https://example.com/a/b/MapleMono-Variable.zip?download=1"));
        assertEquals("a.ttf", FontDownloader.shortName("https://example.com/a.ttf"));
    }

    @Test
    public void deleteRecursively_emptiesANestedStagingTree() throws IOException {
        File root = folder.newFolder("staging");
        File nested = new File(new File(root, "archives"), "deeper");
        assertTrue(nested.mkdirs());
        Files.write(new File(nested, "a.zip").toPath(), new byte[] {1, 2, 3});
        Files.write(new File(root, "regular.ttf").toPath(), new byte[] {4});
        FontDownloader.deleteRecursively(root);
        assertFalse(root.exists());
    }

    @Test
    public void stageFamily_refusesAFamilyOverTheAggregateCap() throws IOException {
        // Guard rather than transport: no request is made, so no network is touched.
        FontCatalog.Result parsed = FontCatalog.parse(oversizeFamilyCatalog());
        assertTrue(parsed.families.isEmpty());
        assertTrue(parsed.errors.toString().contains("download size must be"));
    }

    // ------------------------------------------------------------------- helpers

    private void expectFailure(File zip, String memberPath, String expectedFragment)
        throws IOException {
        File target = new File(folder.newFolder("fail-" + System.nanoTime()), "out.ttf");
        try {
            FontDownloader.extractMember(zip, memberPath, target);
            fail("expected extraction of '" + memberPath + "' to fail");
        } catch (IOException e) {
            String message = String.valueOf(e.getMessage());
            assertTrue(message, message.contains(expectedFragment));
        }
        assertFalse(target.exists());
    }

    private static final class Entry {
        final String name;
        final String content;

        Entry(String name, String content) {
            this.name = name;
            this.content = content;
        }
    }

    private static Entry entry(String name, String content) {
        return new Entry(name, content);
    }

    private File zip(String fileName, Entry... entries) throws IOException {
        File zip = folder.newFile(fileName);
        try (ZipOutputStream out = new ZipOutputStream(new FileOutputStream(zip))) {
            for (Entry entry : entries) {
                out.putNextEntry(new ZipEntry(entry.name));
                out.write(entry.content.getBytes(StandardCharsets.UTF_8));
                out.closeEntry();
            }
        }
        return zip;
    }

    private static String read(File file) throws IOException {
        return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
    }

    /**
     * Four archives that are each individually acceptable but together exceed the per-family
     * transfer cap, so the aggregate check is what has to reject them.
     */
    private static String oversizeFamilyCatalog() {
        String sha = "0000000000000000000000000000000000000000000000000000000000000000";
        long each = FontCatalog.MAX_FAMILY_DOWNLOAD_BYTES / 4 + 1024;
        String[] slots = {"regular", "bold", "italic", "bold_italic"};
        StringBuilder archives = new StringBuilder();
        StringBuilder faces = new StringBuilder();
        for (int i = 0; i < slots.length; i++) {
            String url = "https://example.com/" + i + ".zip";
            if (i > 0) {
                archives.append(',');
                faces.append(',');
            }
            archives.append("{\"url\": \"").append(url).append("\", \"sha256\": \"").append(sha)
                .append("\", \"sizeBytes\": ").append(each).append('}');
            faces.append('"').append(slots[i]).append("\": {\"zipUrl\": \"").append(url)
                .append("\", \"memberPath\": \"a.ttf\", \"sha256\": \"").append(sha)
                .append("\", \"sizeBytes\": 10}");
        }
        return "{\"schemaVersion\": 1, \"symbolFont\": {\"assetPath\": \"fonts/s.ttf\","
            + "\"installName\": \"s.ttf\", \"sha256\": \"" + sha + "\", \"sizeBytes\": 10},"
            + "\"families\": [{\"id\": \"huge\", \"displayName\": \"Huge\","
            + "\"license\": \"OFL 1.1\", \"licenseUrl\": \"https://example.com/OFL.txt\","
            + "\"archives\": [" + archives + "], \"faces\": {" + faces + "}}]}";
    }
}
