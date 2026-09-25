package com.termux.app.fragments.settings.termux;

import com.termux.R;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

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
    public void variantHintsReadBuildTokensBetweenSeparators() {
        assertEquals(R.string.termux_ai_import_variant_smallest, TaiImportNames.variantHint("gemma-3-1b-it_q4_ekv2048.litertlm"));
        assertEquals(R.string.termux_ai_import_variant_compact, TaiImportNames.variantHint("model_q8_ekv4096.litertlm"));
        assertEquals(R.string.termux_ai_import_variant_full,
            TaiImportNames.variantHint("Qwen2.5-0.5B-Instruct_multi-prefill-seq_f32_ekv1280.task"));
        assertEquals(R.string.termux_ai_import_variant_half, TaiImportNames.variantHint("model_fp16.task"));
        assertEquals(R.string.termux_ai_import_variant_web, TaiImportNames.variantHint("gemma-3n-E2B-it-web.litertlm"));
        assertEquals(0, TaiImportNames.variantHint("model_seq4096.litertlm"));
        assertEquals(0, TaiImportNames.variantHint(null));
    }

    @Test
    public void modelPageIsTheRepositoryPage() {
        assertEquals("https://huggingface.co/google/gemma-3-1b-it",
            TaiImportNames.modelPageUrl("https://huggingface.co/google/gemma-3-1b-it/resolve/abc/gemma.litertlm"));
        assertEquals("https://example.com/x.litertlm", TaiImportNames.modelPageUrl("https://example.com/x.litertlm"));
    }
}
