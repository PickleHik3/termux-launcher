package com.termux.app.fragments.settings.termux;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class TaiImportNamesTest {

    @Test
    public void linkBecomesTheRepositoryNameInWords() {
        assertEquals("Gemma 3 1B IT", TaiImportNames.displayName("https://huggingface.co/google/gemma-3-1b-it"));
        assertEquals("gemma-3-1b-it", TaiImportNames.modelId("https://huggingface.co/google/gemma-3-1b-it"));
        // A file link still names the repository, never the file (for MNN that is always config.json).
        assertEquals("gemma-3-1b-it", TaiImportNames.modelId(
            "https://huggingface.co/google/gemma-3-1b-it/resolve/main/gemma-3-1b-it_q4.litertlm"));
        assertEquals("Qwen2.5 Coder 1.5B Instruct",
            TaiImportNames.displayName("https://huggingface.co/taobao-mnn/Qwen2.5-Coder-1.5B-Instruct-MNN"));
        assertEquals("My Model", TaiImportNames.displayName("https://example.com/models/my-model.litertlm?x=1"));
    }

    @Test
    public void fileNameDropsExtensionAndBuildSuffix() {
        assertEquals("DeepSeek R1 Distill Qwen 1.5B",
            TaiImportNames.displayName("DeepSeek-R1-Distill-Qwen-1.5B_multi-prefill-seq_q8_ekv4096.litertlm"));
        assertEquals("Gemma 4 E2B IT", TaiImportNames.displayName("gemma-4-E2B-it-litert-lm.litertlm"));
        assertEquals("Embeddinggemma 300M", TaiImportNames.displayName("embeddinggemma-300M_seq1024_mixed-precision.tflite"));
        assertEquals("Mobile Actions", TaiImportNames.displayName("mobile_actions_q8_ekv1024.litertlm"));
        assertEquals("mobile_actions", TaiImportNames.modelId("mobile_actions_q8_ekv1024.litertlm"));
        assertEquals("", TaiImportNames.displayName("config.json"));
        assertEquals("", TaiImportNames.displayName(null));
    }

    @Test
    public void typedNameMakesAnId() {
        assertEquals("my-gemma", TaiImportNames.modelId("My Gemma"));
    }

    @Test
    public void fullPrecisionIsReadFromBuildTokensBetweenSeparators() {
        // Only the default choice reads this; the picker's words come from TaiImportFacts.
        assertTrue(TaiImportNames.isFullPrecision("Qwen2.5-0.5B-Instruct_multi-prefill-seq_f32_ekv1280.task"));
        assertFalse(TaiImportNames.isFullPrecision("model_fp16.task"));
        assertFalse(TaiImportNames.isFullPrecision("model_seq4096.litertlm"));
        assertFalse(TaiImportNames.isFullPrecision(null));
    }

    @Test
    public void modelPageIsTheRepositoryPage() {
        assertEquals("https://huggingface.co/google/gemma-3-1b-it",
            TaiImportNames.modelPageUrl("https://huggingface.co/google/gemma-3-1b-it/resolve/abc/gemma.litertlm"));
        assertEquals("https://example.com/x.litertlm", TaiImportNames.modelPageUrl("https://example.com/x.litertlm"));
    }
}
