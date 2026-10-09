package com.termux.ai;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/**
 * Sibling window-graph file-name derivation for an EmbeddingGemma download (window-routing brief
 * item 8): pure string math, no network involved.
 */
public class TaiModelDownloaderSiblingWindowTest {

    @Test
    public void siblingWindowFileName_swapsOnlyTheSeqNumber() {
        assertEquals("embeddinggemma-300M_seq256_mixed-precision.tflite",
            TaiModelDownloader.siblingWindowFileName("embeddinggemma-300M_seq1024_mixed-precision.tflite", 256));
        assertEquals("embeddinggemma-300M_seq512_mixed-precision.tflite",
            TaiModelDownloader.siblingWindowFileName("embeddinggemma-300M_seq1024_mixed-precision.tflite", 512));
    }

    @Test
    public void siblingWindowFileName_keepsTheChipVariantSuffix() {
        assertEquals("embeddinggemma-300M_seq256_mixed-precision.google_tensor_g5.tflite",
            TaiModelDownloader.siblingWindowFileName(
                "embeddinggemma-300M_seq1024_mixed-precision.google_tensor_g5.tflite", 256));
    }

    @Test
    public void siblingWindowFileName_refusesAWindowThatIsNotSmaller() {
        assertNull("never a window equal to the primary",
            TaiModelDownloader.siblingWindowFileName("embeddinggemma-300M_seq512_mixed-precision.tflite", 512));
        assertNull("never a window bigger than the primary",
            TaiModelDownloader.siblingWindowFileName("embeddinggemma-300M_seq256_mixed-precision.tflite", 512));
    }

    @Test
    public void siblingWindowFileName_withNoWindowInThePrimaryName_returnsNull() {
        assertNull(TaiModelDownloader.siblingWindowFileName("embed-model.tflite", 256));
    }
}
