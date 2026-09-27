package com.termux.app.fragments.settings.termux;

import com.termux.R;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class TaiImportCardTest {
    private static final long GIB = 1024L * 1024L * 1024L;

    /**
     * Abridged from litert-community/granite-4.0-h-350m's README (read 2026-09-27): front matter,
     * a file table, the GPU paragraph and the precision paragraph, plus a code block whose
     * "recommend" must not count.
     */
    private static final String GRANITE_CARD = "---\n"
        + "license: apache-2.0\n"
        + "pipeline_tag: text-generation\n"
        + "---\n"
        + "# Granite 4.0 H 350M for LiteRT-LM\n"
        + "\n"
        + "| File | What it is | Size |\n"
        + "|---|---|---|\n"
        + "| `granite-4.0-h-350m_int8_gpu.litertlm` | the same int8 weights, re-exported — the GPU file | 481 MB |\n"
        + "\n"
        + "**For GPU, pick `_int8_gpu`.** It carries the same int8 weights as `_int8`, re-exported with the\n"
        + "Mamba2 selective scan written as rank-≤4 batched matmuls.\n"
        + "\n"
        + "Pick **fp16** for reference-faithful quality (greedy outputs match the PyTorch model token-for-token\n"
        + "on our probes), **int8** for size and speed. On phones we recommend **int8**: the CPU runtime unpacks\n"
        + "fp16 weights to fp32 in RAM (~3.7 GB peak on iPhone vs ~2.1 GB for int8), so fp16 is best treated as\n"
        + "the desktop/quality variant.\n"
        + "\n"
        + "```python\n"
        + "# we recommend fp16 for this notebook\n"
        + "```\n";

    private static final String INT8 = "granite-4.0-h-350m_int8.litertlm";
    private static final String INT8_GPU = "granite-4.0-h-350m_int8_gpu.litertlm";
    private static final String FP16 = "granite-4.0-h-350m_fp16.litertlm";

    @Test
    public void theCardsRecommendationIsQuotedForItsFileOnly() {
        TaiImportCard.Quote int8 = TaiImportCard.quoteFor(GRANITE_CARD, INT8);
        assertEquals("On phones we recommend int8", int8.text);
        assertTrue(int8.recommends);

        // "int8" inside "_int8_gpu" is another file; the GPU file has its own sentence.
        TaiImportCard.Quote gpu = TaiImportCard.quoteFor(GRANITE_CARD, INT8_GPU);
        assertEquals("For GPU, pick _int8_gpu", gpu.text);
        assertFalse(gpu.recommends);

        // The code block's "we recommend fp16" is not prose; the paragraph's "Pick fp16 for ..." is.
        TaiImportCard.Quote fp16 = TaiImportCard.quoteFor(GRANITE_CARD, FP16);
        assertEquals("Pick fp16 for reference-faithful quality", fp16.text);
        assertFalse(fp16.recommends);
    }

    @Test
    public void aCardThatNamesNoFileGivesNothing() {
        // litert-community/Qwen3.5-2B's card prefers devices and backends, never one of its files.
        String qwen = "Treat this release as Apple-hardware-first; on Android, prefer 12 GB+ devices for GPU.\n\n"
            + "For complex multi-part prompts, prefer the GPU backend or ask for a float variant.\n";
        assertNull(TaiImportCard.quoteFor(qwen, "Qwen3.5-2B_int8.litertlm"));
        assertNull(TaiImportCard.quoteFor(qwen, "Qwen3.5-2B-VL_int8.litertlm"));
        // A plain word like "gpu" is never a key, so "prefer the GPU backend" is not about a -gpu file.
        assertNull(TaiImportCard.quoteFor(qwen, "gemma-4-E2B-it-gpu.litertlm"));
    }

    @Test
    public void aNegatedRecommendationIsQuotedButNotFollowed() {
        TaiImportCard.Quote quote = TaiImportCard.quoteFor("We do not recommend the int4 file on 8 GB phones.",
            "model_int4.litertlm");
        assertEquals("We do not recommend the int4 file on 8 GB phones", quote.text);
        assertFalse(quote.recommends);
    }

    @Test
    public void noReadmeLeavesTheCandidatesAndTheDefaultAlone() throws Exception {
        JSONArray candidates = granite();
        TaiImportCard.annotate(candidates, "");
        TaiImportCard.annotate(candidates, null);
        for (int i = 0; i < candidates.length(); i++) assertFalse(candidates.getJSONObject(i).has("cardQuote"));
        assertNull(TaiImportCard.quoteFor(null, INT8));
        // Without the card, the largest portable build that fits is the default: fp16.
        int selected = TaiImportFlow.preselect(candidates, 12L * GIB);
        assertEquals(FP16, candidates.getJSONObject(selected).getString("file"));
        assertEquals(R.string.termux_ai_import_selected_largest, TaiImportFlow.preselectReason(candidates, 12L * GIB, selected));
    }

    @Test
    public void theCardsRecommendationBecomesTheDefault() throws Exception {
        JSONArray candidates = granite();
        TaiImportCard.annotate(candidates, GRANITE_CARD);
        int selected = TaiImportFlow.preselect(candidates, 12L * GIB);
        assertEquals(INT8, candidates.getJSONObject(selected).getString("file"));
        assertEquals(R.string.termux_ai_import_selected_card, TaiImportFlow.preselectReason(candidates, 12L * GIB, selected));
        assertEquals("On phones we recommend int8", candidates.getJSONObject(selected).getString("cardQuote"));
    }

    private static JSONArray granite() throws Exception {
        // Sizes as the Hugging Face API lists them.
        return new JSONArray()
            .put(new JSONObject().put("file", FP16).put("sizeBytes", 723_260_832L))
            .put(new JSONObject().put("file", INT8).put("sizeBytes", 435_971_888L))
            .put(new JSONObject().put("file", INT8_GPU).put("sizeBytes", 481_218_880L));
    }
}
