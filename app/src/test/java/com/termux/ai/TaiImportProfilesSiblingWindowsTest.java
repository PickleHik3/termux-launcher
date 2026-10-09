package com.termux.ai;

import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Sibling window-graph discovery for one EmbeddingGemma install (window-routing brief item 1):
 * files beside the primary that differ only in their {@code seqNNNN} number are windows of the
 * same model; anything else beside it is not.
 */
public class TaiImportProfilesSiblingWindowsTest {

    private File file(File dir, String name) throws IOException {
        File file = new File(dir, name);
        assertTrue(file.createNewFile());
        return file;
    }

    @Test
    public void siblingWindowGraphs_findsOtherWindowsWithTheSameSuffix() throws IOException {
        File dir = java.nio.file.Files.createTempDirectory("embeddinggemma").toFile();
        try {
            File primary = file(dir, "embeddinggemma-300M_seq1024_mixed-precision.tflite");
            file(dir, "embeddinggemma-300M_seq256_mixed-precision.tflite");
            file(dir, "embeddinggemma-300M_seq512_mixed-precision.tflite");
            file(dir, "sentencepiece.model"); // not a window graph; must be ignored

            Map<Integer, File> windows = TaiImportProfiles.siblingWindowGraphs(primary);

            assertEquals("primary plus its two smaller siblings", 3, windows.size());
            assertTrue(windows.containsKey(256));
            assertTrue(windows.containsKey(512));
            assertTrue(windows.containsKey(1024));
            assertEquals(primary, windows.get(1024));
        } finally {
            deleteRecursively(dir);
        }
    }

    @Test
    public void siblingWindowGraphs_doesNotMixDifferentChipVariants() throws IOException {
        File dir = java.nio.file.Files.createTempDirectory("embeddinggemma-chip").toFile();
        try {
            File primary = file(dir, "embeddinggemma-300M_seq1024_mixed-precision.tflite");
            // Same window family but compiled for a chip: not the same suffix as the portable build.
            file(dir, "embeddinggemma-300M_seq256_mixed-precision.google_tensor_g5.tflite");

            Map<Integer, File> windows = TaiImportProfiles.siblingWindowGraphs(primary);

            assertEquals("the chip-specific seq256 build must not be picked up as a portable sibling",
                1, windows.size());
            assertTrue(windows.containsKey(1024));
        } finally {
            deleteRecursively(dir);
        }
    }

    @Test
    public void siblingWindowGraphs_matchesChipVariantsToEachOther() throws IOException {
        File dir = java.nio.file.Files.createTempDirectory("embeddinggemma-chip-match").toFile();
        try {
            File primary = file(dir, "embeddinggemma-300M_seq1024_mixed-precision.google_tensor_g5.tflite");
            file(dir, "embeddinggemma-300M_seq512_mixed-precision.google_tensor_g5.tflite");

            Map<Integer, File> windows = TaiImportProfiles.siblingWindowGraphs(primary);

            assertEquals(2, windows.size());
            assertTrue(windows.containsKey(512));
            assertTrue(windows.containsKey(1024));
        } finally {
            deleteRecursively(dir);
        }
    }

    @Test
    public void siblingWindowGraphs_withNoWindowInTheName_reportsNothing() throws IOException {
        File dir = java.nio.file.Files.createTempDirectory("embed-no-window").toFile();
        try {
            File primary = file(dir, "embed-model.tflite");
            file(dir, "embeddinggemma-300M_seq256_mixed-precision.tflite");

            Map<Integer, File> windows = TaiImportProfiles.siblingWindowGraphs(primary);

            assertFalse("a primary whose own name has no seqNNNN reports no windows", windows.containsKey(1024));
            assertTrue(windows.isEmpty());
        } finally {
            deleteRecursively(dir);
        }
    }

    private static void deleteRecursively(File file) {
        File[] children = file.listFiles();
        if (children != null) for (File child : children) deleteRecursively(child);
        file.delete();
    }
}
