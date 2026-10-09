package com.termux.ai;

import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class TaiBenchmarkParamsTest {

    @Test
    public void defaultsMatchGalleryBenchmarkScreen() throws JSONException {
        TaiBenchmarkParams params = TaiBenchmarkParams.fromRequest(new JSONObject());
        assertNotNull(params);
        assertEquals("gpu", params.accelerator);
        assertEquals(256, params.prefillTokens);
        assertEquals(256, params.decodeTokens);
        assertEquals(3, params.runs);
        assertFalse(params.force);
    }

    @Test
    public void cpuAcceleratorIsAccepted() throws JSONException {
        TaiBenchmarkParams params = TaiBenchmarkParams.fromRequest(new JSONObject().put("accelerator", "CPU"));
        assertNotNull(params);
        assertEquals("cpu", params.accelerator);
    }

    @Test
    public void unknownAcceleratorIsRejected() throws JSONException {
        assertNull(TaiBenchmarkParams.fromRequest(new JSONObject().put("accelerator", "npu")));
    }

    @Test
    public void runsIsClampedToTheDocumentedMaximum() throws JSONException {
        TaiBenchmarkParams params = TaiBenchmarkParams.fromRequest(new JSONObject().put("runs", 999));
        assertNotNull(params);
        assertEquals(TaiBenchmarkParams.MAX_RUNS, params.runs);
    }

    @Test
    public void nonPositiveRunsFallsBackToTheDefault() throws JSONException {
        TaiBenchmarkParams params = TaiBenchmarkParams.fromRequest(new JSONObject().put("runs", 0));
        assertNotNull(params);
        assertEquals(TaiBenchmarkParams.DEFAULT_RUNS, params.runs);
    }

    @Test
    public void nonPositiveTokenCountsFallBackToTheDefaults() throws JSONException {
        TaiBenchmarkParams params = TaiBenchmarkParams.fromRequest(new JSONObject()
            .put("prefillTokens", -1).put("decodeTokens", 0));
        assertNotNull(params);
        assertEquals(TaiBenchmarkParams.DEFAULT_PREFILL_TOKENS, params.prefillTokens);
        assertEquals(TaiBenchmarkParams.DEFAULT_DECODE_TOKENS, params.decodeTokens);
    }

    @Test
    public void forceSkipsTheMemoryBudget() throws JSONException {
        TaiBenchmarkParams params = TaiBenchmarkParams.fromRequest(new JSONObject().put("force", true));
        assertNotNull(params);
        assertTrue(params.force);
    }
}
