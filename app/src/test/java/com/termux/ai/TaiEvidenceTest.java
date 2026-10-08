package com.termux.ai;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** The evidence view's typed reader of the bench leaderboard, and the in-memory table's matching. */
public class TaiEvidenceTest {
    private static final String E2B = TaiModelRegistry.MODEL_GEMMA_4_E2B_IT;

    private static JSONObject row(String accelerator, boolean speculative, double tps, boolean checkPassed, String verdict)
            throws Exception {
        return new JSONObject().put("modelId", E2B).put("backend", TaiModelSpec.BACKEND_LITERT_LM)
            .put("accelerator", accelerator).put("speculative", speculative).put("decodeTps", tps)
            .put("checkPassed", checkPassed).put("verdict", verdict);
    }

    @Test
    public void theLeaderboardReadsAsTypedResults() throws Exception {
        JSONObject benchmarks = new JSONObject().put("leaderboard", new JSONObject()
            .put("ranked", new JSONArray()
                .put(row("GPU", false, 18.5, true, TaiBenchStats.VERDICT_SMOOTH))
                .put(row("cpu", true, 9.0, true, TaiBenchStats.VERDICT_USABLE)))
            .put("broken", new JSONArray()
                .put(row("gpu", true, 0.0, false, TaiBenchStats.VERDICT_BROKEN))
                .put(row("cpu", false, 7.0, true, TaiBenchStats.VERDICT_CRASHED))));
        List<TaiEvidence.ChatResult> results = TaiEvidence.chatResultsFrom(benchmarks);
        assertEquals(4, results.size());
        TaiEvidence.ChatResult first = results.get(0);
        assertEquals("gpu", first.accelerator);
        assertEquals(18.5, first.decodeTps, 1e-9);
        assertTrue(first.passed);
        assertTrue(results.get(1).speculative);
        // Wrong answers and crashes are on record, never as passing.
        assertFalse(results.get(2).passed);
        assertFalse(results.get(3).passed);
    }

    @Test
    public void noLeaderboardIsNoResults() throws Exception {
        assertTrue(TaiEvidence.chatResultsFrom(null).isEmpty());
        assertTrue(TaiEvidence.chatResultsFrom(new JSONObject()).isEmpty());
    }

    @Test
    public void resultsMatchOnTheBackendAsWellAsTheModel() {
        TaiEvidence.InMemory evidence = new TaiEvidence.InMemory()
            .chat(new TaiEvidence.ChatResult(E2B, TaiModelSpec.BACKEND_MNN_LLM, "gpu", false, 10.0, true))
            .failure(E2B, TaiModelSpec.BACKEND_MNN_LLM, "GPU");
        assertTrue(evidence.chatBench(E2B, TaiModelSpec.BACKEND_LITERT_LM).isEmpty());
        assertEquals(1, evidence.chatBench(E2B, TaiModelSpec.BACKEND_MNN_LLM).size());
        assertTrue(evidence.failed(E2B, TaiModelSpec.BACKEND_MNN_LLM, "gpu"));
        assertFalse(evidence.failed(E2B, TaiModelSpec.BACKEND_LITERT_LM, "gpu"));
        assertTrue(TaiEvidence.NONE.featureChecks(TaiFunction.TIDY_DICTATION, E2B, TaiModelSpec.BACKEND_LITERT_LM).isEmpty());
        assertEquals(TaiGpuVerdict.State.UNKNOWN, TaiEvidence.NONE.gpuVerdict());
    }
}
