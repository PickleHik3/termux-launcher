package com.termux.app.fragments.settings.termux;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class TaiImportFlowTest {
    private static final long GIB = 1024L * 1024L * 1024L;

    private static JSONObject selection(int count) throws Exception {
        JSONArray candidates = new JSONArray();
        for (int i = 0; i < count; i++) candidates.put(new JSONObject().put("file", "m" + i + ".litertlm").put("sizeBytes", 10));
        return new JSONObject().put("ok", false).put("error", "artifact_selection_required").put("candidates", candidates);
    }

    @Test
    public void previewAnswersDecideTheNextStep() throws Exception {
        assertEquals(TaiImportFlow.PreviewOutcome.FAILED, TaiImportFlow.outcomeOf(null));
        assertEquals(TaiImportFlow.PreviewOutcome.DOWNLOAD_STARTED, TaiImportFlow.outcomeOf(new JSONObject().put("ok", true)));
        assertEquals(TaiImportFlow.PreviewOutcome.NEED_TOKEN,
            TaiImportFlow.outcomeOf(new JSONObject().put("ok", false).put("error", "gated_model_requires_auth")));
        // One runnable file goes straight to the card: a public repo reaches Ready with one tap after pasting.
        assertEquals(TaiImportFlow.PreviewOutcome.SUMMARY, TaiImportFlow.outcomeOf(selection(1)));
        assertEquals(TaiImportFlow.PreviewOutcome.CHOOSE_FILE, TaiImportFlow.outcomeOf(selection(2)));
        assertEquals(TaiImportFlow.PreviewOutcome.FAILED, TaiImportFlow.outcomeOf(selection(0)));
        assertEquals(TaiImportFlow.PreviewOutcome.FAILED,
            TaiImportFlow.outcomeOf(new JSONObject().put("ok", false).put("error", "hf_resolve_failed")));
    }

    @Test
    public void preselectsTheLargestFileThatFits() throws Exception {
        JSONArray candidates = new JSONArray()
            .put(new JSONObject().put("file", "e4b.litertlm").put("sizeBytes", 3_659_530_240L))
            .put(new JSONObject().put("file", "small.litertlm").put("sizeBytes", 1_200_000_000L))
            .put(new JSONObject().put("file", "e2b.litertlm").put("sizeBytes", 2_588_147_712L));
        assertEquals(2, TaiImportFlow.preselect(candidates, 8L * GIB));
        assertEquals(0, TaiImportFlow.preselect(candidates, 16L * GIB));
        assertEquals(1, TaiImportFlow.preselect(candidates, 6L * GIB));
        // Nothing fits outright: the smallest that would merely be slow.
        assertEquals(1, TaiImportFlow.preselect(candidates, 4L * GIB));
        // Unknown sizes rank after a known fit and before a known squeeze.
        JSONArray unknown = new JSONArray()
            .put(new JSONObject().put("file", "a.litertlm").put("sizeBytes", -1L))
            .put(new JSONObject().put("file", "b.litertlm").put("sizeBytes", 2_588_147_712L));
        assertEquals(0, TaiImportFlow.preselect(unknown, 6L * GIB));
        assertEquals(1, TaiImportFlow.preselect(unknown, 8L * GIB));
        assertEquals(0, TaiImportFlow.preselect(new JSONArray(), 8L * GIB));
    }

    @Test
    public void aCompactBuildBeatsFullPrecisionAndBareGraphsAreDropped() throws Exception {
        // litert-community/Qwen2.5-0.5B-Instruct as the preview listed it on 2026-09-25.
        JSONObject result = new JSONObject().put("ok", false).put("error", "artifact_selection_required")
            .put("candidates", new JSONArray()
                .put(new JSONObject().put("file", "Qwen2.5-0.5B-Instruct_multi-prefill-seq_f32_ekv1280.task").put("sizeBytes", 1_900_000_000L))
                .put(new JSONObject().put("file", "Qwen2.5-0.5B-Instruct_multi-prefill-seq_f32_ekv1280.tflite").put("sizeBytes", 1_900_000_000L))
                .put(new JSONObject().put("file", "Qwen2.5-0.5B-Instruct_multi-prefill-seq_q8_ekv1280.task").put("sizeBytes", 521_300_000L))
                .put(new JSONObject().put("file", "Qwen2.5-0.5B-Instruct_seq128_q8_ekv1280.tflite").put("sizeBytes", 489_400_000L)));
        JSONArray kept = TaiImportFlow.pruneCandidates(result).getJSONArray("candidates");
        assertEquals(2, kept.length());
        assertEquals(1, TaiImportFlow.preselect(kept, 32L * GIB));
        assertEquals(1, TaiImportFlow.preselect(kept, 8L * GIB));
        // A repository of bare graphs only (an embedding model) keeps them.
        JSONObject embeddings = new JSONObject().put("candidates", new JSONArray()
            .put(new JSONObject().put("file", "embeddinggemma_seq256.tflite").put("sizeBytes", 180_000_000L)));
        assertEquals(1, TaiImportFlow.pruneCandidates(embeddings).getJSONArray("candidates").length());
    }

    @Test
    public void gpuFailuresOfferTheProcessor() throws Exception {
        assertTrue(TaiImportFlow.gpuFailure(new JSONObject().put("error", "accelerator_not_supported_by_device")
            .put("message", "GPU delegate could not initialise")));
        assertTrue(TaiImportFlow.gpuFailure(new JSONObject().put("error", "litert_lm_load_failed")
            .put("preflight", new JSONObject().put("effectiveAccelerator", "gpu"))));
        assertFalse(TaiImportFlow.gpuFailure(new JSONObject().put("error", "model_file_missing")
            .put("message", "Download or import this model before loading it.")));
        assertFalse(TaiImportFlow.gpuFailure(null));
        assertTrue(TaiImportFlow.memoryFailure(new JSONObject().put("error", "insufficient_memory")));
        assertTrue(TaiImportFlow.memoryFailure(new JSONObject().put("error", "low_available_memory_guard")));
        assertFalse(TaiImportFlow.memoryFailure(new JSONObject().put("error", "model_file_missing").put("message", "gone")));
    }

    @Test
    public void replyIsTheFirstChoiceContent() throws Exception {
        JSONObject completion = new JSONObject().put("choices", new JSONArray().put(new JSONObject()
            .put("message", new JSONObject().put("role", "assistant").put("content", "  Hello there, nice to meet you!  "))));
        assertEquals("Hello there, nice to meet you!", TaiImportFlow.replyText(completion));
        assertEquals("", TaiImportFlow.replyText(new JSONObject()));
        assertEquals("", TaiImportFlow.replyText(new JSONObject().put("choices", new JSONArray().put(new JSONObject()
            .put("message", new JSONObject().put("content", JSONObject.NULL))))));
    }
}
