package com.termux.ai;

import org.junit.Test;

import java.io.File;
import java.io.IOException;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * The {@code /v1/models} embedder metadata dawn's brief asks for (item 3): dimensions, the
 * Matryoshka truncation sizes, and a stable, cheap {@code _revision}.
 */
public class TaiModelSpecEmbeddingMetadataTest {

    @Test
    public void embeddingGemma_reports768DimensionsAndItsMatryoshkaSizes() {
        assertEquals(768, TaiModelSpec.embeddingDimensionsFor(
            "embeddinggemma-300m", "/data/models/embeddinggemma-300M_seq512_mixed-precision.tflite"));
        assertArrayEquals(new int[] {768, 512, 256, 128},
            TaiModelSpec.embeddingMatryoshkaDimsFor(
                "embeddinggemma-300m", "/data/models/embeddinggemma-300M_seq512_mixed-precision.tflite"));
    }

    @Test
    public void embeddingGemma_isRecognisedById_evenWithAnUnrelatedPath() {
        assertEquals(768, TaiModelSpec.embeddingDimensionsFor("litert-community/embeddinggemma-300m", null));
    }

    @Test
    public void unrecognisedEmbeddingFamily_reportsNoDimensionsOrMatryoshkaSizes() {
        assertEquals(0, TaiModelSpec.embeddingDimensionsFor("some-mnn-embedder", "/data/models/some/config.json"));
        assertEquals(0, TaiModelSpec.embeddingMatryoshkaDimsFor("some-mnn-embedder", "/data/models/some/config.json").length);
    }

    @Test
    public void revisionFor_isStableForTheSameFileAndChangesWithItsContentSignature() throws IOException {
        File file = File.createTempFile("embeddinggemma", ".tflite");
        file.deleteOnExit();
        try {
            String first = TaiModelSpec.revisionFor(file.getAbsolutePath());
            String again = TaiModelSpec.revisionFor(file.getAbsolutePath());
            assertNotNull(first);
            assertEquals("same (path, size, mtime) must hash the same, cache or not", first, again);

            // A changed size (a re-download, a different quantization) must change the revision even
            // when the cache already holds an entry for this path.
            try (java.io.FileOutputStream out = new java.io.FileOutputStream(file)) {
                out.write("different content, different size".getBytes());
            }
            file.setLastModified(System.currentTimeMillis() + 5_000L);
            String changed = TaiModelSpec.revisionFor(file.getAbsolutePath());
            assertTrue("revision must change once size/mtime change", !first.equals(changed));
        } finally {
            file.delete();
        }
    }

    @Test
    public void revisionFor_isNullForAMissingFile() {
        assertNull(TaiModelSpec.revisionFor("/does/not/exist/model.tflite"));
        assertNull(TaiModelSpec.revisionFor(null));
        assertNull(TaiModelSpec.revisionFor("  "));
    }
}
